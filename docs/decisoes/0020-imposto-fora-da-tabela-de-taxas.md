# 0020 — Imposto não mora em `taxa_canal`

**Data:** 12 de agosto de 2026
**Status:** aceita
**Reversibilidade:** [REVERSÍVEL]

## Contexto

O documento fiscal (§5.1) sugeriu tratar o imposto do Simples Nacional "no mesmo
mecanismo" das taxas de canal, com `tipo_taxa = 'IMPOSTO_SIMPLES_ANEXO_I'`.

O `arquiteto-dados` implementou a V013 e discordou, com três razões de schema.
Avaliei e **concordo com ele**. O *mecanismo* (versionamento por vigência,
seleção pela data do fato gerador, hierarquia de 3 níveis) é de fato o mesmo — a
*tabela* não pode ser.

## Decisão

`taxa_canal` guarda **apenas taxas cobradas por um canal**. O imposto terá tabela
própria quando for implementado.

As três razões, que são objetivas:

1. **O XOR quebra.** Uma faixa do Anexo I precisa de alíquota nominal **e**
   parcela a deduzir na mesma linha. A `taxa_canal` tem
   `CHECK ((percentual IS NOT NULL) <> (valor_fixo IS NOT NULL))` — exatamente
   um dos dois. A faixa de imposto é inexprimível ali sem afrouxar o CHECK.
2. **`canal_id` é `NOT NULL`.** Imposto não é do canal, é do tenant e do regime
   tributário. Seria preenchido com um canal arbitrário ou com outro sentinela.
3. **A chave de seleção é outra.** Taxa seleciona por
   (canal, categoria, tipo de anúncio, faixa de valor). Imposto seleciona por
   (regime, competência, faixa de RBT12). São dimensões diferentes.

Afrouxar as três coisas na tabela mais crítica do cálculo, para economizar uma
tabela, é troca ruim.

## Alternativas consideradas

- **Seguir a sugestão do documento fiscal ao pé da letra.** Descartada pelas
  três razões acima. O documento acertou no mecanismo e generalizou demais na
  tabela — é o tipo de detalhe que só aparece ao desenhar o schema.
- **Tabela genérica de "regras versionadas"** que sirva para taxa e imposto.
  Descartada: seria uma tabela com metade das colunas nulas em cada uso, e um
  CHECK gigante para dizer quais valem quando. Duas tabelas claras valem mais
  que uma abstrata.

## Consequências

- A tarefa 14/15 calcula imposto a partir de **configuração de regime do
  tenant**, não da `taxa_canal`. Enquanto o regime não estiver configurado, o
  imposto é **lacuna declarada** (decisão 0019, nível 3) — e o documento fiscal
  já registra que isso **superestima a margem**, então o número sai rotulado
  como `COM_TETO`
- A tabela de imposto por regime e competência fica como tarefa explícita,
  registrada em `docs/PENDENCIAS.md` na parte que depende do contador
- `btree_gist`, criada na V013, será reaproveitada por ela

## Extensão da decisão 0019 que registro junto

O arquiteto aplicou o sentinela `'*'` também em `tipo_anuncio`, não só em
`categoria_canal`. Está certo e é a mesma decisão: o buraco do `NULL` no
`EXCLUDE` é idêntico nas duas colunas, e proteger só uma deixaria metade da
constraint fingindo proteger.
