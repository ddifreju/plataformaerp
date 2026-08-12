-- =====================================================================
-- V002 — Tabela raiz de tenants
-- =====================================================================
-- Esta e a tabela de catalogo de tenants. Ela e a UNICA tabela do
-- sistema que legitimamente NAO tem coluna tenant_id e NAO tem RLS.
--
-- PORQUE NAO TEM tenant_id:
--   tenant.id E o tenant_id. Uma coluna tenant_id aqui seria uma
--   auto-referencia sem significado.
--
-- PORQUE NAO TEM RLS:
--   1. E o catalogo administrativo: o processo de provisionamento
--      precisa enxergar todos os tenants para criar/desativar um.
--   2. As FKs das tabelas de dados apontam para ca. Checagem de FK no
--      Postgres nao respeita RLS da tabela referenciada de forma util —
--      colocar RLS aqui criaria comportamento confuso na insercao.
--   3. O dado aqui nao e dado de cliente: e nome, slug e flag de ativo.
--
-- CONSEQUENCIA QUE O BACKEND PRECISA HONRAR:
--   Como nao ha RLS aqui, o papel da aplicacao recebe apenas SELECT
--   nesta tabela (ver V004), e qualquer endpoint que liste tenants
--   precisa filtrar explicitamente pelo tenant do contexto. Criar,
--   alterar ou desativar tenant e caminho de provisionamento, nao de
--   aplicacao.
-- =====================================================================

CREATE TABLE tenant (
    id         uuid        NOT NULL DEFAULT gen_random_uuid(),
    nome       text        NOT NULL,
    -- slug: identificador estavel e legivel usado em URL, subdominio e
    -- configuracao de integracao. Unico globalmente, imutavel na pratica.
    slug       text        NOT NULL,
    -- ativo: desligamos tenant, nao apagamos. Historico fiscal e de
    -- auditoria precisa sobreviver ao fim do contrato.
    ativo      boolean     NOT NULL DEFAULT true,
    criado_em  timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_tenant PRIMARY KEY (id),
    CONSTRAINT uq_tenant_slug UNIQUE (slug),
    -- Formato restrito de proposito: minusculas, digitos e hifen. Evita
    -- que o slug vire fonte de ambiguidade em URL ou nome de recurso.
    CONSTRAINT ck_tenant_slug_formato CHECK (slug ~ '^[a-z0-9][a-z0-9-]{1,62}[a-z0-9]$'),
    CONSTRAINT ck_tenant_nome_nao_vazio CHECK (btrim(nome) <> '')
);

COMMENT ON TABLE  tenant IS
    'Catalogo de tenants. Tabela raiz: sem coluna tenant_id e sem RLS por ser catalogo administrativo. Toda tabela de dados referencia tenant(id).';
COMMENT ON COLUMN tenant.slug IS
    'Identificador estavel e legivel (URL, subdominio, config de integracao). Unico globalmente.';
COMMENT ON COLUMN tenant.ativo IS
    'Desativacao logica. Nao apagamos tenant: historico fiscal e auditoria precisam sobreviver ao contrato.';
