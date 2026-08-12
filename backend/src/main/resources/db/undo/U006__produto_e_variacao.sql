-- =====================================================================
-- U006 — Desfaz V006__produto_e_variacao.sql
-- =====================================================================
-- Rodar DEPOIS de U008 (item_pedido referencia variacao) e ANTES de U005
-- (produto e variacao referenciam canal). Undos rodam em ordem
-- decrescente, entao a ordem natural ja esta correta.
--
-- Dentro deste arquivo a ordem e inversa a da V006: variacao primeiro,
-- porque variacao referencia produto.
--
-- Depois de aplicar, limpe o historico do Flyway:
--     DELETE FROM flyway_schema_history WHERE version = '006';
--
-- ATENCAO: undo restaura o SCHEMA, nao os DADOS.
-- =====================================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'app_aplicacao') THEN
        IF to_regclass('public.variacao') IS NOT NULL THEN
            REVOKE ALL ON TABLE public.variacao FROM app_aplicacao;
        END IF;
        IF to_regclass('public.produto') IS NOT NULL THEN
            REVOKE ALL ON TABLE public.produto FROM app_aplicacao;
        END IF;
    END IF;
END
$$;

-- ------------------------------------------------------------ variacao --
DROP POLICY IF EXISTS variacao_delete ON variacao;
DROP POLICY IF EXISTS variacao_update ON variacao;
DROP POLICY IF EXISTS variacao_insert ON variacao;
DROP POLICY IF EXISTS variacao_select ON variacao;

ALTER TABLE IF EXISTS variacao NO FORCE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS variacao DISABLE ROW LEVEL SECURITY;

DROP INDEX IF EXISTS ix_variacao_tenant_gtin;
DROP INDEX IF EXISTS uq_variacao_origem;
DROP INDEX IF EXISTS ix_variacao_tenant_produto;

DROP TABLE IF EXISTS variacao;

-- ------------------------------------------------------------- produto --
DROP POLICY IF EXISTS produto_delete ON produto;
DROP POLICY IF EXISTS produto_update ON produto;
DROP POLICY IF EXISTS produto_insert ON produto;
DROP POLICY IF EXISTS produto_select ON produto;

ALTER TABLE IF EXISTS produto NO FORCE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS produto DISABLE ROW LEVEL SECURITY;

DROP INDEX IF EXISTS uq_produto_origem;

DROP TABLE IF EXISTS produto;
