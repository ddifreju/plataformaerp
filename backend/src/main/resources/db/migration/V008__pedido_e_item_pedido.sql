-- =====================================================================
-- V008 — pedido e item_pedido
-- =====================================================================
-- Convencoes gerais da Fase 1: cabecalho da V005.
--
-- ---------------------------------------------------------------------
-- O CONTRATO FINANCEIRO DO MODELO (leia antes de somar qualquer coisa)
-- ---------------------------------------------------------------------
-- Tres regras que valem para a Fase 2 inteira e nascem aqui:
--
-- R1. O TOTAL DO PEDIDO NAO E A SOMA DOS ITENS.
--     valor_total_pedido e o que o comprador pagou, informado PELA FONTE.
--     Ele inclui frete, desconto, taxa de servico e, em marketplace,
--     ajustes que a fonte nem sempre detalha. Ele NAO e recalculado por
--     nos e NAO tem CHECK amarrando-o a
--     (valor_bruto_itens - valor_desconto + valor_frete_cobrado).
--     PORQUE NAO TEM CHECK: um CHECK desses rejeitaria pedido REAL na
--     ingestao por causa de arredondamento ou de um campo que a fonte nao
--     expoe — e perderiamos o pedido inteiro para preservar uma igualdade
--     contabil que nao e nossa. Divergencia entre os dois e FATO a ser
--     mostrado (relatorio de conciliacao da Fase 2), nunca erro a ser
--     silenciosamente corrigido. Regra 5 do CLAUDE.md: nao inventamos
--     dado, nem por subtracao.
--
-- R2. RECEITA NAO MUDA DEPOIS DE GRAVADA. TODA PERDA E CUSTO.
--     Devolucao, reembolso, estorno de comissao, frete reverso: nada
--     disso reescreve valor_total_pedido. Tudo vira linha em `custo`
--     (V010). E isso que permite a decomposicao "faturamento bruto ->
--     lucro real" da tarefa 18 fechar sem malabarismo, e que permite
--     responder "quanto sobrou" olhando duas colunas.
--
-- R3. O QUE O CLIENTE PAGOU DE FRETE E O QUE O FRETE CUSTOU SAO NUMEROS
--     DIFERENTES.
--     valor_frete_cobrado (aqui) e RECEITA: o que o comprador pagou.
--     O custo do envio e linha em custo(natureza = 'FRETE').
--     Em marketplace brasileiro isso quase nunca coincide: frete gratis
--     acima de R$ 79 no ML significa cobrado = 0 e custo > 0, subsidiado
--     em parte pela plataforma. Uma coluna so esconderia exatamente o
--     numero que o produto existe para revelar.
-- =====================================================================


-- ---------------------------------------------------------------------
-- pedido
-- ---------------------------------------------------------------------
CREATE TABLE pedido (
    id                        uuid        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id                 uuid        NOT NULL,

    -- canal_id NOT NULL: todo pedido veio de algum lugar, inclusive a
    -- venda manual (que tem canal de categoria LOJA_PROPRIA). Sem isso,
    -- "faturamento por canal" teria um balde de orfaos.
    canal_id                  uuid        NOT NULL,

    -- cliente_id NULLABLE de proposito: marketplace nem sempre expoe o
    -- comprador (ML entrega so o apelido, e as vezes nem isso antes do
    -- envio). Exigir cliente faria o adaptador CRIAR um cliente falso
    -- para conseguir gravar o pedido — inventar dado (regra 5 do
    -- CLAUDE.md) para satisfazer uma restricao nossa. Preferimos o NULL
    -- honesto.
    cliente_id                uuid,

    id_externo                text,
    -- codigo_exibicao: o numero que o comprador e o suporte enxergam.
    -- Separado de id_externo porque nem sempre sao o mesmo (Shopee tem
    -- order_sn visivel e um id interno; ERP tem numero de pedido proprio).
    codigo_exibicao           text,

    -- status canonico. Dominio fechado por CHECK (convencao 5 da V005).
    -- Mediacao/disputa NAO esta aqui: ela vive em devolucao.status, senao
    -- o mesmo pedido precisaria de dois status ao mesmo tempo.
    status                    text        NOT NULL,
    -- status_origem: o status CRU da fonte, palavra por palavra
    -- ('paid', 'READY_TO_SHIP', 'Atendido'). Se o mapeamento canonico
    -- estiver errado, corrigimos sem re-ingerir. Regra 5 do CLAUDE.md.
    status_origem             text,

    -- ------------------------------------------------------------------
    -- Datas do ciclo de vida. Todas timestamptz (convencao 7 da V005).
    -- Sao colunas separadas, e nao uma tabela de eventos de status,
    -- porque o ciclo e curto, fixo e sempre consultado como fotografia
    -- ("pedidos enviados e nao entregues ha mais de 10 dias"). Tabela de
    -- eventos entraria se um dia precisarmos do historico completo de
    -- transicoes — hoje seria join em toda tela por um dado que ninguem
    -- pediu.
    -- ------------------------------------------------------------------
    feito_em                  timestamptz NOT NULL,
    pago_em                   timestamptz,
    enviado_em                timestamptz,
    entregue_em               timestamptz,
    cancelado_em              timestamptz,

    -- prazo_arrependimento_ate: CDC Art. 49 — 7 dias corridos contados do
    -- RECEBIMENTO do produto para compra fora do estabelecimento
    -- comercial (que e todo e-commerce). E CAMPO MODELADO, nao regra
    -- escondida em codigo, por tres motivos:
    --   a) a tela de operacao precisa perguntar "quais pedidos ainda
    --      podem voltar por arrependimento" com um WHERE, nao com uma
    --      regra em Java repetida em cada consulta;
    --   b) o prazo depende de entregue_em, que muda (reentrega, correcao
    --      da transportadora) — armazenado, fica auditavel qual prazo
    --      valia quando decidimos;
    --   c) o lojista pode oferecer prazo MAIOR que o legal (30 dias e
    --      comum). Uma data armazenada representa politica mais generosa
    --      sem tocar em codigo; "entregue_em + 7 dias" no SQL, nao.
    -- NAO e coluna GENERATED de proposito, pelo motivo (c) e porque
    -- entregue_em e NULL na maior parte da vida do pedido.
    -- Quem preenche e o adaptador/ingestao, na transicao para ENTREGUE.
    prazo_arrependimento_ate  timestamptz,

    -- ------------------------------------------------------------------
    -- Dinheiro. NUMERIC(18,4) (convencao 3 da V005). Ver R1/R3 no topo.
    -- ------------------------------------------------------------------
    -- Soma das linhas de item_pedido, ANTES de frete e de desconto de
    -- pedido. Materializado de proposito: evita agregar item_pedido em
    -- toda listagem, e preserva o numero que a fonte informou mesmo que
    -- os itens cheguem depois (ingestao em duas etapas e comum).
    valor_bruto_itens         numeric(18,4) NOT NULL DEFAULT 0,
    -- Desconto total concedido ao comprador (cupom, campanha, negociacao).
    -- Quem PAGOU o desconto (loja ou canal) nao cabe aqui: quando a fonte
    -- informa a divisao, a parte da loja vira custo(DESCONTO_CONCEDIDO).
    valor_desconto            numeric(18,4) NOT NULL DEFAULT 0,
    -- RECEITA de frete: o que o comprador pagou. O CUSTO do envio e
    -- custo(natureza='FRETE'). Ver R3.
    valor_frete_cobrado       numeric(18,4) NOT NULL DEFAULT 0,
    -- O que o comprador pagou no total, conforme a FONTE. Ver R1.
    valor_total_pedido        numeric(18,4) NOT NULL DEFAULT 0,

    -- valor_repasse_previsto: quanto o canal declara que vai repassar
    -- (net_received_amount do ML, escrow da Shopee). NULL quando a fonte
    -- nao informa.
    -- PORQUE VALE UMA COLUNA: e a unica conferencia externa que temos do
    -- motor de margem. Se somarmos todos os custos conhecidos e chegarmos
    -- a um liquido diferente do que o canal prometeu, existe custo que
    -- NAO estamos vendo — e achar esse custo invisivel e literalmente o
    -- produto. Sem esta coluna, a conta fecharia sempre, inclusive quando
    -- estivesse errada.
    valor_repasse_previsto    numeric(18,4),

    moeda                     char(3)     NOT NULL DEFAULT 'BRL',

    -- ------------------------------------------------------------------
    -- Pagamento: entra no pedido porque determina taxa de parcelamento e
    -- de antecipacao, que sao custo real (linhas em `custo`).
    -- ------------------------------------------------------------------
    forma_pagamento           text,
    quantidade_parcelas       smallint,

    -- ------------------------------------------------------------------
    -- Entrega: SO CEP/cidade/UF, nunca endereco completo.
    -- Minimizacao de LGPD (ver V007): analisamos venda, nao despachamos
    -- encomenda. O que a analise precisa e regiao — custo de frete varia
    -- por distancia, e a diferenca de ICMS interestadual depende da UF.
    -- Rua e numero nao respondem nenhuma pergunta nossa e so aumentariam
    -- o dano de um vazamento.
    --
    -- CEP e UF TEM CHECK de formato, e isso e uma excecao consciente a
    -- regra "nao rejeite dado torto da fonte" (que vale, por exemplo,
    -- para telefone em V007). O criterio da excecao: telefone vem de
    -- campo livre digitado por gente, e rejeitar perde o dado; CEP e UF
    -- vem de campo estruturado da API, sao normalizaveis em uma linha no
    -- adaptador, e guardar '12345-678' ao lado de '12345678' quebraria
    -- silenciosamente toda analise por regiao. Adaptador normaliza:
    -- so digitos no CEP, UF em maiusculas.
    -- ------------------------------------------------------------------
    cep_entrega               text,
    cidade_entrega            text,
    uf_entrega                char(2),

    dados_origem              jsonb       NOT NULL DEFAULT '{}'::jsonb,
    sincronizado_em           timestamptz,
    criado_em                 timestamptz NOT NULL DEFAULT now(),
    atualizado_em             timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_pedido PRIMARY KEY (id),
    CONSTRAINT uq_pedido_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_pedido_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    -- FKs COMPOSTAS (convencao 2 da V005): impedem que um pedido do
    -- tenant A referencie canal ou cliente do tenant B. FK simples nao
    -- impediria: a checagem de FK do Postgres ignora RLS.
    CONSTRAINT fk_pedido_canal
        FOREIGN KEY (tenant_id, canal_id) REFERENCES canal (tenant_id, id),
    CONSTRAINT fk_pedido_cliente
        FOREIGN KEY (tenant_id, cliente_id) REFERENCES cliente (tenant_id, id),

    CONSTRAINT ck_pedido_status CHECK (status IN (
        'AGUARDANDO_PAGAMENTO',
        'PAGAMENTO_RECUSADO',
        'PAGO',
        'EM_SEPARACAO',
        'ENVIADO',
        'ENTREGUE',
        'CANCELADO',
        'DEVOLVIDO'
    )),
    CONSTRAINT ck_pedido_forma_pagamento CHECK (forma_pagamento IS NULL OR forma_pagamento IN (
        'PIX',
        'BOLETO',
        'CARTAO_CREDITO',
        'CARTAO_DEBITO',
        'SALDO_CANAL',
        'TRANSFERENCIA',
        'DINHEIRO',
        'OUTRO'
    )),
    CONSTRAINT ck_pedido_id_externo_nao_vazio
        CHECK (id_externo IS NULL OR btrim(id_externo) <> ''),
    CONSTRAINT ck_pedido_moeda CHECK (moeda ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_pedido_parcelas
        CHECK (quantidade_parcelas IS NULL OR quantidade_parcelas BETWEEN 1 AND 36),
    CONSTRAINT ck_pedido_cep
        CHECK (cep_entrega IS NULL OR cep_entrega ~ '^[0-9]{8}$'),
    CONSTRAINT ck_pedido_uf
        CHECK (uf_entrega IS NULL OR uf_entrega ~ '^[A-Z]{2}$'),
    -- Valores nao negativos. NAO ha CHECK ligando os totais entre si:
    -- ver R1 no topo do arquivo.
    CONSTRAINT ck_pedido_valores_nao_negativos CHECK (
        valor_bruto_itens   >= 0 AND
        valor_desconto      >= 0 AND
        valor_frete_cobrado >= 0 AND
        valor_total_pedido  >= 0
    )
);

COMMENT ON TABLE  pedido IS
    'Venda canonica, venha de marketplace, loja propria ou ERP. Receita nao muda depois de gravada: toda perda posterior (devolucao, estorno) entra como linha em custo.';
COMMENT ON COLUMN pedido.tenant_id IS
    'Parte da identidade do registro. Sem DEFAULT de proposito: bug de contexto deve virar erro, nao linha gravada.';
COMMENT ON COLUMN pedido.cliente_id IS
    'NULL quando a fonte nao expoe o comprador. NULL honesto e melhor que cliente inventado para satisfazer uma restricao nossa.';
COMMENT ON COLUMN pedido.codigo_exibicao IS
    'Numero visivel ao comprador e ao suporte. Nem sempre igual a id_externo.';
COMMENT ON COLUMN pedido.status IS
    'Status canonico, mesmo dominio para toda fonte. Disputa/mediacao vive em devolucao.status.';
COMMENT ON COLUMN pedido.status_origem IS
    'Status cru da fonte, preservado palavra por palavra para permitir corrigir o mapeamento canonico sem re-ingerir.';
COMMENT ON COLUMN pedido.prazo_arrependimento_ate IS
    'CDC Art. 49: 7 dias corridos do recebimento. Campo modelado, nao regra em codigo - permite consultar por WHERE e representar politica mais generosa que a legal.';
COMMENT ON COLUMN pedido.valor_bruto_itens IS
    'Soma das linhas de item_pedido, antes de frete e desconto de pedido. Materializado de proposito.';
COMMENT ON COLUMN pedido.valor_frete_cobrado IS
    'RECEITA de frete: o que o comprador pagou. O CUSTO do envio e custo(natureza=FRETE) - em marketplace os dois quase nunca coincidem.';
COMMENT ON COLUMN pedido.valor_total_pedido IS
    'Total pago pelo comprador, conforme a FONTE. Nao e recalculado e nao tem CHECK contra a soma dos itens: divergencia e fato a conciliar, nao erro a corrigir.';
COMMENT ON COLUMN pedido.valor_repasse_previsto IS
    'Liquido que o canal declara que vai repassar. E a conferencia externa do motor de margem: diferenca contra a soma dos custos conhecidos indica custo invisivel.';
COMMENT ON COLUMN pedido.uf_entrega IS
    'Guardamos so CEP/cidade/UF, nunca endereco completo (minimizacao de LGPD). UF importa para frete e para diferenca de ICMS interestadual.';
COMMENT ON COLUMN pedido.dados_origem IS
    'Campo de extensao (decisao 0002): payload da fonte que nao coube no modelo canonico. E o que permite trocar a fonte sem reescrever o produto.';

-- ---------------------------------------------------------------------
-- Indices do pedido
-- ---------------------------------------------------------------------
-- Deliberadamente poucos. Cada indice custa em toda escrita, e a
-- ingestao escreve muito mais do que a interface le. Estes tres cobrem os
-- caminhos que o produto realmente percorre; o quarto entra quando um
-- EXPLAIN pedir, nao antes.

-- "vendas do periodo" — a consulta mais frequente do sistema inteiro.
CREATE INDEX ix_pedido_tenant_feito_em
    ON pedido (tenant_id, feito_em DESC);

-- "faturamento por canal no periodo" — a decomposicao da tela do dono.
CREATE INDEX ix_pedido_tenant_canal_feito_em
    ON pedido (tenant_id, canal_id, feito_em DESC);

-- "historico deste comprador" (atendimento). Parcial: pedido sem cliente
-- identificado nunca e alcancado por esse caminho.
CREATE INDEX ix_pedido_tenant_cliente
    ON pedido (tenant_id, cliente_id)
    WHERE cliente_id IS NOT NULL;

-- Idempotencia de reingestao: o mesmo pedido da mesma fonte nao duplica,
-- mesmo que evento_ingerido (V012) falhe. Duas travas independentes para
-- o mesmo risco, de proposito: pedido duplicado corrompe faturamento, que
-- e o numero que o cliente confere primeiro.
CREATE UNIQUE INDEX uq_pedido_origem
    ON pedido (tenant_id, canal_id, id_externo)
    WHERE id_externo IS NOT NULL;

ALTER TABLE pedido ENABLE ROW LEVEL SECURITY;
ALTER TABLE pedido FORCE  ROW LEVEL SECURITY;

CREATE POLICY pedido_select ON pedido
    FOR SELECT
    USING (tenant_id = app_current_tenant_id());

CREATE POLICY pedido_insert ON pedido
    FOR INSERT
    WITH CHECK (tenant_id = app_current_tenant_id());

CREATE POLICY pedido_update ON pedido
    FOR UPDATE
    USING (tenant_id = app_current_tenant_id())
    WITH CHECK (tenant_id = app_current_tenant_id());

CREATE POLICY pedido_delete ON pedido
    FOR DELETE
    USING (tenant_id = app_current_tenant_id());

-- Sem DELETE (convencao 6 da V005): cancelamento e status, nao remocao.
GRANT SELECT, INSERT, UPDATE ON TABLE pedido TO app_aplicacao;


-- ---------------------------------------------------------------------
-- item_pedido
-- ---------------------------------------------------------------------
-- DECISAO: valor_total_linha e COLUNA ARMAZENADA, nao GENERATED e nao
-- calculada na consulta.
--
-- A tentacao e escrever
--     valor_total_linha numeric(18,4) GENERATED ALWAYS AS
--         (quantidade * valor_unitario_bruto - valor_desconto_linha) STORED
-- e o Postgres aceitaria. Esta errado assim mesmo:
--   1. A FONTE informa o total da linha, e ele nem sempre bate com a
--      multiplicacao. Marketplace arredonda desconto rateado por item,
--      aplica campanha proporcional e as vezes envia unitario ja com duas
--      casas enquanto o total tem quatro. Uma coluna GENERATED
--      SOBRESCREVERIA o numero da fonte com o nosso — e a diferenca de
--      centavos apareceria depois, na conciliacao, sem ninguem saber de
--      onde veio.
--   2. Regra 5 do CLAUDE.md: se a fonte tem o dado, e o dado dela que
--      vale. Calcular por cima e inventar com aparencia de precisao.
-- Entao: redundancia PROPOSITAL e assumida. O adaptador grava o total que
-- a fonte mandou; quando a fonte nao manda, ele calcula UMA VEZ, na
-- ingestao, e grava. Normalizar na ingestao, nao na consulta.
-- Pelo mesmo motivo nao existe CHECK amarrando total a quantidade x preco.
--
-- DECISAO: sku_origem e titulo_origem sao FOTOGRAFIA, nao denormalizacao
-- preguicosa. O item vendido precisa continuar explicavel mesmo que a
-- variacao seja renomeada, desativada ou nunca tenha existido no nosso
-- catalogo (venda de item que o lojista nunca sincronizou). Por isso
-- variacao_id e NULLABLE e o texto e NOT NULL — o contrario do que a
-- intuicao de normalizacao sugere, e o correto para dado historico.
CREATE TABLE item_pedido (
    id                     uuid        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id              uuid        NOT NULL,

    pedido_id              uuid        NOT NULL,

    -- NULLABLE: marketplace vende o que quiser, inclusive item fora do
    -- nosso catalogo. Sem catalogo casado, a linha ainda vale (tem preco,
    -- quantidade e receita); so nao tera custo de mercadoria automatico.
    variacao_id            uuid,

    -- Fotografia no momento da venda (ver decisao acima).
    sku_origem             text,
    titulo_origem          text        NOT NULL,

    -- numeric, nao integer: venda fracionada existe (granel, metro, kg).
    quantidade             numeric(14,4) NOT NULL,

    -- Unitario BRUTO: preco de uma unidade antes do desconto da linha.
    valor_unitario_bruto   numeric(18,4) NOT NULL,
    -- Desconto atribuido a ESTA linha (rateado pela fonte, quando ela
    -- rateia).
    valor_desconto_linha   numeric(18,4) NOT NULL DEFAULT 0,
    -- Total da linha conforme a fonte. Ver a decisao no topo do bloco.
    valor_total_linha      numeric(18,4) NOT NULL,

    -- id_externo do ITEM dentro do pedido na fonte. Existe para tornar a
    -- reingestao do pedido idempotente linha a linha.
    id_externo             text,

    dados_origem           jsonb       NOT NULL DEFAULT '{}'::jsonb,
    sincronizado_em        timestamptz,
    criado_em              timestamptz NOT NULL DEFAULT now(),
    atualizado_em          timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_item_pedido PRIMARY KEY (id),
    CONSTRAINT uq_item_pedido_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_item_pedido_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_item_pedido_pedido
        FOREIGN KEY (tenant_id, pedido_id) REFERENCES pedido (tenant_id, id),
    CONSTRAINT fk_item_pedido_variacao
        FOREIGN KEY (tenant_id, variacao_id) REFERENCES variacao (tenant_id, id),

    CONSTRAINT ck_item_pedido_titulo_nao_vazio CHECK (btrim(titulo_origem) <> ''),
    CONSTRAINT ck_item_pedido_id_externo_nao_vazio
        CHECK (id_externo IS NULL OR btrim(id_externo) <> ''),
    CONSTRAINT ck_item_pedido_quantidade_positiva CHECK (quantidade > 0),
    CONSTRAINT ck_item_pedido_valores_nao_negativos CHECK (
        valor_unitario_bruto >= 0 AND
        valor_desconto_linha >= 0 AND
        valor_total_linha    >= 0
    )
);

-- A moeda do item e SEMPRE a moeda do pedido: nao existe pedido com itens
-- em moedas diferentes em nenhuma fonte que vamos integrar. Repetir a
-- coluna aqui criaria a possibilidade de divergir de pedido.moeda, que e
-- pior do que o join. Registrado para que ninguem "corrija" isso depois.
COMMENT ON TABLE  item_pedido IS
    'Linha de venda. Referencia VARIACAO (o SKU), nunca produto. Guarda fotografia de sku/titulo para o item continuar explicavel se o catalogo mudar. A moeda e sempre a do pedido.';
COMMENT ON COLUMN item_pedido.tenant_id IS
    'Parte da identidade do registro. Sem DEFAULT de proposito: bug de contexto deve virar erro, nao linha gravada.';
COMMENT ON COLUMN item_pedido.variacao_id IS
    'NULL quando o item vendido nao esta no catalogo sincronizado. A linha continua valida; so nao tem custo de mercadoria automatico.';
COMMENT ON COLUMN item_pedido.titulo_origem IS
    'Titulo no momento da venda. NOT NULL de proposito: e o que explica o item mesmo sem variacao casada.';
COMMENT ON COLUMN item_pedido.valor_unitario_bruto IS
    'Preco de UMA unidade, antes do desconto da linha.';
COMMENT ON COLUMN item_pedido.valor_total_linha IS
    'Total da linha conforme a FONTE. Redundancia proposital: nao e GENERATED porque a fonte arredonda diferente e o numero dela e que vale.';
COMMENT ON COLUMN item_pedido.dados_origem IS
    'Campo de extensao (decisao 0002): payload da fonte que nao coube no modelo canonico.';

-- Caminho quente: montar o pedido com suas linhas.
CREATE INDEX ix_item_pedido_tenant_pedido
    ON item_pedido (tenant_id, pedido_id);

-- "quanto vendi deste SKU" e "margem por SKU" (Fase 2). Parcial: linha
-- sem variacao casada nao entra nessa analise.
CREATE INDEX ix_item_pedido_tenant_variacao
    ON item_pedido (tenant_id, variacao_id)
    WHERE variacao_id IS NOT NULL;

-- Idempotencia linha a linha na reingestao do pedido.
CREATE UNIQUE INDEX uq_item_pedido_origem
    ON item_pedido (tenant_id, pedido_id, id_externo)
    WHERE id_externo IS NOT NULL;

ALTER TABLE item_pedido ENABLE ROW LEVEL SECURITY;
ALTER TABLE item_pedido FORCE  ROW LEVEL SECURITY;

CREATE POLICY item_pedido_select ON item_pedido
    FOR SELECT
    USING (tenant_id = app_current_tenant_id());

CREATE POLICY item_pedido_insert ON item_pedido
    FOR INSERT
    WITH CHECK (tenant_id = app_current_tenant_id());

CREATE POLICY item_pedido_update ON item_pedido
    FOR UPDATE
    USING (tenant_id = app_current_tenant_id())
    WITH CHECK (tenant_id = app_current_tenant_id());

CREATE POLICY item_pedido_delete ON item_pedido
    FOR DELETE
    USING (tenant_id = app_current_tenant_id());

GRANT SELECT, INSERT, UPDATE ON TABLE item_pedido TO app_aplicacao;
