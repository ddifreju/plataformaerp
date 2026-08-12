-- =====================================================================
-- V012 — evento_ingerido (a trava de idempotencia da ingestao)
-- =====================================================================
-- Convencoes gerais da Fase 1: cabecalho da V005.
--
-- Esta tabela existe para uma unica pergunta: "eu ja vi este evento?".
-- E a base da tarefa 12 (pipeline de ingestao com idempotencia).
--
-- ---------------------------------------------------------------------
-- SEMANTICA — as tres situacoes possiveis
-- ---------------------------------------------------------------------
-- Chave natural: (tenant_id, canal_id, tipo_evento, id_externo).
--
-- 1. CHAVE NOVA -> processa. Insere a linha e cria/atualiza o dado
--    canonico.
--
-- 2. CHAVE JA VISTA, hash_payload IGUAL -> NO-OP.
--    E o reenvio: webhook repetido, retry de rede, reprocessamento manual
--    da mesma janela. Nada muda, nada e gravado, nenhum efeito colateral.
--    E o caso mais comum na pratica e o que impede pedido duplicado.
--
-- 3. CHAVE JA VISTA, hash_payload DIFERENTE -> ATUALIZACAO LEGITIMA.
--    O mesmo pedido mudou de status, ganhou rastreio, teve item alterado.
--    A linha e atualizada (novo hash, novo payload, status volta para
--    RECEBIDO) e o dado canonico e reprocessado.
--
-- SEM o hash, 2 e 3 seriam indistinguiveis, e so haveria duas saidas
-- ruins: ou ignorar tudo que ja foi visto (e perder atualizacao de
-- status, que e a maior parte do trafego de marketplace), ou reprocessar
-- tudo sempre (e pagar reescrita constante do banco inteiro).
--
-- ---------------------------------------------------------------------
-- COMO A INGESTAO USA ISTO (receita, uma instrucao so, atomica)
-- ---------------------------------------------------------------------
--   INSERT INTO evento_ingerido
--       (tenant_id, canal_id, tipo_evento, id_externo, hash_payload,
--        payload_bruto, status, recebido_em)
--   VALUES (?, ?, ?, ?, ?, ?, 'RECEBIDO', now())
--   ON CONFLICT (tenant_id, canal_id, tipo_evento, id_externo)
--   DO UPDATE SET hash_payload  = excluded.hash_payload,
--                 payload_bruto = excluded.payload_bruto,
--                 status        = 'RECEBIDO',
--                 recebido_em   = excluded.recebido_em,
--                 processado_em = NULL,
--                 tentativas    = 0,
--                 erro_mensagem = NULL,
--                 atualizado_em = now()
--   WHERE evento_ingerido.hash_payload IS DISTINCT FROM excluded.hash_payload
--   RETURNING id;
--
-- Se voltar linha: e o caso 1 ou 3, processe. Se NAO voltar linha
-- nenhuma: e o caso 2, encerre — reenvio identico, nada a fazer.
-- O `WHERE` no DO UPDATE e o que carrega toda a semantica; sem ele, o
-- reenvio identico viraria um UPDATE inutil por evento.
--
-- Notas que evitam duas horas de depuracao:
--   - Isto e UMA instrucao: nao ha janela entre "consultar se existe" e
--     "inserir". Verificar antes com SELECT e depois inserir tem corrida
--     e duplica pedido sob concorrencia — que e exatamente o problema
--     que esta tabela existe para eliminar.
--   - Com RLS, valem a policy de INSERT (WITH CHECK) e a de UPDATE
--     (USING + WITH CHECK) na mesma instrucao. Como tenant_id faz parte
--     da chave de conflito e e sempre o do contexto, ambas passam.
--   - `IS DISTINCT FROM` e nao `<>`: com `<>`, hash NULL de um lado
--     tornaria a condicao NULL e o UPDATE nao aconteceria em silencio.
--   - Se a fonte nao oferecer id proprio para o evento (webhook anonimo),
--     o adaptador SINTETIZA um id_externo deterministico a partir do
--     payload. Ele pode inventar a CHAVE; nunca o DADO (regra 5).
-- =====================================================================

CREATE TABLE evento_ingerido (
    id               uuid        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id        uuid        NOT NULL,

    canal_id         uuid        NOT NULL,

    -- tipo_evento: o que este payload representa. Faz parte da chave
    -- porque o mesmo id externo pode existir em dominios diferentes da
    -- mesma fonte (o id 123 pode ser um pedido E uma devolucao no ML).
    tipo_evento      text        NOT NULL,

    -- id_externo NOT NULL aqui (ao contrario das demais tabelas): sem ele
    -- nao ha chave natural, e sem chave natural nao ha idempotencia. Se a
    -- fonte nao fornece, o adaptador sintetiza (ver cabecalho).
    id_externo       text        NOT NULL,

    -- hash_payload: sha256 do payload BRUTO, em hexadecimal minusculo.
    -- Calculado sobre os bytes recebidos, ANTES de qualquer parse ou
    -- normalizacao: reserializar o JSON antes de hashear faria a mesma
    -- mensagem gerar hashes diferentes conforme a ordem das chaves, e o
    -- caso 2 viraria caso 3 — reprocessamento eterno.
    hash_payload     char(64)    NOT NULL,

    -- payload_bruto: o que a fonte mandou, guardado como veio.
    -- PORQUE GUARDAR: permite reprocessar sem chamar a API de novo
    -- (essencial quando o mapeamento do adaptador tiver bug, que e
    -- quando mais se precisa reprocessar), e e a prova do que a fonte
    -- disse quando um numero e contestado (regra 3 do CLAUDE.md).
    -- ATENCAO DE LGPD: carrega dado pessoal do comprador. E o principal
    -- candidato a politica de retencao — ver a nota no fim do arquivo.
    payload_bruto    jsonb       NOT NULL,

    status           text        NOT NULL DEFAULT 'RECEBIDO',
    recebido_em      timestamptz NOT NULL DEFAULT now(),
    processado_em    timestamptz,

    -- tentativas e erro_mensagem: e o suficiente para diagnosticar sem
    -- construir uma fila de retry no banco. Fila de verdade, se for
    -- preciso, e problema do Camel (decisao 0001), nao desta tabela.
    tentativas       integer     NOT NULL DEFAULT 0,
    erro_mensagem    text,

    -- ------------------------------------------------------------------
    -- Rastreabilidade: o que este evento produziu.
    -- Referencia POLIMORFICA, sem FK, e isso e deliberado: o alvo muda de
    -- tabela conforme o tipo. A alternativa (uma coluna FK por tabela
    -- possivel) seriam 8 colunas quase sempre nulas, e cada entidade nova
    -- viraria migration aqui. O preco assumido: o banco nao garante que
    -- entidade_id aponte para algo existente. E aceitavel porque isto e
    -- ponteiro de DIAGNOSTICO ("de que payload saiu este pedido?"), nunca
    -- relacao de negocio — nenhuma regra le por aqui.
    -- ------------------------------------------------------------------
    entidade_tipo    text,
    entidade_id      uuid,

    atualizado_em    timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_evento_ingerido PRIMARY KEY (id),
    CONSTRAINT uq_evento_ingerido_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_evento_ingerido_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_evento_ingerido_canal
        FOREIGN KEY (tenant_id, canal_id) REFERENCES canal (tenant_id, id),

    -- A CHAVE DA IDEMPOTENCIA. Constraint (nao apenas indice) para que o
    -- ON CONFLICT do cabecalho possa inferi-la pela lista de colunas.
    -- tenant_id vem primeiro: alem de ser o exigido pela decisao 0010,
    -- garante que dois tenants com o mesmo pedido externo nunca colidam —
    -- colisao entre tenants seria um erro que a aplicacao nem conseguiria
    -- enxergar, porque o RLS esconde a linha do outro.
    CONSTRAINT uq_evento_ingerido_chave_natural
        UNIQUE (tenant_id, canal_id, tipo_evento, id_externo),

    CONSTRAINT ck_evento_ingerido_status CHECK (status IN (
        'RECEBIDO',
        'PROCESSANDO',
        'PROCESSADO',
        'IGNORADO',
        'ERRO'
    )),
    CONSTRAINT ck_evento_ingerido_tipo_evento CHECK (tipo_evento IN (
        'PEDIDO',
        'ITEM_PEDIDO',
        'PRODUTO',
        'VARIACAO',
        'CLIENTE',
        'DEVOLUCAO',
        'CONVERSA',
        'MENSAGEM',
        'CUSTO',
        'ESTOQUE',
        'OUTRO'
    )),
    CONSTRAINT ck_evento_ingerido_entidade_tipo CHECK (entidade_tipo IS NULL OR entidade_tipo IN (
        'PEDIDO',
        'ITEM_PEDIDO',
        'PRODUTO',
        'VARIACAO',
        'CLIENTE',
        'DEVOLUCAO',
        'CONVERSA',
        'MENSAGEM',
        'CUSTO'
    )),
    CONSTRAINT ck_evento_ingerido_id_externo_nao_vazio
        CHECK (btrim(id_externo) <> ''),
    -- sha256 em hex minusculo: 64 caracteres. O CHECK pega cedo o
    -- adaptador que gravou hex maiusculo ou base64 — dois formatos
    -- diferentes para o mesmo conteudo fariam o caso 2 virar caso 3.
    CONSTRAINT ck_evento_ingerido_hash_formato
        CHECK (hash_payload ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_evento_ingerido_tentativas_nao_negativas
        CHECK (tentativas >= 0)
);

COMMENT ON TABLE  evento_ingerido IS
    'Trava de idempotencia da ingestao. Chave natural (tenant_id, canal_id, tipo_evento, id_externo). Reenvio com o MESMO hash e no-op; hash diferente e atualizacao legitima e dispara reprocessamento.';
COMMENT ON COLUMN evento_ingerido.tenant_id IS
    'Parte da identidade do registro. Sem DEFAULT de proposito: bug de contexto deve virar erro, nao linha gravada.';
COMMENT ON COLUMN evento_ingerido.tipo_evento IS
    'O que o payload representa. Faz parte da chave porque o mesmo id externo pode existir em dominios diferentes da mesma fonte.';
COMMENT ON COLUMN evento_ingerido.hash_payload IS
    'sha256 hex do payload BRUTO, calculado antes de qualquer parse. Reserializar antes de hashear faria a mesma mensagem gerar hashes diferentes e reprocessar para sempre.';
COMMENT ON COLUMN evento_ingerido.payload_bruto IS
    'O que a fonte mandou, como veio. Permite reprocessar sem rechamar a API e prova o que a fonte disse. Carrega dado pessoal: e o principal alvo de politica de retencao.';
COMMENT ON COLUMN evento_ingerido.entidade_id IS
    'Ponteiro de diagnostico para a linha canonica gerada. Polimorfico e SEM FK de proposito: o alvo muda de tabela. Nenhuma regra de negocio le por aqui.';

-- ---------------------------------------------------------------------
-- Indices
-- ---------------------------------------------------------------------
-- Fila de trabalho: "o que falta processar". PARCIAL, e a economia aqui
-- e grande: em regime, quase toda linha esta PROCESSADA, e o indice
-- parcial guarda so a fracao pendente, permanecendo pequeno e quente
-- mesmo com milhoes de eventos historicos.
CREATE INDEX ix_evento_ingerido_pendentes
    ON evento_ingerido (tenant_id, recebido_em)
    WHERE status IN ('RECEBIDO', 'PROCESSANDO', 'ERRO');

-- Caminho inverso da rastreabilidade: "de que evento saiu este pedido?".
CREATE INDEX ix_evento_ingerido_entidade
    ON evento_ingerido (tenant_id, entidade_tipo, entidade_id)
    WHERE entidade_id IS NOT NULL;

-- Expurgo por retencao (ver nota no fim) e diagnostico por periodo.
CREATE INDEX ix_evento_ingerido_tenant_recebido_em
    ON evento_ingerido (tenant_id, recebido_em DESC);

ALTER TABLE evento_ingerido ENABLE ROW LEVEL SECURITY;
ALTER TABLE evento_ingerido FORCE  ROW LEVEL SECURITY;

CREATE POLICY evento_ingerido_select ON evento_ingerido
    FOR SELECT
    USING (tenant_id = app_current_tenant_id());

CREATE POLICY evento_ingerido_insert ON evento_ingerido
    FOR INSERT
    WITH CHECK (tenant_id = app_current_tenant_id());

CREATE POLICY evento_ingerido_update ON evento_ingerido
    FOR UPDATE
    USING (tenant_id = app_current_tenant_id())
    WITH CHECK (tenant_id = app_current_tenant_id());

CREATE POLICY evento_ingerido_delete ON evento_ingerido
    FOR DELETE
    USING (tenant_id = app_current_tenant_id());

-- UNICA tabela da Fase 1 com DELETE para a aplicacao (excecao anunciada
-- na convencao 6 da V005). Motivo: aqui DELETE nao apaga dado de negocio,
-- apaga copia de payload ja transformado em dado canonico. E o expurgo e
-- necessidade concreta:
--   - o payload bruto e o maior volume do banco por larga margem;
--   - guardar dado pessoal alem do necessario contraria a minimizacao da
--     LGPD.
-- PENDENTE (mesma natureza da pendencia da decisao 0012): por quanto
-- tempo guardar payload bruto e decisao de negocio/juridica. Ate ela
-- existir, nada e expurgado — e nao expurgar e o lado seguro para o
-- diagnostico, embora seja o lado inseguro para a LGPD. Registrar em
-- docs/PENDENCIAS.md.
-- ATENCAO: apagar a linha do evento REABRE a janela de duplicacao — se o
-- mesmo evento chegar de novo depois do expurgo, ele sera tratado como
-- novo. Por isso o expurgo so pode alcancar eventos ja PROCESSADOS e
-- antigos o bastante para a fonte nao os reenviar, e por isso as chaves
-- naturais das tabelas de negocio (uq_pedido_origem e irmas) sao a
-- segunda trava, independente desta.
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE evento_ingerido TO app_aplicacao;
