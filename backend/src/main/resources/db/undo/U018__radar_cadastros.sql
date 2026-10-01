-- Desfaz a V018. Só para bases de desenvolvimento: apaga os cadastros e os
-- vínculos de produto e pedido com eles. Exporte os dados antes.
ALTER TABLE radar_pedido DROP COLUMN cliente_id;
ALTER TABLE radar_produto DROP COLUMN embalagem_id, DROP COLUMN categoria_id;
DROP TABLE radar_embalagem, radar_categoria, radar_fornecedor, radar_cliente;
