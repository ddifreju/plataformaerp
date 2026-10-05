-- 1) Fornecedor passa a ser um contato (radar_cliente) com o tipo FORNECEDOR:
--    mesmo cadastro completo do cliente. A tabela radar_fornecedor fica sem
--    uso (não é apagada, para a migration ser reversível).
-- 2) Cadastro de vendedores (radar_vendedor). O "vendedor padrão" do cliente
--    passa a apontar para ele, e não mais para o usuário do sistema.

ALTER TABLE radar_cliente
 ADD COLUMN prazo_entrega_dias integer CHECK (prazo_entrega_dias IS NULL OR prazo_entrega_dias BETWEEN 0 AND 365);

-- Fornecedor que já é contato pelo mesmo CNPJ/CPF ganha o tipo FORNECEDOR.
UPDATE radar_cliente c SET tipos_contato = c.tipos_contato || '["FORNECEDOR"]'::jsonb
FROM radar_fornecedor f
WHERE f.tenant_id = c.tenant_id
  AND regexp_replace(upper(coalesce(f.documento, '')), '[^0-9A-Z]', '', 'g') = c.documento
  AND NOT c.tipos_contato ? 'FORNECEDOR';

-- Os demais viram contatos novos, com o mesmo id (os vínculos com produto
-- continuam valendo) e marcados como incompletos.
INSERT INTO radar_cliente (id, tenant_id, codigo, nome, tipo_pessoa, documento, email, telefone,
                           observacao, pessoas_contato, prazo_entrega_dias, tipos_contato,
                           origem, incompleto, criado_em)
SELECT f.id, f.tenant_id, 'TMP-' || left(f.id::text, 24), f.nome,
       CASE WHEN length(d.doc) = 14 THEN 'J' ELSE 'F' END,
       CASE WHEN d.doc ~ '^([0-9]{11}|[0-9A-Z]{12}[0-9]{2})$'
                 AND NOT EXISTS (SELECT 1 FROM radar_cliente x
                                 WHERE x.tenant_id = f.tenant_id AND x.documento = d.doc)
            THEN d.doc END,
       f.email, f.telefone, f.observacao,
       CASE WHEN f.contato IS NULL THEN '[]'::jsonb
            ELSE jsonb_build_array(jsonb_build_object('nome', f.contato)) END,
       f.prazo_entrega_dias, '["FORNECEDOR"]'::jsonb, 'MANUAL', true, f.criado_em
FROM radar_fornecedor f
CROSS JOIN LATERAL (SELECT regexp_replace(upper(coalesce(f.documento, '')), '[^0-9A-Z]', '', 'g') doc) d
WHERE NOT EXISTS (SELECT 1 FROM radar_cliente c
                  WHERE c.tenant_id = f.tenant_id AND c.documento = d.doc AND d.doc <> '');

UPDATE radar_cliente c SET codigo = 'C' || lpad(n.ordem::text, 5, '0')
FROM (SELECT c2.id, (SELECT coalesce(max(substring(x.codigo from 2)::int), 0) FROM radar_cliente x
                     WHERE x.tenant_id = c2.tenant_id AND x.codigo ~ '^C[0-9]{1,9}$')
                    + row_number() OVER (PARTITION BY c2.tenant_id ORDER BY c2.criado_em, c2.id) ordem
      FROM radar_cliente c2 WHERE c2.codigo LIKE 'TMP-%') n
WHERE n.id = c.id;

-- Vínculo produto–fornecedor passa a apontar para o contato.
ALTER TABLE radar_produto_fornecedor DROP CONSTRAINT radar_produto_fornecedor_tenant_id_fornecedor_id_fkey;
UPDATE radar_produto_fornecedor pf SET fornecedor_id = c.id
FROM radar_fornecedor f JOIN radar_cliente c ON c.tenant_id = f.tenant_id
 AND c.documento = regexp_replace(upper(coalesce(f.documento, '')), '[^0-9A-Z]', '', 'g')
WHERE pf.tenant_id = f.tenant_id AND pf.fornecedor_id = f.id AND c.id <> f.id;
ALTER TABLE radar_produto_fornecedor
 ADD FOREIGN KEY (tenant_id, fornecedor_id) REFERENCES radar_cliente (tenant_id, id);

CREATE TABLE radar_vendedor (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenant(id),
 codigo varchar(30) NOT NULL,
 nome varchar(200) NOT NULL CHECK (btrim(nome) <> ''),
 fantasia varchar(200),
 tipo_pessoa char(1) NOT NULL DEFAULT 'F' CHECK (tipo_pessoa IN ('F', 'J', 'E', 'B')),
 documento varchar(14) CHECK (documento IS NULL OR documento ~ '^([0-9]{11}|[0-9A-Z]{12}[0-9]{2})$'),
 contribuinte smallint CHECK (contribuinte IS NULL OR contribuinte IN (1, 2, 9)),
 inscricao_estadual varchar(20),
 cep varchar(8) CHECK (cep IS NULL OR cep ~ '^[0-9]{8}$'),
 endereco varchar(200), numero varchar(20), complemento varchar(120), bairro varchar(120),
 cidade varchar(120), uf varchar(2) CHECK (uf IS NULL OR uf ~ '^[A-Z]{2}$'),
 municipio_ibge varchar(7) CHECK (municipio_ibge IS NULL OR municipio_ibge ~ '^[0-9]{7}$'),
 telefone varchar(40), celular varchar(40),
 email varchar(320) CHECK (email IS NULL OR email ~ '^[^[:space:]@]+@[^[:space:]@]+$'),
 email_comunicacoes varchar(320) CHECK (email_comunicacoes IS NULL OR email_comunicacoes ~ '^[^[:space:]@]+@[^[:space:]@]+$'),
 situacao varchar(10) NOT NULL DEFAULT 'ATIVO' CHECK (situacao IN ('ATIVO', 'INATIVO')),
 deposito varchar(60),
 -- Dados de acesso: usuário do sistema ligado a este vendedor e restrições.
 usuario_id uuid,
 acesso_horario_inicio time, acesso_horario_fim time,
 acesso_dias jsonb NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(acesso_dias) = 'array'),
 acesso_ips jsonb NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(acesso_ips) = 'array'),
 perfil_contatos varchar(15) NOT NULL DEFAULT 'QUALQUER'
  CHECK (perfil_contatos IN ('QUALQUER', 'CLIENTE', 'FORNECEDOR', 'TRANSPORTADOR')),
 modulos jsonb NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(modulos) = 'array'),
 pode_incluir_produto_nao_cadastrado boolean NOT NULL DEFAULT false,
 pode_emitir_cobrancas boolean NOT NULL DEFAULT false,
 -- Comissão: alíquota fixa, ou conforme o desconto dado no pedido.
 comissao_regra varchar(10) NOT NULL DEFAULT 'FIXA' CHECK (comissao_regra IN ('FIXA', 'DESCONTO')),
 comissao_aliquota numeric(5,2) NOT NULL DEFAULT 0 CHECK (comissao_aliquota BETWEEN 0 AND 100),
 desconsiderar_comissao_linha boolean NOT NULL DEFAULT false,
 observacoes varchar(2000),
 criado_em timestamptz NOT NULL DEFAULT now(),
 atualizado_em timestamptz NOT NULL DEFAULT now(),
 UNIQUE (tenant_id, id),
 FOREIGN KEY (tenant_id, usuario_id) REFERENCES usuario (tenant_id, id)
);
CREATE UNIQUE INDEX uq_radar_vendedor_codigo ON radar_vendedor (tenant_id, codigo);
CREATE UNIQUE INDEX uq_radar_vendedor_documento ON radar_vendedor (tenant_id, documento) WHERE documento IS NOT NULL;
CREATE UNIQUE INDEX uq_radar_vendedor_usuario ON radar_vendedor (tenant_id, usuario_id) WHERE usuario_id IS NOT NULL;
ALTER TABLE radar_vendedor ENABLE ROW LEVEL SECURITY;
ALTER TABLE radar_vendedor FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON radar_vendedor
 USING (tenant_id = app_current_tenant_id())
 WITH CHECK (tenant_id = app_current_tenant_id());
GRANT SELECT, INSERT, UPDATE ON radar_vendedor TO app_aplicacao;

-- "Vendedor padrão" do cliente: do usuário para o cadastro de vendedor.
ALTER TABLE radar_cliente DROP CONSTRAINT radar_cliente_tenant_id_vendedor_id_fkey;
UPDATE radar_cliente SET vendedor_id = NULL WHERE vendedor_id IS NOT NULL;
ALTER TABLE radar_cliente
 ADD FOREIGN KEY (tenant_id, vendedor_id) REFERENCES radar_vendedor (tenant_id, id);
