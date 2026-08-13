-- =====================================================================
-- V015 — Converte char(n) para varchar(n)
-- =====================================================================
-- MOTIVO IMEDIATO: a aplicacao NAO SOBE com o schema anterior.
--
-- Com spring.jpa.hibernate.ddl-auto=validate (decisao 0006), o Hibernate
-- compara cada coluna mapeada com o tipo real do banco e aborta o boot
-- quando divergem. As entidades mapeiam String com @Column(length = n),
-- que o dialeto do Postgres espera encontrar como varchar(n). O banco
-- tinha char(n) (bpchar), e o boot morria com:
--
--   Schema-validation: wrong column type encountered in column [moeda]
--   in table [custo]; found [bpchar (Types#CHAR)],
--   but expecting [varchar(3) (Types#VARCHAR)]
--
-- Isto foi previsto como "o maior risco" em docs/ESTADO.md antes de
-- existir Docker na maquina, e se confirmou na primeira execucao real
-- da suite: 37 testes de contexto falhavam por esta unica causa.
--
-- POR QUE CORRIGIR O BANCO E NAO O MAPEAMENTO:
-- daria para forcar o Java a aceitar char, com columnDefinition ou
-- @JdbcTypeCode(Types.CHAR) em 9 campos. Seria pior, e nao por gosto:
--
--   1. char(n) no Postgres FAZ PADDING COM ESPACOS ate o tamanho fixo.
--      'BRL' gravado em char(3) volta 'BRL', mas 'BR' em char(3) volta
--      'BR '. Isso vaza para comparacao, concatenacao e para o JSON da
--      API - um dia alguem compara moeda com "BRL" no frontend e nao
--      casa, e o bug leva meia manha para achar.
--   2. A documentacao do proprio Postgres afirma que char(n) NAO tem
--      vantagem de desempenho sobre varchar(n) - e costuma ser mais
--      lento. Nao ha nada sendo trocado por essa mudanca.
--   3. Corrigir no mapeamento espalharia a excecao por 9 campos em 7
--      entidades, e cada campo novo com tamanho fixo repetiria a
--      pegadinha. Corrigir no banco resolve na origem, uma vez.
--
-- POR QUE UMA MIGRATION NOVA E NAO EDITAR A V006/V008/V010/V012/V013:
-- as 14 migrations anteriores JA FORAM APLICADAS num banco real (a
-- primeira validacao de verdade do projeto). Editar uma migration ja
-- aplicada quebra o checksum do Flyway e obriga a recriar o banco.
-- Migration aplicada e imutavel - corrige-se com outra na frente.
--
-- SEGURANCA DOS DADOS: o USING trim(...) remove o padding que o char(n)
-- possa ter deixado. Hoje o banco de desenvolvimento esta praticamente
-- vazio, mas a conversao precisa estar correta para quando nao estiver:
-- sem o trim, 'BR ' continuaria com o espaco depois de virar varchar.
-- =====================================================================

-- --- V006: produto e variacao ----------------------------------------
ALTER TABLE produto
    ALTER COLUMN ncm TYPE varchar(8) USING trim(trailing ' ' FROM ncm);
ALTER TABLE produto
    ALTER COLUMN cest TYPE varchar(7) USING trim(trailing ' ' FROM cest);

-- moeda tem DEFAULT: o Postgres reescreve o default sozinho na
-- conversao, mas declaramos de novo para o tipo do default nao ficar
-- dependendo desse detalhe.
ALTER TABLE variacao
    ALTER COLUMN moeda DROP DEFAULT;
ALTER TABLE variacao
    ALTER COLUMN moeda TYPE varchar(3) USING trim(trailing ' ' FROM moeda);
ALTER TABLE variacao
    ALTER COLUMN moeda SET DEFAULT 'BRL';

-- --- V008: pedido ----------------------------------------------------
ALTER TABLE pedido
    ALTER COLUMN moeda DROP DEFAULT;
ALTER TABLE pedido
    ALTER COLUMN moeda TYPE varchar(3) USING trim(trailing ' ' FROM moeda);
ALTER TABLE pedido
    ALTER COLUMN moeda SET DEFAULT 'BRL';

ALTER TABLE pedido
    ALTER COLUMN uf_entrega TYPE varchar(2) USING trim(trailing ' ' FROM uf_entrega);

-- --- V009: devolucao -------------------------------------------------
ALTER TABLE devolucao
    ALTER COLUMN moeda DROP DEFAULT;
ALTER TABLE devolucao
    ALTER COLUMN moeda TYPE varchar(3) USING trim(trailing ' ' FROM moeda);
ALTER TABLE devolucao
    ALTER COLUMN moeda SET DEFAULT 'BRL';

-- --- V010: custo -----------------------------------------------------
ALTER TABLE custo
    ALTER COLUMN moeda DROP DEFAULT;
ALTER TABLE custo
    ALTER COLUMN moeda TYPE varchar(3) USING trim(trailing ' ' FROM moeda);
ALTER TABLE custo
    ALTER COLUMN moeda SET DEFAULT 'BRL';

-- --- V012: evento_ingerido -------------------------------------------
-- hash_payload tem CHECK de formato (64 hex minusculos). O Postgres
-- revalida o CHECK sozinho na conversao de tipo; como o conteudo nao
-- muda, ele continua valendo.
ALTER TABLE evento_ingerido
    ALTER COLUMN hash_payload TYPE varchar(64) USING trim(trailing ' ' FROM hash_payload);

-- --- V013: taxa_canal ------------------------------------------------
ALTER TABLE taxa_canal
    ALTER COLUMN moeda DROP DEFAULT;
ALTER TABLE taxa_canal
    ALTER COLUMN moeda TYPE varchar(3) USING trim(trailing ' ' FROM moeda);
ALTER TABLE taxa_canal
    ALTER COLUMN moeda SET DEFAULT 'BRL';

COMMENT ON COLUMN custo.moeda IS
    'Codigo ISO 4217. varchar(3), nao char(3): char faz padding com espacos e nao traz vantagem nenhuma no Postgres.';
