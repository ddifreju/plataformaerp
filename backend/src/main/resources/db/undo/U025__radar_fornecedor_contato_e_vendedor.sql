-- Desfaz a V025. Só para bases de desenvolvimento: perde o vendedor padrão
-- dos clientes e os vendedores; fornecedores juntados a um contato existente
-- precisam ser revisados à mão.
ALTER TABLE radar_cliente DROP CONSTRAINT radar_cliente_tenant_id_vendedor_id_fkey;
UPDATE radar_cliente SET vendedor_id = NULL WHERE vendedor_id IS NOT NULL;
DROP TABLE radar_vendedor;
ALTER TABLE radar_cliente
 ADD FOREIGN KEY (tenant_id, vendedor_id) REFERENCES usuario (tenant_id, id);
ALTER TABLE radar_produto_fornecedor DROP CONSTRAINT radar_produto_fornecedor_tenant_id_fornecedor_id_fkey;
DELETE FROM radar_produto_fornecedor pf
 WHERE NOT EXISTS (SELECT 1 FROM radar_fornecedor f WHERE f.tenant_id = pf.tenant_id AND f.id = pf.fornecedor_id);
ALTER TABLE radar_produto_fornecedor
 ADD FOREIGN KEY (tenant_id, fornecedor_id) REFERENCES radar_fornecedor (tenant_id, id);
UPDATE radar_cliente SET tipos_contato = tipos_contato - 'FORNECEDOR'
 WHERE id NOT IN (SELECT id FROM radar_fornecedor);
DELETE FROM radar_cliente c WHERE c.id IN (SELECT id FROM radar_fornecedor)
 AND NOT EXISTS (SELECT 1 FROM radar_pedido p WHERE p.tenant_id = c.tenant_id AND p.cliente_id = c.id);
ALTER TABLE radar_cliente DROP COLUMN prazo_entrega_dias;
