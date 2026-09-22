#!/usr/bin/env bash
set -euo pipefail

readonly SERVICES=(products-api clients-api order-processor order-tracker)
readonly CHART=deploy/helm/mariposa-service

aws eks update-kubeconfig --name "${EKS_CLUSTER}" --region "${AWS_REGION}"
aws ecr get-login-password --region "${AWS_REGION}" \
  | docker login --username AWS --password-stdin "${ECR_REGISTRY}"

for service in "${SERVICES[@]}"; do
  image="${ECR_REGISTRY}/grupo-mariposa/${service}"
  docker tag "grupo-mariposa/${service}:${IMAGE_TAG}" "${image}:${IMAGE_TAG}"
  docker push "${image}:${IMAGE_TAG}"
  helm upgrade --install "${service}" "${CHART}" --namespace "${NAMESPACE}" --create-namespace \
    -f "deploy/helm/values/${service}.yaml" \
    --set image.repository="${image}" --set image.tag="${IMAGE_TAG}" \
    --atomic --wait --timeout 10m
done
