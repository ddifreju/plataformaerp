-- Desfaz a V020. Só para bases de desenvolvimento: apaga imagens, kits,
-- vínculos com fornecedores e as variações (linhas com pai_id). Exporte antes.
DROP TABLE radar_produto_imagem, radar_produto_fornecedor, radar_kit_item;
DELETE FROM radar_produto WHERE pai_id IS NOT NULL;
ALTER TABLE radar_produto
 DROP COLUMN tipo, DROP COLUMN pai_id, DROP COLUMN atributos_variacao, DROP COLUMN tipos_variacao,
 DROP COLUMN gtin, DROP COLUMN motivo_sem_gtin, DROP COLUMN origem, DROP COLUMN unidade, DROP COLUMN cest,
 DROP COLUMN preco_promocional, DROP COLUMN peso_liquido_kg, DROP COLUMN peso_bruto_kg,
 DROP COLUMN largura_cm, DROP COLUMN altura_cm, DROP COLUMN comprimento_cm, DROP COLUMN volumes,
 DROP COLUMN formato_embalagem, DROP COLUMN controla_estoque, DROP COLUMN maximo, DROP COLUMN sob_encomenda,
 DROP COLUMN dias_preparacao, DROP COLUMN modelo, DROP COLUMN condicao, DROP COLUMN garantia_tipo,
 DROP COLUMN garantia_meses, DROP COLUMN video_url, DROP COLUMN keywords, DROP COLUMN descricao_seo,
 DROP COLUMN tags, DROP COLUMN atributos, DROP COLUMN campos_adicionais, DROP COLUMN unidades_por_caixa,
 DROP COLUMN linha_produto, DROP COLUMN permite_venda, DROP COLUMN gtin_tributavel,
 DROP COLUMN unidade_tributavel, DROP COLUMN fator_conversao, DROP COLUMN ipi_codigo_enquadramento,
 DROP COLUMN ipi_enquadramento_legal, DROP COLUMN ipi_valor_fixo, DROP COLUMN ex_tipi,
 DROP COLUMN is_aliquota_especifica, DROP COLUMN qtd_monofasia, DROP COLUMN qtd_monofasia_retencao,
 DROP COLUMN observacoes_internas, DROP COLUMN atualizado_em;
