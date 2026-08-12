-- =====================================================================
-- U005 — Desfaz V005__canal.sql
-- =====================================================================
-- ORDEM: undos rodam em ordem DECRESCENTE (U012 ... U005, U004 ... U001).
-- Este e o ultimo undo da Fase 1 a rodar, porque canal e referenciado por
-- produto, variacao, cliente, pedido, devolucao, conversa e
-- evento_ingerido. Rodar antes deles falha por FK — e falhar e o
-- comportamento correto.
--
-- Depois de aplicar, limpe o historico do Flyway:
--     DELETE FROM flyway_schema_history WHERE version = '005';
--
-- ATENCAO: undo restaura o SCHEMA, nao os DADOS. Apagar canal apaga a
-- rastreabilidade de origem de tudo que ja foi ingerido.
-- =====================================================================

-- REVOKE explicito, espelhando o GRANT da V005. Tecnicamente o
-- DROP TABLE ja remove os privilegios junto; mantemos por simetria, e
-- para que este arquivo continue correto se um dia virar rollback
-- parcial. Guardado em DO porque o undo pode rodar em base onde o papel
-- (U004) ou a tabela ja nao existem.
DO $$
BEGIN
    IF to_regclass('public.canal') IS NOT NULL
       AND EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'app_aplicacao') THEN
        REVOKE ALL ON TABLE public.canal FROM app_aplicacao;
    END IF;
END
$$;

-- Policies na ordem inversa da criacao (espelho exato da V005).
DROP POLICY IF EXISTS canal_delete ON canal;
DROP POLICY IF EXISTS canal_update ON canal;
DROP POLICY IF EXISTS canal_insert ON canal;
DROP POLICY IF EXISTS canal_select ON canal;

ALTER TABLE IF EXISTS canal NO FORCE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS canal DISABLE ROW LEVEL SECURITY;

DROP INDEX IF EXISTS ix_canal_tenant_categoria_ativo;

-- Sem CASCADE de proposito: se ainda houver tabela apontando para canal,
-- queremos o erro, nao meio modelo derrubado em silencio.
DROP TABLE IF EXISTS canal;
