-- Desfaz a V030.
ALTER TABLE radar_pedido
 DROP COLUMN numero_externo,
 DROP COLUMN nota_fiscal_numero,
 DROP COLUMN nota_fiscal_chave,
 DROP COLUMN codigo_rastreio;
