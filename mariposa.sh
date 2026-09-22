#!/usr/bin/env bash
set -euo pipefail
export MSYS_NO_PATHCONV=1

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly ROOT_DIR
readonly ENV_FILE="${ROOT_DIR}/.env"
readonly ENV_TEMPLATE="${ROOT_DIR}/.env.example"
readonly SAMPLES_DIR="${ROOT_DIR}/samples/events"
readonly KAFKA_BIN=/opt/kafka/bin
readonly KAFKA_INTERNAL_BOOTSTRAP=kafka:9092
readonly INPUT_TOPIC=orders.created.v1
readonly DEFAULT_CONSUME_TOPIC=orders.processed.v1
readonly CONSUME_TIMEOUT_MS=5000
readonly KEYCLOAK_TOKEN_PATH=/realms/mariposa/protocol/openid-connect/token
readonly TOKEN_CLIENT_ID=orders-cli
readonly DEFAULT_USER=analyst
readonly FLUTTER_VERSION=3.44.0
readonly FLUTTER_IMAGE="ghcr.io/cirruslabs/flutter:${FLUTTER_VERSION}"
readonly SHOW_SECRETS_FLAG=--show-secrets
readonly SECRET_LENGTH=32
readonly SECRET_ENTROPY_BYTES=48
readonly AES_KEY_BYTES=32
readonly DEFAULT_MONGO_QUERY='db.orders.find().sort({processedAt:-1}).limit(5).toArray()'
readonly SECRET_KEYS=(MONGO_ROOT_PASSWORD MONGO_APP_PASSWORD REDIS_PASSWORD
  KEYCLOAK_ADMIN_PASSWORD ORDER_PROCESSOR_CLIENT_SECRET DEMO_USER_PASSWORD
  GRAFANA_ADMIN_PASSWORD CONFIG_SERVER_PASSWORD)

random_secret() {
  head -c "${SECRET_ENTROPY_BYTES}" /dev/urandom | base64 | tr -dc 'A-Za-z0-9' \
    | head -c "${SECRET_LENGTH}"
}

random_aes_key() { head -c "${AES_KEY_BYTES}" /dev/urandom | base64 | tr -d '\n'; }

set_env_value() {
  local work_file
  work_file="$(mktemp "${ENV_FILE}.XXXXXX")"
  ENV_KEY="$1" ENV_VALUE="$2" awk '
    BEGIN { prefix = ENVIRON["ENV_KEY"] "=" }
    index($0, prefix) == 1 { print prefix ENVIRON["ENV_VALUE"]; next }
    { print }
  ' "${ENV_FILE}" > "${work_file}"
  mv "${work_file}" "${ENV_FILE}"
}

env_value() { grep -E "^$1=" "${ENV_FILE}" | cut -d= -f2-; }

require_env_file() {
  if [[ ! -f "${ENV_FILE}" ]]; then
    echo "No existe .env; ejecuta ./mariposa.sh init" >&2
    exit 1
  fi
}

cmd_init() {
  if [[ -f "${ENV_FILE}" ]]; then
    echo ".env ya existe; no se regenera."
    return
  fi
  umask 077
  cp "${ENV_TEMPLATE}" "${ENV_FILE}"
  local key
  for key in "${SECRET_KEYS[@]}"; do set_env_value "${key}" "$(random_secret)"; done
  set_env_value PII_ENCRYPTION_KEY "$(random_aes_key)"
  echo ".env generado con secretos aleatorios."
}

cmd_up() {
  cmd_init
  docker compose --env-file "${ENV_FILE}" up -d --build --wait "$@"
  cmd_urls
}

demo_password_line() {
  if [[ "${1:-}" == "${SHOW_SECRETS_FLAG}" ]]; then
    echo "  Password demo       $(env_value DEMO_USER_PASSWORD)"
  else
    echo "  Password demo       ./mariposa.sh urls ${SHOW_SECRETS_FLAG}"
  fi
}

cmd_urls() {
  require_env_file
  cat <<EOF

  Plataforma arriba
  -----------------
  PWA order-tracker   http://localhost:8090   (usuarios: analyst | admin | viewer)
  Swagger orders      http://localhost:8080/swagger-ui.html
  Swagger clients     http://localhost:8082/docs
  Keycloak            http://localhost:8180
  Kafka UI            http://localhost:8085
  Grafana             http://localhost:3001
  Prometheus          http://localhost:9090
  Jaeger              http://localhost:16686
EOF
  demo_password_line "${1:-}"
}

cmd_publish() {
  if [[ $# -lt 1 || -z "$1" ]]; then
    echo "Uso: ./mariposa.sh publish <archivo de samples/events>" >&2
    exit 1
  fi
  local file="$1"
  [[ -f "${file}" ]] || file="${SAMPLES_DIR}/${file}"
  if [[ ! -f "${file}" ]]; then
    echo "No existe el archivo ${1}" >&2
    exit 1
  fi
  local payload key
  payload="$(tr -d '\r\n' < "${file}")"
  key="$(grep -o '"orderId"[^,]*' "${file}" | head -1 | sed -E 's/.*"([^"]+)"$/\1/')"
  printf '%s|%s\n' "${key}" "${payload}" | docker compose exec -T kafka \
    "${KAFKA_BIN}/kafka-console-producer.sh" --bootstrap-server "${KAFKA_INTERNAL_BOOTSTRAP}" \
    --topic "${INPUT_TOPIC}" --property parse.key=true --property "key.separator=|"
  echo "Publicado ${key} desde $(basename "${file}")"
}

cmd_scenarios() {
  local file
  for file in "${SAMPLES_DIR}"/*.json; do cmd_publish "${file}"; done
}

cmd_consume() {
  local topic="${1:-${DEFAULT_CONSUME_TOPIC}}"
  docker compose exec kafka "${KAFKA_BIN}/kafka-console-consumer.sh" \
    --bootstrap-server "${KAFKA_INTERNAL_BOOTSTRAP}" --topic "${topic}" --from-beginning \
    --property print.key=true --property print.headers=true \
    --timeout-ms "${CONSUME_TIMEOUT_MS}" || true
}

cmd_token() {
  require_env_file
  local user="${1:-${DEFAULT_USER}}"
  env_value DEMO_USER_PASSWORD | tr -d '\n' \
    | curl -s -X POST "$(env_value KEYCLOAK_PUBLIC_URL)${KEYCLOAK_TOKEN_PATH}" \
      -d grant_type=password -d "client_id=${TOKEN_CLIENT_ID}" -d "username=${user}" \
      --data-urlencode password@- \
    | sed -E 's/.*"access_token":"([^"]+)".*/\1/'
}

cmd_mongo() {
  local query="${1:-${DEFAULT_MONGO_QUERY}}"
  local script="db.getSiblingDB('admin').auth(process.env.MONGO_INITDB_ROOT_USERNAME,
    process.env.MONGO_INITDB_ROOT_PASSWORD); db = db.getSiblingDB('orders'); ${query}"
  docker compose exec -T mongo mongosh --quiet --eval "${script}"
}

cmd_test() {
  (cd "${ROOT_DIR}/products-api" && go test ./... -race -cover)
  (cd "${ROOT_DIR}/clients-api" && npm ci && npm run lint && npm run test:cov)
  (cd "${ROOT_DIR}/order-processor" && ./mvnw -B verify)
  (cd "${ROOT_DIR}/config-server" && ./mvnw -B verify)
  docker run --rm -v "${ROOT_DIR}/order-tracker:/app" -w /app "${FLUTTER_IMAGE}" \
    sh -c "flutter pub get && flutter test --coverage"
}

cmd_e2e() {
  require_env_file
  local keycloak_url demo_password
  keycloak_url="$(env_value KEYCLOAK_PUBLIC_URL)"
  demo_password="$(env_value DEMO_USER_PASSWORD)"
  (cd "${ROOT_DIR}/e2e/karate" \
    && KEYCLOAK_URL="${keycloak_url}" DEMO_PASSWORD="${demo_password}" ./mvnw -B test)
  (cd "${ROOT_DIR}/order-tracker/e2e" && npm ci && npx playwright install chromium \
    && E2E_USERNAME="${DEFAULT_USER}" E2E_PASSWORD="${demo_password}" npx playwright test)
}

usage() {
  cat <<EOF
Uso: ./mariposa.sh <comando>
  init                 genera .env con secretos aleatorios
  up                   construye y levanta toda la plataforma (espera healthchecks)
  down                 detiene la plataforma
  clean                detiene y borra volúmenes
  status               estado de los contenedores
  logs [servicio]      sigue los logs
  urls [${SHOW_SECRETS_FLAG}]  muestra las URLs (y la password demo con ${SHOW_SECRETS_FLAG})
  publish <archivo>    publica un evento de samples/events en ${INPUT_TOPIC}
  scenarios            publica todos los escenarios de samples/events
  consume [tópico]     lee ${DEFAULT_CONSUME_TOPIC} (o el tópico indicado)
  token [usuario]      obtiene un access token (${DEFAULT_USER} por defecto)
  mongo [expresión]    consulta la base orders
  test                 corre las pruebas de los componentes
  e2e                  corre Karate + Playwright contra la plataforma levantada
EOF
}

main() {
  local command="${1:-help}"
  shift || true
  cd "${ROOT_DIR}"
  case "${command}" in
    init) cmd_init ;;
    up) cmd_up "$@" ;;
    down) docker compose down ;;
    clean) docker compose down -v ;;
    status) docker compose ps ;;
    logs) docker compose logs -f "$@" ;;
    urls) cmd_urls "$@" ;;
    publish) cmd_publish "$@" ;;
    scenarios) cmd_scenarios ;;
    consume) cmd_consume "$@" ;;
    token) cmd_token "$@" ;;
    mongo) cmd_mongo "$@" ;;
    test) cmd_test ;;
    e2e) cmd_e2e ;;
    *) usage ;;
  esac
}

main "$@"
