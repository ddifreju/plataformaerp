-- =====================================================================
-- V006 — produto e variacao
-- =====================================================================
-- As convencoes da Fase 1 (RLS, FK composta, NUMERIC(18,4), campo de
-- extensao, CHECK em vez de ENUM, sem DELETE, timestamptz) estao no
-- cabecalho da V005. Aqui so o que e especifico destas duas tabelas.
--
-- ---------------------------------------------------------------------
-- A DECISAO CENTRAL: quem se vende e a VARIACAO, nunca o produto
-- ---------------------------------------------------------------------
-- `produto` e o AGRUPADOR conceitual: "Camiseta Basica". Ninguem compra
-- isso. O que sai do estoque, tem SKU, tem custo proprio, tem GTIN e e
-- referenciado por um item de pedido e a VARIACAO: "Camiseta Basica /
-- Preta / M".
--
-- Consequencia assumida: item_pedido referencia variacao, NAO produto.
-- E: TODO produto tem pelo menos uma variacao. Produto sem variante
-- (um livro, um kit fechado) ganha uma variacao unica, com
-- `eh_variacao_padrao = true`, criada pelo adaptador junto do produto.
--
-- PORQUE ASSIM, e nao "produto opcionalmente com variacoes":
--   Se item_pedido pudesse apontar ora para produto ora para variacao,
--   toda query de margem por SKU precisaria de um COALESCE e de um
--   caminho duplo. O custo de mercadoria, o estoque e o preco vivem no
--   nivel da variacao em TODAS as fontes que vamos integrar (ML tem
--   `variations`, Shopee tem `models`, Bling tem produto pai/filho).
--   Forcar a variacao padrao paga um insert a mais na ingestao e compra
--   um caminho unico para sempre. Normalizar na ingestao em vez de na
--   consulta e a regra.
--
-- ---------------------------------------------------------------------
-- ATRIBUTOS DE VARIACAO EM jsonb, NAO EM COLUNAS cor/tamanho
-- ---------------------------------------------------------------------
-- `variacao.atributos` e jsonb ({"cor":"preta","tamanho":"M"}) porque o
-- eixo de variacao muda por nicho: moda varia cor/tamanho, pet varia
-- peso/sabor, suplemento varia sabor/gramatura. Colunas fixas cor/tamanho
-- ficariam NULL na maioria dos tenants e ainda assim faltariam. E o nicho
-- inicial nem foi decidido (docs/PENDENCIAS.md).
-- Diferente de `dados_origem`: `atributos` E canonico e E consultavel; o
-- dia em que "vendas por cor" virar relatorio, ele le daqui (com indice
-- GIN, se precisar) sem depender do formato de nenhuma fonte.
-- =====================================================================


-- ---------------------------------------------------------------------
-- produto
-- ---------------------------------------------------------------------
CREATE TABLE produto (
    id                 uuid        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id          uuid        NOT NULL,

    -- Origem do dado (convencao 4 da V005). canal_id NULL = cadastrado
    -- direto na plataforma, sem fonte externa.
    canal_id           uuid,
    id_externo         text,

    titulo             text        NOT NULL,
    descricao          text,
    marca              text,
    categoria          text,

    -- NCM/CEST: classificacao fiscal brasileira, obrigatoria em nota.
    -- Fica no PRODUTO e nao na variacao porque cor e tamanho nao mudam a
    -- classificacao fiscal.
    -- ATENCAO PARA A FASE 2: a aliquota que se aplica a este NCM NAO mora
    -- aqui. A reforma tributaria (CBS/IBS) esta em transicao de 2026 a
    -- 2033, com aliquota diferente por ano de vigencia; regra fiscal
    -- versionada por periodo e a tarefa 13, em tabela propria. Guardar
    -- aliquota nesta tabela congelaria o imposto de 2026 em 2030.
    ncm                char(8),
    cest               char(7),

    ativo              boolean     NOT NULL DEFAULT true,

    dados_origem       jsonb       NOT NULL DEFAULT '{}'::jsonb,
    sincronizado_em    timestamptz,
    criado_em          timestamptz NOT NULL DEFAULT now(),
    atualizado_em      timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_produto PRIMARY KEY (id),
    CONSTRAINT uq_produto_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_produto_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    -- FK COMPOSTA (convencao 2): impede produto do tenant A apontar para
    -- canal do tenant B. Com FK simples o Postgres aceitaria, porque a
    -- checagem de FK ignora RLS.
    CONSTRAINT fk_produto_canal
        FOREIGN KEY (tenant_id, canal_id) REFERENCES canal (tenant_id, id),

    CONSTRAINT ck_produto_titulo_nao_vazio CHECK (btrim(titulo) <> ''),
    CONSTRAINT ck_produto_id_externo_nao_vazio
        CHECK (id_externo IS NULL OR btrim(id_externo) <> ''),
    CONSTRAINT ck_produto_ncm CHECK (ncm IS NULL OR ncm ~ '^[0-9]{8}$'),
    CONSTRAINT ck_produto_cest CHECK (cest IS NULL OR cest ~ '^[0-9]{7}$')
);

COMMENT ON TABLE  produto IS
    'Agrupador conceitual do que se anuncia. Nao e o que se vende: quem se vende e a variacao (SKU). Todo produto tem ao menos uma variacao.';
COMMENT ON COLUMN produto.tenant_id IS
    'Parte da identidade do registro. Sem DEFAULT de proposito: bug de contexto deve virar erro, nao linha gravada.';
COMMENT ON COLUMN produto.canal_id IS
    'Fonte que originou o cadastro. NULL = criado na propria plataforma.';
COMMENT ON COLUMN produto.id_externo IS
    'Identificador do produto NA FONTE (MLB..., item_id da Shopee, id do Bling). text porque cada fonte usa um formato.';
COMMENT ON COLUMN produto.ncm IS
    'Classificacao fiscal (8 digitos). A ALIQUOTA correspondente nao mora aqui: regra fiscal e versionada por vigencia (CBS/IBS 2026-2033) em tabela propria da Fase 2.';
COMMENT ON COLUMN produto.dados_origem IS
    'Campo de extensao (decisao 0002): payload da fonte que nao coube no modelo canonico. E o que permite trocar a fonte sem reescrever o produto.';
COMMENT ON COLUMN produto.ativo IS
    'Desativacao logica. Produto nao se apaga: item_pedido historico depende dele para explicar o que foi vendido.';

-- Reingestao idempotente: o mesmo produto da mesma fonte nunca duplica.
-- PARCIAL (WHERE id_externo IS NOT NULL) porque produto cadastrado a mao
-- nao tem id externo, e varios NULLs nao podem colidir entre si.
CREATE UNIQUE INDEX uq_produto_origem
    ON produto (tenant_id, canal_id, id_externo)
    WHERE id_externo IS NOT NULL;

ALTER TABLE produto ENABLE ROW LEVEL SECURITY;
ALTER TABLE produto FORCE  ROW LEVEL SECURITY;

CREATE POLICY produto_select ON produto
    FOR SELECT
    USING (tenant_id = app_current_tenant_id());

CREATE POLICY produto_insert ON produto
    FOR INSERT
    WITH CHECK (tenant_id = app_current_tenant_id());

CREATE POLICY produto_update ON produto
    FOR UPDATE
    USING (tenant_id = app_current_tenant_id())
    WITH CHECK (tenant_id = app_current_tenant_id());

CREATE POLICY produto_delete ON produto
    FOR DELETE
    USING (tenant_id = app_current_tenant_id());

-- Sem DELETE (convencao 6 da V005): produto se desativa, nao se apaga.
GRANT SELECT, INSERT, UPDATE ON TABLE produto TO app_aplicacao;


-- ---------------------------------------------------------------------
-- variacao — o SKU. E ISTO que se vende.
-- ---------------------------------------------------------------------
CREATE TABLE variacao (
    id                       uuid        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id                uuid        NOT NULL,

    produto_id               uuid        NOT NULL,

    -- sku: o codigo interno do lojista. Unico por tenant, e a chave que o
    -- humano usa para falar do item ("o SKU CAM-PRT-M nao esta batendo").
    -- NOT NULL: variacao sem SKU nao e rastreavel. Quando a fonte nao
    -- fornece, o adaptador gera um deterministico a partir do id externo
    -- (o adaptador inventa a CHAVE, nunca o DADO — regra 5 do CLAUDE.md).
    sku                      text        NOT NULL,

    -- gtin (EAN/UPC/GTIN-14): codigo global do fabricante. Opcional, e
    -- deliberadamente NAO unico: kits e reembalagens repetem GTIN
    -- legitimamente, e marketplace as vezes devolve GTIN errado. Usar
    -- como chave unica quebraria ingestao por dado sujo de terceiro.
    gtin                     text,

    -- Descreve a variacao para humano ("Preta / M"). Redundante com
    -- `atributos` de proposito: e o texto que a fonte exibe, e e o que
    -- deve aparecer em relatorio sem precisar remontar o jsonb.
    descricao_variacao       text,

    -- atributos: eixos de variacao, canonicos e consultaveis
    -- ({"cor":"preta","tamanho":"M"}). jsonb porque o eixo muda por nicho
    -- (ver cabecalho). Diferente de dados_origem: isto E modelo, aquilo e
    -- deposito do que a fonte mandou a mais.
    atributos                jsonb       NOT NULL DEFAULT '{}'::jsonb,

    -- eh_variacao_padrao: marca a variacao unica criada para produto sem
    -- variante real. Existe para a interface saber quando NAO mostrar
    -- seletor de variacao, sem precisar contar linhas.
    eh_variacao_padrao       boolean     NOT NULL DEFAULT false,

    -- preco_venda_atual: preco de tabela VIGENTE. NAO e o preco pelo qual
    -- alguma coisa foi vendida — esse fica congelado em
    -- item_pedido.valor_unitario_bruto. Serve para simulacao e para a
    -- interface, jamais para recalcular pedido passado.
    preco_venda_atual        numeric(18,4),

    -- custo_unitario_atual: custo de aquisicao VIGENTE, tipicamente vindo
    -- do ERP (decisao 0013: Bling e a primeira fonte de custo).
    -- Tambem NAO e o custo do que ja foi vendido: na ingestao do pedido,
    -- o adaptador COPIA este valor para uma linha de `custo` de natureza
    -- MERCADORIA amarrada ao item. Isso congela o custo no momento da
    -- venda. Se o fornecedor aumentar o preco amanha, a margem de ontem
    -- nao muda — que e o comportamento correto e o motivo de o custo real
    -- viver em `custo`, nao aqui.
    custo_unitario_atual     numeric(18,4),

    moeda                    char(3)     NOT NULL DEFAULT 'BRL',

    -- estoque_disponivel: fotografia da ultima sincronizacao, nao livro
    -- razao. Controle de estoque com movimentacao nao e escopo da Fase 1;
    -- se um dia for, entra como tabela de movimento e esta coluna vira
    -- projecao. numeric pelo mesmo motivo de `quantidade`: venda
    -- fracionada existe.
    estoque_disponivel       numeric(14,4),

    ativo                    boolean     NOT NULL DEFAULT true,

    canal_id                 uuid,
    id_externo               text,
    dados_origem             jsonb       NOT NULL DEFAULT '{}'::jsonb,
    sincronizado_em          timestamptz,
    criado_em                timestamptz NOT NULL DEFAULT now(),
    atualizado_em            timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_variacao PRIMARY KEY (id),
    CONSTRAINT uq_variacao_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_variacao_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_variacao_produto
        FOREIGN KEY (tenant_id, produto_id) REFERENCES produto (tenant_id, id),
    CONSTRAINT fk_variacao_canal
        FOREIGN KEY (tenant_id, canal_id) REFERENCES canal (tenant_id, id),

    CONSTRAINT uq_variacao_sku UNIQUE (tenant_id, sku),
    CONSTRAINT ck_variacao_sku_nao_vazio CHECK (btrim(sku) <> ''),
    CONSTRAINT ck_variacao_id_externo_nao_vazio
        CHECK (id_externo IS NULL OR btrim(id_externo) <> ''),
    CONSTRAINT ck_variacao_moeda CHECK (moeda ~ '^[A-Z]{3}$'),
    -- Preco e custo podem ser zero (brinde, bonificacao) mas nunca
    -- negativos: valor negativo aqui e sempre erro de parsing da fonte.
    CONSTRAINT ck_variacao_preco_nao_negativo
        CHECK (preco_venda_atual IS NULL OR preco_venda_atual >= 0),
    CONSTRAINT ck_variacao_custo_nao_negativo
        CHECK (custo_unitario_atual IS NULL OR custo_unitario_atual >= 0)
);

COMMENT ON TABLE  variacao IS
    'O SKU: a unidade que realmente se vende. item_pedido referencia variacao, nunca produto. Produto sem variante real tem uma variacao padrao.';
COMMENT ON COLUMN variacao.tenant_id IS
    'Parte da identidade do registro. Sem DEFAULT de proposito: bug de contexto deve virar erro, nao linha gravada.';
COMMENT ON COLUMN variacao.sku IS
    'Codigo interno do lojista, unico por tenant. Quando a fonte nao fornece, o adaptador gera um deterministico a partir do id externo.';
COMMENT ON COLUMN variacao.gtin IS
    'EAN/UPC do fabricante. Deliberadamente NAO unico: kits e reembalagens repetem GTIN, e marketplace as vezes devolve GTIN errado.';
COMMENT ON COLUMN variacao.atributos IS
    'Eixos canonicos de variacao (cor, tamanho, peso, sabor). jsonb porque o eixo muda por nicho. Consultavel, ao contrario de dados_origem.';
COMMENT ON COLUMN variacao.preco_venda_atual IS
    'Preco de tabela VIGENTE, unitario, bruto. Nunca usar para recalcular pedido passado: o preco praticado fica congelado em item_pedido.';
COMMENT ON COLUMN variacao.custo_unitario_atual IS
    'Custo de aquisicao VIGENTE, unitario (tipicamente do ERP). O custo da venda ja realizada e copiado para custo(natureza=MERCADORIA) na ingestao e nao muda depois.';
COMMENT ON COLUMN variacao.estoque_disponivel IS
    'Fotografia da ultima sincronizacao, nao livro razao. Movimentacao de estoque nao e escopo da Fase 1.';
COMMENT ON COLUMN variacao.dados_origem IS
    'Campo de extensao (decisao 0002): payload da fonte que nao coube no modelo canonico.';

-- Caminho quente: listar/mostrar as variacoes de um produto.
CREATE INDEX ix_variacao_tenant_produto
    ON variacao (tenant_id, produto_id);

-- Reingestao idempotente por fonte (mesmo racional do produto).
CREATE UNIQUE INDEX uq_variacao_origem
    ON variacao (tenant_id, canal_id, id_externo)
    WHERE id_externo IS NOT NULL;

-- Casamento por codigo de barras na conciliacao com o ERP. Parcial:
-- a maioria das linhas nao tem GTIN e nao precisa entrar no indice.
CREATE INDEX ix_variacao_tenant_gtin
    ON variacao (tenant_id, gtin)
    WHERE gtin IS NOT NULL;

ALTER TABLE variacao ENABLE ROW LEVEL SECURITY;
ALTER TABLE variacao FORCE  ROW LEVEL SECURITY;

CREATE POLICY variacao_select ON variacao
    FOR SELECT
    USING (tenant_id = app_current_tenant_id());

CREATE POLICY variacao_insert ON variacao
    FOR INSERT
    WITH CHECK (tenant_id = app_current_tenant_id());

CREATE POLICY variacao_update ON variacao
    FOR UPDATE
    USING (tenant_id = app_current_tenant_id())
    WITH CHECK (tenant_id = app_current_tenant_id());

CREATE POLICY variacao_delete ON variacao
    FOR DELETE
    USING (tenant_id = app_current_tenant_id());

GRANT SELECT, INSERT, UPDATE ON TABLE variacao TO app_aplicacao;
