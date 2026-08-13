-- =====================================================================
-- U015 — Desfaz V015__char_para_varchar.sql
-- =====================================================================
-- Volta as 9 colunas para char(n).
--
-- AVISO HONESTO: aplicar este undo faz a aplicacao PARAR DE SUBIR.
-- E exatamente o estado que a V015 corrigiu - o ddl-auto=validate
-- rejeita bpchar onde o mapeamento espera varchar. Este arquivo existe
-- por simetria e pela regra de reversibilidade (decisao 0006), nao
-- porque rodar ele seja uma boa ideia.
--
-- Se o objetivo for so voltar o schema para inspecionar, lembre de que
-- a conversao de volta REINTRODUZ o padding com espacos: um 'BR'
-- gravado como varchar(2) vira 'BR' em char(2) sem alteracao, mas
-- valores mais curtos que o tamanho fixo passam a ser preenchidos.
--
-- Depois de aplicar, limpe o historico do Flyway:
--     DELETE FROM flyway_schema_history WHERE version = '015';
-- =====================================================================

ALTER TABLE taxa_canal ALTER COLUMN moeda DROP DEFAULT;
ALTER TABLE taxa_canal ALTER COLUMN moeda TYPE char(3);
ALTER TABLE taxa_canal ALTER COLUMN moeda SET DEFAULT 'BRL';

ALTER TABLE evento_ingerido ALTER COLUMN hash_payload TYPE char(64);

ALTER TABLE custo ALTER COLUMN moeda DROP DEFAULT;
ALTER TABLE custo ALTER COLUMN moeda TYPE char(3);
ALTER TABLE custo ALTER COLUMN moeda SET DEFAULT 'BRL';

ALTER TABLE devolucao ALTER COLUMN moeda DROP DEFAULT;
ALTER TABLE devolucao ALTER COLUMN moeda TYPE char(3);
ALTER TABLE devolucao ALTER COLUMN moeda SET DEFAULT 'BRL';

ALTER TABLE pedido ALTER COLUMN uf_entrega TYPE char(2);

ALTER TABLE pedido ALTER COLUMN moeda DROP DEFAULT;
ALTER TABLE pedido ALTER COLUMN moeda TYPE char(3);
ALTER TABLE pedido ALTER COLUMN moeda SET DEFAULT 'BRL';

ALTER TABLE variacao ALTER COLUMN moeda DROP DEFAULT;
ALTER TABLE variacao ALTER COLUMN moeda TYPE char(3);
ALTER TABLE variacao ALTER COLUMN moeda SET DEFAULT 'BRL';

ALTER TABLE produto ALTER COLUMN cest TYPE char(7);
ALTER TABLE produto ALTER COLUMN ncm TYPE char(8);
