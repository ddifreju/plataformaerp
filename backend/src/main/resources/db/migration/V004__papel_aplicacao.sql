-- =====================================================================
-- V004 — Papel de banco usado pela aplicacao
-- =====================================================================
-- A aplicacao NAO se conecta como dono do schema nem como superusuario.
-- Ela usa o papel app_aplicacao, com o minimo de privilegio necessario.
--
-- PORQUE ISSO IMPORTA PARA O ISOLAMENTO:
--   1. Superusuario ignora RLS por definicao. Se a aplicacao conectasse
--      como superusuario, todas as policies da V003 seriam decorativas.
--   2. BYPASSRLS faz o mesmo de forma mais discreta e por isso mais
--      perigosa: o papel parece comum e mesmo assim ve tudo.
--   3. O papel tambem nao e DONO das tabelas. O dono pode executar
--      ALTER TABLE ... NO FORCE ROW LEVEL SECURITY e desligar o
--      isolamento; alem disso, sem FORCE, o dono ja escaparia das
--      policies. Aplicacao nao dona = nao consegue desarmar a defesa.
--
-- SENHA: nao existe senha neste arquivo, e nunca vai existir. Migration
-- e versionada em git; segredo em git e segredo vazado. O papel e criado
-- sem senha (PASSWORD NULL => login por senha impossivel ate ser
-- definida). O provisionamento define a senha a partir de variavel de
-- ambiente, fora do controle de versao, por exemplo:
--     ALTER ROLE app_aplicacao WITH PASSWORD :'senha_do_ambiente';
-- (psql -v senha_do_ambiente="$APP_DB_PASSWORD"), ou via secret do
-- ambiente no docker-compose / VPS.
-- =====================================================================

-- Bloco idempotente: a migration pode rodar em base onde o papel ja
-- existe (papel e objeto de CLUSTER, nao de banco — se houver mais de um
-- banco no mesmo cluster, o papel e compartilhado).
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'app_aplicacao') THEN
        CREATE ROLE app_aplicacao
            LOGIN
            NOSUPERUSER
            NOCREATEDB
            NOCREATEROLE
            NOREPLICATION
            NOBYPASSRLS;
        RAISE NOTICE 'papel app_aplicacao criado sem senha; defina a senha via variavel de ambiente no provisionamento';
    ELSE
        RAISE NOTICE 'papel app_aplicacao ja existe; atributos serao verificados';
    END IF;

    -- Verificacao explicita de seguranca. Nao usamos ALTER ROLE aqui
    -- porque remover SUPERUSER/BYPASSRLS exige superusuario e a migration
    -- pode nao rodar como tal. Preferimos FALHAR ALTO a seguir em frente
    -- com um papel de aplicacao que fura RLS.
    IF EXISTS (
        SELECT 1 FROM pg_catalog.pg_roles
        WHERE rolname = 'app_aplicacao'
          AND (rolsuper OR rolbypassrls)
    ) THEN
        RAISE EXCEPTION
            'papel app_aplicacao possui SUPERUSER e/ou BYPASSRLS: isso anula o Row Level Security. Corrija com ALTER ROLE app_aplicacao NOSUPERUSER NOBYPASSRLS (como superusuario) e rode a migration novamente.';
    END IF;
END
$$;

-- Nota: nao usamos COMMENT ON ROLE. Papel e objeto compartilhado do
-- cluster e comentar nele exige superusuario ou ADMIN OPTION, o que
-- quebraria a migration em ambientes onde ela roda com papel restrito.
-- A documentacao do papel vive aqui, neste arquivo versionado.


-- ---------------------------------------------------------------------
-- Privilegios
-- ---------------------------------------------------------------------
-- Concedidos um a um, tabela a tabela. NAO usamos
-- ALTER DEFAULT PRIVILEGES de proposito: com default privileges, toda
-- tabela nova nasceria acessivel a aplicacao automaticamente — inclusive
-- uma tabela que alguem esqueceu de proteger com RLS. Sem eles, tabela
-- nova nasce inacessivel e a migration que a cria e obrigada a declarar
-- o GRANT explicitamente. Fail-closed tambem no privilegio.

GRANT USAGE ON SCHEMA public TO app_aplicacao;

-- Funcao base das policies: a aplicacao precisa poder executa-la.
GRANT EXECUTE ON FUNCTION app_current_tenant_id() TO app_aplicacao;

-- tenant: somente leitura. Criar, renomear ou desativar tenant e
-- caminho de provisionamento (papel administrativo), nao de aplicacao.
GRANT SELECT ON TABLE tenant TO app_aplicacao;

-- consulta_auditada: append-only. Sem UPDATE e sem DELETE — a trilha de
-- auditoria so tem valor se a propria aplicacao nao puder reescreve-la.
GRANT SELECT, INSERT ON TABLE consulta_auditada TO app_aplicacao;

-- Nao ha GRANT em sequences porque nao usamos sequence: as chaves sao
-- uuid geradas por gen_random_uuid().
