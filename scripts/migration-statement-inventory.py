#!/usr/bin/env python3
"""Inventory PostgreSQL statements without executing SQL or asserting deployment history.

Requires pglast. Output is preparation for semantic review, never release authorization.
"""
import argparse
import hashlib
import json
from pathlib import Path

import pglast
from pglast import parser


def inventory_sql(source: str) -> list[dict]:
    encoded = source.encode("utf-8")
    parsed = json.loads(parser.parse_sql_json(source))
    statements = []
    for ordinal, item in enumerate(parsed["stmts"], 1):
        kind, ast = next(iter(item["stmt"].items()))
        start = item.get("stmt_location", 0)
        length = item.get("stmt_len", 0)
        end = start + length if length else len(encoded)
        statements.append({
            "ordinal": ordinal,
            "kind": kind,
            "startByte": start,
            "endByte": end,
            "line": encoded[:start].count(b"\n") + 1,
            "sha256": hashlib.sha256(encoded[start:end]).hexdigest(),
            "reviewNeeds": (
                ["procedural effects must be inspected"] if kind == "DoStmt" else
                ["data effects must be inspected"] if kind in {
                    "InsertStmt", "UpdateStmt", "DeleteStmt", "MergeStmt", "TruncateStmt", "CopyStmt"
                } else
                ["expressions and called functions must be inspected"] if kind == "SelectStmt" else
                ["function body, signature, attributes and grants must be compared"] if kind == "CreateFunctionStmt" else
                ["statement effects must be compared with current catalog and later migrations"]
            ),
            "ast": ast,
        })
    return statements


def inventory_directory(directory: Path) -> dict:
    files = []
    for path in sorted(directory.glob("*.sql")):
        data = path.read_bytes()
        files.append({
            "file": path.name,
            "sha256": hashlib.sha256(data).hexdigest(),
            "offsetBasis": "UTF-8 after optional BOM removal",
            "statements": inventory_sql(data.decode("utf-8-sig")),
        })
    if not files:
        raise ValueError("No migration SQL files found")
    return {"schemaVersion": 1, "pglastVersion": pglast.__version__,
            "postgresParserVersion": json.loads(parser.parse_sql_json(""))["version"],
            "deploymentHistoryProven": False,
            "semanticEquivalenceProven": False, "files": files}


def main():
    args = argparse.ArgumentParser(description=__doc__)
    args.add_argument("--migrations", type=Path, required=True)
    args.add_argument("--out", type=Path, required=True)
    options = args.parse_args()
    report = inventory_directory(options.migrations)
    options.out.parent.mkdir(parents=True, exist_ok=True)
    # Preserve previous audit artifacts; a new observation gets a new output path.
    with options.out.open("x", encoding="utf-8") as output:
        json.dump(report, output, indent=2, ensure_ascii=False)
    print(json.dumps({"files": len(report["files"]),
                      "statements": sum(len(f["statements"]) for f in report["files"]),
                      "deploymentHistoryProven": False, "semanticEquivalenceProven": False}))


if __name__ == "__main__":
    main()
