#!/usr/bin/env bash
# Run on the Swarm manager. Registry token is supplied through stdin.
set -euo pipefail
umask 077
release="$(cd -- "$(dirname -- "$0")" && pwd)"
export IMAGE_TAG="${1:?Commit SHA required}"
registry_user="${2:?Registry user required}"
[[ "$IMAGE_TAG" =~ ^[0-9a-f]{40}$ ]]
: "${GHCR_OWNER:?Set GHCR_OWNER}"
: "${APP_DOMAIN:?Set APP_DOMAIN}"
[[ "$GHCR_OWNER" =~ ^[a-z0-9-]+$ ]]
[[ "$APP_DOMAIN" =~ ^[a-z0-9.-]+$ ]]
state="$HOME/.marketplace-swarm"
mkdir -p "$state"
exec 9>"$state/deploy.lock"
flock -w 600 9
[[ "$(docker info --format '{{.Swarm.ControlAvailable}}')" == true ]]
test -f "$state/ready" || { echo 'Run the one-time Swarm migration first.' >&2; exit 1; }
stack=marketplace-backend
manifest=backend.yml
export CATALOG_REPLICAS="${CATALOG_REPLICAS:-0}"
[[ "$CATALOG_REPLICAS" =~ ^[01]$ ]]
images=("ghcr.io/$GHCR_OWNER/marketplace-auth:$IMAGE_TAG" "ghcr.io/$GHCR_OWNER/marketplace-catalog:$IMAGE_TAG")
services=("${stack}_auth")
if [[ "$CATALOG_REPLICAS" == 1 ]]; then services+=("${stack}_catalog"); fi
smoke_path=/api/v1/users/me
smoke_status=401
export DOCKER_CONFIG
DOCKER_CONFIG="$(mktemp -d)"
trap 'rm -rf -- "$DOCKER_CONFIG"' EXIT
IFS= read -r registry_token
printf '%s' "$registry_token" | docker login ghcr.io --username "$registry_user" --password-stdin
unset registry_token
for image in "${images[@]}"; do
  docker pull "$image"
done
docker stack config -c "$release/$manifest" > "$release/rendered.yml"
current="$state/$stack.current.yml"
previous="$state/$stack.previous.yml"
wait_services() {
  local deadline=$((SECONDS + 240)) service update running desired
  while (( SECONDS < deadline )); do
    local ready=true
    for service in "${services[@]}"; do
      update="$(docker service inspect "$service" --format '{{if .UpdateStatus}}{{.UpdateStatus.State}}{{end}}')"
      case "$update" in
        paused|rollback*) echo "$service update failed: $update" >&2; return 1 ;;
        updating) ready=false ;;
      esac
      desired="$(docker service inspect "$service" --format '{{.Spec.Mode.Replicated.Replicas}}')"
      running="$(docker service ps "$service" --filter desired-state=running --format '{{.CurrentState}}' | awk '$1 == "Running" {n++} END {print n+0}')"
      [[ "$running" == "$desired" ]] || ready=false
    done
    if [[ "$ready" == true ]]; then return 0; fi
    sleep 5
  done
  echo 'Timed out waiting for services.' >&2
  return 1
}
smoke() {
  local deadline=$((SECONDS + 180)) code
  while (( SECONDS < deadline )); do
    code="$(curl --resolve "$APP_DOMAIN:443:127.0.0.1" --connect-timeout 5 --max-time 10 \
      -sS -o /dev/null -w '%{http_code}' "https://$APP_DOMAIN$smoke_path")" || code=000
    if [[ "$code" == "$smoke_status" ]]; then return 0; fi
    sleep 5
  done
  echo "HTTPS smoke check failed: $smoke_path returned $code, expected $smoke_status." >&2
  return 1
}
if ! docker stack deploy --with-registry-auth --resolve-image always -c "$release/rendered.yml" "$stack" \
   || ! wait_services || ! smoke; then
  docker stack services "$stack" || true
  docker stack ps --no-trunc "$stack" || true
  if [[ -f "$current" ]]; then
    echo 'Restoring the previous successful stack specification.' >&2
    docker stack deploy --with-registry-auth --resolve-image never -c "$current" "$stack"
    wait_services && smoke || echo 'Rollback needs manual attention.' >&2
  fi
  exit 1
fi
if [[ -f "$current" ]]; then cp "$current" "$previous"; fi
cp "$release/rendered.yml" "$current.tmp"
mv "$current.tmp" "$current"
printf '%s\n' "$IMAGE_TAG" > "$state/$stack.sha"
echo "Deployed $stack at $IMAGE_TAG"

