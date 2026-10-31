// Only disposable local development databases may use schema push.
import {spawnSync} from 'node:child_process';
let host;
try { host = new URL(process.env.DATABASE_URL).hostname; } catch {}
if (process.env.NODE_ENV !== 'development' || !['localhost','127.0.0.1','[::1]'].includes(host)) {
  console.error('db:push is restricted to local development. Use reviewed migrations.');
  process.exit(1);
}
const result=spawnSync('npx',['--no-install','prisma','db','push'],{stdio:'inherit',env:process.env});
process.exit(result.status ?? 1);
