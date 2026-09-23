#!/usr/bin/env bash
set -euo pipefail

readonly LCOV_FILE="${1:?usage: check-lcov-coverage.sh <lcov.info> <min-percent> [exclude-regex]}"
readonly MIN_PERCENT="${2:?minimum line coverage percent is required}"
readonly EXCLUDE_REGEX="${3:-^$}"
readonly SOURCE_ROOT="${LCOV_SOURCE_ROOT:-.}"
readonly CONST_CONSTRUCTOR_REGEX='^ *const [A-Z][A-Za-z0-9_]*([.][A-Za-z0-9_]+)?[(].*[)]; *$'
export EXCLUDE_REGEX SOURCE_ROOT CONST_CONSTRUCTOR_REGEX

if [[ ! "${MIN_PERCENT}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
  echo "Minimum coverage must be numeric, got '${MIN_PERCENT}'" >&2
  exit 1
fi

awk -v min="${MIN_PERCENT}" '
  BEGIN {
    exclude = ENVIRON["EXCLUDE_REGEX"]
    root = ENVIRON["SOURCE_ROOT"]
    constructor = ENVIRON["CONST_CONSTRUCTOR_REGEX"]
  }
  function source_line(path, number,    text, current) {
    current = 0
    text = ""
    while ((getline text < path) > 0) {
      if (++current == number) { break }
    }
    close(path)
    return current == number ? text : ""
  }
  /^SF:/ { file = substr($0, 4); skip = (file ~ exclude) }
  /^DA:/ && !skip {
    line = substr($0, 4, index($0, ",") - 4) + 0
    found++
    if ($0 !~ /,0$/) { hit++; next }
    if (source_line(root "/" file, line) ~ constructor) { hit++; waived++; next }
    missed[++misses] = file ":" line
  }
  END {
    if (waived > 0) { printf "Const constructor declarations not reported by the VM: %d\n", waived }
    if (found == 0) { print "No coverable lines found"; exit 1 }
    percent = hit * 100 / found
    printf "Line coverage: %.2f%% (%d/%d), minimum %s%%\n", percent, hit, found, min
    if (percent + 0 < min + 0) {
      for (i = 1; i <= misses; i++) { print "Uncovered: " missed[i] }
      exit 1
    }
  }
' "${LCOV_FILE}"
