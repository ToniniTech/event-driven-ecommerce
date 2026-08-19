#!/usr/bin/env bash

set -Eeuo pipefail

readonly MAX_ATTEMPTS="${HEALTH_MAX_ATTEMPTS:-30}"
readonly RETRY_SECONDS="${HEALTH_RETRY_SECONDS:-10}"

declare -A ENDPOINTS=(
  [auth-service]="http://127.0.0.1:8084/actuator/health"
  [order-service]="http://127.0.0.1:8081/actuator/health"
  [payment-service]="http://127.0.0.1:8082/actuator/health"
  [notification-service]="http://127.0.0.1:8083/actuator/health"
  [product-service]="http://127.0.0.1:8085/actuator/health"
)

for attempt in $(seq 1 "$MAX_ATTEMPTS"); do
  unhealthy=()

  for service in "${!ENDPOINTS[@]}"; do
    response="$(curl --silent --show-error --fail --max-time 5 "${ENDPOINTS[$service]}" 2>/dev/null || true)"
    if ! grep --quiet --extended-regexp '"status"[[:space:]]*:[[:space:]]*"UP"' <<< "$response"; then
      unhealthy+=("$service")
    fi
  done

  if (( ${#unhealthy[@]} == 0 )); then
    echo "All application services are healthy."
    exit 0
  fi

  echo "Health attempt ${attempt}/${MAX_ATTEMPTS}; waiting for: ${unhealthy[*]}"
  sleep "$RETRY_SECONDS"
done

echo "Application services did not become healthy in time." >&2
docker compose ps >&2 || true
exit 1
