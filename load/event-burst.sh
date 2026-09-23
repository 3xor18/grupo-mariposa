#!/usr/bin/env bash
set -euo pipefail
export MSYS_NO_PATHCONV=1

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly ROOT_DIR
readonly TOTAL="${1:-2000}"
readonly DUPLICATE_EVERY="${2:-10}"
readonly TIMEOUT_SECONDS="${3:-300}"
RUN_ID="LOAD$(date +%s)"
readonly RUN_ID
readonly INPUT_TOPIC=orders.created.v1
readonly KAFKA_BOOTSTRAP=kafka:9092
readonly PRODUCER_LINGER_MS=5
readonly MAX_VARIABLE_QUANTITY=40
readonly POLL_SECONDS=1
readonly OUTBOX_PENDING_QUERY="db.outbox.countDocuments({ status: { \$ne: 'PUBLISHED' } })"

WORK_FILE="$(mktemp)"
readonly WORK_FILE
cleanup() { rm -f "${WORK_FILE}"; }
trap cleanup EXIT

require_positive_integer() {
  local name="$1" value="$2"
  if [[ ! "${value}" =~ ^[1-9][0-9]*$ ]]; then
    echo "${name} must be a positive integer, got '${value}'" >&2
    echo "Usage: $0 [total] [duplicate-every] [timeout-seconds]" >&2
    exit 1
  fi
}

mongo_eval() {
  local script="db.getSiblingDB('admin').auth(process.env.MONGO_INITDB_ROOT_USERNAME,
    process.env.MONGO_INITDB_ROOT_PASSWORD); db = db.getSiblingDB('orders'); $1"
  docker compose exec -T mongo mongosh --quiet --eval "${script}"
}

event_line() {
  local index="$1" order_id="ORD-MX-${RUN_ID}-$1"
  printf '%s|{"eventId":"%s-%s","eventVersion":1,"orderId":"%s","market":"MX","currency":"MXN",' \
    "${order_id}" "${RUN_ID}" "${index}" "${order_id}"
  printf '"clientId":"CLI-99821","items":[{"productId":"PRD-001","quantity":%s,"unitPrice":35.5},' \
    "$(( (index % MAX_VARIABLE_QUANTITY) + 1 ))"
  printf '{"productId":"PRD-008","quantity":12,"unitPrice":82.0}]}\n'
}

generate() {
  local index
  for index in $(seq 1 "${TOTAL}"); do
    event_line "${index}"
    if (( index % DUPLICATE_EVERY == 0 )); then event_line "${index}"; fi
  done > "${WORK_FILE}"
}

publish() {
  docker compose exec -T kafka \
    /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server "${KAFKA_BOOTSTRAP}" \
    --topic "${INPUT_TOPIC}" --property parse.key=true --property "key.separator=|" \
    --producer-property "linger.ms=${PRODUCER_LINGER_MS}" < "${WORK_FILE}"
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
    sleep "${POLL_SECONDS}"
  done
}

main() {
  require_positive_integer total "${TOTAL}"
  require_positive_integer duplicate-every "${DUPLICATE_EVERY}"
  require_positive_integer timeout-seconds "${TIMEOUT_SECONDS}"
  cd "${ROOT_DIR}"
  generate
  local sent started finished elapsed
  sent="$(wc -l < "${WORK_FILE}")"
  started="$(date +%s)"
  publish
  wait_until_processed "${started}"
  finished="$(date +%s)"
  elapsed=$(( finished - started ))
  (( elapsed == 0 )) && elapsed=1
  echo "Run ${RUN_ID}: ${sent} messages (${TOTAL} unique orders), ${elapsed}s"
  echo "Throughput: $(( TOTAL / elapsed )) orders/s"
  echo "Outbox pending: $(mongo_eval "${OUTBOX_PENDING_QUERY}")"
}

main
