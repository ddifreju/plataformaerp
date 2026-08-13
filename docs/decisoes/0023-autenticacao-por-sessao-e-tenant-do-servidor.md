# 0023 — Sessão no servidor, e o tenant sai do usuário autenticado

**Data:** 12 de agosto de 2026
**Status:** aceita
**Reversibilidade:** [DIFÍCIL DE REVERTER]

Fecha a dívida mais antiga do projeto: o `// PROVISÓRIO` do `FiltroTenant`,
aberto na decisão 0007.

## Contexto

Desde a Fase 0, o tenant vem do header `X-Tenant-Id`, sem autenticação nenhuma.
A decisão 0007 registrou que isso é aceitável **apenas** enquanto não existe
sessão: qualquer chamador pode "ser" qualquer tenant trocando um header.

A tarefa 17 cria a sessão. No minuto em que ela existir, o header vira vetor
trivial de escalação horizontal entre clientes.

## Decisão

**Spring Security com sessão no servidor (cookie de sessão), não JWT.**

- Senha com **BCrypt** (custo 12). Nunca reversível, nunca em log.
- Tabela `usuario` com `tenant_id`: **o usuário pertence a um tenant.**
- **O tenant passa a sair do usuário autenticado.** O header `X-Tenant-Id` deixa
  de ser aceito — removido, não desativado por configuração.
- Cookie `HttpOnly`, `SameSite=Lax`, `Secure` em produção (por variável de
  ambiente, já que dev é HTTP).
- Sessão fixada é invalidada no login (`changeSessionId`), padrão do Spring
  Security — não desligar.

### A ordem dos filtros muda, e isso é o ponto delicado

Hoje o `FiltroTenant` roda em `HIGHEST_PRECEDENCE`. Com autenticação, ele
precisa rodar **depois** da cadeia do Spring Security, porque só aí existe um
usuário autenticado de quem extrair o tenant.

O que **não** muda: `ContextoTenant`, `@TenantId`, `DataSourceComTenant` e o RLS.
As quatro camadas da 0007 continuam idênticas — só a **fonte** do tenant muda,
de header para principal autenticado. É por isso que esta mudança é localizada
apesar de estrutural.

### O que continua valendo

**Login não substitui a resolução de tenant.** Autenticação responde "quem é
você"; o `FiltroTenant` responde "de qual loja". Um usuário autenticado sem
tenant resolvido continua sendo erro, e o `ContextoTenant.atual()` continua
lançando exceção em vez de devolver `null`.

## Alternativas consideradas

- **JWT stateless.** Descartada. É a escolha da moda e a errada aqui. Revogar um
  JWT exige lista de revogação — ou seja, estado no servidor, que é exatamente o
  que o JWT prometia evitar. Para uma VPS com uma instância e um punhado de
  usuários por loja, sessão em servidor é mais simples, revogável na hora
  ("desligar o acesso do funcionário que saiu" é um caso real) e não exige
  decidir tempo de expiração x janela de comprometimento.
- **OAuth/OIDC com provedor externo** (Auth0, Keycloak). Descartada por ora:
  adiciona dependência externa, custo e um sistema a mais para operar. Faz
  sentido quando houver SSO corporativo — não há.
- **Um usuário pertencer a vários tenants.** Descartada agora, e é a que tem
  mais chance de voltar: um contador que atende cinco lojas quer um login só.
  Mas modelar isso hoje significaria o tenant **não** sair direto do usuário e
  precisar de um seletor — reabrindo justamente o buraco que estamos fechando,
  com mais superfície. Quando existir a demanda, a modelagem é
  `usuario_tenant` com troca explícita de contexto e re-autenticação, não um
  parâmetro de requisição.

## Consequências

- **Toda chamada de API passa a exigir login.** Os testes que usavam
  `X-Tenant-Id` precisam autenticar — inclusive os de isolamento.
- As rotas isentas continuam sendo `allowlist` explícita
  (`/actuator/health`, `/actuator/info`), nunca `denylist`.
- Sessão em memória: reiniciar a aplicação derruba todo mundo. Aceitável para
  uma instância. Quando houver duas, entra Spring Session com armazenamento
  compartilhado — e isso **não** muda nada do modelo de tenant.
- O primeiro usuário de cada tenant é criado por provisionamento, não por
  auto-cadastro: não existe "criar conta" público, porque não existe fluxo de
  cobrança nem verificação. Registrado em `docs/PENDENCIAS.md`.
