# ─── Build stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /build

# Copy pom and download dependencies first (Docker layer cache)
COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -q

# Copy source and build
COPY src/ src/
RUN ./mvnw package -DskipTests -q

# ─── Runtime stage ────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

WORKDIR /app

# Copy only the fat JAR
COPY --from=builder /build/target/*.jar app.jar

# Logs directory (mapped to a volume in docker-compose)
RUN mkdir -p /app/logs

EXPOSE 8080

# JVM tuning for containers:
#   - UseContainerSupport: respect cgroup memory limits
#   - MaxRAMPercentage: use up to 75% of container memory for heap
#   - ExitOnOutOfMemoryError: fail fast instead of thrashing
#   - spring.profiles.active can be overridden at runtime
ENTRYPOINT ["java", \
            "-XX:+UseContainerSupport", \
            "-XX:MaxRAMPercentage=75.0", \
            "-XX:+ExitOnOutOfMemoryError", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-jar", "app.jar"]
