#!/bin/sh
set -eu

runtime_dir=/tmp/order-tracker
mkdir -p "$runtime_dir"
envsubst '${API_BASE_URL} ${KEYCLOAK_URL} ${KEYCLOAK_REALM} ${KEYCLOAK_CLIENT_ID}' \
  < /etc/order-tracker/config.json.template \
  > "$runtime_dir/config.json"
echo "order-tracker: runtime config written to $runtime_dir/config.json"
