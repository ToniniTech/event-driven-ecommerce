#!/usr/bin/env bash

set -Eeuo pipefail

: "${AWS_REGION:?AWS_REGION is required}"
: "${ECR_REGISTRY:?ECR_REGISTRY is required}"
: "${IMAGE_TAG:?IMAGE_TAG is required}"

[[ "$IMAGE_TAG" =~ ^[A-Za-z0-9._-]{1,128}$ ]] || {
  echo "IMAGE_TAG contains invalid Docker tag characters." >&2
  exit 1
}

readonly DEPLOYMENT_STATE_FILE=".deployed-image-tag"
readonly APPLICATION_SERVICES=(
  auth-service
  order-service
  payment-service
  notification-service
  product-service
)

if [[ ! -f .env ]]; then
  echo "Missing $(pwd)/.env. Refusing to deploy without production secrets." >&2
  exit 1
fi

if grep --quiet --extended-regexp '(^|=)(replace-me|replace-with)' .env; then
  echo "Production .env still contains bootstrap placeholder values." >&2
  exit 1
fi

previous_tag=""
if [[ -f "$DEPLOYMENT_STATE_FILE" ]]; then
  previous_tag="$(tr -d '[:space:]' < "$DEPLOYMENT_STATE_FILE")"
  if [[ -n "$previous_tag" && ! "$previous_tag" =~ ^[A-Za-z0-9._-]{1,128}$ ]]; then
    echo "Ignoring invalid previous deployment tag from $DEPLOYMENT_STATE_FILE." >&2
    previous_tag=""
  fi
fi

login_to_ecr() {
  aws ecr get-login-password --region "$AWS_REGION" \
    | docker login --username AWS --password-stdin "$ECR_REGISTRY"
}

start_release() {
  local tag="$1"
  export IMAGE_TAG="$tag"
  export ECR_REGISTRY

  docker compose pull "${APPLICATION_SERVICES[@]}"
  docker compose up --detach --no-build --remove-orphans
}

show_diagnostics() {
  docker compose ps || true
  for service in "${APPLICATION_SERVICES[@]}"; do
    docker inspect --format '{{.Name}} {{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$service" || true
  done
}

rollback() {
  if [[ -z "$previous_tag" || "$previous_tag" == "$IMAGE_TAG" ]]; then
    echo "No different previously successful image tag is available for rollback." >&2
    return 1
  fi

  echo "Rolling back from ${IMAGE_TAG} to ${previous_tag}."
  start_release "$previous_tag"
  ./scripts/deployment/verify-health.sh
  printf '%s\n' "$previous_tag" > "$DEPLOYMENT_STATE_FILE"
  echo "Rollback to ${previous_tag} completed."
}

login_to_ecr
echo "Deploying immutable image tag ${IMAGE_TAG}."
start_release "$IMAGE_TAG"

if ./scripts/deployment/verify-health.sh; then
  printf '%s\n' "$IMAGE_TAG" > "$DEPLOYMENT_STATE_FILE"
  echo "Deployment ${IMAGE_TAG} completed successfully."
  exit 0
fi

failed_tag="$IMAGE_TAG"
echo "Deployment ${failed_tag} failed health verification." >&2
show_diagnostics

if rollback; then
  echo "The failed release was rolled back successfully." >&2
else
  echo "Automatic rollback was not possible." >&2
fi

exit 1
