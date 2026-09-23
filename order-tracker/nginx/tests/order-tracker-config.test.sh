#!/bin/sh
set -eu

SCRIPT="${1:-$(dirname "$0")/../order-tracker-config.sh}"
TEMPLATE="$(dirname "$0")/../config.json.template"
WORK=$(mktemp -d)
FAKE_BIN="$WORK/bin"
PASSED=0
FAILED=0
REQUIRED_ORDERS="ORDERS_API_URL=http://orders:8080"
REQUIRED_KEYCLOAK="KEYCLOAK_URL=http://sso:8180"
REQUIRED_REDIRECT="REDIRECT_URI=http://app:8090/"
REQUIRED_MARKETS="PLATFORM_MARKETS=MX:MXN:es-MX,CL:CLP:es-CL"
REQUIRED_CURRENCIES="PLATFORM_CURRENCIES=MXN:2,CLP:0"

trap 'rm -rf "$WORK"' EXIT

install_fake_curl() {
  mkdir -p "$FAKE_BIN"
  cat > "$FAKE_BIN/curl" <<'FAKE'
#!/bin/sh
printf '%s\n' "$*" >> "$FAKE_LOG"
if [ "$1" = "--config" ]; then
  cat >> "$FAKE_STDIN_LOG"
fi
calls=$(($(cat "$FAKE_CALLS" 2>/dev/null || echo 0) + 1))
echo "$calls" > "$FAKE_CALLS"
if [ "$calls" -le "${FAKE_FAILURES:-0}" ]; then
  echo "curl: (7) Failed to connect to config port 8888" >&2
  exit 7
fi
while [ "$#" -gt 1 ]; do
  [ "$1" = "--output" ] && output="$2"
  shift
done
cp "$FAKE_BODY" "$output"
FAKE
  chmod +x "$FAKE_BIN/curl"
}

write_properties() {
  cat > "$WORK/body.properties" <<'PROPS'
# comment
! another comment
api-base-url: /gateway/api
orders-api-url: http://orders.internal:8080
keycloak.url: https://sso.example.com:8443/auth
keycloak.realm=mariposa-docker
keycloak.client-id : tracker-docker
redirect-uri: https\://tracker.example.com\:8443/
enable-semantics: true
hsts-max-age: 31536000
platform.markets: MX\:MXN\:es-MX,CO:COP:es-CO,PE:PEN:es-PE,CL:CLP:es-CL,EC:USD:es-EC
platform.currencies: MXN:2,COP:2,PEN:2,CLP:0,USD:2
market-names: MX:México,CO:Colombia,PE:Perú,CL:Chile,EC:Ecuador
escaped\:key: ignored
unknown.key: ignored
no-separator-line
PROPS
}

run_case() {
  rm -rf "$WORK/out" "$WORK/calls" "$WORK/argv" "$WORK/stdin" "$WORK/stderr"
  env -i PATH="$FAKE_BIN:$PATH" HOME="$WORK" \
    ORDER_TRACKER_RUNTIME_DIR="$WORK/out" ORDER_TRACKER_CONFIG_TEMPLATE="$TEMPLATE" \
    FAKE_LOG="$WORK/argv" FAKE_STDIN_LOG="$WORK/stdin" FAKE_CALLS="$WORK/calls" \
    FAKE_BODY="$WORK/body.properties" CONFIG_SERVER_RETRY_DELAY=0 \
    "$@" sh "$SCRIPT" 2> "$WORK/stderr"
}

run_local() {
  run_case "$REQUIRED_ORDERS" "$REQUIRED_KEYCLOAK" "$REQUIRED_REDIRECT" \
    "$REQUIRED_MARKETS" "$REQUIRED_CURRENCIES" "$@"
}

status_of() {
  status=0
  "$@" || status=$?
  printf '%s' "$status"
}

value_of() {
  (. "$WORK/out/runtime.env" && printenv "$1")
}

check() {
  if [ "$2" = "$3" ]; then
    PASSED=$((PASSED + 1))
  else
    FAILED=$((FAILED + 1))
    printf 'FAIL %s: expected [%s] got [%s]\n' "$1" "$3" "$2"
  fi
}

test_defaults_without_config_server() {
  run_local
  check "default api base url" "$(value_of API_BASE_URL)" "/api"
  check "default realm" "$(value_of KEYCLOAK_REALM)" "mariposa"
  check "default resolver" "$(value_of NGINX_RESOLVER)" "127.0.0.11"
  check "semantics off by default" "$(value_of ENABLE_SEMANTICS)" "false"
  check "hsts off by default" "$(value_of HSTS_HEADER)" ""
  check "csp keycloak origin" "$(value_of CSP_CONNECT_SOURCES)" "http://sso:8180"
  check "no remote call" "$(cat "$WORK/calls" 2>/dev/null || echo 0)" "0"
  check "config.json boolean" \
    "$(grep -c '"enableSemantics": false' "$WORK/out/config.json")" "1"
}

test_required_values_have_no_defaults() {
  check "missing keycloak url" \
    "$(status_of run_case "$REQUIRED_ORDERS" "$REQUIRED_REDIRECT")" "1"
  check "reports missing key" "$(grep -c 'KEYCLOAK_URL is required' "$WORK/stderr")" "1"
  check "missing orders api" \
    "$(status_of run_case "$REQUIRED_KEYCLOAK" "$REQUIRED_REDIRECT")" "1"
  check "missing redirect" \
    "$(status_of run_case "$REQUIRED_ORDERS" "$REQUIRED_KEYCLOAK")" "1"
  check "missing currencies" "$(status_of run_case "$REQUIRED_ORDERS" "$REQUIRED_KEYCLOAK" \
    "$REQUIRED_REDIRECT" "$REQUIRED_MARKETS")" "1"
  check "reports missing catalog" \
    "$(grep -c 'PLATFORM_CURRENCIES is required' "$WORK/stderr")" "1"
  check "missing markets" "$(status_of run_case "$REQUIRED_ORDERS" "$REQUIRED_KEYCLOAK" \
    "$REQUIRED_REDIRECT" "$REQUIRED_CURRENCIES")" "1"
}

test_market_catalog_from_config_server() {
  run_case CONFIG_SERVER_URL=http://config:8888 CONFIG_PROFILE=docker
  expected_markets='"markets": [{"code":"MX","currency":"MXN","locale":"es-MX","name":"México"},'
  check "catalog markets json" "$(grep -cF "$expected_markets" "$WORK/out/config.json")" "1"
  check "catalog shared currency" \
    "$(grep -cF '{"code":"EC","currency":"USD","locale":"es-EC","name":"Ecuador"}]' \
      "$WORK/out/config.json")" "1"
  check "catalog currencies json" \
    "$(grep -cF '"currencies": {"MXN":2,"COP":2,"PEN":2,"CLP":0,"USD":2}' \
      "$WORK/out/config.json")" "1"
}

test_market_names_are_optional() {
  run_local
  check "name falls back to code" \
    "$(grep -cF '{"code":"CL","currency":"CLP","locale":"es-CL","name":"CL"}' \
      "$WORK/out/config.json")" "1"
  run_local MARKET_NAMES='CL: Chile ,BR:Brasil'
  check "names trimmed and unknown ignored" \
    "$(grep -cF '{"code":"CL","currency":"CLP","locale":"es-CL","name":"Chile"}' \
      "$WORK/out/config.json")" "1"
}

test_invalid_catalogs_are_rejected() {
  check "lowercase market" "$(status_of run_local PLATFORM_MARKETS=mx:MXN:es-MX)" "1"
  check "missing locale" "$(status_of run_local PLATFORM_MARKETS=MX:MXN)" "1"
  check "language only locale" "$(status_of run_local PLATFORM_MARKETS=MX:MXN:es)" "1"
  check "underscore locale" "$(status_of run_local PLATFORM_MARKETS=MX:MXN:es_MX)" "1"
  check "duplicated market" \
    "$(status_of run_local PLATFORM_MARKETS=MX:MXN:es-MX,MX:MXN:es-MX)" "1"
  check "reports duplicated market" \
    "$(grep -c 'declares MX more than once' "$WORK/stderr")" "1"
  check "digits out of range" "$(status_of run_local PLATFORM_CURRENCIES=MXN:2,CLP:9)" "1"
  check "undeclared currency" \
    "$(status_of run_local PLATFORM_MARKETS=MX:MXN:es-MX,EC:USD:es-EC)" "1"
  check "reports undeclared currency" \
    "$(grep -c 'uses USD, which is missing' "$WORK/stderr")" "1"
  check "quote in name" "$(status_of run_local MARKET_NAMES='MX:Mé"xico')" "1"
  check "semicolon in name" "$(status_of run_local MARKET_NAMES='MX:México;')" "1"
  check "backslash in name" "$(status_of run_local MARKET_NAMES='MX:Mé\xico')" "1"
  tab=$(printf '\t')
  check "control character in name" "$(status_of run_local MARKET_NAMES="MX:Mé${tab}x")" "1"
}

test_values_from_config_server() {
  run_case CONFIG_SERVER_URL=http://config:8888/ CONFIG_PROFILE=docker
  check "colon separator" "$(value_of API_BASE_URL)" "/gateway/api"
  check "url with colons" "$(value_of KEYCLOAK_URL)" "https://sso.example.com:8443/auth"
  check "equals separator" "$(value_of KEYCLOAK_REALM)" "mariposa-docker"
  check "trimmed key" "$(value_of KEYCLOAK_CLIENT_ID)" "tracker-docker"
  check "orders api" "$(value_of ORDERS_API_URL)" "http://orders.internal:8080"
  check "unescaped colons" "$(value_of REDIRECT_URI)" "https://tracker.example.com:8443/"
  check "semantics flag" "$(value_of ENABLE_SEMANTICS)" "true"
  check "hsts header" "$(value_of HSTS_HEADER)" "max-age=31536000; includeSubDomains"
  check "csp keycloak origin only" "$(value_of CSP_CONNECT_SOURCES)" \
    "https://sso.example.com:8443"
  check "unknown keys ignored" \
    "$(grep -cE 'UNKNOWN|ESCAPED' "$WORK/out/runtime.env" || true)" "0"
  expected_url='http://config:8888/order-tracker-docker.properties'
  check "profile url" "$(grep -c "$expected_url" "$WORK/argv")" "1"
  check "config.json realm" \
    "$(grep -c '"realm": "mariposa-docker"' "$WORK/out/config.json")" "1"
  check "config.json redirect" \
    "$(grep -c '"redirectUri": "https://tracker.example.com:8443/"' "$WORK/out/config.json")" "1"
}

test_absolute_api_base_joins_csp() {
  run_local API_BASE_URL=https://api.example.com/orders-api
  check "csp api origin" "$(value_of CSP_CONNECT_SOURCES)" \
    "http://sso:8180 https://api.example.com"
}

test_explicit_environment_wins() {
  run_case CONFIG_SERVER_URL=http://config:8888 KEYCLOAK_URL=http://override:9000 \
    CONFIG_APP_NAME=tracker
  check "explicit env wins" "$(value_of KEYCLOAK_URL)" "http://override:9000"
  check "server fills the rest" "$(value_of KEYCLOAK_REALM)" "mariposa-docker"
  check "app name url" "$(grep -c '/tracker-default.properties' "$WORK/argv")" "1"
}

test_retries_until_success() {
  run_case CONFIG_SERVER_URL=http://config:8888 FAKE_FAILURES=3
  check "fourth attempt succeeds" "$(cat "$WORK/calls")" "4"
  check "value after retries" "$(value_of KEYCLOAK_REALM)" "mariposa-docker"
  check "curl error surfaced" "$(grep -c 'Failed to connect' "$WORK/stderr")" "3"
}

test_unreachable_server_fails_fast_by_default() {
  check "fail fast by default" \
    "$(status_of run_local CONFIG_SERVER_URL=http://config:8888 FAKE_FAILURES=9)" "1"
  check "stops after four attempts" "$(cat "$WORK/calls")" "4"
}

test_unreachable_server_can_continue() {
  run_local CONFIG_SERVER_URL=http://config:8888 FAKE_FAILURES=9 CONFIG_SERVER_FAIL_FAST=false
  check "falls back to environment" "$(value_of KEYCLOAK_URL)" "http://sso:8180"
  check "falls back to defaults" "$(value_of KEYCLOAK_REALM)" "mariposa"
}

test_password_is_never_exposed() {
  run_case CONFIG_SERVER_URL=http://config:8888 CONFIG_SERVER_USERNAME=reader \
    CONFIG_SERVER_PASSWORD='s3cr"et'
  check "password not in argv" "$(grep -c 's3cr' "$WORK/argv" || true)" "0"
  check "password not logged" "$(grep -c 's3cr' "$WORK/stderr" || true)" "0"
  check "credentials via stdin" "$(cat "$WORK/stdin")" 'user = "reader:s3cr\"et"'
}

test_unsafe_values_are_rejected() {
  check "semicolon in url" "$(status_of run_local ORDERS_API_URL='http://o:1;evil')" "1"
  check "reported invalid" "$(grep -c 'ORDERS_API_URL has an invalid value' "$WORK/stderr")" "1"
  check "space in url" "$(status_of run_local KEYCLOAK_URL='http://sso 8180')" "1"
  check "dollar in url" "$(status_of run_local REDIRECT_URI='http://app/$host')" "1"
  check "quote in realm" "$(status_of run_local KEYCLOAK_REALM='bad"realm')" "1"
  check "backslash in base" "$(status_of run_local API_BASE_URL='/api\x')" "1"
  check "non http url" "$(status_of run_local ORDERS_API_URL='file:///etc/passwd')" "1"
  check "resolver with directive" \
    "$(status_of run_local NGINX_RESOLVER='127.0.0.11; include /tmp/x')" "1"
  check "boolean flag" "$(status_of run_local ENABLE_SEMANTICS=yes)" "1"
  check "numeric max age" "$(status_of run_local HSTS_MAX_AGE=forever)" "1"
  check "multi line value" "$(status_of run_local KEYCLOAK_CLIENT_ID="a
b")" "1"
}

install_fake_curl
write_properties
test_defaults_without_config_server
test_required_values_have_no_defaults
test_values_from_config_server
test_market_catalog_from_config_server
test_market_names_are_optional
test_invalid_catalogs_are_rejected
test_absolute_api_base_joins_csp
test_explicit_environment_wins
test_retries_until_success
test_unreachable_server_fails_fast_by_default
test_unreachable_server_can_continue
test_password_is_never_exposed
test_unsafe_values_are_rejected
printf 'order-tracker-config: %s passed, %s failed\n' "$PASSED" "$FAILED"
[ "$FAILED" -eq 0 ]
