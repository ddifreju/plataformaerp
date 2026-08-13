# Estado do Projeto

**Atualizado em:** 12 de agosto de 2026
**Fase:** 2 concluída — próxima: 3 (interface)
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

> **Fases 0 e 1 compilam e os testes sem banco passam.** O que ainda não rodou
> é o que depende de Postgres — ver "Pendente de validação".

### Fase 1 — Espinha de dados
7. [x] Modelo canônico: tenant, canal, produto, variação, pedido, item
8. [x] Modelo canônico: custo, devolução, conversa, mensagem, cliente
9. [x] Migrations reversíveis de tudo acima
10. [x] Adaptador Mercado Livre contra fixture (mock, sem credencial)
11. [x] Adaptador de ERP contra fixture
12. [x] Pipeline de ingestão com idempotência

### Fase 2 — Motor de margem (o diferencial)
13. [x] Tabela de taxas por marketplace, versionada por vigência
14. [x] Cálculo de custo real por pedido
15. [x] Cálculo de margem líquida com memória de cálculo auditável
16. [x] Endpoint que responde "quanto sobrou no período X"

### Fase 3 — Interface
17. [ ] Autenticação e sessão
18. [ ] Visão do dono: faturamento bruto → lucro real, com decomposição
19. [ ] Visão do gestor: gargalos do processo
20. [ ] Visão do analista: fila de pendências

---

## Bloqueios atuais

Ver `docs/PENDENCIAS.md`. Nenhum bloqueia as tarefas 1 a 20 — todas podem ser
construídas contra mock.

**Único bloqueio de validação: Docker.** Está instalado, mas o engine só sobe
depois de reiniciar o computador. Ver `docs/PENDENCIAS.md`.

## Pendente de validação

### O que JÁ foi validado de verdade (12/08/2026)

- **`./mvnw -B test-compile`: BUILD SUCCESS.** O risco nº 1 da Fase 0 —
  `CurrentTenantIdentifierResolver<UUID>`, que dependia do Hibernate 6.4+ —
  **compila**.
- **67 testes sem banco passando, 0 falhas** (de 116 casos no projeto; os
  demais dependem de Testcontainers). Cobrem a regra do dinheiro
  (`SuporteJsonTest`), os dois adaptadores, o contexto de tenant, as entidades
  e todo o núcleo puro do motor de margem.
- **O exemplo numérico do documento fiscal (§2.5) foi conferido à mão contra o
  teste**: N2 `51,2636`, N3 `44,9636`, 25,64% e 22,49%. O teste usa `compareTo`,
  exercita os 3 rótulos de teto e rastreia os 7 IDs de custo — não passa por
  construção.
- Maven Wrapper (`mvnw`) gerado: o projeto é autossuficiente.
- Versões do `pom.xml` conferidas no Maven Central.
- `.gitignore` de fato ignora `infra/.env` e **não** ignora `.env.exemplo`.

### O que ainda NÃO foi validado (tudo depende do Docker)

Reinicie e rode:
```bash
make preparar     # sobe o Postgres, migra e define a senha do app
make test         # a suíte inteira, incluindo o isolamento
```

Falta provar: `ddl-auto: validate` contra o Postgres real (o risco `char(n)` vs
`varchar` e o mapeamento `jsonb`), as migrations aplicando de fato, e toda a
suíte de isolamento por Testcontainers.

### O que a primeira execução valida de uma vez só

`make test` roda o `RlsAtivoEmTodasAsTabelasTest`, que descobre as tabelas por
`information_schema` e exige RLS forçado + 4 policies em **todas** que tenham
`tenant_id`. Como ele é genérico, a primeira execução valida o molde da decisão
0010 nas **13 tabelas (12 da Fase 1 + `taxa_canal`) de uma vez** — sem ninguém
ter atualizado o teste. Conferi o molde por grep nas 13, mas grep não é o banco:
essa é a prova real.

### A limitação nº 1 do produto hoje: nenhuma margem sai `CALCULADA`

**Não é bug. Não adianta procurar no código.** Encontrado pela revisão da
Fase 2 e registrado aqui para poupar uma tarde de investigação futura.

Nenhum caminho do sistema grava uma linha `custo(MERCADORIA)`: nem os
adaptadores, nem o pipeline de ingestão, nem o `ResolvedorCustoPedido`. O custo
da mercadoria existe em `variacao.custo_unitario_atual`, mas **nada o copia para
`custo` no momento da venda**.

Consequência mecânica: o `DetectorDeLacunas` sempre dispara a lacuna
`custo_mercadoria_nao_cadastrado`, que tem viés `SUPERESTIMA_MARGEM`. Logo,
`RotuloTeto.calcular` **nunca** devolve `CALCULADA` — todo resultado sai, na
melhor das hipóteses, `COM_TETO`, faltando o que costuma ser a maior parcela do
custo.

O mecanismo está funcionando como projetado: a ausência é **visível**, não é um
número errado disfarçado. Mas é a peça que falta para o produto cumprir a
promessa central. **É a primeira tarefa da Fase 3.**

Junto com ela, duas peças que também existem mas não têm quem as chame:
- `ResolvedorCustoPedido` (nível 2 da hierarquia de taxas) **não tem chamador em
  produção** — hoje todo pedido sem valor informado cai direto em lacuna, mesmo
  havendo taxa cadastrada que resolveria. Falha para o lado seguro (mais
  lacunas, nunca número otimista), mas esvazia parte da V013.
- `Rateio` está pronto e testado, sem chamador: `EMBALAGEM` e `ADS` ainda não
  são rateados por ninguém.
- `Apresentacao.paraExibicao` só é usada em teste. O endpoint devolve os valores
  com 4 casas (escala de armazenamento), não 2. Quando a Fase 3 ligar a tela,
  formatar na borda de saída — não recalcular nada.

### A lacuna que o sistema NÃO consegue avisar

`TAXA_ANTECIPACAO` (lacuna #11 do catálogo fiscal) é a única que não deixa
rastro em campo nenhum: um custo ausente do payload e sem taxa cadastrada é
indistinguível, para o código, de um custo que legitimamente não se aplica.

Para um lojista que antecipa recebíveis e não cadastrou a taxa, **a margem sai
superestimada e o sistema não avisa**. Detectar exige saber que aquele tenant
antecipa — informação que não existe no modelo hoje. Está dito no Javadoc do
`DetectorDeLacunas` para não parecer esquecimento.

### Duas limitações que NENHUM teste vai pegar

Não são bugs e não se resolvem rodando a suíte. São limites do que o sistema
sabe hoje, e precisam ser ditos ao usuário em vez de corrigidos no código.

1. **As fixtures são hipótese, não payload real.** A documentação oficial do
   Mercado Livre e do Bling bloqueou o acesso (403/404). Os testes de tradução
   passam contra o que **acreditamos** ser o formato. O primeiro payload real
   pode invalidar parte do mapeamento — isso é esperado. O `SuporteJsonTest`,
   que prova a regra do dinheiro, é o único que não depende disso.
2. **Reconciliação ML × Bling não existe** (decisão 0017): somar canais
   sobrepostos pode contar a mesma venda duas vezes. **Restringe a tarefa 16** —
   a resposta de "quanto sobrou" declara o escopo de canal, em vez de somar às
   cegas.

### Onde é mais provável que quebre no primeiro `make test`

1. **`ddl-auto: validate` com `char(n)`** (`ncm`, `cest`, `uf_entrega`, `moeda`,
   `hash_payload`). Se o Hibernate tratar `bpchar` e `varchar` como
   incompatíveis, a aplicação **não sobe**. Maior risco.
2. **`@JdbcTypeCode(SqlTypes.JSON)` → `jsonb`.**
3. **`especificidade` em `taxa_canal` é `GENERATED ALWAYS`.** Ao mapear em JPA,
   precisa de `insertable = false, updatable = false` — sem isso o Hibernate
   monta o INSERT com a coluna e o Postgres rejeita a instrução inteira.
4. **`UUID[] idsRetornados`** mapeado para `uuid[]` sem `@JdbcTypeCode`.
5. **`@TestConfiguration` aninhado em `IsolamentoDeTenantTest`** (captura de
   SQL). Se `sqlsGerados` vier vazio, adicione `@Import(...)`.
6. **`EXCLUDE USING gist` da V013** — sintaxe revisada mas nunca aplicada.

### Dívida conhecida (não bloqueia)

- `ConsultaAuditada` tem `@Id` sem `@GeneratedValue`, então `save()` chama
  `merge()` e faz um `SELECT` a mais por escrita. Não afeta isolamento nem
  correção. Resolver com `Persistable<UUID>` **se** virar gargalo — por ora,
  simplicidade acima de otimização.

### Dívida da Fase 1 — QUITADA em 12/08/2026

Todos os 5 itens abaixo foram resolvidos antes de começar a Fase 2. Ficam
registrados porque o histórico do *porquê* continua útil.

1. ~~Reprocessar payload alterado não atualiza `Pedido`/`Cliente`.~~
   **Resolvido**: métodos de intenção `atualizarAPartirDaOrigem`. Identidade
   (`id`, `tenant_id`, chave natural) nunca muda; campo nullable só é
   sobrescrito quando a origem traz valor — payload mais estreito não apaga
   dado bom.
2. ~~Falha de tradução desfaz o evento inteiro.~~ **Resolvido**: `status=ERRO`
   e `erro_mensagem` gravados em transação própria (`REQUIRES_NEW`
   sequencial). A mensagem carrega tipo do erro e campo, **nunca** conteúdo do
   payload.
3. ~~`variacaoId` sempre nulo.~~ **Resolvido** conforme decisão 0018: o
   pipeline resolve por SKU; não achando, declara ausência.
4. ~~`RepositorioCusto` com métodos sem chamador.~~ Endereçado na tarefa 14.
5. ~~`AdaptadorBling.traduzirPedido` com ~140 linhas.~~ **Resolvido**:
   `parseItens` extraído, espelhando o do Mercado Livre.

**Bug sério encontrado de brinde e corrigido:** o pedido apontava para o UUID de
cliente que o adaptador gerou na tradução e que nunca era gravado quando o
comprador já existia. A FK rejeitaria. Efeito prático: o sistema ingeria o
**primeiro** pedido de cada comprador e falhava em todos os seguintes — ou seja,
quebrava exatamente no comprador recorrente. Corrigido com
`Pedido.resolverCliente(...)`.

## Decisões tomadas

Ver `docs/decisoes/`. **0004 a 0021.**

As mais estruturantes, em ordem de peso:
- **0007** (propagação de tenant) + **0010** (molde de RLS por tabela). As 13
  tabelas repetem o molde sem exceção.
- **0015** (FK composta com `tenant_id`) — o banco recusa fisicamente uma
  referência cruzada entre tenants.
- **0019** (três níveis de taxa, sem quarto nível) — nenhum número de margem
  existe sem rótulo de confiança. É o que sustenta a proposta do produto.
- **0017** (reconciliação entre fontes é explícita) — restringe a tarefa 16.

Decisões que nasceram de eu ter errado, registradas como tal: **0011** (Flyway
fora do boot), a ressalva da **0010** sobre `SET` vs `SET LOCAL`, e a **0020**
(imposto fora da `taxa_canal`, contra a sugestão do documento fiscal).

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
| 2026-08-12 | Toolchain instalado | JDK 21, Maven 3.9.9, make, Node. `mvnw` gerado. **Compilação: BUILD SUCCESS.** O risco nº 1 da Fase 0 (`CurrentTenantIdentifierResolver<UUID>`, que dependia do Hibernate 6.4+) **compila**. Docker instalado mas o engine só sobe após reiniciar. |
| 2026-08-12 | 13. Tabela de taxas versionada | Feito. `taxa_canal` (V013+U013) com vigência semiaberta, curinga por sentinela `'*'` (não `NULL`, senão o `EXCLUDE` teria buraco), `especificidade` gerada no banco e `EXCLUDE USING gist` impedindo sobreposição. A consulta de seleção vem pronta no cabeçalho. |
| 2026-08-12 | 14. Custo real por pedido | Feito. `ResolvedorCustoPedido` aplica a hierarquia de 3 níveis da decisão 0019: valor informado pela fonte → taxa vigente **na data do fato gerador** → lacuna declarada. Sem quarto nível: nada de "taxa mais próxima". |
| 2026-08-12 | 15. Margem com memória de cálculo | Feito. Fórmula N0→N3 com **quatro números nomeados** em vez de um "lucro" genérico. Cada resultado carrega a decomposição por bloco, a origem de cada dedução e os IDs de custo que entraram. Rateio de maior resto com desempate determinístico — recalcular dá o mesmo número, que é o que torna a auditoria possível. |
| 2026-08-12 | 16. Endpoint do período | Feito. `GET /api/margem/periodo` com `canalId` **obrigatório** (decisão 0021): o backend nunca soma canais potencialmente sobrepostos. Controller com 42 linhas, só delega; tenant vem do contexto, nunca de parâmetro. |
| 2026-08-12 | Verificação independente do motor | Recalculei o exemplo §2.5 à mão e conferi contra o teste: N2 `51,2636`, N3 `44,9636`, 25,64% e 22,49% batem. O teste usa `compareTo`, exercita os 3 rótulos de teto e rastreia os 7 IDs de custo — não passa por construção. **67 testes puros, 0 falhas.** |
| 2026-08-12 | Auditoria de segurança (Fase 2) | **Nenhum vazamento entre tenants.** As 3 queries novas são JPQL sobre entidades com `@TenantId`, então recebem o predicado automático. `canalId` vindo do usuário não é vetor: o predicado de tenant é aplicado **antes** do filtro por canal, e canal alheio devolve vazio — indistinguível de "canal vazio no período", sem oráculo de enumeração. Corrigidos: `GRANT UPDATE` largo demais em `taxa_canal` (agora por coluna) e o alerta de repasse que era calculado e descartado. |
| 2026-08-12 | Revisão de código (Fase 2) | Aprovou o pacote (granularidade adequada, regra do teto sem duplicação, `Rateio` fiel ao §6.3). Achado principal: **nenhuma margem sai `CALCULADA`** porque ninguém grava `custo(MERCADORIA)` — registrado acima como limitação nº 1, não como bug. |
| 2026-08-12 | Teste de isolamento do motor | `IsolamentoMargemTest` (7 casos) fecha o achado ALTO da auditoria: a regra 1 do CLAUDE.md exige teste de isolamento por query nova. **116 casos de teste no projeto.** O agente verificou minha premissa em vez de repeti-la e descobriu que a sabotagem que sugeri (`nativeQuery = true`) **não** derrubaria o teste sozinha — o RLS, camada independente, continuaria filtrando. Está documentado com a cadeia real de sabotagem. |
| 2026-08-12 | Auditoria de segurança (Fase 1) | `revisor-seguranca` **não achou vazamento entre tenants** nas 12 tabelas novas. Corrigido o achado MÉDIO: a ingestão agora valida o canal contra o tenant **antes** de qualquer escrita, com exceção própria, em vez de depender da FK composta estourar no meio do INSERT. Corrigido também vazamento de conteúdo de payload em mensagem de exceção. |
| 2026-08-12 | Revisão de código (Fase 1) | `revisor-codigo` aprovou o padrão geral (12 entidades uniformes, `SuporteJson` como abstração certa, nenhuma abstração prematura entre os adaptadores). Apontou 2 comportamentos críticos sem teste — ambos cobertos agora. Dívida registrada na seção acima. |
| 2026-08-12 | Testes de isolamento da Fase 1 | Feito. `IsolamentoFaseUmTest` (comportamental em `canal`, `pedido`, `cliente`), `ChaveCompostaImpedeReferenciaCruzadaTest` (prova que a decisão 0015 não é só comentário: FK cruzada entre tenants é recusada com SQLSTATE 23503) e 3 casos novos no `ServicoIngestaoTest` (canal de outro tenant, dedup de cliente, backstop de corrida). 63 casos de teste no total. **Nenhum executado.** |
| 2026-08-12 | Auditoria de segurança (Fase 0) | `revisor-seguranca` não achou vazamento entre tenants. Corrigidos: injeção via `VERSAO` no `migrate-undo`, pool Hikari injetável (`autowireCandidate = false`), portas em `0.0.0.0`, `server.error.*` exposto, allowlist do actuator. Registrada a decisão 0012 (LGPD/`executado_por`). |
