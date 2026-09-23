#!/usr/bin/env bash
set -euo pipefail

readonly BASE_DIR="${1:-}"
readonly CHECKS="${CONTRACT_CHECKS:-all}"
readonly OASDIFF_IMAGE="${OASDIFF_IMAGE:-tufin/oasdiff:v1.32.1}"
readonly AJV_CLI="${AJV_CLI:-ajv-cli@5}"
readonly AJV_FORMATS="${AJV_FORMATS:-ajv-formats@3}"
readonly ERR_IGNORE=contracts/oasdiff/err-ignore.txt
readonly EVENT_SCHEMA=contracts/events/orders.created.v1.schema.json
readonly VALID_EVENTS=(contracts/examples/orders.created.v1.approved.json
  "samples/events/0[1-9]-*.json" "samples/events/1[2-9]-*.json" "samples/events/20-*.json"
  "samples/events/2[2346]-*.json")
readonly CHANGE_EVENTS=(clients products)

ajv() {
  npx --yes -p "${AJV_CLI}" -p "${AJV_FORMATS}" ajv "$@" --spec=draft2020 -c ajv-formats
}

check_breaking_changes() {
  local spec ignore=()
  [[ -f "${ERR_IGNORE}" ]] && ignore=(--err-ignore "/head/${ERR_IGNORE}")
  for spec in contracts/http/*.openapi.yaml; do
    [[ -f "${BASE_DIR}/${spec}" ]] || continue
    docker run --rm -v "${BASE_DIR}:/base:ro" -v "${PWD}:/head:ro" "${OASDIFF_IMAGE}" \
      breaking "/base/${spec}" "/head/${spec}" --fail-on ERR "${ignore[@]}"
  done
}

check_schemas() {
  ajv compile -s "contracts/events/*.json" -s contracts/common/problem.schema.json
  local data=() file
  for file in "${VALID_EVENTS[@]}"; do data+=(-d "${file}"); done
  ajv validate -s "${EVENT_SCHEMA}" "${data[@]}"
  local entity
  for entity in "${CHANGE_EVENTS[@]}"; do
    ajv validate -s "contracts/events/${entity}.changed.v1.schema.json" \
      -d "samples/changes/${entity}.changed.v1.json"
  done
}

main() {
  case "${CHECKS}" in
    all | breaking) ;;
    schemas) check_schemas; return ;;
    *) echo "CONTRACT_CHECKS must be all, breaking or schemas" >&2; exit 1 ;;
  esac
  if [[ -n "${BASE_DIR}" && -d "${BASE_DIR}" ]]; then
    check_breaking_changes
  else
    echo "No base checkout at '${BASE_DIR}', skipping breaking change detection"
  fi
  [[ "${CHECKS}" == "all" ]] && check_schemas
  return 0
}

main "$@"
