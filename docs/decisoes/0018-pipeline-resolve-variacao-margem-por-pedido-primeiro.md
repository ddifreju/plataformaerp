# 0018 — O pipeline resolve a variação; a margem começa por pedido

**Data:** 12 de agosto de 2026
**Status:** aceita
**Reversibilidade:** [REVERSÍVEL]

## Contexto

A revisão da Fase 1 apontou que `item_pedido.variacao_id` fica **sempre nulo**.
Os adaptadores não conseguem casar o item vendido com a variação do catálogo
porque isso exigiria consulta ao banco — e a pureza deles (nenhum acesso a
Postgres) é justamente o que permite testá-los com fixture, sem container.

Sem `variacao_id` preenchido, **margem por SKU não fecha**: não há como ligar o
que foi vendido ao custo da mercadoria cadastrado no catálogo.

São duas perguntas, e elas têm respostas diferentes.

## Decisão A — quem resolve a variação

**O pipeline resolve, não o adaptador.**

O adaptador continua puro e expõe o SKU / código externo do item. O
`ServicoIngestao`, que já tem acesso ao banco, consulta a variação por SKU
dentro do tenant e preenche `variacao_id`.

Quando não encontrar, `variacao_id` fica **nulo e é declarado como campo
ausente** no `ResultadoIngestao`. Nunca aproximado por nome de produto, nunca
criado na hora. Regra 5 do CLAUDE.md.

Isso preserva a testabilidade dos adaptadores e coloca a consulta onde já existe
transação e contexto de tenant.

## Decisão B — o que a Fase 2 calcula primeiro

**Margem por PEDIDO primeiro. Margem por SKU depois.**

Motivos, em ordem de peso:

1. **Margem por pedido não depende de `variacao_id`.** Ela usa os totais do
   pedido e as linhas de `custo` ligadas a ele. Ou seja: funciona **hoje**,
   inclusive para pedidos já ingeridos com variação nula.
2. **Margem por SKU depende de dois dados que ainda não existem**: o catálogo
   ingerido (para haver variação a casar) e o custo da mercadoria cadastrado
   pelo lojista — que é, por definição, dado que só ele tem.
3. A pergunta que a tarefa 16 faz é **"quanto sobrou no período X"**. Isso é
   agregação de pedidos. Margem por SKU responde outra pergunta ("qual produto
   dá prejuízo"), igualmente valiosa, mas que vem depois.

Consequência prática: a margem por pedido é entregue completa nesta fase; a
margem por SKU fica destravada pela Decisão A, mas só produz número quando o
catálogo e os custos estiverem cadastrados.

## Alternativas consideradas

- **Adaptador consulta o banco e resolve a variação.** Descartada: mataria o
  teste com fixture puro, que é o que torna os adaptadores baratos de manter.
- **Criar a variação automaticamente quando o SKU não existe.** Descartada, e
  era tentadora. Criaria catálogo fantasma a partir de dado de pedido, com
  custo de mercadoria vazio — e um produto com custo vazio entra no cálculo de
  margem como se custasse zero, produzindo **lucro superestimado apresentado
  como fato**. É exatamente a falha que o produto existe para não cometer.
- **Casar por nome do produto quando o SKU não bate.** Descartada: casamento
  aproximado erra, e erra em silêncio. Melhor não ter o número do que ter o
  número errado.
- **Adiar a Decisão A para a Fase 3.** Descartada: resolver no pipeline é
  barato agora e destrava a margem por SKU sem reescrever nada depois.

## Consequências

- Pedidos ingeridos **antes** desta mudança continuam com `variacao_id` nulo.
  Reprocessar o payload (que agora atualiza, ver dívida 1 da Fase 1) preenche.
- A resposta de margem precisa **declarar** quantos itens não puderam ser
  ligados a uma variação — senão o usuário não sabe o quanto confiar no número
- A margem por SKU vira tarefa explícita da Fase 3, não um efeito colateral
  esperado desta fase
