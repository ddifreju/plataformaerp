-- =====================================================================
-- U003 — Desfaz V003__consulta_auditada.sql
-- =====================================================================
-- Rodar depois de U004 (que remove os GRANTs desta tabela) e antes de
-- U002 (a FK aponta para tenant).
--
-- Depois de aplicar, limpe o historico do Flyway:
--     DELETE FROM flyway_schema_history WHERE version = '003';
--
-- ATENCAO: destroi a trilha de auditoria. Em producao, exporte antes.
-- =====================================================================

-- Policies primeiro, na ordem inversa da criacao. Tecnicamente
-- DROP TABLE ja levaria as policies junto, mas derrubamos explicitamente
-- para que este arquivo seja o espelho exato da V003 — e para que o undo
-- continue correto caso alguem, no futuro, transforme o DROP TABLE em um
-- rollback parcial.
DROP POLICY IF EXISTS consulta_auditada_delete ON consulta_auditada;
DROP POLICY IF EXISTS consulta_auditada_update ON consulta_auditada;
DROP POLICY IF EXISTS consulta_auditada_insert ON consulta_auditada;
DROP POLICY IF EXISTS consulta_auditada_select ON consulta_auditada;

-- FORCE e ENABLE desligados explicitamente pelo mesmo motivo acima.
ALTER TABLE IF EXISTS consulta_auditada NO FORCE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS consulta_auditada DISABLE ROW LEVEL SECURITY;

-- O indice cai junto com a tabela; explicito por simetria com a V003.
DROP INDEX IF EXISTS ix_consulta_auditada_tenant_executado_em;

DROP TABLE IF EXISTS consulta_auditada;
