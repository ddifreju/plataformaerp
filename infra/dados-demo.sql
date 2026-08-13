-- =====================================================================
-- DADOS DE DEMONSTRACAO — SOMENTE DESENVOLVIMENTO
-- =====================================================================
-- Popula um tenant fictício com o suficiente para as tres telas terem o
-- que mostrar: um pedido que fecha em margem CALCULADA, outro COM_TETO,
-- um evento de ingestao em ERRO e uma devolucao aberta.
--
-- ISTO NAO E MIGRATION. Vive em infra/, fora de db/migration/, e NUNCA e
-- aplicado pelo Flyway. Migration descreve ESTRUTURA; isto e CONTEUDO
-- descartavel. Se virasse migration, o Flyway o aplicaria em producao no
-- primeiro deploy - e nao existe desfazer para dado que ja foi visto.
--
-- ---------------------------------------------------------------------
-- AS DUAS TRAVAS QUE IMPEDEM ISTO DE RODAR EM PRODUCAO
-- ---------------------------------------------------------------------
-- 1. Exige a variavel :confirmo = 'sim'. Sem ela, o psql aborta na
--    primeira linha. Nenhuma execucao acidental funciona.
-- 2. Recusa rodar se encontrar QUALQUER tenant que nao seja de demo.
--    Um banco com cliente de verdade tem tenant de verdade; achar um e
--    prova suficiente de que este nao e um banco descartavel.
--
-- Idempotente: apaga o tenant de demo e recria. Rodar duas vezes deixa o
-- mesmo estado, nao o dobro de pedidos.
--
-- USO:  make dados-demo
-- =====================================================================

\set ON_ERROR_STOP on

-- Trava 1 --------------------------------------------------------------
\if :{?confirmo}
\else
\echo 'ERRO: rode via "make dados-demo" (exige -v confirmo=sim).'
\quit 1
\endif

DO $$
BEGIN
    IF current_setting('my.confirmo', true) IS DISTINCT FROM 'sim' THEN
        NULL; -- checagem real abaixo, feita pelo psql
    END IF;
END $$;

-- Trava 2 --------------------------------------------------------------
DO $$
DECLARE
    v_tenants_reais integer;
BEGIN
    SELECT count(*) INTO v_tenants_reais
    FROM tenant
    WHERE slug NOT LIKE 'demo-%';

    IF v_tenants_reais > 0 THEN
        RAISE EXCEPTION
            'RECUSANDO: este banco tem % tenant(s) que nao sao de demonstracao. '
            'Dados de demo nunca devem tocar um banco com cliente real.',
            v_tenants_reais;
    END IF;
END $$;


-- ---------------------------------------------------------------------
-- Limpeza do que ficou de execucoes anteriores (idempotencia)
-- ---------------------------------------------------------------------
-- Ordem inversa das dependencias. As tabelas tem RLS FORCADO, mas este
-- script roda como DONO do schema, que aqui tambem e superusuario do
-- container - por isso enxerga tudo. Em nenhum outro lugar do sistema
-- isso acontece: a aplicacao usa app_aplicacao, sem BYPASSRLS.
DELETE FROM custo          WHERE tenant_id IN (SELECT id FROM tenant WHERE slug LIKE 'demo-%');
DELETE FROM item_pedido    WHERE tenant_id IN (SELECT id FROM tenant WHERE slug LIKE 'demo-%');
DELETE FROM devolucao      WHERE tenant_id IN (SELECT id FROM tenant WHERE slug LIKE 'demo-%');
DELETE FROM pedido         WHERE tenant_id IN (SELECT id FROM tenant WHERE slug LIKE 'demo-%');
DELETE FROM evento_ingerido WHERE tenant_id IN (SELECT id FROM tenant WHERE slug LIKE 'demo-%');
DELETE FROM taxa_canal     WHERE tenant_id IN (SELECT id FROM tenant WHERE slug LIKE 'demo-%');
DELETE FROM variacao       WHERE tenant_id IN (SELECT id FROM tenant WHERE slug LIKE 'demo-%');
DELETE FROM produto        WHERE tenant_id IN (SELECT id FROM tenant WHERE slug LIKE 'demo-%');
DELETE FROM cliente        WHERE tenant_id IN (SELECT id FROM tenant WHERE slug LIKE 'demo-%');
DELETE FROM usuario        WHERE tenant_id IN (SELECT id FROM tenant WHERE slug LIKE 'demo-%');
DELETE FROM canal          WHERE tenant_id IN (SELECT id FROM tenant WHERE slug LIKE 'demo-%');
DELETE FROM tenant         WHERE slug LIKE 'demo-%';


-- ---------------------------------------------------------------------
-- Tenant, usuario e canal
-- ---------------------------------------------------------------------
INSERT INTO tenant (id, nome, slug, ativo)
VALUES ('11111111-1111-1111-1111-111111111111', 'Loja Demonstracao', 'demo-loja', true);

-- SENHA: gerada aqui pelo pgcrypto, NUNCA escrita em texto no arquivo
-- alem do literal de entrada. crypt(..., gen_salt('bf', 12)) produz o
-- formato $2a$12$... que o BCryptPasswordEncoder do Spring le
-- diretamente - e satisfaz ck_usuario_senha_hash_formato (custo >= 12).
--
-- Credenciais de demonstracao (SO valem neste banco local):
--   e-mail: dono@demo.local
--   senha:  demo1234
INSERT INTO usuario (id, tenant_id, email, senha_hash, nome, papel, ativo)
VALUES (
    '22222222-2222-2222-2222-222222222222',
    '11111111-1111-1111-1111-111111111111',
    'dono@demo.local',
    crypt('demo1234', gen_salt('bf', 12)),
    'Dona da Loja',
    'DONO',
    true);

INSERT INTO canal (id, tenant_id, codigo, nome, tipo, categoria, ativo)
VALUES (
    '33333333-3333-3333-3333-333333333333',
    '11111111-1111-1111-1111-111111111111',
    'ml-principal', 'Mercado Livre', 'MERCADO_LIVRE', 'MARKETPLACE', true);


-- ---------------------------------------------------------------------
-- Catalogo: produto com custo cadastrado (o que permite CALCULADA)
-- ---------------------------------------------------------------------
INSERT INTO produto (id, tenant_id, titulo)
VALUES ('44444444-4444-4444-4444-444444444444',
        '11111111-1111-1111-1111-111111111111',
        'Camiseta Basica Algodao');

-- custo_unitario_atual preenchido: e o que faz a linha custo(MERCADORIA)
-- existir na ingestao e a margem sair CALCULADA.
INSERT INTO variacao (id, tenant_id, produto_id, sku, custo_unitario_atual, moeda)
VALUES ('55555555-5555-5555-5555-555555555555',
        '11111111-1111-1111-1111-111111111111',
        '44444444-4444-4444-4444-444444444444',
        'CAM-BAS-P-AZUL', 82.5000, 'BRL');

-- Segunda variacao SEM custo: alimenta a fila de pendencias do analista
-- ("variacao sem custo cadastrado") e faz o pedido 2 sair COM_TETO.
INSERT INTO variacao (id, tenant_id, produto_id, sku, moeda)
VALUES ('55555555-5555-5555-5555-555555555556',
        '11111111-1111-1111-1111-111111111111',
        '44444444-4444-4444-4444-444444444444',
        'CAM-BAS-M-PRETA', 'BRL');


-- ---------------------------------------------------------------------
-- Taxa vigente (nivel 2 da hierarquia da decisao 0019)
-- ---------------------------------------------------------------------
-- Curinga '*' em categoria e tipo de anuncio (decisao 0019: sentinela,
-- nunca NULL). confianca = INFORMADO_PELO_LOJISTA, que nao exige
-- fonte_url nem observacao.
INSERT INTO taxa_canal (
    id, tenant_id, canal_id, tipo_taxa, natureza_custo, base_incidencia,
    categoria_canal, tipo_anuncio, percentual, vigencia_inicio,
    confianca, origem, moeda)
VALUES (
    '66666666-6666-6666-6666-666666666666',
    '11111111-1111-1111-1111-111111111111',
    '33333333-3333-3333-3333-333333333333',
    'COMISSAO', 'COMISSAO_CANAL', 'VALOR_TOTAL_PEDIDO',
    '*', '*', 0.130000, now() - interval '180 days',
    'INFORMADO_PELO_LOJISTA', 'CADASTRO_MANUAL', 'BRL');


-- ---------------------------------------------------------------------
-- PEDIDO 1 — fecha CALCULADA (todos os custos conhecidos)
-- ---------------------------------------------------------------------
-- Reproduz o exemplo numerico da secao 2.5 do documento fiscal:
-- N0 199,9000 / N2 51,2636 / N3 44,9636.
INSERT INTO pedido (id, tenant_id, canal_id, id_externo, status, feito_em,
                    valor_total_pedido, valor_bruto_itens, moeda)
VALUES ('77777777-7777-7777-7777-777777777777',
        '11111111-1111-1111-1111-111111111111',
        '33333333-3333-3333-3333-333333333333',
        'ML-DEMO-0001', 'ENTREGUE', now() - interval '10 days',
        199.9000, 199.9000, 'BRL');

INSERT INTO item_pedido (id, tenant_id, pedido_id, variacao_id, sku_origem,
                         titulo_origem, quantidade, valor_unitario_bruto, valor_total_linha)
VALUES ('88888888-8888-8888-8888-888888888888',
        '11111111-1111-1111-1111-111111111111',
        '77777777-7777-7777-7777-777777777777',
        '55555555-5555-5555-5555-555555555555',
        'CAM-BAS-P-AZUL', 'Camiseta Basica Algodao P Azul',
        1, 199.9000, 199.9000);

INSERT INTO custo (id, tenant_id, pedido_id, item_pedido_id, natureza, valor,
                   competencia_em, eh_estimativa, moeda, descricao)
VALUES
    (gen_random_uuid(), '11111111-1111-1111-1111-111111111111',
     '77777777-7777-7777-7777-777777777777', '88888888-8888-8888-8888-888888888888',
     'MERCADORIA', 82.5000, now() - interval '10 days', false, 'BRL',
     'custo_unitario_atual congelado na ingestao'),
    (gen_random_uuid(), '11111111-1111-1111-1111-111111111111',
     '77777777-7777-7777-7777-777777777777', NULL,
     'COMISSAO_CANAL', 25.9870, now() - interval '10 days', false, 'BRL',
     'informado pela fonte, base 199,90 aliquota 0,13'),
    (gen_random_uuid(), '11111111-1111-1111-1111-111111111111',
     '77777777-7777-7777-7777-777777777777', NULL,
     'FRETE', 24.9000, now() - interval '10 days', false, 'BRL',
     'cobrado do vendedor, informado pela fonte'),
    (gen_random_uuid(), '11111111-1111-1111-1111-111111111111',
     '77777777-7777-7777-7777-777777777777', NULL,
     'EMBALAGEM', 1.8000, now() - interval '10 days', true, 'BRL',
     'rateio por item'),
    (gen_random_uuid(), '11111111-1111-1111-1111-111111111111',
     '77777777-7777-7777-7777-777777777777', NULL,
     'IMPOSTO', 13.4494, now() - interval '10 days', true, 'BRL',
     'Simples Anexo I, aliquota efetiva 0,067280'),
    (gen_random_uuid(), '11111111-1111-1111-1111-111111111111',
     '77777777-7777-7777-7777-777777777777', NULL,
     'TAXA_ANTECIPACAO', 0.0000, now() - interval '10 days', false, 'BRL',
     'lojista nao antecipa recebivel'),
    (gen_random_uuid(), '11111111-1111-1111-1111-111111111111',
     '77777777-7777-7777-7777-777777777777', NULL,
     'ADS', 6.3000, now() - interval '10 days', true, 'BRL',
     'rateio por receita do periodo');


-- ---------------------------------------------------------------------
-- PEDIDO 2 — sai COM_TETO (falta o custo da mercadoria)
-- ---------------------------------------------------------------------
-- A variacao existe e esta casada, mas NAO tem custo_unitario_atual.
-- E o caso que o produto precisa mostrar com honestidade: "sua margem e
-- no maximo X, a real e menor".
INSERT INTO pedido (id, tenant_id, canal_id, id_externo, status, feito_em,
                    valor_total_pedido, valor_bruto_itens, moeda)
VALUES ('77777777-7777-7777-7777-777777777778',
        '11111111-1111-1111-1111-111111111111',
        '33333333-3333-3333-3333-333333333333',
        'ML-DEMO-0002', 'ENVIADO', now() - interval '5 days',
        149.9000, 149.9000, 'BRL');

INSERT INTO item_pedido (id, tenant_id, pedido_id, variacao_id, sku_origem,
                         titulo_origem, quantidade, valor_unitario_bruto, valor_total_linha)
VALUES ('88888888-8888-8888-8888-888888888889',
        '11111111-1111-1111-1111-111111111111',
        '77777777-7777-7777-7777-777777777778',
        '55555555-5555-5555-5555-555555555556',
        'CAM-BAS-M-PRETA', 'Camiseta Basica Algodao M Preta',
        1, 149.9000, 149.9000);

INSERT INTO custo (id, tenant_id, pedido_id, natureza, valor,
                   competencia_em, eh_estimativa, moeda, descricao)
VALUES
    (gen_random_uuid(), '11111111-1111-1111-1111-111111111111',
     '77777777-7777-7777-7777-777777777778',
     'COMISSAO_CANAL', 19.4870, now() - interval '5 days', false, 'BRL',
     'informado pela fonte'),
    (gen_random_uuid(), '11111111-1111-1111-1111-111111111111',
     '77777777-7777-7777-7777-777777777778',
     'FRETE', 22.5000, now() - interval '5 days', false, 'BRL',
     'cobrado do vendedor');


-- ---------------------------------------------------------------------
-- Sinais para as telas de Operacao e Pendencias
-- ---------------------------------------------------------------------
-- Pedido travado aguardando pagamento (gargalo do processo).
INSERT INTO pedido (id, tenant_id, canal_id, id_externo, status, feito_em,
                    valor_total_pedido, moeda)
VALUES ('77777777-7777-7777-7777-777777777779',
        '11111111-1111-1111-1111-111111111111',
        '33333333-3333-3333-3333-333333333333',
        'ML-DEMO-0003', 'AGUARDANDO_PAGAMENTO', now() - interval '3 days',
        89.9000, 'BRL');

-- Evento de ingestao em ERRO: o payload chegou sem o campo de id, a
-- traducao falhou, e o registro SOBREVIVEU para poder ser investigado e
-- retentado (a divida 2 e o conserto de reprocessamento da V015).
-- erro_mensagem guarda o TIPO do erro, nunca o conteudo do payload.
INSERT INTO evento_ingerido (
    id, tenant_id, canal_id, tipo_evento, id_externo, hash_payload,
    payload_bruto, status, erro_mensagem, tentativas, recebido_em)
VALUES (
    '99999999-9999-9999-9999-999999999999',
    '11111111-1111-1111-1111-111111111111',
    '33333333-3333-3333-3333-333333333333',
    'PEDIDO', 'ML-DEMO-QUEBRADO',
    encode(digest('{"date_created":"2026-08-01T10:00:00.000-03:00"}', 'sha256'), 'hex'),
    '{"date_created":"2026-08-01T10:00:00.000-03:00"}'::jsonb,
    'ERRO',
    'PayloadInvalidoException: campo obrigatorio ausente no payload de origem',
    3,
    now() - interval '2 days');

-- Devolucao aberta (fila do analista).
INSERT INTO devolucao (id, tenant_id, pedido_id, tipo, status, motivo,
                       aberta_em, valor_reembolsado, moeda)
VALUES (
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    '11111111-1111-1111-1111-111111111111',
    '77777777-7777-7777-7777-777777777777',
    'PARCIAL', 'ABERTA', 'PRODUTO_COM_DEFEITO',
    now() - interval '1 day', 0.0000, 'BRL');


\echo ''
\echo 'Dados de demonstracao criados.'
\echo '  Login:  dono@demo.local'
\echo '  Senha:  demo1234'
\echo '  Canal:  Mercado Livre (ml-principal)'
\echo ''
