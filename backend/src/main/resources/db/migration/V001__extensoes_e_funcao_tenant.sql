-- =====================================================================
-- V001 — Extensoes e funcao de resolucao do tenant corrente
-- =====================================================================
-- Fase 0: fundacao. Aqui nao existe modelo de negocio ainda (pedido,
-- produto, canal, devolucao sao Fase 1). O objetivo desta migration e
-- criar a base sobre a qual TODA policy de Row Level Security vai apoiar.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Extensoes
-- ---------------------------------------------------------------------
-- pgcrypto: usada por gen_random_uuid(). No PostgreSQL 13+ a funcao
-- gen_random_uuid() ja existe no core, mas mantemos pgcrypto explicita
-- porque tambem precisaremos de digest()/hmac() para fingerprint de
-- payload externo na ingestao (Fase 1, decisao 0002 — modelo agnostico
-- a fonte exige deduplicar evento por hash do payload de origem).
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- vector (pgvector): decisao 0001 — a camada de IA vive no mesmo
-- Postgres para nao termos dois sistemas para isolar por tenant.
-- Criada ja na fundacao para que o ambiente de dev falhe cedo caso a
-- imagem do Postgres nao tenha a extensao disponivel (use a imagem
-- pgvector/pgvector:pg16, a imagem oficial postgres:16 NAO a traz).
CREATE EXTENSION IF NOT EXISTS "vector";


-- ---------------------------------------------------------------------
-- app_current_tenant_id() — a peca central do isolamento
-- ---------------------------------------------------------------------
-- A aplicacao seta o GUC app.tenant_id por conexao/transacao. Esta
-- funcao le esse GUC e devolve o uuid do tenant corrente.
--
-- PORQUE ELA EXISTE (em vez de escrever current_setting() direto na
-- policy):
--   1. current_setting('app.tenant_id') sem o segundo argumento LANCA
--      erro quando o GUC nunca foi setado. Erro em policy = query
--      quebrada em producao no primeiro request de uma conexao nova.
--   2. current_setting('app.tenant_id', true)::uuid LANCA erro de cast
--      quando o GUC existe mas esta vazio (''). E ele fica vazio com
--      frequencia: RESET, `SET app.tenant_id = ''` e alguns pools
--      resetam o valor para string vazia em vez de remover o GUC.
--   3. Centralizar significa que, se a regra de resolucao mudar, muda
--      em um lugar so — e nao em N policies espalhadas.
--
-- FAIL-CLOSED (falha fechada) — o comportamento mais importante daqui:
--   Sem GUC, GUC vazio ou GUC malformado => retorna NULL.
--   As policies comparam `tenant_id = app_current_tenant_id()`.
--   Em SQL, `qualquer_coisa = NULL` avalia para NULL, e NULL em policy
--   e tratado como FALSO. Logo: conexao sem tenant setado NAO enxerga
--   NENHUMA linha e NAO consegue inserir NENHUMA linha.
--   O modo de falha e "nao ve nada", nunca "ve tudo".
--
-- PORQUE VALIDAR COM REGEX EM VEZ DE USAR BLOCO EXCEPTION:
--   Um bloco BEGIN...EXCEPTION em PL/pgSQL abre uma subtransacao a cada
--   chamada. Como esta funcao e chamada dentro de policy (potencialmente
--   por linha avaliada), isso custaria caro em tabela grande. A validacao
--   por regex do formato canonico de uuid resolve o mesmo problema sem
--   subtransacao e deixa explicito o que aceitamos.
--   Consequencia deliberada: aceitamos SOMENTE o formato canonico
--   8-4-4-4-12 com hifens. O Postgres tambem aceitaria uuid sem hifen ou
--   entre chaves; nos nao. A aplicacao Java sempre emite UUID.toString(),
--   que e canonico. Qualquer outro formato e sintoma de bug e vira NULL
--   (fail-closed), nao um cast otimista.
--
-- STABLE: dentro de um mesmo comando o valor nao muda, o que permite ao
-- planejador avaliar a funcao uma vez por scan em vez de por linha.
-- Nao pode ser IMMUTABLE (o valor depende da sessao).
--
-- SET search_path: evita sequestro por schema malicioso no search_path
-- do chamador. A funcao so usa builtins de pg_catalog.
CREATE OR REPLACE FUNCTION app_current_tenant_id()
RETURNS uuid
LANGUAGE plpgsql
STABLE
SET search_path = pg_catalog
AS $$
DECLARE
    v_valor text;
BEGIN
    -- segundo argumento `true` = missing_ok: devolve NULL em vez de erro
    -- quando o GUC nunca foi setado nesta sessao.
    v_valor := current_setting('app.tenant_id', true);

    -- GUC ausente ou em branco => sem tenant => nenhuma linha visivel.
    IF v_valor IS NULL OR btrim(v_valor) = '' THEN
        RETURN NULL;
    END IF;

    -- Formato canonico de uuid. Se nao bater, nao arriscamos o cast.
    IF btrim(v_valor) !~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' THEN
        RETURN NULL;
    END IF;

    RETURN btrim(v_valor)::uuid;
END;
$$;

COMMENT ON FUNCTION app_current_tenant_id() IS
    'Tenant corrente lido do GUC app.tenant_id. Retorna NULL quando o GUC esta ausente, vazio ou malformado (fail-closed: nenhuma linha visivel). Base de todas as policies de RLS.';
