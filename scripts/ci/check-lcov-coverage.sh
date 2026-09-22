#!/usr/bin/env bash
set -euo pipefail

readonly LCOV_FILE="${1:?usage: check-lcov-coverage.sh <lcov.info> <min-percent> [exclude-regex]}"
readonly MIN_PERCENT="${2:?minimum line coverage percent is required}"
readonly EXCLUDE_REGEX="${3:-^$}"
export EXCLUDE_REGEX

if [[ ! "${MIN_PERCENT}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
  echo "Minimum coverage must be numeric, got '${MIN_PERCENT}'" >&2
  exit 1
fi

awk -v min="${MIN_PERCENT}" '
  BEGIN { exclude = ENVIRON["EXCLUDE_REGEX"] }
  /^SF:/ { file = substr($0, 4); skip = (file ~ exclude) }
  /^LF:/ && !skip { found += substr($0, 4) }
  /^LH:/ && !skip { hit += substr($0, 4) }
  END {
    if (found == 0) { print "No coverable lines found"; exit 1 }
    percent = hit * 100 / found
    printf "Line coverage: %.2f%% (%d/%d), minimum %s%%\n", percent, hit, found, min
    if (percent + 0 < min + 0) { exit 1 }
  }
' "${LCOV_FILE}"
