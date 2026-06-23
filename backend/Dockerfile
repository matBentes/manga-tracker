# ── Stage 1: build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS builder

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
FROM eclipse-temurin:21-jre AS runtime

RUN apt-get update \
    && apt-get install -y --no-install-recommends chromium wget ca-certificates \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system app && useradd --system --gid app --create-home app
WORKDIR /app

COPY --from=builder /app/build/libs/app.jar app.jar

# Runtime environment variables — override at container start via -e or docker-compose environment.
# Do NOT set DB_PASSWORD here; inject it at runtime to avoid baking secrets into the image.
# Debian + system Chromium adds roughly a few hundred MB, but avoids Alpine incompatibilities and
# first-request Playwright browser installation inside the running container.
ENV DB_URL=jdbc:postgresql://localhost:5432/manga_tracker \
    DB_USERNAME=manga_tracker \
    MAIL_HOST=localhost \
    MAIL_PORT=1025 \
    NOTIFICATION_FROM_EMAIL=tracker@localhost \
    SCRAPER_SAKURA_CHROMIUM_EXECUTABLE=/usr/bin/chromium \
    PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1

USER app

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
