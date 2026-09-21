# Dockerfile multi-stage para o backend Servire (seção 1.2/11 do plano
# mestre: Java 21 + Spring Boot + Docker, rodando num único VPS via Docker
# Compose — sem ECS/ECR/Kubernetes no MVP).
#
# NÃO TESTADO nesta sessão: o Docker deste ambiente de execução não tinha
# um daemon disponível (só o cliente), então este Dockerfile não foi
# efetivamente construído/rodado aqui — foi escrito seguindo o padrão
# multi-stage usual para Spring Boot + Maven, mas vale validar com
# `docker build .` assim que houver um Docker funcional à mão (ex.: no
# próprio VPS, ou na máquina do desenvolvedor).

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S servire && adduser -S servire -G servire
COPY --from=build /build/target/servire-api-*.jar app.jar
USER servire
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-jar", "app.jar"]
