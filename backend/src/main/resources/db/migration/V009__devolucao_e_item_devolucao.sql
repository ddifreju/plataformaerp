-- =====================================================================
-- V009 — devolucao e item_devolucao
-- =====================================================================
-- Convencoes gerais da Fase 1: cabecalho da V005.
--
-- ---------------------------------------------------------------------
-- POR QUE ESTA MIGRATION VEM ANTES DE `custo` (V010)
-- ---------------------------------------------------------------------
-- A fila de tarefas sugeria custo em V009 e devolucao em V010. Invertido
-- de proposito: `custo` tem FK para `devolucao` (o frete reverso e o
-- reembolso sao custos CAUSADOS por uma devolucao, e queremos poder
-- responder "quanto as devolucoes me custaram no mes" com um join, nao
-- com heuristica). Criar custo primeiro exigiria um ALTER TABLE numa
-- migration seguinte para adicionar a FK — duas migrations mexendo na
-- mesma tabela, e um undo mais fragil. Inverter e a alternativa mais
-- simples e nao muda nada mais.
--
-- ---------------------------------------------------------------------
-- O OBJETO CANONICO (decisao 0002 na pratica)
-- ---------------------------------------------------------------------
-- Devolucao do Mercado Livre, da Shopee e da loja propria sao objetos
-- MUITO diferentes na origem: o ML tem "claim" com mediacao e etiqueta de
-- retorno propria; a Shopee tem "return/refund request" com janela e
-- fluxo proprios; a loja propria e um e-mail e um codigo dos Correios.
-- Aqui viram UMA linha, com o dominio canonico abaixo, e o que e
-- especifico de cada uma sobrevive em `dados_origem`, `status_origem` e
-- `motivo_origem`. Isto e o moat: a Fase 2 calcula margem sem saber de
-- qual marketplace a devolucao veio.
--
-- ---------------------------------------------------------------------
-- O IMPACTO FINANCEIRO NAO E CALCULADO A PARTIR DESTA TABELA
-- ---------------------------------------------------------------------
-- valor_reembolsado e valor_frete_reverso existem aqui como DESCRICAO do
-- que aconteceu, informada pela fonte. O motor de margem (Fase 2) NAO
-- soma estas colunas: ele soma `custo`. Na ingestao, uma devolucao gera
-- linhas em custo (REEMBOLSO, FRETE_REVERSO, e o estorno negativo de
-- COMISSAO_CANAL quando o canal devolve a comissao).
-- PORQUE ASSIM: se a margem lesse ora `custo` ora `devolucao`, bastaria
-- um caminho de ingestao gravar nos dois lugares para o prejuizo aparecer
-- dobrado. Um lugar so para somar dinheiro. Estas colunas ficam sendo a
-- CONFERENCIA: se a soma dos custos ligados a devolucao nao bate com o
-- que a fonte declarou aqui, ha algo faltando.
-- =====================================================================


CREATE TABLE devolucao (
    id                         uuid        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id                  uuid        NOT NULL,

    pedido_id                  uuid        NOT NULL,

    -- canal_id NULLABLE e podendo ser DIFERENTE do canal do pedido: o
    -- cliente compra no site e pede a devolucao por WhatsApp. Guardar de
    -- onde veio a devolucao (e nao assumir o canal do pedido) e o que
    -- permite medir depois "por onde chegam meus problemas".
    canal_id                   uuid,
    id_externo                 text,

    -- TOTAL x PARCIAL: parcial obriga a saber QUAIS itens voltaram, o que
    -- e a razao de existir item_devolucao (ver abaixo).
    tipo                       text        NOT NULL,

    status                     text        NOT NULL,
    status_origem              text,

    -- motivo canonico. NAO e detalhe de relatorio: e ele que diz de quem
    -- e o prejuizo. DEFEITO aponta para o fornecedor (custo recuperavel),
    -- ERRO_DE_ENVIO_DA_LOJA aponta para o processo interno (custo
    -- evitavel), ARREPENDIMENTO e custo do canal de venda e nao tem
    -- culpado. Sem essa distincao, "taxa de devolucao" e um numero que
    -- nao gera nenhuma acao.
    motivo                     text,
    motivo_origem              text,

    -- ------------------------------------------------------------------
    -- CDC Art. 49 — arrependimento
    -- ------------------------------------------------------------------
    -- eh_arrependimento_cdc: a devolucao foi enquadrada no direito legal
    -- de arrependimento (7 dias corridos do recebimento). Consequencia
    -- pratica: nesse caso o frete de retorno e do FORNECEDOR por lei
    -- (Art. 49, paragrafo unico), o que muda quem paga o frete reverso.
    -- Coluna propria, e nao derivada de `motivo`, porque a fonte as vezes
    -- classifica como "defeito" algo que o comprador abriu dentro do
    -- prazo de arrependimento — e o enquadramento legal e o que importa
    -- para saber quem paga.
    eh_arrependimento_cdc      boolean     NOT NULL DEFAULT false,
    -- dentro_prazo_legal: fotografia da avaliacao no momento da abertura,
    -- comparando aberta_em com pedido.prazo_arrependimento_ate. Guardada
    -- e nao recalculada porque entregue_em pode ser corrigido depois pela
    -- transportadora, e a decisao tomada na epoca precisa continuar
    -- auditavel. NULL = nao foi avaliado (fonte nao informou data de
    -- entrega).
    dentro_prazo_legal         boolean,

    aberta_em                  timestamptz NOT NULL,
    -- Quando o produto voltou fisicamente. NULL enquanto nao voltou (ou
    -- se nunca voltar).
    recebida_em                timestamptz,
    finalizada_em              timestamptz,

    -- ------------------------------------------------------------------
    -- Destino do produto devolvido: e AQUI que mora a diferenca entre uma
    -- devolucao que custa o frete e uma que custa o produto inteiro.
    -- ------------------------------------------------------------------
    destino_produto            text        NOT NULL DEFAULT 'NAO_RETORNOU',

    -- ------------------------------------------------------------------
    -- Dinheiro (descricao do que a fonte informou — ver cabecalho)
    -- ------------------------------------------------------------------
    -- Liquido devolvido ao comprador.
    valor_reembolsado          numeric(18,4) NOT NULL DEFAULT 0,
    -- Custo do retorno fisico. Quem paga esta em responsavel_frete_reverso.
    valor_frete_reverso        numeric(18,4) NOT NULL DEFAULT 0,
    responsavel_frete_reverso  text,
    moeda                      char(3)     NOT NULL DEFAULT 'BRL',

    dados_origem               jsonb       NOT NULL DEFAULT '{}'::jsonb,
    sincronizado_em            timestamptz,
    criado_em                  timestamptz NOT NULL DEFAULT now(),
    atualizado_em              timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_devolucao PRIMARY KEY (id),
    CONSTRAINT uq_devolucao_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_devolucao_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_devolucao_pedido
        FOREIGN KEY (tenant_id, pedido_id) REFERENCES pedido (tenant_id, id),
    CONSTRAINT fk_devolucao_canal
        FOREIGN KEY (tenant_id, canal_id) REFERENCES canal (tenant_id, id),

    CONSTRAINT ck_devolucao_tipo CHECK (tipo IN ('TOTAL', 'PARCIAL')),
    CONSTRAINT ck_devolucao_status CHECK (status IN (
        'ABERTA',
        'EM_ANALISE',
        'EM_MEDIACAO',
        'APROVADA',
        'RECUSADA',
        'EM_TRANSITO',
        'RECEBIDA',
        'CONCLUIDA',
        'CANCELADA'
    )),
    CONSTRAINT ck_devolucao_motivo CHECK (motivo IS NULL OR motivo IN (
        'ARREPENDIMENTO',
        'PRODUTO_COM_DEFEITO',
        'PRODUTO_DIFERENTE_DO_ANUNCIO',
        'AVARIA_NO_TRANSPORTE',
        'ATRASO_NA_ENTREGA',
        'NAO_ENTREGUE',
        'ERRO_DE_ENVIO_DA_LOJA',
        'FRAUDE',
        'OUTRO'
    )),
    CONSTRAINT ck_devolucao_destino_produto CHECK (destino_produto IN (
        'NAO_RETORNOU',
        'ESTOQUE',
        'ESTOQUE_COMO_SEGUNDA_LINHA',
        'ASSISTENCIA',
        'DEVOLVIDO_AO_FORNECEDOR',
        'DESCARTE'
    )),
    CONSTRAINT ck_devolucao_responsavel_frete CHECK (
        responsavel_frete_reverso IS NULL OR responsavel_frete_reverso IN (
            'VENDEDOR',
            'COMPRADOR',
            'CANAL',
            'NAO_SE_APLICA'
        )
    ),
    CONSTRAINT ck_devolucao_id_externo_nao_vazio
        CHECK (id_externo IS NULL OR btrim(id_externo) <> ''),
    CONSTRAINT ck_devolucao_moeda CHECK (moeda ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_devolucao_valores_nao_negativos CHECK (
        valor_reembolsado   >= 0 AND
        valor_frete_reverso >= 0
    )
);

COMMENT ON TABLE  devolucao IS
    'Devolucao canonica: claim do ML, return da Shopee e e-mail da loja propria viram a mesma linha, com o especifico de cada um preservado em dados_origem/status_origem/motivo_origem.';
COMMENT ON COLUMN devolucao.tenant_id IS
    'Parte da identidade do registro. Sem DEFAULT de proposito: bug de contexto deve virar erro, nao linha gravada.';
COMMENT ON COLUMN devolucao.canal_id IS
    'Por onde a devolucao chegou. Pode ser diferente do canal do pedido (compra no site, reclamacao no WhatsApp).';
COMMENT ON COLUMN devolucao.tipo IS
    'TOTAL ou PARCIAL. Parcial exige linhas em item_devolucao dizendo o que voltou.';
COMMENT ON COLUMN devolucao.motivo IS
    'Motivo canonico. Define de quem e o prejuizo: DEFEITO aponta ao fornecedor, ERRO_DE_ENVIO_DA_LOJA ao processo interno, ARREPENDIMENTO nao tem culpado.';
COMMENT ON COLUMN devolucao.eh_arrependimento_cdc IS
    'Enquadrada no CDC Art. 49 (7 dias corridos do recebimento). Nesse caso o frete de retorno e do fornecedor por lei (Art. 49, paragrafo unico).';
COMMENT ON COLUMN devolucao.dentro_prazo_legal IS
    'Avaliacao feita na abertura contra pedido.prazo_arrependimento_ate. Guardada, nao recalculada: a data de entrega pode ser corrigida depois e a decisao da epoca precisa continuar auditavel.';
COMMENT ON COLUMN devolucao.destino_produto IS
    'O que aconteceu com o produto devolvido. Separa a devolucao que custou o frete da que custou o produto inteiro (DESCARTE).';
COMMENT ON COLUMN devolucao.valor_reembolsado IS
    'Liquido devolvido ao comprador, conforme a fonte. DESCRICAO: o motor de margem soma custo, nao esta coluna (ver cabecalho da V009).';
COMMENT ON COLUMN devolucao.valor_frete_reverso IS
    'Custo do retorno fisico, conforme a fonte. DESCRICAO: o valor que entra na margem e a linha custo(natureza=FRETE_REVERSO).';
COMMENT ON COLUMN devolucao.dados_origem IS
    'Campo de extensao (decisao 0002): o que e especifico de cada fonte (claim do ML, return da Shopee) sem poluir o modelo canonico.';

CREATE INDEX ix_devolucao_tenant_pedido
    ON devolucao (tenant_id, pedido_id);

-- "devolucoes abertas no periodo" — a fila de trabalho e o indicador.
CREATE INDEX ix_devolucao_tenant_aberta_em
    ON devolucao (tenant_id, aberta_em DESC);

CREATE UNIQUE INDEX uq_devolucao_origem
    ON devolucao (tenant_id, canal_id, id_externo)
    WHERE id_externo IS NOT NULL;

ALTER TABLE devolucao ENABLE ROW LEVEL SECURITY;
ALTER TABLE devolucao FORCE  ROW LEVEL SECURITY;

CREATE POLICY devolucao_select ON devolucao
    FOR SELECT
    USING (tenant_id = app_current_tenant_id());

CREATE POLICY devolucao_insert ON devolucao
    FOR INSERT
    WITH CHECK (tenant_id = app_current_tenant_id());

CREATE POLICY devolucao_update ON devolucao
    FOR UPDATE
    USING (tenant_id = app_current_tenant_id())
    WITH CHECK (tenant_id = app_current_tenant_id());

CREATE POLICY devolucao_delete ON devolucao
    FOR DELETE
    USING (tenant_id = app_current_tenant_id());

GRANT SELECT, INSERT, UPDATE ON TABLE devolucao TO app_aplicacao;


-- ---------------------------------------------------------------------
-- item_devolucao
-- ---------------------------------------------------------------------
-- ADICAO ao escopo pedido na fila de tarefas, e a justificativa e curta:
-- sem esta tabela, "devolucao PARCIAL" e uma palavra sem dado atras.
-- Nao daria para responder qual SKU voltou, nem para atribuir o custo da
-- mercadoria devolvida ao item certo — e margem por SKU (Fase 2) ficaria
-- errada exatamente nos pedidos que mais doem.
-- Em devolucao TOTAL as linhas tambem sao geradas (uma por item do
-- pedido): caminho unico de leitura vale mais que economizar insert.
CREATE TABLE item_devolucao (
    id                  uuid        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           uuid        NOT NULL,

    devolucao_id        uuid        NOT NULL,
    -- Aponta para o ITEM do pedido, nao para a variacao: e a linha
    -- especifica daquela venda que esta voltando, com o preco praticado
    -- naquele momento.
    item_pedido_id      uuid        NOT NULL,

    -- Pode ser menor que a quantidade vendida (comprou 3, devolveu 1).
    quantidade          numeric(14,4) NOT NULL,
    valor_reembolsado   numeric(18,4) NOT NULL DEFAULT 0,

    -- Motivo por item: em devolucao com varios itens, o defeito costuma
    -- ser de um so. NULL = usa o motivo da devolucao.
    motivo              text,

    dados_origem        jsonb       NOT NULL DEFAULT '{}'::jsonb,
    criado_em           timestamptz NOT NULL DEFAULT now(),
    atualizado_em       timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_item_devolucao PRIMARY KEY (id),
    CONSTRAINT uq_item_devolucao_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_item_devolucao_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_item_devolucao_devolucao
        FOREIGN KEY (tenant_id, devolucao_id) REFERENCES devolucao (tenant_id, id),
    CONSTRAINT fk_item_devolucao_item_pedido
        FOREIGN KEY (tenant_id, item_pedido_id) REFERENCES item_pedido (tenant_id, id),

    -- Uma linha por item do pedido dentro da mesma devolucao. Devolver
    -- mais do mesmo item depois abre OUTRA devolucao, que e o que
    -- acontece na realidade.
    CONSTRAINT uq_item_devolucao_item
        UNIQUE (tenant_id, devolucao_id, item_pedido_id),
    CONSTRAINT ck_item_devolucao_quantidade_positiva CHECK (quantidade > 0),
    CONSTRAINT ck_item_devolucao_valor_nao_negativo CHECK (valor_reembolsado >= 0),
    CONSTRAINT ck_item_devolucao_motivo CHECK (motivo IS NULL OR motivo IN (
        'ARREPENDIMENTO',
        'PRODUTO_COM_DEFEITO',
        'PRODUTO_DIFERENTE_DO_ANUNCIO',
        'AVARIA_NO_TRANSPORTE',
        'ATRASO_NA_ENTREGA',
        'NAO_ENTREGUE',
        'ERRO_DE_ENVIO_DA_LOJA',
        'FRAUDE',
        'OUTRO'
    ))
);

-- NAO existe CHECK garantindo que a soma das quantidades devolvidas nao
-- passa da quantidade vendida. Seria preciso um trigger (CHECK nao
-- enxerga outra tabela), e trigger e regra de negocio invisivel no banco.
-- Alem disso a fonte as vezes manda exatamente isso (bonificacao, troca
-- que virou devolucao). E verificacao de conciliacao da Fase 2, com
-- alerta, nao restricao que barra ingestao.
COMMENT ON TABLE  item_devolucao IS
    'O que voltou, item a item. Existe para que devolucao PARCIAL seja dado e nao palavra, e para atribuir o custo ao SKU certo. Gerada tambem em devolucao TOTAL, para caminho de leitura unico.';
COMMENT ON COLUMN item_devolucao.tenant_id IS
    'Parte da identidade do registro. Sem DEFAULT de proposito: bug de contexto deve virar erro, nao linha gravada.';
COMMENT ON COLUMN item_devolucao.item_pedido_id IS
    'Aponta para a LINHA da venda, nao para a variacao: preserva o preco praticado naquele pedido.';
COMMENT ON COLUMN item_devolucao.quantidade IS
    'Pode ser menor que a vendida (comprou 3, devolveu 1). Sem CHECK contra o total vendido: e conciliacao da Fase 2, nao restricao que barra ingestao.';

CREATE INDEX ix_item_devolucao_tenant_devolucao
    ON item_devolucao (tenant_id, devolucao_id);

CREATE INDEX ix_item_devolucao_tenant_item_pedido
    ON item_devolucao (tenant_id, item_pedido_id);

ALTER TABLE item_devolucao ENABLE ROW LEVEL SECURITY;
ALTER TABLE item_devolucao FORCE  ROW LEVEL SECURITY;

CREATE POLICY item_devolucao_select ON item_devolucao
    FOR SELECT
    USING (tenant_id = app_current_tenant_id());

CREATE POLICY item_devolucao_insert ON item_devolucao
    FOR INSERT
    WITH CHECK (tenant_id = app_current_tenant_id());

CREATE POLICY item_devolucao_update ON item_devolucao
    FOR UPDATE
    USING (tenant_id = app_current_tenant_id())
    WITH CHECK (tenant_id = app_current_tenant_id());

CREATE POLICY item_devolucao_delete ON item_devolucao
    FOR DELETE
    USING (tenant_id = app_current_tenant_id());

GRANT SELECT, INSERT, UPDATE ON TABLE item_devolucao TO app_aplicacao;
