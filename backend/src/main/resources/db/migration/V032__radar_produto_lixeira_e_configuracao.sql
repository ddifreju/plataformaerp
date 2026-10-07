-- =====================================================================
-- V032 — Lixeira de produtos e configurações por empresa
-- =====================================================================
-- 1. radar_produto.excluido_em: excluir produto passa a mandar para a
--    lixeira (dá para restaurar). Produto com pedido, estoque ou anúncio
--    não pode sumir: o histórico (regra 3) aponta para ele. Apagar de vez
--    continua possível só para produto sem histórico nenhum, e só a
--    partir da lixeira.
--
-- 2. radar_configuracao: uma linha por empresa e por área de configuração
--    (chave "produtos", depois "vendas", "estoque"...). O valor é jsonb
--    porque cada área tem campos próprios, validados no backend antes de
--    gravar; o banco garante só a chave conhecida e o isolamento.
-- =====================================================================

ALTER TABLE radar_produto ADD COLUMN excluido_em timestamptz;

CREATE TABLE radar_configuracao (
    tenant_id      uuid        NOT NULL REFERENCES tenant(id),
    chave          varchar(40) NOT NULL CHECK (chave ~ '^[a-z_]{2,40}$'),
    valor          jsonb       NOT NULL CHECK (jsonb_typeof(valor) = 'object'),
    atualizado_em  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, chave)
);

ALTER TABLE radar_configuracao ENABLE ROW LEVEL SECURITY;
ALTER TABLE radar_configuracao FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON radar_configuracao
 USING (tenant_id = app_current_tenant_id())
 WITH CHECK (tenant_id = app_current_tenant_id());
GRANT SELECT, INSERT, UPDATE ON radar_configuracao TO app_aplicacao;
