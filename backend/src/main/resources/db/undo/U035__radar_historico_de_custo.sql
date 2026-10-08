-- Desfaz a V035. Apaga o histórico de custos gravado até aqui.
DROP TRIGGER radar_produto_custo_historico ON radar_produto;
DROP FUNCTION radar_registra_custo();
DROP TABLE radar_custo_historico;
