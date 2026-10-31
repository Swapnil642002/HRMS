# Step 2 preparation (not deployed)

Ubuntu 24.04 LTS, Caddy, production `hrms.micromap.in`, staging
`staging-hrms.micromap.in`. Hosting provider and whether production already has
data are still unconfirmed. Do not execute a production migration or bootstrap
until the existing database and migration history have been assessed.

## Configuration

Copy the blank example and supply secrets privately. API requires NODE_ENV,
DATABASE_URL, REDIS_URL, APP_ORIGIN and, in production, TRUST_PROXY. The worker
also requires SMTP_HOST, MAIL_FROM and MAIL_MESSAGE_DOMAIN. SMTP authentication
requires SMTP_PASSWORD when SMTP_USER is supplied. SMTP_PORT defaults to 587.
S3 storage is optional; when enabled supply S3_BUCKET, S3_REGION and scoped AWS
credentials through the SDK credential chain. No default admin is created.
For existing local development environments add NODE_ENV=development explicitly.
Do not run database integration scripts against a shared or production database.

Production API and worker validate configuration and ping Redis before starting;
PostgreSQL must also connect successfully. Errors do not echo environment values.
The API binds only after dependency initialization. `/health` and `/api/health`
check PostgreSQL; these do not constitute an end-to-end worker/SMTP check.
Swagger is not installed or mounted. Sessions are HttpOnly, Secure in production,
SameSite=Strict, host-only, eight hours. Cookie deletion uses the same attributes.
Login throttling retains its 20 attempts per 15 minutes policy, uses an atomic
Redis increment/expiry operation, and responds with HTTP 429.

## Proxy boundary

Only Caddy's fixed address on the dedicated edge network is trusted. API and web
ports are not published. Caddy is the first public HTTP hop and overwrites the
client forwarding headers. Do not insert a CDN/load balancer without reviewing
this boundary. Production and staging on one host need distinct Compose project
names, non-overlapping subnets, databases, volumes and secrets, and a shared edge
proxy configuration; do not start two proxies bound to the same 80/443 ports.
The supplied Compose topology is one environment per host.

## Biometric review

POST /api/biometric/:deviceId skips Origin validation but always throws 403.
It has no device signature, shared-key verification or replay protection; it is
safe only because ingestion is disabled. Do not enable it without a separate
authenticated device protocol review. This route and all attendance rules remain
unchanged. The separate /api/attendance passkey routes use AuthGuard, tenant
membership, approved credentials, session-bound expiring single-use challenges,
and WebAuthn origin/RP/user verification. Origin alone is not authentication.

## Controlled migration and bootstrap

Use the tools image for `prisma migrate deploy` as a deliberate one-off release
step, after backup and staging rehearsal; never run migrations in API startup.
Never run migrate reset or db push in production. The npm db:push wrapper permits
only explicit development mode with a loopback database; Prisma CLI is absent
from the runtime image. Operators with direct tools-image access can still run
Prisma commands, so production credential access must be restricted.

Bootstrap is a separate manual operation on an empty user database. It requires
an explicit real administrator identity, name and company, rejects demo domains,
and serializes concurrent attempts under a database lock. Passwords are hashed
before the transaction and are never logged. Runtime processes reject a supplied
ADMIN_PASSWORD. Do not retain bootstrap credentials in runtime files or CI.
The tools image contains the bootstrap entry point; no service starts it by default.
A reviewed interactive command procedure is supplied with the Compose files.

## Validation scope

`npm run test:security` builds the API and tests real local HTTP requests through
Nest/Express using in-memory database and Redis doubles. It covers forged tenant
headers, Employee access to Admin endpoints, missing/wrong Origin, cookie flags,
logout, HTTP 429, proxy trust, health, disabled device ingestion, environment
validation and bootstrap identity checks. It does not prove live database/Redis,
TLS, mail delivery or migration behavior. Those require isolated staging checks.
Run `npm test` and `npm run typecheck` as well.
