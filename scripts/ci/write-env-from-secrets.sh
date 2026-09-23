#!/usr/bin/env bash
set -euo pipefail

readonly TARGET="${1:-.env}"
readonly TEMPLATE=.env.example
readonly REQUIRED=(MONGO_ROOT_PASSWORD MONGO_APP_PASSWORD MONGO_CLIENTS_PASSWORD
  MONGO_PRODUCTS_PASSWORD REDIS_PASSWORD KEYCLOAK_ADMIN_PASSWORD
  ORDER_PROCESSOR_CLIENT_SECRET DEMO_USER_PASSWORD PII_ENCRYPTION_KEY GRAFANA_ADMIN_PASSWORD
  CONFIG_SERVER_PASSWORD)

umask 077

work_file="$(mktemp "${TARGET}.XXXXXX")"
cleanup() { rm -f "${work_file}"; }
trap cleanup EXIT

validate() {
  local key="$1" value="$2"
  if [[ -z "${value}" ]]; then
    echo "Missing secret ${key}" >&2
    exit 1
  fi
  if [[ "${value}" == *$'\n'* ]]; then
    echo "Secret ${key} must be a single line" >&2
    exit 1
  fi
}

main() {
  local pattern
  pattern="^($(IFS='|'; echo "${REQUIRED[*]}"))="
  grep -v -E "${pattern}" "${TEMPLATE}" > "${work_file}" || true
  for key in "${REQUIRED[@]}"; do
    validate "${key}" "${!key:-}"
    printf '%s=%s\n' "${key}" "${!key}" >> "${work_file}"
  done
  mv "${work_file}" "${TARGET}"
  echo "Environment file written to ${TARGET}"
}

main "$@"
