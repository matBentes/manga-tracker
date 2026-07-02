# My First AWS Deploy — A Manual, Console-Driven Journey

Notes from deploying a Spring Boot + Angular app (manga-tracker) to AWS by hand, learning
the stack before reaching for Terraform (HashiCorp's infrastructure-as-code tool) or CDK
(AWS's own Cloud Development Kit, an alternative to Terraform for the same job). Written up
as I went so I don't forget how the pieces fit together.

> **Status: in progress.** This covers the steps taken so far — prereqs through creating the
> security group chain, though the actual ingress rules for that chain are still pending (see
> "What's left"). ECS, the load balancer, and end-to-end verification are still ahead too.
> I'll fill in the rest as I get to it rather than back-dating this post to look finished.

**Stack:** Spring Boot backend (Docker, bundles headless Chrome for scraping), Angular
frontend (nginx), PostgreSQL. **Target:** ECS (Elastic Container Service) Fargate — AWS's
"serverless" container runner, no EC2 servers to manage — + RDS (Relational Database
Service) + ALB (Application Load Balancer, the entry point that routes internet traffic to
the right container). HTTP-only first (no domain/TLS — Transport Layer Security, i.e.
HTTPS — yet), region `sa-east-1`.

> IDs below (`123456789012`, `vpc-0abc123456789ef01`, `subnet-0abc123456789ef01`, etc.) are
> made-up examples in AWS's usual format — not my real resource IDs, which are
> account-specific and not worth publishing. Swap in whatever your own commands print out;
> each section also links the AWS console page where you can see/verify the same resource if
> you'd rather click than type.

## 0. Prerequisites

- IAM (Identity and Access Management) user for CLI work, **not the root account**: console → IAM → Users → create user,
  attach `AdministratorAccess` (fine for a learning pass, scope down later), generate a CLI
  access key.
  - **Web:** [IAM → Users](https://console.aws.amazon.com/iam/home#/users) — the new user
    and its access key show up here.
- AWS CLI v2:

  ```bash
  curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o awscliv2.zip
  unzip awscliv2.zip && sudo ./aws/install && aws --version
  ```

- Docker (for building/pushing images).
- Configure the CLI and confirm identity:

  ```bash
  aws configure   # access key, secret, region, output format
  aws sts get-caller-identity   # should return your account ID + IAM user ARN (Amazon Resource Name, AWS's unique ID format for any resource)
  ```

## 1. ECR — push the images

ECR (Elastic Container Registry) is AWS's private Docker registry (think private Docker
Hub). ECS pulls images from here.

- **Web:** [ECR → Repositories](https://console.aws.amazon.com/ecr/repositories?region=sa-east-1) —
  after each `create-repository` call, the repo appears here; after each push, click into it
  to see the image tag, digest, size, and push date.

```bash
aws ecr create-repository --repository-name manga-tracker-backend  --region sa-east-1
aws ecr create-repository --repository-name manga-tracker-frontend --region sa-east-1

aws ecr get-login-password --region sa-east-1 \
  | docker login --username AWS --password-stdin 123456789012.dkr.ecr.sa-east-1.amazonaws.com
```

Build, tag, and push the backend image:

```bash
cd backend
docker build --platform linux/amd64 -t manga-tracker-backend .
docker tag manga-tracker-backend:latest 123456789012.dkr.ecr.sa-east-1.amazonaws.com/manga-tracker-backend:latest
docker push 123456789012.dkr.ecr.sa-east-1.amazonaws.com/manga-tracker-backend:latest
```

Then the same three steps for the frontend image:

```bash
cd ../frontend
docker build --platform linux/amd64 -t manga-tracker-frontend .
docker tag manga-tracker-frontend:latest 123456789012.dkr.ecr.sa-east-1.amazonaws.com/manga-tracker-frontend:latest
docker push 123456789012.dkr.ecr.sa-east-1.amazonaws.com/manga-tracker-frontend:latest
```

**Gotcha:** `--platform linux/amd64` matters if you're building on ARM — Fargate expects
x86_64 by default. Also: re-running a push after an interruption creates a new **untagged**
image alongside the `latest` one each time. Harmless, just prune them later — check the
`latest` tag actually points at the image you expect before moving on.

## 2. Network — use the default VPC

No need to build a custom VPC (Virtual Private Cloud — your own isolated network in AWS) for
a first pass — the default VPC already has subnets across multiple Availability Zones (AZs:
physically separate data centers within the region) with a route to the internet.

- **Web:** [VPC → Your VPCs](https://console.aws.amazon.com/vpc/home?region=sa-east-1#vpcs:)
  and [VPC → Subnets](https://console.aws.amazon.com/vpc/home?region=sa-east-1#subnets:) —
  filter subnets by VPC ID to see one row per Availability Zone.

```bash
aws ec2 describe-vpcs --filters "Name=is-default,Values=true" --region sa-east-1
aws ec2 describe-subnets --filters "Name=vpc-id,Values=vpc-0abc123456789ef01" --region sa-east-1
```

Security comes from security groups, not from hiding in a private subnet — see below.

## 3. RDS — PostgreSQL, not publicly accessible

- **Web:** [RDS → Databases](https://console.aws.amazon.com/rds/home?region=sa-east-1#databases:)
  shows instance status (`Creating` → `Available`); [RDS → Subnet
  groups](https://console.aws.amazon.com/rds/home?region=sa-east-1#db-subnet-groups:) shows
  the subnet group.

RDS needs a **DB subnet group** spanning ≥2 AZs first. Use two of the subnet IDs from step
2's `describe-subnets` output — pick ones in different AZs (e.g. one from `sa-east-1a`, one
from `sa-east-1b`):

```bash
aws rds create-db-subnet-group \
  --db-subnet-group-name manga-tracker-subnet-group \
  --db-subnet-group-description "Subnets for manga-tracker RDS" \
  --subnet-ids subnet-0abc123456789ef01 subnet-0def456789abc1234 \
  --region sa-east-1
```

Then a dedicated security group (rules added once the backend's SG exists — chicken/egg,
solved in step 5). This call prints a `GroupId` — that's the `sg-...` value the next command
needs. `--storage-type gp3` below is just AWS's default/cheapest general-purpose SSD tier:

```bash
aws ec2 create-security-group --group-name manga-tracker-rds-sg \
  --description "RDS PostgreSQL access from backend only" --vpc-id vpc-0abc123456789ef01 --region sa-east-1
# -> { "GroupId": "sg-0111222333444a555" }
```

```bash
aws rds create-db-instance \
  --db-instance-identifier manga-tracker-db \
  --db-instance-class db.t4g.micro \
  --engine postgres --engine-version 16 \
  --master-username manga_tracker \
  --manage-master-user-password \
  --allocated-storage 20 --storage-type gp3 \
  --db-subnet-group-name manga-tracker-subnet-group \
  --vpc-security-group-ids sg-0111222333444a555 \
  --no-publicly-accessible \
  --backup-retention-period 1 --no-multi-az \
  --db-name manga_tracker --region sa-east-1
```

**Key choice:** `--manage-master-user-password` skips typing a master password yourself —
AWS generates one and stores it in Secrets Manager automatically. One less secret to handle
by hand.

Even though the default VPC's subnets are technically "public," `--no-publicly-accessible`
plus a locked-down security group means RDS is unreachable from the internet regardless.

Provisioning takes several minutes; poll with:

```bash
aws rds describe-db-instances --db-instance-identifier manga-tracker-db --region sa-east-1 \
  --query "DBInstances[0].{Status:DBInstanceStatus,Endpoint:Endpoint.Address}"
```

## 4. Secrets Manager

- **Web:** [Secrets Manager →
  Secrets](https://console.aws.amazon.com/secretsmanager/listsecrets?region=sa-east-1) —
  the RDS-managed master password secret and the app secret both show up here; click one and
  "Retrieve secret value" to see the actual JSON.

Required backend secrets: `JWT_SECRET` (signs login session tokens; anyone with this value
could forge a valid login), `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`,
`VAPID_PUBLIC_KEY`/`VAPID_PRIVATE_KEY`/`VAPID_SUBJECT` (VAPID = Voluntary Application Server
Identification — these keys authenticate the app to push services for Web Push
notifications), and optionally
`OWNER_PASSWORD`/`DEMO_PASSWORD` for seed accounts. None of these get baked into the Docker
image — they're injected at container start via the ECS task definition's `secrets` block.

Generate values locally:

```bash
openssl rand -base64 48                        # JWT_SECRET
npx web-push generate-vapid-keys --json         # VAPID keys
openssl rand -base64 18                         # a password
```

Bundle everything except the DB password into one secret (the DB password already lives in
the RDS-managed secret from step 3 — no need to duplicate it):

```bash
aws secretsmanager create-secret \
  --name manga-tracker/app \
  --region sa-east-1 \
  --secret-string '{
    "JWT_SECRET": "...",
    "VAPID_PUBLIC_KEY": "...",
    "VAPID_PRIVATE_KEY": "...",
    "VAPID_SUBJECT": "mailto:you@example.com",
    "OWNER_PASSWORD": "...",
    "DEMO_PASSWORD": "...",
    "DB_URL": "jdbc:postgresql://manga-tracker-db.abc123xyz456.sa-east-1.rds.amazonaws.com:5432/manga_tracker",
    "DB_USERNAME": "manga_tracker"
  }'
```

Once stored, values are retrievable anytime (`aws secretsmanager get-secret-value` or via
console) — no risk of losing a randomly generated password.

## 5. Security group chain

- **Web:** [EC2 → Security
  Groups](https://console.aws.amazon.com/ec2/home?region=sa-east-1#SecurityGroups:) — click
  a group's **Inbound rules** tab to confirm the source is another security group, not a
  CIDR range (CIDR = Classless Inter-Domain Routing, the notation for an IP range — e.g.
  `0.0.0.0/0` means "any IP").

The only thing that should ever be open to the raw internet is the load balancer. Everything
else should only accept traffic from the tier directly in front of it:

```text
internet -> ALB (:80, 0.0.0.0/0)
         -> frontend SG (:80, from ALB SG only)
         -> backend SG (:8080, from frontend SG only)
         -> RDS SG (:5432, from backend SG only)
```

Each `create-security-group` call prints its own `GroupId` — collect the three before running
the ingress rules (reusing `sg-0111222333444a555` from step 3 for RDS):

```bash
aws ec2 create-security-group --group-name manga-tracker-alb-sg      --description "ALB - public HTTP"             --vpc-id vpc-0abc123456789ef01 --region sa-east-1
# -> { "GroupId": "sg-0aaa111bbb222c333" }
aws ec2 create-security-group --group-name manga-tracker-frontend-sg --description "Frontend ECS - from ALB only"  --vpc-id vpc-0abc123456789ef01 --region sa-east-1
# -> { "GroupId": "sg-0bbb222ccc333d444" }
aws ec2 create-security-group --group-name manga-tracker-backend-sg  --description "Backend ECS - from frontend only" --vpc-id vpc-0abc123456789ef01 --region sa-east-1
# -> { "GroupId": "sg-0ccc333ddd444e555" }
```

```bash
aws ec2 authorize-security-group-ingress --group-id sg-0aaa111bbb222c333 --protocol tcp --port 80   --cidr 0.0.0.0/0                     --region sa-east-1
aws ec2 authorize-security-group-ingress --group-id sg-0bbb222ccc333d444 --protocol tcp --port 80   --source-group sg-0aaa111bbb222c333 --region sa-east-1
aws ec2 authorize-security-group-ingress --group-id sg-0ccc333ddd444e555 --protocol tcp --port 8080 --source-group sg-0bbb222ccc333d444 --region sa-east-1
aws ec2 authorize-security-group-ingress --group-id sg-0111222333444a555 --protocol tcp --port 5432 --source-group sg-0ccc333ddd444e555 --region sa-east-1
```

The `--source-group` flag (instead of a CIDR) is what makes this a *chain*: each rule allows
traffic only from another security group, not from an IP range. Even if you learned a
backend task's private IP, its security group would still reject a direct connection that
didn't come from the frontend tier.

## What's left

- **Finish wiring the security group chain** — the four groups exist, but as of this
  writing I haven't run the `authorize-security-group-ingress` calls yet. That's the very
  next thing.
- **ECS**: cluster, backend task definition (min 1 vCPU / 2GB — the image bundles headless
  Chrome for Playwright-based scraping), frontend task definition, services using the SGs
  above.
- **ALB**: HTTP `:80` listener, target group health check on `/actuator/health` expecting `200`.
- **Verify**: `/actuator/health` returns `UP` through the ALB, login works end-to-end.
- Later: buy a domain, add an ACM (AWS Certificate Manager) certificate, move ALB to HTTPS,
  flip `AUTH_COOKIE_SECURE=true`.

## Cost note

None of this is fully free. ECR/RDS instance size chosen here fit within AWS's free-tier
eligible instance classes, but **ECS Fargate and the ALB have no free tier at all** — expect
real cost if left running continuously (I'm using AWS's newer credit-based Free Plan to
cover it during the learning phase, and scale ECS tasks to 0 / stop RDS when not actively
testing to conserve credit).
