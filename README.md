# MangaTracker

A web application to track manga reading progress and receive email notifications when new chapters are released. Add manga by URL, mark chapters as read, and get notified automatically whenever a new chapter drops.

## Features

- Add manga to your reading list by pasting a source URL
- Automatic title and chapter detection via web scraper
- Visual indicator for manga with unread chapters
- Update your reading progress (current chapter) per title
- Per-manga and global email notification toggles
- Configurable polling interval for chapter checks
- Email notifications via Mailhog (local) or any SMTP server

## Tech Stack

| Layer     | Technology                                     |
|-----------|------------------------------------------------|
| Backend   | Spring Boot 3.4.4 · Java 21 · Gradle           |
| Frontend  | Angular 18 · TypeScript · nginx                |
| Database  | PostgreSQL 16 · Flyway migrations              |
| Email     | Spring Mail · Mailhog (local dev)              |
| Testing   | JUnit 5 · Mockito · Testcontainers · Playwright|
| CI        | GitHub Actions · SonarCloud                    |
| Container | Docker · docker compose                        |

## Prerequisites

- [Docker](https://docs.docker.com/get-docker/) with the `docker compose` plugin
- Ports 4200, 8080, 5432, 1025, and 8025 available on your machine

## Quick Start

```bash
git clone <repo-url>
cd manga-tracker
docker compose up
```

The app is available at **http://localhost:4200** once all services are healthy.
View test emails at **http://localhost:8025** (Mailhog web UI).

> First startup may take a few minutes while Docker builds the images.

## Local Development (Without Docker)

### Backend

Requirements: Java 21

```bash
cd backend
./gradlew bootRun
```

The API server starts on **http://localhost:8080**.

You will need a local PostgreSQL instance and a running Mailhog container, or override the
environment variables below with your own values.

### Frontend

Requirements: Node 20

```bash
cd frontend
npm install
npm start
```

The dev server starts on **http://localhost:4200** and proxies `/api` requests to `localhost:8080`.

## Environment Variables

The backend reads the following environment variables (with defaults shown):

| Variable                  | Default                                         | Description                               |
|---------------------------|-------------------------------------------------|-------------------------------------------|
| `DB_URL`                  | `jdbc:postgresql://localhost:5432/manga_tracker`| JDBC connection URL for PostgreSQL        |
| `DB_USERNAME`             | `manga_tracker`                                 | PostgreSQL username                       |
| `DB_PASSWORD`             | `manga_tracker`                                 | PostgreSQL password                       |
| `MAIL_HOST`               | `localhost`                                     | SMTP host                                 |
| `MAIL_PORT`               | `1025`                                          | SMTP port                                 |
| `NOTIFICATION_FROM_EMAIL` | `tracker@localhost`                             | From address used for notification emails |
| `POLL_INTERVAL_MINUTES`   | `30`                                            | How often (in minutes) to scrape for new chapters (configurable in UI) |

In docker compose these are set automatically. For local dev without Docker, set them as shell
environment variables or edit `backend/src/main/resources/application.properties`.

## Documentation

- [API Reference](docs/api.md)
- [Architecture Overview](docs/architecture.md)
- [Developer Guide](docs/developer-guide.md)
