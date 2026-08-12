-- =====================================================================
-- U002 — Desfaz V002__tenant.sql
-- =====================================================================
-- Rodar SOMENTE depois de U004 e U003: consulta_auditada tem FK para
-- tenant(id), e o GRANT de V004 referencia esta tabela.
--
-- DROP TABLE sem CASCADE de proposito: se ainda existir tabela de dados
-- apontando para tenant, o comando falha e nos avisa em vez de derrubar
-- meio modelo em cascata.
--
-- Depois de aplicar, limpe o historico do Flyway:
--     DELETE FROM flyway_schema_history WHERE version = '002';
--
-- ATENCAO: este script apaga o catalogo de tenants. Em producao isso e
-- destrutivo e irreversivel sem backup. Undo de migration estrutural em
-- producao pressupoe dump previo.
-- =====================================================================

DROP TABLE IF EXISTS tenant;
