# Step 6 — Smoke-test script and checklist

Run `tests/smoke.test.ts` against a live **staging** origin only. Credentials and the
payslip line ID come exclusively from GitHub Secrets (CI) or a local git-ignored
env file — never from this repository.

```bash
SMOKE_BASE_URL=https://staging-hrms.micromap.in \
SMOKE_USERS='{"EMPLOYEE":{"email":"...","password":"...","companyId":"..."},"MANAGER":{...},"HR":{...},"FINANCE":{...},"ADMIN":{...}}' \
SMOKE_PAYSLIP_LINE_ID=<finalized payroll line id> \
node --import tsx --test tests/smoke.test.ts
```

## Checklist

### Authentication and sessions
- [ ] **Every role** (EMPLOYEE, MANAGER, HR, FINANCE, ADMIN) can log in via `POST /api/auth/login` with an `Origin` header and receives `201`.
- [ ] `GET /api/auth/me` returns the matching `role` and only memberships for the logged-in companies.
- [ ] Session cookie `aster_session` carries `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/`.
- [ ] `POST /api/auth/logout` returns `200`, clears `aster_session` (`Max-Age=0`), and `GET /api/auth/me` returns `401` afterwards.

### Tenant isolation
- [ ] **Forged `x-company-id`**: an authenticated request with a tenant ID the user is not a member of returns `403`.
- [ ] **Employee-to-Admin denial**: an EMPLOYEE calling admin-only writes (`POST /api/members`, `POST /api/onboarding`) returns `403`.
- [ ] Role-scoped reads return only rows for `x-company-id` (dashboard, employees, requests, payroll lines within the tenant).

### Origin and CSRF-style protection
- [ ] **Missing `Origin`**: `POST /api/auth/login` and state-changing writes (e.g. `POST /api/requests`) without `Origin` return `403`.
- [ ] Foreign origin is not reflected in `Access-Control-Allow-Origin` (CORS preflight).

### Rate limiting
- [ ] **429 lockout**: repeated failed logins against a unique smoke email reach HTTP `429` (Redis rate limit, ~20 attempts per 15 minutes per IP+email). Use a unique email per run; never lock out real accounts.

### Documents
- [ ] **Payslip PDF**: `GET /api/payroll/lines/{id}/payslip.pdf` returns `200`, `Content-Type: application/pdf`, body starts with `%PDF`. Accessible to FINANCE/ADMIN and the employee's own payslip; denied to other tenants/roles.

### Android flows (login + writes)
- [ ] **Android login**: `POST /api/auth/login` with `Origin` + `x-company-id` sets `aster_session`; the OkHttp `SessionCookieJar` retains it and sends it on subsequent requests (`auth/me` returns `200`).
- [ ] **Android write flow**: as EMPLOYEE, `POST /api/requests` with cookie + `Origin` + `x-company-id` returns `201`; `GET /api/requests` returns `200`.
- [ ] **Android public flow**: `POST /api/auth/request-access` works without a session and returns `200`.

### Environment notes
- [ ] Run against staging (`staging-hrms.micromap.in`), never production for credential-based checks.
- [ ] Create smoke users per role on staging before the first run; rotate their passwords after each run.
- [ ] `SMOKE_USERS`, `SMOKE_PAYSLIP_LINE_ID` live in GitHub Secrets only (CI) or a local file excluded by `.gitignore`.
- [ ] Any failure blocks the release; do not promote the staging image to production until the checklist passes.
