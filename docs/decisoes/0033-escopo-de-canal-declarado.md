# 0033 — Escopo de canal é declarado pela lojista, e a soma falha fechada

**Data:** 14 de setembro de 2026
**Status:** aceita
**Reversibilidade:** [DIFÍCIL DE REVERTER] — cria coluna com semântica de
negócio que a lojista preenche à mão. Mudar o significado depois invalida o
que ela já declarou, e não há como recuperar a intenção original.

## Contexto

A decisão 0017 proibiu deduplicar pedido entre canais, porque o mesmo pedido do
Mercado Livre é espelhado no Bling pelo integrador do próprio lojista e casar
os dois exigiria uma chave que ninguém confirmou contra dado real. A decisão
0021 tirou a consequência: o endpoint de margem exige `canalId`, e o backend
nunca soma canais potencialmente sobrepostos.

Isso está correto e continua valendo. Mas custa caro: **não dá para perguntar
"quanto sobrou no mês"**, só "quanto sobrou no mês no ML Clássico". Para a
camada de IA (decisão 0030) isso é pior ainda, porque a pergunta que a lojista
faz naturalmente é justamente a que não nomeia canal.

A 0017 já tinha registrado a saída e a chamou de "provavelmente a resposta
certa": deixar o lojista declarar a fonte de cada canal. A decisão 0029
escolheu construir isso em vez do casamento de pares.

## Decisão

Cada canal ganha um **escopo declarado**, preenchido pela lojista:

- **`NAO_DECLARADO`** — o padrão, e o estado de todo canal que existe hoje
- **`FONTE_PRIMARIA`** — os pedidos deste canal nascem aqui
- **`ESPELHO`** — os pedidos deste canal são cópia dos de outro canal, que
  é apontado por `canal.espelha_canal_id`

Somar entre canais passa a ser permitido **apenas** sobre um conjunto que o
sistema consegue provar disjunto: todos os canais envolvidos declarados
`FONTE_PRIMARIA`, nenhum deles espelhado por outro do conjunto.

### Falha fechada, sempre

`NAO_DECLARADO` **bloqueia** a soma. Não "assume primária", não "soma e
avisa". Se qualquer canal do escopo pedido estiver sem declaração, a resposta
é uma recusa explícita dizendo quais canais faltam declarar e o que fazer.

É a mesma postura da lacuna declarada da decisão 0019 e do
`app_current_tenant_id()` fail-closed: **a ausência de informação nunca vira
um número**. O caminho fácil aqui seria tratar não-declarado como primária,
porque hoje todo canal está nesse estado e a funcionalidade "já funcionaria".
Funcionaria contando venda duas vezes na conta de quem tem Bling ligado — o
erro mais grave que este produto pode cometer.

### Declarar é da lojista, não nossa

Nenhuma heurística preenche esse campo. Nem "o Bling costuma ser espelho", nem
"o canal com menos pedidos é o espelho". A declaração é conhecimento que só
quem montou a operação tem, e é exatamente por não termos esse conhecimento
que a 0017 barrou o casamento automático.

O que o sistema pode fazer — e faz — é **perguntar de forma informada**:
mostrar os canais, quantos pedidos cada um tem, e explicar em uma frase o que
está em jogo. Sugerir com números na tela é diferente de decidir sozinho no
banco.

### `ESPELHO` não apaga nem funde nada

Canal marcado como espelho continua ingerindo, continua com os pedidos dele, e
continua consultável individualmente. A declaração afeta **só** a soma entre
canais. Nenhum dado é fundido, nenhum pedido é escondido — o que a 0017 chamou
de "nada é silenciosamente errado" continua verdadeiro.

Isso também é o que torna a decisão barata de errar: se a lojista marcar
espelho errado, ela corrige a declaração e o número seguinte já sai certo. Não
há migração de dado, não há merge a desfazer.

## Alternativas consideradas

- **Tratar `NAO_DECLARADO` como `FONTE_PRIMARIA`.** Descartada acima. É a
  alternativa tentadora porque entrega a funcionalidade sem a lojista fazer
  nada — e entrega faturamento dobrado para quem mais precisa do produto.
- **Um booleano `somavel` por canal.** Mais simples e insuficiente: não diz
  *de quem* o canal é espelho, então não dá para explicar à lojista por que a
  soma foi recusada, nem detectar que ela marcou dois canais como espelho um
  do outro.
- **Grupos de canal nomeados** ("minha operação de marketplace"), somando
  dentro do grupo. Mais flexível e mais conceito para manter. Descartada por
  ora: resolve um problema de organização que ninguém pediu, e a relação
  espelho→primária já expressa o que precisa ser expresso. Se aparecer
  necessidade real de recortes múltiplos, é evolução natural daqui.
- **Inferir espelho pela sobreposição de valor e data dos pedidos.** É
  casamento de pares com outro nome, com todos os problemas que a 0029
  descreveu, e ainda com falso negativo silencioso.

## Consequências

- A decisão 0021 continua íntegra: o endpoint por canal segue exigindo
  `canalId`. A soma entre canais é uma operação **nova e separada**, com
  pré-condição verificada, não um relaxamento da regra antiga
- A camada de IA passa a responder "quanto sobrou no mês" quando — e só
  quando — a declaração permitir. Enquanto não permitir, ela pede a
  declaração, que é uma recusa útil em vez de uma recusa seca
- O seed de demonstração precisa declarar o escopo dos três canais, senão a
  demo cai na recusa. Isso é bom: exercita o caminho feliz e o caminho de
  bloqueio no mesmo ambiente
- Quando a credencial do Mercado Livre chegar e o casamento por `numeroLoja`
  for confirmado contra dado real, ele entra **por cima** disto, não no lugar:
  a declaração continua sendo a verdade sobre a intenção da lojista, e o
  casamento vira o detalhe de quais pedidos são os mesmos
