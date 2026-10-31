#!/usr/bin/env bash
# Restore OFF-SERVER backup to a new, network-isolated disposable database.
set -euo pipefail
set +x
umask 077
[[ $# == 1 && $1 =~ ^[0-9a-f]{8,64}$ ]] || { echo 'Usage: hrms-restore-drill SNAPSHOT_ID' >&2; exit 1; }
source /etc/hrms/backup.conf
: "${RESTIC_REMOTE_REPOSITORY:?}" "${RESTIC_REMOTE_PASSWORD_FILE:?}" "${RESTORE_POSTGRES_IMAGE:?Set the tested PostgreSQL 17 image digest}"
export RESTIC_REPOSITORY="$RESTIC_REMOTE_REPOSITORY" RESTIC_PASSWORD_FILE="$RESTIC_REMOTE_PASSWORD_FILE"
exec 9>/run/lock/hrms-restore.lock
flock -n 9 || exit 1
scratch=$(mktemp -d /run/hrms-restore.XXXXXX)
container="hrms-restore-$(date +%s)-$$"
cleanup() {
  docker rm -f "$container" >/dev/null 2>&1 || true
  mountpoint -q "$scratch" && umount "$scratch"
  rmdir "$scratch"
}
trap cleanup EXIT
mount -t tmpfs -o "size=${RESTORE_TMPFS_SIZE:-4G},noswap,mode=0700" tmpfs "$scratch"
restic check --read-data
# Read only this snapshot's database artifacts, not its secret runtime configuration.
restic restore "$1" --target "$scratch" --include database.dump --include database.sha256
(cd "$scratch" && sha256sum -c database.sha256)
mkdir "$scratch/pgdata"
# No published ports, no network, no application or worker, no production credentials.
docker run -d --name "$container" --network none --restart no \
  -e POSTGRES_HOST_AUTH_METHOD=trust -e POSTGRES_DB=hrms_restore \
  -v "$scratch/pgdata:/var/lib/postgresql/data" "$RESTORE_POSTGRES_IMAGE" >/dev/null
ready=false
for attempt in {1..60}; do
  if docker exec "$container" pg_isready -U postgres -d hrms_restore >/dev/null 2>&1; then ready=true; break; fi
  sleep 1
done
[[ $ready == true ]] || { echo 'Restore database failed to become ready' >&2; exit 1; }
docker exec -i "$container" pg_restore -U postgres -d hrms_restore --no-owner --no-acl --exit-on-error < "$scratch/database.dump"
docker exec -i "$container" psql -X -U postgres -d hrms_restore -v ON_ERROR_STOP=1 <<'SQL'
BEGIN READ ONLY;
SELECT 'users' AS entity, count(*) FROM "User"
UNION ALL SELECT 'employees', count(*) FROM "Employee"
UNION ALL SELECT 'attendance', count(*) FROM "Attendance"
UNION ALL SELECT 'payroll_runs', count(*) FROM "PayrollRun";
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM "_prisma_migrations" WHERE finished_at IS NULL AND rolled_back_at IS NULL) THEN
  RAISE EXCEPTION 'Backup contains an unfinished migration';
 END IF;
END $$;
SELECT migration_name, finished_at IS NOT NULL AS completed FROM "_prisma_migrations" ORDER BY started_at;
COMMIT;
SQL
printf 'PASS: off-server archive decrypted, checksum verified and PostgreSQL restore succeeded.\n'
printf 'Record the row counts, snapshot ID, duration and release revision in the drill log.\n'
