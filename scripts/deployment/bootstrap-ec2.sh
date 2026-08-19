#!/usr/bin/env bash

set -Eeuo pipefail

export HOME="${HOME:-/root}"

readonly REPOSITORY_URL="${1:-https://github.com/ToniniTech/Event-Driven-E-commerce.git}"
readonly APPLICATION_DIR="${2:-/opt/event-driven-ecommerce}"

for command_name in aws curl docker git; do
  command -v "$command_name" >/dev/null 2>&1 || {
    echo "Required command is missing: $command_name" >&2
    exit 1
  }
done

docker compose version >/dev/null 2>&1 || {
  echo "Docker Compose v2 is required." >&2
  exit 1
}

if [[ ! -d "$APPLICATION_DIR/.git" ]]; then
  install -d -m 0755 "$(dirname "$APPLICATION_DIR")"
  git clone "$REPOSITORY_URL" "$APPLICATION_DIR"
fi

git config --global --add safe.directory "$APPLICATION_DIR"
cd "$APPLICATION_DIR"

if [[ ! -f .env ]]; then
  umask 077
  cat > .env <<'ENVIRONMENT'
ADMIN_EMAIL=replace-me@example.com
ADMIN_PASSWORD=replace-me
JWT_SECRET=replace-with-at-least-256-bits-of-random-data
ENVIRONMENT
  echo "Created $APPLICATION_DIR/.env with placeholders. Replace them before the first deployment."
else
  echo "Keeping existing $APPLICATION_DIR/.env unchanged."
fi

chmod +x scripts/deployment/*.sh 2>/dev/null || true
echo "EC2 deployment directory is ready: $APPLICATION_DIR"
