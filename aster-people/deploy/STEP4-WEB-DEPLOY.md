# Step 4 — Web deployment with caching rules (staging)

Prepared commands only. No command in this document has been run on a server.
Run from `aster-people` on the staging host after Step 3 is complete.

## Caching contract (deploy/Caddyfile)

| Path | Cache-Control | Rationale |
|------|---------------|-----------|
| `/api`, `/api/*`, `/health` and all HTML/account pages | `private, no-store, max-age=0` | Authenticated API responses and user-specific HTML are never cached by any cache (browser, CDN, proxy). |
| `/_next/static/*` | `public, max-age=31536000, immutable` | Next.js build assets are content-hashed; long-lived, immutable caching is safe. |

Verify at the edge, not just in the file:

```bash
# Staging edge (only 80/443 published; Postgres and Redis are on the internal
# data network with no host ports — see runbook network invariant check).
curl -si https://staging-hrms.micromap.in/api/health   # expect private, no-store, max-age=0
curl -si https://staging-hrms.micromap.in/_next/static/css/...css   # expect public, max-age=31536000, immutable
curl -si https://staging-hrms.micromap.in/account                  # expect private, no-store, max-age=0
```

## Deployment

Use an immutable image tag/digest for `APP_IMAGE` (produced by Step 5; never `latest`). The runtime image contains both API and web; the `web` process is `next start` inside the same container.

```bash
# 1. Fill staging runtime env privately (already created in Step 3; do not regenerate).
chmod 600 /etc/hrms/runtime.env

# 2. Pin the immutable release.
# Example: APP_IMAGE=ghcr.io/org/hrms@sha256:abc123...
read -r -p 'Immutable APP_IMAGE (tag or digest): ' APP_IMAGE
sudo bash -c "set -a; source /etc/hrms/runtime.env; set +a; \
  sed -i \"s#^APP_IMAGE=.*#APP_IMAGE=$APP_IMAGE#\" /etc/hrms/runtime.env"

# 3. Validate syntax without printing resolved secrets.
compose() { sudo docker compose --env-file /etc/hrms/runtime.env -p hrms-staging -f /srv/hrms/source/aster-people/docker-compose.prod.yml \"$@\"; }
compose config --quiet

# 4. Pull the pinned image and redeploy only the web tier (zero-downtime for API/worker).
compose pull web
compose up -d --no-deps web

# 5. Verify.
compose ps
curl -sf https://staging-hrms.micromap.in/health && echo
curl -si https://staging-hrms.micromap.in/api/auth/me   # 401 without session; no-store header
```

## Rollback (same release lineage)

```bash
read -r -p 'Previous APP_IMAGE (tag or digest): ' PREV_IMAGE
sudo bash -c "set -a; source /etc/hrms/runtime.env; set +a; \
  sed -i \"s#^APP_IMAGE=.*#APP_IMAGE=$PREV_IMAGE#\" /etc/hrms/runtime.env"
compose config --quiet
compose pull web
compose up -d --no-deps web
curl -sf https://staging-hrms.micromap.in/health
```

Do not roll back the database: migrations are forward-only and the previous image must remain compatible with the current schema. See Step 7 for the versioned rollback runbook.