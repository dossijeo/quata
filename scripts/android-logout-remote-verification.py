#!/usr/bin/env python3
import argparse
import hashlib
import json
import sys

import psycopg


def digest(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("--db-url-file", required=True)
    parser.add_argument("--db-ca-file", required=True)
    args = parser.parse_args()
    receipt = json.load(sys.stdin)
    required = ("profileId", "authUserId", "authSessionId", "pushToken")
    if any(not isinstance(receipt.get(key), str) or not receipt[key] for key in required):
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
                    where id=%s::uuid and user_id=%s::uuid
                  ) as exact_auth_session_revoked,
                  exists(
                    select 1 from public.push_tokens
                    where user_id=%s::uuid and auth_user_id=%s::uuid and token=%s
                      and platform='android' and disabled_at is not null
                      and last_error_text='Disabled on user logout'
                  ) as exact_push_token_disabled,
                  not exists(
                    select 1 from public.push_tokens
                    where user_id=%s::uuid and auth_user_id=%s::uuid and token=%s
                      and platform='android' and disabled_at is null
                  ) as no_active_exact_push_token
                """,
                (
                    receipt["authSessionId"],
                    receipt["authUserId"],
                    receipt["profileId"],
                    receipt["authUserId"],
                    receipt["pushToken"],
                    receipt["profileId"],
                    receipt["authUserId"],
                    receipt["pushToken"],
                ),
            )
            row = cursor.fetchone()
    result = {
        "status": "passed" if row and all(row) else "failed",
        "profileIdSha256": digest(receipt["profileId"]),
        "authSessionIdSha256": digest(receipt["authSessionId"]),
        "pushTokenSha256": digest(receipt["pushToken"]),
        "exactAuthSessionRevoked": bool(row and row[0]),
        "exactPushTokenDisabled": bool(row and row[1]),
        "noActiveExactPushToken": bool(row and row[2]),
    }
    sys.stdout.write(json.dumps(result, separators=(",", ":")) + "\n")
    return 0 if result["status"] == "passed" else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except SystemExit:
        raise
    except Exception:
        sys.stderr.write("android_logout_remote_verification_failed\n")
        raise SystemExit(1)
