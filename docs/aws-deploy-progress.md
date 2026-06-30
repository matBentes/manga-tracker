# AWS Manual Deploy — Progress / Resume

> Personal handoff notes for the **manual, console-driven** first deploy (learning pass).
> Canonical prod shape: [`aws-deployment.md`](./aws-deployment.md). Spec: `openspec/specs/production-deployment/spec.md`.
> This file tracks **what is done** and **what to do next** so a fresh Claude Code session can resume.
> To resume: tell Claude "continue AWS manual deploy" and point it at this file.

## Context

First-ever AWS deploy, done **manually via console** to learn the stack; convert to IaC (Terraform/CDK) afterward. PR #25 (`harden-for-aws-deploy`) merged to `main` 2026-06-30 (forwarded headers, nginx `/actuator/health` proxy, Secrets Manager task-def shape, SG chain, single-replica scraper).

## Decisions locked

- **Registry:** Amazon ECR.
- **Domain/TLS:** none yet → **HTTP-only on :80 first** to learn; add domain + ACM HTTPS later.
- **Region:** `sa-east-1` (São Paulo).
- While HTTP-only: set `AUTH_COOKIE_SECURE=false` so login works. Flip to `true` when HTTPS added.
- Backend task min **1 vCPU / 2 GB** (image bundles Google Chrome for Playwright scraping).
- **Single backend replica only** (scraper has no leader lock).

## Roadmap (one phase per turn; click console, report, verify before next)

```
0. Prereqs   — IAM admin user (not root), AWS CLI installed+configured   <-- CURRENT
1. ECR       — 2 repos (backend, frontend), build + push images
2. Network   — default VPC to start (has public subnets)
3. RDS       — PostgreSQL 16 instance + security group
4. Secrets   — Secrets Manager entries (JWT_SECRET, DB_URL/USER/PASS, VAPID x3, OWNER/DEMO pw)
5. Sec groups— ALB -> frontend -> backend -> RDS:5432 chain
6. ECS       — cluster, backend task def (1 vCPU/2GB, has Chrome), frontend task def, service
7. ALB       — HTTP :80 listener, target group health /actuator/health matcher 200
8. Verify    — /actuator/health UP through ALB, login works
```

## Phase 0 status — Prereqs

Migrating to a **Linux machine** to continue (Windows attempt abandoned: AWS CLI was installed there but never configured).

On Linux do:

1. Clone/pull repo `main`.
2. Install AWS CLI v2:
   ```bash
   curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o awscliv2.zip
   unzip awscliv2.zip && sudo ./aws/install && aws --version
   ```
3. Install Docker (needed for ECR image build/push):
   ```bash
   sudo apt update && sudo apt install -y docker.io
   sudo usermod -aG docker $USER   # log out/in after
   ```
4. In AWS console: create IAM user `manga-deploy` (AdministratorAccess for now, not root), make a CLI access key. Same key works on any machine.
5. Configure:
   ```bash
   aws configure   # key, secret, region sa-east-1, output json
   ```

**Resume gate:** `aws sts get-caller-identity` must return account ID + `manga-deploy` ARN. Once it does → start **Phase 1 (ECR)**.

## Phase 1 preview — ECR (next)

```bash
ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
REGION=sa-east-1
aws ecr create-repository --repository-name manga-tracker-backend  --region $REGION
aws ecr create-repository --repository-name manga-tracker-frontend --region $REGION
aws ecr get-login-password --region $REGION | docker login --username AWS --password-stdin $ACCOUNT.dkr.ecr.$REGION.amazonaws.com
# build + tag + push each image to $ACCOUNT.dkr.ecr.$REGION.amazonaws.com/<repo>:latest
```
(Exact build context / Dockerfile paths: confirm against `backend/` and `frontend/` before pushing.)
