# 0006 — Flyway com SQL puro e undo pareado

**Data:** 12 de agosto de 2026
**Status:** aceita
**Reversibilidade:** [REVERSÍVEL]

## Contexto

O CLAUDE.md exige: "migrations sempre reversíveis". Precisa de ferramenta de
versionamento de schema e de uma forma real de desfazer.

## Decisão

**Flyway**, com migrations em **SQL puro** (não Java), em
`backend/src/main/resources/db/migration/`, padrão `V001__descricao.sql`.

Para reversibilidade: **cada migration tem um script de undo pareado** em
`backend/src/main/resources/db/undo/`, padrão `U001__descricao.sql`, que desfaz
exatamente o que a `V` correspondente fez. Aplicado manualmente por
`make migrate-undo`.

Regra: **um PR que adiciona `V0NN` sem `U0NN` está incompleto.**

## Alternativas consideradas

- **Liquibase.** Gera rollback automático e é agnóstico a banco. Descartada:
  o changelog em XML/YAML é uma segunda linguagem para descrever DDL que já
  sabemos escrever em SQL. Além disso o rollback automático é confiável só
  para DDL simples — exatamente o caso em que escrever o undo à mão é trivial.
  E não vamos trocar de banco: a decisão 0001 fixou Postgres, e RLS e pgvector
  são específicos dele. A portabilidade que o Liquibase vende não tem valor
  aqui.
- **Flyway Teams (comando `undo` nativo).** Descartada: é pago.
- **Migrations sem undo, só "roll forward".** É a prática moderna dominante e
  foi seriamente considerada. Descartada porque o CLAUDE.md é explícito, e
  porque em fase inicial com um banco só, poder voltar rápido em dev vale mais
  que a pureza do roll-forward. Em produção, quando existir, roll-forward
  continua sendo o caminho preferido — o undo é rede de segurança de dev.
- **Hibernate `ddl-auto`.** Descartada sem discussão: schema gerado por ORM em
  banco com RLS é receita para vazamento silencioso. `ddl-auto` fica em
  `validate`.

## Consequências

- Disciplina manual: escrever o undo é responsabilidade de quem escreve a
  migration
- Undo de migration que destrói dado não recupera o dado — recupera só o
  schema. Isso é limite inerente e está documentado no `Makefile`
- SQL puro deixa RLS, policies e índices explícitos e revisáveis
