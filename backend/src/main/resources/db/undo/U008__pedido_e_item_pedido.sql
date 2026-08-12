-- =====================================================================
-- U008 — Desfaz V008__pedido_e_item_pedido.sql
-- =====================================================================
-- Rodar DEPOIS de U009 (devolucao/item_devolucao referenciam pedido e
-- item_pedido), U010 (custo referencia pedido e item_pedido) e U011
-- (conversa referencia pedido). A ordem decrescente dos undos garante.
--
-- Dentro deste arquivo: item_pedido primeiro, porque referencia pedido.
--
-- Depois de aplicar, limpe o historico do Flyway:
--     DELETE FROM flyway_schema_history WHERE version = '008';
--
-- ATENCAO: apaga o historico de vendas. Undo restaura o SCHEMA, nao os
-- DADOS. Em producao, dump antes — sem excecao.
-- =====================================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'app_aplicacao') THEN
        IF to_regclass('public.item_pedido') IS NOT NULL THEN
            REVOKE ALL ON TABLE public.item_pedido FROM app_aplicacao;
        END IF;
        IF to_regclass('public.pedido') IS NOT NULL THEN
            REVOKE ALL ON TABLE public.pedido FROM app_aplicacao;
        END IF;
    END IF;
END
$$;

-- --------------------------------------------------------- item_pedido --
DROP POLICY IF EXISTS item_pedido_delete ON item_pedido;
DROP POLICY IF EXISTS item_pedido_update ON item_pedido;
DROP POLICY IF EXISTS item_pedido_insert ON item_pedido;
DROP POLICY IF EXISTS item_pedido_select ON item_pedido;

ALTER TABLE IF EXISTS item_pedido NO FORCE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS item_pedido DISABLE ROW LEVEL SECURITY;

DROP INDEX IF EXISTS uq_item_pedido_origem;
DROP INDEX IF EXISTS ix_item_pedido_tenant_variacao;
DROP INDEX IF EXISTS ix_item_pedido_tenant_pedido;

DROP TABLE IF EXISTS item_pedido;

-- -------------------------------------------------------------- pedido --
DROP POLICY IF EXISTS pedido_delete ON pedido;
DROP POLICY IF EXISTS pedido_update ON pedido;
DROP POLICY IF EXISTS pedido_insert ON pedido;
DROP POLICY IF EXISTS pedido_select ON pedido;

ALTER TABLE IF EXISTS pedido NO FORCE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS pedido DISABLE ROW LEVEL SECURITY;

DROP INDEX IF EXISTS uq_pedido_origem;
DROP INDEX IF EXISTS ix_pedido_tenant_cliente;
DROP INDEX IF EXISTS ix_pedido_tenant_canal_feito_em;
DROP INDEX IF EXISTS ix_pedido_tenant_feito_em;

DROP TABLE IF EXISTS pedido;
