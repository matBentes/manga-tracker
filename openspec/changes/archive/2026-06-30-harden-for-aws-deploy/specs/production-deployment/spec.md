## ADDED Requirements

### Requirement: Forwarded protocol propagation

The reverse proxy SHALL forward the original request scheme to the backend, and the backend SHALL trust forwarded headers, so that the application correctly detects HTTPS when running behind a TLS-terminating load balancer.

#### Scenario: Request arrives over HTTPS at the load balancer

- **WHEN** a browser sends an HTTPS request through the ALB and nginx to the backend
- **THEN** nginx sets `X-Forwarded-Proto: https` on the proxied request
- **AND** the backend resolves the request scheme as `https` via `server.forward-headers-strategy=framework`

#### Scenario: Secure auth cookie issued behind TLS load balancer

- **WHEN** a user logs in over HTTPS through the load balancer
- **THEN** the backend issues the auth cookie with the `Secure` attribute set
- **AND** the cookie is accepted by the browser without a scheme-mismatch warning

### Requirement: Restricted actuator health exposure

The application SHALL expose only liveness status to anonymous callers and SHALL withhold internal component detail unless the caller is authorized.

#### Scenario: Anonymous health probe

- **WHEN** an unauthenticated client requests `/actuator/health`
- **THEN** the response contains only the overall status (e.g. `{"status":"UP"}`)
- **AND** it does NOT include database, disk, or other component detail

#### Scenario: Load balancer health check passes

- **WHEN** the ALB target-group health check polls `/actuator/health`
- **THEN** it receives HTTP 200 with status `UP` while the app is healthy

### Requirement: Externalized production secrets

The application SHALL read all secrets and environment-specific connection settings from the runtime environment, and the deployment SHALL source them from AWS Secrets Manager rather than baking them into images or committing them.

#### Scenario: Missing JWT secret fails fast

- **WHEN** the backend starts without `JWT_SECRET` provided
- **THEN** startup fails immediately with a clear error rather than booting insecurely

#### Scenario: Secrets injected at runtime

- **WHEN** an ECS task starts
- **THEN** `JWT_SECRET`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and VAPID keys are injected from Secrets Manager as task-definition secrets
- **AND** none of these values are present in the container image layers

### Requirement: Managed database connectivity

The application SHALL connect to an external managed PostgreSQL instance (RDS) in production using runtime-provided connection settings, with Flyway migrations applied against that instance.

#### Scenario: Backend connects to RDS on boot

- **WHEN** the backend starts with `DB_URL` pointing at the RDS endpoint and valid credentials
- **THEN** Flyway runs pending migrations and `ddl-auto=validate` passes against the RDS schema
- **AND** the app reports `UP` once the connection pool is established

### Requirement: AWS deployment runbook

The repository SHALL document a reproducible AWS deployment using ECS Fargate, an Application Load Balancer with HTTPS, and RDS PostgreSQL.

#### Scenario: Operator follows the runbook

- **WHEN** an operator follows `docs/aws-deployment.md`
- **THEN** the document specifies the ECS service shape (CPU/memory sized for the bundled Chrome scraper), single-replica scraper constraint, ALB HTTPS listener with an ACM certificate, RDS provisioning, and Secrets Manager wiring
- **AND** it states the rollback approach for a failed deployment
