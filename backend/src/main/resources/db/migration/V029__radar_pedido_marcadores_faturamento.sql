-- Ações em lote dos pedidos: marcadores (etiquetas livres para organizar a
-- fila, ex.: "urgente", "presente") e data de faturamento informada pela
-- lojista. Sem NF-e emitida pelo Radar, a data é só um registro dela.
ALTER TABLE radar_pedido
 ADD COLUMN marcadores jsonb NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(marcadores) = 'array'),
 ADD COLUMN data_faturamento date;
