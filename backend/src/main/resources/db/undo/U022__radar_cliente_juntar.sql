-- Desfaz a V022.
REVOKE DELETE ON radar_cliente FROM app_aplicacao;
REVOKE UPDATE (cliente_id) ON radar_cliente_anexo FROM app_aplicacao;
