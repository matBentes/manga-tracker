# Opus Implementation Review: add-springdoc-openapi

## Verdict

approved

Originally `needs-human-decision` on the 403 -> 401 status change; the human
**accepted** it (2026-06-29). Proposal + api-documentation spec updated to
record the intentional denial-status change (stopped labeling it
documentation-only). No `needs-gpt-fix` items — code is correct and idiomatic.

## The decision (403 -> 401 on unauthenticated protected endpoints)

GPT added an `AuthenticationEntryPoint` to `SecurityConfig` returning
`SC_UNAUTHORIZED`:

```java
.exceptionHandling(
    exceptions ->
        exceptions.authenticationEntryPoint(
            (request, response, authException) ->
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
```

This flips unauthenticated protected requests from **403 -> 401** (test
assertions changed `isForbidden()` -> `isUnauthorized()` at
`SecurityConfigTest` lines 143 / 169).

Why it is sound (not a defect):
- **CSRF enforcement intact.** Missing-CSRF requests still return **403**
  (`authEndpoints_requireCsrf` lines 132/134/135, `protectedMutations_stillRequireCsrf`
  line 157). The entry point only catches `AuthenticationException`; the
  `CsrfFilter` `AccessDeniedException` path is untouched. The 401/403 split is
  tested on both sides.
- **Fixes the frontend.** `auth.interceptor.ts`: `403` -> clear CSRF token +
  retry (no redirect); `401 && !/api/auth/` -> redirect `/login` (line 121).
  Before this change an unauthenticated user hitting a protected path got 403
  and was never redirected to login — a latent UX bug. 401 makes the redirect fire.
- **Makes the spec true.** confirm-plan task 2.2 + spec scenarios assert
  protected paths return 401. GPT implemented exactly what the confirmed plan
  demanded, and disclosed it (summary line 13).

Why a human must sign off:
- It is a **user-observable API-contract change** (HTTP status on every
  protected endpoint), which the proposal advertised as **"documentation-only"
  / "no frontend changes" / "no authorization behavior changes"** (proposal
  lines 8, 22, 29). Authorization (who-can-access-what) is in fact unchanged;
  the **denial status code** changed. The proposal wording is now inaccurate
  and should be corrected once the human accepts the change.

Human question: **Accept 403 -> 401 for unauthenticated protected endpoints?**
Recommended: **yes** (beneficial, tested, CSRF still 403). On accept, I will
correct the proposal/spec wording to stop calling it documentation-only.

## Principle check (SOLID / KISS / YAGNI / DRY / magic numbers/strings)

Requested by the user. No violations found.

- **Magic strings/numbers:** none. `OpenApiConfig` and `MangaController`
  reuse `JwtCookieAuthFilter.COOKIE_NAME` for the security-scheme name and
  `@SecurityRequirement`. HTTP-status literals (`"401"`, `"404"`, ...) inside
  `@ApiResponse` are the documentation payload itself — idiomatic springdoc,
  not constants to extract.
- **DRY:** `ErrorResponse` centralizes the `{"error":"<message>"}` envelope as
  one schema referenced by all error `@ApiResponse`s. The repeated per-method
  `@ApiResponse` blocks are inherent to per-operation OpenAPI docs, not
  extractable duplication.
- **KISS / YAGNI:** `OpenApiConfig` and `ErrorResponse` are minimal (empty
  class body / single-field record). No speculative abstraction, no profile
  gating, no handler rewrite (schema-only, as the plan allowed).
- **SOLID:** annotations are additive metadata; no responsibilities moved, no
  new coupling beyond the existing `JwtCookieAuthFilter` constant reuse.
- **Structure / file growth:** controllers grow only by annotation volume
  (Manga +127 etc.), no new branching or logic. `docs/api.md` shrunk to
  concepts as planned. No spaghetti, no boundary leaks.

## Other notes (non-blocking)

- **jacoco not green:** 3 Testcontainers repository tests fail with
  `Could not find a valid Docker environment` — environmental, not a code
  defect. Targeted controller/security/service/scraper/job tests +
  `OpenApiDocumentationTest` + `SecurityConfigTest` pass. Full
  `test jacocoTestReport` must be green in a Docker-capable env before merge.
- **springdoc 2.6.0 -> 2.8.9:** valid apply-time fix (2.6.0 throws
  `NoSuchMethodError: ControllerAdviceBean.<init>(Object)` on Boot 3.4.4).
  Artifacts already updated.
- **Task 6.2 (PR note):** still unchecked; carry the exposure-rationale note
  into the PR for human sign-off.

## Next command

Human decides the 403 -> 401 question. On accept:

```text
/dual-opus final-review add-springdoc-openapi
```
