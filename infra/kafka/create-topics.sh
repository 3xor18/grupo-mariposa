#!/usr/bin/env bash
set -euo pipefail

readonly TOPICS_BIN=/opt/kafka/bin/kafka-topics.sh
readonly SEVEN_DAYS_MS=604800000
readonly THIRTY_DAYS_MS=2592000000

readonly BOOTSTRAP="${KAFKA_BOOTSTRAP_SERVERS:-kafka:9092}"
readonly REPLICATION="${KAFKA_REPLICATION_FACTOR:-1}"
readonly MIN_INSYNC="${KAFKA_MIN_INSYNC_REPLICAS:-1}"
readonly ORDERS_PARTITIONS="${KAFKA_ORDERS_PARTITIONS:-6}"
readonly DLT_PARTITIONS="${KAFKA_DLT_PARTITIONS:-3}"
readonly ORDERS_RETENTION_MS="${KAFKA_ORDERS_RETENTION_MS:-${SEVEN_DAYS_MS}}"
readonly DLT_RETENTION_MS="${KAFKA_DLT_RETENTION_MS:-${THIRTY_DAYS_MS}}"

require_positive_integer() {
  local name="$1" value="$2"
  if [[ ! "${value}" =~ ^[1-9][0-9]*$ ]]; then
    echo "${name} must be a positive integer, got '${value}'" >&2
    exit 1
  fi
}

create_topic() {
  local name="$1" partitions="$2" retention_ms="$3"
  "${TOPICS_BIN}" --bootstrap-server "${BOOTSTRAP}" --create --if-not-exists \
    --topic "${name}" --partitions "${partitions}" --replication-factor "${REPLICATION}" \
    --config "retention.ms=${retention_ms}" --config "min.insync.replicas=${MIN_INSYNC}"
}

main() {
  require_positive_integer KAFKA_REPLICATION_FACTOR "${REPLICATION}"
  require_positive_integer KAFKA_MIN_INSYNC_REPLICAS "${MIN_INSYNC}"
  require_positive_integer KAFKA_ORDERS_PARTITIONS "${ORDERS_PARTITIONS}"
  require_positive_integer KAFKA_DLT_PARTITIONS "${DLT_PARTITIONS}"
  require_positive_integer KAFKA_ORDERS_RETENTION_MS "${ORDERS_RETENTION_MS}"
  require_positive_integer KAFKA_DLT_RETENTION_MS "${DLT_RETENTION_MS}"
  if (( MIN_INSYNC > REPLICATION )); then
    echo "KAFKA_MIN_INSYNC_REPLICAS cannot exceed KAFKA_REPLICATION_FACTOR" >&2
    exit 1
  fi

  create_topic orders.created.v1 "${ORDERS_PARTITIONS}" "${ORDERS_RETENTION_MS}"
  create_topic orders.processed.v1 "${ORDERS_PARTITIONS}" "${ORDERS_RETENTION_MS}"
  create_topic orders.processing.dlt "${DLT_PARTITIONS}" "${DLT_RETENTION_MS}"

  "${TOPICS_BIN}" --bootstrap-server "${BOOTSTRAP}" --list
}

main "$@"
