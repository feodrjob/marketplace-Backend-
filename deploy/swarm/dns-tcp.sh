#!/usr/bin/env bash
# Local DNS forwarder: clients use ordinary DNS locally; upstream uses TCP.
# Ubuntu 24.04 + systemd-networkd + Docker, single VPS.
set -euo pipefail
umask 077
state="$HOME/.marketplace-swarm"
mkdir -p "$state"
exec 9>"$state/deploy.lock"
flock -w 30 9
resolved=false
for attempt in {1..4}; do
  echo "Checking upstream TCP DNS ($attempt/4)."
  if timeout 15 env RES_OPTIONS='use-vc timeout:2 attempts:2' getent ahostsv4 ghcr.io; then
    resolved=true
    break
  fi
  sleep 2
done
if [[ "$resolved" != true ]]; then
  echo 'TCP DNS bootstrap failed. No resolver settings have been changed.' >&2
  exit 1
fi

# Also fix the missing local hostname entry that causes sudo lookup delays.
sudo -n python3 - "$state/hosts.before-marketplace" <<'PY'
import pathlib, shutil, socket, sys
hosts = pathlib.Path('/etc/hosts')
name = socket.gethostname()
lines = hosts.read_text().splitlines()
if not any(name in line.split('#', 1)[0].split()[1:] for line in lines):
    backup = pathlib.Path(sys.argv[1])
    if not backup.exists():
        shutil.copy2(hosts, backup)
    with hosts.open('a') as f:
        f.write('\n127.0.1.1 ' + name + '\n')
PY

echo 'Installing the Ubuntu DNS forwarder with IPv4/TCP bootstrap resolution.'
sudo -n env RES_OPTIONS='use-vc timeout:2 attempts:2' \
  apt-get -o Acquire::ForceIPv4=true -o Acquire::Retries=1 -o Acquire::http::Timeout=15 update
sudo -n env DEBIAN_FRONTEND=noninteractive RES_OPTIONS='use-vc timeout:2 attempts:2' \
  apt-get -o Acquire::ForceIPv4=true -o Acquire::Retries=1 -o Acquire::http::Timeout=15 \
    install -y --no-install-recommends unbound

bridge="$(ip -4 -o addr show docker0 | awk '{print $4}' | cut -d/ -f1)"
interface="$(ip -4 route show default | awk '{print $5; exit}')"
[[ "$bridge" =~ ^[0-9.]+$ && "$interface" =~ ^[a-zA-Z0-9_.:-]+$ ]]
network_file="$(networkctl status --no-pager "$interface" | awk '/Network File:/ {print $NF}')"
network_file="${network_file##*/}"
[[ "$network_file" =~ ^[a-zA-Z0-9_.-]+\.network$ ]]

config="$(mktemp -d)"
trap 'rm -rf -- "$config"' EXIT
cat > "$config/unbound.conf" <<DNS
server:
    interface: 127.0.0.1
    interface: $bridge
    access-control: 127.0.0.0/8 allow
    access-control: 172.16.0.0/12 allow
    do-ip6: no
    ip-freebind: yes
    tcp-upstream: yes
    prefetch: yes
forward-zone:
    name: "."
    forward-addr: 1.0.0.1
    forward-addr: 1.1.1.1
    forward-tcp-upstream: yes
DNS
sudo -n install -m 644 "$config/unbound.conf" /etc/unbound/unbound.conf.d/marketplace-tcp.conf
sudo -n mkdir -p /etc/systemd/system/unbound.service.d
cat > "$config/service.conf" <<'UNIT'
[Unit]
After=network-online.target
Wants=network-online.target
UNIT
sudo -n install -m 644 "$config/service.conf" /etc/systemd/system/unbound.service.d/marketplace.conf
sudo -n unbound-checkconf
sudo -n systemctl daemon-reload
sudo -n systemctl enable unbound
sudo -n systemctl restart unbound

# Verify the forwarder before changing host or container DNS.
echo 'Verifying the local resolver before switching the host.'
answer="$(timeout 15 dig @"$bridge" ghcr.io A +short +time=5 +tries=1)"
[[ -n "$answer" ]]
answer="$(timeout 15 dig @"$bridge" 87.242.102.190.nip.io A +short +time=5 +tries=1)"
[[ "$answer" == *87.242.102.190* ]]

dropin="/etc/systemd/network/$network_file.d/90-marketplace-dns.conf"
sudo -n mkdir -p "/etc/systemd/network/$network_file.d"
cat > "$config/network.conf" <<DNS
[Network]
DNS=
DNS=$bridge
Domains=~.
[DHCPv4]
UseDNS=no
[DHCPv6]
UseDNS=no
DNS
sudo -n install -m 644 "$config/network.conf" "$dropin"
restore() {
  sudo -n rm -f -- "$dropin"
  sudo -n resolvectl dns "$interface" 1.1.1.1 1.0.0.1
  sudo -n resolvectl domain "$interface" '~.'
  sudo -n resolvectl flush-caches
  echo 'Host DNS verification failed; restored previous upstream addresses.' >&2
}
trap restore ERR
sudo -n resolvectl dns "$interface" "$bridge"
sudo -n resolvectl domain "$interface" '~.'
sudo -n resolvectl flush-caches
timeout 15 getent ahostsv4 87.242.102.190.nip.io
curl -4 --connect-timeout 10 --max-time 20 -fsS https://acme-v02.api.letsencrypt.org/directory >/dev/null
code="$(curl -4 --connect-timeout 10 --max-time 20 -sS -o /dev/null -w '%{http_code}' https://ghcr.io/v2/)"
[[ "$code" == 401 || "$code" == 200 ]]
echo 'Checking Docker registry access.'
timeout 180 docker pull postgres:17-alpine
trap - ERR
echo "DNS ready: local resolver $bridge, TCP upstream; nip.io, ACME, GHCR and Docker verified."
