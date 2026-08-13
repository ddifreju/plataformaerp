-- =====================================================================
-- U013 — Desfaz V013__taxa_canal.sql
-- =====================================================================
-- Undos rodam em ordem DECRESCENTE (U013, U012, ... U001). Este passa a
-- ser o primeiro. Nada referencia taxa_canal; ela referencia canal e
-- tenant, que caem depois.
--
-- Depois de aplicar, limpe o historico do Flyway:
--     DELETE FROM flyway_schema_history WHERE version = '013';
--
-- ATENCAO — este undo e mais destrutivo do que parece. Undo restaura o
-- SCHEMA, nunca os DADOS, e as linhas desta tabela sao majoritariamente
-- CADASTRO MANUAL do lojista (comissao por categoria, tarifa por faixa,
-- taxa de antecipacao contratada). Diferente de pedido ou custo, esse
-- dado NAO existe em nenhuma API para reingestao: ele so existe na cabeca
-- e nos extratos de quem cadastrou. Perde-lo joga todo pedido sem valor
-- informado pela fonte de volta para a LACUNA (nivel 3 da decisao 0019),
-- ou seja, a margem de parte do historico deixa de ser calculavel.
-- Exporte o conteudo antes:
--     \copy (SELECT * FROM taxa_canal) TO 'taxa_canal.csv' CSV HEADER
-- e lembre que, com FORCE ROW LEVEL SECURITY, esse SELECT precisa do GUC
-- app.tenant_id setado — sem ele volta ZERO linhas e o backup sai vazio
-- sem erro nenhum (armadilha registrada na decisao 0010).
-- =====================================================================

DO $$
BEGIN
    IF to_regclass('public.taxa_canal') IS NOT NULL
       AND EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'app_aplicacao') THEN
        REVOKE ALL ON TABLE public.taxa_canal FROM app_aplicacao;
    END IF;
END
$$;

DROP POLICY IF EXISTS taxa_canal_delete ON taxa_canal;
DROP POLICY IF EXISTS taxa_canal_update ON taxa_canal;
DROP POLICY IF EXISTS taxa_canal_insert ON taxa_canal;
DROP POLICY IF EXISTS taxa_canal_select ON taxa_canal;

ALTER TABLE IF EXISTS taxa_canal NO FORCE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS taxa_canal DISABLE ROW LEVEL SECURITY;

DROP INDEX IF EXISTS uq_taxa_canal_origem;
DROP INDEX IF EXISTS ix_taxa_canal_selecao;

-- A constraint ex_taxa_canal_sem_sobreposicao e o indice gist dela caem
-- junto com a tabela; nao ha DROP separado nem necessidade de CASCADE,
-- porque nenhuma outra tabela depende de taxa_canal.
DROP TABLE IF EXISTS taxa_canal;

-- ---------------------------------------------------------------------
-- PORQUE NAO DERRUBAMOS A EXTENSAO btree_gist
-- ---------------------------------------------------------------------
-- Mesmo racional da U001 para pgcrypto e vector, e aqui ele e ainda mais
-- forte:
-- 1. Extensao e objeto de BANCO, nao do nosso schema logico. Ela e
--    criada com CREATE EXTENSION IF NOT EXISTS, entao reaplicar a V013
--    funciona com ou sem este DROP — derruba-la nao compra reversibilidade
--    nenhuma.
-- 2. btree_gist e infraestrutura compartilhada por natureza: qualquer
--    outra migration futura que precise de EXCLUDE sobre (escalar,
--    intervalo) vai depender dela — e a proxima e previsivel, e a tabela
--    versionada de imposto/regime tributario, que tem exatamente o mesmo
--    desenho de vigencia. Derrubar aqui quebraria a tabela irma sem que
--    nada nesta migration mencione o vinculo.
-- 3. DROP EXTENSION derrubaria em CASCADE qualquer indice ou constraint
--    que use os opclasses dela. Isso e perda de garantia de integridade
--    disfarcada de rollback — o efeito oposto ao que um undo deve ter.
-- Se for mesmo necessario remover (descomissionar o banco inteiro), faca
-- manualmente e de forma consciente, depois de conferir os dependentes:
--     SELECT * FROM pg_depend d
--       JOIN pg_extension e ON e.oid = d.refobjid
--      WHERE e.extname = 'btree_gist';
--     DROP EXTENSION btree_gist;
