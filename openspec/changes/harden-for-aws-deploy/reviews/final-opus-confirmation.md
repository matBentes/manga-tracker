# Opus Final Confirmation: harden-for-aws-deploy

## Verdict

needs-gpt-fix

Markdown Lint is now green (GPT wrapped the 3 README bare URLs in `d363ea9`). All CI gates pass on PR #25. However, the **Codex PR review surfaced a valid P2** that blocks sync: the runbook's ALB health check targets `/actuator/health` on the frontend nginx, but nginx does not proxy that path. One implementation fix required before sync. No spec/design change.

## PR / CI evidence (PR #25)

<https://github.com/matBentes/manga-tracker/pull/25> — branch `harden-for-aws-deploy` → `main`.

Settled check results:

- **Tests** (backend, Testcontainers): **pass** 2m11s — confirms the 3 local `*RepositoryTest` failures were Docker-daemon-unavailable environmental only; they pass in CI where Docker is present.
- **E2E Integration Tests**: **pass** 2m26s — the cross-service nginx → backend forwarded-header + auth/health smoke path (task 5.3) is green in CI.
- **E2E**: **pass** 1m15s.
- **Format & Lint** (backend spotless/build): **pass** 1m12s.
- **CodeQL (java)**: **pass** 2m4s. **CodeQL (javascript-typescript)**: **pass** 54s.
- **SonarCloud Code Analysis**: **pass** 25s — no new issues on PR #25.
- **Markdown Lint**: **pass** — GPT wrapped the 3 README bare URLs in angle brackets (`d363ea9`).

## Blocking finding — Codex P2 (valid)

Codex inline comment on `docs/aws-deployment.md:99`: the ALB targets the frontend/nginx container, but `frontend/nginx.conf` only proxies `/api/`; `/actuator/health` falls through the SPA fallback (`location / { try_files $uri $uri/ /index.html; }`) and returns `index.html` with HTTP 200.

Verified against the working tree — `frontend/nginx.conf` has exactly two locations (`/api/` proxy + `/` SPA fallback), no `/actuator/health`. Consequences:

- `docs/aws-deployment.md:99` "Target group health check path `/actuator/health`" with matcher `200` passes blindly on the SPA fallback and never validates backend/RDS health.
- `docs/aws-deployment.md:111` "Verify `/actuator/health` returns `UP` through the ALB" cannot work — the ALB path returns HTML, not `{"status":"UP"}`.

This is a real inconsistency between the runbook architecture (single ALB → frontend nginx, line 7/14) and the proxy config. Sync precondition "Codex PR review + accepted fixes complete" is therefore not met.

### Decision (accept)

Fix in `frontend/nginx.conf`: add a dedicated location that proxies `/actuator/health` to `http://backend:8080`, with the same forwarded-header set as `/api/`. Rationale:

- Keeps the single-ALB-entry shape coherent: the frontend target group health check on `/actuator/health` now actually reaches the backend.
- Makes the deploy verify step (`:111`) honest — `UP` returns through the ALB.
- Anonymous-safe: backend already returns only `{"status":"UP"}` to unauthenticated callers (`show-details=when-authorized`), so exposing the path adds no detail leak.
- No runbook edit needed; smaller, more correct diff than re-pointing health checks at a separate backend target group.

After the fix: re-run the `run-e2e-integration.sh` smoke (curl `/actuator/health` through nginx on `4200`, assert no `components`/`db`/`diskSpace`), let CI re-run green, then return to final-review → sync.

## Quality (thermo-nuclear bar)

Unchanged from `implementation-opus-review.md`: no structural debt, minimal diffs, sound boundary handling. CI corroborates correctness end-to-end (backend Testcontainers + cross-service integration both green). The only outstanding item is the nginx health-proxy fix above.

## Remaining before `/dual-opus sync`

1. GPT adds the `/actuator/health` nginx proxy → resolves Codex P2, CI re-runs green.
2. Optionally extend the integration smoke to curl `/actuator/health` through nginx (`4200`) rather than backend `8080` directly, so the proxy path is regression-guarded.

## Next Command

```text
/dual-gpt fix harden-for-aws-deploy
```
