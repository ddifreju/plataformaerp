-- =====================================================================
-- V003 — consulta_auditada + Row Level Security
-- =====================================================================
-- Atende a regra inegociavel 3 do CLAUDE.md: "toda resposta numerica e
-- rastreavel". Se o lojista contestar um numero, precisamos reproduzir
-- exatamente a query que gerou aquele numero e os IDs que ela devolveu.
--
-- Esta e tambem a PRIMEIRA tabela de dados do sistema, logo e o
-- laboratorio de isolamento: o padrao de RLS estabelecido aqui e o que
-- toda tabela da Fase 1 vai repetir.
-- =====================================================================

CREATE TABLE consulta_auditada (
    id                uuid        NOT NULL DEFAULT gen_random_uuid(),

    -- tenant_id faz parte da IDENTIDADE do registro, nao e coluna
    -- acessoria. NOT NULL sem default de proposito: quem escreve declara
    -- o tenant, e a policy de INSERT confere contra o GUC. Se puséssemos
    -- DEFAULT app_current_tenant_id(), um bug de contexto na aplicacao
    -- viraria linha gravada silenciosamente em vez de erro.
    tenant_id         uuid        NOT NULL,

    -- pergunta: o texto que originou a consulta (linguagem natural vinda
    -- da camada de IA, ou NULL quando a consulta nasceu de um endpoint
    -- deterministico).
    pergunta          text,

    -- sql_executado: o SQL literal que produziu o numero. E a prova.
    sql_executado     text        NOT NULL,

    -- ids_retornados: as chaves das linhas que compuseram o resultado.
    -- Guardar os IDs (e nao so o total) e o que permite auditar
    -- "por que deu 47 e nao 46" sem reprocessar nada.
    ids_retornados    uuid[]      NOT NULL DEFAULT '{}',

    -- linhas_retornadas: contagem real do resultado. Pode divergir do
    -- cardinal de ids_retornados quando truncamos a lista de IDs por
    -- volume — por isso e campo proprio, nao derivado.
    linhas_retornadas integer     NOT NULL DEFAULT 0,

    executado_em      timestamptz NOT NULL DEFAULT now(),

    -- executado_por: identidade do solicitante (usuario, job, agente).
    -- text e nao FK porque em Fase 0 ainda nao existe tabela de usuario,
    -- e a origem pode ser um processo sem usuario humano.
    executado_por     text,

    CONSTRAINT pk_consulta_auditada PRIMARY KEY (id),
    CONSTRAINT fk_consulta_auditada_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT ck_consulta_auditada_linhas_nao_negativas
        CHECK (linhas_retornadas >= 0),
    CONSTRAINT ck_consulta_auditada_sql_nao_vazio
        CHECK (btrim(sql_executado) <> '')
);

-- Indice com tenant_id na PRIMEIRA posicao: toda query da aplicacao
-- carrega o predicado de tenant, entao a coluna lider tem que ser ela.
-- executado_em DESC atende o acesso tipico ("ultimas consultas deste
-- tenant") sem sort adicional.
CREATE INDEX ix_consulta_auditada_tenant_executado_em
    ON consulta_auditada (tenant_id, executado_em DESC);

COMMENT ON TABLE  consulta_auditada IS
    'Trilha de auditoria de respostas numericas (regra 3 do CLAUDE.md): guarda a pergunta, o SQL executado e os IDs retornados. Append-only.';
COMMENT ON COLUMN consulta_auditada.tenant_id IS
    'Parte da identidade do registro. Sem DEFAULT de proposito: bug de contexto deve virar erro, nao linha gravada.';
COMMENT ON COLUMN consulta_auditada.sql_executado IS
    'SQL literal que produziu o numero. E a prova diante de contestacao do cliente.';
COMMENT ON COLUMN consulta_auditada.ids_retornados IS
    'IDs das linhas que compuseram o resultado. Pode ser truncado em resultados grandes; por isso linhas_retornadas e campo proprio.';


-- ---------------------------------------------------------------------
-- Row Level Security — SEGUNDA camada de isolamento
-- ---------------------------------------------------------------------
-- A PRIMEIRA camada e o predicado obrigatorio na aplicacao. RLS existe
-- para o dia em que alguem esquecer o predicado: o banco recusa, em vez
-- de vazar dado de outro lojista.

ALTER TABLE consulta_auditada ENABLE ROW LEVEL SECURITY;

-- FORCE ROW LEVEL SECURITY — nao e redundante, e essencial:
-- ENABLE sozinho NAO aplica as policies ao DONO da tabela. O dono
-- (o papel que roda as migrations) continuaria enxergando todas as
-- linhas de todos os tenants. Isso significa que:
--   a) um teste de isolamento rodando como dono passaria falsamente;
--   b) qualquer script administrativo, rotina de backup logico ou job
--      que use o usuario dono ignoraria o isolamento sem aviso.
-- Com FORCE, ate o dono precisa do GUC app.tenant_id setado.
-- Contrapartida assumida: manutencao cross-tenant exige um papel
-- separado com BYPASSRLS, criado de forma consciente e temporaria —
-- nunca o papel da aplicacao (ver V004).
ALTER TABLE consulta_auditada FORCE ROW LEVEL SECURITY;

-- Policies separadas por comando, de proposito. Uma unica policy FOR ALL
-- seria mais curta, porem esconde a diferenca entre USING (que filtra o
-- que ja existe) e WITH CHECK (que valida o que esta sendo gravado).
-- Separado, cada regra fica lida e revisada isoladamente.

-- SELECT: so enxerga linha do proprio tenant.
-- Sem GUC, app_current_tenant_id() = NULL, a comparacao vira NULL,
-- e NULL em policy e tratado como falso => zero linhas. Fail-closed.
CREATE POLICY consulta_auditada_select ON consulta_auditada
    FOR SELECT
    USING (tenant_id = app_current_tenant_id());

-- INSERT: WITH CHECK impede gravar linha carimbada com o tenant de
-- outro. INSERT nao tem USING (nao existe linha anterior para filtrar);
-- toda a validacao mora no WITH CHECK. Sem GUC, a condicao e NULL =>
-- falso => INSERT rejeitado. Tambem fail-closed.
CREATE POLICY consulta_auditada_insert ON consulta_auditada
    FOR INSERT
    WITH CHECK (tenant_id = app_current_tenant_id());

-- UPDATE: USING define quais linhas podem ser alcancadas; WITH CHECK
-- define como a linha pode ficar depois. Os dois sao necessarios —
-- so com USING seria possivel alcancar a propria linha e reescrever o
-- tenant_id dela para outro tenant ("dar" o registro a terceiro).
CREATE POLICY consulta_auditada_update ON consulta_auditada
    FOR UPDATE
    USING (tenant_id = app_current_tenant_id())
    WITH CHECK (tenant_id = app_current_tenant_id());

-- DELETE: so alcanca linha do proprio tenant.
CREATE POLICY consulta_auditada_delete ON consulta_auditada
    FOR DELETE
    USING (tenant_id = app_current_tenant_id());

-- Nota de coerencia: as policies de UPDATE e DELETE existem como defesa
-- em profundidade, mas o papel da aplicacao recebe apenas SELECT e
-- INSERT nesta tabela (V004). Trilha de auditoria e append-only: se a
-- aplicacao pudesse editar a prova, ela deixaria de ser prova.
