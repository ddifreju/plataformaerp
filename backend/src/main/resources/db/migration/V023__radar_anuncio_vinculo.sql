-- Anúncios por loja: situação no marketplace, id do anúncio lá fora e
-- importação. Todo anúncio continua obrigatoriamente vinculado a um produto
-- (produto_id NOT NULL desde a V017); importado sem produto correspondente,
-- o Radar cria o produto e o marca como incompleto.

-- Um produto pode ter mais de um anúncio no mesmo canal (ex.: clássico e
-- premium no Mercado Livre). Quem identifica o anúncio lá fora é o id_externo.
ALTER TABLE radar_anuncio DROP CONSTRAINT radar_anuncio_tenant_id_produto_id_canal_key;

ALTER TABLE radar_anuncio
 ADD COLUMN id_externo varchar(60),
 ADD COLUMN sku_externo varchar(80),
 -- Situação no marketplace. Sem loja conectada, nada foi publicado.
 ADD COLUMN situacao_ecommerce varchar(15) NOT NULL DEFAULT 'NAO_PUBLICADO'
  CHECK (situacao_ecommerce IN ('NAO_PUBLICADO', 'ATIVO', 'PAUSADO', 'REJEITADO', 'ENCERRADO')),
 ADD COLUMN motivo_rejeicao varchar(500),
 ADD COLUMN erro_integracao varchar(500),
 ADD COLUMN origem varchar(12) NOT NULL DEFAULT 'MANUAL' CHECK (origem IN ('MANUAL', 'IMPORTACAO')),
 ADD COLUMN atualizado_em timestamptz NOT NULL DEFAULT now();

CREATE UNIQUE INDEX uq_radar_anuncio_externo ON radar_anuncio (tenant_id, canal, id_externo)
 WHERE id_externo IS NOT NULL;
CREATE INDEX ON radar_anuncio (tenant_id, produto_id);

-- Produto criado a partir de um anúncio importado: entra incompleto até a
-- lojista salvar o cadastro pela tela.
ALTER TABLE radar_produto
 ADD COLUMN incompleto boolean NOT NULL DEFAULT false,
 ADD COLUMN origem_cadastro varchar(12) NOT NULL DEFAULT 'MANUAL'
  CHECK (origem_cadastro IN ('MANUAL', 'ANUNCIO'));
