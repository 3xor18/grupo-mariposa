#!/usr/bin/env bash
set -euo pipefail
export MSYS_NO_PATHCONV=1

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly ROOT_DIR
readonly MINUTES="${1:-10}"
readonly BATCH_SIZE="${DEMO_BATCH_SIZE:-12}"
readonly PAUSE_SECONDS="${DEMO_PAUSE_SECONDS:-3}"
readonly CHAOS="${DEMO_CHAOS:-false}"
readonly TOPIC=orders.created.v1
readonly KAFKA_BIN=/opt/kafka/bin
readonly PERCENT=100
readonly REJECT_PERCENT=12
readonly INVALID_PERCENT=4
readonly DUPLICATE_PERCENT=6
readonly CHAOS_PERCENT=4
readonly MAX_QUANTITY=40
readonly OCCURRED_AT="2026-09-18T15:42:10Z"

readonly MARKETS=(MX MX MX CO PE CL EC)
declare -A CURRENCY=([MX]=MXN [CO]=COP [PE]=PEN [CL]=CLP [EC]=USD)
declare -A CLIENTS=(
  [MX]="CLI-99821 CLI-10002 CLI-10003"
  [CO]="CLI-20001"
  [PE]="CLI-30001 CLI-30002"
  [CL]="CLI-50001 CLI-50002"
  [EC]="CLI-60001 CLI-60002"
)
declare -A PRODUCTS=(
  [MX]="PRD-001 PRD-002 PRD-003 PRD-008 PRD-012"
  [CO]="PRD-001 PRD-005 PRD-006"
  [PE]="PRD-001 PRD-009 PRD-010 PRD-011"
  [CL]="PRD-001 PRD-015 PRD-016"
  [EC]="PRD-001 PRD-018 PRD-019"
)
readonly REJECTIONS=(
  "CO CLI-20002 PRD-001"
  "MX CLI-10002 PRD-004"
  "CL CLI-50001 PRD-017"
  "PE CLI-30002 PRD-008"
  "PE CLI-99821 PRD-001"
)
readonly CHAOS_CASES=("MX CLI-40002 PRD-001" "MX CLI-99821 PRD-013")

run_id="DEMO$(date +%s)"
sent=0

pick() {
  local -a options
  read -ra options <<< "$1"
  printf '%s' "${options[RANDOM % ${#options[@]}]}"
}

roll() { (( RANDOM % PERCENT < $1 )); }

price_for() {
  case "$1" in
    CLP) printf '%d' $(( 500 + RANDOM % 15000 )) ;;
    COP) printf '%d.%02d' $(( 1500 + RANDOM % 20000 )) $(( RANDOM % 100 )) ;;
    *) printf '%d.%02d' $(( 1 + RANDOM % 150 )) $(( RANDOM % 100 )) ;;
  esac
}

item_json() {
  local product="$1" currency="$2"
  printf '{"productId":"%s","quantity":%d,"unitPrice":%s}' \
    "${product}" $(( 1 + RANDOM % MAX_QUANTITY )) "$(price_for "${currency}")"
}

items_for() {
  local market="$1" currency="$2" first second
  first="$(pick "${PRODUCTS[${market}]}")"
  second="$(pick "${PRODUCTS[${market}]}")"
  if [[ "${first}" == "${second}" ]]; then
    printf '[%s]' "$(item_json "${first}" "${currency}")"
  else
    printf '[%s,%s]' "$(item_json "${first}" "${currency}")" "$(item_json "${second}" "${currency}")"
  fi
}

event_line() {
  local number="$1" market="$2" currency="$3" client="$4" items="$5" order_id event_id
  order_id="ORD-${market}-${run_id}-${number}"
  event_id="EVT-${run_id}-${number}"
  printf '%s|{"eventId":"%s","eventVersion":1,"occurredAt":"%s","orderId":"%s","market":"%s",' \
    "${order_id}" "${event_id}" "${OCCURRED_AT}" "${order_id}" "${market}"
  printf '"currency":"%s","clientId":"%s","channel":"C1","items":%s}\n' "${currency}" "${client}" "${items}"
}

special_line() {
  local -a parts
  read -ra parts <<< "$2"
  local market="${parts[0]}" currency="${CURRENCY[${parts[0]}]}"
  event_line "$1" "${market}" "${currency}" "${parts[1]}" "[$(item_json "${parts[2]}" "${currency}")]"
}

normal_line() {
  local market currency
  market="$(pick "${MARKETS[*]}")"
  currency="${CURRENCY[${market}]}"
  if roll "${INVALID_PERCENT}"; then
    currency="XXX"
  fi
  event_line "$1" "${market}" "${currency}" "$(pick "${CLIENTS[${market}]}")" "$(items_for "${market}" "${CURRENCY[${market}]}")"
}

next_line() {
  if [[ "${CHAOS}" == "true" ]] && roll "${CHAOS_PERCENT}"; then
    special_line "$1" "${CHAOS_CASES[RANDOM % ${#CHAOS_CASES[@]}]}"
  elif roll "${REJECT_PERCENT}"; then
    special_line "$1" "${REJECTIONS[RANDOM % ${#REJECTIONS[@]}]}"
  else
    normal_line "$1"
  fi
}

batch() {
  local line number
  for number in $(seq $(( $1 + 1 )) $(( $1 + BATCH_SIZE ))); do
    line="$(next_line "${number}")"
    printf '%s\n' "${line}"
    if roll "${DUPLICATE_PERCENT}"; then
      printf '%s\n' "${line}"
    fi
  done
}

publish() {
  docker compose exec -T kafka "${KAFKA_BIN}/kafka-console-producer.sh" \
    --bootstrap-server kafka:9092 --topic "${TOPIC}" \
    --property parse.key=true --property "key.separator=|"
}

main() {
  cd "${ROOT_DIR}"
  if [[ ! "${MINUTES}" =~ ^[0-9]+$ ]]; then
    echo "Uso: ./mariposa.sh demo-traffic [minutos]" >&2
    exit 1
  fi
  local deadline=$(( $(date +%s) + MINUTES * 60 ))
  echo "Generando tráfico de demo durante ${MINUTES} min (lotes de ${BATCH_SIZE} cada ${PAUSE_SECONDS}s, caos=${CHAOS})"
  while (( $(date +%s) < deadline )); do
    batch "${sent}" | publish
    sent=$(( sent + BATCH_SIZE ))
    printf '.'
    sleep "${PAUSE_SECONDS}"
  done
  echo
  echo "Listo: ${sent} pedidos (${run_id}). Mira Grafana: http://localhost:3001/d/mariposa-demo"
}

main
