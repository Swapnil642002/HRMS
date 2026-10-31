# Step 7 — Error tracking, uptime checks, backup-failure alerts

Prepared documentation only. No endpoint or provider is configured by this file.

## 1. Error tracking — sensitive-data filtering

Error tracking (e.g. Sentry or an equivalent provider) is hooked into the API's
global exception filter (`apps/api/src/main.ts`, `Errors` filter) and the worker's
job handler. The integration **must** apply `beforeSend`-style filtering so that
no salary or personal data ever leaves the server:

- **Never send**: request bodies, request headers, cookies, `x-company-id` values,
  email addresses, names, employee codes, salary components
  (`basic`, `allowance`, `pfEmployee`, `pfEmployer`, `esicEmployee`, `esicEmployer`,
  `tds`, `professionalTax`, `net`), password hashes, password-reset tokens, or
  document/S3 keys.
- **Safe to send**: environment, release image digest, HTTP status code, route
  pattern (not query parameters), error class, generic server-safe message,
  timestamp, and an opaque internal reference (e.g. audit-row ID) that lets an
  operator correlate with database audit logs without exposing PII.
- **Worker jobs**: enqueue only opaque IDs; the error handler must not serialize
  job payloads containing payslip or personal data.
- **Logging**: `console.error` in the global filter must not dump request objects
  or database rows. Log error class and route only.

Before enabling any provider, verify with a test event that contains salary and
personal fields and confirm nothing of it appears in the provider UI or logs.

## 2. Uptime checks

- External monitor (outside the VPC, on a different network than the host) polls
  `https://staging-hrms.micromap.in/health` (production: `https://hrms.micromap.in/health`)
  every 2 minutes.
- Alert on: 2 consecutive non-200 responses, connection failure, or response time
  above 10 seconds.
- The `/health` endpoint performs a `SELECT 1` against Postgres, so it also
  validates database connectivity.
- Periodically re-run the runbook's external `nmap` check (ports 22/80/443 only;
  5432/6379 must be closed) to confirm Postgres and Redis remain off the public
  interface.

## 3. Backup-failure alerts

The nightly encrypted backup runs via `deploy/server/backup.sh` under
`hrms-backup.timer`/`hrms-backup.service`:

- `OnFailure=` on the timer unit sends an alert (email/pager per on-call rota)
  when `backup.sh` exits non-zero.
- A separate daily check verifies `restic snapshots` lists at least one snapshot
  newer than 26 hours (RPO is 24 hours); alert if the newest snapshot is older.
- Weekly `restic check` on both local and remote repositories; alert on failure.
- Alert payloads must never contain backup contents, repository paths with
  credentials, or encryption keys — only the job name, exit code, and timestamp.

## 4. Severity and response

| Signal | Severity | First response |
|--------|----------|----------------|
| `/health` failing | Critical | On-call engineer; check Caddy/API/Postgres/Redis container status |
| Backup failure | High | Inspect `journalctl -u hrms-backup.service`; verify encryption key availability |
| Sensitive data in error tracker | Critical | Disable the event, rotate any leaked values, fix the filter, redeploy |
