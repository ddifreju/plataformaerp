-- =====================================================================
-- DADOS DE DEMONSTRACAO — SOMENTE DESENVOLVIMENTO
-- =====================================================================
-- Popula um tenant ficticio com volume suficiente para as tres telas
-- ficarem interessantes: 3 canais, catalogo com variacoes, ~50 pedidos
-- espalhados por 90 dias, devolucoes com motivo, eventos de ingestao em
-- erro, e os TRES rotulos de margem representados de proposito.
--
-- ISTO NAO E MIGRATION. Vive em infra/, fora de db/migration/, e o
-- Flyway nunca o enxerga. Migration descreve ESTRUTURA; isto e CONTEUDO
-- descartavel. Se fosse migration, seria aplicado em producao no
-- primeiro deploy - e nao existe desfazer para dado que um cliente ja
-- viu. Ver docs/decisoes/0028-dados-de-demonstracao.md.
--
-- ---------------------------------------------------------------------
-- AS DUAS TRAVAS QUE IMPEDEM ISTO DE RODAR EM PRODUCAO
-- ---------------------------------------------------------------------
-- 1. Exige a variavel :confirmo. Sem ela o psql aborta na primeira
--    linha. Nenhuma execucao acidental funciona.
-- 2. Recusa rodar se encontrar QUALQUER tenant que nao seja de demo.
--    Um banco com cliente de verdade tem tenant de verdade; achar um e
--    prova suficiente de que este banco nao e descartavel.
--
-- DATAS SAO RELATIVAS a CURRENT_DATE: a demonstracao nao envelhece.
-- Rodar daqui a seis meses produz pedidos dos ultimos 90 dias, nao de
-- um passado morto.
--
-- DETERMINISTICO: nada de random(). A variacao vem de aritmetica sobre o
-- indice do pedido, entao rodar duas vezes produz exatamente o mesmo
-- conjunto - o que permite conferir um numero na tela hoje e reencontra-lo
-- amanha.
--
-- Idempotente: apaga o tenant de demo e recria.
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
-- Limpeza (idempotencia)
-- ---------------------------------------------------------------------
-- Ordem inversa das dependencias. Este script roda como DONO do schema,
-- que no container tambem e superusuario - por isso enxerga tudo apesar
-- do RLS forcado. Em nenhum outro lugar do sistema isso acontece: a
-- aplicacao usa app_aplicacao, sem BYPASSRLS.
DO $$
DECLARE
    v_ids uuid[];
BEGIN
    SELECT array_agg(id) INTO v_ids FROM tenant WHERE slug LIKE 'demo-%';
    IF v_ids IS NULL THEN RETURN; END IF;

    DELETE FROM custo           WHERE tenant_id = ANY(v_ids);
    DELETE FROM item_devolucao  WHERE tenant_id = ANY(v_ids);
    DELETE FROM devolucao       WHERE tenant_id = ANY(v_ids);
    DELETE FROM item_pedido     WHERE tenant_id = ANY(v_ids);
    DELETE FROM pedido          WHERE tenant_id = ANY(v_ids);
    DELETE FROM mensagem        WHERE tenant_id = ANY(v_ids);
    DELETE FROM conversa        WHERE tenant_id = ANY(v_ids);
    DELETE FROM evento_ingerido WHERE tenant_id = ANY(v_ids);
    DELETE FROM taxa_canal      WHERE tenant_id = ANY(v_ids);
    DELETE FROM variacao        WHERE tenant_id = ANY(v_ids);
    DELETE FROM produto         WHERE tenant_id = ANY(v_ids);
    DELETE FROM cliente         WHERE tenant_id = ANY(v_ids);
    DELETE FROM usuario         WHERE tenant_id = ANY(v_ids);
    DELETE FROM canal           WHERE tenant_id = ANY(v_ids);
    DELETE FROM tenant          WHERE id = ANY(v_ids);
END $$;


-- ---------------------------------------------------------------------
-- Tenant e usuarios
-- ---------------------------------------------------------------------
INSERT INTO tenant (id, nome, slug, ativo)
VALUES ('11111111-1111-1111-1111-111111111111', 'Ateliê Bem Posto', 'demo-loja', true);

-- SENHA gerada aqui pelo pgcrypto: crypt(..., gen_salt('bf', 12)) produz
-- o formato $2a$12$... que o BCryptPasswordEncoder do Spring le direto e
-- que satisfaz ck_usuario_senha_hash_formato (custo >= 12).
-- Nenhum hash fica escrito no arquivo.
--
-- CREDENCIAIS DE DEMONSTRACAO (so existem em banco local):
--   dono@demo.plataforma     / demo1234   (papel DONO)
--   gestor@demo.plataforma   / demo1234   (papel GESTOR)
--   analista@demo.plataforma / demo1234   (papel ANALISTA)
INSERT INTO usuario (id, tenant_id, email, senha_hash, nome, papel, ativo)
VALUES
    ('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111',
     'dono@demo.plataforma', crypt('demo1234', gen_salt('bf', 12)), 'Juliana (dona)', 'DONO', true),
    ('22222222-2222-2222-2222-222222222223', '11111111-1111-1111-1111-111111111111',
     'gestor@demo.plataforma', crypt('demo1234', gen_salt('bf', 12)), 'Marcos (gestor)', 'GESTOR', true),
    ('22222222-2222-2222-2222-222222222224', '11111111-1111-1111-1111-111111111111',
     'analista@demo.plataforma', crypt('demo1234', gen_salt('bf', 12)), 'Rita (analista)', 'ANALISTA', true);


-- ---------------------------------------------------------------------
-- Canais
-- ---------------------------------------------------------------------
-- Dois canais de venda (marketplace) e um ERP. O ERP existe para a tela
-- deixar visivel que ele NAO e somado aos outros: a decisao 0017 proibe
-- somar canais que podem espelhar a mesma venda.
INSERT INTO canal (id, tenant_id, codigo, nome, tipo, categoria, ativo)
VALUES
    ('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111',
     'ml-classico', 'Mercado Livre — Clássico', 'MERCADO_LIVRE', 'MARKETPLACE', true),
    ('33333333-3333-3333-3333-333333333334', '11111111-1111-1111-1111-111111111111',
     'ml-premium', 'Mercado Livre — Premium', 'MERCADO_LIVRE', 'MARKETPLACE', true),
    ('33333333-3333-3333-3333-333333333335', '11111111-1111-1111-1111-111111111111',
     'bling-erp', 'Bling (ERP)', 'ERP_BLING', 'ERP', true);


-- ---------------------------------------------------------------------
-- Taxas vigentes por canal (nivel 2 da hierarquia, decisao 0019)
-- ---------------------------------------------------------------------
-- Curinga '*' em categoria e tipo de anuncio: sentinela, nunca NULL
-- (decisao 0019 - com NULL o EXCLUDE nao protegeria contra sobreposicao).
-- Premium cobra mais que Classico, como no ML real.
INSERT INTO taxa_canal (
    id, tenant_id, canal_id, tipo_taxa, natureza_custo, base_incidencia,
    categoria_canal, tipo_anuncio, percentual, vigencia_inicio,
    confianca, origem, moeda)
VALUES
    ('66666666-6666-6666-6666-666666666666', '11111111-1111-1111-1111-111111111111',
     '33333333-3333-3333-3333-333333333333', 'COMISSAO', 'COMISSAO_CANAL', 'VALOR_TOTAL_PEDIDO',
     '*', '*', 0.130000, CURRENT_DATE - 365, 'INFORMADO_PELO_LOJISTA', 'CADASTRO_MANUAL', 'BRL'),
    ('66666666-6666-6666-6666-666666666667', '11111111-1111-1111-1111-111111111111',
     '33333333-3333-3333-3333-333333333334', 'COMISSAO', 'COMISSAO_CANAL', 'VALOR_TOTAL_PEDIDO',
     '*', '*', 0.170000, CURRENT_DATE - 365, 'INFORMADO_PELO_LOJISTA', 'CADASTRO_MANUAL', 'BRL');


-- ---------------------------------------------------------------------
-- Catalogo
-- ---------------------------------------------------------------------
INSERT INTO produto (id, tenant_id, titulo, marca, ativo) VALUES
    ('44444444-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Camiseta Algodão Pima',        'Bem Posto', true),
    ('44444444-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'Calça Alfaiataria Linho',      'Bem Posto', true),
    ('44444444-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'Vestido Midi Viscose',         'Bem Posto', true),
    ('44444444-0000-0000-0000-000000000004', '11111111-1111-1111-1111-111111111111', 'Jaqueta Sarja Oversized',      'Bem Posto', true),
    ('44444444-0000-0000-0000-000000000005', '11111111-1111-1111-1111-111111111111', 'Blusa Tricot Canelado',        'Bem Posto', true),
    ('44444444-0000-0000-0000-000000000006', '11111111-1111-1111-1111-111111111111', 'Saia Plissada Midi',           'Bem Posto', true),
    ('44444444-0000-0000-0000-000000000007', '11111111-1111-1111-1111-111111111111', 'Kit 3 Meias Cano Alto',        'Bem Posto', true),
    ('44444444-0000-0000-0000-000000000008', '11111111-1111-1111-1111-111111111111', 'Bolsa Transversal Couro Eco',  'Bem Posto', true);

-- 16 variacoes. As DUAS ULTIMAS ficam SEM custo_unitario_atual de
-- proposito: sao elas que produzem os pedidos COM_TETO (lacuna
-- "custo_mercadoria_nao_cadastrado", vies SUPERESTIMA_MARGEM) e povoam a
-- fila do analista com "variacao sem custo cadastrado".
INSERT INTO variacao (id, tenant_id, produto_id, sku, descricao_variacao, preco_venda_atual, custo_unitario_atual, moeda, ativo)
SELECT
    ('55555555-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
    '11111111-1111-1111-1111-111111111111',
    ('44444444-0000-0000-0000-' || lpad((((n - 1) / 2) + 1)::text, 12, '0'))::uuid,
    'SKU-' || lpad(n::text, 3, '0'),
    CASE WHEN n % 2 = 1 THEN 'Tamanho M' ELSE 'Tamanho G' END,
    precos.preco,
    CASE WHEN n <= 14 THEN round(precos.preco * 0.42, 4) ELSE NULL END,
    'BRL', true
FROM generate_series(1, 16) AS n
CROSS JOIN LATERAL (
    SELECT (79.90 + ((n * 17) % 22) * 10)::numeric(18,4) AS preco
) AS precos;


-- ---------------------------------------------------------------------
-- Clientes
-- ---------------------------------------------------------------------
-- Dado pessoal claramente ficticio. O documento NUNCA e guardado em
-- claro: a coluna e documento_hash (HMAC na aplicacao). Aqui gravamos so
-- o mascarado, que e o que a interface mostra.
INSERT INTO cliente (id, tenant_id, canal_id, id_externo, tipo, nome, email, documento_tipo, documento_mascarado)
SELECT
    ('cccccccc-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
    '11111111-1111-1111-1111-111111111111',
    '33333333-3333-3333-3333-333333333333',
    'BUYER-' || lpad(n::text, 4, '0'),
    'PESSOA_FISICA',
    (ARRAY['Ana Souza','Bruno Lima','Carla Dias','Diego Alves','Elisa Rocha',
           'Fábio Nunes','Gabriela Melo','Henrique Sá','Isabel Prado','João Vieira',
           'Karina Ramos','Lucas Pinto'])[((n - 1) % 12) + 1],
    'comprador' || n || '@exemplo.invalido',
    'CPF',
    '***.' || lpad(((n * 137) % 1000)::text, 3, '0') || '.***-**'
FROM generate_series(1, 12) AS n;


-- =====================================================================
-- PEDIDOS — 50, espalhados por 90 dias
-- =====================================================================
-- Distribuicao dos rotulos, escolhida para as tres telas terem o que
-- mostrar (ver docs/marca/guia-de-interface.md):
--
--   n % 7 = 0  (7 pedidos)  -> INDETERMINADA: valor_repasse_previsto NULL,
--                             que dispara a lacuna #17 (vies INDETERMINADA)
--   n % 5 = 0  (8 pedidos)  -> COM_TETO: item de variacao SEM custo
--                             cadastrado, entao falta custo(MERCADORIA)
--                             (vies SUPERESTIMA_MARGEM, e so ele)
--   demais    (35 pedidos)  -> CALCULADA: mercadoria + imposto + repasse
--                             conferindo, sem nenhuma lacuna
--
-- A regra esta em RotuloTeto.calcular: sem lacuna e CALCULADA; todas
-- para cima e COM_TETO; qualquer indeterminada contamina o conjunto.
-- =====================================================================
INSERT INTO pedido (
    id, tenant_id, canal_id, cliente_id, id_externo, codigo_exibicao,
    status, feito_em, pago_em, valor_bruto_itens, valor_frete_cobrado,
    valor_total_pedido, valor_repasse_previsto, moeda, forma_pagamento,
    cidade_entrega, uf_entrega)
SELECT
    p.pedido_id,
    '11111111-1111-1111-1111-111111111111',
    p.canal_id,
    ('cccccccc-0000-0000-0000-' || lpad((((p.n - 1) % 12) + 1)::text, 12, '0'))::uuid,
    'ML-' || lpad(p.n::text, 5, '0'),
    'PED-' || lpad(p.n::text, 4, '0'),
    p.status,
    p.feito_em,
    CASE WHEN p.status <> 'AGUARDANDO_PAGAMENTO' THEN p.feito_em + interval '2 hours' END,
    p.valor_total,
    0.0000,
    p.valor_total,
    -- Repasse = N0 - comissao - frete do vendedor (secao 2.6 do
    -- documento fiscal). NULL nos pedidos que devem sair INDETERMINADA.
    CASE WHEN p.n % 7 = 0 THEN NULL
         ELSE round(p.valor_total - p.comissao - p.frete, 4) END,
    'BRL',
    (ARRAY['PIX','CARTAO_CREDITO','BOLETO'])[(p.n % 3) + 1],
    (ARRAY['São Paulo','Belo Horizonte','Curitiba','Recife','Porto Alegre'])[(p.n % 5) + 1],
    (ARRAY['SP','MG','PR','PE','RS'])[(p.n % 5) + 1]
FROM (
    SELECT
        n,
        ('77777777-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid AS pedido_id,
        -- Distribuicao entre os tres canais. O Bling recebe poucos
        -- pedidos, de venda propria: sao pedidos DELE, nao espelho dos
        -- do Mercado Livre - por isso somar canais continua proibido
        -- (decisao 0017), mas o canal nao aparece vazio no seletor.
        CASE WHEN n % 8 = 0 THEN '33333333-3333-3333-3333-333333333335'::uuid
             WHEN n % 3 = 0 THEN '33333333-3333-3333-3333-333333333334'::uuid
             ELSE                '33333333-3333-3333-3333-333333333333'::uuid END AS canal_id,
        -- Espalha por 90 dias, mais denso no passado recente.
        (CURRENT_DATE - ((n * 89 / 50) % 90) - (n % 3))::timestamptz
            + ((n * 7) % 24) * interval '1 hour' AS feito_em,
        CASE
            WHEN n % 11 = 0 THEN 'AGUARDANDO_PAGAMENTO'
            WHEN n % 13 = 0 THEN 'CANCELADO'
            WHEN n % 4  = 0 THEN 'ENVIADO'
            WHEN n % 4  = 1 THEN 'ENTREGUE'
            WHEN n % 4  = 2 THEN 'EM_SEPARACAO'
            ELSE 'PAGO'
        END AS status,
        v.valor_total,
        round(v.valor_total * CASE WHEN n % 3 = 0 THEN 0.170 ELSE 0.130 END, 4) AS comissao,
        round(18.90 + (n % 4) * 3.50, 4) AS frete
    FROM generate_series(1, 50) AS n
    CROSS JOIN LATERAL (
        -- Ticket entre R$ 79,90 e ~R$ 299,90: faixa plausivel de moda
        -- no e-commerce brasileiro.
        -- O multiplicador (7) precisa ser COPRIMO do modulo (23), senao
        -- o resto e sempre o mesmo e todo pedido sai com valor
        -- identico - foi o que aconteceu na primeira versao, com
        -- (n * 23) % 23, que e zero para todo n.
        SELECT (79.90 + ((n * 7) % 23) * 10)::numeric(18,4) AS valor_total
    ) AS v
) AS p;


-- ---------------------------------------------------------------------
-- Itens dos pedidos
-- ---------------------------------------------------------------------
-- Um item por pedido, mantendo a leitura simples. Os pedidos que devem
-- sair COM_TETO recebem uma das duas variacoes SEM custo (15 ou 16).
INSERT INTO item_pedido (
    id, tenant_id, pedido_id, variacao_id, sku_origem, titulo_origem,
    quantidade, valor_unitario_bruto, valor_total_linha)
SELECT
    ('88888888-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
    '11111111-1111-1111-1111-111111111111',
    ('77777777-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
    var.variacao_id,
    'SKU-' || lpad(var.indice::text, 3, '0'),
    'Item do pedido PED-' || lpad(n::text, 4, '0'),
    1,
    ped.valor_total_pedido,
    ped.valor_total_pedido
FROM generate_series(1, 50) AS n
JOIN pedido ped ON ped.id = ('77777777-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid
CROSS JOIN LATERAL (
    SELECT idx AS indice,
           -- variacao_id NULO em 1 a cada 17 pedidos: simula o SKU que
           -- chegou do canal e NAO casou com nada do catalogo. E um caso
           -- real e frequente (SKU renomeado, anuncio antigo), e alimenta
           -- a linha "item sem variação casada" na fila do analista.
           -- Tambem dispara a lacuna item_sem_variacao no motor.
           CASE WHEN n % 17 = 0 THEN NULL
                ELSE ('55555555-0000-0000-0000-' || lpad(idx::text, 12, '0'))::uuid
           END AS variacao_id
    FROM (SELECT CASE WHEN n % 5 = 0
                      THEN 15 + (n % 2)          -- variacoes SEM custo
                      ELSE ((n * 3) % 14) + 1    -- variacoes COM custo
                 END AS idx) AS escolha
) AS var;


-- ---------------------------------------------------------------------
-- Custos por pedido
-- ---------------------------------------------------------------------
-- MERCADORIA: so onde a variacao tem custo cadastrado. Onde nao tem, a
-- linha simplesmente NAO EXISTE - e a lacuna e declarada pelo motor.
-- Custo zero seria mentira, e mentira otimista (regra 5 do CLAUDE.md).
INSERT INTO custo (id, tenant_id, pedido_id, item_pedido_id, natureza, valor,
                   competencia_em, eh_estimativa, moeda, descricao)
SELECT
    gen_random_uuid(), '11111111-1111-1111-1111-111111111111',
    ip.pedido_id, ip.id, 'MERCADORIA',
    round(v.custo_unitario_atual * ip.quantidade, 4),
    ped.feito_em, false, 'BRL',
    'custo_unitario_atual congelado na ingestao'
FROM item_pedido ip
JOIN variacao v  ON v.id = ip.variacao_id
JOIN pedido  ped ON ped.id = ip.pedido_id
WHERE ip.tenant_id = '11111111-1111-1111-1111-111111111111'
  AND v.custo_unitario_atual IS NOT NULL;

-- COMISSAO do canal: informada pela fonte (nao e estimativa).
INSERT INTO custo (id, tenant_id, pedido_id, natureza, valor,
                   competencia_em, eh_estimativa, moeda, descricao)
SELECT
    gen_random_uuid(), '11111111-1111-1111-1111-111111111111',
    p.id, 'COMISSAO_CANAL',
    round(p.valor_total_pedido * CASE WHEN p.canal_id = '33333333-3333-3333-3333-333333333334'
                                      THEN 0.170 ELSE 0.130 END, 4),
    p.feito_em, false, 'BRL',
    'informada pela fonte na fatura do canal'
FROM pedido p
WHERE p.tenant_id = '11111111-1111-1111-1111-111111111111';

-- FRETE cobrado do vendedor.
INSERT INTO custo (id, tenant_id, pedido_id, natureza, valor,
                   competencia_em, eh_estimativa, moeda, descricao)
SELECT
    gen_random_uuid(), '11111111-1111-1111-1111-111111111111',
    p.id, 'FRETE',
    round(18.90 + (('x' || substr(md5(p.id::text), 1, 8))::bit(32)::bigint % 4) * 3.50, 4),
    p.feito_em, false, 'BRL',
    'frete cobrado do vendedor'
FROM pedido p
WHERE p.tenant_id = '11111111-1111-1111-1111-111111111111';

-- IMPOSTO: estimativa (Simples Anexo I). Sem esta linha, TODO pedido
-- dispararia "regime_tributario_nao_configurado" e nenhum sairia
-- CALCULADA.
INSERT INTO custo (id, tenant_id, pedido_id, natureza, valor,
                   competencia_em, eh_estimativa, moeda, descricao)
SELECT
    gen_random_uuid(), '11111111-1111-1111-1111-111111111111',
    p.id, 'IMPOSTO', round(p.valor_total_pedido * 0.06728, 4),
    p.feito_em, true, 'BRL',
    'Simples Nacional Anexo I, alíquota efetiva 6,728%'
FROM pedido p
WHERE p.tenant_id = '11111111-1111-1111-1111-111111111111';

-- EMBALAGEM: estimativa por item.
INSERT INTO custo (id, tenant_id, pedido_id, natureza, valor,
                   competencia_em, eh_estimativa, moeda, descricao)
SELECT
    gen_random_uuid(), '11111111-1111-1111-1111-111111111111',
    p.id, 'EMBALAGEM', 1.8000, p.feito_em, true, 'BRL', 'rateio por item'
FROM pedido p
WHERE p.tenant_id = '11111111-1111-1111-1111-111111111111';


-- ---------------------------------------------------------------------
-- Devolucoes (fila do analista e custo de frete reverso)
-- ---------------------------------------------------------------------
INSERT INTO devolucao (
    id, tenant_id, pedido_id, canal_id, tipo, status, motivo,
    aberta_em, valor_reembolsado, valor_frete_reverso,
    responsavel_frete_reverso, destino_produto, moeda)
SELECT
    ('aaaaaaaa-0000-0000-0000-' || lpad(d.n::text, 12, '0'))::uuid,
    '11111111-1111-1111-1111-111111111111',
    p.id, p.canal_id,
    CASE WHEN d.n % 2 = 0 THEN 'PARCIAL' ELSE 'TOTAL' END,
    (ARRAY['ABERTA','ABERTA','EM_ANALISE','EM_TRANSITO','CONCLUIDA','ABERTA'])[d.n],
    (ARRAY['PRODUTO_COM_DEFEITO','ARREPENDIMENTO','PRODUTO_DIFERENTE_DO_ANUNCIO',
           'AVARIA_NO_TRANSPORTE','NAO_ENTREGUE','ARREPENDIMENTO'])[d.n],
    p.feito_em + interval '6 days',
    CASE WHEN d.n % 2 = 0 THEN round(p.valor_total_pedido / 2, 4) ELSE p.valor_total_pedido END,
    round(17.50 + d.n, 4),
    'VENDEDOR',
    CASE WHEN d.n = 5 THEN 'ESTOQUE' ELSE 'NAO_RETORNOU' END,
    'BRL'
FROM generate_series(1, 6) AS d(n)
JOIN pedido p ON p.id = ('77777777-0000-0000-0000-' || lpad((d.n * 6)::text, 12, '0'))::uuid;

-- O frete reverso e custo real do pedido: entra como linha negativa de
-- margem, e a soma dos blocos ja o absorve sem tratamento especial.
INSERT INTO custo (id, tenant_id, pedido_id, devolucao_id, natureza, valor,
                   competencia_em, eh_estimativa, moeda, descricao)
SELECT
    gen_random_uuid(), '11111111-1111-1111-1111-111111111111',
    dv.pedido_id, dv.id, 'FRETE_REVERSO', dv.valor_frete_reverso,
    dv.aberta_em, false, 'BRL', 'frete reverso pago pelo vendedor'
FROM devolucao dv
WHERE dv.tenant_id = '11111111-1111-1111-1111-111111111111';


-- ---------------------------------------------------------------------
-- Eventos de ingestao em ERRO (fila do analista)
-- ---------------------------------------------------------------------
-- Sobrevivem a falha de traducao de proposito: sem isso o reenvio
-- identico tentaria para sempre, invisivel. erro_mensagem guarda o TIPO
-- do erro e o campo, NUNCA o conteudo do payload - o payload de
-- marketplace carrega nome, endereco e contato do consumidor final.
INSERT INTO evento_ingerido (
    id, tenant_id, canal_id, tipo_evento, id_externo, hash_payload,
    payload_bruto, status, erro_mensagem, tentativas, recebido_em)
SELECT
    ('99999999-0000-0000-0000-' || lpad(e.n::text, 12, '0'))::uuid,
    '11111111-1111-1111-1111-111111111111',
    CASE WHEN e.n % 2 = 0 THEN '33333333-3333-3333-3333-333333333334'::uuid
         ELSE '33333333-3333-3333-3333-333333333333'::uuid END,
    'PEDIDO',
    'ML-FALHA-' || lpad(e.n::text, 4, '0'),
    encode(digest('payload-demo-' || e.n, 'sha256'), 'hex'),
    ('{"date_created":"2026-01-0' || e.n || 'T10:00:00.000-03:00"}')::jsonb,
    'ERRO',
    (ARRAY[
        'PayloadInvalidoException: campo obrigatório "id" ausente no payload de origem',
        'PayloadInvalidoException: campo "total_amount" deveria ser numérico e veio do tipo STRING',
        'CanalDesconhecidoException: canal informado não pertence a este tenant',
        'PayloadInvalidoException: lista "order_items" vazia'
    ])[e.n],
    e.n,
    (CURRENT_DATE - e.n)::timestamptz + interval '9 hours'
FROM generate_series(1, 4) AS e(n);


-- ---------------------------------------------------------------------
-- Conferencia: o seed produziu os tres rotulos?
-- ---------------------------------------------------------------------
DO $$
DECLARE
    v_total          integer;
    v_sem_repasse    integer;
    v_sem_mercadoria integer;
    v_calculada      integer;
BEGIN
    SELECT count(*) INTO v_total
      FROM pedido WHERE tenant_id = '11111111-1111-1111-1111-111111111111';

    SELECT count(*) INTO v_sem_repasse
      FROM pedido WHERE tenant_id = '11111111-1111-1111-1111-111111111111'
       AND valor_repasse_previsto IS NULL;

    SELECT count(*) INTO v_sem_mercadoria
      FROM pedido p
     WHERE p.tenant_id = '11111111-1111-1111-1111-111111111111'
       AND p.valor_repasse_previsto IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM custo c
                        WHERE c.pedido_id = p.id AND c.natureza = 'MERCADORIA');

    v_calculada := v_total - v_sem_repasse - v_sem_mercadoria;

    RAISE NOTICE 'Pedidos: % (CALCULADA ~%, COM_TETO ~%, INDETERMINADA ~%)',
                 v_total, v_calculada, v_sem_mercadoria, v_sem_repasse;

    -- Falha alto se o seed nao cobrir os tres rotulos: um seed que nao
    -- exercita o que promete e pior que nenhum, porque a tela parece
    -- certa e esconde o caso nao testado.
    IF v_sem_repasse = 0 OR v_sem_mercadoria = 0 OR v_calculada <= 0 THEN
        RAISE EXCEPTION
            'Seed nao cobriu os tres rotulos (calculada=%, com_teto=%, indeterminada=%)',
            v_calculada, v_sem_mercadoria, v_sem_repasse;
    END IF;
END $$;


\echo ''
\echo '  Dados de demonstracao criados.'
\echo ''
\echo '  Login (so em banco local):'
\echo '    dono@demo.plataforma     / demo1234'
\echo '    gestor@demo.plataforma   / demo1234'
\echo '    analista@demo.plataforma / demo1234'
\echo ''
\echo '  3 canais - Mercado Livre Classico, Premium e Bling (ERP)'
\echo '  Escolha UM canal por consulta: somar canais sobrepostos'
\echo '  contaria a mesma venda duas vezes (decisao 0017).'
\echo ''
