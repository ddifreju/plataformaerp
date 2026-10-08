# Acessos e infraestrutura

Aqui ficam onde está cada coisa e como entrar, **sem senhas**. As senhas
ficam no `ACESSOS-LOCAL.md`, que a Jéssica preenche no computador dela e o
git ignora (modelo em `ACESSOS-LOCAL.exemplo.md`).

## Mapa

```
Navegador ──► Vercel (frontend Next.js)  https://plataformaerp.vercel.app/radar
                 │  reescreve /api/* para BACKEND_URL
                 ▼
             Render (backend Spring)     https://radar-api-navega.onrender.com
                 │  JDBC
                 ▼
             Supabase (PostgreSQL 16)    projeto lovciyurbpgzeaijvldk, sa-east-1 (São Paulo)
                 └─ pg_cron "acordar-radar": chama o /actuator/health do Render a cada 10 min
```

## Serviços

| Serviço | Para que serve | Endereço | Detalhes |
|---|---|---|---|
| **GitHub** | Código, PRs, Actions | https://github.com/ddifreju/plataformaerp | Branch padrão `main`; branch de trabalho `claude/radar-planning-local-setup-zb8knr`. Workflow `.github/workflows/manter-acordado.yml` (reserva do despertador). |
| **Vercel** | Hospeda o frontend | https://plataformaerp.vercel.app/radar (painel: vercel.com → projeto `plataformaerp`) | Publica sozinho a cada merge na `main`. Variável de ambiente: `BACKEND_URL=https://radar-api-navega.onrender.com`. Não dorme. A antiga tela `/login` não é a plataforma: a plataforma é `/radar`. |
| **Render** | Hospeda o backend | https://radar-api-navega.onrender.com (painel: dashboard.render.com → serviço `radar-api-navega`) | Plano **gratuito**: dorme depois de 15 min sem acesso e leva uns 3 min para acordar. Publica sozinho a cada merge na `main`. Variáveis: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `APP_CORS_ORIGENS`, `SESSAO_COOKIE_SECURE`. Cada carga da tela leva de 7 a 9 s, porque o servidor fica nos EUA e o banco em São Paulo (melhoria oferecida e não aprovada: juntar as consultas). |
| **Supabase** | Banco PostgreSQL | supabase.com → projeto `lovciyurbpgzeaijvldk` (região sa-east-1) | Migrations são aplicadas **antes do merge** (pelo MCP do Supabase na nuvem; na sessão local, veja `06-como-rodar-e-publicar.md`). Já aplicadas: até a **V032**. O plano gratuito pausa depois de 7 dias sem uso; o despertador também mantém o banco ativo. A senha do papel `app_aplicacao` é só de demonstração. |
| **Despertador** | Mantém o Render acordado | Supabase `pg_cron` job `acordar-radar` (a cada 10 min) + GitHub Actions como reserva | Conferir: `select * from net._http_response order by created desc limit 5;` Desligar: `select cron.unschedule('acordar-radar');`. Detalhes em `docs/infra/despertador.md`. |
| **Oracle Cloud** | Era o plano para hospedagem gratuita mais rápida | — | **O cadastro foi recusado 3 vezes (com 2 e-mails).** Ficou o pacote pronto em `infra/oracle/` e `docs/infra/oracle-passo-a-passo.md`, caso dê certo um dia. Magalu Cloud foi cogitada e não usada. |
| **Bling / Mercado Livre / Shopee / TikTok / SHEIN** | Integrações futuras | — | Pausadas até ter **CNPJ** (lembrete marcado para 09/10/2026). Plano em `docs/integracoes/plano-conexoes.md`. Chaves de marketplace **nunca no chat**: vão nas variáveis do Render. |

## Logins

| Onde | Login | Senha |
|---|---|---|
| Radar (demonstração, pode compartilhar) | `dono@demo.plataforma` | `demo1234` |
| Radar, outros perfis de demonstração | `gestor@`, `analista@`, `financeiro@`, `estoque@`, `atendimento@`, `marketing@` `demo.plataforma` | `demo1234` (definidos em `infra/dados-demo.sql`) |
| GitHub, Vercel, Render, Supabase | Conta da Jéssica | No `ACESSOS-LOCAL.md` |

## Como a sessão local acessa cada serviço

- **GitHub:** git com a conta da Jéssica (`gh auth login` ou credencial do
  git). Na nuvem a sessão usava as ferramentas `mcp__github__*`; na local, o
  `gh` CLI faz o mesmo (PR: `gh pr create`; merge: `gh pr merge --squash`).
- **Supabase:** três caminhos.
  - O MCP do Supabase, se a Jéssica conectar no Claude local.
  - O SQL Editor no painel, colando o SQL da migration.
  - `psql` com a connection string do painel (Project Settings → Database).
    A connection string é segredo e vai no `ACESSOS-LOCAL.md`.
- **Vercel e Render:** publicam sozinhos a partir da `main`. Para ver logs ou
  mudar variáveis, use o painel; a Jéssica entra e você orienta. A Vercel
  também tem MCP, se ela conectar.
- **Conferir que publicou** (sem navegador):
  - Backend: faça login e veja se a resposta de `GET /api/radar` tem o campo
    novo.
  - Frontend: procure um texto novo nos arquivos
    `/_next/static/chunks/*.js`. Há exemplos em `06-como-rodar-e-publicar.md`.
