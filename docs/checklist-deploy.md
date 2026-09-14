# Checklist de deploy

Fase 4, bloco B (tarefas 27-30, decisão 0032). Lista curta e conferível do
que fazer quando a VPS existir. Não é um tutorial genérico de Docker — só
o que é específico deste projeto. Cada item tem uma caixa; marque ao
concluir.

## Já está pronto (não precisa refazer)

- [x] `backend/Dockerfile` — multi-stage (JDK para build, JRE para
      rodar), usuário sem privilégio, `HEALTHCHECK` em `/actuator/health`
- [x] `frontend/Dockerfile` — multi-stage, modo `standalone`, usuário sem
      privilégio, sem URL de backend embutida (chama `/api/...` relativo)
- [x] `infra/docker-compose.staging.yml` + `infra/Caddyfile` — provam,
      numa máquina sem VPS, que frontend e backend funcionam atrás de
      proxy reverso numa origem só, com TLS (`tls internal`)
- [x] Perfil Spring `staging` (`application-staging.yml`) — cookie de
      sessão `Secure=true`, CORS vazio por padrão
- [x] Validação de ambiente no boot (`ValidadorDeAmbiente`) — a
      aplicação recusa subir fora do perfil `dev` sem
      `SPRING_DATASOURCE_PASSWORD` e `APP_DOCUMENTO_HMAC_CHAVE`

O que ainda depende de Docker de verdade para ser validado (não foi
possível nesta sessão — ver relato da tarefa): as imagens efetivamente
constroem e o container sobe; o `Set-Cookie` sai com `Secure`/`HttpOnly`/
`SameSite=Lax` no fio (via `make staging-conferir-cookie`).

## Só a fundadora pode fazer

- [ ] **Registrar o domínio** da plataforma
- [ ] **Criar a VPS** (provedor, tamanho, região) e anotar o IP
- [ ] **Apontar o DNS** do domínio para o IP da VPS (registro A/AAAA)
- [ ] **Gerar `APP_DOCUMENTO_HMAC_CHAVE` de produção** — string aleatória
      longa (por exemplo `openssl rand -base64 48`), diferente da usada em
      staging/teste. Guardar num gerenciador de senha, **fora do git e
      fora do banco**. Trocar depois invalida todo `documento_hash` já
      gravado (docs/PENDENCIAS.md) — gerar uma vez, com cuidado
- [ ] **Gerar as senhas de produção** do Postgres (dono do schema) e do
      papel `app_aplicacao`, e guardar do mesmo jeito
- [ ] **Primeiro deploy** — subir o compose na VPS pela primeira vez

## Passo a passo, quando a VPS existir

1. Instalar Docker Engine + Docker Compose na VPS (só isso, nenhuma outra
   dependência — as imagens já trazem tudo que precisam).
2. Copiar `infra/docker-compose.staging.yml` para a VPS. Trocar `tls
   internal` no `infra/Caddyfile` pelo domínio real (uma linha — decisão
   0032: `tls internal` vira o endereço `seudominio.com.br`, sem mais
   nada; o Caddy busca o certificado Let's Encrypt sozinho). Abrir as
   portas 80 e 443 no firewall da VPS.
3. Criar `infra/.env.staging` (ou um `.env` de produção equivalente) na
   VPS a partir de `infra/.env.staging.exemplo`, com os valores reais
   gerados no passo anterior. **Nunca commitar este arquivo.**
4. **Aplicar as migrations ANTES de subir o backend** (decisão 0011: o
   boot nunca roda Flyway sozinho). Como o Postgres deste compose não
   publica porta no host de propósito, rode o Flyway de dentro da mesma
   rede do compose, por exemplo:
   ```
   docker compose -f infra/docker-compose.staging.yml --env-file infra/.env.staging \
     run --rm -v "$PWD/../backend:/workspace" -w /workspace \
     --network plataforma-ecommerce-staging_default \
     eclipse-temurin:21-jdk-jammy \
     ./mvnw -B flyway:migrate \
       -Dflyway.url=jdbc:postgresql://postgres:5432/$POSTGRES_DB \
       -Dflyway.user=$POSTGRES_USER -Dflyway.password=$POSTGRES_PASSWORD
   ```
   Confira o nome real da rede com `docker network ls` antes (o compose
   gera o nome a partir do `name:` do arquivo).
5. `make staging-subir` (ou `docker compose ... up -d --build`
   diretamente na VPS).
6. Definir a senha do papel `app_aplicacao` no banco de produção (mesma
   ideia do `make definir-senha-app` do ambiente de dev, contra este
   outro Postgres).
7. `make staging-conferir-cookie` contra o domínio real — confirmar
   `Secure`, `HttpOnly` e `SameSite=Lax` no `Set-Cookie` que sai do
   Caddy, não só ler a configuração (decisão 0032: "a verificação é feita
   contra o `Set-Cookie` que sai do Caddy, não por leitura de arquivo").
8. Testar login de ponta a ponta pelo domínio real, num navegador comum
   (não só `curl`).

## Fora do escopo deste bloco (pendências futuras, não inventar agora)

- Backup e restauração do Postgres de produção
- Monitoramento e alertas
- Rotação/retenção de log
- Pipeline de CI/CD (hoje o deploy é manual, por decisão implícita do
  tamanho do time — ver CLAUDE.md, "fundadora solo, ~20h/semana")
