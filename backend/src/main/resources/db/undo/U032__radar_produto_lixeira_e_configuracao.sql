-- Desfaz a V032.
DROP TABLE radar_configuracao;
ALTER TABLE radar_produto DROP COLUMN excluido_em;
