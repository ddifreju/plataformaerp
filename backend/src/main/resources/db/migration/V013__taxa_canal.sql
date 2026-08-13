-- =====================================================================
-- V013 — taxa_canal (a regra de cobranca do canal, versionada por vigencia)
-- =====================================================================
-- Convencoes gerais: cabecalho da V005. Especificacao de dominio:
-- docs/fiscal/regras-de-margem.md secao 8. Decisoes ja tomadas e NAO
-- reabertas aqui: docs/decisoes/0019-hierarquia-de-taxas-e-lacuna-declarada.md.
--
-- Esta tabela e o NIVEL 2 da hierarquia de taxas. Os tres niveis, e nao
-- existe um quarto:
--   1. FATO    — valor cobrado, informado pela fonte no pedido/fatura.
--                Vira custo com eh_estimativa = false. NAO passa por aqui.
--   2. REGRA   — esta tabela. Usada SO quando a fonte nao informou o
--                valor. Vira custo com eh_estimativa = TRUE, sempre,
--                mesmo que a aliquota cadastrada esteja certa: a
--                distincao e entre MEDIDO e CALCULADO.
--   3. LACUNA  — nao existe linha vigente para o contexto. NENHUMA linha
--                de custo e criada, a ausencia e reportada. Gravar zero
--                apagaria a lacuna (V010, S5: custo zero e custo
--                desconhecido sao coisas diferentes).
-- Sem "a taxa mais proxima", sem "a taxa de hoje", sem "a media da
-- categoria". Numero plausivel e errado e o pior tipo de saida deste
-- produto.
--
-- ---------------------------------------------------------------------
-- A CONSULTA DE SELECAO — copie daqui
-- ---------------------------------------------------------------------
--   SELECT id, tipo_taxa, natureza_custo, base_incidencia,
--          percentual, valor_fixo, valor_minimo, valor_maximo, moeda,
--          confianca, origem, consultado_em,
--          categoria_canal, tipo_anuncio,
--          faixa_valor_min, faixa_valor_max,
--          vigencia_inicio, vigencia_fim,
--          especificidade
--     FROM taxa_canal
--    WHERE tenant_id = :tenant
--      AND canal_id  = :canal
--      AND tipo_taxa = :tipo_taxa
--      -- vigencia semiaberta [inicio, fim). Ver "FUSO" abaixo: :momento
--      -- JA chega como o instante correto; nao converta aqui.
--      AND vigencia_inicio <= :momento
--      AND (vigencia_fim IS NULL OR vigencia_fim > :momento)
--      -- curinga e o sentinela '*', nunca NULL. Ver "CURINGA" abaixo.
--      AND categoria_canal IN (:categoria, '*')
--      AND tipo_anuncio    IN (:tipo_anuncio, '*')
--      -- faixa semiaberta [min, max). NULL = sem limite daquele lado.
--      AND (faixa_valor_min IS NULL OR :valor >= faixa_valor_min)
--      AND (faixa_valor_max IS NULL OR :valor <  faixa_valor_max)
--    ORDER BY especificidade DESC
--    LIMIT 2;
--
-- COMO LER O RESULTADO (as quatro saidas possiveis, todas obrigatorias):
--   0 linhas  -> LACUNA (nivel 3). Nao crie custo. Reporte a ausencia com
--                tipo_taxa, os discriminadores usados e a data consultada,
--                e ofereca o cadastro ja preenchido.
--   1 linha   -> use.
--   2 linhas com especificidade DIFERENTE -> use a primeira.
--   2 linhas com especificidade IGUAL -> ERRO DE CADASTRO. Aborte o
--                calculo desta taxa e reporte os DOIS ids. Nao escolha a
--                primeira, a mais recente nem a menor: duas linhas
--                empatadas sairiam em ordem fisica, e o MESMO pedido daria
--                margens diferentes em dias diferentes. Isso quebra a
--                regra 3 do CLAUDE.md em silencio, que e o modo de falha
--                mais caro que existe aqui.
--   NAO acrescente um segundo criterio ao ORDER BY para "resolver" o
--   empate. O empate precisa continuar visivel: ele e defeito de cadastro,
--   nao caso de negocio. O LIMIT 2 existe exatamente para enxerga-lo.
--
-- :momento — QUAL data do fato gerador, por tipo_taxa (secao 8.2 da
-- especificacao). "A data do pedido" e ambiguo e escolher errado seleciona
-- a tabela de tarifas errada:
--   COMISSAO, TARIFA_FIXA, PARCELAMENTO, FRETE, FRETE_SUBSIDIO
--                          -> pedido.feito_em (a tarifa e contratada na venda)
--   ANTECIPACAO            -> data da antecipacao (competencia_em da
--                             linha-mae de custo, nao a data do pedido)
--   FRETE_REVERSO          -> devolucao.aberta_em
--   ARMAZENAGEM, TARIFA_ADMINISTRATIVA -> competencia do periodo cobrado
--
-- :valor — QUAL valor de referencia, por tipo_taxa. Isto NAO e sempre o
-- total do pedido, e errar aqui e erro silencioso: leia base_incidencia da
-- propria linha (a coluna existe para que essa escolha seja DADO, e nao um
-- `if` escondido no motor). O paradoxo de partida se resolve assim: para
-- montar a consulta, use o valor previsto pelo tipo_taxa da tabela de
-- base_incidencia abaixo; ao aplicar a taxa encontrada, use o
-- base_incidencia que veio na linha (e grave-o em custo.base_calculo).
--   VALOR_UNITARIO_ITEM  preco de UMA unidade — e a faixa da tarifa fixa
--                        do ML (abaixo de R$ 12,50 / ate R$ 79,00)
--   VALOR_TOTAL_ITEM     preco unitario x quantidade da linha
--   VALOR_TOTAL_PEDIDO   pedido.valor_total_pedido
--   VALOR_FRETE          tarifa de envio (subsidio, frete reverso)
--   VALOR_A_RECEBER      montante antecipado (antecipacao)
--
-- GUARDA DO CURINGA — leia antes de confiar numa linha '*':
-- se :categoria vier NULL (o adaptador nao preservou o category_id do
-- anuncio) a consulta acima casa apenas com as linhas curinga. Isso SO e
-- honesto se nao existir nenhuma taxa por categoria cadastrada para o
-- contexto — curinga significa "vale para todas as categorias", nao
-- "serve quando eu nao sei a categoria". Antes de usar o curinga com
-- categoria desconhecida, verifique:
--
--   SELECT count(*) FROM taxa_canal
--    WHERE tenant_id = :tenant AND canal_id = :canal
--      AND tipo_taxa = :tipo_taxa AND categoria_canal <> '*'
--      AND vigencia_inicio <= :momento
--      AND (vigencia_fim IS NULL OR vigencia_fim > :momento);
--
-- Se voltar > 0, trate como LACUNA ("nao sei a categoria deste anuncio"),
-- nao aplique o curinga. O mesmo raciocinio vale para tipo_anuncio.
--
-- EXEMPLO DE CADASTRO (note o '*' explicito e a vigencia em Sao Paulo):
--   INSERT INTO taxa_canal
--       (tenant_id, canal_id, tipo_taxa, natureza_custo, base_incidencia,
--        categoria_canal, tipo_anuncio, faixa_valor_min, faixa_valor_max,
--        vigencia_inicio, percentual, confianca, origem, observacao)
--   VALUES (:tenant, :canal, 'COMISSAO', 'COMISSAO_CANAL', 'VALOR_TOTAL_ITEM',
--           'MLB1051', 'CLASSICO', NULL, NULL,
--           DATE '2026-03-02' AT TIME ZONE 'America/Sao_Paulo',
--           0.130000, 'INFORMADO_PELO_LOJISTA', 'CADASTRO_MANUAL',
--           'lida do extrato de comissoes de marco/2026');
--   NUNCA liste `especificidade` no INSERT: e GENERATED ALWAYS e o
--   Postgres rejeita a instrucao inteira. Em JPA, mapeie o campo com
--   insertable = false, updatable = false (ou @Generated) — sem isso o
--   Hibernate monta o INSERT com todas as colunas e nada grava.
--
-- ---------------------------------------------------------------------
-- CURINGA: o sentinela '*', e por que NAO e NULL (decisao 0019)
-- ---------------------------------------------------------------------
-- categoria_canal e tipo_anuncio sao NOT NULL e o curinga e o texto
-- literal '*'.
--
-- PORQUE: a constraint EXCLUDE abaixo compara essas colunas com `WITH =`,
-- e em SQL `NULL = NULL` nao e verdadeiro. Duas linhas curinga gravadas
-- com NULL NAO seriam vistas como conflitantes, e a constraint que existe
-- para impedir sobreposicao teria um buraco exatamente no caso mais comum
-- (a taxa geral que o lojista cadastra primeiro). Uma constraint que
-- parece proteger e nao protege e pior que constraint nenhuma.
-- Ganhos colaterais: a consulta vira `IN (:valor, '*')`, legivel; e some
-- a ambiguidade eterna de NULL ("nao se aplica" x "desconhecido" x
-- "todos"). Custo aceito: '*' e valor magico, e valor magico exige este
-- comentario.
--
-- ONDE NULL CONTINUA LEGITIMO, E POR QUE E DIFERENTE:
--   faixa_valor_min / faixa_valor_max / vigencia_fim aceitam NULL, e ali
--   NULL significa "sem limite deste lado" (infinito).
--   A diferenca nao e de gosto: esses tres NAO entram no EXCLUDE como
--   escalar. Eles entram dentro de numrange()/tstzrange(), onde o
--   Postgres interpreta NULL como limite infinito e o operador de
--   sobreposicao && trata o caso corretamente — uma faixa aberta
--   SOBREPOE qualquer outra faixa e a constraint pega. Ou seja: NULL e
--   proibido onde ele criaria buraco (comparacao escalar) e permitido
--   onde ele tem semantica definida (limite de intervalo).
--
-- ---------------------------------------------------------------------
-- INTERVALOS SEMIABERTOS [min, max) — faixa E vigencia
-- ---------------------------------------------------------------------
-- Limite inferior INCLUSIVO, superior EXCLUSIVO, nos dois eixos.
-- R$ 79,00 e um limiar real do Mercado Livre: com limites fechados dos
-- dois lados, um pedido de exatamente R$ 79,00 casaria com [12,50; 79,00]
-- e com [79,00; infinito) ao mesmo tempo, e o resultado dependeria do
-- ORDER BY. Com semiaberto a ambiguidade nao existe por construcao — e o
-- bug aconteceria na primeira semana de uso.
-- Consequencia de modelagem que o cadastro precisa respeitar: dentro do
-- mesmo (canal, tipo_taxa, categoria, tipo_anuncio, vigencia), as faixas
-- PARTICIONAM o eixo de valor, nao se aninham. Nao existe "13% para tudo"
-- convivendo com "11% acima de R$ 1.000": escreva [0; 1000) 13% e
-- [1000; infinito) 11%. O EXCLUDE recusa o aninhamento, e isso e
-- proposital — faixa que engloba outra faixa e ambiguidade disfarcada.
--
-- ---------------------------------------------------------------------
-- FUSO America/Sao_Paulo — onde a conversao acontece DE VERDADE
-- ---------------------------------------------------------------------
-- As fronteiras de vigencia de tarifa de marketplace sao anunciadas em
-- hora de Brasilia. Uma venda as 22:00 de 01/03 em Sao Paulo e 01:00 de
-- 02/03 em UTC: comparar contra uma fronteira gravada como meia-noite UTC
-- escolhe a tabela errada para as tres primeiras horas de pedidos de todo
-- dia de virada de tarifa.
--
-- ONDE a conversao entra, e e so nestes dois pontos:
--   a) AO GRAVAR a fronteira. "vigente a partir de 02/03/2026" e o
--      instante `DATE '2026-03-02' AT TIME ZONE 'America/Sao_Paulo'`,
--      nunca `TIMESTAMP '2026-03-02 00:00'` interpretado no fuso do
--      servidor. Idem para vigencia_fim.
--   b) AO DERIVAR :momento de uma DATA (nao de um instante). O caso
--      concreto e a competencia mensal: "primeiro dia do mes de
--      competencia" e
--      `date_trunc('month', :data AT TIME ZONE 'America/Sao_Paulo')
--       AT TIME ZONE 'America/Sao_Paulo'`.
--      Quando :momento ja e um timestamptz vindo do dado (feito_em,
--      aberta_em), nao ha nada a converter: ele ja e um instante absoluto.
--
-- ONDE A CONVERSAO NAO ENTRA — e isto e uma armadilha, nao um detalhe:
-- NAO escreva `vigencia_inicio AT TIME ZONE 'America/Sao_Paulo' <=
-- :momento AT TIME ZONE 'America/Sao_Paulo'` na consulta. Comparar dois
-- timestamptz ja compara instantes absolutos; converter OS DOIS LADOS
-- para o mesmo fuso nao muda resultado nenhum (e um no-op matematico) e
-- ainda transforma a coluna em expressao, o que descarta o indice
-- ix_taxa_canal_selecao. Fica caro e nao compra nada. O teste unitario
-- obrigatorio do fuso (caso de 23:30 do dia anterior a virada) deve
-- atacar o ponto (a)/(b) acima, que e onde o erro realmente mora.
--
-- ---------------------------------------------------------------------
-- ESCALAS: dinheiro NUMERIC(18,4), percentual NUMERIC(9,6)
-- ---------------------------------------------------------------------
-- Dinheiro segue a convencao 3 da V005 sem excecao: NUMERIC(18,4).
--
-- Percentual tem escala PROPRIA, e maior, por tres motivos concretos:
--   1. Ele e multiplicado, dinheiro e somado. Uma imprecisao na aliquota
--      e multiplicada pela base inteira; uma imprecisao no valor fica
--      onde esta.
--   2. Aliquota efetiva do Simples tem 5 a 6 casas significativas:
--      0,067280. Truncada em 4 casas (0,0673) muda o imposto de um mes de
--      R$ 500.000,00 em R$ 20,00 — e o lojista confere isso contra a guia.
--   3. E a MESMA escala de custo.aliquota_aplicada (V010): a aliquota
--      cadastrada vai para a memoria de calculo sem conversao nem perda.
--      Se a origem tivesse mais casas que o destino, a linha de custo
--      mentiria sobre a aliquota que foi usada, e a regra 3 do CLAUDE.md
--      cairia justamente na coluna que existe para sustenta-la.
-- 9 digitos = 3 inteiros + 6 decimais: sobra folga para aliquota acima de
-- 100% (existe, em multa e taxa punitiva) sem desperdicio.
--
-- FRACAO DECIMAL, sempre: 0,130000 = 13%. Nunca 13.0. Ambiguidade de
-- percentual e erro por fator 100 esperando acontecer (V010).
--
-- ---------------------------------------------------------------------
-- O QUE NAO MORA AQUI: imposto
-- ---------------------------------------------------------------------
-- A especificacao (secao 5.1) sugere guardar a tabela do Simples Anexo I
-- "no mesmo mecanismo da secao 8" com tipo_taxa = 'IMPOSTO_SIMPLES_ANEXO_I'.
-- O MECANISMO e o mesmo (vigencia semiaberta, confianca, fonte, undo
-- pareado); a TABELA nao pode ser, por tres razoes de schema:
--   1. Uma faixa do Anexo I precisa de aliquota nominal E parcela a
--      deduzir na MESMA linha (9,50% e R$ 13.860,00). Aqui vale
--      exatamente um entre percentual e valor_fixo — o CHECK que impede a
--      ambiguidade "qual dos dois esta em uso" e o mesmo que torna a
--      faixa do Anexo I inexprimivel.
--   2. canal_id e NOT NULL. Imposto nao e do canal: e do TENANT e do
--      regime tributario. Aceitar imposto aqui significaria afrouxar a FK
--      que garante que toda taxa pertence a um canal.
--   3. A chave de selecao do imposto e (regime, competencia, faixa de
--      RBT12), nao (canal, categoria, tipo de anuncio, faixa de valor).
-- Afrouxar as tres coisas na tabela mais critica do calculo para
-- economizar uma tabela e troca ruim. O imposto versionado e tabela irma,
-- da tarefa de imposto, com o mesmo molde de RLS e o mesmo desenho de
-- vigencia. Registrado aqui para que a divergencia com a secao 5.1 seja
-- deliberada e visivel, nao esquecimento.
--
-- ---------------------------------------------------------------------
-- CORRECAO RETROATIVA E "DESLIGAR" UMA TAXA (secao 8.4)
-- ---------------------------------------------------------------------
-- Nao existe coluna `ativo`, e nao ha DELETE para a aplicacao. Vigencia E
-- o ciclo de vida:
--   - Corrigiu a aliquota? Encerre a linha antiga (UPDATE vigencia_fim) e
--     insira a nova. O EXCLUDE RECUSA a linha nova enquanto a antiga
--     estiver aberta — e isso e o comportamento correto: corrigir o
--     passado exige dizer ate quando o passado errado valeu.
--   - Cadastrou por engano e a linha nunca foi usada? `vigencia_fim =
--     vigencia_inicio`. O intervalo fica VAZIO: nunca e selecionado pela
--     consulta e nao conflita com ninguem no EXCLUDE (intervalo vazio nao
--     sobrepoe nada). E por isso que o CHECK de vigencia aceita `>=` e nao
--     exige `>`.
--   - Linhas de custo ja geradas NAO sao atualizadas. Recalculo e estorno
--     (valor negativo) + linha nova, S5 da V010. E o recalculo e acionado
--     pelo lojista, com previa de quantos pedidos mudam: reescrever em
--     silencio a margem de 400 pedidos que ele ja olhou e pior que a taxa
--     errada.
--
-- ---------------------------------------------------------------------
-- A API DE SIMULACAO DO ML POPULA, NUNCA CALCULA
-- ---------------------------------------------------------------------
-- GET sites/MLB/listing_prices?price=&category_id=&listing_type_id=
-- devolve sale_fee_amount da taxa VIGENTE HOJE. Ela e uma fonte de
-- CADASTRO desta tabela (origem = 'API_CANAL', confianca =
-- 'CONFIRMADO_FONTE_OFICIAL', vigencia_inicio = dia da consulta), e nunca
-- e chamada no caminho de calculo de um pedido. Calcular um pedido de
-- marco com a taxa lida em agosto e precisamente o erro que esta tabela
-- existe para impedir — e ele nao deixa rastro nenhum no resultado.
-- O CHECK ck_taxa_canal_api_nao_retroage transforma esse erro em violacao
-- de constraint em vez de numero errado.
-- =====================================================================

-- btree_gist: necessaria para usar `WITH =` sobre uuid e text dentro de
-- um EXCLUDE USING gist. Sem ela, gist so sabe comparar os tipos de
-- intervalo, e a constraint de nao sobreposicao nao pode ser criada.
-- IF NOT EXISTS pela mesma razao da V001: reaplicar a migration precisa
-- ser inofensivo.
CREATE EXTENSION IF NOT EXISTS "btree_gist";


CREATE TABLE taxa_canal (
    id                uuid          NOT NULL DEFAULT gen_random_uuid(),

    -- Parte da identidade. Sem DEFAULT de proposito (decisao 0010): bug
    -- de contexto deve virar erro, nunca linha gravada em silencio.
    tenant_id         uuid          NOT NULL,

    -- Taxa e SEMPRE de um canal. Duas contas de Mercado Livre do mesmo
    -- lojista sao dois canais e podem ter acordos comerciais diferentes,
    -- entao a taxa nao pode ser "do Mercado Livre", tem que ser da conta.
    canal_id          uuid          NOT NULL,

    -- ------------------------------------------------------------------
    -- Identidade da regra
    -- ------------------------------------------------------------------
    -- tipo_taxa: o que esta regra precifica. E o discriminador de
    -- SELECAO. Dominio fechado por CHECK (convencao 5 da V005).
    tipo_taxa         text          NOT NULL,

    -- natureza_custo: em que linha de `custo` (V010) esta taxa vira
    -- dinheiro, e portanto em que bloco da margem ela cai. Coluna
    -- separada de tipo_taxa porque a relacao e MUITOS-PARA-UM: COMISSAO e
    -- TARIFA_FIXA sao regras distintas com vigencias distintas, e FRETE e
    -- FRETE_SUBSIDIO desembocam na mesma natureza FRETE. O par valido e
    -- garantido por CHECK: assim o mapa regra -> bloco de margem e DADO
    -- verificavel no schema, e nao um switch escondido no motor.
    natureza_custo    text          NOT NULL,

    -- base_incidencia: SOBRE O QUE o percentual incide e, pela mesma
    -- razao, qual valor a faixa compara. Existe porque isso muda por tipo
    -- de taxa e por canal: a tarifa fixa do ML tem faixa por preco
    -- UNITARIO, e a comissao incide sobre a linha do item — nao sobre o
    -- total do pedido. Sem esta coluna, essa escolha viraria um `if` por
    -- tipo_taxa no motor, invisivel para quem cadastra e impossivel de
    -- auditar. O valor daqui e o que o motor grava em custo.base_calculo.
    base_incidencia   text          NOT NULL,

    -- categoria_canal: a categoria NA FONTE ('MLB1051'), nao uma
    -- categoria nossa — e o que o adaptador consegue provar. NOT NULL:
    -- curinga e o sentinela '*'. Sem DEFAULT de proposito: quem cadastra
    -- declara se a linha vale para uma categoria ou para todas. Um
    -- DEFAULT '*' faria o esquecimento virar a regra mais abrangente
    -- possivel, que e o lado errado para errar.
    categoria_canal   text          NOT NULL,

    -- tipo_anuncio: valor CANONICO NOSSO, mapeado pelo adaptador a partir
    -- do listing_type_id da fonte (gold_special -> CLASSICO, gold_pro ->
    -- PREMIUM). Guardar o codigo cru da fonte aqui amarraria a tabela de
    -- taxas ao vocabulario do Mercado Livre. Curinga tambem e '*' — e e
    -- ele que atende canal que nem tem tipo de anuncio (Shopee, loja
    -- propria).
    tipo_anuncio      text          NOT NULL,

    -- Faixa de valor, semiaberta [min, max). NULL = sem limite deste
    -- lado. O significado do valor comparado vem de base_incidencia.
    faixa_valor_min   numeric(18,4),
    faixa_valor_max   numeric(18,4),

    -- ------------------------------------------------------------------
    -- Vigencia, semiaberta [inicio, fim)
    -- ------------------------------------------------------------------
    vigencia_inicio   timestamptz   NOT NULL,
    -- NULL = vigencia aberta (vale ate hoje). Aqui NULL e legitimo e tem
    -- semantica definida, ao contrario do curinga de categoria: este
    -- valor entra no EXCLUDE dentro de tstzrange(), onde NULL significa
    -- "infinito" e o operador && trata a sobreposicao corretamente.
    vigencia_fim      timestamptz,

    -- ------------------------------------------------------------------
    -- O valor da taxa: percentual OU valor fixo, nunca os dois
    -- ------------------------------------------------------------------
    -- FRACAO DECIMAL: 0.130000 = 13%. Nunca 13.0. Escala propria (9,6),
    -- justificada no cabecalho.
    percentual        numeric(9,6),
    -- Tarifa em dinheiro (tarifa fixa por item, taxa administrativa).
    -- NUMERIC(18,4), convencao 3 da V005.
    valor_fixo        numeric(18,4),
    -- Piso e teto do valor RESULTANTE da taxa, quando o canal os pratica
    -- ("comissao de 13%, minimo R$ 6,00"). So fazem sentido com
    -- percentual: com valor fixo, o piso e o proprio valor.
    valor_minimo      numeric(18,4),
    valor_maximo      numeric(18,4),
    -- Convencao 3 da V005: moeda junto do valor. ML CBT e Ads em USD
    -- existem, e somar moedas diferentes sem perceber e a pior classe de
    -- bug financeiro.
    moeda             char(3)       NOT NULL DEFAULT 'BRL',

    -- ------------------------------------------------------------------
    -- especificidade: a ordem de desempate, materializada
    -- ------------------------------------------------------------------
    -- GENERATED ALWAYS ... STORED e nao expressao na consulta, por tres
    -- motivos: a ordenacao passa a ser propriedade do DADO (duas consultas
    -- diferentes nao podem discordar sobre qual taxa e mais especifica);
    -- fica indexavel; e o empate vira detectavel comparando um numero, o
    -- que e o que o `LIMIT 2` do cabecalho explora.
    -- Pesos 4/2/1: categoria e o discriminador mais forte, faixa o mais
    -- fraco. Potencias de dois dao ordem TOTAL entre combinacoes
    -- distintas — duas linhas so empatam se tiverem exatamente o mesmo
    -- conjunto de discriminadores preenchido, que e justamente o caso que
    -- deve falhar alto.
    -- Compara com '*' (e nao IS NOT NULL) porque o curinga aqui e o
    -- sentinela; a coluna e NOT NULL.
    especificidade    integer       NOT NULL GENERATED ALWAYS AS (
                          (categoria_canal <> '*')::int * 4
                        + (tipo_anuncio    <> '*')::int * 2
                        + (faixa_valor_min IS NOT NULL
                           OR faixa_valor_max IS NOT NULL)::int * 1
                      ) STORED,

    -- ------------------------------------------------------------------
    -- PROCEDENCIA — regra 5 do CLAUDE.md dentro do schema
    -- ------------------------------------------------------------------
    -- confianca: o que permite a interface escrever "comissao 13% —
    -- informada por voce em 08/01/2026" em vez de apresentar palpite como
    -- fato. Nenhuma taxa entra sem ela, e nao ha DEFAULT: o valor mais
    -- otimista nunca deve ser o que se ganha por esquecimento.
    confianca         text          NOT NULL,
    -- origem: COMO a linha chegou aqui. Diferente de confianca — uma
    -- planilha importada pode conter dado oficial, e uma digitacao manual
    -- pode ser palpite. Separadas porque respondem perguntas diferentes:
    -- "quao boa e a fonte" e "por onde ela entrou".
    origem            text          NOT NULL,
    fonte_url         text,
    consultado_em     timestamptz,
    -- observacao: contexto para o humano que revisar isto daqui a um ano.
    -- Obrigatoria quando confianca = 'ESTIMADO' (ver CHECK): estimativa
    -- sem explicacao e indistinguivel de invencao.
    observacao        text,

    -- ------------------------------------------------------------------
    -- Origem do dado (convencao 4 da V005)
    -- ------------------------------------------------------------------
    -- id_externo: identificador da regra NA FONTE, quando ela tem um.
    -- Para linhas vindas de listing_prices o adaptador SINTETIZA um id
    -- deterministico que precisa incluir a DATA DA CONSULTA — senao a
    -- repopulacao de amanha colide com a linha de hoje no indice de
    -- idempotencia, e a taxa nova nao entra.
    id_externo        text,
    -- dados_origem: o payload da fonte que nao coube no modelo canonico
    -- (price, category_id, listing_type_id, sale_fee_amount, listing_fee).
    dados_origem      jsonb         NOT NULL DEFAULT '{}'::jsonb,
    sincronizado_em   timestamptz,
    criado_em         timestamptz   NOT NULL DEFAULT now(),
    atualizado_em     timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT pk_taxa_canal PRIMARY KEY (id),
    -- Convencao 2 da V005: identidade composta. E o indice com tenant_id
    -- na primeira posicao exigido pela 0010 e o alvo de FKs compostas
    -- futuras (uma linha de custo que queira apontar para a taxa que a
    -- gerou, por exemplo).
    CONSTRAINT uq_taxa_canal_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_taxa_canal_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    -- FK COMPOSTA (decisao 0015): impede fisicamente que a taxa do tenant
    -- A aponte para o canal do tenant B. Uma FK simples seria verificada
    -- por dentro, ignorando RLS, e o vinculo cruzado passaria calado —
    -- para so aparecer depois como margem errada.
    CONSTRAINT fk_taxa_canal_canal
        FOREIGN KEY (tenant_id, canal_id) REFERENCES canal (tenant_id, id),

    -- ------------------------------------------------------------------
    -- Dominios
    -- ------------------------------------------------------------------
    -- tipo_taxa: cada valor e uma regra de cobranca com vigencia propria.
    --   COMISSAO               percentual do marketplace sobre a venda
    --   TARIFA_FIXA            tarifa por item de baixo valor
    --   PARCELAMENTO           custo de vender em N vezes (NAO lancar em
    --                          anuncio Premium: ja esta embutido na
    --                          comissao, e lancar de novo conta duas vezes)
    --   ANTECIPACAO            custo de receber antes do prazo
    --   PAGAMENTO              gateway / meio de pagamento
    --   FRETE                  tabela de envio cobrada do vendedor
    --   FRETE_SUBSIDIO         parcela do frete que o canal cobre
    --   FRETE_REVERSO          retorno na devolucao
    --   ARMAZENAGEM            estoque parado em fulfillment
    --   TARIFA_ADMINISTRATIVA  mensalidade de plano do canal
    --   OUTRO                  escape hatch consciente; aparecer em
    --                          producao e sinal de tipo faltando
    -- Imposto NAO esta aqui de proposito — ver o cabecalho.
    CONSTRAINT ck_taxa_canal_tipo_taxa CHECK (tipo_taxa IN (
        'COMISSAO',
        'TARIFA_FIXA',
        'PARCELAMENTO',
        'ANTECIPACAO',
        'PAGAMENTO',
        'FRETE',
        'FRETE_SUBSIDIO',
        'FRETE_REVERSO',
        'ARMAZENAGEM',
        'TARIFA_ADMINISTRATIVA',
        'OUTRO'
    )),
    -- Mesmo dominio de custo.natureza (V010). A lista e repetida em vez
    -- de referenciada porque tabela de dominio compartilhada seria tabela
    -- sem tenant_id (excecao ao molde da 0010) ou um join em toda leitura.
    CONSTRAINT ck_taxa_canal_natureza_custo CHECK (natureza_custo IN (
        'COMISSAO_CANAL',
        'TARIFA_FIXA_CANAL',
        'TAXA_PAGAMENTO',
        'TAXA_PARCELAMENTO',
        'TAXA_ANTECIPACAO',
        'FRETE',
        'FRETE_REVERSO',
        'ARMAZENAGEM',
        'TARIFA_ADMINISTRATIVA',
        'OUTRO'
    )),
    -- O mapa regra -> natureza, no schema. Impede cadastrar uma COMISSAO
    -- que gera linha de FRETE — erro que ninguem percebe olhando a tela
    -- de cadastro e que desloca dinheiro de um bloco da margem para outro
    -- (B3 e B4 somam igual no total e contam historias diferentes).
    -- Este CHECK ja implica os dois de dominio acima. Eles ficam mesmo
    -- assim, e a redundancia e deliberada: sao eles que documentam o
    -- dominio completo de cada coluna em um lugar so, dao mensagem de
    -- erro especifica ("tipo_taxa invalido" x "par incoerente") e sao o
    -- que o grep encontra quando alguem procurar os valores validos.
    CONSTRAINT ck_taxa_canal_tipo_natureza_coerentes CHECK (
        (tipo_taxa, natureza_custo) IN (
            ('COMISSAO',              'COMISSAO_CANAL'),
            ('TARIFA_FIXA',           'TARIFA_FIXA_CANAL'),
            ('PARCELAMENTO',          'TAXA_PARCELAMENTO'),
            ('ANTECIPACAO',           'TAXA_ANTECIPACAO'),
            ('PAGAMENTO',             'TAXA_PAGAMENTO'),
            ('FRETE',                 'FRETE'),
            ('FRETE_SUBSIDIO',        'FRETE'),
            ('FRETE_REVERSO',         'FRETE_REVERSO'),
            ('ARMAZENAGEM',           'ARMAZENAGEM'),
            ('TARIFA_ADMINISTRATIVA', 'TARIFA_ADMINISTRATIVA'),
            ('OUTRO',                 'OUTRO')
        )
    ),
    CONSTRAINT ck_taxa_canal_base_incidencia CHECK (base_incidencia IN (
        'VALOR_UNITARIO_ITEM',
        'VALOR_TOTAL_ITEM',
        'VALOR_TOTAL_PEDIDO',
        'VALOR_FRETE',
        'VALOR_A_RECEBER'
    )),
    -- Valores canonicos nossos (secao 4.2 da especificacao). '*' e o
    -- curinga e atende tambem canal sem tipo de anuncio.
    CONSTRAINT ck_taxa_canal_tipo_anuncio CHECK (tipo_anuncio IN (
        'CLASSICO',
        'PREMIUM',
        'GRATIS',
        'LEGADO',
        '*'
    )),
    CONSTRAINT ck_taxa_canal_confianca CHECK (confianca IN (
        'CONFIRMADO_FONTE_OFICIAL',
        'INFORMADO_PELO_LOJISTA',
        'ESTIMADO'
    )),
    CONSTRAINT ck_taxa_canal_origem CHECK (origem IN (
        'CADASTRO_MANUAL',
        'API_CANAL',
        'IMPORTACAO_ARQUIVO'
    )),

    -- ------------------------------------------------------------------
    -- Integridade do valor
    -- ------------------------------------------------------------------
    -- XOR: exatamente um dos dois. Os dois nulos e linha inutil que so
    -- serve para mascarar lacuna (a consulta a encontra, o motor nao tem
    -- o que aplicar). Os dois preenchidos e pior: nada no schema diria
    -- qual esta em uso, e dois motores leriam a mesma linha de formas
    -- diferentes. Por isso NAO existe uma coluna "modo" separada — ela
    -- poderia discordar do conteudo; este CHECK nao pode.
    CONSTRAINT ck_taxa_canal_valor_exclusivo
        CHECK ((percentual IS NOT NULL) <> (valor_fixo IS NOT NULL)),
    -- Sem limite superior (existe taxa punitiva acima de 100%); limite
    -- inferior sim: taxa negativa e sempre erro de cadastro. O SINAL e
    -- responsabilidade do motor (S5 da V010: custo positivo e saida,
    -- negativo e estorno). Um subsidio cadastrado como percentual
    -- negativo aqui viraria estorno em dobro la.
    CONSTRAINT ck_taxa_canal_percentual_nao_negativo
        CHECK (percentual IS NULL OR percentual >= 0),
    CONSTRAINT ck_taxa_canal_valor_fixo_nao_negativo
        CHECK (valor_fixo IS NULL OR valor_fixo >= 0),
    CONSTRAINT ck_taxa_canal_piso_teto_coerentes
        CHECK (valor_minimo IS NULL OR valor_maximo IS NULL
               OR valor_minimo <= valor_maximo),
    -- Piso/teto so tem sentido sobre percentual.
    CONSTRAINT ck_taxa_canal_piso_teto_exigem_percentual
        CHECK (percentual IS NOT NULL
               OR (valor_minimo IS NULL AND valor_maximo IS NULL)),
    CONSTRAINT ck_taxa_canal_moeda CHECK (moeda ~ '^[A-Z]{3}$'),

    -- ------------------------------------------------------------------
    -- Integridade dos intervalos
    -- ------------------------------------------------------------------
    CONSTRAINT ck_taxa_canal_faixa_nao_negativa
        CHECK (faixa_valor_min IS NULL OR faixa_valor_min >= 0),
    -- ESTRITO: com min = max a faixa fica VAZIA em [min, max) e a linha
    -- nunca seria selecionada. Dado morto que parece cadastrado e pior
    -- que ausencia de dado.
    CONSTRAINT ck_taxa_canal_faixa_semiaberta
        CHECK (faixa_valor_min IS NULL OR faixa_valor_max IS NULL
               OR faixa_valor_min < faixa_valor_max),
    -- `>=` e nao `>`: a igualdade e o cancelamento deliberado de uma
    -- linha cadastrada por engano (intervalo vazio, nunca selecionada,
    -- nao conflita no EXCLUDE) — ver o cabecalho. Sem DELETE para a
    -- aplicacao, este e o unico jeito de desfazer um cadastro sem apagar
    -- historico.
    CONSTRAINT ck_taxa_canal_vigencia_semiaberta
        CHECK (vigencia_fim IS NULL OR vigencia_fim >= vigencia_inicio),

    -- ------------------------------------------------------------------
    -- Integridade da procedencia
    -- ------------------------------------------------------------------
    CONSTRAINT ck_taxa_canal_categoria_nao_vazia
        CHECK (btrim(categoria_canal) <> ''),
    CONSTRAINT ck_taxa_canal_id_externo_nao_vazio
        CHECK (id_externo IS NULL OR btrim(id_externo) <> ''),
    -- "Confirmado em fonte oficial" sem dizer QUAL fonte e QUANDO e um
    -- palpite com etiqueta boa — exatamente o que a regra 5 do CLAUDE.md
    -- proibe. A etiqueta mais forte e a que exige mais prova.
    CONSTRAINT ck_taxa_canal_confirmado_exige_fonte
        CHECK (confianca <> 'CONFIRMADO_FONTE_OFICIAL'
               OR (fonte_url IS NOT NULL AND consultado_em IS NOT NULL)),
    -- Estimativa sem explicacao e indistinguivel de invencao daqui a seis
    -- meses. Espelha a regra 3 da secao 7 da especificacao (descricao
    -- obrigatoria em linha estimada).
    CONSTRAINT ck_taxa_canal_estimado_exige_observacao
        CHECK (confianca <> 'ESTIMADO'
               OR btrim(coalesce(observacao, '')) <> ''),
    -- A API de simulacao do canal devolve a taxa VIGENTE HOJE. Datar essa
    -- resposta para tras (para "cobrir" pedidos antigos) e o erro central
    -- que esta tabela existe para impedir, e ele nao deixa rastro no
    -- resultado — o numero sai plausivel e errado. Aqui ele vira violacao
    -- de constraint. A tolerancia e ate a meia-noite de Sao Paulo do dia
    -- da consulta, porque "vigente a partir de hoje" e uma data, nao um
    -- instante.
    CONSTRAINT ck_taxa_canal_api_nao_retroage
        CHECK (origem <> 'API_CANAL'
               OR (consultado_em IS NOT NULL
                   AND vigencia_inicio >=
                       date_trunc('day', consultado_em AT TIME ZONE 'America/Sao_Paulo')
                       AT TIME ZONE 'America/Sao_Paulo')),

    -- ------------------------------------------------------------------
    -- A constraint que impede o empate de nascer
    -- ------------------------------------------------------------------
    -- Duas linhas que casem com o MESMO contexto (mesmo canal, mesmo tipo
    -- de taxa, mesma categoria, mesmo tipo de anuncio, faixas que se
    -- tocam, vigencias que se tocam) tornariam a selecao ambigua. A
    -- aplicacao detecta isso com ORDER BY especificidade DESC LIMIT 2 e
    -- falha alto — mas detectar na hora do calculo e tarde: o cadastro
    -- ruim ja esta gravado e o lojista so descobre quando pede a margem.
    -- Aqui o INSERT ja e recusado.
    --
    -- `&&` sobre numrange/tstzrange trata NULL como infinito, que e
    -- exatamente a semantica de "sem limite deste lado" — por isso NULL e
    -- seguro nessas quatro colunas e proibido nas duas de curinga, que
    -- entram como `WITH =`.
    --
    -- tenant_id e a PRIMEIRA coluna e entra com `=`: alem do exigido pela
    -- 0010, isso garante que uma linha nunca colide com a de outro
    -- tenant. Constraint de exclusao roda ABAIXO do RLS; sem tenant_id
    -- aqui, uma mensagem de erro de sobreposicao revelaria a existencia
    -- de cadastro de outro cliente.
    CONSTRAINT ex_taxa_canal_sem_sobreposicao EXCLUDE USING gist (
        tenant_id       WITH =,
        canal_id        WITH =,
        tipo_taxa       WITH =,
        categoria_canal WITH =,
        tipo_anuncio    WITH =,
        numrange(faixa_valor_min, faixa_valor_max, '[)') WITH &&,
        tstzrange(vigencia_inicio, vigencia_fim, '[)')   WITH &&
    )
);

COMMENT ON TABLE  taxa_canal IS
    'Regra de cobranca de um canal, versionada por vigencia. Nivel 2 da hierarquia de taxas (decisao 0019): usada apenas quando a fonte nao informou o valor cobrado, e sempre produz custo com eh_estimativa = true. Selecao: ver o cabecalho da V013.';
COMMENT ON COLUMN taxa_canal.tenant_id IS
    'Parte da identidade do registro. Sem DEFAULT de proposito: bug de contexto deve virar erro, nao linha gravada.';
COMMENT ON COLUMN taxa_canal.canal_id IS
    'Taxa e sempre de um canal (uma CONTA), nao de um marketplace: duas contas do mesmo lojista podem ter acordos comerciais diferentes.';
COMMENT ON COLUMN taxa_canal.tipo_taxa IS
    'O que a regra precifica (COMISSAO, TARIFA_FIXA, ANTECIPACAO...). E o discriminador de selecao. Imposto NAO mora aqui - ver cabecalho da V013.';
COMMENT ON COLUMN taxa_canal.natureza_custo IS
    'Em que natureza de `custo` (V010) esta taxa vira dinheiro, e portanto em que bloco da margem cai. Relacao muitos-para-um com tipo_taxa, garantida por CHECK.';
COMMENT ON COLUMN taxa_canal.base_incidencia IS
    'Sobre que valor o percentual incide e qual valor a faixa compara (unitario, total do item, total do pedido, frete, valor a receber). E o que o motor grava em custo.base_calculo.';
COMMENT ON COLUMN taxa_canal.categoria_canal IS
    'Categoria NA FONTE (MLB1051), nao categoria nossa. NOT NULL: o curinga e o sentinela ''*'', nunca NULL - ver decisao 0019 e o cabecalho da V013.';
COMMENT ON COLUMN taxa_canal.tipo_anuncio IS
    'Valor canonico nosso (CLASSICO, PREMIUM, GRATIS, LEGADO), mapeado pelo adaptador a partir do listing_type_id. Curinga ''*'' atende canal sem tipo de anuncio.';
COMMENT ON COLUMN taxa_canal.faixa_valor_min IS
    'Limite INCLUSIVO da faixa [min, max). NULL = sem limite inferior. O que e comparado depende de base_incidencia.';
COMMENT ON COLUMN taxa_canal.faixa_valor_max IS
    'Limite EXCLUSIVO da faixa [min, max). NULL = sem limite superior. Exclusivo porque R$ 79,00 e limiar real do ML e casaria com duas faixas se fosse fechado.';
COMMENT ON COLUMN taxa_canal.vigencia_inicio IS
    'Inicio INCLUSIVO da vigencia. Gravar como instante de Sao Paulo (DATE ... AT TIME ZONE ''America/Sao_Paulo''), nunca meia-noite UTC.';
COMMENT ON COLUMN taxa_canal.vigencia_fim IS
    'Fim EXCLUSIVO. NULL = vigencia aberta (legitimo aqui, ao contrario do curinga de categoria: entra em tstzrange, onde NULL significa infinito). Igual ao inicio = cadastro cancelado.';
COMMENT ON COLUMN taxa_canal.percentual IS
    'FRACAO DECIMAL: 0.130000 = 13%. Nunca 13.0. Escala 6 (maior que a do dinheiro) porque aliquota e multiplicada pela base e porque e a mesma escala de custo.aliquota_aplicada.';
COMMENT ON COLUMN taxa_canal.valor_fixo IS
    'Tarifa em dinheiro. Exatamente um entre percentual e valor_fixo esta preenchido (CHECK) - e isso, e nao uma coluna de modo, que diz qual esta em uso.';
COMMENT ON COLUMN taxa_canal.valor_minimo IS
    'Piso do valor RESULTANTE da taxa, quando o canal o pratica. So faz sentido com percentual.';
COMMENT ON COLUMN taxa_canal.especificidade IS
    'Ordem de desempate materializada: categoria*4 + tipo_anuncio*2 + faixa*1. Duas linhas selecionaveis com o MESMO valor sao erro de cadastro e o calculo deve abortar, nunca escolher.';
COMMENT ON COLUMN taxa_canal.confianca IS
    'Qualidade da fonte (CONFIRMADO_FONTE_OFICIAL, INFORMADO_PELO_LOJISTA, ESTIMADO). E o que permite a tela dizer "informada por voce em 08/01" em vez de apresentar palpite como fato.';
COMMENT ON COLUMN taxa_canal.origem IS
    'Por onde a linha entrou (CADASTRO_MANUAL, API_CANAL, IMPORTACAO_ARQUIVO). Diferente de confianca: planilha pode trazer dado oficial e digitacao pode ser palpite.';
COMMENT ON COLUMN taxa_canal.consultado_em IS
    'Quando a fonte foi consultada. Obrigatorio em origem API_CANAL e em confianca CONFIRMADO_FONTE_OFICIAL, e limita a vigencia para tras (a API devolve a taxa de hoje).';
COMMENT ON COLUMN taxa_canal.dados_origem IS
    'Campo de extensao (decisao 0002): payload da fonte que nao coube no modelo (price, category_id, listing_type_id, sale_fee_amount).';


-- ---------------------------------------------------------------------
-- Indices
-- ---------------------------------------------------------------------
-- Caminho quente, e o unico que roda por pedido calculado: os tres
-- predicados de igualdade primeiro, a vigencia por ultimo porque e o
-- unico de intervalo. tenant_id lidera (decisao 0010).
-- NAO e parcial em `vigencia_fim IS NULL`: a consulta e sempre historica
-- (a taxa da data do fato gerador), entao linha encerrada continua sendo
-- lida com a mesma frequencia que linha aberta.
CREATE INDEX ix_taxa_canal_selecao
    ON taxa_canal (tenant_id, canal_id, tipo_taxa, vigencia_inicio DESC);

-- Idempotencia da populacao automatica: reprocessar a mesma consulta a
-- API do canal nao vira segunda linha. Use com ON CONFLICT DO NOTHING.
-- A sintese do id_externo precisa incluir a data da consulta (ver o
-- comentario da coluna) - sem isso, a leitura de amanha colide com a de
-- hoje e a taxa nova e silenciosamente descartada.
CREATE UNIQUE INDEX uq_taxa_canal_origem
    ON taxa_canal (tenant_id, canal_id, tipo_taxa, id_externo)
    WHERE id_externo IS NOT NULL;

-- A constraint ex_taxa_canal_sem_sobreposicao cria seu proprio indice
-- gist e nao precisa de indice de apoio.


-- ---------------------------------------------------------------------
-- Row Level Security — molde da decisao 0010, integral
-- ---------------------------------------------------------------------
ALTER TABLE taxa_canal ENABLE ROW LEVEL SECURITY;
-- FORCE, nao so ENABLE: sem FORCE o DONO da tabela escapa das policies, e
-- o teste de isolamento rodando como dono passaria falsamente.
ALTER TABLE taxa_canal FORCE  ROW LEVEL SECURITY;

CREATE POLICY taxa_canal_select ON taxa_canal
    FOR SELECT
    USING (tenant_id = app_current_tenant_id());

CREATE POLICY taxa_canal_insert ON taxa_canal
    FOR INSERT
    WITH CHECK (tenant_id = app_current_tenant_id());

-- USING + WITH CHECK: so com USING seria possivel alcancar a propria
-- linha e reescrever o tenant_id dela, "doando" o registro a outro tenant.
CREATE POLICY taxa_canal_update ON taxa_canal
    FOR UPDATE
    USING (tenant_id = app_current_tenant_id())
    WITH CHECK (tenant_id = app_current_tenant_id());

CREATE POLICY taxa_canal_delete ON taxa_canal
    FOR DELETE
    USING (tenant_id = app_current_tenant_id());


-- ---------------------------------------------------------------------
-- Privilegios (GRANT explicito, sempre — decisao 0010)
-- ---------------------------------------------------------------------
-- Sem DELETE (convencao 6 da V005). Apagar uma taxa apagaria a
-- explicacao de margens ja mostradas ao lojista: a memoria de calculo de
-- um custo estimado aponta para a regra que estava vigente, e sem a linha
-- a resposta a "por que voces disseram 13%?" deixa de existir.
-- Encerrar (vigencia_fim) e cancelar (vigencia_fim = vigencia_inicio)
-- cobrem todos os casos de uso e deixam rastro.
GRANT SELECT, INSERT, UPDATE ON TABLE taxa_canal TO app_aplicacao;
