#!/usr/bin/env bash
set -euo pipefail

readonly KEYFILE=/data/configdb/replica.key

if [[ ! -f "${KEYFILE}" ]]; then
  head -c 756 /dev/urandom | base64 > "${KEYFILE}"
fi
chmod 400 "${KEYFILE}"
chown mongodb:mongodb "${KEYFILE}"

exec docker-entrypoint.sh mongod --replSet rs0 --keyFile "${KEYFILE}" --bind_ip_all
