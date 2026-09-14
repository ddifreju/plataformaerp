# syntax=docker/dockerfile:1
#
# Imagem do frontend Next.js, modo standalone (Fase 4, bloco B - tarefa 28).
# Build context esperado: o diretorio "frontend/" (nao a raiz do repo) -
# ver "build.context" em infra/docker-compose.staging.yml.
#
# ESTA IMAGEM NAO EMBUTE URL DE BACKEND. Em staging/produção o navegador
# fala só com a própria origem (`/api/...` relativo, ver
# frontend/src/lib/api/cliente.ts) e é o Caddy (infra/Caddyfile) quem
# decide para onde a requisição vai (decisão 0032) - não há CORS, não há
# "endereço do backend" para configurar aqui.
#
# Variável de ambiente esperada em runtime:
#   PORT   opcional, default 3000 (o server.js do modo standalone lê
#          sozinho, sem precisar de código nosso)

# ---------------------------------------------------------------- deps --
FROM node:20-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

# --------------------------------------------------------------- build --
FROM node:20-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
# BACKEND_URL não é lido em build nem em runtime de produção - só existe
# para `next dev` local (ver next.config.ts). Não passamos nada aqui de
# propósito, para não criar a falsa impressão de que esta imagem precisa
# saber o endereço do backend.
RUN npm run build

# ------------------------------------------------------------- runtime --
FROM node:20-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production

# Usuário sem privilégio, UID/GID explícitos (não root).
RUN addgroup -g 10001 aplicacao \
    && adduser -D -H -u 10001 -G aplicacao aplicacao

# O modo "standalone" (next.config.ts: output: "standalone") já traz um
# server.js e só os node_modules que o runtime de fato usa - não precisa
# copiar o node_modules inteiro nem rodar "npm install" de novo aqui.
# ".next/static" e "public" ficam de fora do standalone por padrão e
# precisam ser copiados à parte (é o próprio Next que documenta isto).
COPY --from=build --chown=aplicacao:aplicacao /app/.next/standalone ./
COPY --from=build --chown=aplicacao:aplicacao /app/.next/static ./.next/static
COPY --from=build --chown=aplicacao:aplicacao /app/public ./public

USER aplicacao:aplicacao

EXPOSE 3000
ENTRYPOINT ["node", "server.js"]
