-- Promoções do Radar. Uma promoção vale para um produto (ou todos, quando
-- produto_id é nulo), opcionalmente num canal só, dentro de um período.
-- O pedido guarda qual promoção gerou o desconto, para a conta ser
-- rastreável depois.

CREATE TABLE radar_promocao (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenant(id),
 nome varchar(160) NOT NULL CHECK (btrim(nome) <> ''),
 tipo varchar(20) NOT NULL CHECK (tipo IN ('PERCENTUAL', 'VALOR_FIXO')),
 -- PERCENTUAL: 0,01 a 100,00 (%). VALOR_FIXO: reais por unidade.
 valor numeric(18,2) NOT NULL CHECK (valor > 0),
 produto_id uuid, canal varchar(40),
 inicio date NOT NULL, fim date NOT NULL,
 ativo boolean NOT NULL DEFAULT true,
 criado_em timestamptz NOT NULL DEFAULT now(),
 UNIQUE (tenant_id, id),
 CHECK (fim >= inicio),
 CHECK (tipo <> 'PERCENTUAL' OR valor <= 100),
 FOREIGN KEY (tenant_id, produto_id) REFERENCES radar_produto (tenant_id, id)
);

ALTER TABLE radar_pedido
 ADD COLUMN promocao_id uuid,
 ADD FOREIGN KEY (tenant_id, promocao_id) REFERENCES radar_promocao (tenant_id, id);

ALTER TABLE radar_promocao ENABLE ROW LEVEL SECURITY;
ALTER TABLE radar_promocao FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON radar_promocao
 USING (tenant_id = app_current_tenant_id())
 WITH CHECK (tenant_id = app_current_tenant_id());
GRANT SELECT, INSERT, UPDATE ON radar_promocao TO app_aplicacao;
CREATE INDEX ON radar_promocao (tenant_id);
