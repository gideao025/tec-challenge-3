# Build a partir da RAIZ do repositorio (o reactor Maven precisa do pom pai):
#   docker build -f agendamento-service/Dockerfile .
# O docker-compose.yml ja faz isso via context: . / dockerfile: agendamento-service/Dockerfile

# ---------- Estagio 1: build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Os poms vem primeiro e sozinhos: enquanto as dependencias nao mudarem, esta camada
# e reaproveitada do cache e o build nao volta a baixar o Maven Central inteiro.
COPY pom.xml .
COPY agendamento-service/pom.xml agendamento-service/
COPY notificacao-service/pom.xml notificacao-service/
RUN mvn -B -pl agendamento-service -am dependency:go-offline

COPY agendamento-service/src agendamento-service/src
RUN mvn -B -pl agendamento-service -am clean package -DskipTests

# ---------- Estagio 2: runtime ----------
# So o JRE e o jar chegam na imagem final: nem Maven, nem codigo-fonte, nem .m2.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Processo sem privilegios de root.
RUN addgroup -S hospital && adduser -S hospital -G hospital
USER hospital

COPY --from=build --chown=hospital:hospital \
     /build/agendamento-service/target/agendamento-service-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
