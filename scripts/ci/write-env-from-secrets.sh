#!/usr/bin/env bash
set -euo pipefail

readonly TARGET="${1:-.env}"
readonly REQUIRED=(MONGO_ROOT_PASSWORD MONGO_APP_PASSWORD REDIS_PASSWORD KEYCLOAK_ADMIN_PASSWORD
  ORDER_PROCESSOR_CLIENT_SECRET DEMO_USER_PASSWORD PII_ENCRYPTION_KEY GRAFANA_ADMIN_PASSWORD)

cp .env.example "${TARGET}"
for key in "${REQUIRED[@]}"; do
  value="${!key:-}"
  if [[ -z "${value}" ]]; then
    echo "Missing secret ${key}" >&2
    exit 1
  fi
  sed -i "s|^${key}=.*|${key}=${value}|" "${TARGET}"
done
echo "Environment file written to ${TARGET}"
