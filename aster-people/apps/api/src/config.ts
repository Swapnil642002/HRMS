import Redis from 'ioredis';

export type Service = 'api' | 'worker' | 'bootstrap';
// Errors contain variable names only, never supplied values.
export function validateEnv(env: NodeJS.ProcessEnv = process.env, service: Service = 'api') {
  const required = (name: string) => {
    if (!env[name]?.trim()) throw new Error(`${name} is required`);
    return env[name]!;
  };
  const url = (name: string, protocols: string[]) => {
    let value: URL;
    try { value = new URL(required(name)); } catch { throw new Error(`${name} must be a valid URL`); }
    if (!protocols.includes(value.protocol)) throw new Error(`${name} has an unsupported protocol`);
    return value;
  };
  const production = env.NODE_ENV === 'production';
  if (!['development', 'test', 'production'].includes(required('NODE_ENV'))) throw new Error('NODE_ENV is invalid');
  const database = url('DATABASE_URL', ['postgres:', 'postgresql:']);
  if (service === 'bootstrap') return;
  if (production && env.ADMIN_PASSWORD) throw new Error('ADMIN_PASSWORD must not be supplied to runtime services');
  const redis = url('REDIS_URL', ['redis:', 'rediss:']);
  const origin = url('APP_ORIGIN', ['http:', 'https:']);
  if (origin.origin !== env.APP_ORIGIN || origin.username || origin.password) throw new Error('APP_ORIGIN must be an exact origin without a path');
  if (production) {
    if (origin.protocol !== 'https:') throw new Error('APP_ORIGIN must use HTTPS in production');
    for (const [name, value] of [['DATABASE_URL', database], ['REDIS_URL', redis]] as const) {
      if (['localhost', '127.0.0.1', '[::1]'].includes(value.hostname)) throw new Error(`${name} must not use localhost in production containers`);
      if (!value.password) throw new Error(`${name} requires credentials in production`);
    }
    if (service === 'api') {
      // Single trusted Caddy address on the dedicated Docker network; never trust arbitrary X-Forwarded-For.
      if (!/^(?:\d{1,3}\.){3}\d{1,3}$/.test(required('TRUST_PROXY')) || env.TRUST_PROXY!.split('.').some(n => Number(n) > 255)) throw new Error('TRUST_PROXY must be the Caddy IPv4 address');
    }
  }
  if (env.PORT && (!/^\d+$/.test(env.PORT) || Number(env.PORT) < 1 || Number(env.PORT) > 65535)) throw new Error('PORT is invalid');
  if (service === 'worker') {
    required('SMTP_HOST'); required('MAIL_FROM'); required('MAIL_MESSAGE_DOMAIN');
    if (env.SMTP_USER) required('SMTP_PASSWORD');
    if (env.SMTP_PORT && (!/^\d+$/.test(env.SMTP_PORT) || Number(env.SMTP_PORT) < 1 || Number(env.SMTP_PORT) > 65535)) throw new Error('SMTP_PORT is invalid');
  }
  if (env.S3_BUCKET) required('S3_REGION');
}

export async function checkRedis() {
  const redis = new Redis(process.env.REDIS_URL!, {lazyConnect:true, connectTimeout:5000, retryStrategy:() => null, maxRetriesPerRequest:0});
  redis.on('error', () => {});
  try { await redis.connect(); await redis.ping(); }
  catch { throw new Error('Redis startup check failed'); }
  finally { redis.disconnect(); }
}
