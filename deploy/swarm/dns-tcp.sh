#!/usr/bin/env bash
# Work around unreachable UDP resolvers using the libc/Go use-vc option.
# Does not change cloud firewall rules or disable DNS/certificate validation.
set -euo pipefail
umask 077
state="$HOME/.marketplace-swarm"
mkdir -p "$state"
exec 9>"$state/deploy.lock"
flock -w 30 9
RES_OPTIONS='use-vc timeout:2 attempts:2' getent ahostsv4 ghcr.io >/dev/null
RES_OPTIONS='use-vc timeout:2 attempts:2' getent ahostsv4 87.242.102.190.nip.io >/dev/null
backup="$state/resolv.conf.before-tcp"
if [[ ! -e "$backup" && ! -L "$backup" ]]; then
  sudo -n cp -a /etc/resolv.conf "$backup"
fi
config="$(mktemp)"
trap 'rm -f -- "$config"' EXIT
cat > "$config" <<'DNS'
# Managed by marketplace deploy/swarm/dns-tcp.sh. Previous symlink/config:
# ~/.marketplace-swarm/resolv.conf.before-tcp
nameserver 1.1.1.1
nameserver 1.0.0.1
options use-vc timeout:2 attempts:2
DNS
sudo -n install -m 644 "$config" /etc/resolv.conf.marketplace
sudo -n mv -T /etc/resolv.conf.marketplace /etc/resolv.conf
restore() {
  sudo -n cp --remove-destination -a "$backup" /etc/resolv.conf
  echo 'DNS verification failed; restored previous resolver configuration.' >&2
}
trap restore ERR
getent ahostsv4 87.242.102.190.nip.io
curl --connect-timeout 10 --max-time 20 -fsS https://acme-v02.api.letsencrypt.org/directory >/dev/null
code="$(curl --connect-timeout 10 --max-time 20 -sS -o /dev/null -w '%{http_code}' https://ghcr.io/v2/)"
[[ "$code" == 401 || "$code" == 200 ]]
trap - ERR
echo 'TCP DNS enabled; nip.io, ACME and GHCR resolve successfully.'
