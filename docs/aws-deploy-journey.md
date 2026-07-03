# My First AWS Deploy — A Manual, Console-Driven Journey

Notes from deploying a Spring Boot + Angular app (manga-tracker) to AWS by hand, learning
the stack before reaching for Terraform (HashiCorp's infrastructure-as-code tool) or CDK
(AWS's own Cloud Development Kit, an alternative to Terraform for the same job). Written up
as I went so I don't forget how the pieces fit together.

> **Status: done.** The app is live on ECS Fargate behind an ALB, HTTP-only (no domain/TLS
> yet — see "Later"), login verified end-to-end. Written up as I went, phase by phase.

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

## 6. ECS — cluster, task definitions, services

- **Web:** [ECS → Clusters](https://console.aws.amazon.com/ecs/v2/clusters?region=sa-east-1)
  shows cluster status; click into a cluster → **Services** tab to see running/desired task
  counts; click a service → **Tasks** tab for individual task status and a link to its logs.

```bash
aws ecs create-cluster --cluster-name manga-tracker-cluster --region sa-east-1
```

**Gotcha:** if this is the first time ECS is used in the account, `create-cluster` fails with
`InvalidParameterException: Unable to assume the service linked role`. Fix once, up front:

```bash
aws iam create-service-linked-role --aws-service-name ecs.amazonaws.com
```

Every task needs an execution role (lets ECS itself pull the image and write logs — distinct
from any permissions the *application* needs at runtime):

```bash
aws iam create-role --role-name ecsTaskExecutionRole \
  --assume-role-policy-document '{
    "Version": "2012-10-17",
    "Statement": [{
      "Effect": "Allow",
      "Principal": {"Service": "ecs-tasks.amazonaws.com"},
      "Action": "sts:AssumeRole"
    }]
  }'

aws iam attach-role-policy --role-name ecsTaskExecutionRole \
  --policy-arn arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy
```

That managed policy covers ECR pulls and writing to *existing* CloudWatch log groups, but not
creating a new one. Since the task definitions below use `awslogs-create-group: true`, add an
inline policy too, or the task fails at startup with a log-group `AccessDenied`:

```bash
aws iam put-role-policy --role-name ecsTaskExecutionRole \
  --policy-name manga-tracker-logs-access \
  --policy-document '{
    "Version": "2012-10-17",
    "Statement": [{
      "Effect": "Allow",
      "Action": ["logs:CreateLogGroup"],
      "Resource": [
        "arn:aws:logs:sa-east-1:123456789012:log-group:/ecs/manga-tracker-backend:*",
        "arn:aws:logs:sa-east-1:123456789012:log-group:/ecs/manga-tracker-frontend:*"
      ]
    }]
  }'
```

The application itself also needs to read the app secret and the RDS-managed master password
secret from step 4 — another inline policy on the same role:

```bash
aws iam put-role-policy --role-name ecsTaskExecutionRole \
  --policy-name manga-tracker-secrets-access \
  --policy-document '{
    "Version": "2012-10-17",
    "Statement": [{
      "Effect": "Allow",
      "Action": ["secretsmanager:GetSecretValue"],
      "Resource": [
        "arn:aws:secretsmanager:sa-east-1:123456789012:secret:manga-tracker/app-AbCdEf",
        "arn:aws:secretsmanager:sa-east-1:123456789012:secret:rds!db-a1b2c3d4-e5f6-47a8-b9c0-d1e2f3a4b5c6-GhIjKl"
      ]
    }]
  }'
```

Register the backend task definition — the interesting parts are the `secrets` block (pulls
straight from Secrets Manager at container start, nothing baked into the image) and the port
name `backend` (used by Service Connect below):

```json
{
  "family": "manga-tracker-backend",
  "requiresCompatibilities": ["FARGATE"],
  "networkMode": "awsvpc",
  "cpu": "1024",
  "memory": "2048",
  "executionRoleArn": "arn:aws:iam::123456789012:role/ecsTaskExecutionRole",
  "containerDefinitions": [{
    "name": "backend",
    "image": "123456789012.dkr.ecr.sa-east-1.amazonaws.com/manga-tracker-backend:latest",
    "portMappings": [{ "containerPort": 8080, "name": "backend" }],
    "secrets": [
      { "name": "JWT_SECRET", "valueFrom": "arn:aws:secretsmanager:sa-east-1:123456789012:secret:manga-tracker/app-AbCdEf:JWT_SECRET::" },
      { "name": "DB_URL", "valueFrom": "arn:aws:secretsmanager:sa-east-1:123456789012:secret:manga-tracker/app-AbCdEf:DB_URL::" }
    ],
    "logConfiguration": {
      "logDriver": "awslogs",
      "options": {
        "awslogs-group": "/ecs/manga-tracker-backend",
        "awslogs-region": "sa-east-1",
        "awslogs-stream-prefix": "ecs",
        "awslogs-create-group": "true"
      }
    }
  }]
}
```

```bash
aws ecs register-task-definition --cli-input-json file://backend-task-def.json --region sa-east-1
```

The frontend task definition is the same shape but lighter (0.25 vCPU / 512MB, no secrets,
port named `frontend`).

**Service discovery gotcha:** `frontend/nginx.conf` proxies to the bare hostname
`http://backend:8080` — a Docker Compose convention that doesn't resolve automatically
between two separate ECS services. Rather than touch app config, use **ECS Service
Connect**: it lets the frontend service resolve `backend` by giving the backend service a
client alias of that name, pointing at the `backend`-named port from the task def above. This
needs a Cloud Map namespace, which the CLI (unlike the console) does not create for you
automatically:

```bash
aws servicediscovery create-private-dns-namespace \
  --name manga-tracker --vpc vpc-0abc123456789ef01 --region sa-east-1
# poll the returned operation-id with:
aws servicediscovery get-operation --operation-id <operation-id> --region sa-east-1
```

Then create the backend service with Service Connect enabled:

```bash
aws ecs create-service \
  --cluster manga-tracker-cluster \
  --service-name manga-tracker-backend \
  --task-definition manga-tracker-backend \
  --desired-count 1 \
  --launch-type FARGATE \
  --network-configuration '{
    "awsvpcConfiguration": {
      "subnets": ["subnet-0abc123456789ef01", "subnet-0def456789abc1234"],
      "securityGroups": ["sg-0ccc333ddd444e555"],
      "assignPublicIp": "ENABLED"
    }
  }' \
  --service-connect-configuration '{
    "enabled": true,
    "namespace": "manga-tracker",
    "services": [{
      "portName": "backend",
      "clientAliases": [{ "port": 8080, "dnsName": "backend" }]
    }]
  }' \
  --region sa-east-1
```

`assignPublicIp: ENABLED` is needed here only because the default VPC has no NAT gateway —
tasks still need a route out to reach ECR, Secrets Manager, and CloudWatch. The security
group (from step 5) still blocks all unwanted inbound traffic regardless of the public IP.

The frontend service is created the same way, minus the `services` array in Service Connect
(it only needs to *resolve* `backend`, not expose anything itself) — that comes with a
`--load-balancers` flag once the ALB exists (step 7). Both services eventually settle at
`RUNNING (1/1)`.

## 7. ALB — expose it to the internet

- **Web:** [EC2 → Load
  Balancers](https://console.aws.amazon.com/ec2/home?region=sa-east-1#LoadBalancers:) and
  [EC2 → Target
  Groups](https://console.aws.amazon.com/ec2/home?region=sa-east-1#TargetGroups:) — the
  target group's **Targets** tab shows each task's health check status live.

```bash
aws elbv2 create-load-balancer \
  --name manga-tracker-alb --type application --scheme internet-facing \
  --subnets subnet-0abc123456789ef01 subnet-0def456789abc1234 \
  --security-groups sg-0aaa111bbb222c333 --region sa-east-1

aws elbv2 create-target-group \
  --name manga-tracker-frontend-tg --protocol HTTP --port 8080 \
  --vpc-id vpc-0abc123456789ef01 --target-type ip \
  --health-check-path /actuator/health --region sa-east-1

aws elbv2 create-listener \
  --load-balancer-arn <alb-arn-from-first-command> \
  --protocol HTTP --port 80 \
  --default-actions Type=forward,TargetGroupArn=<target-group-arn> \
  --region sa-east-1
```

**Gotcha:** ECS will not let you attach a load balancer to a service that's already running —
`loadBalancers` is a create-time-only field via the API. If the frontend service already
exists from step 6, the only option (short of a blue/green cutover) is to delete it and
recreate it with `--load-balancers` set this time.

**Gotcha:** the frontend container listens on port **8080**, not 80 — the nginx-unprivileged
base image can't bind to a port below 1024. The target group above already used 8080, but the
security group rule from step 5 only opened port 80 from the ALB to the frontend. Target
health showed `Target.Timeout` until adding one more ingress rule: allow tcp/8080 on the
frontend SG, sourced from the ALB SG.

With that fixed, the target turns healthy and the Sign In page loads through the ALB's DNS
name (`manga-tracker-alb-123456789.sa-east-1.elb.amazonaws.com` in this account).

## 8. Verify — health check and login, end to end

```bash
curl http://manga-tracker-alb-123456789.sa-east-1.elb.amazonaws.com/actuator/health
```

**Gotcha:** the first real request through the ALB came back `502 Bad Gateway` from nginx.
Nothing to do with the ALB or security groups — the backend logs showed
`NoRouteToHostException` connecting to Postgres. Root cause: the RDS instance had been
stopped (a cost-saving habit from the day before) — Flyway/Hikari couldn't connect at
startup, so the Spring context never came up, so nginx's `proxy_pass` had nothing to reach.
Fixed with `aws rds start-db-instance`, waited for `available`, then forced a fresh ECS
deployment so the backend would retry the connection:

```bash
aws rds start-db-instance --db-instance-identifier manga-tracker-db --region sa-east-1
aws rds wait db-instance-available --db-instance-identifier manga-tracker-db --region sa-east-1
aws ecs update-service --cluster manga-tracker-cluster --service manga-tracker-backend \
  --force-new-deployment --region sa-east-1
```

After that, `/actuator/health` returns `200 {"status":"UP"}` and the login form works
end-to-end through the ALB's DNS name, using the seeded owner/demo credentials from the app
secret. **All 8 phases done.**

**Lesson for future-me:** if resuming this stack after a break and something looks broken,
check RDS status first — a stopped database produces the exact same 502 symptom every time,
and it's the cheapest thing to rule out before debugging anything else.

## Later

- Convert this whole manual setup to Terraform or CDK — clicking through the console once was
  worth it for learning, but it's not how I'd want to reproduce or tear this down again.
- Buy a domain, add an ACM (AWS Certificate Manager) certificate, move the ALB to HTTPS, flip
  `AUTH_COOKIE_SECURE=true`.
- A real bug surfaced during verification (CSRF cookies misbehaving on login) that turned into
  its own debugging story — Spring Security internals, reproducing with a real browser,
  instrumenting the framework to find the actual cause. Probably a separate post.

## Cost note

None of this is fully free. ECR/RDS instance size chosen here fit within AWS's free-tier
eligible instance classes, but **ECS Fargate and the ALB have no free tier at all** — expect
real cost if left running continuously (I'm using AWS's newer credit-based Free Plan to cover
it during the learning phase, and scale ECS tasks to 0 / stop RDS when not actively testing to
conserve credit — just remember to start RDS back up before debugging anything, per the
lesson above).
