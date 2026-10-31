# Staging Day Checklist
**Date:** $(date +%Y-%m-%d)  
**Host:** 4 GB Ubuntu 24.04 VPS, India region  
**Staging domain:** staging-hrms.micromap.in  
**Production holds real data:** yes  
**Android distribution:** private  

---
## 1. Accounts and secrets to create first (names only, no values)
Store each in your password manager (e.g. Bitwarden, 1Password) under the vault `HRMS/staging`:

- `staging_vps_ssh_key` — the SSH private key for VPS access (generate with `ssh-keygen -t ed25519 -f staging_vps`)
- `staging_vps_user` — sudo user name (default: `staging`)
- `staging_vps_password` — private sudo password for the `staging` user
- `staging_vps_fqdn` — the full domain name (`staging-hrms.micromap.in`)
- `staging_cloudflare_api_token` — if using Cloudflare DNS (optional)
- `staging_github_personal_token` — GitHub PAT with `repo`, `workflow`, `admin:org` scope for repo operations
- `staging_github_app_id` — GitHub App ID for self-hosted runner registration (optional, can use PAT instead)
- `staging_github_app_private_key` — private key for GitHub App (optional, can use PAT instead)
- `staging_registry_user` — GHCR username (typically the repo owner)
- `staging_registry_password` — GHCR authentication token (use `https://` login)
- `staging_smtp_host` — SMTP host for email delivery (e.g. `smtp.sendgrid.net`)
- `staging_smtp_password` — SMTP password / API key
- `staging_smtp_from` — email sender address
- `staging_base_url` — `https://staging-hrms.micromap.in` (the base URL for smoke tests)
- `smoke_users` — JSON string of test accounts for all five roles (populated later)
- `smoke_payslip_line_id` — payroll line ID for payslip PDF testing (populated later)
- `deploy_enabled` — set to `'true'` only after all prerequisites are verified (default: `unset` / `'false'`)

---
## 2. DNS record and VPS creation settings
- **DNS A record:** `staging-hrms` → `VPS_IPv4`
- **DNS AAAA record:** `staging-hrms` → `VPS_IPv6` (if IPv6 is enabled)
- **Cloudflare (optional):** disable Proxied status for `staging-hrms.micromap.in` so Caddy gets direct TCP connectivity
- **VPS specifications:** 4 GB RAM, 2 vCPU, 50 GB SSD, Ubuntu 24.04 LTS, IPv4 + IPv6 enabled
- **Initial VPS root password** (out-of-band, not stored in password manager until after OS setup)
- **Enable:** SSH, UFW firewall, Docker, Docker Compose, fail2ban
