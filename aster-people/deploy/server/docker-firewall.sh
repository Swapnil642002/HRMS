#!/usr/bin/env bash
set -euo pipefail
# Ubuntu/Docker iptables backend. Docker-published ports can bypass UFW.
iface=$(ip -4 route show default | awk 'NR==1 {print $5}')
[[ -n "$iface" ]] || { echo 'No default external interface found' >&2; exit 1; }
for tool in iptables ip6tables; do
  if ! "$tool" -w -nL DOCKER-USER >/dev/null 2>&1; then
    [[ $tool == ip6tables ]] && continue
    echo 'Docker iptables DOCKER-USER chain missing; review firewall backend' >&2; exit 1
  fi
  "$tool" -w -N HRMS-INGRESS 2>/dev/null || true
  "$tool" -w -F HRMS-INGRESS
  "$tool" -w -A HRMS-INGRESS -m conntrack --ctstate ESTABLISHED,RELATED -j RETURN
  "$tool" -w -A HRMS-INGRESS -p tcp -m conntrack --ctorigdstport 80 -j RETURN
  "$tool" -w -A HRMS-INGRESS -p tcp -m conntrack --ctorigdstport 443 -j RETURN
  "$tool" -w -A HRMS-INGRESS -j DROP
  "$tool" -w -C DOCKER-USER -i "$iface" -j HRMS-INGRESS 2>/dev/null || \
    "$tool" -w -I DOCKER-USER 1 -i "$iface" -j HRMS-INGRESS
 done
