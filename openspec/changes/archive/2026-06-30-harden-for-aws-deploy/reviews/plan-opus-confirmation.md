# Opus Plan Confirmation: harden-for-aws-deploy

## Verdict

confirmed-with-updates

GPT-5.5 plan review (`reviews/plan-gpt-5.5.md`) reviewed. All three findings accepted; both open questions resolved. `tasks.md` updated accordingly. No proposal/spec/design changes required — findings were verification-coverage and documentation gaps, not scope or design errors.

## Accepted

- **M1 — jacocoTestReport gate (tasks.md 5.1):** Repo backend gate in `docs/agent-workflow.md` is `./gradlew test jacocoTestReport`. This change touches backend config + prod behavior, so coverage report must run. Updated 5.1 to `./gradlew spotlessApply test jacocoTestReport`.
- **M2 — cross-service integration verification (tasks.md 5.x):** Core behavioral risk is the nginx → backend forwarded-header + auth/health path, which `docker compose up` boot alone does not exercise. `docs/agent-workflow.md` requires `./run-e2e-integration.sh --down` for cross-service behavior. Added task 5.3 to run the integration runner against the proxy/auth/health path.
- **L3 — forwarded-proto fallback made explicit (tasks.md 1.3):** Clarified that when the ALB `X-Forwarded-Proto` header is absent, nginx omits the empty value and the backend falls back to the request scheme; local HTTP login must still work and the fallback is documented in the runbook. Verify explicitly.

## Open Questions — resolved

- **Q1 — full frontend quality gate?** No. The only frontend change is `nginx.conf` (container/runtime config), not Angular source; `npm run format/test/lint/e2e` does not build or exercise nginx. The proxy path is verified by `./run-e2e-integration.sh --down` (task 5.3). Rationale recorded inline in 5.3 so the skip is intentional and auditable.
- **Q2 — document `AUTH_COOKIE_SECURE`?** Yes. Added task 4.6 to document it in the runbook: `true` in production (HTTPS via ALB), override to `false` only for local HTTP dev, tied to the forwarded-header rationale.

## Rejected

- None.

## Risks / Watch Items for Apply

- `run-e2e-integration.sh` must actually cover the auth/health path through the proxy; if the existing harness does not, GPT should add a focused smoke check rather than silently passing on container boot only.
- Trusting `X-Forwarded-Proto` is only safe because the ECS backend is reachable solely via the ALB SG — runbook (task 4.2 / 4.4) must state the inbound-from-ALB-SG-only rule, otherwise the header trust is a spoofing vector.
- No DB migrations, no API contract changes, no Flyway edits — confirmed unchanged. `ddl-auto=validate` stays.

## Apply-phase amendment — actuator test must be Docker-free (decided 2026-06-29)

GPT hit a Windows/Testcontainers Docker-discovery failure because the new `ActuatorHealthSecurityTest` booted a full `@SpringBootTest`, pulling DataSource → Testcontainers.

**Decision: replace with a no-Testcontainers test. Reject the "fix Windows Docker discovery" path.**

- The requirement is "anonymous `/actuator/health` exposes no component detail" — an actuator-exposure + security concern, NOT DB connectivity. A real Postgres must not be required to prove it.
- Repo precedent: all security/controller tests (`SecurityConfigTest`, `AuthControllerTest`, etc.) use `@WebMvcTest` + `@Import(SecurityConfig.class)` slices or `standaloneSetup` MockMvc with zero Testcontainers. Testcontainers appear only in `*RepositoryTest`. A heavy Testcontainers actuator test breaks the established pattern and makes the gate non-portable.

**Exact accepted fix for GPT — Docker-free actuator test:**

- Boot only enough for actuator, no DB:
  - `@SpringBootTest(webEnvironment = RANDOM_PORT)` (or `MOCK` + `@AutoConfigureMockMvc`)
  - test props exclude the DB stack: `spring.autoconfigure.exclude` = `DataSourceAutoConfiguration`, `HibernateJpaAutoConfiguration`, `FlywayAutoConfiguration`
  - `management.health.db.enabled=false`
  - `management.endpoint.health.show-details=when-authorized`
- Assertions:
  - anonymous `GET /actuator/health` → 200, body `status: UP`, and `jsonPath("$.components").doesNotExist()` (also no `db` / `diskSpace` keys).
  - (optional) an authorized request still permitted, mirroring `SecurityConfigTest`.

**Constraint:** do NOT add Testcontainers config, a WSL-only run instruction, or new DB deps to make this pass. The test must run with no Docker daemon present. Maps to task 2.3.

## Next Command

`/dual-gpt apply harden-for-aws-deploy`
