import test from 'node:test';
import assert from 'node:assert/strict';
import { scrub, scrubEvent } from '../apps/api/src/scrub';

const salary = {
  basic: 2000000,
  allowance: 500000,
  pfEmployee: 180000,
  pfEmployer: 180000,
  esicEmployee: 15750,
  net: 2290000,
  gross: 2500000,
  amount: 10000,
};

test('scrub redacts salary and monetary fields', () => {
  const out = scrub(salary);
  for (const key of Object.keys(salary)) {
    assert.equal(out[key], '[REDACTED]', `salary field ${key} must be redacted`);
  }
});

test('scrub redacts personal data', () => {
  const out = scrub({ email: 'a@b.in', name: 'Alice', employeeId: 'emp-1', employeeCode: 'E001', department: 'Eng' });
  assert.equal(out.email, '[REDACTED]');
  assert.equal(out.name, '[REDACTED]');
  assert.equal(out.employeeId, '[REDACTED]');
  assert.equal(out.employeeCode, '[REDACTED]');
  assert.equal(out.department, 'Eng');
});

test('scrub redacts credentials and tokens', () => {
  const out = scrub({
    password: 's3cret',
    passwordHash: 'salt:hash',
    token: 'abc123',
    secret: 'shhh',
    secretHash: 'digest',
    signingKey: 'key',
  });
  assert.equal(out.password, '[REDACTED]');
  assert.equal(out.passwordHash, '[REDACTED]');
  assert.equal(out.token, '[REDACTED]');
  assert.equal(out.secret, '[REDACTED]');
  assert.equal(out.secretHash, '[REDACTED]');
  assert.equal(out.signingKey, '[REDACTED]');
});

test('scrub redacts request metadata', () => {
  const out = scrub({
    headers: { cookie: 'aster_session=abc' },
    cookie: 'aster_session=abc',
    cookies: { aster_session: 'abc' },
    authorization: 'Bearer xyz',
  });
  assert.equal(out.headers, '[REDACTED]');
  assert.equal(out.cookie, '[REDACTED]');
  assert.equal(out.cookies, '[REDACTED]');
  assert.equal(out.authorization, '[REDACTED]');
});

test('scrub redacts nested objects and arrays without mutating input', () => {
  const input = {
    user: { email: 'a@b.in', salary: { basic: 100, net: 200 } },
    tags: ['a', 'b'],
    nested: { deep: { password: 'p' } },
  };
  const before = JSON.stringify(input);
  const out = scrub(input);
  assert.equal(input.user.email, 'a@b.in');
  assert.equal(input.user.salary.basic, 100);
  assert.equal(out.user.email, '[REDACTED]');
  assert.equal(out.user.salary.basic, '[REDACTED]');
  assert.equal(out.nested.deep.password, '[REDACTED]');
  assert.equal(before, JSON.stringify(input), 'input must not be mutated');
});

test('scrubEvent redacts request, contexts, extra and tags', () => {
  const event = {
    request: { url: 'https://example.invalid/api/payroll', body: salary, headers: { cookie: 'x' } },
    contexts: { app: { email: 'a@b.in' } },
    extra: { name: 'Alice' },
    tags: { token: 'abc' },
    environment: 'staging',
    release: 'sha-abc123',
  };
  const out = scrubEvent(event) as Record<string, unknown>;
  assert.equal(out.environment, 'staging');
  assert.equal(out.release, 'sha-abc123');
  const req = out.request as Record<string, unknown>;
  assert.equal((req.body as Record<string, unknown>).basic, '[REDACTED]');
  assert.equal(req.headers, '[REDACTED]');
  const appCtx = (out.contexts as Record<string, unknown>).app as Record<string, unknown>;
  assert.equal(appCtx.email, '[REDACTED]');
  assert.equal((out.extra as Record<string, unknown>).name, '[REDACTED]');
  assert.equal((out.tags as Record<string, unknown>).token, '[REDACTED]');
});

test('scrubEvent redacts breadcrumb data', () => {
  const event = {
    breadcrumbs: { values: [{ data: { email: 'a@b.in', password: 'p' }, message: 'login' }] },
  };
  const out = scrubEvent(event) as Record<string, unknown>;
  const vals = (out.breadcrumbs as Record<string, unknown>).values as unknown[];
  const data = (vals[0] as Record<string, unknown>).data as Record<string, unknown>;
  assert.equal(data.email, '[REDACTED]');
  assert.equal(data.password, '[REDACTED]');
  assert.equal((vals[0] as Record<string, unknown>).message, 'login');
});
