-- =====================================================================
-- V016 — escopo declarado de canal (a pre-condicao da soma entre canais)
-- =====================================================================
-- Convencoes gerais: cabecalho da V005. Contrato desta migration:
-- docs/decisoes/0033-escopo-de-canal-declarado.md. Decisoes ja tomadas la
-- e NAO reabertas aqui: a declaracao e da lojista (nenhuma heuristica
-- preenche), NAO_DECLARADO bloqueia a soma, ESPELHO nao funde nem esconde
-- dado, o endpoint por canal da 0021 continua exigindo canalId.
--
-- O QUE ESTA MIGRATION ACRESCENTA A `canal` (V005): quatro colunas que
-- respondem UMA pergunta — "este conjunto de canais pode ser somado sem
-- contar a mesma venda duas vezes?". Ate aqui a resposta era sempre "nao
-- sei", e por isso a 0017/0021 proibiram a soma. Agora ela e dado.
--
-- ---------------------------------------------------------------------
-- 1. O DOMINIO, E POR QUE O DEFAULT E O VALOR QUE BLOQUEIA
-- ---------------------------------------------------------------------
--   NAO_DECLARADO   a lojista ainda nao disse nada. BLOQUEIA a soma.
--   FONTE_PRIMARIA  os pedidos deste canal nascem aqui.
--   ESPELHO         os pedidos deste canal sao copia dos de outro canal,
--                   apontado por espelha_canal_id.
--
-- DEFAULT 'NAO_DECLARADO' faz duas coisas ao mesmo tempo, e as duas sao
-- deliberadas:
--   a) migra o dado antigo sozinho. Todo canal que ja existe passa a ter
--      exatamente o estado que a 0033 descreve para ele ("o estado de
--      todo canal que existe hoje"). Nao ha backfill, nao ha script, nao
--      ha janela em que a coluna esteja NULL;
--   b) faz o esquecimento cair do lado seguro. O caminho facil seria
--      DEFAULT 'FONTE_PRIMARIA' — a funcionalidade "ja funcionaria" para
--      todo mundo no dia da migration. Funcionaria contando venda duas
--      vezes na conta de quem tem Bling ligado, que e o erro mais grave
--      que este produto pode cometer (0033, e o mesmo fail-closed do
--      app_current_tenant_id() da V001).
-- E o unico caso em que um DEFAULT de dominio e correto neste schema
-- (compare com taxa_canal.categoria_canal, V013, que NAO tem default de
-- proposito): aqui o valor default e a AUSENCIA de declaracao, nao uma
-- declaracao escolhida por nos.
--
-- ADD COLUMN ... NOT NULL DEFAULT nao reescreve a tabela (Postgres 11+,
-- default nao volatil e gravado no catalogo). A migration e barata mesmo
-- com canal cheio.
--
-- ---------------------------------------------------------------------
-- 2. varchar(20), nunca char(n) — e por que nao `text`
-- ---------------------------------------------------------------------
-- char(n) esta proibido no schema inteiro pela decisao 0027: faz padding
-- com espacos, vaza o espaco para comparacao e para o JSON da API, e nao
-- compra desempenho nenhum no Postgres. A V015 existiu so para desfazer
-- essa escolha em nove colunas; nao a reintroduzimos aqui.
-- Entre varchar(20) e text: os dominios antigos (canal.tipo,
-- canal.categoria, pedido.status) sao `text`, e text continua valido. Aqui
-- vale varchar(20) porque o dominio e fechado por CHECK e o maior valor
-- tem 14 caracteres — o limite e documentacao executavel do tamanho do
-- vocabulario, e barra lixo colado por engano, como o CHECK de tamanho de
-- usuario.email (V014).
-- CONSEQUENCIA OBRIGATORIA EM JPA: com ddl-auto=validate, o campo desta
-- coluna precisa de @Column(length = 20). Sem isso o Hibernate espera
-- varchar(255) para um @Enumerated(EnumType.STRING) e A APLICACAO NAO
-- SOBE — exatamente o modo de falha que a 0027 registrou.
--
-- ---------------------------------------------------------------------
-- 3. espelha_canal_id: FK COMPOSTA, e ela e auto-referente
-- ---------------------------------------------------------------------
-- Decisao 0015 / convencao 2 da V005: a FK carrega tenant_id, entao o
-- banco RECUSA FISICAMENTE que um canal do tenant A aponte para um canal
-- do tenant B. Uma FK simples (espelha_canal_id -> canal.id) seria
-- verificada pelo Postgres por dentro, IGNORANDO RLS, e o vinculo cruzado
-- passaria calado — para so aparecer depois como soma errada, que e o
-- unico numero que esta coluna existe para proteger.
-- O alvo ja existe: uq_canal_tenant_id UNIQUE (tenant_id, id), criada na
-- V005 exatamente para isso. NADA precisou ser criado aqui — o padrao da
-- 0015 ja tinha previsto o caso, inclusive o auto-referente.
--
-- NULL na FK composta: `espelha_canal_id IS NULL` com `tenant_id` sempre
-- preenchido. O default do SQL e MATCH SIMPLE, que DISPENSA a verificacao
-- quando qualquer coluna da chave e NULL — que e o comportamento desejado
-- (canal sem espelho nao aponta para nada). Se um dia alguem trocar por
-- MATCH FULL, toda linha nao-espelho passa a ser rejeitada.
--
-- ---------------------------------------------------------------------
-- 4. DECISAO DESTA MIGRATION: escopo_declarado_por existe, e um uuid de
--    usuario, e e nullable
-- ---------------------------------------------------------------------
-- A pergunta foi levantada por causa da decisao 0012 ("executado_por e
-- prova de auditoria, nao metrica de pessoa"). A resposta, com a
-- justificativa, porque ela nao e obvia:
--
-- POR QUE A COLUNA EXISTE. Esta declaracao nao e um clique de tela: ela
-- muda o numero que o sistema apresenta como faturamento do mes. Quando a
-- lojista contestar um total — e a regra 3 do CLAUDE.md diz que ela vai —
-- a resposta precisa ser "este total soma ML Classico e ML Premium; o
-- Bling ficou de fora porque foi declarado espelho do ML Classico em
-- 14/09, por Fulana". Sem o autor, a explicacao fica pela metade
-- justamente no ponto em que a 0033 diz que o conhecimento e da lojista e
-- nao nosso. `escopo_declarado_em` sozinho responde "quando", nunca
-- "quem foi que sabia disso".
--
-- POR QUE uuid DE usuario, E NAO TEXTO. Guardar nome ou e-mail congelaria
-- dado pessoal dentro de `canal`, fora do alcance da desativacao e de
-- qualquer expurgo/anonimizacao futuro (0012, restricao 3) — e duplicaria
-- o que `usuario` ja guarda. Com o uuid, o nome sai de um JOIN, some
-- quando tiver de sumir, e a FK composta impede apontar para usuario de
-- outro tenant. A V014 ja tinha previsto este uso em voz alta ao criar
-- uq_usuario_tenant_id: "toda trilha de acao ('alterado por') vai
-- referenciar (tenant_id, usuario_id)". Esta e a primeira delas.
--
-- POR QUE NULLABLE, E NAO NOT NULL QUANDO DECLARADO. Existem declaracoes
-- legitimas sem usuario logado: o seed de demonstracao, o
-- provisionamento, e uma eventual migration de dados. Exigir autor
-- obrigaria esses caminhos a INVENTAR um usuario para conseguir gravar —
-- regra 5 do CLAUDE.md, e o mesmo motivo pelo qual pedido.cliente_id e
-- nullable (V008). NULL aqui tem significado definido e util:
-- "declarado fora da aplicacao". O que o CHECK garante e o contrario —
-- autor sem declaracao nao pode existir.
--
-- O LIMITE DA 0012, QUE CONTINUA VALENDO AQUI: esta coluna e prova
-- pontual, nunca metrica. NAO existe, e nao deve passar a existir,
-- `GROUP BY escopo_declarado_por`, tela de "declaracoes por pessoa" nem
-- ranking de quem configurou mais canal. O acesso legitimo e ler a linha
-- do canal cuja declaracao esta sendo explicada ou contestada. Numa
-- tabela com um punhado de linhas por tenant, agregar por pessoa nao seria
-- nem util nem inocente.
--
-- ---------------------------------------------------------------------
-- 5. O QUE O BANCO GARANTE SOZINHO (e nao a aplicacao)
-- ---------------------------------------------------------------------
--   a) espelha_canal_id preenchido SE E SOMENTE SE escopo = 'ESPELHO'.
--      Os dois lados importam: espelho sem alvo e uma declaracao que nao
--      diz espelho DE QUEM (a 0033 recusou o booleano `somavel` por
--      exatamente isso); alvo em canal FONTE_PRIMARIA ou NAO_DECLARADO e
--      dado fantasma que a proxima leitura interpreta como sobreposicao
--      inexistente e recusa uma soma legitima.
--   b) canal nao espelha a si mesmo.
--   c) escopo_declarado_em preenchido SE E SOMENTE SE houve declaracao.
--      Data sem declaracao e ruido; declaracao sem data tira da explicacao
--      o "desde quando" — que e o que separa "a lojista declarou" de "o
--      sistema assumiu".
--   d) escopo_declarado_por so pode existir se houve declaracao (ver 4).
--
-- DECLARAR E UM UPDATE DAS QUATRO COLUNAS JUNTAS. CHECK e avaliado por
-- linha, ao fim da instrucao: voltar um canal para NAO_DECLARADO exige
-- zerar espelha_canal_id, escopo_declarado_em e escopo_declarado_por na
-- MESMA instrucao. Em dois UPDATEs, o primeiro e recusado — e isso e o
-- comportamento correto, nao um obstaculo a contornar.
--
-- ---------------------------------------------------------------------
-- 6. CICLOS: o que o banco pega, e o que ELE NAO PEGA (leia inteiro)
-- ---------------------------------------------------------------------
-- O CICLO DE DOIS (A espelha B e B espelha A) E IMPEDIDO, sem gatilho, por
-- uq_canal_espelho_reciproco: um indice unico sobre o PAR NAO ORDENADO
-- (least(id, espelha_canal_id), greatest(...)). A -> B e B -> A produzem o
-- mesmo par {A,B}, entao a segunda linha colide. Nao ha falso positivo:
-- como `id` e unico, duas linhas so podem gerar o mesmo par se forem
-- exatamente essas duas. O caso importa porque a propria 0033 o cita como
-- vantagem do desenho com espelha_canal_id sobre um booleano ("detectar
-- que ela marcou dois canais como espelho um do outro") — e porque A<->B e
-- o estado em que NENHUM dos dois e a fonte, ou seja, a declaracao nao
-- significa mais nada.
--
-- O QUE O BANCO NAO PEGA, DITO SEM EUFEMISMO:
--   - CICLO DE TRES OU MAIS: A espelha B, B espelha C, C espelha A. Os
--     tres pares sao distintos, o indice nao ve nada, e as tres linhas
--     entram.
--   - ESPELHO DE ESPELHO (cadeia sem ciclo): A espelha B e B espelha C.
--     E legal no banco. A 0033 nao proibe a cadeia, mas quem calcula
--     precisa segui-la ate a raiz para saber com quem A se sobrepoe.
-- POR QUE NAO FECHAMOS ISSO NO BANCO: CHECK nao enxerga outra linha, e o
-- resto exige gatilho recursivo (a instrucao desta tarefa e explicita:
-- nao inventar gatilho) ou uma FK de tres colunas com coluna gerada para
-- obrigar o alvo a ser FONTE_PRIMARIA. A segunda funcionaria e foi
-- considerada: custaria uma coluna a mais, tornaria a ORDEM da declaracao
-- obrigatoria (declarar o espelho antes da primaria passaria a dar erro de
-- integridade referencial na cara da lojista) e proibiria a cadeia que a
-- 0033 nao proibiu. Complexidade demais para um caso que exige tres
-- declaracoes erradas seguidas.
--
-- QUAL E, EXATAMENTE, O ESTRAGO DO CASO NAO COBERTO — e ele e menor do
-- que parece, o que e parte da razao de nao gastarmos um gatilho nele:
-- todo canal de um ciclo ou de uma cadeia esta declarado ESPELHO, e
-- ESPELHO ja e excluido de QUALQUER soma pela regra da 0033 (so entram
-- canais FONTE_PRIMARIA — e um FONTE_PRIMARIA nunca tem espelha_canal_id,
-- pelo ck_canal_espelho_exige_alvo). Ou seja, o ciclo de tres NAO produz
-- faturamento dobrado: ele produz um conjunto de canais que some de todo
-- total, sem ninguem entender por que. O modo de falha e NUMERO FALTANDO,
-- nunca numero errado — e "faltando" e o lado em que este produto aceita
-- errar (a mesma escolha da lacuna declarada da 0019).
-- ONDE A VERIFICACAO MORA, ENTAO: NA APLICACAO, e como DIAGNOSTICO da tela
-- de declaracao, nao como guarda do calculo. Percorrer espelha_canal_id
-- ate a raiz com limite de profundidade, e avisar "estes canais se
-- declaram espelho em circulo; nenhum deles entra em nenhum total" — uma
-- consulta e um `while`, com canais na casa das dezenas por tenant. O
-- limite de profundidade nao e zelo: sem ele, o ciclo que o banco aceita
-- vira laco infinito na aplicacao.
--
-- ---------------------------------------------------------------------
-- 7. RLS E PRIVILEGIOS: o que muda (nada) e por que esta escrito aqui
-- ---------------------------------------------------------------------
-- ALTER TABLE ... ADD COLUMN NAO mexe em relrowsecurity nem em
-- relforcerowsecurity, e nao invalida policy nenhuma: as quatro policies
-- de canal (V005) filtram por tenant_id, coluna que esta migration nao
-- toca. O sentinela RlsAtivoEmTodasAsTabelasTest continua passando, e
-- continua sendo ele quem prova isso — nao este comentario.
--
-- GRANT: canal tem GRANT SELECT, INSERT, UPDATE na TABELA INTEIRA (V005),
-- e no Postgres um privilegio de tabela alcanca as colunas futuras. Logo
-- NAO HA GRANT A ESTENDER aqui: as quatro colunas ja nascem gravaveis pela
-- aplicacao, que e o desejado — declarar escopo e operacao de aplicacao, e
-- a 0033 diz que corrigir uma declaracao errada tem de ser barato.
-- Isto e diferente da V013/V014, que concedem UPDATE POR COLUNA: la a
-- linha e referenciada como memoria de calculo e reescreve-la apagaria a
-- explicacao de um numero ja mostrado. Aqui nao ha ponteiro para essas
-- colunas: a declaracao e configuracao corrente, feita para mudar.
-- REGISTRO PARA A PROXIMA AUDITORIA (achado, nao alterado por esta
-- migration): o GRANT UPDATE largo da V005 tambem alcanca codigo, tipo,
-- categoria e criado_em. Estreita-lo e mudanca de comportamento em
-- caminhos de escrita que ja existem, com impacto fora do escopo da
-- tarefa 31 — fica anotado para virar decisao propria, nao um efeito
-- colateral desta.
--
-- ---------------------------------------------------------------------
-- 8. INDICES
-- ---------------------------------------------------------------------
-- Alem do indice unico do par (secao 6), NENHUM outro, e a ausencia e
-- deliberada. A consulta de disjuncao pergunta "quem, dentro deste
-- conjunto, aponta para este canal", o que seria um indice em
-- (tenant_id, espelha_canal_id). Nao entra: sao unidades a poucas dezenas
-- de canais por tenant, uq_canal_tenant_id ja restringe a varredura ao
-- tenant, e indice que nao muda plano so custa escrita (mesmo racional
-- declarado na V014). Se a tela de canais um dia ficar lenta, o indice
-- entra por migration, com o EXPLAIN que o justificou.
-- Nota: o Postgres NAO cria indice do lado REFERENCIADOR de uma FK. Aqui
-- isso e inofensivo porque canal nao tem DELETE para a aplicacao
-- (convencao 6 da V005) e `id` nunca muda, entao a verificacao reversa da
-- FK praticamente nunca roda.
-- =====================================================================


-- ---------------------------------------------------------------------
-- As quatro colunas
-- ---------------------------------------------------------------------
ALTER TABLE canal
    ADD COLUMN escopo_declarado     varchar(20) NOT NULL DEFAULT 'NAO_DECLARADO',
    ADD COLUMN espelha_canal_id     uuid,
    ADD COLUMN escopo_declarado_em  timestamptz,
    ADD COLUMN escopo_declarado_por uuid;


-- ---------------------------------------------------------------------
-- Dominio (convencao 5 da V005: CHECK sobre texto, nunca ENUM nativo)
-- ---------------------------------------------------------------------
-- Sem 'OUTRO' aqui, ao contrario de canal.tipo: la o escape hatch evita
-- PERDER dado de uma fonte externa (regra 5 do CLAUDE.md); aqui nao ha
-- fonte externa nenhuma — o valor e uma declaracao humana, e um quarto
-- estado seria um escopo que a regra de soma nao sabe tratar.
ALTER TABLE canal
    ADD CONSTRAINT ck_canal_escopo_declarado CHECK (escopo_declarado IN (
        'NAO_DECLARADO',
        'FONTE_PRIMARIA',
        'ESPELHO'
    ));


-- ---------------------------------------------------------------------
-- FKs compostas (decisao 0015). Ver secoes 3 e 4 do cabecalho.
-- ---------------------------------------------------------------------
ALTER TABLE canal
    ADD CONSTRAINT fk_canal_espelha_canal
        FOREIGN KEY (tenant_id, espelha_canal_id) REFERENCES canal (tenant_id, id);

ALTER TABLE canal
    ADD CONSTRAINT fk_canal_escopo_declarado_por_usuario
        FOREIGN KEY (tenant_id, escopo_declarado_por) REFERENCES usuario (tenant_id, id);


-- ---------------------------------------------------------------------
-- Coerencia da declaracao. Ver secao 5 do cabecalho.
-- ---------------------------------------------------------------------
-- Bicondicional escrita como igualdade de dois booleanos. Nenhum lado
-- pode ser NULL (escopo_declarado e NOT NULL e `IS NOT NULL` nunca
-- devolve NULL), entao o CHECK e sempre conclusivo — o modo de falha que
-- derruba constraint mal escrita no SQL (o CHECK que "passa" porque
-- avaliou NULL) nao existe aqui.
ALTER TABLE canal
    ADD CONSTRAINT ck_canal_espelho_exige_alvo
        CHECK ((escopo_declarado = 'ESPELHO') = (espelha_canal_id IS NOT NULL));

ALTER TABLE canal
    ADD CONSTRAINT ck_canal_nao_espelha_a_si_mesmo
        CHECK (espelha_canal_id IS NULL OR espelha_canal_id <> id);

ALTER TABLE canal
    ADD CONSTRAINT ck_canal_escopo_declarado_em_coerente
        CHECK ((escopo_declarado <> 'NAO_DECLARADO') = (escopo_declarado_em IS NOT NULL));

-- Implicacao simples, nao bicondicional: autor exige declaracao, mas
-- declaracao NAO exige autor (seed, provisionamento e migration declaram
-- sem usuario logado — secao 4 do cabecalho).
ALTER TABLE canal
    ADD CONSTRAINT ck_canal_escopo_declarado_por_coerente
        CHECK (escopo_declarado <> 'NAO_DECLARADO' OR escopo_declarado_por IS NULL);


-- ---------------------------------------------------------------------
-- O ciclo de dois, impedido declarativamente. Ver secao 6 do cabecalho.
-- ---------------------------------------------------------------------
-- E indice unico, e nao constraint, porque constraint nao aceita
-- expressao. Consequencia pratica a tratar na aplicacao: a violacao chega
-- como "duplicate key value violates unique constraint
-- uq_canal_espelho_reciproco" — mensagem que nao explica nada a lojista.
-- Traduza para "o canal X ja esta declarado como espelho de Y; os dois nao
-- podem ser espelho um do outro" antes de mostrar.
-- tenant_id lidera (decisao 0010) e tambem evita que a colisao atravesse
-- tenants: indice unico roda ABAIXO do RLS, e sem tenant_id na chave uma
-- mensagem de erro revelaria a existencia de canal de outro cliente (mesmo
-- cuidado do ex_taxa_canal_sem_sobreposicao, V013).
-- Parcial: linhas sem espelho nao entram no indice, o que o mantem do
-- tamanho do numero de espelhos declarados e dispensa raciocinio sobre
-- least()/greatest() com NULL.
CREATE UNIQUE INDEX uq_canal_espelho_reciproco
    ON canal (tenant_id,
              least(id, espelha_canal_id),
              greatest(id, espelha_canal_id))
    WHERE espelha_canal_id IS NOT NULL;


-- ---------------------------------------------------------------------
-- Documentacao no proprio schema
-- ---------------------------------------------------------------------
COMMENT ON COLUMN canal.escopo_declarado IS
    'Declaracao da LOJISTA sobre a origem dos pedidos deste canal (NAO_DECLARADO, FONTE_PRIMARIA, ESPELHO) - decisao 0033. Nenhuma heuristica preenche. NAO_DECLARADO e o default e BLOQUEIA a soma entre canais: ausencia de informacao nunca vira numero.';
COMMENT ON COLUMN canal.espelha_canal_id IS
    'Canal de quem este e copia, preenchido se e somente se escopo_declarado = ESPELHO. FK COMPOSTA com tenant_id (decisao 0015): o banco recusa apontar para canal de outro tenant. Ciclo A<->B e impedido por uq_canal_espelho_reciproco; cadeia de 3 ou mais e verificada na aplicacao (secao 6 da V016).';
COMMENT ON COLUMN canal.escopo_declarado_em IS
    'Quando a declaracao foi feita. Preenchido se e somente se escopo_declarado <> NAO_DECLARADO. E o "desde quando" da explicacao dada a lojista quando ela contesta um total.';
COMMENT ON COLUMN canal.escopo_declarado_por IS
    'Usuario que declarou (FK composta para usuario). NULL = declarado fora da aplicacao (seed, provisionamento, migration) - estado real, nao ausencia a preencher. PROVA PONTUAL, NUNCA METRICA: nao existe GROUP BY por esta coluna (decisao 0012).';
