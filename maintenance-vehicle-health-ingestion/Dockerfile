# ── Stage 1: build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY contracts/schemas /workspace/contracts/schemas
COPY maintenance-vehicle-health-ingestion/pom.xml /workspace/maintenance-vehicle-health-ingestion/pom.xml
COPY maintenance-vehicle-health-ingestion/src     /workspace/maintenance-vehicle-health-ingestion/src

WORKDIR /workspace/maintenance-vehicle-health-ingestion
RUN apk add --no-cache maven && mvn --batch-mode --no-transfer-progress -DskipTests package

# ── Stage 2: runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/maintenance-vehicle-health-ingestion/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
