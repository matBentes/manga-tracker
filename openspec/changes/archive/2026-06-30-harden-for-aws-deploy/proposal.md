## Why

The app runs cleanly under local Docker Compose but is not safe to expose on AWS as-is. Behind an HTTPS load balancer the backend cannot tell requests are secure (nginx drops `X-Forwarded-Proto`), the health endpoint leaks internal details to anonymous callers, and there is no documented path to run on managed AWS infrastructure (RDS, Secrets Manager, ECS/ALB). These gaps cause broken secure-cookie auth and information disclosure in production.

## What Changes

- nginx reverse proxy forwards `X-Forwarded-Proto` (and `X-Forwarded-Host`/`X-Forwarded-For`) so the backend sees the real HTTPS scheme behind the ALB.
- Spring Boot honors forwarded headers via `server.forward-headers-strategy=framework`, so secure-cookie issuance and absolute URLs reflect HTTPS.
- Actuator health detail is restricted: `management.endpoint.health.show-details=when-authorized` (anonymous callers get UP/DOWN only, no DB/disk internals).
- Document and parameterize AWS deployment: RDS Postgres connection + Secrets Manager-injected credentials (`JWT_SECRET`, `DB_*`, VAPID), with a deploy runbook for ECS Fargate + ALB + RDS.
- No application feature behavior changes; this is hardening + ops documentation.

## Capabilities

### New Capabilities
- `production-deployment`: requirements for running the app on AWS — forwarded-header handling, restricted actuator exposure, externalized secrets, and the managed-infra deployment contract (RDS, ALB/HTTPS, ECS Fargate).

### Modified Capabilities
<!-- None: api-documentation requirements unchanged. -->

## Impact

- `frontend/nginx.conf` — add forwarded headers on the `/api/` proxy block.
- `backend/src/main/resources/application.properties` — `forward-headers-strategy`, `health.show-details=when-authorized`.
- New `docs/aws-deployment.md` — ECS Fargate + ALB + RDS + Secrets Manager runbook.
- `.env.example` / `docs` — note which vars come from Secrets Manager in prod.
- No DB migrations, no API contract changes.
