-- Desfaz a V033. Anúncios PRONTO voltam a ser rascunho antes da regra antiga.
UPDATE radar_anuncio SET estado = 'RASCUNHO' WHERE estado = 'PRONTO';
ALTER TABLE radar_anuncio DROP CONSTRAINT radar_anuncio_estado_check;
ALTER TABLE radar_anuncio ADD CONSTRAINT radar_anuncio_estado_check
 CHECK (estado IN ('RASCUNHO', 'SIMULADO', 'PAUSADO'));
DROP INDEX uq_radar_anuncio_produto_loja;
ALTER TABLE radar_anuncio DROP CONSTRAINT fk_radar_anuncio_loja;
ALTER TABLE radar_anuncio DROP COLUMN estoque, DROP COLUMN loja_id;
DROP TABLE radar_loja;
