-- Categorias vinculadas às categorias de cada marketplace, e embalagens
-- sugeridas com tags.

-- O nome da categoria é o da lojista (pode ser um "nome fantasia"); o nome
-- e o código do marketplace ficam no vínculo. Uma categoria tem no máximo um
-- vínculo por canal.
ALTER TABLE radar_categoria
 ADD COLUMN origem varchar(12) NOT NULL DEFAULT 'MANUAL' CHECK (origem IN ('MANUAL', 'IMPORTACAO'));

CREATE TABLE radar_categoria_canal (
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 categoria_id uuid NOT NULL,
 canal varchar(40) NOT NULL,
 codigo_externo varchar(60) NOT NULL CHECK (btrim(codigo_externo) <> ''),
 nome_externo varchar(300) NOT NULL CHECK (btrim(nome_externo) <> ''),
 atualizado_em timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY (tenant_id, categoria_id, canal),
 FOREIGN KEY (tenant_id, categoria_id) REFERENCES radar_categoria (tenant_id, id) ON DELETE CASCADE
);
-- Na importação, a categoria do marketplace leva direto à categoria da loja.
CREATE INDEX ON radar_categoria_canal (tenant_id, canal, codigo_externo);
ALTER TABLE radar_categoria_canal ENABLE ROW LEVEL SECURITY;
ALTER TABLE radar_categoria_canal FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON radar_categoria_canal
 USING (tenant_id = app_current_tenant_id())
 WITH CHECK (tenant_id = app_current_tenant_id());
GRANT SELECT, INSERT, UPDATE, DELETE ON radar_categoria_canal TO app_aplicacao;

-- Embalagens: tipo, tags (usadas depois pela calculadora de preços) e
-- marcação de sugerida (vem pronta; a lojista edita como qualquer outra).
ALTER TABLE radar_embalagem
 ADD COLUMN tipo varchar(15) NOT NULL DEFAULT 'OUTRO'
  CHECK (tipo IN ('CAIXA', 'ENVELOPE', 'ENVELOPE_BOLHA', 'SACO', 'TUBO', 'PAPELAO', 'OUTRO')),
 ADD COLUMN tags jsonb NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(tags) = 'array'),
 ADD COLUMN sugerida boolean NOT NULL DEFAULT false;
