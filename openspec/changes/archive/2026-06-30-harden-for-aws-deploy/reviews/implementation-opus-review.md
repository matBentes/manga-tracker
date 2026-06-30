# Opus Implementation Review: harden-for-aws-deploy

## Verdict

approved

GPT's applied implementation matches the confirmed plan and the Docker-free actuator-test amendment. Diffs are minimal, structurally clean, and the runbook covers every required item. One deviation from the prescribed test pattern is accepted (see below); two minor notes are non-blocking.

## Reviewed

Diff (uncommitted on `fix/ci-docs-only-gate`):

- `backend/src/main/resources/application.properties` — `server.forward-headers-strategy=framework`; `show-details` `always` → `when-authorized`.
- `frontend/nginx.conf` `/api/` — adds `X-Forwarded-Proto $http_x_forwarded_proto`, `X-Forwarded-Host $host`, `X-Forwarded-For $proxy_add_x_forwarded_for`.
- `.env.example` — Secrets-Manager source comments; prod `DB_URL` note.
- `README.md` — runbook link.
- `run-e2e-integration.sh` — proxy/auth/health smoke block.
- NEW `backend/.../security/ActuatorHealthSecurityTest.java` — Docker-free anonymous-health-exposure test.
- NEW `docs/aws-deployment.md` — AWS runbook.

Dockerfile and SecurityConfig unchanged — tasks 2.2 / 3.1 / 3.3 were verify-only, correctly no-code-change.

## Task coverage

- 1.1 / 1.2 — present, exact. ✓
- 1.3 — fallback documented in runbook "Forwarded Headers" (empty header → backend falls back to request scheme). ✓
- 2.1 — `when-authorized`. ✓
- 2.3 — Docker-free test present (see deviation). ✓
- 3.2 — `.env.example` Secrets-Manager comments. ✓
- 4.1–4.6 — runbook covers architecture, RDS + SG shape (RDS inbound 5432 from backend SG only; backend inbound from frontend/ALB SG only), Secrets Manager `secrets` block with ARN mapping, ECS Fargate ≥2 GB / 1 vCPU, single-replica scraper constraint, ALB HTTPS + ACM, health-check path/matcher, rollback, and `AUTH_COOKIE_SECURE` (true prod / false local). ✓
- 5.x — verification gates are runtime steps for the apply/verify pass, not artifacts to review here.

## Quality (thermo-nuclear bar)

- No structural regression, no spaghetti branching, no file-size bloat, no boundary leaks. Each diff is the smallest change that satisfies its task.
- nginx uses `$http_x_forwarded_proto` (pass ALB value; empty locally → backend `$scheme` fallback) — matches design decision and keeps local HTTP working. No spoofing exposure given ECS backend reachable only via the frontend/ALB SG (runbook states this).
- Smoke block cleans its cookie jar on every exit path; `-fsS` makes non-2xx fail the run. Sound.

## Accepted deviation — actuator test pattern

The amendment prescribed `@SpringBootTest(RANDOM_PORT)` (or `MOCK` + `@AutoConfigureMockMvc`) with `spring.autoconfigure.exclude` of the DB stack. GPT instead wrote a **pure unit test**: it constructs `HealthEndpointWebExtension` directly with a registry containing `db` + `diskSpace` indicators, reads the real `management.endpoint.health.show-details` value from `application.properties`, and asserts an anonymous (`SecurityContext.NONE`) request returns `status: UP` with no details.

Accepted because it honors the constraint better than prescribed:

- Zero Docker, zero Spring context, zero DB deps — runs with no daemon, fully portable. The constraint ("no Testcontainers / no Docker / no new DB deps") was the binding requirement; the exact annotation was a suggested means.
- It is a genuine regression guard: it reads the actual properties value, so reverting to `show-details=always` flips `Show.ALWAYS.isShown(...)` → details present → assertion fails.

Trade-off (non-blocking): the test exercises Spring's health-group plumbing rather than the live HTTP endpoint + security filter chain, so it does not catch a `SecurityConfig` regression on `/actuator/health`. That live path is covered by the new `run-e2e-integration.sh` smoke check, which curls the endpoint and fails if `components`/`db`/`diskSpace` appear. Combined coverage is sufficient; a MockMvc rewrite would add context-boot churn for marginal gain.

## Non-blocking notes

1. Health smoke check curls `http://localhost:8080/actuator/health` (backend port) rather than through the nginx proxy on `4200`; the forwarded-header proxy path is still exercised by the CSRF + demo-login calls that go through `4200`. Acceptable.
2. No `reviews/implementation-gpt-5.5-summary.md` was produced, and `tasks.md` checkboxes remain unchecked. Process bookkeeping only — does not affect code correctness. GPT should tick `tasks.md` during the verify pass.

## Risks / watch for verify pass

- Verify gates not yet run here: `cd backend && ./gradlew spotlessApply test jacocoTestReport`, `docker compose up` regression, and `./run-e2e-integration.sh --down`. These must pass before final review.
- `demo-login` smoke requires `DEMO_PASSWORD` seeded in the integration env; confirm the runner provides it or the check will false-fail.

## Next Command

`/dual-opus final-review harden-for-aws-deploy`
