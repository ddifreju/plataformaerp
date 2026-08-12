# 0007 — Propagação de tenant: filtro, contexto, GUC e RLS

**Data:** 12 de agosto de 2026
**Status:** aceita
**Reversibilidade:** [DIFÍCIL DE REVERTER]

Esta é a decisão mais estruturante da Fase 0. Tudo que for construído depois
assume este mecanismo. Mudar isso depois significa revisitar toda query.

## Contexto

Regra 1 do CLAUDE.md: "Tenant é identidade, não filtro. Resolvido no gateway,
propagado por contexto, predicado obrigatório em toda query, RLS no banco como
segunda camada."

Precisa decidir *como* cada um desses quatro elos funciona na prática.

## Decisão

Quatro camadas, nesta ordem:

### 1. Resolução — `TenantFilter` (OncePerRequestFilter, ordem mais alta)
Lê o tenant da requisição, valida contra a tabela `tenant`, e rejeita com
**400** se ausente/malformado e **403** se desconhecido ou inativo.
Rotas públicas (`/actuator/health`, `/actuator/info`) são a única isenção,
via lista explícita — *allowlist*, nunca *denylist*.

Na Fase 0 o tenant vem do header `X-Tenant-Id`. **Isto é provisório**: quando
a autenticação existir (tarefa 17), o tenant passa a vir do token assinado e o
header deixa de ser aceito. Está marcado com `// PROVISÓRIO` no código.
Aceitar tenant por header sem autenticação é seguro apenas enquanto não existe
autenticação nenhuma — no minuto em que existir sessão, header vira vetor de
escalação horizontal.

### 2. Propagação — `ContextoTenant` (ThreadLocal)
`ThreadLocal` simples, com `limpar()` obrigatório em `finally` no filtro.
Ler o tenant quando não há um setado **lança exceção** — nunca retorna null,
nunca retorna um default.

### 3. Predicado na aplicação — Hibernate `@TenantId`
Toda entidade multi-tenant carrega um campo anotado com `@TenantId`. O
Hibernate 6 passa a adicionar o predicado de tenant automaticamente em toda
consulta e a preencher o campo no insert, via `CurrentTenantIdentifierResolver`
ligado ao `ContextoTenant`.

Isso é deliberado: **o predicado não pode depender do programador lembrar de
escrevê-lo**. Um `WHERE tenant_id = ?` escrito à mão em 200 repositórios é uma
questão de tempo até alguém esquecer em um.

### 4. Segunda camada — RLS no Postgres via GUC `app.tenant_id`
Um `DataSource` decorador (`DataSourceComTenant`) executa
`SET app.tenant_id = '<uuid>'` em **toda** conexão entregue pelo pool.

Ponto crítico: quando **não há** tenant no contexto, ele seta o GUC como
**string vazia**, e a função `app_current_tenant_id()` retorna NULL, e as
policies não liberam nenhuma linha. **Falha fechada.** O modo de falha de um
sistema multi-tenant nunca pode ser "mostra tudo".

## Alternativas consideradas

- **Filtro manual em cada repositório** (`findByTenantIdAnd...`). Descartada:
  depende de disciplina humana em 100% dos casos, e o custo do erro é
  vazamento entre clientes. É a única falha do produto que pode matar a
  empresa.
- **Schema por tenant** (`SCHEMA` separado). Isolamento mais forte e ainda
  assim descartada: migrations passam a rodar N vezes, o número de schemas
  vira problema operacional na casa das centenas, e consultas analíticas
  cross-tenant (nossas, para métrica de produto) ficam caras. Para um produto
  SMB com muitos tenants pequenos, discriminador + RLS é o padrão adequado.
- **Banco por tenant.** Descartada: inviável para uma pessoa operar.
- **`SET LOCAL` dentro de transação, via aspecto em `@Transactional`.**
  Tecnicamente mais correto que `SET` (reseta sozinho no commit). Descartada
  por dois motivos: leituras fora de transação explícita ficariam sem GUC, e o
  aspecto adiciona uma camada de mágica difícil de depurar. O decorador de
  `DataSource` que sempre seta o valor (tenant ou vazio) cobre 100% das
  conexões, inclusive as fora de transação, e é lido de cima a baixo em 40
  linhas.
- **Hibernate Filters (`@Filter`)**. Descartada: precisa ser habilitado por
  sessão, e se esquecerem de habilitar, a consulta roda sem filtro —
  falha aberta. `@TenantId` é sempre-ligado.

## Consequências

- Nenhuma query de aplicação precisa escrever o predicado de tenant à mão, e
  **mesmo que alguém escreva SQL nativo e esqueça**, o RLS barra no banco
- O pool de conexões nunca entrega conexão com tenant de outro request: o GUC é
  reescrito a cada `getConnection()`
- Operações administrativas legítimas que precisam cruzar tenants (relatório
  interno, migração de dados) **não passam pela aplicação** — usam um papel de
  banco separado, e isso é intencionalmente incômodo
- O `@TenantId` do Hibernate exige que o campo tenant seja imutável na entidade
- Exige teste de isolamento em todo código que toca query (tarefa 5 estabelece
  o padrão desse teste)
