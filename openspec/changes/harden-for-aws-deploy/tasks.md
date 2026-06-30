## 1. Forwarded-header handling

- [ ] 1.1 In `frontend/nginx.conf` `/api/` block, add `proxy_set_header X-Forwarded-Proto $http_x_forwarded_proto;`, `proxy_set_header X-Forwarded-Host $host;`, and `proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;`
- [ ] 1.2 In `backend/src/main/resources/application.properties`, add `server.forward-headers-strategy=framework`
- [ ] 1.3 Verify locally over HTTP that login still works when the ALB `X-Forwarded-Proto` header is absent: nginx omits the empty header value and the backend falls back to the request scheme. Confirm explicitly (curl/login) and note this fallback in `docs/aws-deployment.md`.

## 2. Actuator hardening

- [ ] 2.1 Change `management.endpoint.health.show-details` from `always` to `when-authorized` in `application.properties`
- [ ] 2.2 Confirm `/actuator/health` and `/actuator/info` remain `permitAll` in `SecurityConfig` and anonymous health returns status-only JSON
- [ ] 2.3 Add/adjust a backend test asserting anonymous `/actuator/health` body contains no component (db/diskSpace) detail

## 3. Secrets & env wiring

- [ ] 3.1 Confirm `JWT_SECRET`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, VAPID vars are all env-sourced with fail-fast on missing `JWT_SECRET` (no code change expected — verify)
- [ ] 3.2 Update `.env.example` comments to note which vars come from AWS Secrets Manager in production
- [ ] 3.3 Ensure no secret defaults are baked into the backend Dockerfile `ENV` (verify `DB_PASSWORD`/`JWT_SECRET` not present)

## 4. AWS deployment runbook

- [ ] 4.1 Create `docs/aws-deployment.md`: architecture overview (ALB → nginx/frontend + backend tasks → RDS)
- [ ] 4.2 Document RDS Postgres provisioning, security group (inbound from ECS SG only), and `DB_URL` format
- [ ] 4.3 Document Secrets Manager entries and the ECS task-definition `secrets` block mapping ARNs → env vars
- [ ] 4.4 Document ECS Fargate service: task sizing (≥2 GB / 1 vCPU for bundled Chrome), single-replica scraper constraint, ALB HTTPS listener + ACM cert, target-group health check on `/actuator/health`
- [ ] 4.5 Document rollback (redeploy prior task-definition revision) and link the runbook from `README.md`
- [ ] 4.6 Document `AUTH_COOKIE_SECURE`: must be `true` in production (HTTPS via ALB) and may be overridden to `false` only for local HTTP dev; tie this to the forwarded-header rationale

## 5. Verification

- [ ] 5.1 `cd backend && ./gradlew spotlessApply test jacocoTestReport` passes (matches the repo backend gate in `docs/agent-workflow.md`)
- [ ] 5.2 `docker compose up` boots end-to-end locally over HTTP (regression check)
- [ ] 5.3 Run `./run-e2e-integration.sh --down` from repo root to exercise the nginx → backend proxy + auth/health path (cross-service change). Frontend unit gate (`npm test`/`lint`/`e2e`) is intentionally skipped: the only frontend change is `nginx.conf`, not Angular source, so the proxy path is verified by the integration runner instead.
- [ ] 5.4 `openspec validate harden-for-aws-deploy` passes
