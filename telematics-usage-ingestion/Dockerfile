# ── Stage 1: build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY contracts/schemas /workspace/contracts/schemas
COPY telematics-usage-ingestion/pom.xml /workspace/telematics-usage-ingestion/pom.xml
COPY telematics-usage-ingestion/src     /workspace/telematics-usage-ingestion/src

WORKDIR /workspace/telematics-usage-ingestion
RUN apk add --no-cache maven && mvn --batch-mode --no-transfer-progress -DskipTests package

# ── Stage 2: runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/telematics-usage-ingestion/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
