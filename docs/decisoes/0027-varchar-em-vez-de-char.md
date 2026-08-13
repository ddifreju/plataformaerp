# 0027 — `varchar(n)` em vez de `char(n)`, sempre

**Data:** 13 de agosto de 2026
**Status:** aceita
**Reversibilidade:** [REVERSÍVEL]

Primeira decisão tomada com o sistema rodando de verdade, em vez de por leitura.

## Contexto

O `docs/ESTADO.md` listava, desde a Fase 1, como **maior risco** do projeto:

> `ddl-auto: validate` com `char(n)` — se o Hibernate tratar `bpchar` e
> `varchar` como incompatíveis, a aplicação **não sobe**.

Na primeira execução da suíte contra um Postgres real, foi exatamente o que
aconteceu. 37 dos 150 testes morriam com:

```
Schema-validation: wrong column type encountered in column [moeda]
in table [custo]; found [bpchar (Types#CHAR)],
but expecting [varchar(3) (Types#VARCHAR)]
```

Nove colunas em sete tabelas: `ncm`, `cest`, `moeda` (×5), `uf_entrega`,
`hash_payload`.

## Decisão

**Toda coluna de texto com tamanho limitado é `varchar(n)`.** `char(n)` não é
usado em lugar nenhum do schema.

A conversão foi feita pela migration **V015**, com `U015` pareado.

## Por que corrigir o banco e não o mapeamento

Daria para forçar o Java a aceitar `char`, com `columnDefinition` ou
`@JdbcTypeCode(Types.CHAR)` nos nove campos. Seria pior, e não por gosto:

1. **`char(n)` faz padding com espaços.** `'BR'` gravado em `char(3)` volta
   `'BR '`. Isso vaza para comparação, concatenação e para o JSON da API. Um dia
   alguém compara `moeda === "BRL"` no frontend, não casa, e o bug leva meia
   manhã para achar.
2. **Não há nada sendo trocado.** A documentação do próprio Postgres diz que
   `char(n)` não tem vantagem de desempenho sobre `varchar(n)` — e costuma ser
   mais lento.
3. **Corrigir no mapeamento espalharia a exceção** por nove campos em sete
   entidades, e cada campo novo de tamanho fixo repetiria a pegadinha. No banco,
   resolve na origem, uma vez.

## Por que uma migration nova e não editar as anteriores

As 14 migrations já haviam sido aplicadas num Postgres real. **Migration
aplicada é imutável** — editar quebra o checksum do Flyway e obriga a recriar o
banco. Corrige-se com outra na frente.

Isso vira regra: a partir do momento em que uma migration roda fora da máquina
de quem a escreveu, ela não se edita mais.

## Registro junto: Testcontainers 1.19.8 → 1.21.4

Mesma execução revelou que o `docker-java` 3.3.6 (embutido no Testcontainers
que o Spring Boot 3.3.13 gerencia) não conversa com o **Docker Engine 29**: a
estratégia de named pipe no Windows falha com `BadRequestException (Status 400)`
no `GET /info`, embora o `docker` CLI use o mesmo pipe sem problema.

Fixado pelo **BOM** do Testcontainers, não por propriedade solta: o BOM alinha
`testcontainers`, `junit-jupiter`, `postgresql` **e** a versão do `docker-java`
de uma vez. Mexer só na propriedade deixaria o `docker-java` velho — que é
justamente o problema.

Mantido na linha **1.x** de propósito: a 2.x é major nova, e trocar a API de
teste no mesmo passo em que se conserta a conexão com o Docker misturaria duas
causas de falha.

## Consequências

- A suíte passa inteira: **150 testes, 0 falhas, 0 erros**, contra Postgres real
- O banco de desenvolvimento já existente precisa de `make migrate` para receber
  a V015
- Toda tabela nova daqui em diante usa `varchar(n)`. Se alguém escrever
  `char(n)`, a aplicação **não sobe** — o `ddl-auto: validate` avisa na hora, o
  que é o modo de falha desejável
