#!/usr/bin/env bash
set -euo pipefail

bundle="$(cd -- "${1:?Bundle directory required}" && pwd)"
sha="${2:?Commit SHA required}"
[[ "$sha" =~ ^[0-9a-f]{40}$ ]]
app_dir="$HOME/marketplace-backend"
state_dir="$HOME/.marketplace-deploy"
mkdir -p "$state_dir/releases"
exec 9>"$state_dir/deploy.lock"
flock -w 30 9
test -f "$app_dir/.env" || { echo "Missing $app_dir/.env" >&2; exit 1; }

cd "$bundle"
sha256sum --check SHA256SUMS
case "$(docker info --format '{{.Architecture}}')" in
  x86_64|amd64) platform=linux/amd64 ;;
  aarch64|arm64) platform=linux/arm64 ;;
  *) echo 'Unsupported VM architecture.' >&2; exit 1 ;;
esac
[[ "$(cat platform)" == "$platform" ]] || { echo 'Image/VM architecture mismatch.' >&2; exit 1; }

# Keep the current Compose project so PostgreSQL uses its existing named volume.
project="${3:-}"
if [[ -z "$project" && -f "$app_dir/compose.yaml" ]]; then
  db_id="$(docker compose --project-directory "$app_dir" -f "$app_dir/compose.yaml" ps -a -q db)"
  if [[ -n "$db_id" ]]; then
    project="$(docker inspect --format '{{index .Config.Labels "com.docker.compose.project"}}' "$db_id")"
  fi
fi
project="${project:-marketplace-backend}"
[[ "$project" =~ ^[a-z0-9][a-z0-9_-]*$ ]]

# Read once so every login attempt receives the complete password over stdin.
registry="${4:?Registry hostname required}"
[[ "$registry" =~ ^[a-z0-9][a-z0-9.-]*\.cr\.cloud\.ru$ ]]
IFS= read -r registry_user
IFS= read -r registry_password
umask 077
export DOCKER_CONFIG="$bundle/docker-config"
for attempt in {1..11}; do
  if printf '%s' "$registry_password" | timeout 45s docker login "$registry" \
      --username "$registry_user" --password-stdin; then
    break
  fi
  if (( attempt == 11 )); then
    echo 'Registry login failed after 11 attempts. Checking VM connectivity:' >&2
    getent ahostsv4 "$registry" >&2 || true
    if command -v curl >/dev/null; then
      for mode in IPv4 default; do
        curl_options=()
        if [[ "$mode" == IPv4 ]]; then curl_options=(-4); fi
        curl "${curl_options[@]}" -sS -o /dev/null --connect-timeout 10 --max-time 20 \
          -w "Registry $mode: HTTP %{http_code}, remote %{remote_ip}, connect %{time_connect}s, TLS %{time_appconnect}s\n" \
          "https://$registry/v2/" >&2 || true
      done
    fi
    exit 1
  fi
  echo "Registry login attempt $attempt/11 failed; retrying in 5 seconds." >&2
  sleep 5
done
unset registry_password
release="$(mktemp -d "$state_dir/releases/$sha.XXXXXXXX")"
cp compose.json platform SHA256SUMS "$release/"
printf '%s\n' "$project" > "$release/project"
compose=(docker compose --project-name "$project" --project-directory "$app_dir"
  --env-file "$app_dir/.env" -f "$release/compose.json")
"${compose[@]}" config --quiet
for attempt in 1 2 3; do
  if timeout 300s "${compose[@]}" pull; then
    break
  fi
  if (( attempt == 3 )); then
    echo 'Failed to pull release images after three attempts; containers were not updated.' >&2
    exit 1
  fi
  echo "Image pull attempt $attempt failed; retrying in 5 seconds." >&2
  sleep 5
done
if ! "${compose[@]}" up --no-build --pull never --remove-orphans --wait --wait-timeout 180; then
  "${compose[@]}" ps -a
  echo "Deployment failed. Last successful release: $state_dir/current" >&2
  exit 1
fi
if [[ -L "$state_dir/current" ]]; then
  ln -sfn "$(readlink "$state_dir/current")" "$state_dir/previous"
fi
ln -sfn "$release" "$state_dir/current"
"${compose[@]}" ps
echo "Deployed $sha (Compose project: $project)."
