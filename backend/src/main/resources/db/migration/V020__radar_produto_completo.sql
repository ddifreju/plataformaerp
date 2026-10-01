-- Cadastro completo de produto: dados comerciais, fiscais, logísticos e de
-- marketplace; variações (até 3 tipos), kits, imagens e fornecedores.
--
-- Variação é uma linha de radar_produto com pai_id: assim pedido, estoque e
-- anúncio funcionam por variação sem tabela paralela. Kit é um produto cujo
-- estoque vem dos componentes (radar_kit_item).

ALTER TABLE radar_produto
 ADD COLUMN tipo varchar(10) NOT NULL DEFAULT 'SIMPLES'
  CHECK (tipo IN ('SIMPLES', 'KIT', 'VARIACAO')),
 ADD COLUMN pai_id uuid,
 ADD COLUMN atributos_variacao jsonb NOT NULL DEFAULT '{}'::jsonb,
 ADD COLUMN tipos_variacao jsonb NOT NULL DEFAULT '[]'::jsonb,
 ADD COLUMN gtin varchar(14) CHECK (gtin IS NULL OR gtin ~ '^([0-9]{8}|[0-9]{12,14})$'),
 ADD COLUMN motivo_sem_gtin varchar(30),
 ADD COLUMN origem smallint CHECK (origem IS NULL OR origem BETWEEN 0 AND 8),
 ADD COLUMN unidade varchar(6) NOT NULL DEFAULT 'UN',
 ADD COLUMN cest varchar(7) CHECK (cest IS NULL OR cest ~ '^[0-9]{7}$'),
 ADD COLUMN preco_promocional numeric(18,2) CHECK (preco_promocional IS NULL OR preco_promocional > 0),
 ADD COLUMN peso_liquido_kg numeric(10,3) CHECK (peso_liquido_kg IS NULL OR peso_liquido_kg >= 0),
 ADD COLUMN peso_bruto_kg numeric(10,3) CHECK (peso_bruto_kg IS NULL OR peso_bruto_kg >= 0),
 ADD COLUMN largura_cm numeric(8,1) CHECK (largura_cm IS NULL OR largura_cm > 0),
 ADD COLUMN altura_cm numeric(8,1) CHECK (altura_cm IS NULL OR altura_cm > 0),
 ADD COLUMN comprimento_cm numeric(8,1) CHECK (comprimento_cm IS NULL OR comprimento_cm > 0),
 ADD COLUMN volumes integer NOT NULL DEFAULT 1 CHECK (volumes BETWEEN 1 AND 999),
 ADD COLUMN formato_embalagem varchar(15) NOT NULL DEFAULT 'PACOTE_CAIXA'
  CHECK (formato_embalagem IN ('PACOTE_CAIXA', 'ROLO_CILINDRO', 'ENVELOPE')),
 ADD COLUMN controla_estoque boolean NOT NULL DEFAULT true,
 ADD COLUMN maximo integer CHECK (maximo IS NULL OR maximo >= 0),
 ADD COLUMN sob_encomenda boolean NOT NULL DEFAULT false,
 ADD COLUMN dias_preparacao integer CHECK (dias_preparacao IS NULL OR dias_preparacao BETWEEN 0 AND 90),
 ADD COLUMN modelo varchar(120),
 ADD COLUMN condicao varchar(15) NOT NULL DEFAULT 'NOVO'
  CHECK (condicao IN ('NOVO', 'USADO', 'RECONDICIONADO')),
 ADD COLUMN garantia_tipo varchar(15) CHECK (garantia_tipo IS NULL OR garantia_tipo IN ('VENDEDOR', 'FABRICANTE', 'SEM_GARANTIA')),
 ADD COLUMN garantia_meses integer CHECK (garantia_meses IS NULL OR garantia_meses BETWEEN 0 AND 120),
 ADD COLUMN video_url varchar(500) CHECK (video_url IS NULL OR video_url LIKE 'https://%'),
 ADD COLUMN keywords varchar(500),
 ADD COLUMN descricao_seo varchar(320),
 ADD COLUMN tags jsonb NOT NULL DEFAULT '[]'::jsonb,
 ADD COLUMN atributos jsonb NOT NULL DEFAULT '[]'::jsonb,
 ADD COLUMN campos_adicionais jsonb NOT NULL DEFAULT '[]'::jsonb,
 ADD COLUMN unidades_por_caixa integer CHECK (unidades_por_caixa IS NULL OR unidades_por_caixa >= 1),
 ADD COLUMN linha_produto varchar(120),
 ADD COLUMN permite_venda boolean NOT NULL DEFAULT true,
 -- Fiscal
 ADD COLUMN gtin_tributavel varchar(14) CHECK (gtin_tributavel IS NULL OR gtin_tributavel ~ '^([0-9]{8}|[0-9]{12,14})$'),
 ADD COLUMN unidade_tributavel varchar(6),
 ADD COLUMN fator_conversao numeric(12,4) CHECK (fator_conversao IS NULL OR fator_conversao > 0),
 ADD COLUMN ipi_codigo_enquadramento varchar(5),
 ADD COLUMN ipi_enquadramento_legal varchar(3) CHECK (ipi_enquadramento_legal IS NULL OR ipi_enquadramento_legal ~ '^[0-9]{3}$'),
 ADD COLUMN ipi_valor_fixo numeric(18,2) CHECK (ipi_valor_fixo IS NULL OR ipi_valor_fixo >= 0),
 ADD COLUMN ex_tipi varchar(3) CHECK (ex_tipi IS NULL OR ex_tipi ~ '^[0-9]{1,3}$'),
 ADD COLUMN is_aliquota_especifica numeric(7,4) CHECK (is_aliquota_especifica IS NULL OR is_aliquota_especifica >= 0),
 ADD COLUMN qtd_monofasia numeric(15,4) CHECK (qtd_monofasia IS NULL OR qtd_monofasia >= 0),
 ADD COLUMN qtd_monofasia_retencao numeric(15,4) CHECK (qtd_monofasia_retencao IS NULL OR qtd_monofasia_retencao >= 0),
 ADD COLUMN observacoes_internas text,
 ADD COLUMN atualizado_em timestamptz NOT NULL DEFAULT now(),
 ADD FOREIGN KEY (tenant_id, pai_id) REFERENCES radar_produto (tenant_id, id),
 ADD CHECK (pai_id IS NULL OR pai_id <> id),
 ADD CHECK (jsonb_typeof(tipos_variacao) = 'array' AND jsonb_array_length(tipos_variacao) <= 3);

CREATE INDEX ON radar_produto (tenant_id, pai_id) WHERE pai_id IS NOT NULL;

CREATE TABLE radar_kit_item (
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 kit_id uuid NOT NULL, componente_id uuid NOT NULL,
 quantidade integer NOT NULL CHECK (quantidade BETWEEN 1 AND 1000),
 PRIMARY KEY (tenant_id, kit_id, componente_id),
 CHECK (kit_id <> componente_id),
 FOREIGN KEY (tenant_id, kit_id) REFERENCES radar_produto (tenant_id, id),
 FOREIGN KEY (tenant_id, componente_id) REFERENCES radar_produto (tenant_id, id)
);

CREATE TABLE radar_produto_fornecedor (
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 produto_id uuid NOT NULL, fornecedor_id uuid NOT NULL,
 codigo_no_fornecedor varchar(60),
 PRIMARY KEY (tenant_id, produto_id, fornecedor_id),
 FOREIGN KEY (tenant_id, produto_id) REFERENCES radar_produto (tenant_id, id),
 FOREIGN KEY (tenant_id, fornecedor_id) REFERENCES radar_fornecedor (tenant_id, id)
);

-- Imagens no próprio banco (até 2 MB cada, 12 por produto, validado na
-- aplicação). Quando os canais forem conectados, migram para um storage com
-- URL pública.
CREATE TABLE radar_produto_imagem (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenant(id),
 produto_id uuid NOT NULL, ordem integer NOT NULL DEFAULT 0,
 tipo_conteudo varchar(20) NOT NULL CHECK (tipo_conteudo IN ('image/jpeg', 'image/png', 'image/webp')),
 dados bytea NOT NULL CHECK (octet_length(dados) <= 2097152),
 criado_em timestamptz NOT NULL DEFAULT now(),
 UNIQUE (tenant_id, id),
 FOREIGN KEY (tenant_id, produto_id) REFERENCES radar_produto (tenant_id, id)
);
CREATE INDEX ON radar_produto_imagem (tenant_id, produto_id);

DO $$ DECLARE t text; BEGIN
 FOREACH t IN ARRAY ARRAY['radar_kit_item','radar_produto_fornecedor','radar_produto_imagem'] LOOP
  EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
  EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
  EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (tenant_id = app_current_tenant_id()) WITH CHECK (tenant_id = app_current_tenant_id())', t);
 END LOOP;
END $$;
-- Composição de kit, fornecedores e imagens são substituídos a cada
-- salvamento do produto: a aplicação precisa de DELETE só nestas três.
GRANT SELECT, INSERT, DELETE ON radar_kit_item, radar_produto_fornecedor, radar_produto_imagem TO app_aplicacao;
GRANT UPDATE ON radar_produto_imagem TO app_aplicacao;
