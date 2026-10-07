-- Identificadores do pedido fora do Radar, para a busca achar o pedido por
-- qualquer número que a lojista tenha em mãos. Ficam vazios até existir a
-- integração com o marketplace (numero_externo, codigo_rastreio) e a
-- emissão de NF-e (nota_fiscal_numero, nota_fiscal_chave): nenhum valor é
-- inventado.
ALTER TABLE radar_pedido
 ADD COLUMN numero_externo varchar(60),
 ADD COLUMN nota_fiscal_numero varchar(20),
 ADD COLUMN nota_fiscal_chave varchar(44)
  CHECK (nota_fiscal_chave IS NULL OR nota_fiscal_chave ~ '^[0-9]{44}$'),
 ADD COLUMN codigo_rastreio varchar(60);
CREATE INDEX ON radar_pedido (tenant_id, numero_externo) WHERE numero_externo IS NOT NULL;
