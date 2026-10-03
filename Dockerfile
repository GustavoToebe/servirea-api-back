# syntax=docker/dockerfile:1
# Dockerfile multi-stage para o backend Servirea (seção 1.2/11 do plano
# mestre: Java 21 + Spring Boot + Docker, rodando num único VPS via Docker
# Compose — sem ECS/ECR/Kubernetes no MVP).
#
# Testado em 27/09/2026: imagem construída e subida com o profile prod
# (deploy/ do central-api-back, que tem o compose e o Caddyfile).

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN --mount=type=secret,id=gh_token,required=false \
    set -eu; \
    trap 'rm -f /root/.m2/settings.xml' EXIT; \
    if [ -s /run/secrets/gh_token ]; then \
      mkdir -p /root/.m2; umask 077; \
      printf '<settings><servers><server><id>github</id><username>GustavoToebe</username><password>%s</password></server></servers></settings>' "$(cat /run/secrets/gh_token)" > /root/.m2/settings.xml; \
    fi; \
    mvn -q -B dependency:go-offline
COPY src ./src
RUN --mount=type=secret,id=gh_token,required=false \
    set -eu; \
    trap 'rm -f /root/.m2/settings.xml' EXIT; \
    if [ -s /run/secrets/gh_token ]; then \
      mkdir -p /root/.m2; umask 077; \
      printf '<settings><servers><server><id>github</id><username>GustavoToebe</username><password>%s</password></server></servers></settings>' "$(cat /run/secrets/gh_token)" > /root/.m2/settings.xml; \
    fi; \
    mvn -q -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S servire && adduser -S servire -G servire
COPY --from=build /build/target/servire-api-*.jar app.jar
USER servire
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-jar", "app.jar"]
