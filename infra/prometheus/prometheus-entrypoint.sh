#!/bin/sh
set -eu

readonly SECRETS_DIR=/tmp/prometheus-secrets
readonly PROMETHEUS_BIN=/bin/prometheus

umask 077
mkdir -p "${SECRETS_DIR}"
printf '%s' "${CONFIG_SERVER_USERNAME:?CONFIG_SERVER_USERNAME is required}" \
  > "${SECRETS_DIR}/config-server-username"
printf '%s' "${CONFIG_SERVER_PASSWORD:?CONFIG_SERVER_PASSWORD is required}" \
  > "${SECRETS_DIR}/config-server-password"
unset CONFIG_SERVER_USERNAME CONFIG_SERVER_PASSWORD

exec "${PROMETHEUS_BIN}" "$@"
