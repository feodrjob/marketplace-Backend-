#!/usr/bin/env bash
# One-time preparation on the existing VPS. Leaves Compose and nginx running.
set -euo pipefail
umask 077
state="$HOME/.marketplace-swarm"
mkdir -p "$state/backups"
exec 9>"$state/deploy.lock"
flock -w 30 9
test ! -f "$state/ready" || { echo 'Migration already completed.'; exit 0; }
db=marketplace-backend-db-1
docker volume inspect marketplace-backend_postgres-data >/dev/null
test "$(docker inspect "$db" --format '{{.State.Health.Status}}')" == healthy

# Check networking before making any changes to Docker.
curl --connect-timeout 10 --max-time 20 -fsS https://acme-v02.api.letsencrypt.org/directory >/dev/null
code="$(curl --connect-timeout 10 --max-time 20 -sS -o /dev/null -w '%{http_code}' https://ghcr.io/v2/)"
[[ "$code" == 401 || "$code" == 200 ]]
getent ahostsv4 "${APP_DOMAIN:-87.242.102.190.nip.io}" >/dev/null
docker pull postgres:17-alpine
docker pull "${TRAEFIK_IMAGE:-traefik:v3.8.0}"

backup="$state/backups/postgres-$(date -u +%Y%m%dT%H%M%SZ).dump"
docker exec "$db" pg_dump -U marketplace -d marketplace -Fc > "$backup"
test -s "$backup"
sha256sum "$backup" > "$backup.sha256"
# Verify actual restoration in a disposable, isolated database, not production.
check="marketplace-restore-check-$$"
cleanup() { docker rm -f "$check" >/dev/null 2>&1 || true; }
trap cleanup EXIT
docker run -d --name "$check" --network none --tmpfs /var/lib/postgresql/data \
  -e POSTGRES_HOST_AUTH_METHOD=trust -e POSTGRES_USER=marketplace \
  -e POSTGRES_DB=marketplace postgres:17-alpine >/dev/null
for _attempt in {1..30}; do
  if docker exec "$check" pg_isready -U marketplace -d marketplace >/dev/null 2>&1; then break; fi
  sleep 2
done
docker exec -i "$check" pg_restore --no-owner --exit-on-error -U marketplace -d marketplace < "$backup"
cleanup
trap - EXIT
printf '%s\n' "$backup" > "$state/verified-backup"

if [[ "$(docker info --format '{{.Swarm.LocalNodeState}}')" == inactive ]]; then
  # This deployment has one manager. Do not expose its control API publicly.
  docker swarm init --advertise-addr "${SWARM_ADVERTISE_ADDR:-87.242.102.190}" --listen-addr 127.0.0.1:2377 >/dev/null
fi
test "$(docker info --format '{{.Swarm.ControlAvailable}}')" == true
docker node update --label-add marketplace.data=true "$(docker info --format '{{.Swarm.NodeID}}')" >/dev/null
for network in marketplace-edge marketplace-internal; do
  if ! docker network inspect "$network" >/dev/null 2>&1; then
    docker network create --driver overlay "$network" >/dev/null
  fi
done
docker volume create marketplace-traefik-certificates >/dev/null
if ! docker secret inspect marketplace_postgres_password_v1 >/dev/null 2>&1; then
  docker inspect "$db" --format '{{json .Config.Env}}' | python3 -c '
import json, sys
env = dict(item.split("=", 1) for item in json.load(sys.stdin))
password = env.get("POSTGRES_PASSWORD")
if not password:
    sys.exit("Existing database has no POSTGRES_PASSWORD; inspect its secret configuration manually.")
sys.stdout.write(password)
' | docker secret create marketplace_postgres_password_v1 - >/dev/null
fi
if ! docker secret inspect marketplace_jwt_secret_v1 >/dev/null 2>&1; then
  openssl rand -hex 64 | tr -d '\n' | docker secret create marketplace_jwt_secret_v1 - >/dev/null
fi
echo "Prepared Swarm. Verified database backup: $backup"
echo 'Compose and nginx are still running. Next: cutover.sh.'
