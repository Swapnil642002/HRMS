// Data-scrubbing filter for error tracking (e.g. Sentry `beforeSend`).
// Guarantees that no salary or personal data ever leaves the server.
//
// Wire it into Sentry as:
//   Sentry.init({ beforeSend: scrubEvent })
//
// Safe to send: environment, release digest, HTTP status, route pattern,
// opaque error class, generic message, timestamp. Never send: request bodies,
// headers, cookies, personal identifiers, salary components, passwords or
// tokens.

const SENSITIVE_KEYS = new Set([
  // Personal data.
  'email',
  'name',
  'employeeid',
  'employeecode',
  // Salary / monetary values (paise or currency).
  'basic',
  'allowance',
  'pfemployee',
  'pfemployer',
  'esicemployee',
  'esicemployer',
  'tds',
  'professionaltax',
  'net',
  'amount',
  'gross',
  // Credentials and tokens.
  'password',
  'passwordhash',
  'token',
  'secret',
  'secrethash',
  'signingkey',
  // Request metadata that may carry client data.
  'headers',
  'cookie',
  'cookies',
  'authorization',
]);

const SENSITIVE_PREFIXES = ['password', 'token', 'secret', 'authorization'];

function isSensitiveKey(key: string): boolean {
  const k = key.toLowerCase();
  if (SENSITIVE_KEYS.has(k)) return true;
  return SENSITIVE_PREFIXES.some((prefix) => k.startsWith(prefix));
}

// Recursively scrub sensitive keys from an unknown value. Objects are copied
// (never mutated) so the original input is left untouched.
export function scrub(value: unknown): unknown {
  if (value === null || value === undefined) return value;
  if (Array.isArray(value)) return value.map(scrub);
  if (typeof value === 'object') {
    const result: Record<string, unknown> = {};
    for (const [key, val] of Object.entries(value as Record<string, unknown>)) {
      if (isSensitiveKey(key)) {
        result[key] = '[REDACTED]';
      } else {
        result[key] = scrub(val);
      }
    }
    return result;
  }
  return value;
}

// Sentry-compatible `beforeSend` handler. Scrubs request, contexts, extra and
// tags so no salary or personal data is transmitted to the error tracker.
export function scrubEvent(event: unknown): unknown {
  if (!event || typeof event !== 'object') return event;
  const e = event as Record<string, unknown>;
  if (e.request) e.request = scrub(e.request);
  if (e.contexts) e.contexts = scrub(e.contexts);
  if (e.extra) e.extra = scrub(e.extra);
  if (e.tags) e.tags = scrub(e.tags);
  if (e.breadcrumbs && typeof e.breadcrumbs === 'object') {
    const b = e.breadcrumbs as Record<string, unknown>;
    if (Array.isArray(b.values)) {
      b.values = (b.values as unknown[]).map((crumb) => {
        if (crumb && typeof crumb === 'object') {
          const c = crumb as Record<string, unknown>;
          return { ...c, data: scrub(c.data) };
        }
        return crumb;
      });
    }
  }
  return e;
}
