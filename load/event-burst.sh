#!/usr/bin/env bash
set -euo pipefail
export MSYS_NO_PATHCONV=1

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly TOTAL="${1:-2000}"
readonly DUPLICATE_EVERY="${2:-10}"
readonly RUN_ID="LOAD$(date +%s)"
readonly TIMEOUT_SECONDS="${3:-300}"
readonly WORK_FILE="$(mktemp)"

env_value() { grep -E "^$1=" "${ROOT_DIR}/.env" | cut -d= -f2-; }

mongo_eval() {
  docker compose -f "${ROOT_DIR}/docker-compose.yml" exec -T mongo mongosh --quiet \
    -u "$(env_value MONGO_ROOT_USERNAME)" -p "$(env_value MONGO_ROOT_PASSWORD)" \
    --authenticationDatabase admin orders --eval "$1"
}

event_line() {
  local index="$1" order_id="ORD-MX-${RUN_ID}-$1"
  printf '%s|{"eventId":"%s-%s","eventVersion":1,"orderId":"%s","market":"MX","currency":"MXN",' \
    "${order_id}" "${RUN_ID}" "${index}" "${order_id}"
  printf '"clientId":"CLI-99821","items":[{"productId":"PRD-001","quantity":%s,"unitPrice":35.5},' \
    "$(( (index % 40) + 1 ))"
  printf '{"productId":"PRD-008","quantity":12,"unitPrice":82.0}]}\n'
}

generate() {
  for index in $(seq 1 "${TOTAL}"); do
    event_line "${index}"
    if (( index % DUPLICATE_EVERY == 0 )); then event_line "${index}"; fi
  done > "${WORK_FILE}"
}

publish() {
  docker compose -f "${ROOT_DIR}/docker-compose.yml" exec -T kafka \
    /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server kafka:9092 \
    --topic orders.created.v1 --property parse.key=true --property "key.separator=|" \
    --producer-property linger.ms=5 < "${WORK_FILE}"
}

processed_count() {
  mongo_eval "db.orders.countDocuments({ _id: { \$regex: '^ORD-MX-${RUN_ID}-' } })" | tr -d '\r'
}

wait_until_processed() {
  local started="$1" count=0
  while (( count < TOTAL )); do
    count="$(processed_count)"
    if (( $(date +%s) - started > TIMEOUT_SECONDS )); then
      echo "Timeout: ${count}/${TOTAL} processed"
      exit 1
    fi
    sleep 1
  done
}

main() {
  generate
  local sent started finished
  sent="$(wc -l < "${WORK_FILE}")"
  started="$(date +%s)"
  publish
  wait_until_processed "${started}"
  finished="$(date +%s)"
  local elapsed=$(( finished - started ))
  (( elapsed == 0 )) && elapsed=1
  echo "Run ${RUN_ID}: ${sent} messages (${TOTAL} unique orders), ${elapsed}s"
  echo "Throughput: $(( TOTAL / elapsed )) orders/s"
  echo "Outbox pending: $(mongo_eval 'db.outbox.countDocuments({ status: { $ne: "PUBLISHED" } })')"
  rm -f "${WORK_FILE}"
}

main
