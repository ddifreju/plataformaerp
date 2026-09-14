# Estado do Projeto

**Atualizado em:** 14 de setembro de 2026
**Fase:** 4 em andamento — camada de IA, preparo de produção, escopo de canal
**Modo:** autônomo (gerente decide, registra e segue)

---

## Onde paramos (14/09/2026)

Sessão nova, um mês depois. Nada mudou entre 14/08 e 14/09: nenhum commit,
nenhuma credencial nova, nenhuma decisão de negócio resolvida.

A Fase 4 foi definida e registrada na **decisão 0029**: camada de IA primeiro
(bloco A), preparo de produção depois (bloco B), escopo de canal declarado por
último (bloco C). A reconciliação ML × Bling por casamento de pares foi
**descartada nesta fase** por contradizer a decisão 0017 — o gatilho para
retomá-la é o primeiro payload real do Mercado Livre.

A arquitetura da camada de IA está na **decisão 0030**: o modelo de linguagem
interpreta a pergunta e nada mais. Catálogo fechado de perguntas, parâmetro
validado em Java, número sempre vindo dos serviços já testados, resposta por
template, recusa como caminho de primeira classe. **Decisão 0031**: pgvector
continua sem uso, com gatilhos escritos para reverter.

---

## Histórico: passagem de máquina (14/08/2026)

Este resumo existe porque o projeto está migrando de computador. Estado honesto:

**Funcionando, visto no navegador em 14/08/2026:**
- Pilha completa em dev: Postgres (Docker) → backend Spring Boot na 8080 →
  frontend Next.js na 3000. Login com sessão real, e as três visões
  renderizando com os dados de demonstração (Ateliê Bem Posto, 50 pedidos):
  Resultado (decomposição N0→N3, 30 pedidos e 142 linhas de custo na consulta
  do ML Clássico, margem 34,49%, lacunas declaradas com ação sugerida),
  Operação (status, devoluções, eventos com erro) e Pendências (fila acionável).
- Suíte inteira verde contra Postgres real: 161 testes backend + 20 frontend.
- 16 migrations aplicando de primeira; molde de RLS provado nas 14 tabelas.

**Quebrado/limitado — nada conhecido quebrado em código.** Limitações abertas:
- Fixtures de Mercado Livre e Bling são hipótese até a primeira credencial real.
- CORS nunca exercitado (rewrite do Next em dev) — pendência de staging.
- Margem: imposto depende do regime tributário do tenant (pendência de negócio);
  antecipação de recebível é indetectável sem resposta da lojista.

**Próxima tarefa (em 14/08):** não havia fila aberta — o roadmap inicial
(tarefas 1–20) acabou. Resolvido em 14/09 pela decisão 0029, que abriu a
Fase 4 (tarefas 21–32).

**Para subir na máquina nova:** clonar, copiar `infra/.env.exemplo` para
`infra/.env` e revisar senhas locais, `make preparar`, `make dados-demo`,
`make backend`, e `cd frontend && npm install && npm run dev`. Logins de
demonstração no relatório do seed (`infra/dados-demo.sql`).
Toolchain necessário: JDK 21, Maven (ou o mvnw do repo), Docker Desktop,
Node 20+, GNU make. Memória do Claude Code e log do task-observer são locais
por máquina e não migram com o repositório.

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
17. [x] Autenticação e sessão
18. [x] Visão do dono: faturamento bruto → lucro real, com decomposição
19. [x] Visão do gestor: gargalos do processo
20. [x] Visão do analista: fila de pendências

### Fase 4 — Camada de IA, produção e escopo de canal (decisão 0029)

**Bloco A — Camada de IA sobre dados operacionais** (decisão 0030: o modelo
interpreta a pergunta; número nenhum passa pela geração do modelo)

21. [x] `PortaModeloLinguagem` + duas implementações sem chave: `ModeloHeuristico`
    (conservador, roda em dev, extrai canal e período de texto livre) e
    `ModeloGravado` (fixture, inclusive adversária)
22. [x] Catálogo fechado de perguntas respondíveis, com parâmetros tipados e
    validação determinística (canal do tenant, período, ausência → esclarecimento)
23. [x] Executor: intenção → serviço já existente → `ConsultaAuditada` → resposta
    por template, com rótulo de confiança e memória de cálculo
24. [x] `POST /api/pergunta` + teste de isolamento de tenant + auditoria de segurança
25. [ ] Suíte de avaliação com limiar (regra 4) e asserção dura nas travas
26. [ ] Tela de perguntas, com "como cheguei nesse número" e a recusa honesta

**Bloco B — Preparo de produção** (nada aqui é deploy; deploy é da fundadora)

27. [ ] Dockerfile do backend: multi-stage, JRE 21, usuário sem privilégio
28. [ ] Dockerfile do frontend: build standalone do Next
29. [ ] Perfil de staging com proxy reverso de mesma origem (condição 2 da
    decisão 0025): cookie `Secure`/`SameSite` exercitado no fio, CORS resolvido
30. [ ] Checklist de deploy + falha no boot quando faltar variável obrigatória

**Bloco C — Escopo de canal declarado** (substitui a reconciliação por
casamento, descartada na decisão 0029)

31. [ ] A lojista declara o escopo de cada canal — sem heurística, sem adivinhar
32. [ ] Soma entre canais permitida apenas sobre conjunto declarado disjunto
33. [ ] A camada de IA passa a responder sem exigir canal, quando a declaração
    permitir — é o que torna "quanto sobrou no mês?" respondível

---

## Placar de testes da Fase 4 (14/09/2026)

**Docker não subiu nesta máquina nesta sessão** (o daemon não respondeu; o
Docker Desktop desta máquina está em `AppData\Local\Programs\DockerDesktop`,
instalação por usuário — e já há pastas `run-quebrado-*` de sockets órfãos
afastados antes, exatamente o padrão descrito no `CONTEXTO-HANDOFF.md`).
Seguindo o padrão já provado nas Fases 0 e 1: escrever o teste, rodar o que
roda sem banco, e registrar o que fica esperando.

Nota de ambiente aprendida hoje: **não exporte `TMP=C:\Temp` ao rodar o
Maven** nesta máquina — o surefire 3.2.5 morre com
`ExceptionInInitializerError` antes de executar teste nenhum. O `TMP` do
handoff serve para **subir o backend** (o pipe AF_UNIX do Tomcat), não para
rodar a suíte.

| | |
|---|---|
| Testes puros rodados e verdes (pacote `pergunta`) | **61** |
| `./mvnw -B test-compile` do projeto inteiro | BUILD SUCCESS |
| Aguardando Docker | `IsolamentoPerguntaTest` (3 casos) e os casos novos de `/api/pergunta` no `ContratoApiTest` |

---

## Bloqueios atuais

Ver `docs/PENDENCIAS.md`. Nenhum bloqueia as tarefas 1 a 20 — todas podem ser
construídas contra mock.

**Nenhum bloqueio de ferramenta.** Toolchain completo e Docker Engine 29
funcionando desde 13/08/2026. O que resta depende de credencial externa e de
decisão de negócio — ver `docs/PENDENCIAS.md`.

## Validação real — FEITA em 13/08/2026

**A suíte inteira passa contra um Postgres de verdade.** Não há mais nada
esperando o Docker. Placar atual: **161 testes, 0 falhas, 0 erros** (eram 150
nesta primeira rodada; os 11 restantes são o `ContratoApiTest`, descrito
adiante).

O que a primeira execução provou:
- As **15 migrations aplicam** num Postgres 16 real (pgvector), da primeira vez
- O **molde de RLS da decisão 0010 está correto nas 14 tabelas** — provado pelo
  sentinela genérico, não por leitura
- Os **testes de isolamento nunca executados agora executam e passam**:
  `IsolamentoFaseUmTest`, `IsolamentoMargemTest`, `IsolamentoLoginTest`,
  `ChaveCompostaImpedeReferenciaCruzadaTest`, `IsolamentoDeTenantTest`
- O motor de margem, a ingestão idempotente e o login funcionam ponta a ponta
  contra banco

### O que quebrou de verdade, e o que isso ensinou

Três coisas — e nenhuma delas era o que a revisão por leitura suspeitava:

1. **O risco nº 1 estava certo.** `char(n)` vs `varchar` derrubou o contexto de
   37 testes. Corrigido pela V015 (decisão 0027). A previsão feita meses antes,
   sem compilador, acertou o alvo.
2. **`Testcontainers` 1.19.8 não fala com Docker Engine 29.** Nada a ver com o
   código — dependência velha. Bump para 1.21.4 via BOM.
3. **Bug real de produção que só o banco revelou:** evento com `status = ERRO`
   nunca era reprocessado, porque o reenvio idêntico era no-op incondicional.
   Um payload quebrado ficava travado **para sempre**, mesmo depois de corrigido
   o bug que o quebrou. Nenhuma revisão por leitura tinha pego isso.

E uma falha de teste que era o sistema funcionando: um `count()` fora do
contexto de tenant devolveu zero, porque o RLS estava fazendo exatamente o que
deveria. Corrigido no teste, com comentário para o próximo que tropeçar.

### Contrato da API validado — e um bug crítico que só ele achou

`ContratoApiTest` (11 casos) exercita a stack HTTP inteira — filtros, Spring
Security, controllers, Jackson — contra Postgres real. **161 testes, 0 falhas.**

Ele achou o bug mais grave do projeto até hoje: **o login nunca persistia a
sessão.** O `FiltroLoginJson` é construído à mão e não recebia um
`SecurityContextRepository`, ficando com o default de fábrica
(`RequestAttributeSecurityContextRepository`), que guarda o contexto como
atributo da requisição e o descarta no fim dela.

O efeito era o pior possível: o login respondia **204 (sucesso)**, a senha era
conferida de verdade, a sessão até era criada — e a requisição **seguinte
chegava anônima**. O sistema inteiro era inacessível por trás do login, sem
nenhuma mensagem de erro.

**Nenhuma das três revisões por leitura pegou**, porque o código parece
completo — é a ausência de uma linha que só aparece quando o fluxo roda.
É o mesmo padrão da `ChangeSessionIdAuthenticationStrategy` logo acima: filtro
montado à mão não recebe o que `.formLogin()` configuraria.

O que o teste também confirmou: todo valor monetário chega como **string**
(decisão 0026), verificado no JSON cru com `isString()`; nenhum DTO vaza
`senhaHash` nem `chaveCredencial`; login com senha errada e com e-mail
inexistente devolvem resposta idêntica; e canal de outro tenant não devolve
dado. **Nenhuma divergência de nome ou tipo** entre a API real e
`frontend/src/lib/api/tipos.ts`.

### Ambiente de demonstração pronto para ver no navegador

`make dados-demo` popula um tenant navegável. **Credenciais (só em banco
local):** `dono@demo.plataforma`, `gestor@demo.plataforma` e
`analista@demo.plataforma`, todos com senha `demo1234`.

O que o seed contém: 3 canais (ML Clássico, ML Premium, Bling), 8 produtos com
16 variações, 12 clientes, **50 pedidos em 90 dias** (ticket R$ 79,90–299,90,
média R$ 190,50), 246 linhas de custo, 6 devoluções com motivo e 4 eventos de
ingestão em `ERRO`.

Os **três rótulos** aparecem de propósito: ~32 `CALCULADA`, ~11 `COM_TETO`
(variação sem custo cadastrado ou item sem variação casada) e 7
`INDETERMINADA` (repasse previsto ausente). Um bloco de conferência no fim do
script **recusa o seed** se algum rótulo ficar de fora.

Datas relativas a `CURRENT_DATE` (a demo não envelhece) e geração
determinística, sem `random()`: um número conferido na tela hoje continua o
mesmo amanhã.

### O que ainda não foi validado

- **O navegador ainda não abriu a interface.** O bloqueio do Tomcat foi
  resolvido (era reserva de portas do Hyper-V/winnat, não antivírus), mas a
  validação visual é da fundadora. O contrato está coberto pelo
  `ContratoApiTest`; o que só um container real expõe são os atributos do
  cookie no fio (`HttpOnly`/`SameSite`/`Secure`).
- **As fixtures continuam sendo hipótese** (a documentação do ML e do Bling
  respondeu 403/404). Só o primeiro payload real resolve.
- **CORS não é exercitado em dev**, por causa do rewrite do Next. Fica para
  staging, quando frontend e backend estiverem em origens distintas.
- **Deploy**: nada foi para VPS. Lembrar da condição 2 da decisão 0025 —
  mesma origem atrás de proxy reverso é requisito de segurança, não preferência.

### Histórico: o que já era validado antes (12/08/2026)

- **`./mvnw -B test-compile`: BUILD SUCCESS.** O risco nº 1 da Fase 0 —
  `CurrentTenantIdentifierResolver<UUID>`, que dependia do Hibernate 6.4+ —
  **compila**.
- **109 testes de backend sem banco passando, 0 falhas.** Cobrem a regra do
  dinheiro (`SuporteJsonTest`), os dois adaptadores, o contexto de tenant, as
  entidades, todo o núcleo puro do motor de margem, o fluxo de login e os
  painéis. Os demais dependem de Testcontainers.
- **20 testes de frontend passando** (`node --test`), cobrindo o "vai um" do
  arredondamento em texto e o mapeamento de lacunas.
- **Frontend**: `npm run build` e `npm run lint` limpos.
- **O exemplo numérico do documento fiscal (§2.5) foi conferido à mão contra o
  teste**: N2 `51,2636`, N3 `44,9636`, 25,64% e 22,49%. O teste usa `compareTo`,
  exercita os 3 rótulos de teto e rastreia os 7 IDs de custo — não passa por
  construção.
- Maven Wrapper (`mvnw`) gerado: o projeto é autossuficiente.
- Versões do `pom.xml` conferidas no Maven Central.
- `.gitignore` de fato ignora `infra/.env` e **não** ignora `.env.exemplo`.

### A limitação nº 1 — RESOLVIDA em 12/08/2026

Era: nenhum caminho gravava `custo(MERCADORIA)`, então `RotuloTeto` **nunca**
devolvia `CALCULADA` e toda margem saía faltando a maior parcela do custo.

**Resolvido** na pré-tarefa da Fase 3. O custo é copiado de
`variacao.custo_unitario_atual` no momento da venda e **congelado**: nunca
recalculado depois, para que a memória de cálculo reproduza o número que já foi
mostrado ao lojista. Se o custo não estiver cadastrado ou o SKU não casar,
nenhuma linha é criada e a lacuna continua declarada — com códigos distintos
para "não sei qual produto é" e "sei qual é, mas não sei quanto custou".

O `ResolvedorCustoPedido` (nível 2) passou a ser chamado, e
`Apresentacao.paraExibicao` foi aplicada na borda de saída (2 casas), sem
recalcular nada.

Critério de aceite verificado:
`CongelamentoCustoMercadoriaTest.pedidoComVariacaoCasadaECustoCadastradoProduzRotuloCalculada`.

**`Rateio` continua sem chamador, de propósito.** `EMBALAGEM` e `ADS` não têm
origem de dado alguma hoje: nenhum adaptador captura esses gastos e não há
cadastro manual que gere a linha-mãe de período. Ligar um chamador exigiria
inventar o dado — o que a regra 5 proíbe. Destrava quando existir (a) captura
pelo adaptador ou (b) tela de cadastro.

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

### Os riscos que a suíte já derrubou (histórico, 13/08/2026)

Ficam registrados porque a lista foi escrita **antes** de existir Docker na
máquina, e o placar de acerto diz algo útil sobre o quanto confiar em revisão
por leitura:

1. ~~`ddl-auto: validate` com `char(n)`~~ — **aconteceu exatamente como
   previsto.** Era o item marcado "maior risco". Corrigido na V015 (decisão
   0027).
2. ~~`@JdbcTypeCode(SqlTypes.JSON)` → `jsonb`~~ — funcionou de primeira.
3. ~~`especificidade` `GENERATED ALWAYS`~~ — o `insertable = false` já estava
   correto.
4. ~~`UUID[] idsRetornados`~~ — funcionou.
5. ~~`@TestConfiguration` aninhado~~ — funcionou.
6. ~~`EXCLUDE USING gist` da V013~~ — aplicou sem erro.

Um acerto em seis. E o que de fato mais doeu não estava na lista: uma
dependência de teste velha demais para o Docker Engine, e um bug de
reprocessamento que nenhuma leitura tinha pego.

### Invariantes que não podem regredir (da auditoria da Fase 3)

A auditoria de segurança **não achou vazamento** e listou o que sustenta isso.
Se alguma destas cair, o isolamento cai junto:

1. **`usuario.tenant_id` fora do `GRANT UPDATE` e `updatable = false` no JPA.**
   É o que torna "tenant do contexto == tenant do usuário" uma garantia
   estrutural, não disciplina.
2. **`FiltroTenant` registrado fora do ciclo `@Component`**, com ordem explícita
   `DEFAULT_FILTER_ORDER + 1`. Sem isso, ele poderia rodar antes da cadeia de
   segurança e não haveria usuário de quem extrair o tenant.
3. **Nenhum endpoint `GET` pode alterar estado** — é a condição 1 da decisão
   0025, e é o que sustenta o CSRF desligado. Vale conferir em toda revisão.
4. **`papel` (DONO/GESTOR/ANALISTA) não é autorização** e nada finge que é.
   Vira autorização de verdade só com matriz papel×operação e testes de negação.

### Uma armadilha de teste que a auditoria encontrou

Em dev, `frontend/next.config.ts` faz rewrite de `/api/*` para o backend. Isso é
bom para a segurança (o navegador só fala com a própria origem), mas significa
que **testar em dev não exercita a configuração de CORS**. Um erro nela só
apareceria em produção ou numa chamada direta fora do rewrite.

### Invariante novo, de sangue (decisão 0034)

**`GET` que chama serviço que grava `consulta_auditada` é bug de segurança,
não de estilo.** A auditoria da Fase 4 encontrou que `GET /api/margem/periodo`
gravava auditoria desde a tarefa 16 — ou seja, a condição 1 da decisão 0025
("nenhum GET altera estado"), que é o que sustenta o CSRF desligado, era falsa
havia um mês. Corrigido trocando o verbo para POST.

O que isso ensina sobre revisão: o invariante estava escrito nesta mesma
seção desde a Fase 3, e três auditorias passaram por ele. Só apareceu quando a
pergunta foi feita de forma específica — "confirme que nenhum GET, **inclusive
os antigos**, alterou-se para gravar estado". Invariante escrito não se
verifica sozinho; a pergunta precisa mirar o código antigo, não só o novo.

### Dívida da Fase 4 (registrada nas revisões de 14/09, não bloqueia)

Da revisão de código do pacote `pergunta`:

1. **`GARGALOS_DA_OPERACAO`, `FILA_DE_PENDENCIAS` e `LACUNAS_DA_MARGEM` sem
   teste de contrato HTTP.** 3 dos 5 itens do catálogo sem prova de
   serialização ponta a ponta. O `ContratoApiTest` cobre só margem, recusa,
   texto vazio e canais.
2. **Esclarecimento com canal E período inválidos ao mesmo tempo não é
   testado.** As duas mensagens são coladas com espaço; pode sair frase
   estranha e ninguém notaria.
3. **`DescricaoIntencao.parametrosObrigatorios` e `parametrosOpcionais` são
   dados mortos** — populados no catálogo e lidos por ninguém. Ou some até a
   tela precisar, ou ganha um consumidor. Enquanto estiver assim, pode
   desatualizar em silêncio.
4. **Gatilho de extração do `ServicoPergunta`:** ~400 linhas e 5 caminhos está
   no limite. Quando entrar a 6ª pergunta no catálogo, extrair o bloco de
   margem (hoje mais da metade do arquivo) para classe própria. Antes disso,
   não.
5. **Molde de `RespostaPergunta` repetido em 3 caminhos curtos** (gargalos,
   fila, canais). Na régua do "duplicou 3x, extraia", mas de risco baixo.
6. **`500` duplicado** entre `ServicoPergunta.TAMANHO_MAXIMO_PERGUNTA` e o
   `@Size(max = 500)` de `RequisicaoPergunta`, sem teste amarrando os dois.
   A duplicação é proposital (defesa nas duas bordas); a falta do teste não é.

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

Ver `docs/decisoes/`. **0004 a 0028.**

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
| 2026-09-14 | Fase 4 definida | Decisões 0029 (escopo e ordem), 0030 (arquitetura da camada de IA) e 0031 (pgvector segue sem uso). A reconciliação ML × Bling por casamento de pares foi **descartada** por contradizer a 0017 — com gatilho escrito para retomar. No lugar entrou o escopo de canal declarado, que a própria 0017 chamava de "provavelmente a resposta certa". |
| 2026-09-14 | 21, 22 e 23. Núcleo da camada de pergunta | Feito, **sem migration nenhuma**. `PortaModeloLinguagem` com `ModeloHeuristico` (casa intenção por vocabulário derivado do próprio catálogo, e extrai canal e período de texto livre), catálogo fechado de 5 perguntas, `ValidadorDeParametros` determinístico com `Clock` injetado, e `ServicoPergunta` executando sobre `ServicoMargemPeriodo`/`ServicoPainelGestor`/`ServicoPainelAnalista`/`RepositorioCanal`. **`ConsultaAuditada` é gravada em toda chamada — inclusive recusa e esclarecimento**, porque o texto cru das perguntas recusadas é o insumo para decidir o que entra no catálogo. 61 testes puros. |
| 2026-09-14 | O buraco que quase passou | O `ModeloHeuristico` classificava a intenção mas **não extraía parâmetro nenhum**, então as duas perguntas que interessam (margem e lacunas) nunca chegavam a RESPOSTA pela API real — só com dublê de teste. Passava despercebido porque todo teste de contrato usa dublê. Corrigido: a heurística injeta `RepositorioCanal` (já filtrado por `@TenantId` + RLS) e casa nome/código de canal e um conjunto fechado de expressões de período. Dois canais casando → parâmetro ausente e esclarecimento, nunca escolha. |
| 2026-09-14 | 24. `POST /api/pergunta` | Feito. É POST de propósito e está comentado no código: a rota grava `consulta_auditada`, ou seja, altera estado — um GET aqui quebraria a condição 1 da decisão 0025, que é o que sustenta o CSRF desligado. Nenhuma alteração em `ConfiguracaoSeguranca` foi necessária: `.anyRequest().authenticated()` já cobre rota nova. `PerguntaInvalidaException` e `MethodArgumentNotValidException` → 400 em `ErroApi`, sem ecoar o texto da pergunta. |
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
| 2026-08-12 | 16. Endpoint do período | Feito. `/api/margem/periodo` com `canalId` **obrigatório** (decisão 0021). *(Virou POST em 14/09 — decisão 0034.)*: o backend nunca soma canais potencialmente sobrepostos. Controller com 42 linhas, só delega; tenant vem do contexto, nunca de parâmetro. |
| 2026-08-12 | Verificação independente do motor | Recalculei o exemplo §2.5 à mão e conferi contra o teste: N2 `51,2636`, N3 `44,9636`, 25,64% e 22,49% batem. O teste usa `compareTo`, exercita os 3 rótulos de teto e rastreia os 7 IDs de custo — não passa por construção. **67 testes puros, 0 falhas.** |
| 2026-08-12 | Pré-tarefa da Fase 3 | **A margem agora sai `CALCULADA`.** Custo da mercadoria congelado na ingestão, `ResolvedorCustoPedido` ligado, `Apresentacao` na borda de saída. `Rateio` segue sem chamador porque embalagem e Ads não têm origem de dado — inventar violaria a regra 5. 75 testes puros. |
| 2026-08-12 | Frontend inicializado | `create-next-app` abortou o `npm install` por rede transitória, mas já tinha gerado a árvore: bastou completar. Next 16 + React 19 + Tailwind, `npm run build` passa. `CLAUDE.md` atualizado de 15 para 16 (decisão 0022) — documentação que mente sobre a stack é pior que documentação ausente. |
| 2026-08-12 | 17. Autenticação e sessão | Feito. Spring Security com sessão em cookie, BCrypt custo 12. **O `X-Tenant-Id` foi removido**, não desativado: com sessão existindo, ele seria escalação horizontal trivial. Fecha o `// PROVISÓRIO` aberto na decisão 0007 na Fase 0. A tabela `usuario` (V014) precisou de uma quinta policy para o login enxergar a própria linha antes de haver tenant — decisão 0024, com o que ela expõe dito sem eufemismo. |
| 2026-08-12 | Endpoints das visões 19 e 20 | Feito. `/api/painel/gestor` e `/api/painel/analista`, construídos **só sobre dado que existe**: pedido por status, evento com `status=ERRO`, devolução aberta, lacuna de custo. Processo, nunca pessoa (decisões 0003 e 0012). 88 testes puros. |
| 2026-08-13 | Seed de demonstração navegável | 3 canais, 50 pedidos em 90 dias, 6 devoluções, 4 eventos em `ERRO`, os três rótulos representados. Dois bugs meus no gerador, pegos por conferência dos dados e não por teste: `(n * 23) % 23` é sempre zero, então **todos os 50 pedidos saíram com o mesmo ticket** (multiplicador precisa ser coprimo do módulo), e o canal Bling ficou sem nenhum pedido. Suíte: 161 backend + 20 frontend, build e lint limpos. |
| 2026-08-13 | **Contrato validado ponta a ponta** | `ContratoApiTest` (11 casos) exercita filtros, Spring Security, controllers e Jackson contra Postgres real. **161 testes, 0 falhas.** Confirmou dinheiro-como-texto no JSON cru, ausência de vazamento em DTO, resposta idêntica para senha errada e e-mail inexistente, e isolamento por canal alheio. Zero divergência entre a API e `tipos.ts` do frontend. |
| 2026-08-13 | **Bug crítico: login não persistia sessão** | O `FiltroLoginJson`, construído à mão, não recebia `SecurityContextRepository` e ficava com o default de requisição. Login respondia 204, senha era conferida, sessão era criada — e a requisição seguinte chegava **anônima**. O sistema era inacessível por trás do login, em silêncio. Nenhuma das três revisões por leitura pegou. Corrigido com `DelegatingSecurityContextRepository`. |
| 2026-08-13 | Dados de demonstração | `make dados-demo` (decisão 0028): fora do Flyway, com duas travas contra rodar em produção e senha gerada pelo pgcrypto. Corrigido `valor_repasse_previsto`, cuja ausência faria os dois pedidos saírem `INDETERMINADA` — o script contradizia o próprio comentário. |
| 2026-08-13 | **Primeira validação real** | **150 testes, 0 falhas, 0 erros contra Postgres real.** As 15 migrations aplicam, o molde de RLS está correto nas 14 tabelas (provado pelo sentinela, não por leitura) e os 5 testes de isolamento que nunca tinham executado passam. Corrigidos: `char(n)`→`varchar` (V015, decisão 0027), Testcontainers 1.19.8→1.21.4 (não falava com Docker Engine 29) e um bug real de reprocessamento. |
| 2026-08-13 | Bug do reprocessamento | Evento com `status = ERRO` **nunca era reprocessado**: o reenvio idêntico era no-op incondicional, então um payload quebrado ficava travado para sempre, mesmo depois de corrigido o bug que o quebrou. Nenhuma das três revisões por leitura tinha pego. Agora evento em ERRO sempre reprocessa, e o contador de tentativas é preservado entre retentativas do mesmo payload. |
| 2026-08-13 | Conserto do `definir-senha-app` | `psql` não expande `:variáveis` em comando passado por `-c`, só pela entrada padrão. O alvo falhava ao definir a senha do papel da aplicação. Corrigido pela fundadora e validado contra o banco real. |
| 2026-08-12 | 18, 19 e 20. As três telas | Feito. **Resultado** (decomposição N0→N3 com origem de cada dedução, rótulo de confiança e lacunas acionáveis), **Operação** e **Pendências**. Seletor de canal obrigatório, sem opção "todos" (decisão 0021) — precisou de `GET /api/canais`, senão a tela pediria UUID digitado à mão. Microcopy vindo literalmente do guia de marca. |
| 2026-08-12 | Auditoria de segurança (Fase 3) | **Nenhum vazamento.** A auditora verificou uma a uma as quatro afirmações da decisão 0024 sobre a fresta de login e confirmou todas contra o SQL e contra o `IsolamentoLoginTest`. Confirmou também que o `X-Tenant-Id` morreu de fato (nenhum `getHeader` no código de produção) e que nenhum `GET` altera estado — a condição que sustenta o CSRF desligado. Dois achados MÉDIOS corrigidos, ambos de disponibilidade no login. |
| 2026-08-12 | Revisão de código (Fase 3) | Confirmou que o frontend segue o guia de marca **de fato**, não decorativamente (comparou palavra por palavra). Achado principal: o comentário do parser JSON afirmava que o backend não serializava `BigDecimal` como string — já não era verdade. Resolvido removendo o parser (decisão 0026) e cobrindo a formatação com teste. |
| 2026-08-12 | Auditoria de segurança (Fase 2) | **Nenhum vazamento entre tenants.** As 3 queries novas são JPQL sobre entidades com `@TenantId`, então recebem o predicado automático. `canalId` vindo do usuário não é vetor: o predicado de tenant é aplicado **antes** do filtro por canal, e canal alheio devolve vazio — indistinguível de "canal vazio no período", sem oráculo de enumeração. Corrigidos: `GRANT UPDATE` largo demais em `taxa_canal` (agora por coluna) e o alerta de repasse que era calculado e descartado. |
| 2026-08-12 | Revisão de código (Fase 2) | Aprovou o pacote (granularidade adequada, regra do teto sem duplicação, `Rateio` fiel ao §6.3). Achado principal: **nenhuma margem sai `CALCULADA`** porque ninguém grava `custo(MERCADORIA)` — registrado acima como limitação nº 1, não como bug. |
| 2026-08-12 | Teste de isolamento do motor | `IsolamentoMargemTest` (7 casos) fecha o achado ALTO da auditoria: a regra 1 do CLAUDE.md exige teste de isolamento por query nova. **116 casos de teste no projeto.** O agente verificou minha premissa em vez de repeti-la e descobriu que a sabotagem que sugeri (`nativeQuery = true`) **não** derrubaria o teste sozinha — o RLS, camada independente, continuaria filtrando. Está documentado com a cadeia real de sabotagem. |
| 2026-08-12 | Auditoria de segurança (Fase 1) | `revisor-seguranca` **não achou vazamento entre tenants** nas 12 tabelas novas. Corrigido o achado MÉDIO: a ingestão agora valida o canal contra o tenant **antes** de qualquer escrita, com exceção própria, em vez de depender da FK composta estourar no meio do INSERT. Corrigido também vazamento de conteúdo de payload em mensagem de exceção. |
| 2026-08-12 | Revisão de código (Fase 1) | `revisor-codigo` aprovou o padrão geral (12 entidades uniformes, `SuporteJson` como abstração certa, nenhuma abstração prematura entre os adaptadores). Apontou 2 comportamentos críticos sem teste — ambos cobertos agora. Dívida registrada na seção acima. |
| 2026-08-12 | Testes de isolamento da Fase 1 | Feito. `IsolamentoFaseUmTest` (comportamental em `canal`, `pedido`, `cliente`), `ChaveCompostaImpedeReferenciaCruzadaTest` (prova que a decisão 0015 não é só comentário: FK cruzada entre tenants é recusada com SQLSTATE 23503) e 3 casos novos no `ServicoIngestaoTest` (canal de outro tenant, dedup de cliente, backstop de corrida). 63 casos de teste no total. **Nenhum executado.** |
| 2026-08-12 | Auditoria de segurança (Fase 0) | `revisor-seguranca` não achou vazamento entre tenants. Corrigidos: injeção via `VERSAO` no `migrate-undo`, pool Hikari injetável (`autowireCandidate = false`), portas em `0.0.0.0`, `server.error.*` exposto, allowlist do actuator. Registrada a decisão 0012 (LGPD/`executado_por`). |
