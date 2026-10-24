# Daksha local development

Use Node.js 24 and Docker Compose. Run commands from this directory.

1. Copy `.env.example` to `.env` on a fresh checkout. Set unique database and Redis passwords, and use the same passwords in their connection URLs. Keep an existing `.env` when restarting.
2. Run `npm ci` and `npm run db:generate`.
3. Start the dependencies with `docker compose up -d postgres redis`.
4. Run `npm run db:migrate` to apply the checked-in migrations to a new or existing database.
5. Set `ADMIN_EMAIL` and `ADMIN_PASSWORD` in `.env`, then run `npm run bootstrap` once. The password must have at least 14 characters. Bootstrap refuses to run if a user already exists.
6. In separate terminals run `npm run dev:api` and `npm run dev:web`.
7. Open **http://localhost:3000** and sign in with the credentials used for bootstrap. Use this hostname to match `APP_ORIGIN`.

The API development command builds and starts the server; restart it after API source changes. The frontend reloads automatically. Both commands load the root `.env`.

PostgreSQL uses local port `55433`; Redis uses `56379`, the API `4000`, and the web app `3000`. If changing a database port, update both `compose.yaml` and `.env`. Changing `DB_PASSWORD` alone does not change the password of an existing PostgreSQL volume.

Run `npm run typecheck`, `npm test`, and `npm run build` to validate changes. Email delivery and document storage require the additional settings described in `.env.example`.

Assigned-branch attendance requires location setup and phone passkey enrollment. See [Attendance setup](docs/ATTENDANCE-SETUP.md) for the five-branch, 50-metre policy and rollout steps.

Request approvals now route through HR-assigned reporting managers. See [Request approvals](docs/REQUEST-APPROVALS.md) for assignment and approval steps.
