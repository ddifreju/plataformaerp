-- =====================================================================
-- V033 — Lojas do cliente e anúncio por loja (decisão 0036)
-- =====================================================================
-- 1. radar_loja: cada conta que a empresa tem num marketplace ("Mercado
--    Livre Loja X", "Shopee Loja Y"). Pode haver várias no mesmo
--    marketplace; o nome é escolhido pela lojista para não se perder.
--    conectada_em fica nulo até a conexão real (autorização do vendedor,
--    depois do CNPJ): a tela nunca diz "conectada" antes disso (regra 5).
--    Remover é soft delete: anúncios antigos continuam apontando para ela.
--
-- 2. radar_anuncio ganha:
--    - loja_id: em qual loja o anúncio vai subir (nulo nos anúncios de
--      antes, que só sabiam o marketplace);
--    - estoque: a quantidade que a lojista escolheu anunciar (livre, por
--      decisão dela; não é limitada pelo estoque físico);
--    - estado PRONTO: passou na conferência e espera a loja ser conectada.
-- =====================================================================

CREATE TABLE radar_loja (
    id            uuid        PRIMARY KEY,
    tenant_id     uuid        NOT NULL REFERENCES tenant(id),
    marketplace   varchar(40) NOT NULL,
    nome          varchar(60) NOT NULL CHECK (btrim(nome) <> ''),
    conectada_em  timestamptz,
    excluida_em   timestamptz,
    criado_em     timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id)
);
-- Dois nomes iguais confundiriam a lojista; só vale entre as lojas ativas.
CREATE UNIQUE INDEX uq_radar_loja_nome ON radar_loja (tenant_id, lower(nome))
 WHERE excluida_em IS NULL;

ALTER TABLE radar_loja ENABLE ROW LEVEL SECURITY;
ALTER TABLE radar_loja FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON radar_loja
 USING (tenant_id = app_current_tenant_id())
 WITH CHECK (tenant_id = app_current_tenant_id());
GRANT SELECT, INSERT, UPDATE ON radar_loja TO app_aplicacao;

ALTER TABLE radar_anuncio
 ADD COLUMN loja_id uuid,
 ADD COLUMN estoque integer CHECK (estoque >= 0),
 ADD CONSTRAINT fk_radar_anuncio_loja FOREIGN KEY (tenant_id, loja_id)
  REFERENCES radar_loja (tenant_id, id);
CREATE INDEX ON radar_anuncio (tenant_id, loja_id);

ALTER TABLE radar_anuncio DROP CONSTRAINT radar_anuncio_estado_check;
ALTER TABLE radar_anuncio ADD CONSTRAINT radar_anuncio_estado_check
 CHECK (estado IN ('RASCUNHO', 'PRONTO', 'SIMULADO', 'PAUSADO'));
