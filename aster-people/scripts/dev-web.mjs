import { loadEnvFile } from 'node:process';
import { fileURLToPath, pathToFileURL } from 'node:url';

// Load environment without adding --env-file to execArgv: Next forwards those
// arguments to workers through NODE_OPTIONS, where --env-file is forbidden.
loadEnvFile(fileURLToPath(new URL('../.env', import.meta.url)));
const nextBin = fileURLToPath(new URL('../node_modules/next/dist/bin/next', import.meta.url));
const args = process.argv.slice(2);
if (!args.some(arg => ['--webpack', '--turbo', '--turbopack'].includes(arg))) {
  args.unshift('--webpack');
}
process.chdir(fileURLToPath(new URL('../apps/web/', import.meta.url)));
// The shared .env PORT belongs to the API. Keep the web origin on port 3000.
process.argv = [process.execPath, nextBin, 'dev', '--hostname', '127.0.0.1', '--port', '3000', ...args];
await import(pathToFileURL(nextBin).href);
