#!/usr/bin/env bash
# Installed as root-owned /usr/local/sbin/hrms-backup. Does not deploy anything.
set -euo pipefail
set +x
umask 077
source /etc/hrms/backup.conf
: "${APP_DIR:?}" "${COMPOSE_PROJECT:?}" "${APP_ENV_FILE:?}" "${RESTIC_REPOSITORY:?}" "${RESTIC_PASSWORD_FILE:?}" "${RESTIC_REMOTE_REPOSITORY:?}" "${RESTIC_REMOTE_PASSWORD_FILE:?}"
export RESTIC_REPOSITORY RESTIC_PASSWORD_FILE
exec 9>/run/lock/hrms-backup.lock
flock -n 9 || exit 1
scratch=$(mktemp -d /run/hrms-backup.XXXXXX)
result=$(mktemp /run/hrms-backup-result.XXXXXX)
cleanup() { rm -f "$result"; mountpoint -q "$scratch" && umount "$scratch"; rmdir "$scratch"; }
trap cleanup EXIT
# Plaintext exists only in RAM, never in the encrypted repositories or disk swap.
mount -t tmpfs -o "size=${BACKUP_TMPFS_SIZE:-2G},noswap,mode=0700" tmpfs "$scratch"
cd "$APP_DIR"
compose=(docker compose --env-file "$APP_ENV_FILE" -p "$COMPOSE_PROJECT" -f docker-compose.prod.yml)
"${compose[@]}" exec -T postgres sh -ec 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --no-owner --no-acl' > "$scratch/database.dump"
test -s "$scratch/database.dump"
"${compose[@]}" exec -T postgres pg_restore --list < "$scratch/database.dump" > "$scratch/archive.list"
(cd "$scratch" && sha256sum database.dump > database.sha256)
git -c safe.directory="$(dirname "$APP_DIR")" -C "$(dirname "$APP_DIR")" rev-parse HEAD > "$scratch/release.txt"
# Configuration contains secrets: protected by restic encryption at both destinations.
cp "$APP_ENV_FILE" "$scratch/runtime.env"
cp docker-compose.prod.yml "$scratch/compose.yml"
cp deploy/Caddyfile "$scratch/Caddyfile"
# Stable snapshot path avoids creating a new retention group for every temporary directory.
(cd "$scratch" && restic backup --host "$COMPOSE_PROJECT" --tag hrms-db --json .) > "$result"
snapshot=$(jq -er 'select(.message_type=="summary") | .snapshot_id' "$result")
# Copy only the completed snapshot to the independently encrypted off-server repository.
RESTIC_REPOSITORY="$RESTIC_REMOTE_REPOSITORY" RESTIC_PASSWORD_FILE="$RESTIC_REMOTE_PASSWORD_FILE" \
  restic copy --from-repo "$RESTIC_REPOSITORY" --from-password-file "$RESTIC_PASSWORD_FILE" "$snapshot"
# Each environment has dedicated repositories. Never prune before successful off-server copy.
restic forget --host "$COMPOSE_PROJECT" --tag hrms-db --group-by host,tags --keep-within 30d --prune
RESTIC_REPOSITORY="$RESTIC_REMOTE_REPOSITORY" RESTIC_PASSWORD_FILE="$RESTIC_REMOTE_PASSWORD_FILE" \
  restic forget --host "$COMPOSE_PROJECT" --tag hrms-db --group-by host,tags --keep-within 30d --prune
restic check
RESTIC_REPOSITORY="$RESTIC_REMOTE_REPOSITORY" RESTIC_PASSWORD_FILE="$RESTIC_REMOTE_PASSWORD_FILE" restic check
printf 'Backup and off-server copy completed: %s\n' "$snapshot"
