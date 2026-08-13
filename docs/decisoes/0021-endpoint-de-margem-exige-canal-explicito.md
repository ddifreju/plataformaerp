# 0021 — O endpoint de margem exige um canal, nunca uma lista

**Data:** 12 de agosto de 2026
**Status:** aceita
**Reversibilidade:** [REVERSÍVEL]

Materializa a decisão 0017 no contrato da API. Vale a pena registrar em
separado porque define o formato da resposta que a Fase 3 vai consumir.

## Contexto

A tarefa 16 pede um endpoint que responda "quanto sobrou no período X".

A decisão 0017 estabeleceu que **não há reconciliação entre fontes**: o mesmo
pedido existe no Mercado Livre e no Bling, e a chave para casá-los não foi
confirmada. Somar os dois canais conta a mesma venda duas vezes.

O endpoint é justamente onde esse risco vira número na tela.

## Decisão

`GET /api/margem/periodo` recebe **`canalId` obrigatório, um único valor**.
Não aceita lista, não aceita omissão com significado de "todos".

Quem quiser comparar canais faz uma chamada por canal e compara. O backend
**nunca** soma canais por conta própria.

A resposta declara o escopo aplicado (`escopoCanal`), para que o número nunca
apareça sem dizer a que se refere.

## Alternativas consideradas

- **`canalId` opcional, omissão = todos os canais.** Descartada, e é a
  alternativa que parece mais amigável. O problema é o modo de falha: o usuário
  que esquece o parâmetro recebe um número **maior**, plausível, sem nenhum
  sinal de erro. Faturamento inflado é exatamente o que os integradores atuais
  fazem e o que este produto existe para corrigir. Um endpoint cujo erro mais
  provável é inflar o resultado está mal desenhado.
- **Aceitar lista de canais e somar, com aviso no corpo.** Descartada: o aviso é
  ignorado, o número é copiado para um slide, e a soma errada vira decisão de
  negócio. Se somar é inseguro, a API não deve oferecer a soma.
- **Somar apenas canais marcados como "não sobrepostos".** É provavelmente o
  desenho final, mas depende de o lojista declarar a topologia dos canais dele —
  o que exige interface e decisão de produto que ainda não existem (0017).
  Quando existir, este endpoint ganha o modo agregado sem quebrar o atual.

## Consequências

- A Fase 3 precisa de um seletor de canal na tela de resultado. Não é limitação
  de interface: é a informação sendo dita com honestidade
- Um lojista de canal único (loja própria, ou só ML) não sente diferença
  nenhuma — que é o caso mais comum no início
- Quando a reconciliação existir, a evolução é aditiva: um parâmetro novo de
  agregação, com o comportamento atual como padrão
