# =====================================================================
# Stage 1 — build (Maven + JDK 21)
# =====================================================================
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# Cache de dependências: copia só o pom primeiro. Enquanto o pom não mudar,
# esta camada é reaproveitada e o build não baixa a internet de novo.
COPY pom.xml ./
RUN mvn -B -e -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -e -ntp -DskipTests -Djacoco.skip=true package

# =====================================================================
# Stage 2 — runtime (só JRE, sem Maven nem JDK)
# =====================================================================
FROM eclipse-temurin:21-jre-alpine

ENV LANG=C.UTF-8 \
    JAVA_OPTS="" \
    QUARKUS_HTTP_HOST=0.0.0.0

WORKDIR /app

# Layout fast-jar do Quarkus: dependências em camada separada do código.
COPY --from=build /workspace/target/quarkus-app/lib/      /app/lib/
COPY --from=build /workspace/target/quarkus-app/*.jar     /app/
COPY --from=build /workspace/target/quarkus-app/app/      /app/app/
COPY --from=build /workspace/target/quarkus-app/quarkus/  /app/quarkus/

# Usuário não-root com UID numérico: o Kubernetes só valida 'runAsNonRoot'
# quando o USER da imagem é numérico.
RUN addgroup -S -g 1001 app && adduser -S -u 1001 -G app app && chown -R app:app /app
USER 1001

EXPOSE 8080

# Readiness, não liveness: no docker-compose os outros containers esperam este
# ficar "healthy", e pronto aqui significa banco e RabbitMQ alcançáveis.
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=6 \
  CMD wget -qO- http://127.0.0.1:8080/q/health/ready | grep -q '"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/quarkus-run.jar"]
