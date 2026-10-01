-- Desfaz a V023. Só para bases de desenvolvimento: falha se algum produto
-- tiver mais de um anúncio no mesmo canal (resolva antes de rodar).
ALTER TABLE radar_produto DROP COLUMN incompleto, DROP COLUMN origem_cadastro;
DROP INDEX uq_radar_anuncio_externo;
DROP INDEX IF EXISTS radar_anuncio_tenant_id_produto_id_idx;
ALTER TABLE radar_anuncio
 DROP COLUMN id_externo, DROP COLUMN sku_externo, DROP COLUMN situacao_ecommerce,
 DROP COLUMN motivo_rejeicao, DROP COLUMN erro_integracao, DROP COLUMN origem,
 DROP COLUMN atualizado_em;
ALTER TABLE radar_anuncio ADD CONSTRAINT radar_anuncio_tenant_id_produto_id_canal_key
 UNIQUE (tenant_id, produto_id, canal);
