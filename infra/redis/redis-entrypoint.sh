#!/bin/sh
set -eu

readonly CONFIG_FILE=/tmp/redis.conf
readonly MAX_MEMORY="${REDIS_MAX_MEMORY:-128mb}"
readonly EVICTION_POLICY="${REDIS_EVICTION_POLICY:-allkeys-lru}"

umask 077
{
  printf 'requirepass %s\n' "${REDIS_PASSWORD:?REDIS_PASSWORD is required}"
  printf 'maxmemory %s\n' "${MAX_MEMORY}"
  printf 'maxmemory-policy %s\n' "${EVICTION_POLICY}"
} > "${CONFIG_FILE}"
chown redis:redis "${CONFIG_FILE}"

exec docker-entrypoint.sh redis-server "${CONFIG_FILE}"
