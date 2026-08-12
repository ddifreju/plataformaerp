# 0001 — Stack e arquitetura base

**Data:** agosto de 2026
**Status:** aceita

## Contexto

Fundadora solo, engenheira de backend com domínio em Java/Spring e
Kubernetes, ~20h/semana disponíveis.

## Decisão

- Backend em Java 21 + Spring Boot 3
- Integrações via Apache Camel
- PostgreSQL 16 + pgvector, com Row Level Security
- Frontend em Next.js + React + Tailwind
- Docker Compose em dev, VPS em produção inicial

## Justificativa

A vantagem competitiva da fundadora é velocidade de execução em backend de
alto volume, não aprender stack nova. Construir no que ela domina é o que
torna 20h/semana viável.

pgvector no mesmo Postgres evita ter dois sistemas para isolar e dá
transações e backup de graça. Se escalar além, a camada de recuperação está
atrás de interface e a troca é contida.

Kubernetes fica de fora até existir o problema que ele resolve.

## Consequências

- Menos ferramentas para manter
- Contratação futura mais fácil (Java e React são commodities)
- Se precisar de serving de modelo customizado, vira serviço separado
  falando por API
