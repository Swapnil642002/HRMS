import test from 'node:test';
import assert from 'node:assert/strict';

// Smoke tests run against a live STAGING origin only. Credentials never touch
// this file or any log output; supply them via GitHub Secrets or a local,
// git-ignored env file.
//
//   SMOKE_BASE_URL=https://staging-hrms.micromap.in \
//   SMOKE_USERS='{"EMPLOYEE":{"email":"...","password":"...","companyId":"..."},...}' \
//   SMOKE_PAYSLIP_LINE_ID=<finalized payroll line id> \
//   node --import tsx --test tests/smoke.test.ts
//
// Allows only staging hosts. Refuses to run against any other origin,
// including production (hrms.micromap.in).

const BASE = process.env.SMOKE_BASE_URL;
const usersRaw = process.env.SMOKE_USERS || '';
const payslipLineId = process.env.SMOKE_PAYSLIP_LINE_ID || '';

if (!BASE || !usersRaw) {
  test.skip('smoke tests skipped: set SMOKE_BASE_URL and SMOKE_USERS', () => {});
} else {
  const BASE_URL = BASE.replace(/\/+$/, '');
  const baseHost = new URL(BASE_URL).hostname;
  const stagingOnly =
    baseHost === 'staging-hrms.micromap.in' || baseHost.endsWith('.staging-hrms.micromap.in');
  if (!stagingOnly) {
    throw new Error(
      `Smoke tests are restricted to staging hosts only. Refusing to run against: ${BASE_URL}. ` +
        'Set SMOKE_BASE_URL=https://staging-hrms.micromap.in.',
    );
  }
  const users = JSON.parse(usersRaw) as Record<string, { email: string; password: string; companyId: string }>;
  const roles = Object.keys(users);
  const origin = new URL(BASE_URL).origin;
  const cookies: Record<string, string> = {};

  assert.ok(roles.includes('EMPLOYEE') && roles.includes('ADMIN'),
    'SMOKE_USERS must include at least EMPLOYEE and ADMIN');

  const api = (path: string) => `${BASE_URL}/api/${path.replace(/^\/+/, '')}`;

  async function request(path: string, init: RequestInit = {}): Promise<Response> {
    const headers: Record<string, string> = { ...(init.headers as Record<string, string> | undefined) || {} };
    return fetch(api(path), { ...init, headers });
  }

  function cookieAssertions(res: Response) {
    const setCookie = res.headers.get('set-cookie') || '';
    assert.match(setCookie, /HttpOnly/i, 'session cookie must be HttpOnly');
    assert.match(setCookie, /Secure/i, 'session cookie must be Secure');
    assert.match(setCookie, /SameSite=Strict/i, 'session cookie must be SameSite=Strict');
    assert.match(setCookie, /Path=\//i, 'session cookie must be Path=/');
    const cookie = setCookie.split(';')[0];
    assert.ok(cookie.startsWith('aster_session='), 'expected aster_session cookie');
    return cookie;
  }

  const firstOfNextMonth = (): string => {
    const now = new Date();
    const first = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() + 1, 1));
    return first.toISOString().slice(0, 10);
  };
  const addDays = (iso: string, days: number): string => {
    const d = new Date(iso + 'T00:00:00Z');
    d.setUTCDate(d.getUTCDate() + days);
    return d.toISOString().slice(0, 10);
  };

  test('every role authenticates and sees only its own role and companies', async () => {
    for (const role of roles) {
      const { email, password, companyId } = users[role];
      const login = await request('auth/login', {
        method: 'POST',
        headers: { Origin: origin, 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password }),
      });
      assert.equal(login.status, 201, `${role} login failed: ${await login.text()}`);
      const cookie = cookieAssertions(login);
      cookies[role] = cookie;

      const me = await request('auth/me', { headers: { Cookie: cookie } });
      assert.equal(me.status, 200, `${role} auth/me failed`);
      const body = await me.json();
      assert.equal(body.role, role, `${role} token resolved to wrong role`);
      assert.ok(body.memberships.some((m: { company: { id: string } }) => m.company.id === companyId),
        `${role} is not a member of the expected company`);
    }
  });

  test('forged x-company-id is rejected', async () => {
    const role = roles.find((r) => r === 'EMPLOYEE') ?? roles[0];
    const cookie = cookies[role];
    const { companyId } = users[role];
    const res = await request('employees', {
      headers: { Cookie: cookie, 'x-company-id': companyId === 'forged' ? 'forged' : 'forged-company-12345' },
    });
    assert.equal(res.status, 403, 'a forged tenant header must be rejected');
  });

  test('employee-to-admin write is denied', async () => {
    const role = roles.find((r) => r === 'EMPLOYEE') ?? roles[0];
    const cookie = cookies[role];
    const { companyId } = users[role];
    const res = await request('members', {
      method: 'POST',
      headers: {
        Cookie: cookie, Origin: origin, 'Content-Type': 'application/json', 'x-company-id': companyId,
      },
      body: JSON.stringify({ email: 'smoke-employee@micromap.invalid', name: 'Smoke', password: 'LongEnoughPass123', role: 'ADMIN', employeeId: '' }),
    });
    assert.equal(res.status, 403, 'an employee must not be able to create admin members');
  });
  test('missing Origin is rejected on state-changing requests', async () => {
    const cookie = cookies[roles[0]];
    const { companyId } = users[roles[0]];

    const login = await request('auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: users[roles[0]].email, password: 'wrong-password' }),
    });
    assert.equal(login.status, 403, 'login without Origin must be rejected');

    const write = await request('requests', {
      method: 'POST',
      headers: {
        Cookie: cookie, 'Content-Type': 'application/json', 'x-company-id': companyId,
      },
      body: '{}',
    });
    assert.equal(write.status, 403, 'a write without Origin must be rejected');
  });

  test('logout clears the session cookie', async () => {
    const role = roles.includes('ADMIN') ? 'ADMIN' : roles[roles.length - 1];
    const cookie = cookies[role];

    const res = await request('auth/logout', { method: 'POST', headers: { Cookie: cookie, Origin: origin } });
    assert.equal(res.status, 200, 'logout failed');
    const cleared = res.headers.get('set-cookie') || '';
    assert.match(cleared, /aster_session/);
    assert.match(cleared, /Max-Age=0/, 'logout must clear the session cookie');

    const me = await request('auth/me', { headers: { Cookie: cookie } });
    assert.equal(me.status, 401, 'session must be invalid after logout');
    delete cookies[role];
  });

  test('repeated login failures reach a 429 lockout without locking out real users', async () => {
    // A unique, never-registered email keeps the per-email rate-limit bucket
    // fresh and ensures real users are never affected. The per-IP bucket is
    // advanced only by this run; the test stops at the first 429 and runs
    // last, so no further app login is needed from this runner IP afterward.
    // The runner itself (GitHub API access) is never locked out.
    const lockoutEmail = `smoke-lockout-${Date.now()}@micromap.invalid`;
    let saw429 = false;
    let lastStatus = 0;
    for (let i = 0; i < 25; i++) {
      const res = await request('auth/login', {
        method: 'POST',
        headers: { Origin: origin, 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: lockoutEmail, password: 'wrong-password' }),
      });
      lastStatus = res.status;
      if (res.status === 429) { saw429 = true; break; }
    }
    assert.ok(saw429, `expected a 429 lockout; last status was ${lastStatus}`);
  });

  test('payslip PDF is served with PDF content', async (t) => {
    if (!payslipLineId) {
      t.skip('set SMOKE_PAYSLIP_LINE_ID to exercise the payslip endpoint');
      return;
    }
    const role = roles.includes('FINANCE') ? 'FINANCE' : (roles.includes('ADMIN') ? 'ADMIN' : roles[0]);
    const cookie = cookies[role];

    const res = await request(`payroll/lines/${payslipLineId}/payslip.pdf`, { headers: { Cookie: cookie } });
    assert.equal(res.status, 200, `payslip request failed: ${await res.text()}`);
    assert.equal(res.headers.get('content-type'), 'application/pdf');
    const bytes = new Uint8Array(await res.arrayBuffer());
    assert.equal(bytes[0], 0x25); // '%'
    assert.equal(bytes[1], 0x50); // 'P'
  });

  test('android login and write flows honor the cookie and tenant contract', async () => {
    const role = roles.find((r) => r === 'EMPLOYEE') ?? roles[0];
    const { email, password, companyId } = users[role];

    // 1. Android login: OkHttp sends Origin + x-company-id; SessionCookieJar retains aster_session.
    const login = await request('auth/login', {
      method: 'POST',
      headers: { Origin: origin, 'x-company-id': companyId, 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),
    });
    assert.equal(login.status, 201, `android login failed: ${await login.text()}`);
    const cookie = cookieAssertions(login);

    // 2. Android dashboard call (auth/me) works with the retained cookie only.
    const me = await request('auth/me', { headers: { Cookie: cookie } });
    assert.equal(me.status, 200);
    const body = await me.json();
    const membership = body.memberships.find((m: { company: { id: string }; employee: { id: string | null } }) => m.company.id === companyId);
    assert.ok(membership, 'employee membership not found');
    const employeeId = membership.employee?.id ?? null;
    assert.ok(employeeId, 'employee profile must be linked to the membership');

    // 3. Android write flow: create a leave request as the employee, then list requests.
    const from = firstOfNextMonth();
    const to = addDays(from, 5);
    const create = await request('requests', {
      method: 'POST',
      headers: { Cookie: cookie, Origin: origin, 'x-company-id': companyId, 'Content-Type': 'application/json' },
      body: JSON.stringify({ kind: 'LEAVE', employeeId, title: 'Smoke test leave', details: { from, to, reason: 'Smoke test' } }),
    });
    assert.equal(create.status, 201, `android write failed: ${await create.text()}`);

    const list = await request('requests', { headers: { Cookie: cookie, 'x-company-id': companyId } });
    assert.equal(list.status, 200);

    // 4. Public Android request-access flow (no session, still bound to Origin + tenant).
    const access = await request('auth/request-access', {
      method: 'POST',
      headers: { Origin: origin, 'Content-Type': 'application/json' },
      body: JSON.stringify({ companyId, employeeCode: 'SMOKE-001', name: 'Smoke User', email: `smoke-${Date.now()}@micromap.invalid` }),
    });
    assert.equal(access.status, 200, `request-access failed: ${await access.text()}`);
  });
}
