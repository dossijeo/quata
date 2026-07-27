#!/bin/sh
set -eu

database_url="$(tr -d '\r\n' < /run/secrets/db-url)"

exec psql "$database_url" \
    -X \
    -v ON_ERROR_STOP=1 \
    -v "EXPECTED_ADMIN_SHA256=$EXPECTED_ADMIN_SHA256" \
    -v "EXPECTED_OFFICIAL_SHA256=$EXPECTED_OFFICIAL_SHA256" \
    -f /workspace/scripts/sql/community-profiles-rollout-preflight.sql
