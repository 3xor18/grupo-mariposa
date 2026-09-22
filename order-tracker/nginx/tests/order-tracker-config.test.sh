#!/bin/sh
set -eu

SCRIPT="${1:-$(dirname "$0")/../order-tracker-config.sh}"
TEMPLATE="$(dirname "$0")/../config.json.template"
WORK=$(mktemp -d)
FAKE_BIN="$WORK/bin"
PASSED=0
FAILED=0

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
[ "$calls" -gt "${FAKE_FAILURES:-0}" ] || exit 7
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
  run_case
  check "default api base url" "$(value_of API_BASE_URL)" "/api"
  check "default orders api" "$(value_of ORDERS_API_URL)" "http://order-processor:8080"
  check "default resolver" "$(value_of NGINX_RESOLVER)" "127.0.0.11"
  check "no remote call" "$(cat "$WORK/calls" 2>/dev/null || echo 0)" "0"
}

test_values_from_config_server() {
  run_case CONFIG_SERVER_URL=http://config:8888/ CONFIG_PROFILE=docker
  check "colon separator" "$(value_of API_BASE_URL)" "/gateway/api"
  check "url with colons" "$(value_of KEYCLOAK_URL)" "https://sso.example.com:8443/auth"
  check "equals separator" "$(value_of KEYCLOAK_REALM)" "mariposa-docker"
  check "trimmed key" "$(value_of KEYCLOAK_CLIENT_ID)" "tracker-docker"
  check "orders api" "$(value_of ORDERS_API_URL)" "http://orders.internal:8080"
  check "unknown key ignored" "$(grep -c UNKNOWN "$WORK/out/runtime.env" || true)" "0"
  expected_url='http://config:8888/order-tracker-docker.properties'
  check "profile url" "$(grep -c "$expected_url" "$WORK/argv")" "1"
  check "config.json realm" "$(grep -c '"realm": "mariposa-docker"' "$WORK/out/config.json")" "1"
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
}

test_unreachable_server_continues() {
  run_case CONFIG_SERVER_URL=http://config:8888 FAKE_FAILURES=9
  check "stops after four attempts" "$(cat "$WORK/calls")" "4"
  check "falls back to defaults" "$(value_of KEYCLOAK_REALM)" "mariposa"
}

test_unreachable_server_fails_fast() {
  status=0
  run_case CONFIG_SERVER_URL=http://config:8888 FAKE_FAILURES=9 \
    CONFIG_SERVER_FAIL_FAST=true || status=$?
  check "fail fast exit status" "$status" "1"
}

test_password_is_never_exposed() {
  run_case CONFIG_SERVER_URL=http://config:8888 CONFIG_SERVER_USERNAME=reader \
    CONFIG_SERVER_PASSWORD='s3cr"et'
  check "password not in argv" "$(grep -c 's3cr' "$WORK/argv" || true)" "0"
  check "password not logged" "$(grep -c 's3cr' "$WORK/stderr" || true)" "0"
  check "credentials via stdin" "$(cat "$WORK/stdin")" 'user = "reader:s3cr\"et"'
}

test_unsafe_json_value_is_rejected() {
  status=0
  run_case KEYCLOAK_REALM='bad"realm' || status=$?
  check "json unsafe quote rejected" "$status" "1"
  status=0
  run_case API_BASE_URL='/api\x' || status=$?
  check "json unsafe backslash rejected" "$status" "1"
}

test_quotes_survive_env_file() {
  run_case ORDERS_API_URL="http://o'hara:8080"
  check "single quote round trip" "$(value_of ORDERS_API_URL)" "http://o'hara:8080"
}

install_fake_curl
write_properties
test_defaults_without_config_server
test_values_from_config_server
test_explicit_environment_wins
test_retries_until_success
test_unreachable_server_continues
test_unreachable_server_fails_fast
test_password_is_never_exposed
test_unsafe_json_value_is_rejected
test_quotes_survive_env_file
printf 'order-tracker-config: %s passed, %s failed\n' "$PASSED" "$FAILED"
[ "$FAILED" -eq 0 ]
