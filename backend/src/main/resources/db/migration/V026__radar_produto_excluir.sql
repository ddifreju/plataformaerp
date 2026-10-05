-- Ação em lote "excluir produto": a aplicação só apaga produto sem histórico
-- (sem pedido, anúncio, movimento de estoque nem uso como componente de kit).
-- Com histórico, o caminho é inativar.
GRANT DELETE ON radar_produto TO app_aplicacao;
