# syntax=docker/dockerfile:1
#
# Imagem do backend Spring Boot (Fase 4, bloco B - tarefa 27).
# Build context esperado: o diretorio "backend/" (nao a raiz do repo) -
# ver "build.context" em infra/docker-compose.staging.yml.
#
# O QUE ESTA IMAGEM ESPERA DO AMBIENTE em tempo de execucao (nenhum
# segredo embutido - CLAUDE.md, "nenhum segredo em codigo"; ver
# application.yml e application-staging.yml para onde cada uma entra):
#
#   SPRING_PROFILES_ACTIVE     "staging" (ou outro perfil fora de "dev")
#   SPRING_DATASOURCE_URL      jdbc:postgresql://host:5432/banco
#   SPRING_DATASOURCE_USERNAME normalmente "app_aplicacao" (ver V004)
#   SPRING_DATASOURCE_PASSWORD obrigatoria fora de "dev" - o boot recusa
#                              subir sem ela (tarefa 30, ValidadorDeAmbiente)
#   APP_DOCUMENTO_HMAC_CHAVE   obrigatoria fora de "dev", mesmo motivo
#                              (ver docs/PENDENCIAS.md)
#   APP_CORS_ORIGENS           normalmente VAZIA fora de dev (decisao 0025/0032)
#   SERVER_PORT                opcional, default 8080 (troque tambem o
#                              HEALTHCHECK abaixo se mudar isto)
#
# Migration NAO roda no boot desta imagem (decisao 0011, spring.flyway.enabled=false
# em application.yml): aplique com "make migrate" (ou o equivalente de
# staging, documentado em docs/checklist-deploy.md) ANTES de subir um
# container novo contra um schema desatualizado.

# ---------------------------------------------------------------- build --
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build

# Camada de dependencia isolada: copiar so o pom.xml primeiro faz o
# Docker reaproveitar esta camada (e pular o download inteiro do
# repositorio Maven) toda vez que so o CODIGO muda, sem o pom mudar
# junto.
COPY mvnw ./mvnw
COPY .mvn/ ./.mvn/
COPY pom.xml ./pom.xml
RUN chmod +x ./mvnw \
    && ./mvnw -B -ntp -DskipTests dependency:go-offline

COPY src ./src
# Sem "-o" (offline) de proposito: "dependency:go-offline" cobre bem a
# maioria dos casos, mas nao e garantia formal de 100% das dependencias
# de plugin - forcar offline aqui trocaria "camada de cache bem pensada"
# por "build que quebra se o go-offline esqueceu alguma coisa". O ganho
# de cache já vem do COPY do pom.xml antes do COPY do src, acima.
RUN ./mvnw -B -ntp -DskipTests package

# -------------------------------------------------------------- runtime --
# JRE, nao JDK: a imagem final so RODA o jar, nunca compila nada.
FROM eclipse-temurin:21-jre-alpine AS runtime

# Usuario sem privilegio, UID/GID explicitos (nao root, e nao depende de
# um usuario "generico" que a imagem base tenha por acaso).
RUN addgroup -g 10001 aplicacao \
    && adduser -D -H -u 10001 -G aplicacao -s /sbin/nologin aplicacao

WORKDIR /app
COPY --from=build /build/target/plataforma-backend.jar ./aplicacao.jar
RUN chown aplicacao:aplicacao /app/aplicacao.jar

USER aplicacao:aplicacao

EXPOSE 8080

# "wget --spider" só confere o status HTTP (sem baixar corpo) - suficiente
# para "o processo esta de pe e o Spring respondeu". Usa wget (nao curl)
# porque o busybox do Alpine ja traz ele, sem instalar pacote extra so
# para isto. "actuator/health" e rota publica (ConfiguracaoSeguranca) de
# proposito, para o healthcheck nao precisar de credencial.
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "aplicacao.jar"]
