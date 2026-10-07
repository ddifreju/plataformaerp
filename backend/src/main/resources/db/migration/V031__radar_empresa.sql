-- =====================================================================
-- V031 — Dados da empresa (Configurações → geral → Alterar dados da empresa)
-- =====================================================================
-- Uma linha por tenant: razão social, CNPJ, inscrições, regime, endereço,
-- contato e logo. É a base da nota fiscal, das etiquetas e do painel do
-- grupo de empresas (decisão 0035).
--
-- Por que tabela própria e não colunas em `tenant`: `tenant` é catálogo
-- sem RLS (V002) e a aplicação só tem SELECT nela. Dado da empresa é dado
-- do cliente, editável por ele, então segue o molde da decisão 0010:
-- tenant_id, RLS com FORCE e GRANT explícito.
--
-- Tudo é opcional: a empresa preenche aos poucos. Campo vazio fica NULL,
-- nunca um valor inventado (regra 5).
-- =====================================================================

CREATE TABLE radar_empresa (
    tenant_id            uuid        PRIMARY KEY REFERENCES tenant(id),
    razao_social         varchar(200),
    nome_fantasia        varchar(200),
    -- 14 posições: aceita o CNPJ alfanumérico (IN RFB 2.229/2024).
    cnpj                 varchar(14) CHECK (cnpj ~ '^[0-9A-Z]{12}[0-9]{2}$'),
    inscricao_estadual   varchar(20),
    inscricao_municipal  varchar(20),
    regime_tributario    varchar(20) CHECK (regime_tributario IN
                             ('MEI', 'SIMPLES', 'SIMPLES_EXCESSO', 'PRESUMIDO', 'REAL')),
    cnae                 varchar(7)  CHECK (cnae ~ '^[0-9]{7}$'),
    email                varchar(320),
    telefone             varchar(40),
    celular              varchar(40),
    site                 varchar(200),
    cep                  varchar(8)  CHECK (cep ~ '^[0-9]{8}$'),
    endereco             varchar(200),
    numero               varchar(20),
    complemento          varchar(120),
    bairro               varchar(120),
    cidade               varchar(120),
    uf                   varchar(2),
    municipio_ibge       varchar(7)  CHECK (municipio_ibge ~ '^[0-9]{7}$'),
    logo                 bytea,
    logo_tipo            varchar(20) CHECK (logo_tipo IN ('image/png', 'image/jpeg', 'image/webp')),
    atualizado_em        timestamptz NOT NULL DEFAULT now(),
    CHECK ((logo IS NULL) = (logo_tipo IS NULL))
);

ALTER TABLE radar_empresa ENABLE ROW LEVEL SECURITY;
ALTER TABLE radar_empresa FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON radar_empresa
 USING (tenant_id = app_current_tenant_id())
 WITH CHECK (tenant_id = app_current_tenant_id());
GRANT SELECT, INSERT, UPDATE ON radar_empresa TO app_aplicacao;
