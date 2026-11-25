#!/usr/bin/env bash
# Switch only the database and edge. Deploy backend, then frontend immediately.
set -euo pipefail
umask 077
here="$(cd -- "$(dirname -- "$0")" && pwd)"
state="$HOME/.marketplace-swarm"
exec 9>"$state/deploy.lock"
flock -w 30 9
test ! -f "$state/ready" || { echo 'Migration already completed.'; exit 0; }
test -s "$state/verified-backup"
backup="$(cat "$state/verified-backup")"
sha256sum --check "$backup.sha256"
test "$(docker info --format '{{.Swarm.ControlAvailable}}')" == true
docker secret inspect marketplace_postgres_password_v1 >/dev/null
docker secret inspect marketplace_jwt_secret_v1 >/dev/null
docker stack config -c "$here/data.yml" >/dev/null
docker stack config -c "$here/edge.yml" >/dev/null

rollback() {
  echo 'Cutover failed. Restoring Compose database/auth and host nginx.' >&2
  docker stack rm marketplace-edge marketplace-data >/dev/null 2>&1 || true
  # Wait until the new database task has released the volume.
  for _attempt in {1..60}; do
    ids="$(docker ps -q --filter label=com.docker.swarm.service.name=marketplace-data_db)"
    [[ -z "$ids" ]] && break
    sleep 2
  done
  if [[ -n "$(docker ps -q --filter label=com.docker.swarm.service.name=marketplace-data_db)" ]]; then
    echo 'New database is still running; do not start the old one until it stops.' >&2
    return 1
  fi
  docker update --restart=unless-stopped marketplace-backend-db-1 marketplace-backend-auth-1 >/dev/null
  docker start marketplace-backend-db-1 marketplace-backend-auth-1 >/dev/null
  sudo -n systemctl start nginx
}
trap rollback ERR
# Preserve old containers for rollback, but prevent two PostgreSQL processes
# from ever mounting the production volume at the same time.
for name in marketplace-backend-auth-1 marketplace-backend-catalog-1 marketplace-backend-db-1; do
  docker update --restart=no "$name" >/dev/null
  docker stop --time 60 "$name" >/dev/null
done
sudo -n systemctl stop nginx
docker stack deploy -c "$here/data.yml" marketplace-data
ready=false
for _attempt in {1..60}; do
  id="$(docker ps -q --filter label=com.docker.swarm.service.name=marketplace-data_db | head -1)"
  if [[ -n "$id" ]] && [[ "$(docker inspect "$id" --format '{{if .State.Health}}{{.State.Health.Status}}{{end}}')" == healthy ]]; then
    ready=true
    break
  fi
  sleep 3
done
[[ "$ready" == true ]]
docker stack deploy -c "$here/edge.yml" marketplace-edge
ready=false
for _attempt in {1..60}; do
  id="$(docker ps -q --filter label=com.docker.swarm.service.name=marketplace-edge_traefik | head -1)"
  if [[ -n "$id" ]] && [[ "$(docker inspect "$id" --format '{{if .State.Health}}{{.State.Health.Status}}{{end}}')" == healthy ]]; then
    ready=true
    break
  fi
  sleep 3
done
[[ "$ready" == true ]]
sudo -n systemctl disable nginx
touch "$state/ready"
trap - ERR
echo 'Database and edge are ready. Deploy backend, then frontend.'
