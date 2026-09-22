#!/usr/bin/env bash
set -euo pipefail

readonly PROFILE="${1:?usage: check-go-coverage.sh <coverage.out> <min-percent>}"
readonly MIN_PERCENT="${2:?minimum total coverage percent is required}"

if [[ ! "${MIN_PERCENT}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
  echo "Minimum coverage must be numeric, got '${MIN_PERCENT}'" >&2
  exit 1
fi

go tool cover -func="${PROFILE}" | awk -v min="${MIN_PERCENT}" '
  /^total:/ {
    total = $NF
    sub(/%/, "", total)
    printf "Total coverage: %s%%, minimum %s%%\n", total, min
    found = 1
    if (total + 0 < min + 0) { exit 1 }
  }
  END { if (!found) { print "No total line in coverage report"; exit 1 } }
'
