-- =====================================================================
-- U010 — Desfaz V010__custo.sql
-- =====================================================================
-- Primeiro undo da Fase 1 a rodar entre as tabelas de negocio: custo
-- referencia pedido, item_pedido, devolucao e canal, e nada referencia
-- custo (a nao ser ela mesma). A ordem decrescente dos undos garante que
-- ele caia antes de todos eles.
--
-- Depois de aplicar, limpe o historico do Flyway:
--     DELETE FROM flyway_schema_history WHERE version = '010';
--
-- ATENCAO: apaga a base do calculo de margem. Undo restaura o SCHEMA,
-- nao os DADOS — e custo perdido nao se recompoe sozinho: parte dele vem
-- de fatura de canal que pode nao estar mais disponivel na API.
-- =====================================================================

DO $$
BEGIN
    IF to_regclass('public.custo') IS NOT NULL
       AND EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'app_aplicacao') THEN
        REVOKE ALL ON TABLE public.custo FROM app_aplicacao;
    END IF;
END
$$;

DROP POLICY IF EXISTS custo_delete ON custo;
DROP POLICY IF EXISTS custo_update ON custo;
DROP POLICY IF EXISTS custo_insert ON custo;
DROP POLICY IF EXISTS custo_select ON custo;

ALTER TABLE IF EXISTS custo NO FORCE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS custo DISABLE ROW LEVEL SECURITY;

DROP INDEX IF EXISTS uq_custo_origem;
DROP INDEX IF EXISTS ix_custo_tenant_devolucao;
DROP INDEX IF EXISTS ix_custo_tenant_competencia_natureza;
DROP INDEX IF EXISTS ix_custo_tenant_item_pedido;
DROP INDEX IF EXISTS ix_custo_tenant_pedido;

-- A auto-referencia (rateado_de_custo_id) cai junto com a tabela; nao
-- exige CASCADE porque nao ha dependente externo.
DROP TABLE IF EXISTS custo;
