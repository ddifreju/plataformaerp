# Estado do Projeto

**Atualizado em:** 12 de agosto de 2026
**Fase:** 0 — Fundação
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
7. [ ] Modelo canônico: tenant, canal, produto, variação, pedido, item
8. [ ] Modelo canônico: custo, devolução, conversa, mensagem, cliente
9. [ ] Migrations reversíveis de tudo acima
10. [ ] Adaptador Mercado Livre contra fixture (mock, sem credencial)
11. [ ] Adaptador de ERP contra fixture
12. [ ] Pipeline de ingestão com idempotência

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

## Decisões tomadas

Ver `docs/decisoes/`.

## Log de execução

| Data | Tarefa | Resultado |
|---|---|---|
| | | |
