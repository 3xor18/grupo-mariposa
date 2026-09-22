#!/usr/bin/env bash
set -euo pipefail

readonly CHART=deploy/helm/mariposa-service
readonly VALUES_DIR=deploy/helm/values
readonly SERVICES=(config-server products-api clients-api order-processor order-tracker)
readonly ENVIRONMENTS=(staging production)
readonly RELEASE_NAMESPACE="${NAMESPACE:-grupo-mariposa}"
readonly VALIDATION_TAG="${VALIDATION_TAG:-validation}"
readonly KUBERNETES_VERSION="${KUBERNETES_VERSION:-1.33.0}"
readonly CRD_SCHEMAS="https://raw.githubusercontent.com/datreeio/CRDs-catalog/main/\
{{.Group}}/{{.ResourceKind}}_{{.ResourceAPIVersion}}.json"

read -ra HELM <<< "${HELM_CMD:-helm}"
read -ra KUBECONFORM <<< "${KUBECONFORM_CMD:-kubeconform}"

validate() {
  local service="$1" environment="$2"
  local values=(-f "${VALUES_DIR}/${service}.yaml"
    -f "${VALUES_DIR}/${environment}/${service}.yaml")
  echo "==> ${service} (${environment})"
  "${HELM[@]}" lint "${CHART}" --strict "${values[@]}" --set image.tag="${VALIDATION_TAG}"
  "${HELM[@]}" template "${service}" "${CHART}" --namespace "${RELEASE_NAMESPACE}" \
    "${values[@]}" --set image.tag="${VALIDATION_TAG}" \
    | "${KUBECONFORM[@]}" -strict -summary -kubernetes-version "${KUBERNETES_VERSION}" \
      -schema-location default -schema-location "${CRD_SCHEMAS}" -
}

main() {
  local service environment
  for environment in "${ENVIRONMENTS[@]}"; do
    for service in "${SERVICES[@]}"; do
      validate "${service}" "${environment}"
    done
  done
}

main "$@"
