# 0017 — Reconciliação entre fontes é passo explícito, nunca implícito

**Data:** 12 de agosto de 2026
**Status:** aceita
**Reversibilidade:** [REVERSÍVEL]

## Contexto

O `engenheiro-integracao` encontrou o risco estrutural mais importante da Fase 1:

O mesmo pedido de venda existe **nas duas fontes**. Ele nasce no Mercado Livre e
é espelhado no Bling pelo próprio integrador do lojista. Se os dois adaptadores
ingerirem, o banco ganha **dois `pedido`** para uma venda só — e o faturamento
aparece dobrado. Num produto cuja proposta é "saber o lucro real", contar a
mesma venda duas vezes é a falha mais grave possível.

A chave de idempotência de `evento_ingerido` é
`(tenant_id, canal_id, tipo_evento, id_externo)`. Ela impede reprocessar **o
mesmo evento da mesma fonte** — corretamente. Ela **não** impede, e não deveria
impedir, que a mesma venda chegue por dois canais diferentes.

O candidato natural a chave de ligação seria o campo `numeroLoja` do Bling
(onde o integrador costuma gravar o número do pedido de origem). Mas o agente
foi explícito: isso é **hipótese, não fato** — não pôde ser confirmado na
documentação oficial, que respondeu 403/404.

## Decisão

**A Fase 1 não deduplica entre canais.** Cada adaptador ingere o que a sua
fonte diz, e cada `pedido` carrega o `canal_id` de onde veio.

Casar "este pedido do ML é o mesmo pedido do Bling" é uma etapa **separada e
explícita** de reconciliação, que só será construída quando existir uma chave
**confirmada contra dado real** — não contra uma suposição.

Enquanto essa etapa não existir:
- Quem responde "quanto vendi" deve consultar **um canal de cada vez**, ou o
  conjunto de canais que o lojista declarar como não sobrepostos
- Qualquer soma que cruze canais de origens sobrepostas é potencialmente dupla,
  e o sistema **não pode apresentá-la como fato**

## Alternativas consideradas

- **Deduplicar automaticamente por `numeroLoja`.** Descartada, e é a alternativa
  tentadora. Casar registros por uma chave que ninguém confirmou significa que,
  quando ela vier vazia ou com outro conteúdo, o sistema ou funde dois pedidos
  diferentes (perde uma venda) ou não funde os iguais (dobra). As duas falhas
  são silenciosas e viram número errado num relatório. Isso viola a regra 5 do
  CLAUDE.md: seria estimativa apresentada como fato.
- **Deixar só o marketplace ingerir pedido, e o ERP só custo.** Foi a segunda
  colocada e continua sendo o desenho provável no futuro. Descartada agora
  porque nem todo lojista vende só em marketplace — quem tem loja própria tem o
  pedido só no ERP. A regra "ERP não cria pedido" quebraria esse caso.
- **Deixar o lojista escolher a fonte-mestra de pedido por canal.** É
  provavelmente a resposta certa, mas depende de decisão de produto e de
  interface que ainda não existe. Fica registrado como caminho preferido para
  quando a Fase 3 tiver onde configurar isso.

## Consequências

- **Nada é silenciosamente errado**: o dado duplicado, se existir, é visível e
  atribuível a canais distintos, em vez de fundido por uma regra frágil
- O pipeline de ingestão fica mais simples nesta fase
- **Bloqueia parcialmente a tarefa 16** ("quanto sobrou no período X"): a
  resposta precisa declarar o escopo de canal, ou recusar-se a somar canais
  sobrepostos. Anotado em `docs/ESTADO.md`
- A primeira conta de Mercado Livre real com Bling ligado resolve a dúvida em
  minutos: basta olhar um `numeroLoja` de verdade. É a primeira coisa a
  verificar quando a credencial chegar
