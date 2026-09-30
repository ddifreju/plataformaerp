-- Operational workspace. Existing analytics tables remain intact.
CREATE TABLE radar_produto (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenant(id), sku varchar(80) NOT NULL,
 nome varchar(250) NOT NULL, marca varchar(120) NOT NULL DEFAULT '', ncm varchar(8) NOT NULL DEFAULT '',
 descricao text NOT NULL DEFAULT '', custo numeric(18,2) NOT NULL CHECK(custo>=0),
 preco numeric(18,2) NOT NULL CHECK(preco>=0), fisico integer NOT NULL DEFAULT 0 CHECK(fisico>=0),
 reservado integer NOT NULL DEFAULT 0 CHECK(reservado>=0 AND reservado<=fisico),
 minimo integer NOT NULL DEFAULT 5 CHECK(minimo>=0), criado_em timestamptz NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,id), UNIQUE(tenant_id,sku)
);
CREATE TABLE radar_anuncio (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenant(id), produto_id uuid NOT NULL,
 canal varchar(40) NOT NULL, titulo varchar(250) NOT NULL, preco numeric(18,2) NOT NULL CHECK(preco>0),
 estado varchar(30) NOT NULL DEFAULT 'RASCUNHO', versao integer NOT NULL DEFAULT 1,
 criado_em timestamptz NOT NULL DEFAULT now(), UNIQUE(tenant_id,id), UNIQUE(tenant_id,produto_id,canal),
 FOREIGN KEY(tenant_id,produto_id) REFERENCES radar_produto(tenant_id,id),
 CHECK(estado IN ('RASCUNHO','SIMULADO','PAUSADO'))
);
CREATE TABLE radar_pedido (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenant(id), produto_id uuid NOT NULL,
 numero varchar(40) NOT NULL, canal varchar(40) NOT NULL, cliente varchar(160) NOT NULL,
 quantidade integer NOT NULL CHECK(quantidade>0), preco numeric(18,2) NOT NULL CHECK(preco>0),
 custo_unitario numeric(18,2) NOT NULL CHECK(custo_unitario>=0),
 comissao numeric(18,2) NOT NULL CHECK(comissao>=0), frete numeric(18,2) NOT NULL CHECK(frete>=0),
 imposto numeric(18,2) NOT NULL CHECK(imposto>=0), ads numeric(18,2) NOT NULL CHECK(ads>=0),
 embalagem numeric(18,2) NOT NULL CHECK(embalagem>=0), desconto numeric(18,2) NOT NULL CHECK(desconto>=0),
 estado varchar(30) NOT NULL DEFAULT 'RESERVADO', conciliado boolean NOT NULL DEFAULT false,
 fonte varchar(30) NOT NULL DEFAULT 'LOCAL', criado_em timestamptz NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,id), UNIQUE(tenant_id,numero),
 FOREIGN KEY(tenant_id,produto_id) REFERENCES radar_produto(tenant_id,id),
 CHECK(estado IN ('RESERVADO','SEPARADO','EXPEDIDO','CANCELADO','DEVOLVIDO'))
);
CREATE TABLE radar_movimento (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenant(id), produto_id uuid NOT NULL,
 pedido_id uuid, tipo varchar(40) NOT NULL, fisico_delta integer NOT NULL, reserva_delta integer NOT NULL,
 motivo varchar(500) NOT NULL, ator uuid NOT NULL, criado_em timestamptz NOT NULL DEFAULT now(),
 FOREIGN KEY(tenant_id,produto_id) REFERENCES radar_produto(tenant_id,id),
 FOREIGN KEY(tenant_id,pedido_id) REFERENCES radar_pedido(tenant_id,id)
);
CREATE TABLE radar_lancamento (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenant(id), pedido_id uuid,
 tipo varchar(40) NOT NULL, valor numeric(18,2) NOT NULL, fonte varchar(80) NOT NULL,
 criado_em timestamptz NOT NULL DEFAULT now(),
 FOREIGN KEY(tenant_id,pedido_id) REFERENCES radar_pedido(tenant_id,id)
);
CREATE TABLE radar_titulo (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenant(id), descricao varchar(250) NOT NULL,
 tipo varchar(20) NOT NULL CHECK(tipo IN ('PAGAR','RECEBER')), valor numeric(18,2) NOT NULL CHECK(valor>0),
 vencimento date NOT NULL, estado varchar(20) NOT NULL DEFAULT 'ABERTO' CHECK(estado IN ('ABERTO','BAIXADO')),
 pedido_id uuid, criado_em timestamptz NOT NULL DEFAULT now(),
 FOREIGN KEY(tenant_id,pedido_id) REFERENCES radar_pedido(tenant_id,id)
);
CREATE TABLE radar_acao (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenant(id), anuncio_id uuid NOT NULL,
 tipo varchar(30) NOT NULL, antes numeric(18,2) NOT NULL, depois numeric(18,2) NOT NULL,
 versao integer NOT NULL, motivo varchar(500) NOT NULL, estado varchar(20) NOT NULL DEFAULT 'PENDENTE',
 proposto_por uuid NOT NULL, aprovado_por uuid, criado_em timestamptz NOT NULL DEFAULT now(),
 FOREIGN KEY(tenant_id,anuncio_id) REFERENCES radar_anuncio(tenant_id,id),
 CHECK(estado IN ('PENDENTE','EXECUTADA','REJEITADA','EXPIRADA'))
);
CREATE TABLE radar_registro (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenant(id), tipo varchar(40) NOT NULL,
 dados jsonb NOT NULL, criado_em timestamptz NOT NULL DEFAULT now(), UNIQUE(tenant_id,id)
);
CREATE TABLE radar_auditoria (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenant(id), ator uuid NOT NULL,
 operacao varchar(80) NOT NULL, recurso varchar(100) NOT NULL, detalhes jsonb NOT NULL,
 criado_em timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE radar_comando (
 tenant_id uuid NOT NULL REFERENCES tenant(id), id uuid NOT NULL, ator uuid NOT NULL,
 hash varchar(64) NOT NULL, resultado jsonb NOT NULL, criado_em timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(tenant_id,id)
);
DO $$ DECLARE t text; BEGIN
 FOREACH t IN ARRAY ARRAY['radar_produto','radar_anuncio','radar_pedido','radar_movimento','radar_lancamento','radar_titulo','radar_acao','radar_registro','radar_auditoria','radar_comando'] LOOP
  EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);
  EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',t);
  EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (tenant_id=app_current_tenant_id()) WITH CHECK (tenant_id=app_current_tenant_id())',t);
  EXECUTE format('GRANT SELECT,INSERT ON %I TO app_aplicacao',t);
  EXECUTE format('CREATE INDEX ON %I (tenant_id)',t);
 END LOOP;
END $$;
GRANT UPDATE ON radar_produto,radar_anuncio,radar_pedido,radar_titulo,radar_acao,radar_registro TO app_aplicacao;
ALTER TABLE usuario DROP CONSTRAINT ck_usuario_papel;
ALTER TABLE usuario ADD CONSTRAINT ck_usuario_papel CHECK(papel IN ('DONO','GESTOR','ANALISTA','FINANCEIRO','ATENDIMENTO','ESTOQUE','MARKETING'));
