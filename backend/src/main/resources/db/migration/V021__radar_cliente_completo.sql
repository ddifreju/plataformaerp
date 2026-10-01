-- Cadastro completo de cliente: identificação fiscal, endereços, contatos,
-- CRM e anexos. Pedido sem cliente cadastrado passa a criar o cliente
-- automaticamente (origem PEDIDO, marcado como incompleto).
--
-- Documento (CPF/CNPJ) é dado pessoal: guardado completo para NF-e e para
-- reconhecer recorrência; a aplicação devolve mascarado nas listas.

ALTER TABLE radar_cliente
 ADD COLUMN codigo varchar(30),
 ADD COLUMN fantasia varchar(200),
 -- F física, J jurídica, E estrangeira (mora fora), B estrangeira residente no Brasil.
 ADD COLUMN tipo_pessoa char(1) NOT NULL DEFAULT 'F' CHECK (tipo_pessoa IN ('F', 'J', 'E', 'B')),
 -- Passaporte ou documento do país de origem (idEstrangeiro da NF-e).
 ADD COLUMN documento_estrangeiro varchar(20),
 ADD COLUMN pais varchar(60),
 -- CPF (11 dígitos) ou CNPJ (14, inclusive o alfanumérico de 2026).
 ADD COLUMN documento varchar(14) CHECK (documento IS NULL OR documento ~ '^([0-9]{11}|[0-9A-Z]{12}[0-9]{2})$'),
 -- 1 contribuinte de ICMS, 2 contribuinte isento, 9 não contribuinte (indIEDest da NF-e).
 ADD COLUMN contribuinte smallint CHECK (contribuinte IS NULL OR contribuinte IN (1, 2, 9)),
 ADD COLUMN inscricao_estadual varchar(20),
 ADD COLUMN inscricao_municipal varchar(20),
 ADD COLUMN tipos_contato jsonb NOT NULL DEFAULT '["CLIENTE"]'::jsonb,
 ADD COLUMN cep varchar(8) CHECK (cep IS NULL OR cep ~ '^[0-9]{8}$'),
 ADD COLUMN endereco varchar(200),
 ADD COLUMN numero varchar(20),
 ADD COLUMN complemento varchar(120),
 ADD COLUMN bairro varchar(120),
 ADD COLUMN municipio_ibge varchar(7) CHECK (municipio_ibge IS NULL OR municipio_ibge ~ '^[0-9]{7}$'),
 ADD COLUMN cobranca_diferente boolean NOT NULL DEFAULT false,
 ADD COLUMN cobranca_cep varchar(8) CHECK (cobranca_cep IS NULL OR cobranca_cep ~ '^[0-9]{8}$'),
 ADD COLUMN cobranca_endereco varchar(200),
 ADD COLUMN cobranca_numero varchar(20),
 ADD COLUMN cobranca_complemento varchar(120),
 ADD COLUMN cobranca_bairro varchar(120),
 ADD COLUMN cobranca_cidade varchar(120),
 ADD COLUMN cobranca_uf varchar(2) CHECK (cobranca_uf IS NULL OR cobranca_uf ~ '^[A-Z]{2}$'),
 ADD COLUMN telefone_adicional varchar(40),
 ADD COLUMN celular varchar(40),
 ADD COLUMN website varchar(300),
 ADD COLUMN email_nfe varchar(320) CHECK (email_nfe IS NULL OR email_nfe ~ '^[^[:space:]@]+@[^[:space:]@]+$'),
 ADD COLUMN observacoes_contato varchar(2000),
 ADD COLUMN pessoas_contato jsonb NOT NULL DEFAULT '[]'::jsonb,
 -- CRT: 1 Simples Nacional, 2 Simples com excesso de sublimite, 3 Regime normal, 4 MEI.
 ADD COLUMN regime_tributario smallint CHECK (regime_tributario IS NULL OR regime_tributario BETWEEN 1 AND 4),
 ADD COLUMN inscricao_suframa varchar(12),
 ADD COLUMN data_nascimento date,
 ADD COLUMN status_crm varchar(15) NOT NULL DEFAULT 'NOVO'
  CHECK (status_crm IN ('NOVO', 'EM_CONTATO', 'NEGOCIACAO', 'CLIENTE', 'FIDELIZADO', 'INATIVO', 'PERDIDO')),
 ADD COLUMN vendedor_id uuid,
 ADD COLUMN condicao_pagamento varchar(60),
 ADD COLUMN lista_preco varchar(60),
 ADD COLUMN limite_credito numeric(18,2) NOT NULL DEFAULT 0 CHECK (limite_credito >= 0),
 ADD COLUMN origem varchar(10) NOT NULL DEFAULT 'MANUAL' CHECK (origem IN ('MANUAL', 'PEDIDO', 'IMPORTACAO')),
 ADD COLUMN incompleto boolean NOT NULL DEFAULT false,
 ADD COLUMN atualizado_em timestamptz NOT NULL DEFAULT now(),
 ADD FOREIGN KEY (tenant_id, vendedor_id) REFERENCES usuario (tenant_id, id),
 ADD CHECK (jsonb_typeof(pessoas_contato) = 'array' AND jsonb_typeof(tipos_contato) = 'array'),
 -- Tipos de contato: CLIENTE, FORNECEDOR, TRANSPORTADOR (um cadastro pode ter mais de um).
 ADD CHECK (tipos_contato <@ '["CLIENTE", "FORNECEDOR", "TRANSPORTADOR"]'::jsonb);

-- Clientes que já existiam (cadastro simples da V018) ganham código e ficam
-- marcados como incompletos até alguém completar os dados da nota.
UPDATE radar_cliente c SET
 codigo = 'C' || lpad(n.ordem::text, 5, '0'),
 incompleto = true
FROM (SELECT id, row_number() OVER (PARTITION BY tenant_id ORDER BY criado_em, id) ordem
      FROM radar_cliente) n
WHERE n.id = c.id;

-- Pedidos antigos sem cliente cadastrado: cria um cliente por nome (por
-- empresa) e vincula, para a recorrência já nascer com o histórico.
INSERT INTO radar_cliente (id, tenant_id, nome, origem, incompleto, criado_em)
SELECT gen_random_uuid(), p.tenant_id, min(btrim(p.cliente)), 'PEDIDO', true, min(p.criado_em)
FROM radar_pedido p
WHERE p.cliente_id IS NULL AND btrim(p.cliente) <> ''
  AND NOT EXISTS (SELECT 1 FROM radar_cliente c
                  WHERE c.tenant_id = p.tenant_id AND lower(c.nome) = lower(btrim(p.cliente)))
GROUP BY p.tenant_id, lower(btrim(p.cliente));

UPDATE radar_cliente c SET codigo = 'C' || lpad(n.ordem::text, 5, '0')
FROM (SELECT id, row_number() OVER (PARTITION BY tenant_id ORDER BY criado_em, id) ordem
      FROM radar_cliente) n
WHERE n.id = c.id AND c.codigo IS NULL;

UPDATE radar_pedido p SET cliente_id = (
 SELECT c.id FROM radar_cliente c
 WHERE c.tenant_id = p.tenant_id AND lower(c.nome) = lower(btrim(p.cliente))
 ORDER BY c.criado_em LIMIT 1)
WHERE p.cliente_id IS NULL AND btrim(p.cliente) <> '';

ALTER TABLE radar_cliente ALTER COLUMN codigo SET NOT NULL;
CREATE UNIQUE INDEX uq_radar_cliente_codigo ON radar_cliente (tenant_id, codigo);
CREATE UNIQUE INDEX uq_radar_cliente_documento ON radar_cliente (tenant_id, documento) WHERE documento IS NOT NULL;
CREATE INDEX ON radar_cliente (tenant_id, lower(nome));
CREATE INDEX ON radar_pedido (tenant_id, cliente_id) WHERE cliente_id IS NOT NULL;

CREATE TABLE radar_cliente_anexo (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenant(id),
 cliente_id uuid NOT NULL,
 nome_arquivo varchar(200) NOT NULL,
 tipo_conteudo varchar(100) NOT NULL,
 dados bytea NOT NULL CHECK (octet_length(dados) <= 2097152),
 criado_em timestamptz NOT NULL DEFAULT now(),
 UNIQUE (tenant_id, id),
 FOREIGN KEY (tenant_id, cliente_id) REFERENCES radar_cliente (tenant_id, id)
);
CREATE INDEX ON radar_cliente_anexo (tenant_id, cliente_id);
ALTER TABLE radar_cliente_anexo ENABLE ROW LEVEL SECURITY;
ALTER TABLE radar_cliente_anexo FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON radar_cliente_anexo
 USING (tenant_id = app_current_tenant_id())
 WITH CHECK (tenant_id = app_current_tenant_id());
GRANT SELECT, INSERT, DELETE ON radar_cliente_anexo TO app_aplicacao;
