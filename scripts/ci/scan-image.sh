#!/usr/bin/env bash
set -euo pipefail

readonly IMAGE="${1:?usage: scan-image.sh <image:tag>}"
readonly TRIVY_IMAGE="${TRIVY_IMAGE:-aquasec/trivy:0.70.0}"
readonly SEVERITY="${TRIVY_SEVERITY:-CRITICAL,HIGH}"
readonly DOCKER_SOCKET=/var/run/docker.sock

docker run --rm -v "${DOCKER_SOCKET}:${DOCKER_SOCKET}" "${TRIVY_IMAGE}" image \
  --severity "${SEVERITY}" --ignore-unfixed --exit-code 1 --no-progress \
  --skip-version-check "${IMAGE}"
