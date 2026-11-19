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

docker image load --input images.tar.gz
release="$(mktemp -d "$state_dir/releases/$sha.XXXXXXXX")"
cp compose.json platform SHA256SUMS "$release/"
printf '%s\n' "$project" > "$release/project"
compose=(docker compose --project-name "$project" --project-directory "$app_dir"
  --env-file "$app_dir/.env" -f "$release/compose.json")
"${compose[@]}" config --quiet
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
