#!/usr/bin/env bash
# Run manually from aster-people after staging verification and migration review.
set -euo pipefail
set +x
if [[ $# -ne 2 ]]; then
  printf 'Usage: bash deploy/bootstrap-admin.sh ENV_FILE COMPOSE_PROJECT\n' >&2
  exit 1
fi
if [[ ! -t 0 ]]; then
  printf 'An interactive terminal is required.\n' >&2
  exit 1
fi
trap 'unset ADMIN_EMAIL ADMIN_PASSWORD ADMIN_NAME COMPANY_NAME' EXIT
read -r -p 'Administrator email (real identity): ' ADMIN_EMAIL
read -r -p 'Administrator name: ' ADMIN_NAME
read -r -p 'Company name: ' COMPANY_NAME
read -r -s -p 'Administrator password (14–256 characters): ' ADMIN_PASSWORD
printf '\n'
export ADMIN_EMAIL ADMIN_PASSWORD ADMIN_NAME COMPANY_NAME
docker compose --env-file "$1" -p "$2" -f docker-compose.prod.yml run --rm --no-deps \
  -e ADMIN_EMAIL -e ADMIN_PASSWORD -e ADMIN_NAME -e COMPANY_NAME bootstrap
