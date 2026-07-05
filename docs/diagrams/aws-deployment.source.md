# AWS Deployment Architecture Source

Canonical source for the AWS deployment diagram.

## Scope

Production AWS deployment shape documented in `docs/aws-deployment.md`.

## Confirmed Components

| Component | AWS / Runtime Service | Responsibility |
| ----------- | ------------------------ | ---------------- |
| Browser / PWA | End-user browser | Accesses the app over HTTPS and receives push notifications |
| ACM certificate | AWS Certificate Manager | TLS certificate for the public HTTPS endpoint |
| Application Load Balancer | ALB | Terminates HTTPS and routes traffic to ECS frontend/nginx target |
| Frontend service | ECS Fargate task running nginx | Serves Angular static assets and proxies `/api/` to backend |
| Backend service | ECS Fargate task running Spring Boot | API, auth, scraping, scheduled jobs, push notification delivery |
| RDS PostgreSQL | Amazon RDS PostgreSQL 16 | Production relational database |
| Secrets Manager | AWS Secrets Manager | Runtime secret injection into backend task definition |
| Container registry | Registry used by ECS task definitions | Stores frontend and backend images |
| Browser push service | External Web Push provider | Delivers VAPID-signed notifications to browsers/devices |

## Security Groups

| Security Group | Inbound Allowed From | Port / Purpose |
| ---------------- | ---------------------- | ---------------- |
| ALB security group | Internet or approved source ranges | `443` HTTPS; optional `80` redirect |
| Frontend ECS security group | ALB target group only | Frontend/nginx target port |
| Backend ECS security group | Frontend/proxy security group only | Backend API port |
| RDS security group | Backend ECS security group only | `5432` PostgreSQL |

## Main Connections

| From | To | Protocol / Mechanism | Notes |
| ------ | ---- | ---------------------- | ------- |
| Browser / PWA | ALB | HTTPS | Public entrypoint |
| ALB | Frontend ECS task | HTTP | TLS terminates at ALB |
| Frontend ECS task | Backend ECS task | HTTP proxy for `/api/` | Backend is not internet-facing |
| Backend ECS task | RDS PostgreSQL | JDBC / SQL | Flyway migrates; Hibernate validates |
| Backend ECS task | Secrets Manager | ECS task definition secrets | Secrets injected at runtime |
| Backend ECS task | Browser push service | Web Push / VAPID | Sends push notifications |

## Required Backend Secrets

- `JWT_SECRET`
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `VAPID_PUBLIC_KEY`
- `VAPID_PRIVATE_KEY`
- `VAPID_SUBJECT`
- `OWNER_PASSWORD` when seeding owner account
- `DEMO_PASSWORD` when seeding demo account

## Operational Constraints

- Keep `AUTH_COOKIE_SECURE=true` in production.
- Backend should not be directly reachable from the internet.
- Run a single backend replica until the scheduled `MangaDexNotificationJob` has a distributed lock or moves to a dedicated scheduled task.
- The backend image is a slim JRE image with no bundled browser; size the task from observed JVM/traffic usage, not a Chrome-driven minimum.
