#!/bin/sh
set -eu

RUNTIME_DIR="${ORDER_TRACKER_RUNTIME_DIR:-/tmp/order-tracker}"
CONFIG_TEMPLATE="${ORDER_TRACKER_CONFIG_TEMPLATE:-/etc/order-tracker/config.json.template}"
DEFAULT_TIMEOUT_SECONDS=5
DEFAULT_RETRY_DELAY_SECONDS=1
MAX_ATTEMPTS=4
HSTS_DIRECTIVES='includeSubDomains'
MANAGED_KEYS="API_BASE_URL ORDERS_API_URL KEYCLOAK_URL KEYCLOAK_REALM KEYCLOAK_CLIENT_ID
REDIRECT_URI ENABLE_SEMANTICS HSTS_MAX_AGE NGINX_RESOLVER PLATFORM_MARKETS PLATFORM_CURRENCIES
MARKET_NAMES"
OPTIONAL_KEYS="MARKET_NAMES"
URL_PATTERN='^https?://[A-Za-z0-9._~:/?#@!&()*+,=%-]+$'
MARKET_ENTRY='[A-Z]{2}:[A-Z]{3}:[a-z]{2}-[A-Z]{2}'
CURRENCY_ENTRY='[A-Z]{3}:[0-4]'
MARKET_NAME_ENTRY='[A-Z]{2}:[^,:"\\$;<>{}|&`]+'
PATH_PATTERN='^/[A-Za-z0-9._~/-]*$'
NAME_PATTERN='^[A-Za-z0-9._-]+$'
HOST_PATTERN='^[]A-Za-z0-9.:[-]+$'
BOOLEAN_PATTERN='^(true|false)$'
NUMBER_PATTERN='^[0-9]+$'
NEWLINE='
'

log() {
  printf 'order-tracker-config: %s\n' "$*" >&2
}

default_for() {
  case "$1" in
    API_BASE_URL) printf '%s' '/api' ;;
    KEYCLOAK_REALM) printf '%s' 'mariposa' ;;
    KEYCLOAK_CLIENT_ID) printf '%s' 'order-tracker' ;;
    ENABLE_SEMANTICS) printf '%s' 'false' ;;
    HSTS_MAX_AGE) printf '%s' '0' ;;
    NGINX_RESOLVER) printf '%s' '127.0.0.11' ;;
    *) printf '%s' '' ;;
  esac
}

pattern_for() {
  case "$1" in
    API_BASE_URL) printf '%s|%s' "$URL_PATTERN" "$PATH_PATTERN" ;;
    KEYCLOAK_REALM | KEYCLOAK_CLIENT_ID) printf '%s' "$NAME_PATTERN" ;;
    ENABLE_SEMANTICS) printf '%s' "$BOOLEAN_PATTERN" ;;
    HSTS_MAX_AGE) printf '%s' "$NUMBER_PATTERN" ;;
    NGINX_RESOLVER) printf '%s' "$HOST_PATTERN" ;;
    PLATFORM_MARKETS) list_pattern "$MARKET_ENTRY" ;;
    PLATFORM_CURRENCIES) list_pattern "$CURRENCY_ENTRY" ;;
    MARKET_NAMES) list_pattern "$MARKET_NAME_ENTRY" ;;
    *) printf '%s' "$URL_PATTERN" ;;
  esac
}

list_pattern() {
  printf '^%s(,%s)*$' "$1" "$1"
}

is_optional() {
  case " $OPTIONAL_KEYS " in
    *" $1 "*) return 0 ;;
    *) return 1 ;;
  esac
}

properties_to_env() {
  awk '
    function trim(text) { gsub(/^[ \t\r]+|[ \t\r]+$/, "", text); return text }
    function unescape(text,    i, c, out) {
      out = ""
      for (i = 1; i <= length(text); i++) {
        c = substr(text, i, 1)
        if (c == "\\" && i < length(text)) { i++; c = substr(text, i, 1) }
        out = out c
      }
      return out
    }
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
      key = toupper(unescape(trim(substr(line, 1, at - 1))))
      gsub(/[.-]/, "_", key)
      print key "=" unescape(trim(substr(line, at + 1)))
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
  timeout="${CONFIG_SERVER_TIMEOUT:-$DEFAULT_TIMEOUT_SECONDS}"
  if [ -n "${CONFIG_SERVER_USERNAME:-}" ]; then
    curl_credentials | curl --config - --silent --show-error --fail \
      --max-time "$timeout" --output "$2" "$1"
  else
    curl --silent --show-error --fail --max-time "$timeout" --output "$2" "$1"
  fi
}

fetch_properties() {
  url=$(properties_url)
  delay="${CONFIG_SERVER_RETRY_DELAY:-$DEFAULT_RETRY_DELAY_SECONDS}"
  attempt=1
  while [ "$attempt" -le "$MAX_ATTEMPTS" ]; do
    if error=$(download_properties "$url" "$1" 2>&1); then
      log "loaded $url"
      return 0
    fi
    log "attempt $attempt/$MAX_ATTEMPTS to reach $url failed: ${error:-no details}"
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
  if [ "${CONFIG_SERVER_FAIL_FAST:-true}" = "true" ]; then
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

validate() {
  if [ -z "$2" ] && is_optional "$1"; then
    return 0
  fi
  case "$2" in
    '')
      log "$1 is required (environment variable or config server key)"
      return 1
      ;;
    *"$NEWLINE"*)
      log "$1 must be a single line"
      return 1
      ;;
  esac
  if printf '%s' "$2" | grep -q '[[:cntrl:]]'; then
    log "$1 contains control characters"
    return 1
  fi
  if ! printf '%s' "$2" | grep -Eq "$(pattern_for "$1")"; then
    log "$1 has an invalid value"
    return 1
  fi
}

shell_quote() {
  printf "'%s'" "$(printf '%s' "$1" | sed "s/'/'\\\\''/g")"
}

origin_of() {
  printf '%s' "$1" | sed -nE 's#^(https?://[^/?#]+).*#\1#p'
}

hsts_header() {
  if [ "$1" -gt 0 ]; then
    printf 'max-age=%s; %s' "$1" "$HSTS_DIRECTIVES"
  fi
}

list_entries() {
  printf '%s\n' "$1" | tr ',' '\n'
}

validate_unique_markets() {
  duplicate=$(list_entries "$1" | cut -d: -f1 | sort | uniq -d | head -n 1)
  if [ -n "$duplicate" ]; then
    log "PLATFORM_MARKETS declares $duplicate more than once"
    return 1
  fi
}

validate_catalog_currencies() {
  validate_unique_markets "$1" || return 1
  known=",$2,"
  for currency in $(list_entries "$1" | cut -d: -f2); do
    case "$known" in
      *",$currency:"*) ;;
      *)
        log "PLATFORM_MARKETS uses $currency, which is missing from PLATFORM_CURRENCIES"
        return 1
        ;;
    esac
  done
}

markets_json() {
  list_entries "$1" | awk -F: -v names="$2" '
    BEGIN {
      count = split(names, pairs, ",")
      for (i = 1; i <= count; i++) {
        split(pairs[i], pair, ":")
        gsub(/^[ \t]+|[ \t]+$/, "", pair[2])
        label[pair[1]] = pair[2]
      }
    }
    {
      name = ($1 in label) ? label[$1] : $1
      entry = sprintf("{\"code\":\"%s\",\"currency\":\"%s\",\"locale\":\"%s\",\"name\":\"%s\"}",
        $1, $2, $3, name)
      items = items separator entry
      separator = ","
    }
    END { printf "[%s]", items }
  '
}

currencies_json() {
  list_entries "$1" | awk -F: '
    { items = items separator sprintf("\"%s\":%s", $1, $2); separator = "," }
    END { printf "{%s}", items }
  '
}

write_env_file() {
  for key in $MANAGED_KEYS; do
    value=$(resolve "$key" "$1")
    validate "$key" "$value"
    printf 'export %s=%s\n' "$key" "$(shell_quote "$value")"
  done > "$RUNTIME_DIR/runtime.env"
}

derived_values() {
  . "$1"
  validate_catalog_currencies "$PLATFORM_MARKETS" "$PLATFORM_CURRENCIES" || exit 1
  sources="$(origin_of "$KEYCLOAK_URL") $(origin_of "$API_BASE_URL")"
  printf 'export CSP_CONNECT_SOURCES=%s\n' "$(shell_quote "${sources% }")"
  printf 'export HSTS_HEADER=%s\n' "$(shell_quote "$(hsts_header "$HSTS_MAX_AGE")")"
  printf 'export MARKETS_JSON=%s\n' \
    "$(shell_quote "$(markets_json "$PLATFORM_MARKETS" "$MARKET_NAMES")")"
  printf 'export CURRENCIES_JSON=%s\n' \
    "$(shell_quote "$(currencies_json "$PLATFORM_CURRENCIES")")"
}

append_derived_values() {
  derived=$(derived_values "$RUNTIME_DIR/runtime.env")
  printf '%s\n' "$derived" >> "$RUNTIME_DIR/runtime.env"
}

write_config_json() {
  (
    set -a
    . "$RUNTIME_DIR/runtime.env"
    envsubst '${API_BASE_URL} ${KEYCLOAK_URL} ${KEYCLOAK_REALM} ${KEYCLOAK_CLIENT_ID}
      ${REDIRECT_URI} ${ENABLE_SEMANTICS} ${MARKETS_JSON} ${CURRENCIES_JSON}' \
      < "$CONFIG_TEMPLATE" > "$RUNTIME_DIR/config.json"
  )
}

main() {
  mkdir -p "$RUNTIME_DIR"
  remote="$RUNTIME_DIR/remote.env"
  load_remote_properties "$remote"
  write_env_file "$remote"
  append_derived_values
  write_config_json
  rm -f "$remote"
  log "runtime config written to $RUNTIME_DIR"
}

main "$@"
