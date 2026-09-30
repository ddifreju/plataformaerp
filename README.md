# Radar

[**Manual mestre, operação e plano de desenvolvimento →**](docs/radar/README.md)

A versão local atual funciona sem Docker. Veja a documentação acima para distinguir capacidades disponíveis e recursos planejados. O conteúdo abaixo preserva o histórico do projeto.

# Plataforma [NOME A DEFINIR]

Sistema unificado de gestão para e-commerce brasileiro. Multi-tenant,
multi-canal, com camada de IA sobre dados operacionais.

O problema que resolve: o lojista brasileiro não sabe seu lucro real. Os
integradores atuais mostram faturamento bruto e chamam de resultado, sem
deduzir corretamente taxa de marketplace, frete, imposto, devolução e Ads.

## Estrutura

```
backend/    Java 21 + Spring Boot 3 + Apache Camel (Maven)
frontend/   Next.js 15 + React + Tailwind  (inicializado na Fase 3)
infra/      Docker Compose do ambiente local
docs/       estado, decisões, pendências, marca
Makefile    ponto de entrada: dev, test, migrate
```

## Primeira execução

Pré-requisitos: **JDK 21**, **Maven**, **Docker Desktop** e **make**.
Nenhum deles está instalado nesta máquina ainda — ver `docs/PENDENCIAS.md`.

```bash
cp infra/.env.exemplo infra/.env   # ajuste as senhas locais
make dev                           # sobe Postgres 16 + pgvector
make migrate                       # aplica o schema
make test                          # roda a suíte (exige Docker no ar)
```

`make ajuda` lista todos os comandos.

## As regras que não se negociam

Estão em `CLAUDE.md`. As duas que mais afetam o dia a dia:

1. **Tenant é identidade, não filtro.** Resolvido no filtro de request,
   propagado por contexto, predicado automático via Hibernate `@TenantId`, e
   Row Level Security no Postgres como segunda camada. Todo código que toca
   query precisa de teste de isolamento. Ver
   `docs/decisoes/0007-propagacao-de-tenant.md`.
2. **Dinheiro nunca é float.** `BigDecimal` no Java, `NUMERIC` no Postgres,
   com escala e `RoundingMode` explícitos em toda operação.

## Onde o projeto está

`docs/ESTADO.md` é a memória entre sessões de trabalho. Comece por ele.
