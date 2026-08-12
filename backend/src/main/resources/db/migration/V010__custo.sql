-- =====================================================================
-- V010 — custo (o coracao do produto)
-- =====================================================================
-- Convencoes gerais da Fase 1: cabecalho da V005.
--
-- Esta e a tabela de que a Fase 2 inteira depende. "Saber o lucro real"
-- e, no fim, somar linhas daqui. Por isso o desenho gasta mais tempo em
-- COMO SOMAR do que em como gravar.
--
-- ---------------------------------------------------------------------
-- FORMATO: uma linha por parcela de custo, com natureza
-- ---------------------------------------------------------------------
-- Escolhido: tabela LONGA (natureza + valor + vinculo), nao tabela larga
-- (uma coluna por tipo de custo em `pedido`).
--   Contra a tabela larga: custo novo (uma taxa nova do marketplace, um
--   custo de armazenagem do fulfillment) viraria coluna nova em pedido,
--   com migration, backfill e reescrita de toda query de margem. E o
--   Mercado Livre inventa taxa nova todo ano.
--   A favor da longa: natureza nova e uma linha no CHECK; a query de
--   margem nao muda; a memoria de calculo (quanto, sobre o que, com que
--   aliquota, estimado ou nao) cabe em colunas proprias, o que numa
--   tabela larga seria impossivel.
--   Preco aceito: nada de custo e legivel sem GROUP BY. Para a interface,
--   isso vira uma view ou um DTO montado na Fase 2 — nao mais colunas.
--
-- ---------------------------------------------------------------------
-- CONTRATO DE SOMA (memorize, porque errar aqui e contar dinheiro duas
-- vezes)
-- ---------------------------------------------------------------------
-- S1. CUSTO REAL DE UM PEDIDO:
--         SELECT sum(valor) FROM custo WHERE pedido_id = :id
--     E so isso. Custo de nivel de ITEM tambem carrega pedido_id
--     preenchido (ver S3), entao esta soma pega os dois niveis e NUNCA
--     conta duas vezes. Nao existe UNION, nao existe COALESCE, nao existe
--     "somar custo do pedido mais custo dos itens".
--
-- S2. CUSTO DE UM ITEM (margem por SKU):
--         SELECT sum(valor) FROM custo WHERE item_pedido_id = :id
--     Custo que so existe no nivel do pedido (frete, Ads) fica de fora,
--     porque ele nao e atribuivel a um item sem rateio. Quando o rateio
--     for feito, ele gera linha filha com item_pedido_id preenchido.
--
-- S3. REGRA DE PREENCHIMENTO QUE SUSTENTA S1 e S2:
--     item_pedido_id preenchido OBRIGA pedido_id preenchido (ha CHECK).
--     A denormalizacao e proposital: e ela que torna S1 uma unica linha
--     de SQL e um unico indice.
--
-- S4. CUSTO DE PERIODO (P&L do mes, tarefa 16):
--         SELECT sum(valor) FROM custo
--          WHERE competencia_em >= :inicio AND competencia_em < :fim
--            AND rateado_de_custo_id IS NULL
--     O filtro `rateado_de_custo_id IS NULL` exclui as linhas DERIVADAS
--     de rateio. Sem ele, a fatura de Ads de R$ 1.000 seria contada uma
--     vez como linha-mae e outra vez como as N linhas filhas distribuidas
--     entre pedidos. Com ele, cada real aparece exatamente uma vez.
--     As duas somas convivem porque respondem perguntas diferentes:
--     S1 e "quanto custou este pedido"; S4 e "quanto saiu do caixa".
--
-- S5. SINAL: valor POSITIVO reduz a margem (saida). Valor NEGATIVO e
--     estorno/credito — o caso classico e o marketplace devolvendo a
--     comissao quando o pedido e devolvido. Estorno e linha NOVA com
--     valor negativo, nunca UPDATE apagando a original: a linha original
--     e o que aconteceu, e o historico da margem tem que continuar
--     explicavel. Zero e permitido e significativo ("o frete custou zero
--     e nos sabemos disso"), diferente de nao ter linha nenhuma ("nao
--     sabemos quanto custou").
--
-- S6. RECEITA NAO ENTRA AQUI. Reembolso ao comprador e CUSTO
--     (natureza REEMBOLSO), nao receita negativa em pedido. Ver R2 no
--     cabecalho da V008. Um lugar so para dinheiro que sai.
-- =====================================================================

CREATE TABLE custo (
    id                    uuid        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id             uuid        NOT NULL,

    natureza              text        NOT NULL,

    -- ------------------------------------------------------------------
    -- Vinculos. Todos opcionais, com regras no CHECK mais abaixo.
    -- ------------------------------------------------------------------
    -- pedido_id NULL = custo de PERIODO, ainda nao atribuido a nenhuma
    -- venda (fatura de Ads do mes, compra de embalagem, mensalidade de
    -- ferramenta). Entra em S4, nunca em S1.
    pedido_id             uuid,
    -- item_pedido_id preenchido exige pedido_id preenchido (S3).
    item_pedido_id        uuid,
    -- devolucao_id: liga o custo ao evento que o causou. E o que permite
    -- responder "quanto as devolucoes me custaram" sem heuristica.
    devolucao_id          uuid,

    -- ------------------------------------------------------------------
    -- Dinheiro. NUMERIC(18,4) (convencao 3 da V005). Sinal: ver S5.
    -- ------------------------------------------------------------------
    valor                 numeric(18,4) NOT NULL,
    moeda                 char(3)     NOT NULL DEFAULT 'BRL',

    -- competencia_em: a QUE PERIODO este custo pertence. Nao e criado_em
    -- (quando gravamos) nem a data do pedido: a comissao do ML e cobrada
    -- na venda, mas a fatura de Ads e do mes, e o frete reverso acontece
    -- semanas depois. Sem uma data propria de competencia, "quanto sobrou
    -- em marco" seria uma pergunta sem resposta estavel.
    competencia_em        timestamptz NOT NULL,

    -- ------------------------------------------------------------------
    -- MEMORIA DE CALCULO — regras 3 e 5 do CLAUDE.md dentro do schema
    -- ------------------------------------------------------------------
    -- eh_estimativa: TRUE quando o numero foi calculado por nos e nao
    -- informado pela fonte. Regra 5: "se for estimativa, diga que e".
    -- Esta coluna e o que permite a interface escrever "R$ 12,40
    -- (estimado)" em vez de apresentar palpite como fato. Nenhuma tela
    -- deve exibir soma de custo sem saber quanto dela e estimativa.
    eh_estimativa         boolean     NOT NULL DEFAULT false,

    -- base_calculo e aliquota_aplicada: o "12,5% sobre R$ 199,90" que
    -- gerou o valor. Guardados porque a pergunta que o cliente faz e
    -- "por que voces dizem que paguei R$ 24,99 de comissao?" e a resposta
    -- tem que sair do banco, nao de uma reexecucao do calculo com o
    -- codigo de hoje sobre um pedido de ano passado.
    base_calculo          numeric(18,4),
    -- FRACAO DECIMAL: 0.125000 = 12,5%. Nunca 12.5. Ambiguidade de
    -- percentual e fonte classica de erro por fator 100.
    aliquota_aplicada     numeric(9,6),

    -- metodo_rateio: como o valor foi distribuido, quando foi.
    -- Ex.: 'ADS_POR_RECEITA_DO_PERIODO', 'FRETE_POR_PESO',
    -- 'EMBALAGEM_POR_ITEM'. text livre de proposito: metodo de rateio e
    -- experimentacao da Fase 2, e fechar o dominio agora congelaria a
    -- discussao antes de ela acontecer.
    metodo_rateio         text,

    -- rateado_de_custo_id: aponta para a linha-mae de periodo que gerou
    -- esta linha. E o que torna S4 correto (ver contrato de soma).
    -- Auto-referencia com FK composta, mesmo padrao das demais.
    rateado_de_custo_id   uuid,

    descricao             text,

    -- ------------------------------------------------------------------
    -- Origem (convencao 4 da V005). canal_id NULL + eh_estimativa TRUE e
    -- o retrato de um custo que NOS calculamos; canal_id preenchido e um
    -- custo que a fonte informou.
    -- ------------------------------------------------------------------
    canal_id              uuid,
    id_externo            text,
    dados_origem          jsonb       NOT NULL DEFAULT '{}'::jsonb,
    sincronizado_em       timestamptz,
    criado_em             timestamptz NOT NULL DEFAULT now(),
    atualizado_em         timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_custo PRIMARY KEY (id),
    CONSTRAINT uq_custo_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_custo_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_custo_pedido
        FOREIGN KEY (tenant_id, pedido_id) REFERENCES pedido (tenant_id, id),
    CONSTRAINT fk_custo_item_pedido
        FOREIGN KEY (tenant_id, item_pedido_id) REFERENCES item_pedido (tenant_id, id),
    CONSTRAINT fk_custo_devolucao
        FOREIGN KEY (tenant_id, devolucao_id) REFERENCES devolucao (tenant_id, id),
    CONSTRAINT fk_custo_canal
        FOREIGN KEY (tenant_id, canal_id) REFERENCES canal (tenant_id, id),
    -- Auto-referencia: o alvo e a propria uq_custo_tenant_id declarada
    -- acima, o que o Postgres aceita dentro do mesmo CREATE TABLE.
    CONSTRAINT fk_custo_rateado_de
        FOREIGN KEY (tenant_id, rateado_de_custo_id) REFERENCES custo (tenant_id, id),

    -- Dominio de natureza. Cada valor aqui e um custo real do e-commerce
    -- brasileiro, nao uma categoria teorica:
    --   MERCADORIA          custo de aquisicao do que foi vendido
    --   EMBALAGEM           caixa, plastico, etiqueta
    --   FRETE               envio ao comprador (diferente do que ele pagou)
    --   FRETE_REVERSO       retorno na devolucao
    --   COMISSAO_CANAL      percentual do marketplace
    --   TARIFA_FIXA_CANAL   tarifa por unidade em item de valor baixo
    --                       (o ML cobra por item abaixo de certo valor;
    --                       ignorar isso distorce a margem justamente na
    --                       faixa em que ela e mais apertada)
    --   TAXA_PAGAMENTO      gateway / meio de pagamento
    --   TAXA_PARCELAMENTO   custo de vender em N vezes
    --   TAXA_ANTECIPACAO    custo de receber antes do prazo
    --   IMPOSTO             tributo sobre a venda. A ALIQUOTA vem de
    --                       tabela versionada por vigencia (Fase 2,
    --                       tarefa 13) porque a reforma CBS/IBS muda o
    --                       calculo ano a ano entre 2026 e 2033; aqui
    --                       guardamos so o valor apurado e a aliquota que
    --                       foi aplicada
    --   ADS                 publicidade, tipicamente rateada
    --   DESCONTO_CONCEDIDO  parte do desconto bancada pela loja
    --   REEMBOLSO           devolvido ao comprador (ver S6)
    --   ARMAZENAGEM         fulfillment, estoque parado
    --   TARIFA_ADMINISTRATIVA  mensalidade de plano/ferramenta
    --   OUTRO               escape hatch consciente; aparecer em producao
    --                       e sinal de natureza faltando
    CONSTRAINT ck_custo_natureza CHECK (natureza IN (
        'MERCADORIA',
        'EMBALAGEM',
        'FRETE',
        'FRETE_REVERSO',
        'COMISSAO_CANAL',
        'TARIFA_FIXA_CANAL',
        'TAXA_PAGAMENTO',
        'TAXA_PARCELAMENTO',
        'TAXA_ANTECIPACAO',
        'IMPOSTO',
        'ADS',
        'DESCONTO_CONCEDIDO',
        'REEMBOLSO',
        'ARMAZENAGEM',
        'TARIFA_ADMINISTRATIVA',
        'OUTRO'
    )),

    -- S3: custo de item SEMPRE carrega o pedido. E o que faz a soma S1
    -- funcionar com um WHERE so.
    CONSTRAINT ck_custo_item_exige_pedido
        CHECK (item_pedido_id IS NULL OR pedido_id IS NOT NULL),
    -- Linha derivada de rateio existe para atribuir custo a uma venda.
    -- Rateio que nao chega a um pedido nao rateou nada.
    CONSTRAINT ck_custo_rateio_exige_pedido
        CHECK (rateado_de_custo_id IS NULL OR pedido_id IS NOT NULL),
    -- Linha nao pode ser mae de si mesma.
    CONSTRAINT ck_custo_rateio_nao_autorreferente
        CHECK (rateado_de_custo_id IS NULL OR rateado_de_custo_id <> id),
    -- Aliquota e fracao decimal: 0.125 = 12,5%. O limite superior de 1
    -- (=100%) nao e imposto porque existe multa e taxa acima de 100% em
    -- casos raros; o limite inferior sim, aliquota negativa e sempre erro.
    CONSTRAINT ck_custo_aliquota_nao_negativa
        CHECK (aliquota_aplicada IS NULL OR aliquota_aplicada >= 0),
    CONSTRAINT ck_custo_moeda CHECK (moeda ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_custo_id_externo_nao_vazio
        CHECK (id_externo IS NULL OR btrim(id_externo) <> '')
    -- NAO ha CHECK exigindo valor <> 0: custo zero informado pela fonte e
    -- informacao ("o frete custou zero"), diferente de ausencia de linha
    -- ("nao sabemos quanto custou"). Ver S5.
);

COMMENT ON TABLE  custo IS
    'Toda parcela de dinheiro que sai, ligada ou nao a um pedido. Base do calculo de margem (Fase 2). Soma por pedido: WHERE pedido_id = X. Soma por periodo: WHERE competencia_em ... AND rateado_de_custo_id IS NULL.';
COMMENT ON COLUMN custo.tenant_id IS
    'Parte da identidade do registro. Sem DEFAULT de proposito: bug de contexto deve virar erro, nao linha gravada.';
COMMENT ON COLUMN custo.natureza IS
    'Tipo do custo. Dominio fechado por CHECK; natureza nova exige migration, o que e desejavel: custo novo e mudanca de modelo de margem.';
COMMENT ON COLUMN custo.pedido_id IS
    'NULL = custo de periodo ainda nao atribuido a venda. Custo de item SEMPRE preenche esta coluna tambem (ha CHECK): e o que faz a soma por pedido ser um unico WHERE.';
COMMENT ON COLUMN custo.devolucao_id IS
    'Evento que causou o custo, quando houve. Permite responder "quanto as devolucoes custaram" por join, sem heuristica.';
COMMENT ON COLUMN custo.valor IS
    'Positivo reduz a margem (saida). Negativo e estorno/credito, gravado como linha NOVA - nunca UPDATE apagando a original.';
COMMENT ON COLUMN custo.competencia_em IS
    'Periodo a que o custo pertence. Diferente de criado_em e da data do pedido: comissao e da venda, Ads e do mes, frete reverso e de semanas depois.';
COMMENT ON COLUMN custo.eh_estimativa IS
    'TRUE quando o numero foi calculado por nos, nao informado pela fonte (regra 5 do CLAUDE.md). Nenhuma tela deve somar custo sem saber quanto e estimativa.';
COMMENT ON COLUMN custo.base_calculo IS
    'Sobre que valor a aliquota incidiu. Junto com aliquota_aplicada, e a memoria de calculo que responde "por que este numero?" anos depois.';
COMMENT ON COLUMN custo.aliquota_aplicada IS
    'FRACAO DECIMAL: 0.125000 = 12,5%. Nunca 12.5. Ambiguidade de percentual e erro por fator 100 esperando acontecer.';
COMMENT ON COLUMN custo.rateado_de_custo_id IS
    'Linha-mae de periodo que originou esta linha por rateio. Somar periodo com rateado_de_custo_id IS NULL evita contar o mesmo real duas vezes.';
COMMENT ON COLUMN custo.metodo_rateio IS
    'Como o valor foi distribuido (ADS_POR_RECEITA_DO_PERIODO, FRETE_POR_PESO...). text livre: metodo de rateio e experimentacao da Fase 2.';
COMMENT ON COLUMN custo.dados_origem IS
    'Campo de extensao (decisao 0002): detalhe da fonte sobre este custo (id da fatura, linha do extrato) sem poluir o modelo canonico.';

-- ---------------------------------------------------------------------
-- Indices — desenhados a partir do contrato de soma acima
-- ---------------------------------------------------------------------
-- S1: custo real de um pedido. Parcial porque custo de periodo nunca e
-- alcancado por este caminho.
CREATE INDEX ix_custo_tenant_pedido
    ON custo (tenant_id, pedido_id)
    WHERE pedido_id IS NOT NULL;

-- S2: custo de um item (margem por SKU).
CREATE INDEX ix_custo_tenant_item_pedido
    ON custo (tenant_id, item_pedido_id)
    WHERE item_pedido_id IS NOT NULL;

-- S4: P&L do periodo, com decomposicao por natureza. natureza vem depois
-- da data porque o filtro de periodo e sempre presente e o de natureza,
-- nao.
CREATE INDEX ix_custo_tenant_competencia_natureza
    ON custo (tenant_id, competencia_em, natureza);

-- "quanto as devolucoes custaram".
CREATE INDEX ix_custo_tenant_devolucao
    ON custo (tenant_id, devolucao_id)
    WHERE devolucao_id IS NOT NULL;

-- Idempotencia: a mesma cobranca da mesma fonte nao vira duas linhas de
-- custo na reingestao. natureza entra na chave porque uma unica cobranca
-- externa (um "billing item" do ML) pode se desdobrar em naturezas
-- distintas para nos.
CREATE UNIQUE INDEX uq_custo_origem
    ON custo (tenant_id, canal_id, natureza, id_externo)
    WHERE id_externo IS NOT NULL;

ALTER TABLE custo ENABLE ROW LEVEL SECURITY;
ALTER TABLE custo FORCE  ROW LEVEL SECURITY;

CREATE POLICY custo_select ON custo
    FOR SELECT
    USING (tenant_id = app_current_tenant_id());

CREATE POLICY custo_insert ON custo
    FOR INSERT
    WITH CHECK (tenant_id = app_current_tenant_id());

CREATE POLICY custo_update ON custo
    FOR UPDATE
    USING (tenant_id = app_current_tenant_id())
    WITH CHECK (tenant_id = app_current_tenant_id());

CREATE POLICY custo_delete ON custo
    FOR DELETE
    USING (tenant_id = app_current_tenant_id());

-- Sem DELETE (convencao 6 da V005), e aqui isso e especialmente
-- importante: apagar linha de custo muda silenciosamente uma margem ja
-- reportada ao cliente. Correcao se faz com linha de estorno (S5), que
-- deixa rastro.
GRANT SELECT, INSERT, UPDATE ON TABLE custo TO app_aplicacao;
