-- =====================================================================
-- U011 — Desfaz V011__conversa_e_mensagem.sql
-- =====================================================================
-- Rodar ANTES de U008 (conversa referencia pedido), U007 (referencia
-- cliente) e U005 (referencia canal). A ordem decrescente dos undos ja
-- garante.
--
-- Dentro deste arquivo: mensagem primeiro, porque referencia conversa.
--
-- Depois de aplicar, limpe o historico do Flyway:
--     DELETE FROM flyway_schema_history WHERE version = '011';
--
-- ATENCAO: apaga historico de atendimento, que e dado pessoal. Undo
-- restaura o SCHEMA, nao os DADOS.
-- =====================================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'app_aplicacao') THEN
        IF to_regclass('public.mensagem') IS NOT NULL THEN
            REVOKE ALL ON TABLE public.mensagem FROM app_aplicacao;
        END IF;
        IF to_regclass('public.conversa') IS NOT NULL THEN
            REVOKE ALL ON TABLE public.conversa FROM app_aplicacao;
        END IF;
    END IF;
END
$$;

-- ------------------------------------------------------------ mensagem --
DROP POLICY IF EXISTS mensagem_delete ON mensagem;
DROP POLICY IF EXISTS mensagem_update ON mensagem;
DROP POLICY IF EXISTS mensagem_insert ON mensagem;
DROP POLICY IF EXISTS mensagem_select ON mensagem;

ALTER TABLE IF EXISTS mensagem NO FORCE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS mensagem DISABLE ROW LEVEL SECURITY;

DROP INDEX IF EXISTS uq_mensagem_origem;
DROP INDEX IF EXISTS ix_mensagem_tenant_conversa_enviada;

DROP TABLE IF EXISTS mensagem;

-- ------------------------------------------------------------ conversa --
DROP POLICY IF EXISTS conversa_delete ON conversa;
DROP POLICY IF EXISTS conversa_update ON conversa;
DROP POLICY IF EXISTS conversa_insert ON conversa;
DROP POLICY IF EXISTS conversa_select ON conversa;

ALTER TABLE IF EXISTS conversa NO FORCE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS conversa DISABLE ROW LEVEL SECURITY;

DROP INDEX IF EXISTS uq_conversa_origem;
DROP INDEX IF EXISTS ix_conversa_tenant_pedido;
DROP INDEX IF EXISTS ix_conversa_tenant_cliente;
DROP INDEX IF EXISTS ix_conversa_tenant_ultima_mensagem;

DROP TABLE IF EXISTS conversa;
