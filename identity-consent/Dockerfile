# ── Stage 1: build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Copy contracts schemas first (maven-resources-plugin reads them from ../contracts/schemas)
COPY contracts/schemas /workspace/contracts/schemas

# Copy service source
COPY identity-consent/pom.xml /workspace/identity-consent/pom.xml
COPY identity-consent/src     /workspace/identity-consent/src

WORKDIR /workspace/identity-consent
RUN apk add --no-cache maven && mvn --batch-mode --no-transfer-progress -DskipTests package

# ── Stage 2: runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/identity-consent/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
