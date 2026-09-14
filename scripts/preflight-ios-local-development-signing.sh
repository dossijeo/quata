#!/usr/bin/env bash
# Local Personal Team identity check only; never imports or exports private keys.
set -euo pipefail

if [[ "$(uname -s)" != Darwin ]]; then
  echo 'Local Apple Development preflight requires macOS.' >&2
  exit 2
fi

expected_identity=F8EF844BE964B1CE2B7132BBCDCD6509839B70FC
keychain="${HOME}/Library/Keychains/login.keychain-db"
if ! identities=$(security find-identity -v -p codesigning "$keychain" 2>/dev/null); then
  echo 'Unable to inspect the local signing keychain.' >&2
  exit 1
fi
matches=$(printf '%s\n' "$identities" | grep -Ec "^[[:space:]]*[0-9]+\) ${expected_identity} " || true)
unset identities
if [[ "$matches" != 1 ]]; then
  echo 'Expected Apple Development identity is not uniquely available as valid.' >&2
  exit 1
fi

echo 'Local Apple Development identity preflight: PASS (Personal Team 28X9AG24SW).'
echo 'Identity presence only: device provisioning, APNs, App Groups and distribution are not certified.'
