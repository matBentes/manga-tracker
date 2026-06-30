# Opus Sync Confirmation: harden-for-aws-deploy

## Status

Ready to archive. All sync preconditions met.

## Preconditions (all satisfied)

- **Codex PR review**: P2 (nginx `/actuator/health` proxy) surfaced and resolved in `589cb6e`.
- **Accepted fixes**: nginx health-proxy fix landed; README bare-URL fixes (`d363ea9`).
- **CI**: all gates green on PR #25 head `589cb6e` (Tests, E2E, E2E Integration, Format & Lint, CodeQL java+js, SonarCloud, Markdown Lint).
- **Opus final confirmation**: `final-opus-confirmation.md` verdict `approved`.

## Delta reconciliation

`openspec validate harden-for-aws-deploy --strict` → **valid**.

5 deltas, all `ADDED` to the new `production-deployment` capability:

1. Reverse proxy forwards request scheme; backend trusts forwarded headers (HTTPS detection behind TLS-terminating LB).
2. Anonymous `/actuator/health` exposes liveness only; component detail withheld unless authorized.
3. (+ remaining production-deployment requirements — secrets injection, SG chain, single-replica scraper, RDS private subnets per `specs/production-deployment/spec.md`.)

`production-deployment` does **not** exist in main specs yet (`openspec list --specs` → only `api-documentation`). No conflicting/overlapping requirement — these are pure additions. Folding deltas into main specs is conflict-free; `archive` will create `openspec/specs/production-deployment/spec.md`.

## Next Command

```text
/dual-opus archive harden-for-aws-deploy
```
