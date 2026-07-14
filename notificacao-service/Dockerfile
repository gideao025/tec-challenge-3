# Build a partir da RAIZ do repositorio (o reactor Maven precisa do pom pai):
#   docker build -f notificacao-service/Dockerfile .
# O docker-compose.yml ja faz isso via context: . / dockerfile: notificacao-service/Dockerfile

# ---------- Estagio 1: build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Camada de dependencias separada do codigo: rebuild de src nao rebaixa o Maven Central.
COPY pom.xml .
COPY agendamento-service/pom.xml agendamento-service/
COPY notificacao-service/pom.xml notificacao-service/
RUN mvn -B -pl notificacao-service -am dependency:go-offline

COPY notificacao-service/src notificacao-service/src
RUN mvn -B -pl notificacao-service -am clean package -DskipTests

# ---------- Estagio 2: runtime ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S hospital && adduser -S hospital -G hospital
USER hospital

COPY --from=build --chown=hospital:hospital \
     /build/notificacao-service/target/notificacao-service-*.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
