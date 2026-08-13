# 0019 — Três níveis de taxa, sem quarto nível

**Data:** 12 de agosto de 2026
**Status:** aceita
**Reversibilidade:** [DIFÍCIL DE REVERTER]

Formaliza o que o `especialista-fiscal-brasil` estabeleceu em
`docs/fiscal/regras-de-margem.md` e resolve as questões de modelagem que ela
deixou explicitamente para mim.

## A hierarquia

Para obter o valor de qualquer taxa num pedido, exatamente três níveis:

1. **Valor cobrado, informado pela fonte.** Veio na fatura/payload do canal.
   `custo.eh_estimativa = false`. É fato.
2. **Tabela `taxa_canal`, versionada por vigência.** Usada quando a fonte não
   informou. `custo.eh_estimativa = true`. É estimativa, e é rotulada como tal.
3. **Lacuna declarada.** Não existe taxa cadastrada para aquela data/contexto.
   **Nenhuma linha de `custo` é criada** e a ausência é reportada na resposta.

**Não existe nível 4.** Sem "a taxa mais próxima", sem "a taxa atual quando não
há vigente", sem "a média da categoria". Um número aproximado por heurística é
indistinguível de um número correto depois que entra no banco — e a proposta do
produto é justamente ser confiável sobre lucro.

## Decisões que eu tinha que tomar

### Curinga de categoria: sentinela `'*'`, não `NULL`

A especialista apontou o problema: `EXCLUDE` não trata `NULL` como conflitante,
então uma linha com `categoria IS NULL` (curinga) não seria protegida contra
sobreposição pela constraint.

**Decido pelo sentinela `'*'`.** A coluna é `NOT NULL` e o curinga é o texto
literal `'*'`. Assim:
- o `EXCLUDE` funciona de verdade, sem buraco
- a consulta é `categoria IN (:categoria, '*')` — explícita e legível
- não existe a ambiguidade eterna de `NULL` ("não se aplica" vs "desconhecido"
  vs "todos")

Custo aceito: `'*'` é um valor mágico, e valor mágico exige comentário. Vale
mais que uma constraint que parece proteger e não protege.

### Empate de especificidade falha alto

A seleção usa `ORDER BY especificidade DESC LIMIT 2`. Se vierem duas linhas com
a **mesma** especificidade, o cálculo **aborta com erro** em vez de escolher uma.
Dois `ORDER BY` empatados dariam resultado dependente do plano de execução — ou
seja, o mesmo pedido poderia dar margens diferentes em dias diferentes. Isso
destrói a regra 3 do CLAUDE.md (rastreabilidade) de forma silenciosa.

### Intervalos semiabertos `[min, max)`

Faixa de valor e vigência são sempre semiabertas. R$ 79,00 é um limiar real do
Mercado Livre: com limites fechados dos dois lados, um pedido de exatamente
R$ 79,00 casa com duas faixas.

### Fuso `America/Sao_Paulo` na seleção por data

A data do fato gerador é convertida para o fuso de São Paulo antes de comparar
com a vigência. Sem isso, os pedidos das três primeiras horas de cada dia (em
UTC) selecionam a tabela do dia anterior.

### `V010__custo.sql` não será alterada

As 16 naturezas cobrem a fórmula. As duas ausências reais (perda de valor da
mercadoria devolvida, custo de reprocessamento) resolvem-se por **convenção de
uso** de naturezas existentes com `devolucao_id` preenchido. Migration numa
tabela central para ganhar precisão de nomenclatura é troca ruim para quem tem
20h/semana.

## Consequências

- Todo número de margem carrega um dos três rótulos da "regra do teto":
  **calculada**, **com teto** ou **indeterminada**. Nenhum número aparece sem
  rótulo
- "Com teto" é uma afirmação verdadeira e acionável ("sua margem é de no
  máximo X; a real é menor"), não um aviso vago
- A ausência de taxa cadastrada é **visível ao lojista** como pendência de
  cadastro, não como número silenciosamente errado
- O nível 1 é o que torna a Fase 2 viável sem confirmar as alíquotas do Mercado
  Livre: ler o valor efetivamente cobrado dispensa modelar a regra de cobrança
