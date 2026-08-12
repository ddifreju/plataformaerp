# 0004 — Monorepo com pastas por artefato

**Data:** 12 de agosto de 2026
**Status:** aceita
**Reversibilidade:** [REVERSÍVEL]

## Contexto

O sistema tem backend Java, frontend Next.js, infraestrutura de dev e
documentação. Fundadora solo, ~20h/semana. Precisa decidir se isso vive em um
repositório ou em vários.

## Decisão

Um único repositório com quatro pastas de primeiro nível:

```
backend/    Java 21 + Spring Boot 3 (Maven)
frontend/   Next.js 15 + React + Tailwind
infra/      Docker Compose, scripts de provisionamento local
docs/       decisões, estado, pendências, marca
Makefile    ponto de entrada único (dev, test, migrate)
```

Sem ferramenta de monorepo (Nx, Turborepo, Bazel). A coordenação entre
backend e frontend é feita pelo Makefile na raiz.

## Alternativas consideradas

- **Repositórios separados (polyrepo).** Descartada: para uma pessoa só, o
  custo de sincronizar versão de contrato entre dois repositórios e abrir dois
  PRs para uma mudança de ponta a ponta é puro atrito. Polyrepo resolve
  autonomia de times — não existem times aqui.
- **Turborepo/Nx.** Descartada agora: são otimizadores de build para muitos
  pacotes JS. Aqui há um app JS e um app Java, que o Turborepo não orquestra
  bem. Adiciona configuração para resolver um problema que ainda não existe.
- **Backend e frontend na raiz sem pasta `infra/`.** Descartada: os arquivos de
  Compose e SQL de provisionamento não pertencem a nenhum dos dois e ficariam
  espalhados.

## Consequências

- Uma mudança de ponta a ponta é um commit só
- CI futura precisa filtrar por pasta para não rodar tudo a cada commit
  (problema para quando existir CI, não agora)
- Se um dia backend e frontend tiverem donos diferentes, dá para extrair —
  por isso a decisão é reversível
