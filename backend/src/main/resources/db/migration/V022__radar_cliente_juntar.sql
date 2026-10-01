-- Cadastro incompleto (criado por pedido) que recebe um CPF/CNPJ já cadastrado
-- é juntado ao cliente existente: os pedidos passam para ele e o incompleto é
-- apagado. A aplicação só apaga cliente marcado como incompleto.
GRANT DELETE ON radar_cliente TO app_aplicacao;
GRANT UPDATE (cliente_id) ON radar_cliente_anexo TO app_aplicacao;
