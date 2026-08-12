# 0010 — Padrão de RLS que toda tabela deve repetir

**Data:** 12 de agosto de 2026
**Status:** aceita
**Reversibilidade:** [DIFÍCIL DE REVERTER]

Complementa a 0007. Enquanto a 0007 descreve o caminho do tenant pela
aplicação, esta descreve o que acontece no banco. Foi extraída do que o
`arquiteto-dados` estabeleceu na V001–V004 e vira **molde obrigatório** para
toda tabela da Fase 1 em diante.

## O molde

Toda tabela que guarda dado de cliente repete, sem exceção:

```sql
tenant_id uuid NOT NULL REFERENCES tenant (id)   -- sem DEFAULT

ALTER TABLE x ENABLE ROW LEVEL SECURITY;
ALTER TABLE x FORCE  ROW LEVEL SECURITY;

CREATE POLICY x_select ON x FOR SELECT USING      (tenant_id = app_current_tenant_id());
CREATE POLICY x_insert ON x FOR INSERT WITH CHECK (tenant_id = app_current_tenant_id());
CREATE POLICY x_update ON x FOR UPDATE USING      (tenant_id = app_current_tenant_id())
                                       WITH CHECK (tenant_id = app_current_tenant_id());
CREATE POLICY x_delete ON x FOR DELETE USING      (tenant_id = app_current_tenant_id());

GRANT ... ON TABLE x TO app_aplicacao;            -- explícito, sempre
CREATE INDEX ... ON x (tenant_id, ...);           -- tenant_id na primeira posição
```

Quatro pontos que parecem detalhe e não são:

1. **`FORCE`, não só `ENABLE`.** `ENABLE` sozinho não aplica policy ao dono da
   tabela. Sem `FORCE`, o teste de isolamento rodando como dono **passa
   falsamente** e qualquer job administrativo vaza sem avisar.
2. **`tenant_id` sem `DEFAULT`.** Com `DEFAULT app_current_tenant_id()`, um bug
   de contexto vira linha gravada em silêncio. Sem default, vira erro. Preferir
   o erro é a regra.
3. **`WITH CHECK` no UPDATE, além do `USING`.** Só com `USING`, um tenant
   alcança a própria linha e reescreve o `tenant_id` dela para outro tenant —
   "doando" o registro.
4. **Índice com `tenant_id` na primeira posição.** Toda query carrega o
   predicado de tenant; a coluna líder tem que ser ela.

## Decisões que o arquiteto submeteu e eu aprovei

**`consulta_auditada` é append-only** (`GRANT SELECT, INSERT` apenas).
Aprovado. A regra 3 do CLAUDE.md existe para provar um número a um cliente que
contesta. Se a aplicação pode reescrever a trilha, ela não prova nada. Correção
de auditoria, se um dia for necessária, é operação administrativa deliberada —
não caminho de código.

**Sem `ALTER DEFAULT PRIVILEGES`.** Aprovado. Custa uma linha de `GRANT` por
tabela nova e compra que **tabela nova nasça inacessível**. O contrário
significa que uma tabela que alguém esqueceu de proteger com RLS já nasce
legível pela aplicação. Fail-closed também no privilégio.

## Sobre `SET` de sessão vs `SET LOCAL`: mantida a 0007

O arquiteto alertou, com razão, que `SET` de sessão vaza no pool: a conexão
volta ao HikariCP carregando o tenant do request anterior, e um request que
esqueça de setar lê dado do tenant errado **com RLS ativo e funcionando** —
falha silenciosa e plausível, a pior categoria.

**Mantenho o `SET` de sessão da decisão 0007**, por um motivo específico: no
nosso desenho *nenhum request pode esquecer de setar*. O GUC não é setado por
quem escreve a query, é setado no `getConnection()` do `DataSourceComTenant`.
Toda conexão entregue pelo pool passa por lá e recebe ou o tenant atual ou
string vazia. Não existe caminho de código que obtenha conexão sem passar pelo
decorador.

O que me fez não trocar para `SET LOCAL` é o alerta nº 3 do próprio arquiteto:
`set_config(..., true)` fora de transação explícita é **no-op silencioso**.
Isso transformaria toda leitura em autocommit em "zero linhas" — e trocaria uma
falha silenciosa por outra.

Condição que sustenta esta escolha, e que vira regra:
**o `DataSourceComTenant` é o único `DataSource` exposto como bean `@Primary`.**
O Hikari cru fica encapsulado dentro dele e não é injetável. Se algum dia
alguém expuser o `DataSource` não decorado, esta decisão cai e passamos para
`SET LOCAL` com interceptor de transação.

Isso está coberto por teste: o teste de isolamento inclui um caso que pega uma
conexão do pool **sem tenant no contexto** e exige zero linhas.

## Armadilhas registradas (do arquiteto, valem para sempre)

- `SET app.tenant_id = ?` **não aceita bind no JDBC**. Concatenar string ali é
  injeção de SQL no ponto mais sensível do sistema. Use
  `SELECT set_config('app.tenant_id', ?, ...)`.
- GUC customizado **exige prefixo com ponto**: `app.tenant_id` funciona,
  `tenant_id` o Postgres rejeita.
- Com `FORCE`, `SELECT * FROM consulta_auditada` no psql como `postgres` sem
  GUC devolve **zero linhas**. Não é bug. Backfill em migration precisa setar o
  GUC.
- Undo roda em ordem **decrescente**: U004 → U003 → U002 → U001.
- `postgres:16` não tem pgvector. A imagem é `pgvector/pgvector:pg16`.
- Se um dia entrar PgBouncer: `transaction` mode é compatível com `SET LOCAL`,
  **não** com `SET` de sessão. Trocar para PgBouncer obriga a revisitar a 0007.
