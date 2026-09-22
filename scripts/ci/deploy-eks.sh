#!/usr/bin/env bash
set -euo pipefail

readonly ALLOWED_SERVICES=(config-server products-api clients-api order-processor order-tracker)
readonly ALLOWED_ENVIRONMENTS=(staging production)
readonly CHART=deploy/helm/mariposa-service
readonly VALUES_DIR=deploy/helm/values
readonly IMAGE_PREFIX=grupo-mariposa
readonly HELM_TIMEOUT="${HELM_TIMEOUT:-10m}"
readonly SESSION_NAME="${AWS_SESSION_NAME:-mariposa-deploy}"
readonly SESSION_SECONDS="${AWS_SESSION_SECONDS:-3600}"

: "${ENVIRONMENT:?ENVIRONMENT is required (staging or production)}"
: "${AWS_REGION:?AWS_REGION is required}"
: "${EKS_CLUSTER:?EKS_CLUSTER is required}"
: "${ECR_REGISTRY:?ECR_REGISTRY is required}"
: "${NAMESPACE:?NAMESPACE is required}"
: "${IMAGE_TAG:?IMAGE_TAG is required}"

contains() {
  local needle="$1"
  shift
  local item
  for item in "$@"; do
    [[ "${item}" == "${needle}" ]] && return 0
  done
  return 1
}

requested_services() {
  local raw="${SERVICES:-$(IFS=','; echo "${ALLOWED_SERVICES[*]}")}"
  local entry service
  local -a entries
  IFS=',' read -ra entries <<< "${raw}"
  for entry in "${entries[@]}"; do
    service="$(echo "${entry}" | tr -d '[:space:]')"
    [[ -n "${service}" ]] && echo "${service}"
  done
}

assume_role() {
  [[ -z "${AWS_ROLE_ARN:-}" ]] && return 0
  local credentials
  credentials="$(aws sts assume-role --role-arn "${AWS_ROLE_ARN}" \
    --role-session-name "${SESSION_NAME}" --duration-seconds "${SESSION_SECONDS}" \
    --query 'Credentials.[AccessKeyId,SecretAccessKey,SessionToken]' --output text)"
  read -r AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_SESSION_TOKEN <<< "${credentials}"
  export AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_SESSION_TOKEN
}

deploy_service() {
  local service="$1"
  local image="${ECR_REGISTRY}/${IMAGE_PREFIX}/${service}"
  docker tag "${IMAGE_PREFIX}/${service}:${IMAGE_TAG}" "${image}:${IMAGE_TAG}"
  docker push "${image}:${IMAGE_TAG}"
  helm upgrade --install "${service}" "${CHART}" --namespace "${NAMESPACE}" --create-namespace \
    -f "${VALUES_DIR}/${service}.yaml" -f "${VALUES_DIR}/${ENVIRONMENT}/${service}.yaml" \
    --set image.repository="${image}" --set image.tag="${IMAGE_TAG}" \
    --atomic --wait --timeout "${HELM_TIMEOUT}"
}

main() {
  if ! contains "${ENVIRONMENT}" "${ALLOWED_ENVIRONMENTS[@]}"; then
    echo "Unknown environment '${ENVIRONMENT}'" >&2
    exit 1
  fi
  local services
  mapfile -t services < <(requested_services)
  if (( ${#services[@]} == 0 )); then
    echo "No services requested" >&2
    exit 1
  fi
  local service
  for service in "${services[@]}"; do
    if ! contains "${service}" "${ALLOWED_SERVICES[@]}"; then
      echo "Unknown service '${service}'. Allowed: ${ALLOWED_SERVICES[*]}" >&2
      exit 1
    fi
  done
  assume_role
  aws eks update-kubeconfig --name "${EKS_CLUSTER}" --region "${AWS_REGION}"
  aws ecr get-login-password --region "${AWS_REGION}" \
    | docker login --username AWS --password-stdin "${ECR_REGISTRY}"
  for service in "${services[@]}"; do
    deploy_service "${service}"
  done
}

main "$@"
