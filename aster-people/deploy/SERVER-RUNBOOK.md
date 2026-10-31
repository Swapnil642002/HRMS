# Step 3 — Ubuntu 24.04 LTS server runbook

Prepared commands only. No command in this document has been run on a server.
Start on a new **staging host**. This topology uses separate staging/production
hosts, databases, Redis volumes, secrets, backup repositories and encryption keys.
Do not run both Compose proxies on the same host. The hosting provider and whether
production already contains data remain unanswered (`[provider]`, `[yes/no]`).
Provider security-group/snapshot commands cannot be supplied until it is named.
Do not initialize a production volume, bootstrap or run production migrations
until the existing deployment/volume and Prisma history are inventoried. Never
run `docker compose down -v`, `prisma migrate reset` or `db push` on these hosts.

## 1. Prerequisites and DNS

Use a fresh Ubuntu 24.04 LTS VM with console/rescue access and enough RAM/disk for
the app, local encrypted backups and the restore drill. The backup scripts use
non-swappable tmpfs: reserve at least the compressed dump size for backup and the
restored database plus dump for a drill. Increase configured limits when needed;
do not substitute an unencrypted disk scratch directory. Daily dumps provide an
RPO up to 24 hours; point-in-time recovery/WAL archiving is not configured here.

Point `staging-hrms.micromap.in` A/AAAA to staging only. Add AAAA only if IPv6
routing and firewall work. Later point `hrms.micromap.in` to production. Keep Caddy
as the direct public edge (no CDN proxy). In the provider firewall allow inbound
TCP 22 from your administrator IP/CIDR and TCP 80/443 from the internet; deny other
inbound connections. Allow necessary outbound DNS, NTP, HTTPS, SMTP and backup SSH.
Provider snapshots are additional protection, not the encrypted backup substitute.

On your workstation, set the actual staging IP, then verify DNS:

```bash
read -r -p 'Staging public IP: ' STAGING_IP
dig +short A staging-hrms.micromap.in
dig +short AAAA staging-hrms.micromap.in
# Connect using your provider's initial sudo-capable account/key.
ssh INITIAL_USER@"$STAGING_IP"
```

## 2. SSH keys, host firewall and fail2ban

On staging, keep the current session open until a second key-authenticated session
works. These instructions retain SSH port 22. Substitute actual network CIDRs;
do not paste the placeholder as a firewall rule.

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg git jq openssl ufw fail2ban restic dnsutils
sudo adduser --disabled-password --gecos '' hrmsops
sudo usermod -aG sudo hrmsops
sudo passwd hrmsops                   # private sudo password; SSH remains key-only
sudo install -d -m 0700 -o hrmsops -g hrmsops /home/hrmsops/.ssh
sudo install -m 0600 -o hrmsops -g hrmsops /dev/null /home/hrmsops/.ssh/authorized_keys
sudoedit /home/hrmsops/.ssh/authorized_keys
# Paste only your PUBLIC SSH key. Never copy the private key to this server.
```

From a second workstation terminal verify `ssh hrmsops@STAGING_IP` and `sudo -v`.
Keep **both sessions open** while changing sshd and UFW. Before changing anything,
save a root-only recovery copy (do not reset an existing firewall):

```bash
sudo install -d -m 0700 /root/hrms-access-before
sudo cp -a /etc/ssh/sshd_config /etc/ssh/sshd_config.d /root/hrms-access-before/
sudo cp -a /etc/ufw /root/hrms-access-before/
```

Only then apply hardening in the original session:

```bash
sudo tee /etc/ssh/sshd_config.d/00-hrms-hardening.conf >/dev/null <<'CONFIG'
PubkeyAuthentication yes
AuthenticationMethods publickey
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin no
PermitEmptyPasswords no
X11Forwarding no
MaxAuthTries 3
CONFIG
sudo sshd -t
sudo sshd -T | awk '/^(passwordauthentication|kbdinteractiveauthentication|permitrootlogin|authenticationmethods|pubkeyauthentication) /'
# Verify effective settings above, including any cloud-init/Match overrides.
sudo systemctl reload ssh
read -r -p 'Administrator IPv4 CIDR (for example your public IP/32): ' ADMIN_CIDR
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow from "$ADMIN_CIDR" to any port 22 proto tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
# If you administer via IPv6, add its actual CIDR for SSH before enabling UFW.
sudo ufw enable
sudo ufw status verbose
sudo tee /etc/fail2ban/jail.d/hrms-sshd.local >/dev/null <<'CONFIG'
[sshd]
enabled = true
backend = systemd
port = 22
maxretry = 5
findtime = 10m
bantime = 1h
banaction = ufw
CONFIG
sudo fail2ban-client -t
sudo systemctl enable --now fail2ban
sudo systemctl restart fail2ban
sudo fail2ban-client status sshd
```

Retest a new SSH session before closing the original. If locked out, use the
provider console to correct UFW/SSH configuration. Do not disable key-only login
as a routine workaround. Do not expose the Docker socket/TCP API or grant the app
user Docker group membership; Docker administration is effectively root access.

## 3. Docker installation, logs and source

Fresh hosts only; inspect existing Docker installations instead of replacing them.

```bash
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
sudo tee /etc/apt/sources.list.d/docker.sources >/dev/null <<EOF_DOCKER
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: noble
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF_DOCKER
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
sudo install -d -m 0755 /etc/docker
sudo python3 - <<'PY'
import json
from pathlib import Path
p=Path('/etc/docker/daemon.json')
v=json.loads(p.read_text()) if p.exists() else {}
v.update({'log-driver':'json-file','log-opts':{'max-size':'10m','max-file':'5'}})
p.write_text(json.dumps(v,indent=2)+'\n')
PY
sudo install -d /etc/systemd/journald.conf.d
sudo tee /etc/systemd/journald.conf.d/hrms.conf >/dev/null <<'CONFIG'
[Journal]
SystemMaxUse=500M
MaxRetentionSec=30day
CONFIG
sudo systemctl restart systemd-journald
sudo systemctl restart docker
sudo docker version
sudo docker compose version
sudo install -d -o hrmsops -g hrmsops /srv/hrms
sudo -u hrmsops git clone --branch production/backend-hardening --single-branch https://github.com/Swapnil642002/HRMS.git /srv/hrms/source
cd /srv/hrms/source
# Pin the reviewed commit; use a read-only deploy key if repository access needs it.
read -r -p 'Reviewed backend commit SHA: ' RELEASE_SHA
sudo -u hrmsops git -C /srv/hrms/source checkout --detach "$RELEASE_SHA"
cd aster-people
```

Use a credential helper/read-only deploy key for private repositories, never a
PAT embedded in the clone URL. Commit SHAs and immutable image tags/digests are
release identifiers. Later CI/CD can publish images; this runbook builds staging
images locally on the server only when an operator executes it.

Docker-published ports bypass UFW. Install the supplied inbound filter as well:

```bash
sudo install -m 0755 deploy/server/docker-firewall.sh /usr/local/sbin/hrms-docker-firewall
sudo install -d /etc/systemd/system/docker.service.d
sudo tee /etc/systemd/system/docker.service.d/hrms-firewall.conf >/dev/null <<'CONFIG'
[Service]
ExecStartPost=/usr/local/sbin/hrms-docker-firewall
CONFIG
sudo systemctl daemon-reload
sudo /usr/local/sbin/hrms-docker-firewall
sudo iptables -S DOCKER-USER
sudo iptables -S HRMS-INGRESS
```

This filter assumes Docker's default **iptables backend** and one public network
interface. If the VM has multiple public interfaces, add the same DOCKER-USER jump
for each; for distinct IPv6 ingress use its interface too. Do not switch Docker
to the nftables backend without rewriting/testing this filter. Only original
public TCP destinations 80/443 are forwarded to containers. Host SSH is controlled
by UFW. Postgres, Redis, API, web and Caddy's admin port have no published ports.

## 4. Staging configuration and controlled startup

```bash
sudo install -d -m 0700 /etc/hrms
sudo install -m 0600 deploy/production.env.example /etc/hrms/runtime.env
sudoedit /etc/hrms/runtime.env
```

Fill these non-secret choices: APP_DOMAIN=staging-hrms.micromap.in,
APP_IMAGE=hrms-app:REVIEWED_COMMIT, TOOLS_IMAGE=hrms-tools:REVIEWED_COMMIT,
EDGE_SUBNET=172.30.10.0/24, CADDY_IP=172.30.10.2, DB_NAME=hrms_staging,
DB_USER=hrms_staging. Check `ip route`/Docker networks for subnet collisions first.
Generate distinct database and Redis passwords privately; use hex to avoid URL
and Redis configuration escaping. For example generate private scratch files:

```bash
sudo sh -c 'umask 077; openssl rand -hex 32 > /etc/hrms/db-password.tmp; openssl rand -hex 32 > /etc/hrms/redis-password.tmp'
sudoedit /etc/hrms/runtime.env /etc/hrms/db-password.tmp /etc/hrms/redis-password.tmp
# Copy passwords within the editor, not to terminal output.
sudo rm /etc/hrms/db-password.tmp /etc/hrms/redis-password.tmp
```

DATABASE_URL must be `postgresql://hrms_staging:PASSWORD@postgres:5432/hrms_staging`;
REDIS_URL must be `redis://:PASSWORD@redis:6379`. Supply real SMTP settings and
MAIL_FROM/MAIL_MESSAGE_DOMAIN; do not use production mail recipients in staging.
Use a sandbox mailbox/provider. Configure a separate S3 bucket/credentials if
uploads are enabled; S3 bucket CORS must permit the matching web Origin for direct
PUT uploads. The reverse proxy's 10 MB limit does not limit direct S3 uploads.
Do not store ADMIN_PASSWORD in runtime.env. No `.env` file is copied to the image.

```bash
compose() { sudo docker compose --env-file /etc/hrms/runtime.env -p hrms-staging -f /srv/hrms/source/aster-people/docker-compose.prod.yml "$@"; }
compose config --quiet                 # never print resolved configuration/secrets
compose build api migrate
compose up -d postgres redis
compose ps
# Fresh staging DB only. For existing data first take/restore-test a backup.
compose run --rm migrate npx --no-install prisma migrate status
# Pending migrations make status nonzero on a new database; inspect before proceeding.
compose run --rm migrate
sudo bash deploy/bootstrap-admin.sh /etc/hrms/runtime.env hrms-staging
compose up -d api worker web caddy
compose ps
compose logs --tail=50 api worker caddy
```

Caddy's existing Caddyfile manages HTTPS issuance, renewal and HTTP redirects;
there is no Certbot cron. Keep 80/443 reachable and the `certificates` and
`caddy_config` volumes persistent. HSTS is one year, without preload or
includeSubDomains. Do not add those until every affected domain supports HTTPS.
An incorrect HSTS deployment is cached by browsers; validate TLS on staging first.
Caddy accepts at most 10 MB request bodies; normal Nest JSON limits still apply.

```bash
curl --fail --head http://staging-hrms.micromap.in/
curl --fail --head https://staging-hrms.micromap.in/
curl --fail https://staging-hrms.micromap.in/health
# Confirm Location=https://..., Strict-Transport-Security and no-store on API.
curl --fail --head https://staging-hrms.micromap.in/api/health
openssl s_client -connect staging-hrms.micromap.in:443 -servername staging-hrms.micromap.in </dev/null 2>/dev/null | openssl x509 -noout -dates -issuer
compose port postgres 5432             # must report no published mapping
compose port redis 6379                # must report no published mapping
sudo ss -lntup
```

From your workstation scan the staging public IP (both IP families if enabled):
`nmap -Pn -sT -p 22,80,443,3000,4000,5432,6379,2019 STAGING_IP`. Only 22/80/443
should be reachable from the permitted admin network. Repeat after a reboot.

Compose uses `restart: unless-stopped` and 30-second app shutdown grace periods;
Docker starts on boot. Container healthchecks report health but Docker does not
automatically restart an unhealthy yet running process. Investigate alerts before
an operator restart. Logs rotate at 10 MB x 5 per runtime/database/proxy container;
journald is capped above. Do not run logrotate against Docker's active JSON logs.

## 5. Encrypted local backups and independent off-server copy

Use dedicated Restic repositories per environment. Both repositories are
authenticated-encrypted; local and remote keys are distinct. Keep copies of both
keys and repository connection details in an offline/team secret vault. Losing
the remote key makes off-server recovery impossible. The hosting provider is not
needed for the SFTP option below, but an independent backup host/account is.

On the **separate backup host**, an operator creates a restricted SFTP account:

```bash
sudo adduser --disabled-password --gecos '' hrmsbackup
sudo install -d -m 0755 -o root -g root /srv/hrms-backup
sudo install -d -m 0700 -o hrmsbackup -g hrmsbackup /srv/hrms-backup/backups
sudo install -d -m 0700 -o hrmsbackup -g hrmsbackup /home/hrmsbackup/.ssh
sudo install -m 0600 -o hrmsbackup -g hrmsbackup /dev/null /home/hrmsbackup/.ssh/authorized_keys
sudoedit /home/hrmsbackup/.ssh/authorized_keys
sudo tee /etc/ssh/sshd_config.d/90-hrms-backup.conf >/dev/null <<'CONFIG'
Match User hrmsbackup
    ChrootDirectory /srv/hrms-backup
    ForceCommand internal-sftp
    PasswordAuthentication no
    KbdInteractiveAuthentication no
    AllowTcpForwarding no
    X11Forwarding no
    PermitTTY no
    AllowAgentForwarding no
Match all
CONFIG
sudo sshd -t
sudo systemctl reload ssh
```

If staging and production use this same backup host, create separate accounts,
chroots and SSH keys so neither can delete the other's repository. The application
host's backup account needs repository prune permission; off-host snapshots or
provider immutability under separate administrator credentials additionally
protect against an application-host compromise. Do not reuse app SSH keys.

On the staging application host:

```bash
sudo install -d -m 0700 /root/.ssh
sudo ssh-keygen -t ed25519 -N '' -f /root/.ssh/hrms-backup -C hrms-staging-backup
# Transfer only /root/.ssh/hrms-backup.pub into the backup account authorized_keys.
sudoedit /root/.ssh/config
```

Add an entry using the actual backup IP/hostname:

```sshconfig
Host hrms-backup
    HostName BACKUP_HOST
    User hrmsbackup
    IdentityFile /root/.ssh/hrms-backup
    IdentitiesOnly yes
    StrictHostKeyChecking yes
    BatchMode yes
```

Obtain the backup host's SSH public host key/fingerprint through its trusted
console/operator. Put the verified host key in `/root/.ssh/known_hosts`. Do not
blindly trust ssh-keyscan output. Test `sudo sftp hrms-backup` and exit.

```bash
sudo sh -c 'umask 077; openssl rand -hex 32 > /etc/hrms/restic-local.key; openssl rand -hex 32 > /etc/hrms/restic-remote.key'
sudo install -d -m 0700 /var/backups/hrms-restic
sudo install -m 0600 deploy/server/backup.conf.example /etc/hrms/backup.conf
sudoedit /etc/hrms/backup.conf
# Set actual repositories/project and RESTORE_POSTGRES_IMAGE to the approved 17 digest.
sudo docker image inspect postgres:17-alpine --format '{{index .RepoDigests 0}}'
sudo bash -c 'set -a; source /etc/hrms/backup.conf; set +a; restic init; RESTIC_REPOSITORY="$RESTIC_REMOTE_REPOSITORY" RESTIC_PASSWORD_FILE="$RESTIC_REMOTE_PASSWORD_FILE" restic init'
# Run init only for NEW repositories. Existing repositories must be checked/unlocked normally.
sudo install -m 0755 deploy/server/backup.sh /usr/local/sbin/hrms-backup
sudo install -m 0755 deploy/server/restore-drill.sh /usr/local/sbin/hrms-restore-drill
sudo install -m 0644 deploy/server/hrms-backup.service deploy/server/hrms-backup.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl start hrms-backup.service
sudo journalctl -u hrms-backup.service --since today --no-pager
sudo systemctl enable --now hrms-backup.timer
sudo systemctl list-timers hrms-backup.timer
```

The first backup must finish and copy successfully before enabling the timer.
The schedule is 02:15 UTC plus up to 15 minutes (07:45–08:00 India time), daily,
with missed runs caught up after reboot. `pg_dump` takes a consistent snapshot;
plaintext dumps/configuration exist only on a restricted `noswap` tmpfs, which is
unmounted on exit. A full tmpfs causes backup failure, not a partial success.

After a successful encrypted off-server copy, retain every snapshot within 30 days
of the newest backup and prune older ones. Dedicated environment repos and
host/tag grouping prevent unrelated backups being removed. Restic deliberately
retains the last successful backup during an outage; this is a **minimum 30-day
recovery window**, not a guaranteed deletion deadline during extended outages.
Test retention using `restic forget --host hrms-staging --tag hrms-db --group-by
host,tags --keep-within 30d --dry-run` before the first automated prune if importing
existing backups. Never initialize or prune an unknown repository.

Backups include PostgreSQL (schema/data/migration history), exact source revision,
Compose/Caddy config and runtime env. They do not include S3 objects, Caddy private
keys, or Redis queue state. Configure separate bucket versioning/backup if S3 is
used. Email outbox rows survive in PostgreSQL and the worker can rebuild pending
jobs; after recovery, review mail recipients/sent status before starting workers.
Restore into the existing database role from the private runtime configuration;
cluster-global roles are not copied by this per-database dump.

Alert on failed `hrms-backup.service` and no off-server snapshot within 26 hours.
Until Step 7 installs alert delivery, assign an operator to review daily:

```bash
sudo systemctl --failed
sudo journalctl -u hrms-backup.service --since '26 hours ago' --no-pager
sudo bash -c 'set -a; source /etc/hrms/backup.conf; set +a; RESTIC_REPOSITORY="$RESTIC_REMOTE_REPOSITORY" RESTIC_PASSWORD_FILE="$RESTIC_REMOTE_PASSWORD_FILE" restic snapshots --host "$COMPOSE_PROJECT" --tag hrms-db'
```

## 6. Isolated restore drill — required before go-live and monthly

Run on staging or a dedicated recovery host with adequate RAM and the pinned
PostgreSQL image pre-pulled. Never point this drill at the serving database.
Use an explicit **off-server snapshot ID** from the previous command:

```bash
read -r -p 'Off-server snapshot ID to restore: ' SNAPSHOT_ID
sudo /usr/local/sbin/hrms-restore-drill "$SNAPSHOT_ID"
```

The script verifies repository data and dump checksum, decrypts only the database
artifacts to non-swappable RAM, and restores to a uniquely named PostgreSQL
container with **no network, no published ports, no restart policy**. It never
starts the HRMS API/worker or sends email. `pg_restore --exit-on-error` must pass;
row counts and migration completion are reported. Record snapshot ID, source
release, row counts, duration/RTO and operator signoff. Compare counts and expected
business records with your known backup baseline before declaring recovery ready.
The script removes its disposable container and plaintext scratch on exit.
This is a runnable drill, not a claim that your future production backup has
already been restored. A database-only drill does not validate S3 document recovery.

For an actual recovery: pause API writes/workers; restore to a new private database,
verify with the matching release, change its private connection configuration,
then start API/web and only resume workers after reviewing outbox effects. Preserve
the old database for investigation. Restoring yesterday's dump loses subsequent
writes; obtain explicit operational approval for that loss. A detailed release
rollback remains Step 7. Never treat Prisma migrate resolve as SQL rollback.

## 7. Production gate

Complete staging TLS, login/roles, file-upload, Android API, email and restore
checks first. Record the exact image digests and source commit. Confirm hosting
provider, console access and **whether production data exists** before proceeding.
If it exists, inventory its volumes, DB version, schema/migration history and
backup/restore path; do not create a new empty volume and assume it is migration.
For a confirmed new production host repeat the hardening with independent secrets,
APP_DOMAIN=hrms.micromap.in, project hrms-production, its own database/user,
172.30.20.0/24 and CADDY_IP=172.30.20.2 (if unused), separate backup repositories,
keys and real SMTP/S3 configuration. Do not copy staging credentials or user data.
No production command is authorized for execution by this preparation.

References: [Docker Ubuntu installation](https://docs.docker.com/engine/install/ubuntu/),
[Docker firewall behavior](https://docs.docker.com/engine/network/packet-filtering-firewalls/),
[Caddy automatic HTTPS](https://caddyserver.com/docs/automatic-https),
[Restic backup](https://restic.readthedocs.io/en/stable/040_backup.html),
[Restic retention](https://restic.readthedocs.io/en/stable/060_forget.html).


## Access recovery, network invariants and key recovery addendum

If a new SSH login fails, use an existing open session or provider console. Do
not close both working sessions, reboot, or remove the tested public key:

```bash
# First restore an explicit SSH allow rule for the actual current admin CIDR.
read -r -p 'Current administrator IP/CIDR: ' RECOVERY_CIDR
sudo ufw allow from "$RECOVERY_CIDR" to any port 22 proto tcp
# Undo only the new snippet, restoring any prior copy if it existed.
sudo rm -f /etc/ssh/sshd_config.d/00-hrms-hardening.conf
if sudo test -f /root/hrms-access-before/sshd_config.d/00-hrms-hardening.conf; then
  sudo cp -a /root/hrms-access-before/sshd_config.d/00-hrms-hardening.conf /etc/ssh/sshd_config.d/
fi
sudo sshd -t && sudo systemctl reload ssh
# If UFW itself is broken, restore its saved configuration from the console:
sudo cp -a /root/hrms-access-before/ufw/. /etc/ufw/
sudo ufw reload
```

Retest key login in a third session, then reapply corrected hardening. If only a
provider console works, use it; no automatic SSH changes are made by CI/CD.

The checked-in production Compose file gives Postgres and Redis **only** the
`data` network, which has `internal: true`; neither has a `ports` entry. Other
services join it for DB access; only Caddy publishes host ports. Verify the actual
containers, not merely this source file:

```bash
compose ps
sudo docker inspect "$(compose ps -q postgres)" --format '{{json .HostConfig.PortBindings}} {{json .NetworkSettings.Networks}}'
sudo docker inspect "$(compose ps -q redis)" --format '{{json .HostConfig.PortBindings}} {{json .NetworkSettings.Networks}}'
sudo docker network inspect hrms-staging_data --format '{{.Internal}}'
```

Expect empty port bindings, a single `hrms-staging_data` attachment for each DB,
and `true` for Internal. From a separate external workstation:

```bash
nmap -Pn -sT -p 22,80,443,3000,4000,5432,6379,2019 STAGING_PUBLIC_IP
# If AAAA exists, repeat against its address:
nmap -6 -Pn -sT -p 22,80,443,3000,4000,5432,6379,2019 STAGING_PUBLIC_IPV6
```

Expect only 22 (from permitted admin IPs), 80 and 443 open. Repeat for production
only during an explicitly approved go-live verification window.

Encryption keys live at `/etc/hrms/restic-local.key` and
`/etc/hrms/restic-remote.key` (root-only, mode 0600), **and separately** as attachments
in the team's password-manager vault under `HRMS/<environment>/backup-recovery`.
Give at least two named recovery operators access; keep an offline encrypted
export with a separately held unlock secret. Store repository URI, verified SSH
host fingerprint, backup account recovery procedure and environment name alongside
the keys. Never put keys in GitHub artifacts, logs, this repository, or only inside
the backup they unlock. Staging and production need different vault entries.

On a clean recovery host: retrieve the remote key via the vault's authenticated
attachment download to an encrypted workstation, transfer it over verified SSH,
and install without displaying it:

```bash
# Workstation (path is the downloaded vault attachment, not a literal password):
scp -o StrictHostKeyChecking=yes ./restic-remote.key hrmsops@RECOVERY_HOST:restic-remote.key
# Recovery host:
sudo install -d -m 0700 /etc/hrms
sudo install -m 0600 ~/restic-remote.key /etc/hrms/restic-remote.key
rm ~/restic-remote.key
# Recreate backup.conf and the independently recovered SFTP key/known_hosts.
sudo bash -c 'set -a; source /etc/hrms/backup.conf; set +a; RESTIC_REPOSITORY="$RESTIC_REMOTE_REPOSITORY" RESTIC_PASSWORD_FILE="$RESTIC_REMOTE_PASSWORD_FILE" restic snapshots'
```

Then run the isolated restore drill using an off-server snapshot ID. Log only the
key-vault version reference, never key contents. Verify this recovery path before
go-live. If all copies of the encryption key are lost, backups cannot be decrypted.

## 7. CI deployment design — self-hosted runner, no inbound SSH from CI

GitHub-hosted runners come from changing IP ranges. SSH to the staging/production
hosts is therefore **not** opened to GitHub runners, and `StrictHostKeyChecking=no`
is never used. Deployment is pull-based:

- A self-hosted GitHub Actions runner is installed on each host (staging and
  production) and runs as the dedicated non-root `hrms-deploy` user.
- CI (GitHub-hosted) only builds and pushes immutable images to GHCR.
- The deployment job runs on the host's self-hosted runner, pulls the pinned image
  digest from GHCR, runs migrations (staging first; production only after a
  verified pre-migration backup and inside the manual approval gate), and promotes
  the new containers locally. No CI runner IP ever needs an SSH allow rule.

### hrms-deploy user (dedicated, non-root, minimal permissions)

On each host, create `hrms-deploy` and run the self-hosted runner as that user.
`hrms-deploy` has no interactive password and no Docker group membership (Docker
access is effectively root access). Grant only the commands needed for deployment
via a root-owned sudoers drop-in (`/etc/sudoers.d/hrms-deploy`, mode 0440):

```
hrms-deploy ALL=(root) NOPASSWD: \
  /usr/bin/docker compose -f /srv/hrms/source/aster-people/docker-compose.prod.yml -p hrms-staging *, \
  /usr/bin/docker compose -f /srv/hrms/source/aster-people/docker-compose.prod.yml -p hrms-production *, \
  /usr/bin/docker login ghcr.io, \
  /usr/bin/docker pull *
```

Runtime secrets live on the host in `/etc/hrms/runtime.env` (root-owned, mode 0600),
provisioned by the operator in Step 3. CI never transports or reads them; the
deploy job only updates `APP_IMAGE`/`TOOLS_IMAGE` via `sudo sed` and then runs
`sudo docker compose --env-file /etc/hrms/runtime.env ...`.

### Host key pinning

Operator SSH access (for break-glass only) uses `StrictHostKeyChecking=yes` with
each host's public host key pre-seeded in `known_hosts`. The fingerprint is
recorded in the runbook recovery materials alongside the backup key. Never accept
an unknown host key automatically.

## 8. GitHub Environments and the production approval gate

Two GitHub Environments are configured in the repository settings:

- `staging` — used by `deploy-staging`. No required reviewers by default; protects
  the staging secret set (`SMOKE_BASE_URL`, `SMOKE_USERS`, registry credentials).
- `production` — used by `deploy-production`. **Required reviewers** must be set
  (at least two named reviewers, e.g. a backend engineer and a release manager),
  and the deployment branch policy must be restricted to `main` (the release
  branch). `production/backend-hardening` is a pre-release hardening branch and
  must not be authorized for production deployment. A branch protection rule on
  `main` should require status checks (`test`, `build-images`) before merge.

### Verifying the gate really blocks production

1. Push a change to `main` (the release branch). The `build-images` job runs, but
   `deploy-production` stays in a `pending`/waiting state showing "Waiting for
   approval" until a required reviewer approves the `production` environment.
2. Confirm no new container is promoted on the production host while pending
   (`docker compose -p hrms-production ps` shows the previous image/digest).
3. Have an unauthorized actor (or a forked PR) attempt a production deploy — secrets
   are not available to forked PR runs, and the environment approval is still
   required; the job must not start.
4. Approve in the GitHub UI → `deploy-production` runs the pre-migration backup,
   verifies it, runs migrations, then deploys the pinned digest.

## 9. Where migrations run

Migrations are forward-only and run via the `migrate` Compose profile
(`npx --no-install prisma migrate deploy`):

- **Staging:** `deploy-staging` runs migrations **before** `compose up -d`
  promotes the new image, using the staging database. This validates the migration
  against a real (non-production) schema first.
- **Production:** `deploy-production` runs migrations **only** after:
  1. the required production environment approval, and
  2. a pre-migration encrypted backup (`deploy/server/backup.sh`) is taken and its
     newest snapshot is verified within the 24-hour RPO (`restic snapshots`).

Never run `prisma migrate reset`, `db push` or `docker compose down -v` on either
host. Do not roll back the database; see `deploy/STEP7-ROLLBACK.md` for the
application-tier rollback procedure.

## 10. Smoke tests

`tests/smoke.test.ts` runs against staging only and **refuses to run** if
`SMOKE_BASE_URL` resolves to `hrms.micromap.in` (production). The 429 lockout test
uses a unique throwaway email per run, stops at the first `429`, and runs last, so
it cannot lock out real users or the CI runner (the runner itself is not locked —
only app login from the runner IP is transiently rate-limited for 15 minutes).

