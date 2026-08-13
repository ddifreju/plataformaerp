# 0026 — A garantia do dinheiro-como-texto mora no backend

**Data:** 12 de agosto de 2026
**Status:** aceita
**Reversibilidade:** [REVERSÍVEL]

## Contexto

A decisão 0022 promete: "valor monetário chega como string decimal; o frontend
formata, nunca recalcula".

Ao construir as telas, o `engenheiro-frontend` descobriu que essa promessa era
**falsa no fio**: o Jackson serializava `BigDecimal` como número JSON
(`{"valor": 199.90}`), e o `JSON.parse` do JavaScript converte todo literal
numérico para `double` de 64 bits **antes** de qualquer código do cliente rodar
— inclusive antes de um `reviver`. A precisão guardada em `NUMERIC(18,4)` era
destruída na desserialização, e não havia nada que o frontend pudesse fazer
depois.

Ele resolveu do lado dele, escrevendo um parser JSON recursivo à mão
(`parseJsonPreservandoNumeros`, ~190 linhas) que preserva todo literal numérico
como texto.

Isso funcionou, mas deixou a garantia no lugar errado.

## Decisão

**A garantia vai para o backend.** `ConfiguracaoJackson` registra um
`JsonSerializer<BigDecimal>` global que emite `toPlainString()`. Todo valor
monetário sai da API como string, sempre, para qualquer cliente.

**O parser à mão do frontend foi removido**, e o cliente voltou a usar
`response.json()`.

## Por que não manter os dois

Defesa em profundidade é boa quando as camadas protegem contra falhas
diferentes. Aqui não era o caso: com o serializador global, o parser não
protegia mais nada de real. O que sobrava passando por `number` eram contagens
inteiras pequenas (`quantidadePedidos`, `tentativas`), sem risco algum de perda
de precisão.

Pesou contra manter:

- **O comentário no topo do parser afirmava que o backend não tinha
  serializador** — já não era verdade. Documentação que mente é pior que
  ausente: em seis meses, alguém leria aquilo e concluiria "ainda preciso disso
  para dinheiro".
- A auditoria de segurança apontou que o parser **não tem guarda de
  profundidade** (payload aninhado estoura a pilha) e **aceita literais
  malformados** (`1e` vira string malformada em vez de erro). Não é explorável
  hoje, porque a única fonte é o próprio backend — mas é superfície mantida à
  toa.
- São 190 linhas de código recursivo de baixo nível, sem teste, para uma pessoa
  sozinha manter. "Simplicidade acima de otimização" resolve isso sem empate.

**O ponto mais importante:** o contrato precisa dizer a verdade sozinho. O
próximo cliente da API — um aplicativo, uma planilha, a integração do próprio
lojista — não vai ter o parser do nosso frontend. Se a garantia morasse só no
cliente, cada novo consumidor herdaria o bug silencioso.

## Alternativas consideradas

- **Manter o parser e corrigir o comentário.** Descartada: manteria o custo de
  manutenção e a superfície de robustez para proteger o que já está protegido
  na origem.
- **`@JsonFormat(shape = STRING)` campo a campo.** Descartada: depende de
  alguém lembrar de anotar cada `BigDecimal` novo. Serializador global é
  sempre-ligado, e sempre-ligado é o padrão que este projeto escolhe em toda
  garantia que importa (mesma lógica do `@TenantId` na decisão 0007).

## Consequências

- O frontend ficou mais simples e sem o arquivo mais arriscado de manter
- Contagens inteiras continuam chegando como número JSON, e tudo bem
- **Não reintroduza o parser** sem antes verificar se `ConfiguracaoJackson`
  ainda está no lugar. Há um comentário em `cliente.ts` apontando para cá
- `toPlainString()` e não `toString()`: este último pode emitir notação
  científica (`1E+2`) para certos expoentes, que nenhum cliente espera ler
