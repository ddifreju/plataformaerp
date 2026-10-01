-- Desfaz a V024. Apaga os vínculos de categoria com marketplace.
ALTER TABLE radar_embalagem DROP COLUMN tipo, DROP COLUMN tags, DROP COLUMN sugerida;
DROP TABLE radar_categoria_canal;
ALTER TABLE radar_categoria DROP COLUMN origem;
