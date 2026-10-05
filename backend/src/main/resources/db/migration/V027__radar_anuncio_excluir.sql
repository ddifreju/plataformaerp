-- Ação em lote "excluir anúncios": a aplicação só apaga anúncio que não está
-- no ar no marketplace e não tem histórico de preço na Central de ações.
GRANT DELETE ON radar_anuncio TO app_aplicacao;
