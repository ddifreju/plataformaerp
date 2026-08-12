# 0015 — Chave estrangeira composta carregando `tenant_id`

**Data:** 12 de agosto de 2026
**Status:** aceita
**Reversibilidade:** [DIFÍCIL DE REVERTER]

Decisão tomada pelo `arquiteto-dados` durante a Fase 1 e promovida a padrão do
projeto por mim, porque vale para tudo que vier depois.

## Contexto

O molde da decisão 0010 protege cada tabela isoladamente: RLS impede ver linha
de outro tenant. Mas ele **não** impede que uma linha do tenant A aponte para
uma linha do tenant B por chave estrangeira.

Exemplo do estrago: um `pedido` do tenant A com `canal_id` de um canal do
tenant B. Cada tabela continua "isolada", as policies continuam corretas, e
mesmo assim o grafo de dados está corrompido entre clientes. Um `JOIN` a partir
daí produz número errado, e a Fase 2 calcula margem em cima disso.

## Decisão

Toda FK entre tabelas multi-tenant é **composta, incluindo `tenant_id`**:

```sql
tenant_id uuid NOT NULL,
canal_id  uuid NOT NULL,
CONSTRAINT uq_canal_tenant_id UNIQUE (tenant_id, id),   -- na tabela referenciada
CONSTRAINT fk_pedido_canal
    FOREIGN KEY (tenant_id, canal_id) REFERENCES canal (tenant_id, id)
```

Isso exige que cada tabela referenciada declare `UNIQUE (tenant_id, id)` além da
PK em `id` — chave alternativa que existe só para ser alvo dessas FKs.

## Por que isto importa

O banco passa a **recusar fisicamente** uma referência cruzada entre tenants.
Não é convenção, não é teste, não é revisão de código: é a definição do schema.
Nenhum bug de aplicação, nenhum `INSERT` manual e nenhuma migração de dados
consegue criar o vínculo inválido.

É a mesma filosofia da decisão 0007 aplicada ao relacionamento em vez de à
consulta: a garantia não pode depender de alguém lembrar.

## Alternativas consideradas

- **FK simples (`REFERENCES canal (id)`) + confiar no RLS.** Descartada: RLS
  filtra leitura, não valida integridade referencial. Uma escrita com o
  `canal_id` errado passa, e o erro só aparece muito depois, como número
  errado num relatório — a pior forma de descobrir.
- **Validar na aplicação.** Descartada: é exatamente a "disciplina humana" que
  a decisão 0007 recusa como garantia.
- **Trigger de validação.** Descartada: faz o mesmo que a FK composta, só que
  mais devagar, mais difícil de ler e sem otimização do planejador.

## Consequências

- Uma coluna `UNIQUE (tenant_id, id)` a mais por tabela referenciada, e um
  índice junto. Custo pequeno e previsível
- As entidades JPA precisam mapear a FK composta. Em muitos casos o mapeamento
  mais simples é guardar o `UUID` do relacionado (`canalId`) em vez de uma
  associação `@ManyToOne`, com `tenant_id` implícito pelo `@TenantId`.
  **Preferir isso**: associação com chave composta em JPA é fonte conhecida de
  complexidade, e a legibilidade vale mais que a navegação de objeto
- Toda tabela nova da Fase 2 em diante repete o padrão
