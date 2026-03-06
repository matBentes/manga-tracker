# ── Stage 1: build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Gradle wrapper and build files first (layer cache)
COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle settings.gradle ./

# Download dependencies (cached unless build.gradle changes)
RUN ./gradlew dependencies --no-daemon

# Copy source and build
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test && \
    cp build/libs/backend-*.jar build/libs/app.jar

# ── Stage 2: runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

COPY --from=builder /app/build/libs/app.jar app.jar

# Runtime environment variables — override at container start via -e or docker-compose environment.
# Do NOT set DB_PASSWORD here; inject it at runtime to avoid baking secrets into the image.
ENV DB_URL=jdbc:postgresql://localhost:5432/manga_tracker \
    DB_USERNAME=manga_tracker \
    MAIL_HOST=localhost \
    MAIL_PORT=1025 \
    NOTIFICATION_FROM_EMAIL=tracker@localhost

USER app

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
