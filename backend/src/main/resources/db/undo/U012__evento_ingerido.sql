-- =====================================================================
-- U012 — Desfaz V012__evento_ingerido.sql
-- =====================================================================
-- PRIMEIRO undo a rodar (undos vao em ordem decrescente: U012 ... U001).
-- Nada referencia evento_ingerido; ela referencia canal e tenant.
--
-- Depois de aplicar, limpe o historico do Flyway:
--     DELETE FROM flyway_schema_history WHERE version = '012';
--
-- ATENCAO ESPECIFICA DESTE UNDO: apagar esta tabela apaga a MEMORIA do
-- que ja foi ingerido. Ao recriar e reprocessar a mesma janela de
-- eventos, todos serao tratados como novos. Isso NAO duplica pedido,
-- porque as chaves naturais das tabelas de negocio
-- (uq_pedido_origem, uq_devolucao_origem, uq_cliente_origem, ...) sao uma
-- segunda trava independente — mas so se o pipeline usar upsert por essa
-- chave, e nao INSERT cego. Se o pipeline fizer INSERT cego, este undo
-- duplica dado. Verifique antes de rodar.
-- =====================================================================

DO $$
BEGIN
    IF to_regclass('public.evento_ingerido') IS NOT NULL
       AND EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'app_aplicacao') THEN
        REVOKE ALL ON TABLE public.evento_ingerido FROM app_aplicacao;
    END IF;
END
$$;

DROP POLICY IF EXISTS evento_ingerido_delete ON evento_ingerido;
DROP POLICY IF EXISTS evento_ingerido_update ON evento_ingerido;
DROP POLICY IF EXISTS evento_ingerido_insert ON evento_ingerido;
DROP POLICY IF EXISTS evento_ingerido_select ON evento_ingerido;

ALTER TABLE IF EXISTS evento_ingerido NO FORCE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS evento_ingerido DISABLE ROW LEVEL SECURITY;

DROP INDEX IF EXISTS ix_evento_ingerido_tenant_recebido_em;
DROP INDEX IF EXISTS ix_evento_ingerido_entidade;
DROP INDEX IF EXISTS ix_evento_ingerido_pendentes;

DROP TABLE IF EXISTS evento_ingerido;
