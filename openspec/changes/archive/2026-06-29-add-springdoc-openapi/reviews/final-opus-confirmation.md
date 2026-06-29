# Opus Final Review: add-springdoc-openapi

## Verdict

approved

Pending two non-blocking pre-merge gates (jacoco in Docker env, task 6.2 PR note) — neither is a code defect; both are environmental/process.

## Scope confirmed unchanged since review-impl

Implementation diff vs `main` identical to what review-impl approved. Re-verified:

- `SecurityConfig.java` (+7): `AuthenticationEntryPoint` → `SC_UNAUTHORIZED`, plus
  Swagger `permitAll` clarity matchers. No authorization-rule change.
- `ErrorResponse.java` (new), `OpenApiConfig` (new), controller/DTO annotations,
  `application.properties` springdoc paths, `build.gradle` 2.8.9, `docs/api.md`
  shrunk, pointer prose in `CLAUDE.md`/`AGENTS.md`/`README.md`.
- `SecurityConfigTest.java` (+30): 403/401 split tested on both paths.

## Code quality (thermo-nuclear bar)

No structural debt. Additive annotation metadata only; no new branching, no
moved responsibilities, no speculative abstraction. `OpenApiConfig` empty body,
`ErrorResponse` single-field record — minimal. `JwtCookieAuthFilter.COOKIE_NAME`
reused for scheme name + `@SecurityRequirement` — no magic strings. HTTP-status
literals in `@ApiResponse` are doc payload, not extractable constants. SOLID /
KISS / YAGNI / DRY clean (full analysis in `implementation-opus-review.md`).

## 403 -> 401 decision

Human-accepted 2026-06-29. CSRF failures still 403 (tested), only
`AuthenticationException` → 401. Fixes frontend `/login` redirect. Proposal +
`specs/api-documentation/spec.md` corrected to record the intentional denial-status
change (no longer mislabeled documentation-only).

## Test evidence (this phase)

Fresh local run, both green:

```
./gradlew test --tests SecurityConfigTest --tests OpenApiDocumentationTest
BUILD SUCCESSFUL
```

## Pre-merge gates (non-blocking, carry into PR)

1. **Full `./gradlew test jacocoTestReport` green in a Docker-capable env.** 3
   Testcontainers repo tests (`MangaRepositoryTest`, `NotificationLogRepositoryTest`,
   `PushSubscriptionRepositoryTest`) fail locally with
   `Could not find a valid Docker environment` — environmental, not this change.
   CI (Docker-backed) must show green.
2. **Task 6.2 PR note.** Add exposure rationale: Swagger/OpenAPI enabled
   everywhere (demo app, no secrets in spec, public demo account already exposed;
   profile-gating deferrable if reviewers ask).

## Working-tree note (not part of this change)

Branch `chore/adopt-openspec-canonical` also carries unrelated openspec-canonical
adoption edits (`opencode.json`, `openspec/config.yaml`, `.openspec.yaml`,
`deprecated-tasks/` moves, `.gitignore`). Out of scope for this change; left
untouched per "do not revert unrelated local changes." Keep PR scoping in mind —
consider isolating the springdoc commit if reviewers want a clean PR.

## Next command

CI green + Codex PR review complete, then:

```text
/dual-opus sync add-springdoc-openapi
```
