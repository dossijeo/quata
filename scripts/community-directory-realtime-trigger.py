#!/usr/bin/env python3
"""Create one owned wall fixture, then always remove that exact fixture."""

import argparse
import json
import queue
import sys
import threading
import uuid
from pathlib import Path

import psycopg


def wait_for_restore(timeout_seconds: float) -> None:
    received: queue.Queue[str] = queue.Queue(maxsize=1)

    def read_line() -> None:
        received.put(sys.stdin.readline())

    thread = threading.Thread(target=read_line, daemon=True)
    thread.start()
    try:
        received.get(timeout=timeout_seconds)
    except queue.Empty:
        pass


def write_journal(path: Path, payload: dict[str, str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    temporary.replace(path)


def remove_owned_fixture(connection: psycopg.Connection, journal_path: Path, fixture: dict[str, str]) -> None:
    prefix = "quata-community-directory-realtime-"
    uuid.UUID(fixture["id"])
    if not fixture["slug"].startswith(prefix) or fixture["name"] != fixture["slug"]:
        raise RuntimeError("community_realtime_probe_journal_invalid")
    connection.rollback()
    with connection.cursor() as cursor:
        cursor.execute(
            "delete from public.community_walls where id = %s and slug = %s and name = %s",
            (fixture["id"], fixture["slug"], fixture["name"]),
        )
    connection.commit()
    with connection.cursor() as cursor:
        cursor.execute("select count(*) from public.community_walls where id = %s", (fixture["id"],))
        remaining = cursor.fetchone()
    if remaining != (0,):
        raise RuntimeError("community_realtime_probe_cleanup_conflict")
    journal_path.unlink(missing_ok=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--db-url-file", required=True)
    parser.add_argument("--journal", required=True)
    parser.add_argument("--restore-timeout-seconds", type=float, default=30.0)
    args = parser.parse_args()

    dsn = Path(args.db_url_file).read_text(encoding="utf-8").strip()
    journal_path = Path(args.journal)
    fixture_id = str(uuid.uuid4())
    marker = f"quata-community-directory-realtime-{fixture_id}"
    fixture = {
        "id": fixture_id,
        "slug": marker,
        "name": marker,
        "description": marker,
    }

    with psycopg.connect(dsn) as connection:
        if journal_path.exists():
            previous = json.loads(journal_path.read_text(encoding="utf-8"))
            remove_owned_fixture(connection, journal_path, previous)
        write_journal(journal_path, fixture)
        try:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    insert into public.community_walls (id, slug, name, description, sort_order, is_active)
                    values (%s, %s, %s, %s, 2147483647, true)
                    returning id
                    """,
                    (fixture["id"], fixture["slug"], fixture["name"], fixture["description"]),
                )
                row = cursor.fetchone()
                if row != (uuid.UUID(fixture["id"]),):
                    raise RuntimeError("community_realtime_probe_insert_failed")
            connection.commit()
            print(json.dumps({"phase": "inserted", "table": "community_walls", "id": fixture["id"]}), flush=True)
            wait_for_restore(args.restore_timeout_seconds)
        finally:
            remove_owned_fixture(connection, journal_path, fixture)
            print(json.dumps({"phase": "removed", "ok": True}), flush=True)


if __name__ == "__main__":
    main()
