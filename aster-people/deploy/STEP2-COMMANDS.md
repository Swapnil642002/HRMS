# Prepared commands — not executed against any environment

Run from `aster-people`. Fill a private environment file using
`deploy/production.env.example`, chmod 600 it, and exclude it from version control.
For staging use `staging-hrms.micromap.in`, a separate database and credentials.
Choose an unused edge subnet, e.g. 172.30.10.0/24, and a Caddy address within it,
e.g. 172.30.10.2. Use a different subnet if both environments share a Docker host.
Use immutable release tags/digests for APP_IMAGE and TOOLS_IMAGE. DATABASE_URL
must use the `postgres` hostname and the configured DB_USER/DB_NAME; REDIS_URL
must use `redis`. Generate hex passwords to avoid Redis configuration quoting
and URL-encoding issues. Do not put ADMIN_PASSWORD in any environment file.
The supplied topology assumes separate hosts; shared-host Caddy routing is a
Step 3 decision. Default Compose project names/volumes must not be reused across
environments. An existing deployment's volume must be explicitly mapped after
inspection; this configuration does not import or reuse an old database volume.

Inspect syntax without displaying resolved secrets:

```bash
docker compose --env-file .env.staging -p hrms-staging -f docker-compose.prod.yml config --quiet
```

Commands for an approved staging rehearsal, not run by this preparation:

```bash
docker compose --env-file .env.staging -p hrms-staging -f docker-compose.prod.yml build api migrate
docker compose --env-file .env.staging -p hrms-staging -f docker-compose.prod.yml up -d postgres redis
# First inspect history and take/verify a backup if the database already has data.
docker compose --env-file .env.staging -p hrms-staging -f docker-compose.prod.yml run --rm migrate npx --no-install prisma migrate status
docker compose --env-file .env.staging -p hrms-staging -f docker-compose.prod.yml run --rm migrate
# Only if there are no existing users; prompts privately, removes the one-off container.
bash deploy/bootstrap-admin.sh .env.staging hrms-staging
docker compose --env-file .env.staging -p hrms-staging -f docker-compose.prod.yml up -d api worker web caddy
```

Never add bootstrap to automatic deployment. It refuses existing users and demo
identities. Password input is not part of shell history or command-line arguments;
it is present briefly in the one-off container environment, accessible to Docker
administrators, then removed with the container. Keep Docker access restricted.
No runtime service receives bootstrap credentials.

S3 document storage needs a separate least-privilege credential source (e.g. a
read-only mounted AWS shared credentials file plus AWS_SHARED_CREDENTIALS_FILE).
No credential source is invented or baked into these images. Confirm the provider
before configuring this. Off-server backups and restore testing remain Step 3.
