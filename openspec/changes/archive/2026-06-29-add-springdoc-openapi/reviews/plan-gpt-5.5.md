# GPT-5.5 Plan Review: add-springdoc-openapi

## Verdict

approve

## Findings

None.

## Review Notes

- The previous security-model blocker is resolved. The artifacts now correctly describe the current `SecurityConfig`: explicit `authenticated()` matchers for `/api/auth/me`, `/api/manga/**`, and `/api/push/**`, followed by `anyRequest().permitAll()`. The implementation tasks preserve that behavior and add Swagger `permitAll` matchers only for clarity/future-proofing.
- The previous missing-error blocker is resolved. The spec and tasks now include the real `422 Unprocessable Entity` mapping for `ScrapingException` on `POST /api/manga`.
- The previous config-file issue is resolved. The tasks now target `backend/src/main/resources/application.properties`, matching the project.
- The previous production-exposure ambiguity is resolved. The plan chooses enabled-everywhere before apply and records the PR note as sign-off evidence, not as an implementation decision point.

## Open Questions

- None blocking. The remaining `@Schema(requiredMode = REQUIRED)` choice for package-private nested request records is implementer judgment and does not affect plan approval.

## Next Command

`/dual-opus confirm-plan add-springdoc-openapi`
