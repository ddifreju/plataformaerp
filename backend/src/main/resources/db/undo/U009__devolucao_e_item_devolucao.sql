-- =====================================================================
-- U009 — Desfaz V009__devolucao_e_item_devolucao.sql
-- =====================================================================
-- Rodar DEPOIS de U010 (custo tem FK para devolucao) e ANTES de U008
-- (devolucao e item_devolucao referenciam pedido e item_pedido). A ordem
-- decrescente dos undos ja garante os dois.
--
-- Dentro deste arquivo: item_devolucao primeiro, porque referencia
-- devolucao.
--
-- Depois de aplicar, limpe o historico do Flyway:
--     DELETE FROM flyway_schema_history WHERE version = '009';
--
-- ATENCAO: undo restaura o SCHEMA, nao os DADOS.
-- =====================================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'app_aplicacao') THEN
        IF to_regclass('public.item_devolucao') IS NOT NULL THEN
            REVOKE ALL ON TABLE public.item_devolucao FROM app_aplicacao;
        END IF;
        IF to_regclass('public.devolucao') IS NOT NULL THEN
            REVOKE ALL ON TABLE public.devolucao FROM app_aplicacao;
        END IF;
    END IF;
END
$$;

-- ------------------------------------------------------ item_devolucao --
DROP POLICY IF EXISTS item_devolucao_delete ON item_devolucao;
DROP POLICY IF EXISTS item_devolucao_update ON item_devolucao;
DROP POLICY IF EXISTS item_devolucao_insert ON item_devolucao;
DROP POLICY IF EXISTS item_devolucao_select ON item_devolucao;

ALTER TABLE IF EXISTS item_devolucao NO FORCE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS item_devolucao DISABLE ROW LEVEL SECURITY;

DROP INDEX IF EXISTS ix_item_devolucao_tenant_item_pedido;
DROP INDEX IF EXISTS ix_item_devolucao_tenant_devolucao;

DROP TABLE IF EXISTS item_devolucao;

-- ----------------------------------------------------------- devolucao --
DROP POLICY IF EXISTS devolucao_delete ON devolucao;
DROP POLICY IF EXISTS devolucao_update ON devolucao;
DROP POLICY IF EXISTS devolucao_insert ON devolucao;
DROP POLICY IF EXISTS devolucao_select ON devolucao;

ALTER TABLE IF EXISTS devolucao NO FORCE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS devolucao DISABLE ROW LEVEL SECURITY;

DROP INDEX IF EXISTS uq_devolucao_origem;
DROP INDEX IF EXISTS ix_devolucao_tenant_aberta_em;
DROP INDEX IF EXISTS ix_devolucao_tenant_pedido;

DROP TABLE IF EXISTS devolucao;
