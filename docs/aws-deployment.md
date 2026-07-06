# AWS Deployment Runbook

This runbook describes the production shape for deploying MangaTracker on AWS without baking secrets into images.

## Architecture

Traffic flows through an HTTPS Application Load Balancer (ALB) to the frontend nginx container. Nginx serves the Angular SPA and proxies `/api/` requests to the Spring Boot backend task. The backend connects to Amazon RDS PostgreSQL and sends Web Push notifications with VAPID keys injected at runtime.

```text
Browser HTTPS
  -> ALB HTTPS listener with ACM certificate
  -> ECS Fargate frontend/nginx target
  -> /api/ proxy to ECS Fargate backend target
  -> RDS PostgreSQL
```

The backend must not be reachable directly from the internet. Its security group should allow inbound traffic only from the ALB or frontend/proxy security group that is allowed to call it.

## Forwarded Headers

TLS terminates at the ALB, so nginx receives HTTP from the ALB and must pass the original scheme to the backend. The frontend nginx config forwards `X-Forwarded-Proto`, `X-Forwarded-Host`, and `X-Forwarded-For`; the backend resolves them with `server.forward-headers-strategy=native` (Tomcat `RemoteIpValve`), which walks `X-Forwarded-For` right-to-left past trusted private-range proxies so client-spoofed tokens cannot forge the resolved client IP used by the login rate limiter.

For AWS, the ALB supplies `X-Forwarded-Proto: https`, nginx passes it through, and the backend resolves the request scheme as HTTPS. If the app is accessed locally without an ALB header, nginx omits the empty forwarded-proto value and the backend falls back to the direct request scheme, so local HTTP development still works.

Keep `AUTH_COOKIE_SECURE=true` in production. Override it to `false` only for local HTTP development where browsers would otherwise reject secure cookies on plain HTTP.

## RDS PostgreSQL

Provision an RDS PostgreSQL 16 instance in private subnets.

Use security groups with this shape:

- ALB security group: inbound `443` from the internet or approved source ranges.
- ECS frontend security group: inbound from the ALB target group only.
- ECS backend security group: inbound from the frontend/proxy security group only.
- RDS security group: inbound `5432` from the backend ECS security group only.

Set the backend JDBC URL as:

```text
DB_URL=jdbc:postgresql://<rds-endpoint>:5432/manga_tracker
```

On backend startup, Flyway applies pending migrations and Hibernate validates the schema with `ddl-auto=validate`.

## Secrets Manager

Store production secrets in AWS Secrets Manager and reference them from the ECS task definition `secrets` block. Do not set secret values as plaintext task-definition environment variables and do not bake them into Docker images.

Required backend secrets:

- `JWT_SECRET`
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `VAPID_PUBLIC_KEY`
- `VAPID_PRIVATE_KEY`
- `VAPID_SUBJECT`
- `OWNER_PASSWORD` if seeding the private owner account
- `DEMO_PASSWORD` if seeding the public demo account

Example task-definition snippet:

```json
{
  "containerDefinitions": [
    {
      "name": "backend",
      "image": "ghcr.io/<owner>/manga-tracker-backend:<tag>",
      "secrets": [
        { "name": "JWT_SECRET", "valueFrom": "arn:aws:secretsmanager:<region>:<account>:secret:manga/JWT_SECRET" },
        { "name": "DB_URL", "valueFrom": "arn:aws:secretsmanager:<region>:<account>:secret:manga/DB_URL" },
        { "name": "DB_USERNAME", "valueFrom": "arn:aws:secretsmanager:<region>:<account>:secret:manga/DB_USERNAME" },
        { "name": "DB_PASSWORD", "valueFrom": "arn:aws:secretsmanager:<region>:<account>:secret:manga/DB_PASSWORD" },
        { "name": "VAPID_PUBLIC_KEY", "valueFrom": "arn:aws:secretsmanager:<region>:<account>:secret:manga/VAPID_PUBLIC_KEY" },
        { "name": "VAPID_PRIVATE_KEY", "valueFrom": "arn:aws:secretsmanager:<region>:<account>:secret:manga/VAPID_PRIVATE_KEY" },
        { "name": "VAPID_SUBJECT", "valueFrom": "arn:aws:secretsmanager:<region>:<account>:secret:manga/VAPID_SUBJECT" }
      ],
      "environment": [
        { "name": "AUTH_COOKIE_SECURE", "value": "true" }
      ]
    }
  ]
}
```

Grant the task execution role permission to read only the specific secret ARNs it needs.

## ECS Fargate Service

Use ECS Fargate tasks for the frontend and backend containers. The backend image is a slim JRE
image with no bundled browser, so it no longer needs the elevated CPU/memory headroom a
Playwright/Chrome scraper required; size the task from observed JVM/traffic usage instead.

Run a single backend replica for now. The scheduled `MangaDexNotificationJob` runs inside the
backend process and has no distributed leader lock, so multiple replicas can duplicate daily
MangaDex checks and notifications. Add a lock or move the job to a dedicated scheduled task
before scaling backend replicas above one.

Configure the ALB with:

- HTTPS listener on `443` using an ACM certificate.
- HTTP to HTTPS redirect if exposing port `80`.
- Target group health check path `/actuator/health`.
- Health check matcher `200`.

Anonymous health responses expose only overall status, for example `{"status":"UP"}`. Component details such as database and disk space are withheld from anonymous callers.

## Deploy

1. Build and push frontend and backend images to the registry used by the ECS task definitions.
2. Provision RDS PostgreSQL and create the Secrets Manager entries above.
3. Register a new backend task-definition revision referencing Secrets Manager ARNs.
4. Register a frontend/nginx task definition that forwards `/api/` to the backend service.
5. Update the ECS services and wait for the target groups to report healthy.
6. Verify `/actuator/health` returns `UP` through the ALB and that login works over HTTPS.

## Rollback

Rollback by redeploying the previous ECS task-definition revision for the affected service. This change has no database migration, so app rollback does not require RDS schema rollback.
