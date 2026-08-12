-- =====================================================================
-- U007 — Desfaz V007__cliente.sql
-- =====================================================================
-- Rodar DEPOIS de U008 (pedido referencia cliente) e U011 (conversa
-- referencia cliente), e ANTES de U005 (cliente referencia canal). A
-- ordem decrescente dos undos ja garante isso.
--
-- Depois de aplicar, limpe o historico do Flyway:
--     DELETE FROM flyway_schema_history WHERE version = '007';
--
-- ATENCAO: apaga dado pessoal de compradores. Em producao isso e
-- destrutivo e irreversivel sem backup — e um backup de dado pessoal tem
-- as mesmas obrigacoes de LGPD do banco vivo.
-- =====================================================================

DO $$
BEGIN
    IF to_regclass('public.cliente') IS NOT NULL
       AND EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'app_aplicacao') THEN
        REVOKE ALL ON TABLE public.cliente FROM app_aplicacao;
    END IF;
END
$$;

DROP POLICY IF EXISTS cliente_delete ON cliente;
DROP POLICY IF EXISTS cliente_update ON cliente;
DROP POLICY IF EXISTS cliente_insert ON cliente;
DROP POLICY IF EXISTS cliente_select ON cliente;

ALTER TABLE IF EXISTS cliente NO FORCE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS cliente DISABLE ROW LEVEL SECURITY;

DROP INDEX IF EXISTS uq_cliente_origem;
DROP INDEX IF EXISTS ix_cliente_tenant_email;
DROP INDEX IF EXISTS ix_cliente_tenant_documento_hash;

DROP TABLE IF EXISTS cliente;
