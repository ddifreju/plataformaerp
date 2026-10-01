-- Desfaz a V021. Só para bases de desenvolvimento: apaga anexos e os campos
-- novos do cliente. Clientes criados automaticamente pelos pedidos ficam
-- (os pedidos apontam para eles). Exporte antes.
DROP TABLE radar_cliente_anexo;
DROP INDEX IF EXISTS uq_radar_cliente_codigo;
DROP INDEX IF EXISTS uq_radar_cliente_documento;
ALTER TABLE radar_cliente
 DROP COLUMN codigo, DROP COLUMN fantasia, DROP COLUMN tipo_pessoa, DROP COLUMN documento, DROP COLUMN documento_estrangeiro, DROP COLUMN pais,
 DROP COLUMN contribuinte, DROP COLUMN inscricao_estadual, DROP COLUMN inscricao_municipal,
 DROP COLUMN tipos_contato, DROP COLUMN cep, DROP COLUMN endereco, DROP COLUMN numero,
 DROP COLUMN complemento, DROP COLUMN bairro, DROP COLUMN municipio_ibge,
 DROP COLUMN cobranca_diferente, DROP COLUMN cobranca_cep, DROP COLUMN cobranca_endereco,
 DROP COLUMN cobranca_numero, DROP COLUMN cobranca_complemento, DROP COLUMN cobranca_bairro,
 DROP COLUMN cobranca_cidade, DROP COLUMN cobranca_uf, DROP COLUMN telefone_adicional,
 DROP COLUMN celular, DROP COLUMN website, DROP COLUMN email_nfe, DROP COLUMN observacoes_contato,
 DROP COLUMN pessoas_contato, DROP COLUMN regime_tributario, DROP COLUMN inscricao_suframa,
 DROP COLUMN data_nascimento, DROP COLUMN status_crm, DROP COLUMN vendedor_id,
 DROP COLUMN condicao_pagamento, DROP COLUMN lista_preco, DROP COLUMN limite_credito,
 DROP COLUMN origem, DROP COLUMN incompleto, DROP COLUMN atualizado_em;
