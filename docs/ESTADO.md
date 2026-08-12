# Estado do Projeto

**Atualizado em:** 12 de agosto de 2026
**Fase:** 1 — Espinha de dados (tarefas 7 a 12 concluídas)
**Modo:** autônomo (gerente decide, registra e segue)

---

## Fila de tarefas

Execute em ordem. Não pergunte antes de começar cada uma.

### Fase 0 — Fundação
1. [x] Criar estrutura do monorepo (backend, frontend, infra, docs)
2. [x] Docker Compose com PostgreSQL 16 + pgvector
3. [x] Esqueleto Spring Boot com resolução de tenant no filtro de request
4. [x] Row Level Security configurado
5. [x] Teste de isolamento de tenant (precisa FALHAR se vazar)
6. [x] Makefile com dev, test, migrate

> **Fase 0 escrita, NÃO executada.** Ver "Pendente de validação" no fim deste
> arquivo. A máquina não tem JDK, Maven, Docker nem Node — nada foi compilado
> nem rodado. Este é o risco aberto mais importante do projeto agora.

### Fase 1 — Espinha de dados
7. [x] Modelo canônico: tenant, canal, produto, variação, pedido, item
8. [x] Modelo canônico: custo, devolução, conversa, mensagem, cliente
9. [x] Migrations reversíveis de tudo acima
10. [x] Adaptador Mercado Livre contra fixture (mock, sem credencial)
11. [x] Adaptador de ERP contra fixture
12. [x] Pipeline de ingestão com idempotência

### Fase 2 — Motor de margem (o diferencial)
13. [ ] Tabela de taxas por marketplace, versionada por vigência
14. [ ] Cálculo de custo real por pedido
15. [ ] Cálculo de margem líquida com memória de cálculo auditável
16. [ ] Endpoint que responde "quanto sobrou no período X"

### Fase 3 — Interface
17. [ ] Autenticação e sessão
18. [ ] Visão do dono: faturamento bruto → lucro real, com decomposição
19. [ ] Visão do gestor: gargalos do processo
20. [ ] Visão do analista: fila de pendências

---

## Bloqueios atuais

Ver `docs/PENDENCIAS.md`. Nenhum bloqueia as tarefas 1 a 20 — todas podem ser
construídas contra mock.

**Novo bloqueio de validação (não de construção):** a máquina não tem JDK,
Maven, Docker nem Node. Nada da Fase 0 foi compilado ou executado.

## Pendente de validação — LEIA ANTES DE SEGUIR PARA A FASE 2

As Fases 0 e 1 estão **escritas e revisadas, não executadas**. Todo o código foi
verificado por leitura (com auditoria de segurança e revisão de código
dedicadas), mas nenhum compilador ou banco confirmou nada.

São 76 arquivos Java, 12 migrations e 12 undos escritos sem uma única
compilação. Trate a primeira execução como uma fase de trabalho própria, não
como um detalhe.

O que **foi** validado de verdade:
- Todas as versões do `pom.xml` existem no Maven Central (conferidas em
  12/08/2026) e foram elevadas para o último patch da linha 3.3.x
- `pom.xml` é XML bem formado (balanceamento de tags verificado)
- `Makefile` usa tabs corretamente nas 38 linhas de receita
- `.gitignore` de fato ignora `infra/.env` e de fato **não** ignora
  `infra/.env.exemplo` (verificado com `git check-ignore`)

### Primeira execução, na ordem

```bash
make preparar     # sobe o Postgres, migra e define a senha do app
make test         # a suíte inteira, incluindo o isolamento
```

Espere quebrar. Nada disso rodou ainda.

### O que a primeira execução valida de uma vez só

`make test` roda o `RlsAtivoEmTodasAsTabelasTest`, que descobre as tabelas por
`information_schema` e exige RLS forçado + 4 policies em **todas** que tenham
`tenant_id`. Como ele é genérico, a primeira execução valida o molde da decisão
0010 nas **12 tabelas da Fase 1 de uma vez** — sem ninguém ter atualizado o
teste. Conferi o molde por grep, mas grep não é o banco: essa é a prova real.

### Riscos da Fase 1 que só o primeiro `make test` resolve

1. **`char(n)` vs `varchar` no `ddl-auto: validate`** — colunas `char` (`ncm`,
   `cest`, `uf_entrega`, `moeda`, `hash_payload`) mapeadas com
   `@Column(length=n)`. Se o validador do Hibernate tratar `bpchar` e `varchar`
   como incompatíveis, a aplicação **não sobe**. É o maior risco da fase.
2. **`@JdbcTypeCode(SqlTypes.JSON)` → `jsonb`** — se o dialeto mapear para
   `json` em vez de `jsonb`, o `validate` falha em todas as colunas jsonb.
3. **`?::jsonb` via `JdbcTemplate`** no pipeline de ingestão.
4. **Fixtures são hipótese, não payload real.** A documentação oficial do
   Mercado Livre e do Bling bloqueou o acesso (403/404). Os testes de tradução
   passam contra o que **acreditamos** ser o formato. O primeiro payload real
   pode invalidar parte do mapeamento — e isso é esperado, não é bug.
   O teste que **não** depende disso é o `SuporteJsonTest`, que prova a regra do
   dinheiro de forma independente da fixture.
5. **Reconciliação ML × Bling não existe** (decisão 0017): somar canais
   sobrepostos pode contar a mesma venda duas vezes. Isso **restringe a tarefa
   16** — a resposta de "quanto sobrou" precisa declarar o escopo de canal.

### Onde é mais provável que quebre (por ordem de risco)

1. **`ResolvedorTenantHibernate implements CurrentTenantIdentifierResolver<UUID>`**
   — o resolver genérico com tipo diferente de `String` depende do Hibernate 6.4+.
   Se não compilar, a alternativa é `<String>`, mas aí o mapeamento contra a
   coluna `uuid` pode falhar no `ddl-auto: validate`. Teste isto isolado antes
   de confiar no resto.
2. **`UUID[] idsRetornados`** mapeado direto para `uuid[]` sem `@JdbcTypeCode`.
3. **`@TestConfiguration` aninhado em `IsolamentoDeTenantTest`** (captura de SQL).
   Se `sqlsGerados` vier vazio, adicione `@Import(...)` — a instrução exata está
   no relato do próprio teste.
4. **Placeholder `${SPRING_DATASOURCE_PASSWORD}` sem default** sendo sobreposto
   por `@DynamicPropertySource` nos testes.
5. **`server.error.*` e o handler de fallback** foram adicionados depois da
   auditoria e não têm teste próprio.

### Dívida conhecida (não bloqueia)

- `ConsultaAuditada` tem `@Id` sem `@GeneratedValue`, então `save()` chama
  `merge()` e faz um `SELECT` a mais por escrita. Não afeta isolamento nem
  correção. Resolver com `Persistable<UUID>` **se** virar gargalo — por ora,
  simplicidade acima de otimização.
- O Maven Wrapper (`mvnw`) não existe. Rode `mvn wrapper:wrapper` no `backend/`
  na primeira vez que tiver Maven instalado.

## Decisões tomadas

Ver `docs/decisoes/`. Nesta execução: **0004 a 0017**.

As mais estruturantes, em ordem de peso:
- **0007** (propagação de tenant) + **0010** (molde de RLS por tabela). As 12
  tabelas da Fase 1 repetem o molde sem exceção.
- **0015** (FK composta com `tenant_id`) — o banco recusa fisicamente uma
  referência cruzada entre tenants. Veio do `arquiteto-dados`, promovida a
  padrão do projeto.
- **0017** (reconciliação entre fontes é explícita) — a que mais restringe a
  Fase 2.

Duas decisões nasceram de eu ter errado e estão registradas assim: **0011**
(Flyway fora do boot) e a ressalva dentro da **0010** sobre `SET` vs `SET LOCAL`.

## Log de execução

| Data | Tarefa | Resultado |
|---|---|---|
| 2026-08-12 | 1. Estrutura do monorepo | Feito. `backend/`, `frontend/`, `infra/`, `docs/` + Makefile na raiz. Git inicializado (decisão 0009). `frontend/` é placeholder documentado: sem Node, o scaffold do Next.js é gerado, não escrito à mão. |
| 2026-08-12 | 2. Docker Compose com Postgres 16 + pgvector | Feito. Imagem `pgvector/pgvector:pg16`, healthcheck, portas presas a `127.0.0.1`, segredos só via `infra/.env`. Adminer em perfil opcional. **Não executado** (sem Docker). |
| 2026-08-12 | 3. Esqueleto Spring Boot com tenant no filtro | Feito. Quatro camadas da decisão 0007: `FiltroTenant` → `ContextoTenant` → Hibernate `@TenantId` → `DataSourceComTenant` (GUC). Todas falham fechadas. **Não compilado.** |
| 2026-08-12 | 4. Row Level Security | Feito. V001–V004 com `ENABLE`+`FORCE`, quatro policies por tabela, `app_current_tenant_id()` fail-closed, papel `app_aplicacao` sem `BYPASSRLS`. Undo pareado U001–U004. **Não executado.** |
| 2026-08-12 | 5. Teste de isolamento | Feito. `IsolamentoDeTenantTest` (6 casos, prova que a linha alheia existe antes de provar que não vaza) + `RlsAtivoEmTodasAsTabelasTest` (sentinela genérico: quebra sozinho se a Fase 1 criar tabela sem RLS) + testes do filtro e do contexto. Inclui seção de 4 sabotagens para provar que o teste não é decorativo. **Não executado.** |
| 2026-08-12 | 6. Makefile | Feito. `dev`, `test`, `migrate`, `migrate-undo`, `definir-senha-app`, `preparar`, `ajuda`. Tabs verificados. **Não executado** (sem make). |
| 2026-08-12 | 7 e 8. Modelo canônico | Feito. 12 tabelas: `canal`, `produto`, `variacao`, `cliente`, `pedido`, `item_pedido`, `devolucao`, `item_devolucao`, `custo`, `conversa`, `mensagem`, `evento_ingerido`. `item_devolucao` foi adicionada ao escopo: sem ela "devolução parcial" não tem dado e a margem por SKU erra justamente nos pedidos que mais doem. Dinheiro `NUMERIC(18,4)`. Documento de cliente só como HMAC, nunca em claro. |
| 2026-08-12 | 9. Migrations reversíveis | Feito. V005–V012 com U005–U012 pareados. Verifiquei mecanicamente que as 12 tabelas têm `ENABLE`+`FORCE`, 4 policies e GRANT — o molde da 0010 está integral. Nenhum tipo de ponto flutuante em coluna monetária. |
| 2026-08-12 | 7–9. Entidades JPA | Feito. 12 entidades + repositórios, pacote por domínio. Cruzei os 9 maiores enums Java contra os `CHECK` do SQL: batem exatamente. Nenhum `double`/`float` no código. |
| 2026-08-12 | 10 e 11. Adaptadores ML e Bling | Feito contra fixture. **A documentação oficial das duas APIs respondeu 403/404** — as fixtures são reconstrução do formato conhecido, com cada campo marcado por nível de confiança. Nada foi apresentado como confirmado. Ver `docs/integracoes/`. |
| 2026-08-12 | 12. Pipeline de ingestão | Feito. UPSERT por chave natural `(tenant_id, canal_id, tipo_evento, id_externo)` numa instrução só, sem janela de corrida. SQL nativo com `tenant_id` explícito e tudo em bind parameter — nativo não recebe o predicado do `@TenantId`. |
| 2026-08-12 | Auditoria de segurança (Fase 0) | `revisor-seguranca` não achou vazamento entre tenants. Corrigidos: injeção via `VERSAO` no `migrate-undo`, pool Hikari injetável (`autowireCandidate = false`), portas em `0.0.0.0`, `server.error.*` exposto, allowlist do actuator. Registrada a decisão 0012 (LGPD/`executado_por`). |
