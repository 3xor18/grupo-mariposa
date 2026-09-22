#!/usr/bin/env bash
set -euo pipefail

readonly BOOTSTRAP="${KAFKA_BOOTSTRAP_SERVERS:-kafka:9092}"
readonly TOPICS_BIN=/opt/kafka/bin/kafka-topics.sh
readonly REPLICATION="${KAFKA_REPLICATION_FACTOR:-1}"

create_topic() {
  local name="$1" partitions="$2" retention_ms="$3"
  "${TOPICS_BIN}" --bootstrap-server "${BOOTSTRAP}" --create --if-not-exists \
    --topic "${name}" --partitions "${partitions}" --replication-factor "${REPLICATION}" \
    --config "retention.ms=${retention_ms}" --config min.insync.replicas=1
}

readonly SEVEN_DAYS_MS=604800000
readonly THIRTY_DAYS_MS=2592000000

create_topic orders.created.v1 6 "${SEVEN_DAYS_MS}"
create_topic orders.processed.v1 6 "${SEVEN_DAYS_MS}"
create_topic orders.processing.dlt 3 "${THIRTY_DAYS_MS}"

"${TOPICS_BIN}" --bootstrap-server "${BOOTSTRAP}" --list
