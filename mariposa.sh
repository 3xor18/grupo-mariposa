#!/usr/bin/env bash
set -euo pipefail

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly ENV_FILE="${ROOT_DIR}/.env"
readonly ENV_TEMPLATE="${ROOT_DIR}/.env.example"
readonly SAMPLES_DIR="${ROOT_DIR}/samples/events"
readonly KAFKA_BIN=/opt/kafka/bin
readonly INPUT_TOPIC=orders.created.v1
readonly KEYCLOAK_TOKEN_PATH=/realms/mariposa/protocol/openid-connect/token
readonly FLUTTER_IMAGE=ghcr.io/cirruslabs/flutter:stable
readonly SECRET_KEYS=(MONGO_ROOT_PASSWORD MONGO_APP_PASSWORD REDIS_PASSWORD KEYCLOAK_ADMIN_PASSWORD
  ORDER_PROCESSOR_CLIENT_SECRET DEMO_USER_PASSWORD GRAFANA_ADMIN_PASSWORD)

random_secret() { head -c 48 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 32; }
random_aes_key() { head -c 32 /dev/urandom | base64 | tr -d '\n'; }

set_env_value() {
  local key="$1" value="$2"
  sed -i.bak "s|^${key}=.*|${key}=${value}|" "${ENV_FILE}" && rm -f "${ENV_FILE}.bak"
}

env_value() { grep -E "^$1=" "${ENV_FILE}" | cut -d= -f2-; }

cmd_init() {
  if [[ -f "${ENV_FILE}" ]]; then
    echo ".env ya existe; no se regenera."
    return
  fi
  cp "${ENV_TEMPLATE}" "${ENV_FILE}"
  for key in "${SECRET_KEYS[@]}"; do set_env_value "${key}" "$(random_secret)"; done
  set_env_value PII_ENCRYPTION_KEY "$(random_aes_key)"
  echo ".env generado con secretos aleatorios."
}

cmd_up() {
  cmd_init
  docker compose --env-file "${ENV_FILE}" up -d --build --wait "$@"
  cmd_urls
}

cmd_urls() {
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
  Password demo       $(env_value DEMO_USER_PASSWORD)
EOF
}

cmd_publish() {
  local file="$1"
  [[ -f "${file}" ]] || file="${SAMPLES_DIR}/${file}"
  local payload key
  payload="$(tr -d '\r\n' < "${file}")"
  key="$(grep -o '"orderId"[^,]*' "${file}" | head -1 | sed -E 's/.*"([^"]+)"$/\1/')"
  printf '%s|%s\n' "${key}" "${payload}" | docker compose exec -T kafka \
    "${KAFKA_BIN}/kafka-console-producer.sh" --bootstrap-server kafka:9092 --topic "${INPUT_TOPIC}" \
    --property parse.key=true --property "key.separator=|"
  echo "Publicado ${key} desde $(basename "${file}")"
}

cmd_scenarios() {
  for file in "${SAMPLES_DIR}"/*.json; do cmd_publish "${file}"; done
}

cmd_consume() {
  local topic="${1:-orders.processed.v1}"
  docker compose exec kafka "${KAFKA_BIN}/kafka-console-consumer.sh" --bootstrap-server kafka:9092 \
    --topic "${topic}" --from-beginning --property print.key=true --property print.headers=true \
    --timeout-ms 5000 || true
}

cmd_token() {
  local user="${1:-analyst}"
  curl -s -X POST "$(env_value KEYCLOAK_PUBLIC_URL)${KEYCLOAK_TOKEN_PATH}" \
    -d grant_type=password -d client_id=orders-cli -d "username=${user}" \
    -d "password=$(env_value DEMO_USER_PASSWORD)" | sed -E 's/.*"access_token":"([^"]+)".*/\1/'
}

cmd_mongo() {
  docker compose exec mongo mongosh --quiet -u "$(env_value MONGO_ROOT_USERNAME)" \
    -p "$(env_value MONGO_ROOT_PASSWORD)" --authenticationDatabase admin orders \
    --eval "${1:-db.orders.find().sort({processedAt:-1}).limit(5).toArray()}"
}

cmd_test() {
  (cd "${ROOT_DIR}/products-api" && go test ./... -race -cover)
  (cd "${ROOT_DIR}/clients-api" && npm ci && npm run test:cov && npm run test:e2e)
  (cd "${ROOT_DIR}/order-processor" && ./mvnw -B verify)
  docker run --rm -v "${ROOT_DIR}/order-tracker:/app" -w /app "${FLUTTER_IMAGE}" \
    sh -c "flutter pub get && flutter test --coverage"
}

cmd_e2e() {
  (cd "${ROOT_DIR}/e2e/karate" && ./mvnw -B test \
    -Dkeycloak.url="$(env_value KEYCLOAK_PUBLIC_URL)" -Ddemo.password="$(env_value DEMO_USER_PASSWORD)")
  (cd "${ROOT_DIR}/order-tracker/e2e" && npm ci && npx playwright install chromium \
    && E2E_USERNAME=analyst E2E_PASSWORD="$(env_value DEMO_USER_PASSWORD)" npx playwright test)
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
  urls                 muestra las URLs y la password demo
  publish <archivo>    publica un evento de samples/events en orders.created.v1
  scenarios            publica todos los escenarios de samples/events
  consume [tópico]     lee orders.processed.v1 (o el tópico indicado)
  token [usuario]      obtiene un access token (analyst por defecto)
  mongo [expresión]    consulta la base orders
  test                 corre las pruebas de los cuatro componentes
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
    urls) cmd_urls ;;
    publish) cmd_publish "$1" ;;
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
