#!/usr/bin/env bash
set -euo pipefail

readonly ALLOWED_SERVICES=(config-server products-api clients-api order-processor order-tracker)

contains() {
  local needle="$1" item
  shift
  for item in "$@"; do
    [[ "${item}" == "${needle}" ]] && return 0
  done
  return 1
}

main() {
  local raw="${SERVICES:-$(IFS=','; echo "${ALLOWED_SERVICES[*]}")}"
  local -a entries resolved=()
  local entry service
  IFS=',' read -ra entries <<< "${raw}"
  for entry in "${entries[@]}"; do
    service="$(tr -d '[:space:]' <<< "${entry}")"
    [[ -z "${service}" ]] && continue
    if ! contains "${service}" "${ALLOWED_SERVICES[@]}"; then
      echo "Unknown service '${service}'. Allowed: ${ALLOWED_SERVICES[*]}" >&2
      exit 1
    fi
    contains "${service}" "${resolved[@]}" || resolved+=("${service}")
  done
  if (( ${#resolved[@]} == 0 )); then
    echo "No services requested" >&2
    exit 1
  fi
  printf '%s\n' "${resolved[@]}"
}

main "$@"
