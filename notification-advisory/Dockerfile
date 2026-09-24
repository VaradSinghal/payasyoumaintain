# ── Stage 1: build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY contracts/schemas /workspace/contracts/schemas
COPY notification-advisory/pom.xml /workspace/notification-advisory/pom.xml
COPY notification-advisory/src     /workspace/notification-advisory/src

WORKDIR /workspace/notification-advisory
RUN apk add --no-cache maven && mvn --batch-mode --no-transfer-progress -DskipTests package

# ── Stage 2: runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/notification-advisory/target/*.jar app.jar
EXPOSE 8087
ENTRYPOINT ["java", "-jar", "app.jar"]
