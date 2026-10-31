# Production Migration Guide

## How to tell if the live database was created with `prisma migrate` or `db:push`

```bash
# Method 1: Check Prisma migration history
npx prisma migrate show
# Shows list of applied migrations if using prisma migrate

# Method 2: Check the migration lock file
cat prisma/migration_lock.toml
# Contains hash of last applied migration

# Method 3: Inspect database schema vs Prisma schema diff
npx prisma migrate diff --from-empty --to-target <hash>
# Shows what prisma would generate
```

## Baseline safely (COPY first)

```bash
# 1. COPY the entire production volume to a safe location
sudo rsync -av /var/lib/postgresql/data /tmp/prod-backup/

# 2. COPY the Prisma schema and lock file
cp -r /srv/hrms/source/aster-people/prisma /tmp/prisma-backup/
cp /srv/hrms/source/aster-people/package-lock.json /tmp/prisma-backup/

# 3. COPY the entire deployment directory
sudo rsync -av /srv/hrms/source/aster-people/deploy /tmp/deploy-backup/
```

## Pre-change backup and restore test

```bash
# 1. Run backup per Step 3 runbook
sudo /srv/hrms/source/aster-people/deploy/server/backup.sh

# 2. Verify newest snapshot within RPO
sudo bash -c 'set -a; source /etc/hrms/backup.conf; set +a; restic snapshots --json | jq -e "any(.time > (now - 24*3600 | todate))"'
# Expected: true (newest snapshot within 24 hours)

# 3. Restore test: restore to a throwaway environment and verify data integrity
# (Documented in runbook §3 - isolated restore drill using off-server snapshot)
```

## Staging rehearsal (anonymized/synthetic data only)

```bash
# 1. On staging, use synthetic test accounts only (not real employee data)
# 2. Run `npm run test:operations` to validate operations flow
# 3. Run smoke tests with `SMOKE_USERS` containing synthetic emails only
# 4. Verify no real employee data is touched

# Synthetic test data setup:
# - Create test accounts with `employee1@staging.example.com` format
# - Use `LongEnoughPass123` style passwords (synthetic, not real)
# - Run `npx prisma migrate deploy` with --preview-feature if needed
# - Validate with `npm test`; no real PII should be processed
```

## Cutover plan with maintenance window

| Phase | Action | Duration | Rollback |
|-------|--------|----------|----------|
| **Pre-cutover (T-30 min)** | Take final backup; verify `restic snapshots` recent | 10 min | Backup already exists |
| **Cutover (T-0)** | `sudo docker compose -p hrms-production -f docker-compose.prod.yml pull && sudo docker compose -p hrms-production -f docker-compose.prod.yml up -d` | 5-10 min | `sudo docker compose -p hrms-production -f docker-compose.prod.yml down` |
| **Post-cutover (T+0)** | Health check: `curl -sf https://hrms.micromap.in/health` | 5 min | If failed: rollback above |
| **Verification (T+15 min)** | Run smoke tests against production; verify five roles can login | 10 min | If failed: contact DB admin, restore from `/tmp/prod-backup/` |

## Low DNS TTL strategy

```bash
# 1. Reduce DNS TTL to 5 minutes (1800s) 24h before cutover
# 2. At cutover time, update the Docker image/tags
# 3. Keep TTL low for 1 hour after successful verification
# 4. Restore TTL to default (e.g., 3600s or 14400s)
```

## Rollback to old system

```bash
# 1. If Docker-based: sudo docker compose -p hrms-production -f docker-compose.prod.yml down
# 2. Restore PostgreSQL from /tmp/prod-backup/
# 3. Re-apply old Prisma schema: npx prisma migrate resolve <target> --rolled-back
# 4. Verify: curl -sf https://hrms.micromap.in/health
# 5. Announce restoration to stakeholders
