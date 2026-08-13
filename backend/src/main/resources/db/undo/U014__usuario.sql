-- =====================================================================
-- U014 — Desfaz V014__usuario.sql
-- =====================================================================
-- Undos rodam em ordem DECRESCENTE (U014, U013, ... U001). Este passa a
-- ser o primeiro. Nada referencia `usuario` ainda — ela e a ponta da
-- cadeia; referencia `tenant`, que cai depois.
--
-- Depois de aplicar, limpe o historico do Flyway:
--     DELETE FROM flyway_schema_history WHERE version = '014';
--
-- ---------------------------------------------------------------------
-- ESTE UNDO DERRUBA O LOGIN DO SISTEMA INTEIRO. Leia antes de rodar.
-- ---------------------------------------------------------------------
-- A partir da decisao 0023 o tenant de toda requisicao sai de uma linha
-- desta tabela, e o header X-Tenant-Id foi REMOVIDO (nao desativado por
-- configuracao). Consequencia direta e nao obvia:
--
--   Reverter APENAS o schema deixa a aplicacao sem NENHUMA fonte de
--   tenant. Nao e "o login para de funcionar": e a API inteira parar,
--   porque ContextoTenant.atual() lanca excecao em vez de devolver null
--   (0007/0023). Este undo so faz sentido ACOMPANHADO da reversao do
--   codigo da tarefa 17 — e essa reversao e o trabalho de verdade; o
--   DROP TABLE abaixo e a parte facil.
--
-- ---------------------------------------------------------------------
-- O DADO NAO VOLTA, E AQUI ISSO E PIOR QUE O NORMAL
-- ---------------------------------------------------------------------
-- Undo restaura o SCHEMA, nunca os DADOS. As linhas desta tabela sao
-- CREDENCIAIS: nao existem em API nenhuma para reingestao e nao podem ser
-- recriadas a partir de outra fonte, porque ninguem — nem nos — conhece
-- as senhas. Reaplicar a V014 depois disso significa provisionar tudo de
-- novo e cada pessoa definir uma senha nova.
--
-- Exporte antes:
--     \copy (SELECT * FROM usuario) TO 'usuario.csv' CSV HEADER
-- Duas ressalvas sobre esse backup:
--   a) com FORCE ROW LEVEL SECURITY, esse SELECT precisa do GUC
--      app.tenant_id setado se voce estiver conectada como DONO (nao
--      superusuario) — sem ele volta ZERO linhas e o arquivo sai vazio,
--      sem erro nenhum (armadilha registrada na decisao 0010). Como
--      superusuario o RLS e ignorado e vem tudo.
--   b) o arquivo gerado contem os hashes BCrypt de todos os clientes.
--      Trate como segredo: fora do git, fora de pasta sincronizada,
--      apagado depois de usado.
-- =====================================================================

-- A funcao sai ANTES da tabela. Ela e SECURITY INVOKER e nao e dona de
-- nada, entao a ordem nao e obrigatoria tecnicamente — mas derrubar
-- primeiro o caminho de leitura pre-sessao e depois a tabela deixa o
-- script coerente com a ordem inversa da criacao.
-- Os privilegios de uma funcao caem junto com ela; nao ha REVOKE a fazer.
DROP FUNCTION IF EXISTS app_usuario_para_login(text);

DO $$
BEGIN
    IF to_regclass('public.usuario') IS NOT NULL
       AND EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'app_aplicacao') THEN
        -- REVOKE ALL cobre tambem o GRANT UPDATE por coluna da V014:
        -- privilegio de coluna nao sobrevive a um REVOKE ALL de tabela.
        REVOKE ALL ON TABLE public.usuario FROM app_aplicacao;
    END IF;
END
$$;

-- Ordem inversa da criacao. A policy de login sai junto com as outras:
-- ela e uma policy comum, so com um USING diferente.
DROP POLICY IF EXISTS usuario_select_login ON usuario;
DROP POLICY IF EXISTS usuario_delete ON usuario;
DROP POLICY IF EXISTS usuario_update ON usuario;
DROP POLICY IF EXISTS usuario_insert ON usuario;
DROP POLICY IF EXISTS usuario_select ON usuario;

ALTER TABLE IF EXISTS usuario NO FORCE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS usuario DISABLE ROW LEVEL SECURITY;

-- Nao ha DROP INDEX aqui, e a ausencia e proposital: a V014 nao criou
-- nenhum indice avulso. Os dois indices existentes sao os das constraints
-- uq_usuario_tenant_id e uq_usuario_email, e constraint nao se derruba
-- com DROP INDEX — os dois caem junto com a tabela, na linha abaixo.
DROP TABLE IF EXISTS usuario;

-- ---------------------------------------------------------------------
-- O GUC app.login_email nao precisa ser "removido"
-- ---------------------------------------------------------------------
-- Ele nunca foi declarado em lugar nenhum: GUC customizado com prefixo
-- (`app.`) existe por ser setado, e some quando a sessao termina. Sem a
-- policy usuario_select_login, seta-lo passa a nao ter efeito algum.
-- Nao ha objeto de catalogo a limpar.
