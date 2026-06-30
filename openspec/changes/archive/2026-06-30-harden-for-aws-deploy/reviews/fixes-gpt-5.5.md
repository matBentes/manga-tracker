# GPT-5.5 Fix Handoff: harden-for-aws-deploy

## Scope

Fix phase for Opus review findings, CI Markdown Lint failure, and accepted Codex P2 health-proxy finding.

## Opus Review Files Read

- `reviews/implementation-opus-review.md`
- `reviews/final-opus-confirmation.md`

## Accepted Fixes Applied

- Wrapped the three accepted `README.md` bare localhost URLs in angle brackets for `MD034/no-bare-urls`.
- Normalized the existing README tech-stack table alignment after local Markdown Lint surfaced `MD060` in the same changed file.
- Checked all `tasks.md` items based on implementation evidence and CI evidence recorded by Opus.
- Added `reviews/implementation-gpt-5.5-summary.md` for OpenSpec bookkeeping.
- Added an nginx exact-match `/actuator/health` proxy to the backend with the same forwarded headers as `/api/`.
- Updated the integration smoke check to validate `/actuator/health` through nginx on port `4200`.

## Verification Run

- `openspec validate harden-for-aws-deploy` — passed.
- `cd backend && ./gradlew test --tests "com.mangatracker.backend.security.ActuatorHealthSecurityTest"` — passed; also generated `jacocoTestReport` via the Gradle test finalizer.
- `npx --yes markdownlint-cli2 README.md docs/aws-deployment.md` — passed with 0 errors using `rtk proxy`.
- Latest fix pass: `openspec validate harden-for-aws-deploy` — passed.
- Latest fix pass: `npx --yes markdownlint-cli2 README.md docs/aws-deployment.md openspec/changes/harden-for-aws-deploy/reviews/final-opus-confirmation.md openspec/changes/harden-for-aws-deploy/reviews/fixes-gpt-5.5.md` — rerun after wrapping the review PR URL.
- Latest fix pass: `JWT_SECRET=... docker compose config` — passed with dummy local verification secret.
- Latest fix pass: rebuilt `backend` and `frontend` Docker images, then ran `JWT_SECRET=... OWNER_PASSWORD=... DEMO_PASSWORD=... bash ./run-e2e-integration.sh --down`. The new proxied `/actuator/health` smoke passed; the script later failed in Playwright on an existing unrelated locator ambiguity in `frontend/e2e/integration.spec.ts` where `getByText('Phone Notifications')` matches both a label and button.

## Not Run / Still External

- Full backend gate remains locally blocked by the existing Windows/Testcontainers Docker discovery issue documented in `reviews/final-opus-confirmation.md`; PR #25 CI passed backend Tests.
- Docker compose and integration E2E are evidenced by PR #25 CI per `reviews/final-opus-confirmation.md`.

## Next Command

`/dual-opus final-review harden-for-aws-deploy`
