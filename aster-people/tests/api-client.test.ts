import test from 'node:test';
import assert from 'node:assert/strict';
import { apiFetch, ApiError } from '../apps/web/lib/api';

test('API client preserves validation messages and handles unavailable services', async t => {
  const mock = t.mock.method(globalThis, 'fetch');
  mock.mock.mockImplementation(async () => new Response(JSON.stringify({message:'Email or password is incorrect'}), {status:401}));
  await assert.rejects(apiFetch('auth/login'), (error:unknown) =>
    error instanceof ApiError && error.status === 401 && error.message === 'Email or password is incorrect');

  mock.mock.mockImplementation(async () => new Response('<html>Proxy failure</html>', {status:502}));
  await assert.rejects(apiFetch('auth/me'), (error:unknown) =>
    error instanceof ApiError && error.status === 502 && error.message.includes('service is unavailable'));

  mock.mock.mockImplementation(async () => {throw new TypeError('Failed to fetch');});
  await assert.rejects(apiFetch('auth/me'), (error:unknown) =>
    error instanceof ApiError && error.status === 0 && error.message.includes('Cannot connect'));
});

test('API client keeps successful download bodies intact', async t => {
  t.mock.method(globalThis, 'fetch', async () => new Response('Code,Name\n001,Example'));
  assert.equal(await (await apiFetch('reports/employees.csv')).text(), 'Code,Name\n001,Example');
});
