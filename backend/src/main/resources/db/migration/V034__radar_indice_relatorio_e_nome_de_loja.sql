-- =====================================================================
-- V034 — Índice para os relatórios e nome de loja por marketplace
-- =====================================================================
-- 1. O lucro por pedido nos relatórios soma os lançamentos de cada pedido;
--    sem este índice o banco varreria todos os lançamentos da empresa.
-- 2. O nome da loja só não pode repetir dentro do mesmo marketplace:
--    "Casa Bonita" pode ter uma loja no Mercado Livre e outra na Shopee.
-- =====================================================================
CREATE INDEX idx_radar_lancamento_pedido ON radar_lancamento (tenant_id, pedido_id);

DROP INDEX uq_radar_loja_nome;
CREATE UNIQUE INDEX uq_radar_loja_nome ON radar_loja (tenant_id, marketplace, lower(nome))
 WHERE excluida_em IS NULL;
