#!/usr/bin/env python3
import argparse
import hashlib
import json
import re
import sys
import urllib.error
import urllib.request

import psycopg


def digest(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def public_config(path: str) -> tuple[str, str]:
    with open(path, "r", encoding="utf-8") as handle:
        source = handle.read()
    url = re.search(r'SUPABASE_URL\s*=\s*"([^"]+)"', source)
    key = re.search(r'SUPABASE_PUBLISHABLE_KEY\s*=\s*"([^"]+)"', source)
    if not url or not key:
        raise RuntimeError("public_backend_configuration_missing")
    return url.group(1).rstrip("/"), key.group(1)


def refresh_rejected(base_url: str, publishable_key: str, refresh_token: str) -> bool:
    request = urllib.request.Request(
        f"{base_url}/auth/v1/token?grant_type=refresh_token",
        data=json.dumps({"refresh_token": refresh_token}).encode("utf-8"),
        method="POST",
        headers={
            "apikey": publishable_key,
            "content-type": "application/json",
            "x-client-info": "quata-auth-global-logout-verification",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            return response.status < 200 or response.status >= 300
    except urllib.error.HTTPError as error:
        return 400 <= error.code < 500


def main() -> int:
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("--db-url-file", required=True)
    parser.add_argument("--db-ca-file", required=True)
    parser.add_argument(
        "--public-config-file",
        default="core/src/commonMain/kotlin/com/quata/core/config/QuataPublicBackendConfig.kt",
    )
    args = parser.parse_args()
    receipt = json.load(sys.stdin)
    auth_user_id = receipt.get("authUserId")
    session_ids = receipt.get("authSessionIds")
    refresh_tokens = receipt.get("refreshTokens")
    if (
        not isinstance(auth_user_id, str)
        or not auth_user_id
        or not isinstance(session_ids, list)
        or len(session_ids) != 2
        or any(not isinstance(value, str) or not value for value in session_ids)
        or len(set(session_ids)) != 2
        or not isinstance(refresh_tokens, list)
        or len(refresh_tokens) != 2
        or any(not isinstance(value, str) or not value for value in refresh_tokens)
    ):
        raise RuntimeError("private_receipt_invalid")

    with open(args.db_url_file, "r", encoding="utf-8") as handle:
        connection_string = handle.read().strip()
    with psycopg.connect(
        connection_string,
        sslmode="verify-full",
        sslrootcert=args.db_ca_file,
        connect_timeout=5,
        options="-c statement_timeout=5000",
    ) as connection:
        with connection.cursor() as cursor:
            cursor.execute(
                """
                select
                  not exists(
                    select 1 from auth.sessions
                    where user_id=%s::uuid and id=any(%s::uuid[])
                  ) as owned_auth_sessions_revoked,
                  not exists(
                    select 1 from auth.refresh_tokens
                    where session_id=any(%s::uuid[]) and revoked is not true
                  ) as owned_refresh_tokens_revoked,
                  not exists(
                    select 1 from public.push_tokens
                    where auth_user_id=%s::uuid and disabled_at is null
                  ) and not exists(
                    select 1 from public.web_push_subscriptions
                    where auth_user_id=%s::uuid and disabled_at is null
                  ) and not exists(
                    select 1 from public.web_client_sessions
                    where auth_user_id=%s::uuid and revoked_at is null
                  ) as all_device_endpoints_retired
                """,
                (auth_user_id, session_ids, session_ids, auth_user_id, auth_user_id, auth_user_id),
            )
            row = cursor.fetchone()

    base_url, publishable_key = public_config(args.public_config_file)
    rejected = sum(refresh_rejected(base_url, publishable_key, token) for token in refresh_tokens)
    passed = bool(row and all(row) and rejected == 2)
    result = {
        "status": "passed" if passed else "failed",
        "authUserIdSha256": digest(auth_user_id),
        "authSessionIdSha256": [digest(value) for value in session_ids],
        "ownedAuthSessionsRevoked": bool(row and row[0]),
        "ownedRefreshTokensRevoked": bool(row and row[1]),
        "allDeviceEndpointsRetired": bool(row and row[2]),
        "refreshTokensRejected": rejected,
    }
    sys.stdout.write(json.dumps(result, separators=(",", ":")) + "\n")
    return 0 if passed else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except SystemExit:
        raise
    except Exception:
        sys.stderr.write("auth_global_logout_remote_verification_failed\n")
        raise SystemExit(1)
