-- Cadastros do Radar: clientes, fornecedores, categorias e embalagens.
-- Mesmo padrão da V017: chave composta (tenant_id, id), RLS forçada com
-- policy única de isolamento, app_aplicacao sem DELETE (desativação lógica
-- pela coluna ativo).

CREATE TABLE radar_cliente (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenant(id),
 nome varchar(200) NOT NULL CHECK (btrim(nome) <> ''),
 email varchar(320) CHECK (email IS NULL OR email ~ '^[^[:space:]@]+@[^[:space:]@]+$'),
 telefone varchar(40), cidade varchar(120),
 uf varchar(2) CHECK (uf IS NULL OR uf ~ '^[A-Z]{2}$'),
 observacao varchar(1000), ativo boolean NOT NULL DEFAULT true,
 criado_em timestamptz NOT NULL DEFAULT now(),
 UNIQUE (tenant_id, id)
);

CREATE TABLE radar_fornecedor (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenant(id),
 nome varchar(200) NOT NULL CHECK (btrim(nome) <> ''),
 documento varchar(18), contato varchar(160),
 email varchar(320) CHECK (email IS NULL OR email ~ '^[^[:space:]@]+@[^[:space:]@]+$'),
 telefone varchar(40),
 prazo_entrega_dias integer CHECK (prazo_entrega_dias IS NULL OR prazo_entrega_dias BETWEEN 0 AND 365),
 observacao varchar(1000), ativo boolean NOT NULL DEFAULT true,
 criado_em timestamptz NOT NULL DEFAULT now(),
 UNIQUE (tenant_id, id), UNIQUE (tenant_id, nome)
);

CREATE TABLE radar_categoria (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenant(id),
 nome varchar(120) NOT NULL CHECK (btrim(nome) <> ''),
 descricao varchar(500), ativo boolean NOT NULL DEFAULT true,
 criado_em timestamptz NOT NULL DEFAULT now(),
 UNIQUE (tenant_id, id), UNIQUE (tenant_id, nome)
);

CREATE TABLE radar_embalagem (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenant(id),
 nome varchar(120) NOT NULL CHECK (btrim(nome) <> ''),
 custo numeric(18,2) NOT NULL CHECK (custo >= 0),
 comprimento_cm numeric(8,1) CHECK (comprimento_cm IS NULL OR comprimento_cm > 0),
 largura_cm numeric(8,1) CHECK (largura_cm IS NULL OR largura_cm > 0),
 altura_cm numeric(8,1) CHECK (altura_cm IS NULL OR altura_cm > 0),
 peso_g integer CHECK (peso_g IS NULL OR peso_g >= 0),
 ativo boolean NOT NULL DEFAULT true,
 criado_em timestamptz NOT NULL DEFAULT now(),
 UNIQUE (tenant_id, id), UNIQUE (tenant_id, nome)
);

-- Vínculos opcionais. A chave composta impede apontar para cadastro de
-- outra empresa mesmo que alguém descubra o id.
ALTER TABLE radar_produto
 ADD COLUMN categoria_id uuid,
 ADD COLUMN embalagem_id uuid,
 ADD FOREIGN KEY (tenant_id, categoria_id) REFERENCES radar_categoria (tenant_id, id),
 ADD FOREIGN KEY (tenant_id, embalagem_id) REFERENCES radar_embalagem (tenant_id, id);

ALTER TABLE radar_pedido
 ADD COLUMN cliente_id uuid,
 ADD FOREIGN KEY (tenant_id, cliente_id) REFERENCES radar_cliente (tenant_id, id);

DO $$ DECLARE t text; BEGIN
 FOREACH t IN ARRAY ARRAY['radar_cliente','radar_fornecedor','radar_categoria','radar_embalagem'] LOOP
  EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
  EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
  EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (tenant_id = app_current_tenant_id()) WITH CHECK (tenant_id = app_current_tenant_id())', t);
  EXECUTE format('GRANT SELECT, INSERT, UPDATE ON %I TO app_aplicacao', t);
  EXECUTE format('CREATE INDEX ON %I (tenant_id)', t);
 END LOOP;
END $$;
