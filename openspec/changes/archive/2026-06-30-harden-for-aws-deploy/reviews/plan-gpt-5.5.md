# GPT-5.5 Plan Review: harden-for-aws-deploy

## Verdict

approve-with-changes

## Findings

### Medium: Verification tasks are weaker than the repository's required gates

- `openspec/changes/harden-for-aws-deploy/tasks.md:29` only requires `cd backend && ./gradlew spotlessApply test`, but `docs/agent-workflow.md` requires `./gradlew test jacocoTestReport` for backend verification.
- This change touches backend configuration and production behavior, so the implementation handoff should require `jacocoTestReport` or explicitly document why it is skipped.
- Recommended change: update task 5.1 to `cd backend && ./gradlew spotlessApply test jacocoTestReport`.

### Medium: Cross-service verification should exercise the proxy path, not only container boot

- `openspec/changes/harden-for-aws-deploy/tasks.md:30` requires `docker compose up` boot verification, but the core behavioral risk is nginx-to-backend forwarded headers plus auth/health behavior through the frontend proxy.
- `docs/agent-workflow.md:47` says cross-service behavior should also run `./run-e2e-integration.sh --down`.
- Recommended change: add a verification task for the integration runner, or explicitly record why the proxy/auth path is verified by an equivalent smoke check instead.

### Low: Forwarded-proto fallback should be explicit in the task or runbook

- `openspec/changes/harden-for-aws-deploy/design.md:21` says nginx should pass the ALB-supplied proto and fall back to `$scheme` for direct/local use.
- `openspec/changes/harden-for-aws-deploy/tasks.md:3` only names `proxy_set_header X-Forwarded-Proto $http_x_forwarded_proto;`.
- Nginx generally omits an empty proxy header value, which lets the backend fall back to the request scheme, but the implementation expectation is easy to misread.
- Recommended change: clarify in `tasks.md` or `docs/aws-deployment.md` that local/direct requests must still work when the ALB header is absent, and verify that explicitly.

## Open Questions

- Should the apply phase run the full frontend quality gate (`npm run format`, `npm test`, `npm run lint`, `npm run e2e`) even though the frontend source change is nginx-only, or is `docker compose` plus integration E2E enough for this change?
- Should the runbook document `AUTH_COOKIE_SECURE` explicitly as production `true` and local override behavior, since cookie security is part of the deployment hardening rationale?

## Checks Run

- `openspec status --change harden-for-aws-deploy --json`
- `openspec validate harden-for-aws-deploy`

## Next Command

`/dual-opus confirm-plan harden-for-aws-deploy`
