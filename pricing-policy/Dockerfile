# ── Stage 1: build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY contracts/schemas /workspace/contracts/schemas
COPY pricing-policy/pom.xml /workspace/pricing-policy/pom.xml
COPY pricing-policy/src     /workspace/pricing-policy/src

WORKDIR /workspace/pricing-policy
RUN apk add --no-cache maven && mvn --batch-mode --no-transfer-progress -DskipTests package

# ── Stage 2: runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/pricing-policy/target/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
