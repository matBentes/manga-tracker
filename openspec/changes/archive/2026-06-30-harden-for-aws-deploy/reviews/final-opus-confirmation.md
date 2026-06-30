# Opus Final Confirmation: harden-for-aws-deploy

## Verdict

approved

Codex P2 resolved and all CI green on PR #25 head `589cb6e`. GPT added the `/actuator/health` nginx proxy as prescribed; the runbook's single-ALB health-check path is now coherent. No spec/design change. Implementation and runbook are merge-ready.

## PR / CI evidence (PR #25, head `589cb6e`)

<https://github.com/matBentes/manga-tracker/pull/25> — branch `harden-for-aws-deploy` → `main`. MERGEABLE.

Settled check results (re-run on `589cb6e`):

- **Tests** (backend, Testcontainers): **pass** 2m4s — confirms the 3 local `*RepositoryTest` failures were Docker-daemon-unavailable environmental only; they pass in CI where Docker is present.
- **E2E Integration Tests**: **pass** 2m20s — full nginx → backend stack boots with the new `/actuator/health` location; cross-service forwarded-header + auth/health smoke path (task 5.3) green.
- **E2E**: **pass** 1m10s.
- **Format & Lint** (backend spotless/build): **pass** 1m6s.
- **CodeQL (java)**: **pass** 2m34s. **CodeQL (javascript-typescript)**: **pass** 50s.
- **SonarCloud Code Analysis**: **pass** 21s — no new issues on PR #25.
- **Markdown Lint**: **pass** — README bare URLs fixed (`d363ea9`).
- **Build & Deploy** / **Label Autofix PRs**: skipping (expected on PR).

## Resolved finding — Codex P2 (fixed in `589cb6e`)

Codex inline comment on `docs/aws-deployment.md:99`: the ALB targets the frontend/nginx container, but `frontend/nginx.conf` only proxies `/api/`; `/actuator/health` falls through the SPA fallback (`location / { try_files $uri $uri/ /index.html; }`) and returns `index.html` with HTTP 200.

Verified against the working tree — `frontend/nginx.conf` has exactly two locations (`/api/` proxy + `/` SPA fallback), no `/actuator/health`. Consequences:

- `docs/aws-deployment.md:99` "Target group health check path `/actuator/health`" with matcher `200` passes blindly on the SPA fallback and never validates backend/RDS health.
- `docs/aws-deployment.md:111` "Verify `/actuator/health` returns `UP` through the ALB" cannot work — the ALB path returns HTML, not `{"status":"UP"}`.

This is a real inconsistency between the runbook architecture (single ALB → frontend nginx, line 7/14) and the proxy config. Sync precondition "Codex PR review + accepted fixes complete" is therefore not met.

### Fix applied (`589cb6e`, "fix: proxy health check through nginx")

GPT added a dedicated `location = /actuator/health` to `frontend/nginx.conf` that proxies to `http://backend:8080` with the same forwarded-header set as `/api/` (exact match `=`, so no other `/actuator/*` path is exposed). Verified against the working tree:

```nginx
location = /actuator/health {
    proxy_pass http://backend:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-Proto $http_x_forwarded_proto;
    proxy_set_header X-Forwarded-Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
}
```

Why this resolves P2:

- Single-ALB-entry shape now coherent: the frontend target group health check on `/actuator/health` actually reaches the backend instead of the SPA fallback.
- Deploy verify step (`docs/aws-deployment.md:111`) is honest — `UP` returns through the ALB, not `index.html`.
- Anonymous-safe: backend returns only `{"status":"UP"}` to unauthenticated callers (`show-details=when-authorized`), so exposing the exact path leaks no detail.
- No runbook edit needed; smaller, more correct diff than re-pointing health checks at a separate backend target group.
- E2E Integration Tests green on `589cb6e` proves the full nginx → backend stack boots with the new location (config syntactically valid in a live run).

## Quality (thermo-nuclear bar)

Unchanged from `implementation-opus-review.md`: no structural debt, minimal diffs, sound boundary handling. The nginx fix is a 8-line additive block mirroring the existing `/api/` proxy — no new abstraction, no boundary leak. CI corroborates correctness end-to-end (backend Testcontainers + cross-service integration both green). No outstanding items.

## Next Command

```text
/dual-opus sync harden-for-aws-deploy
```
