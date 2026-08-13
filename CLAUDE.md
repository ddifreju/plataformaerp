# Plataforma [NOME A DEFINIR]

Sistema unificado de gestão para e-commerce brasileiro.
Multi-tenant, multi-canal, com camada de IA sobre dados operacionais.

## Contexto

Fundadora solo, ~20h/semana, sem time. Isso define as prioridades:
legibilidade acima de elegância, simplicidade acima de otimização,
manutenibilidade acima de tudo.

## Stack

- Backend: Java 21 + Spring Boot 3
- Integrações: Apache Camel
- Banco: PostgreSQL 16 + pgvector, com Row Level Security
- Frontend: Next.js 16 + React 19 + Tailwind (ver decisão 0022)
- Infra: Docker Compose (dev), VPS (produção inicial)

## Regras inegociáveis

### 1. Tenant é identidade, não filtro
Resolvido no gateway, propagado por contexto, predicado obrigatório em
toda query, RLS no banco como segunda camada. Todo PR que toca query
precisa de teste de isolamento.

### 2. Dinheiro nunca é float
Sempre BigDecimal no Java, NUMERIC no Postgres. Toda operação monetária
declara escala e RoundingMode explicitamente.

### 3. Toda resposta numérica é rastreável
Guarde a query executada e os IDs retornados. Se o cliente contestar um
número, precisamos provar ou corrigir em minutos.

### 4. Separe determinístico de não-determinístico
Roteamento, integrações, isolamento e regra de negócio: teste unitário
com asserção dura. Saída de LLM: avaliação com threshold, nunca asserção.

### 5. Nunca invente dado
Se a fonte não tem, a resposta é "não tenho esse dado". Nunca uma
estimativa apresentada como fato. Se for estimativa, diga que é.

## Convenções

- Commits em português, imperativo: "adiciona conector Mercado Livre"
- Migrations sempre reversíveis
- Nenhum segredo em código, sempre variável de ambiente
- Pacotes organizados por domínio, não por camada
- Sem lógica de negócio em controller

## Comandos

- `make dev` — sobe ambiente local
- `make test` — suíte completa
- `make migrate` — aplica migrations

## Quando parar e perguntar

- A demanda contradiz uma decisão em docs/decisoes/
- O escopo cresceu além do que foi pedido
- Existe caminho mais simples que resolve 80% do problema
- A decisão é de negócio, não técnica

## Observação de sessão

No início de toda sessão orientada a tarefa (qualquer trabalho com
ferramentas que produza entregável), invoque a skill `task-observer` e
mantenha o log de observações que ela define. A descrição da skill
sozinha não dispara de forma confiável — esta instrução existe por isso.
