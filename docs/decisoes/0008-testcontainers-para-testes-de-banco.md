# 0008 — Testcontainers para todo teste que toca banco

**Data:** 12 de agosto de 2026
**Status:** aceita
**Reversibilidade:** [REVERSÍVEL]

## Contexto

O isolamento entre tenants depende de Row Level Security, que é um recurso do
Postgres. Precisa decidir contra o que os testes rodam.

## Decisão

**Testcontainers** com a imagem `pgvector/pgvector:pg16` — o mesmo Postgres e a
mesma extensão que rodam em dev e em produção. As migrations do Flyway são
aplicadas no container antes dos testes.

Um container reaproveitado por toda a suíte (singleton), não um por classe.

## Alternativas consideradas

- **H2 em modo Postgres.** Descartada, e não é perto. H2 não tem Row Level
  Security, não tem pgvector, e trata `NUMERIC` de forma diferente. Um teste de
  isolamento contra H2 testaria o nada e passaria uma falsa sensação de
  segurança — o pior resultado possível para este teste específico.
- **Postgres local instalado.** Descartada: exige setup manual, e o estado suja
  entre execuções.
- **Mock do repositório.** Descartada para este caso: mock de repositório não
  pode provar que uma policy do banco funciona. Serve para lógica de domínio,
  não para isolamento.

## Consequências

- **Rodar os testes exige Docker em execução.** É o custo aceito em troca de o
  teste de isolamento significar alguma coisa
- Primeira execução baixa a imagem (lenta); as seguintes usam cache
- Testes de lógica pura (cálculo de margem, por exemplo) continuam sendo testes
  unitários sem container, e devem ser a maioria
