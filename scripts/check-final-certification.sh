#!/usr/bin/env bash
set -euo pipefail

: "${EVENT_NAME:?EVENT_NAME is required}"
: "${FINAL_CANDIDATE:?FINAL_CANDIDATE is required}"
: "${DOCS_ONLY:=false}"

if [[ "$EVENT_NAME" == "pull_request" && "$DOCS_ONLY" == "true" ]]; then
  exit 0
fi

for expectation in "$@"; do
  IFS=: read -r lane affected result <<< "$expectation"
  if [[ "$affected" != "true" && "$affected" != "false" ]]; then
    echo "Final certification is invalid: '$lane' has affected='$affected'." >&2
    exit 1
  fi
  if [[ "$EVENT_NAME" == "pull_request" && "$FINAL_CANDIDATE" != "true" ]]; then
    if [[ "$result" != "skipped" ]]; then
      echo "Preparation pull request is invalid: '$lane' final job must be skipped before candidate-final, but was '$result'." >&2
      exit 1
    fi
    continue
  fi
  expected_result="skipped"
  [[ "$affected" == "true" ]] && expected_result="success"
  if [[ "$result" != "$expected_result" ]]; then
    echo "Final certification is incomplete: '$lane' expected '$expected_result' but was '$result'." >&2
    exit 1
  fi
done
