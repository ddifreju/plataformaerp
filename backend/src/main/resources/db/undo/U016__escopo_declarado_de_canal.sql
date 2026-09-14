-- =====================================================================
-- U016 — Desfaz V016__escopo_declarado_de_canal.sql
-- =====================================================================
-- Undos rodam em ordem DECRESCENTE (U016, U015, ... U001). Este passa a
-- ser o primeiro. Nada depende das colunas que ele remove: elas so
-- existem em `canal`, e a FK para `usuario` (V014) e derrubada aqui,
-- antes de U014 chegar na tabela.
--
-- Depois de aplicar, limpe o historico do Flyway:
--     DELETE FROM flyway_schema_history WHERE version = '016';
--
-- ---------------------------------------------------------------------
-- ATENCAO — o que se perde, e nao e o schema
-- ---------------------------------------------------------------------
-- Undo restaura o SCHEMA, nunca os DADOS (decisao 0006). O conteudo
-- destas quatro colunas e DECLARACAO MANUAL DA LOJISTA: qual canal e
-- fonte primaria, qual e espelho de qual. Esse dado nao existe em API
-- nenhuma para reingestao — a decisao 0033 diz exatamente por que: e
-- conhecimento de quem montou a operacao, e e por nao termos esse
-- conhecimento que a 0017 barrou o casamento automatico de pedidos.
-- Reaplicar a V016 depois traz todo canal de volta para NAO_DECLARADO.
--
-- O ESTADO RESULTANTE E SEGURO, E INUTIL: sem as colunas, a soma entre
-- canais volta a ser sempre recusada (0017/0021), que e o comportamento
-- correto na ausencia de declaracao. Nada passa a ser somado errado; o
-- que se perde e a capacidade de responder "quanto sobrou no mes".
--
-- Exporte antes de rodar:
--     \copy (SELECT id, codigo, escopo_declarado, espelha_canal_id,
--                   escopo_declarado_em, escopo_declarado_por
--              FROM canal) TO 'escopo_canal.csv' CSV HEADER
-- e lembre que, com FORCE ROW LEVEL SECURITY, esse SELECT precisa do GUC
-- app.tenant_id setado, um tenant por vez — sem ele volta ZERO linhas e o
-- backup sai vazio sem erro nenhum (armadilha registrada na decisao 0010).
--
-- SE A ENTIDADE JPA `Canal` JA MAPEAR ESTAS COLUNAS, aplicar este undo faz
-- a aplicacao PARAR DE SUBIR: ddl-auto=validate nao encontra a coluna
-- mapeada. Mesmo aviso honesto da U015 — o arquivo existe pela regra de
-- reversibilidade, nao porque roda-lo seja uma boa ideia.
--
-- ---------------------------------------------------------------------
-- O QUE ESTE UNDO NAO TOCA, DE PROPOSITO
-- ---------------------------------------------------------------------
-- RLS, as quatro policies e os GRANTs de `canal` sao da V005 e continuam
-- de pe. A V016 nao criou nenhum deles (ela nao precisou: ADD COLUMN nao
-- mexe em RLS, e o GRANT de tabela ja alcancava as colunas novas), entao
-- derruba-los aqui deixaria `canal` sem isolamento — o oposto do que um
-- undo deve fazer, e o sentinela RlsAtivoEmTodasAsTabelasTest quebraria
-- na hora. Quem desfaz isso e a U005.
-- =====================================================================

-- Ordem: indice, depois constraints, depois colunas. DROP COLUMN levaria
-- os tres juntos em cascata, mas o desfazer explicito e na ordem inversa
-- da V016 e o que torna este arquivo revisavel linha a linha.
DROP INDEX IF EXISTS uq_canal_espelho_reciproco;

ALTER TABLE canal DROP CONSTRAINT IF EXISTS ck_canal_escopo_declarado_por_coerente;
ALTER TABLE canal DROP CONSTRAINT IF EXISTS ck_canal_escopo_declarado_em_coerente;
ALTER TABLE canal DROP CONSTRAINT IF EXISTS ck_canal_nao_espelha_a_si_mesmo;
ALTER TABLE canal DROP CONSTRAINT IF EXISTS ck_canal_espelho_exige_alvo;

ALTER TABLE canal DROP CONSTRAINT IF EXISTS fk_canal_escopo_declarado_por_usuario;
ALTER TABLE canal DROP CONSTRAINT IF EXISTS fk_canal_espelha_canal;

ALTER TABLE canal DROP CONSTRAINT IF EXISTS ck_canal_escopo_declarado;

-- Os COMMENT ON COLUMN caem junto com as colunas; nao ha o que reverter.
ALTER TABLE canal
    DROP COLUMN IF EXISTS escopo_declarado_por,
    DROP COLUMN IF EXISTS escopo_declarado_em,
    DROP COLUMN IF EXISTS espelha_canal_id,
    DROP COLUMN IF EXISTS escopo_declarado;
