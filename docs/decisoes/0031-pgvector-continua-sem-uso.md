# 0031 — pgvector continua sem uso, e isso não é esquecimento

**Data:** 14 de setembro de 2026
**Status:** aceita
**Reversibilidade:** [REVERSÍVEL] — a extensão já está instalada; passar a
usá-la é uma migration nova.

## Contexto

A extensão `pgvector` foi instalada na Fase 0, junto com o Postgres, e nunca
foi usada. A Fase 4 traz a camada de IA — o momento óbvio para perguntar se
enfim chegou a hora.

## Decisão

**Não. A camada de IA da Fase 4 não usa embedding nem busca vetorial.**

Três motivos, do mais forte ao mais fraco:

1. **O problema que ela resolveria não existe aqui.** Busca vetorial serve
   para achar o item relevante num corpus grande demais para enumerar. O
   catálogo de perguntas respondíveis da decisão 0030 tem menos de vinte
   itens, escritos por nós, com vocabulário conhecido. Enumerar é exato,
   instantâneo e depurável. Trocar isso por similaridade de cosseno é adicionar
   um modo de falha ("por que ele achou que isso era margem?") para resolver um
   problema de escala que não temos.
2. **Embedding também precisa de chave.** Gerar vetor exige um modelo. Adotar
   busca vetorial agora criaria dependência de credencial numa camada que a
   decisão 0029 escolheu justamente por *não* depender de credencial.
3. **Não há corpus de texto para indexar.** O candidato natural seria
   `mensagem` (conversas com cliente), mas ela está vazia: nenhum canal de
   comunicação foi ligado, e o WhatsApp depende de aprovação do Meta que pode
   levar semanas. Indexar tabela vazia é cerimônia.

## Quando reverter

Gatilhos concretos, para o próximo que abrir esta pergunta:

- O catálogo de perguntas passar de ~30 itens **e** a avaliação mostrar a
  interpretação errando por sinônimo que a heurística não cobre
- A tabela `mensagem` ganhar volume real e alguém precisar de "ache a conversa
  em que o cliente reclamou de atraso"
- Aparecer busca semântica em catálogo de produto (achar variação por
  descrição livre) — hoje o casamento é por SKU e é exato de propósito

Nenhum desses é o caso em 14/09/2026.

## Alternativas consideradas

- **Indexar o catálogo de intenções por embedding "já que a extensão está
  lá".** Descartada: a extensão estar instalada é custo afundado, não motivo.
  Usar tecnologia porque ela está disponível é como o projeto acumula peso.
- **Guardar embedding das perguntas feitas, para agrupar depois.** Ideia boa e
  fora de hora. O que realmente ajuda a decidir quais perguntas entram no
  catálogo é o **texto cru** das perguntas recusadas, que a Fase 4 já vai
  guardar. Agrupar isso é análise manual de dezenas de linhas, não busca
  vetorial. Se um dia forem milhares, revisita-se.

## Consequências

- A extensão continua instalada e sem uso. Custo: uma linha de migration e
  nenhum efeito em runtime. Manter é mais barato que remover e reinstalar.
- A pergunta "por que pgvector nunca foi usado?" tem resposta escrita, o que
  era o objetivo — a próxima sessão não gasta tempo redescobrindo isto.
