# Step 7 — Versioned rollback runbook

Prepared commands only. No command in this document has been run on a server.

## Principle

Images are immutable: each release is tagged with the git SHA and pushed by its
digest (`APP_IMAGE=...@sha256:...`, `TOOLS_IMAGE=...@sha256:...`). A rollback
redeploys the **previous known-good digest**; it never mutates an image or tag.

The database is **not** rolled back. Migrations are forward-only, and the
previous image must remain compatible with the current schema for one release.
If data itself is corrupt, restore from the encrypted backup (Step 3 restore
drill) — that is a separate incident, not a code rollback.

## Before you roll back

Keep a second SSH session open (Step 3 rule). Save the current state:

```bash
compose() { sudo docker compose --env-file /etc/hrms/runtime.env -p "$COMPOSE_PROJECT" -f docker-compose.prod.yml "$@"; }
compose ps
sudo docker compose --env-file /etc/hrms/runtime.env -p "$COMPOSE_PROJECT" -f docker-compose.prod.yml images
curl -sf https://"$APP_DOMAIN"/health || true
```

Record the current digests (`APP_IMAGE`, `TOOLS_IMAGE`) and the timestamp in the
deploy log (`/var/log/hrms/deploy.log`, root-only) before changing anything.

## Roll back the application tier

```bash
# 1. Select the previous good digest (from the previous deploy log entry).
read -r -p 'Previous APP_IMAGE digest: ' PREV_APP
read -r -p 'Previous TOOLS_IMAGE digest: ' PREV_TOOLS

# 2. Pin the runtime env to the previous digests (never edit the running
#    container; change the env file, then redeploy).
sudo sed -i "s#^APP_IMAGE=.*#APP_IMAGE=$PREV_APP#; s#^TOOLS_IMAGE=.*#TOOLS_IMAGE=$PREV_TOOLS#" /etc/hrms/runtime.env

# 3. Validate syntax without printing secrets.
compose config --quiet

# 4. Pull the previous images and redeploy the changed services only.
compose pull
compose up -d --no-deps api web worker

# 5. Verify.
compose ps
curl -sf https://"$APP_DOMAIN"/health && echo OK
```

Do not run `compose down` or `prisma migrate` during rollback. Do not run
migrations with the older image.

## If rollback does not restore service

1. Check `compose logs --tail=50 api web worker` (logs exclude secrets by
   design; never log environment values).
2. If the failure is data-related, stop: escalate to the restore-drill
   procedure (Step 3) using an off-server encrypted snapshot.
3. Record the outcome in the deploy log: digests before/after, timestamp,
   operator, and result.

## After rollback

- Create a new forward fix (revert the bad change in code, rebuild, push a new
  immutable digest, deploy via the normal GitHub pipeline with production
  manual approval).
- Do not re-tag or reuse the rolled-back digest as "current" without a new build.