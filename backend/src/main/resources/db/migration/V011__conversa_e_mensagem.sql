-- =====================================================================
-- V011 — conversa e mensagem
-- =====================================================================
-- Convencoes gerais da Fase 1: cabecalho da V005.
--
-- ---------------------------------------------------------------------
-- POR QUE conversa E mensagem, e nao so mensagem
-- ---------------------------------------------------------------------
-- A unidade de trabalho do atendimento e a CONVERSA (o thread), nao a
-- mensagem. "Quantas conversas estao aguardando resposta", "quanto tempo
-- levamos para responder", "quais conversas nao tem pedido associado" —
-- todas as perguntas uteis sao sobre o thread. Com uma tabela so de
-- mensagens, cada uma dessas perguntas viraria uma agregacao com window
-- function. A conversa tambem e o lugar natural para o vinculo com
-- pedido e cliente, que sao do thread inteiro e nao de cada mensagem.
--
-- ---------------------------------------------------------------------
-- LGPD
-- ---------------------------------------------------------------------
-- Conteudo de conversa e dado pessoal, e as vezes dado sensivel (o
-- comprador conta coisas que nao deveria). Vale o mesmo desenho da V007:
-- a aplicacao NAO tem DELETE; a exclusao a pedido do titular e
-- anonimizacao (`anonimizado_em`, com conteudo e identificacao do autor
-- em NULL), preservando a existencia do atendimento para a metrica de
-- processo sem preservar o que a pessoa escreveu.
--
-- ---------------------------------------------------------------------
-- NAO MODELADO AGORA, DE PROPOSITO: embedding para busca semantica
-- ---------------------------------------------------------------------
-- A extensao pgvector ja esta instalada (V001) e a camada de IA vai
-- querer indexar mensagens. Nao criamos coluna `vector(N)` aqui porque N
-- e fixo por modelo de embedding, e escolher o modelo antes de existir
-- chave de API (docs/PENDENCIAS.md) seria congelar uma decisao sem
-- informacao. Quando entrar, entra como TABELA propria
-- (mensagem_embedding: mensagem_id, modelo, vetor, gerado_em) e nao como
-- coluna: assim trocar de modelo nao reescreve a tabela de mensagens, e
-- reprocessar embeddings nao toca no dado operacional.
-- =====================================================================


CREATE TABLE conversa (
    id                    uuid        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id             uuid        NOT NULL,

    -- canal_id NOT NULL: conversa sem canal nao existe (veio do WhatsApp,
    -- da mensageria do ML, do e-mail). Espera-se canal de categoria
    -- COMUNICACAO ou MARKETPLACE; nao ha CHECK cruzando tabelas para isso
    -- (exigiria trigger), e a validacao mora no adaptador.
    canal_id              uuid        NOT NULL,

    -- Ambos opcionais: a primeira mensagem de um desconhecido nao tem
    -- cliente nem pedido, e amarrar depois e trabalho do atendimento (ou
    -- da IA). Forcar o vinculo aqui obrigaria a inventar cliente.
    cliente_id            uuid,
    pedido_id             uuid,

    id_externo            text,

    assunto               text,
    status                text        NOT NULL DEFAULT 'ABERTA',

    iniciada_em           timestamptz NOT NULL,
    -- ultima_mensagem_em: materializado de proposito. E a coluna de
    -- ordenacao da fila de atendimento; calcular por max(mensagem) em
    -- toda abertura de tela seria pagar uma agregacao por linha listada.
    -- Quem mantem e a ingestao, no mesmo passo que grava a mensagem.
    ultima_mensagem_em    timestamptz,
    encerrada_em          timestamptz,

    -- Ver bloco de LGPD no cabecalho.
    anonimizado_em        timestamptz,

    dados_origem          jsonb       NOT NULL DEFAULT '{}'::jsonb,
    sincronizado_em       timestamptz,
    criado_em             timestamptz NOT NULL DEFAULT now(),
    atualizado_em         timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_conversa PRIMARY KEY (id),
    CONSTRAINT uq_conversa_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_conversa_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_conversa_canal
        FOREIGN KEY (tenant_id, canal_id) REFERENCES canal (tenant_id, id),
    CONSTRAINT fk_conversa_cliente
        FOREIGN KEY (tenant_id, cliente_id) REFERENCES cliente (tenant_id, id),
    CONSTRAINT fk_conversa_pedido
        FOREIGN KEY (tenant_id, pedido_id) REFERENCES pedido (tenant_id, id),

    CONSTRAINT ck_conversa_status CHECK (status IN (
        'ABERTA',
        'AGUARDANDO_CLIENTE',
        'AGUARDANDO_LOJA',
        'RESOLVIDA',
        'ARQUIVADA'
    )),
    CONSTRAINT ck_conversa_id_externo_nao_vazio
        CHECK (id_externo IS NULL OR btrim(id_externo) <> '')
);

COMMENT ON TABLE  conversa IS
    'Thread de atendimento, venha do WhatsApp, da mensageria do marketplace ou do e-mail. E a unidade de trabalho: as perguntas uteis do atendimento sao sobre o thread, nao sobre a mensagem.';
COMMENT ON COLUMN conversa.tenant_id IS
    'Parte da identidade do registro. Sem DEFAULT de proposito: bug de contexto deve virar erro, nao linha gravada.';
COMMENT ON COLUMN conversa.cliente_id IS
    'Opcional: a primeira mensagem de um desconhecido nao tem cliente. Amarrar depois e trabalho do atendimento, nao motivo para inventar um cliente.';
COMMENT ON COLUMN conversa.ultima_mensagem_em IS
    'Materializado de proposito: e a coluna de ordenacao da fila de atendimento. Mantido pela ingestao junto com a gravacao da mensagem.';
COMMENT ON COLUMN conversa.anonimizado_em IS
    'Exclusao a pedido do titular (LGPD Art. 18, VI): preserva a existencia do atendimento para metrica de processo, sem preservar o que a pessoa escreveu.';
COMMENT ON COLUMN conversa.dados_origem IS
    'Campo de extensao (decisao 0002): payload da fonte que nao coube no modelo canonico.';

-- Fila de atendimento: "o que esta esperando resposta, mais recente
-- primeiro". NULLS LAST porque conversa sem mensagem ainda nao e fila.
CREATE INDEX ix_conversa_tenant_ultima_mensagem
    ON conversa (tenant_id, ultima_mensagem_em DESC NULLS LAST);

CREATE INDEX ix_conversa_tenant_cliente
    ON conversa (tenant_id, cliente_id)
    WHERE cliente_id IS NOT NULL;

-- "as conversas deste pedido" — usado no atendimento e, na Fase 2, para
-- correlacionar problema de conversa com devolucao.
CREATE INDEX ix_conversa_tenant_pedido
    ON conversa (tenant_id, pedido_id)
    WHERE pedido_id IS NOT NULL;

CREATE UNIQUE INDEX uq_conversa_origem
    ON conversa (tenant_id, canal_id, id_externo)
    WHERE id_externo IS NOT NULL;

ALTER TABLE conversa ENABLE ROW LEVEL SECURITY;
ALTER TABLE conversa FORCE  ROW LEVEL SECURITY;

CREATE POLICY conversa_select ON conversa
    FOR SELECT
    USING (tenant_id = app_current_tenant_id());

CREATE POLICY conversa_insert ON conversa
    FOR INSERT
    WITH CHECK (tenant_id = app_current_tenant_id());

CREATE POLICY conversa_update ON conversa
    FOR UPDATE
    USING (tenant_id = app_current_tenant_id())
    WITH CHECK (tenant_id = app_current_tenant_id());

CREATE POLICY conversa_delete ON conversa
    FOR DELETE
    USING (tenant_id = app_current_tenant_id());

GRANT SELECT, INSERT, UPDATE ON TABLE conversa TO app_aplicacao;


-- ---------------------------------------------------------------------
-- mensagem
-- ---------------------------------------------------------------------
CREATE TABLE mensagem (
    id                     uuid        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id              uuid        NOT NULL,

    conversa_id            uuid        NOT NULL,

    -- direcao: do ponto de vista DA LOJA. ENTRADA = a loja recebeu.
    -- Ponto de vista fixado no comentario de proposito: e a ambiguidade
    -- que faz metrica de tempo de resposta sair invertida.
    direcao                text        NOT NULL,

    -- autor_tipo: quem escreveu. ATENDENTE e AUTOMACAO sao separados
    -- porque medir "quanto a IA respondeu sozinha" e um objetivo do
    -- produto, e depois de misturar os dois nao da mais para separar.
    -- CANAL cobre mensagem de sistema do marketplace ("o envio foi
    -- postado"), que nao e pessoa nenhuma.
    autor_tipo             text        NOT NULL,

    -- autor_identificacao: quem, dentro do tipo (login do atendente,
    -- apelido do comprador). DADO PESSOAL: entra na anonimizacao da
    -- conversa. Vale a mesma ressalva da decisao 0012: identificacao de
    -- pessoa em trilha operacional nao vira metrica de desempenho
    -- individual.
    autor_identificacao    text,

    -- conteudo: o texto. NULL quando a mensagem e so anexo, ou depois de
    -- anonimizada.
    conteudo               text,
    tipo_conteudo          text        NOT NULL DEFAULT 'TEXTO',

    -- anexos: metadados dos arquivos (nome, tipo, url na fonte, tamanho).
    -- jsonb e nao tabela porque nao consultamos anexo por si; ele so
    -- acompanha a mensagem. Se um dia houver busca por anexo, vira tabela.
    -- O ARQUIVO em si nao e guardado no banco.
    anexos                 jsonb       NOT NULL DEFAULT '[]'::jsonb,

    enviada_em             timestamptz NOT NULL,
    lida_em                timestamptz,

    id_externo             text,
    dados_origem           jsonb       NOT NULL DEFAULT '{}'::jsonb,
    sincronizado_em        timestamptz,
    criado_em              timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_mensagem PRIMARY KEY (id),
    CONSTRAINT uq_mensagem_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_mensagem_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_mensagem_conversa
        FOREIGN KEY (tenant_id, conversa_id) REFERENCES conversa (tenant_id, id),

    CONSTRAINT ck_mensagem_direcao CHECK (direcao IN ('ENTRADA', 'SAIDA')),
    CONSTRAINT ck_mensagem_autor_tipo CHECK (autor_tipo IN (
        'CLIENTE',
        'ATENDENTE',
        'AUTOMACAO',
        'CANAL'
    )),
    CONSTRAINT ck_mensagem_tipo_conteudo CHECK (tipo_conteudo IN (
        'TEXTO',
        'IMAGEM',
        'AUDIO',
        'VIDEO',
        'ARQUIVO',
        'LOCALIZACAO',
        'SISTEMA'
    )),
    CONSTRAINT ck_mensagem_id_externo_nao_vazio
        CHECK (id_externo IS NULL OR btrim(id_externo) <> ''),
    -- anexos e uma LISTA. jsonb aceitaria objeto ou escalar sem reclamar,
    -- e um adaptador gravando {} em vez de [] quebraria quem itera.
    CONSTRAINT ck_mensagem_anexos_e_lista
        CHECK (jsonb_typeof(anexos) = 'array')
);

-- Sem atualizado_em de proposito: mensagem enviada nao muda. O que muda
-- (lida_em) tem coluna propria, e anonimizacao e evento raro registrado
-- na conversa. Diferente das demais tabelas por natureza do dado, nao por
-- esquecimento.
COMMENT ON TABLE  mensagem IS
    'Mensagem de um thread de atendimento. Imutavel na pratica: o que muda tem coluna propria (lida_em). Conteudo e dado pessoal - ver bloco de LGPD na V011.';
COMMENT ON COLUMN mensagem.tenant_id IS
    'Parte da identidade do registro. Sem DEFAULT de proposito: bug de contexto deve virar erro, nao linha gravada.';
COMMENT ON COLUMN mensagem.direcao IS
    'Do ponto de vista DA LOJA: ENTRADA = a loja recebeu, SAIDA = a loja enviou. Ambiguidade aqui inverte metrica de tempo de resposta.';
COMMENT ON COLUMN mensagem.autor_tipo IS
    'ATENDENTE e AUTOMACAO separados de proposito: medir quanto a IA respondeu sozinha e objetivo do produto, e depois de misturar nao da para separar.';
COMMENT ON COLUMN mensagem.autor_identificacao IS
    'Dado pessoal; entra na anonimizacao da conversa. Nao vira metrica de desempenho individual (mesma ressalva da decisao 0012).';
COMMENT ON COLUMN mensagem.anexos IS
    'Metadados dos arquivos (nome, tipo, url na fonte). O arquivo em si nao e guardado no banco. Sempre uma lista JSON.';
COMMENT ON COLUMN mensagem.dados_origem IS
    'Campo de extensao (decisao 0002): payload da fonte que nao coube no modelo canonico.';

-- O caminho quente e unico: abrir a conversa e ler as mensagens em ordem.
CREATE INDEX ix_mensagem_tenant_conversa_enviada
    ON mensagem (tenant_id, conversa_id, enviada_em);

CREATE UNIQUE INDEX uq_mensagem_origem
    ON mensagem (tenant_id, conversa_id, id_externo)
    WHERE id_externo IS NOT NULL;

ALTER TABLE mensagem ENABLE ROW LEVEL SECURITY;
ALTER TABLE mensagem FORCE  ROW LEVEL SECURITY;

CREATE POLICY mensagem_select ON mensagem
    FOR SELECT
    USING (tenant_id = app_current_tenant_id());

CREATE POLICY mensagem_insert ON mensagem
    FOR INSERT
    WITH CHECK (tenant_id = app_current_tenant_id());

CREATE POLICY mensagem_update ON mensagem
    FOR UPDATE
    USING (tenant_id = app_current_tenant_id())
    WITH CHECK (tenant_id = app_current_tenant_id());

CREATE POLICY mensagem_delete ON mensagem
    FOR DELETE
    USING (tenant_id = app_current_tenant_id());

-- UPDATE concedido apesar de a mensagem ser imutavel: e por ele que
-- lida_em e a anonimizacao de conteudo acontecem. DELETE nao, pelo mesmo
-- motivo da V007 — exclusao do titular e anonimizacao, nao remocao.
GRANT SELECT, INSERT, UPDATE ON TABLE mensagem TO app_aplicacao;
