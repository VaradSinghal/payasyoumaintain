# ── Stage 1: build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY contracts/schemas /workspace/contracts/schemas
COPY claims-verification/pom.xml /workspace/claims-verification/pom.xml
COPY claims-verification/src     /workspace/claims-verification/src

WORKDIR /workspace/claims-verification
RUN apk add --no-cache maven && mvn --batch-mode --no-transfer-progress -DskipTests package

# ── Stage 2: runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/claims-verification/target/*.jar app.jar
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "app.jar"]
