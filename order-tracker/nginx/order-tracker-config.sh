#!/bin/sh
set -eu

RUNTIME_DIR="${ORDER_TRACKER_RUNTIME_DIR:-/tmp/order-tracker}"
CONFIG_TEMPLATE="${ORDER_TRACKER_CONFIG_TEMPLATE:-/etc/order-tracker/config.json.template}"
CLIENT_KEYS="API_BASE_URL KEYCLOAK_URL KEYCLOAK_REALM KEYCLOAK_CLIENT_ID"
MANAGED_KEYS="$CLIENT_KEYS ORDERS_API_URL NGINX_RESOLVER"
MAX_ATTEMPTS=4

log() {
  printf 'order-tracker-config: %s\n' "$*" >&2
}

default_for() {
  case "$1" in
    API_BASE_URL) printf '%s' '/api' ;;
    ORDERS_API_URL) printf '%s' 'http://order-processor:8080' ;;
    KEYCLOAK_URL) printf '%s' 'http://localhost:8180' ;;
    KEYCLOAK_REALM) printf '%s' 'mariposa' ;;
    KEYCLOAK_CLIENT_ID) printf '%s' 'order-tracker' ;;
    NGINX_RESOLVER) printf '%s' '127.0.0.11' ;;
  esac
}

properties_to_env() {
  awk '
    function trim(text) { gsub(/^[ \t\r]+|[ \t\r]+$/, "", text); return text }
    function separator(line,    i, c) {
      for (i = 1; i <= length(line); i++) {
        c = substr(line, i, 1)
        if (c == "\\") { i++; continue }
        if (c == "=" || c == ":") { return i }
      }
      return 0
    }
    {
      line = trim($0)
      if (line == "" || line ~ /^[#!]/) { next }
      at = separator(line)
      if (at == 0) { next }
      key = toupper(trim(substr(line, 1, at - 1)))
      gsub(/[.-]/, "_", key)
      print key "=" trim(substr(line, at + 1))
    }
  '
}

property_value() {
  awk -v key="$1" '
    index($0, key "=") == 1 { value = substr($0, length(key) + 2) }
    END { print value }
  ' "$2"
}

properties_url() {
  printf '%s/%s-%s.properties' "${CONFIG_SERVER_URL%/}" "${CONFIG_APP_NAME:-order-tracker}" \
    "${CONFIG_PROFILE:-default}"
}

escape_for_curl() {
  sed -e 's/\\/\\\\/g' -e 's/"/\\"/g'
}

curl_credentials() {
  user=$(printf '%s' "${CONFIG_SERVER_USERNAME:-}" | escape_for_curl)
  password=$(printf '%s' "${CONFIG_SERVER_PASSWORD:-}" | escape_for_curl)
  printf 'user = "%s:%s"\n' "$user" "$password"
}

download_properties() {
  if [ -n "${CONFIG_SERVER_USERNAME:-}" ]; then
    curl_credentials | curl --config - --silent --show-error --fail \
      --max-time "${CONFIG_SERVER_TIMEOUT:-5}" --output "$2" "$1"
  else
    curl --silent --show-error --fail --max-time "${CONFIG_SERVER_TIMEOUT:-5}" --output "$2" "$1"
  fi
}

fetch_properties() {
  url=$(properties_url)
  delay="${CONFIG_SERVER_RETRY_DELAY:-1}"
  attempt=1
  while [ "$attempt" -le "$MAX_ATTEMPTS" ]; do
    if download_properties "$url" "$1" 2>/dev/null; then
      log "loaded $url"
      return 0
    fi
    log "attempt $attempt/$MAX_ATTEMPTS to reach $url failed"
    [ "$attempt" -lt "$MAX_ATTEMPTS" ] && sleep "$delay"
    delay=$((delay * 2))
    attempt=$((attempt + 1))
  done
  return 1
}

load_remote_properties() {
  : > "$1"
  [ -n "${CONFIG_SERVER_URL:-}" ] || return 0
  raw="$1.raw"
  if fetch_properties "$raw"; then
    properties_to_env < "$raw" > "$1"
    rm -f "$raw"
    return 0
  fi
  rm -f "$raw"
  if [ "${CONFIG_SERVER_FAIL_FAST:-false}" = "true" ]; then
    log "config server unreachable and CONFIG_SERVER_FAIL_FAST=true"
    return 1
  fi
  log "config server unreachable, continuing with environment and defaults"
}

resolve() {
  explicit=$(printenv "$1" || true)
  if [ -n "$explicit" ]; then
    printf '%s' "$explicit"
    return 0
  fi
  remote=$(property_value "$1" "$2")
  if [ -n "$remote" ]; then
    printf '%s' "$remote"
    return 0
  fi
  default_for "$1"
}

shell_quote() {
  printf "'%s'" "$(printf '%s' "$1" | sed "s/'/'\\\\''/g")"
}

write_env_file() {
  for key in $MANAGED_KEYS; do
    printf 'export %s=%s\n' "$key" "$(shell_quote "$(resolve "$key" "$1")")"
  done > "$RUNTIME_DIR/runtime.env"
}

assert_json_safe() {
  for key in $CLIENT_KEYS; do
    case "$(printenv "$key")" in
      *'"'* | *"\\"*)
        log "$key contains characters that are not allowed in config.json"
        return 1
        ;;
    esac
  done
}

write_config_json() {
  (
    set -a
    . "$RUNTIME_DIR/runtime.env"
    assert_json_safe
    envsubst '${API_BASE_URL} ${KEYCLOAK_URL} ${KEYCLOAK_REALM} ${KEYCLOAK_CLIENT_ID}' \
      < "$CONFIG_TEMPLATE" > "$RUNTIME_DIR/config.json"
  )
}

main() {
  mkdir -p "$RUNTIME_DIR"
  remote="$RUNTIME_DIR/remote.env"
  load_remote_properties "$remote"
  write_env_file "$remote"
  write_config_json
  rm -f "$remote"
  log "runtime config written to $RUNTIME_DIR"
}

main "$@"
