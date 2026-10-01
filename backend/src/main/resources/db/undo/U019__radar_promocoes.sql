-- Desfaz a V019. Só para bases de desenvolvimento: os pedidos perdem o
-- vínculo com a promoção (o desconto gravado continua). Exporte antes.
ALTER TABLE radar_pedido DROP COLUMN promocao_id;
DROP TABLE radar_promocao;
