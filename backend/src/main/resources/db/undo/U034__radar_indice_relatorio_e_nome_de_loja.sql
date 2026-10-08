-- Desfaz a V034. Volta a exigir nome único na empresa inteira: falha se já houver
-- duas lojas ativas com o mesmo nome em marketplaces diferentes (renomeie antes).
DROP INDEX uq_radar_loja_nome;
CREATE UNIQUE INDEX uq_radar_loja_nome ON radar_loja (tenant_id, lower(nome))
 WHERE excluida_em IS NULL;
DROP INDEX idx_radar_lancamento_pedido;
