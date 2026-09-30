-- Only for disposable development databases. Export operational data first.
DROP TABLE radar_comando,radar_auditoria,radar_registro,radar_acao,radar_titulo,radar_lancamento,radar_movimento,radar_pedido,radar_anuncio,radar_produto;
-- Existing users with new roles must be explicitly remapped before restoring V014's role constraint.
