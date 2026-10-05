-- Vendedor excluído sai das listas e da escolha de vendedor do cliente, mas o
-- cadastro fica (aba "excluídos"): os clientes e pedidos antigos continuam
-- apontando para ele. Excluir também tira o acesso (desliga o usuário).
ALTER TABLE radar_vendedor ADD COLUMN excluido_em timestamptz;
