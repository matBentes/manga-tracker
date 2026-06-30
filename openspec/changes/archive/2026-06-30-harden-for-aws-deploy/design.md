## Context

Backend is Spring Boot 3 / Java 21, served behind an nginx container that also serves the Angular SPA and proxies `/api/`. Locally everything is plain HTTP via Docker Compose. On AWS, TLS terminates at an ALB, so the backend receives plain HTTP from the proxy and currently cannot detect the original HTTPS scheme. `app.auth.cookie-secure` defaults to `true`, so without forwarded-header trust the auth cookie behavior and any scheme-derived logic are wrong. Actuator health is `permitAll` with `show-details=always`, leaking component internals. There is no managed-infra deployment doc.

## Goals / Non-Goals

**Goals:**
- Backend correctly detects HTTPS behind the ALB.
- No anonymous disclosure of internal health detail.
- Secrets sourced from Secrets Manager; nothing baked into images.
- A reproducible ECS Fargate + ALB + RDS runbook.

**Non-Goals:**
- IaC (Terraform/CDK) authoring — runbook only this round.
- Autoscaling / multi-replica scraper coordination (documented as a constraint, not solved).
- CDN/WAF, blue-green automation, custom domain DNS automation.

## Decisions

- **`server.forward-headers-strategy=framework`** over `native`: framework strategy is server-agnostic and the Spring-recommended default for proxies/ALBs; `native` depends on Tomcat's `RemoteIpValve` config and the proxy being a trusted internal hop. We control the nginx hop, ALB is the only public ingress, so framework strategy is sufficient and simpler.
- **nginx forwards `X-Forwarded-Proto $scheme`** (plus `X-Forwarded-Host`, `X-Forwarded-For`). `$scheme` at nginx is `http` (TLS already terminated at ALB), so nginx must instead propagate what the ALB sent. Use `proxy_set_header X-Forwarded-Proto $http_x_forwarded_proto;` to pass the ALB-supplied value through, falling back to `$scheme` only for direct/local use. Decision: trust the ALB header since the backend is not publicly reachable except via the ALB.
- **`health.show-details=when-authorized`** over `never`: keeps full detail for authenticated operators while giving anonymous/ALB probes only UP/DOWN. ALB health check only needs the status line, so this does not break probes.
- **Secrets via ECS task-definition `secrets` (Secrets Manager ARNs)** over `environment` plaintext or SSM Parameter Store: task `secrets` inject at container start without appearing in the task def's plaintext env or image layers; Secrets Manager supports rotation if needed later.
- **RDS Postgres** over self-managed Postgres container: managed backups, patching, and durable storage; the container Postgres in compose stays for local dev only.
- **Single backend replica** for now: the scheduled scraper would double-run under >1 replica with no leader lock. Documented as a hard constraint in the runbook; revisit with a lock if scaling is needed.

## Risks / Trade-offs

- **Trusting `X-Forwarded-Proto` blindly** → backend must not be reachable except through the ALB. Mitigation: ECS security group allows inbound only from the ALB security group.
- **Bundled Chrome inflates task memory** (Playwright + Chrome ≈ 1–2 GB) → undersized Fargate task OOM-kills the scraper. Mitigation: runbook mandates ≥2 GB memory, 1 vCPU minimum.
- **`when-authorized` could still 200 with status only to the ALB** → confirm ALB health check matcher expects 200 and parses status, not detail. Mitigation: documented health-check config in runbook.
- **Single replica = no HA** → brief downtime on task replacement. Mitigation: ECS rolling deploy with health-check grace; acceptable for current scale.

## Migration Plan

1. Land app/config changes (nginx headers, properties) — backward compatible, local dev still works over HTTP (ALB header absent → falls back to `$scheme`).
2. Provision RDS, Secrets Manager entries, ACM cert, ALB, ECS cluster per runbook.
3. Deploy task definition referencing Secrets Manager ARNs; verify Flyway migrates RDS and `/actuator/health` returns UP.
4. Rollback: ECS keeps the prior task-definition revision; redeploy previous revision. RDS unaffected by app rollback (no destructive migrations in this change).

## Open Questions

- Custom domain + Route 53 record automation — out of scope this round, operator does manually.
- Whether to move scraper to a separate scheduled task/Lambda later to decouple from the web replica.
