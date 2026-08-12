# Ponto de entrada único do projeto.
# Rode `make` ou `make ajuda` para ver os comandos disponíveis.
#
# Windows: use o Git Bash. Se `make` não existir, instale com:
#     winget install ezwinports.make
# Todo comando abaixo é uma linha de shell comum e pode ser copiado e
# colado à mão se preferir não instalar o make.

COMPOSE := docker compose -f infra/docker-compose.yml --env-file infra/.env

# O Flyway e a aplicação leem credenciais de variável de ambiente
# (FLYWAY_URL, SPRING_DATASOURCE_*). Elas moram em infra/.env, que o docker
# compose lê sozinho mas o Maven não — por isso exportamos aqui antes de
# chamar o mvn. `set -a` faz todo assignment virar export automaticamente.
CARREGA_ENV := set -a && . ./infra/.env && set +a
MAVEN       := $(CARREGA_ENV) && cd backend && mvn

.DEFAULT_GOAL := ajuda

# ---------------------------------------------------------------- ambiente --

.PHONY: dev
dev: infra/.env ## Sobe o ambiente local (Postgres 16 + pgvector)
	$(COMPOSE) up -d
	@echo ""
	@echo "Postgres no ar. Rode 'make migrate' para aplicar o schema."

.PHONY: dev-ferramentas
dev-ferramentas: infra/.env ## Sobe o ambiente local + Adminer (visualizador de banco)
	$(COMPOSE) --profile ferramentas up -d
	@echo "Adminer: http://localhost:8081"

.PHONY: parar
parar: ## Para os containers, preservando os dados
	$(COMPOSE) stop

.PHONY: logs
logs: ## Acompanha os logs do ambiente
	$(COMPOSE) logs -f

.PHONY: psql
psql: ## Abre um psql no banco de desenvolvimento
	$(COMPOSE) exec postgres psql -U $${POSTGRES_USER:-plataforma} -d $${POSTGRES_DB:-plataforma}

# Cria o .env na primeira execução em vez de falhar com erro obscuro.
infra/.env:
	@echo "infra/.env não existe. Criando a partir de infra/.env.exemplo."
	@cp infra/.env.exemplo infra/.env
	@echo "Revise infra/.env antes de seguir."

# --------------------------------------------------------------- migrations --

.PHONY: migrate
migrate: ## Aplica as migrations pendentes (Flyway)
	$(MAVEN) flyway:migrate

.PHONY: migrate-info
migrate-info: ## Mostra quais migrations já foram aplicadas
	$(MAVEN) flyway:info

.PHONY: definir-senha-app
definir-senha-app: ## Define a senha do papel app_aplicacao a partir de APP_DB_PASSWORD
	@# A V004 cria app_aplicacao sem senha de propósito: segredo não entra
	@# em migration versionada. Este alvo fecha essa lacuna lendo a senha do
	@# ambiente. Rode uma vez, depois do primeiro `make migrate`.
	@$(CARREGA_ENV) && \
	  test -n "$$APP_DB_PASSWORD" || { echo "APP_DB_PASSWORD não definida em infra/.env"; exit 1; }
	@$(CARREGA_ENV) && $(COMPOSE) exec -T postgres \
	  psql -v ON_ERROR_STOP=1 -U "$$POSTGRES_USER" -d "$$POSTGRES_DB" \
	  -v senha="$$APP_DB_PASSWORD" \
	  -c "ALTER ROLE app_aplicacao WITH PASSWORD :'senha';"
	@echo "Senha de app_aplicacao definida."
	@echo "Confirme que SPRING_DATASOURCE_PASSWORD tem o mesmo valor."

.PHONY: preparar
preparar: dev migrate definir-senha-app ## Primeira execução: sobe, migra e define a senha do app
	@echo ""
	@echo "Ambiente pronto. Rode 'make test' para validar o isolamento."

.PHONY: migrate-undo
migrate-undo: ## Desfaz UMA migration. Uso: make migrate-undo VERSAO=003
ifndef VERSAO
	$(error Informe a versão. Exemplo: make migrate-undo VERSAO=003)
endif
	@echo "Desfazendo a migration $(VERSAO)."
	@echo "ATENÇÃO: undo restaura o SCHEMA, não os DADOS já apagados."
	$(COMPOSE) exec -T postgres sh -c \
	  'psql -v ON_ERROR_STOP=1 -U $$POSTGRES_USER -d $$POSTGRES_DB \
	   -f "$$(ls /migrations/undo/U$(VERSAO)__*.sql)"'
	@echo "Removendo a linha da flyway_schema_history para permitir reaplicar."
	$(COMPOSE) exec -T postgres sh -c \
	  'psql -v ON_ERROR_STOP=1 -U $$POSTGRES_USER -d $$POSTGRES_DB \
	   -c "DELETE FROM flyway_schema_history WHERE version = '"'"'$(VERSAO)'"'"';"'

# -------------------------------------------------------------------- teste --

.PHONY: test
test: ## Roda a suíte completa (exige Docker no ar, por causa do Testcontainers)
	$(MAVEN) test

.PHONY: test-isolamento
test-isolamento: ## Roda só os testes de isolamento entre tenants
	$(MAVEN) test -Dtest='*Isolamento*'

.PHONY: build
build: ## Compila e empacota o backend
	$(MAVEN) package

# --------------------------------------------------------------- limpeza ----

.PHONY: limpar
limpar: ## Remove containers e artefatos de build (PRESERVA os dados do banco)
	$(COMPOSE) down --remove-orphans
	$(MAVEN) clean

.PHONY: limpar-tudo
limpar-tudo: ## Remove TUDO, inclusive o volume do banco. Apaga os dados locais.
	@echo "Isto APAGA os dados do banco local. Ctrl-C em 5 segundos para cancelar."
	@sleep 5
	$(COMPOSE) down -v --remove-orphans
	$(MAVEN) clean

# ---------------------------------------------------------------- ajuda -----

.PHONY: ajuda
ajuda: ## Mostra esta lista
	@echo "Comandos disponíveis:"
	@echo ""
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
	  | sort \
	  | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-18s\033[0m %s\n", $$1, $$2}'
	@echo ""
