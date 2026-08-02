#!/usr/bin/env bash
set -euo pipefail

: "${EVENT_NAME:?EVENT_NAME is required}"
: "${FINAL_CANDIDATE:?FINAL_CANDIDATE is required}"

if [[ "$EVENT_NAME" == "pull_request" && "$FINAL_CANDIDATE" != "true" ]]; then
  echo "A pull request must carry candidate-final before final certification can pass." >&2
  exit 1
fi

for result in "$@"; do
  if [[ "$result" != "success" ]]; then
    echo "A required final job ended as: $result" >&2
    exit 1
  fi
done
