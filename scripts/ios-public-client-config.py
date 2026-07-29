#!/usr/bin/env python3
"""Extract the two public iOS runtime values from exact Kotlin client declarations."""
from __future__ import annotations

import argparse
import pathlib
import re
import urllib.parse


def strip_kotlin_comments(source: str) -> str:
    """Remove line/block comments while preserving strings and line boundaries."""
    output: list[str] = []
    index = 0
    state = "code"
    while index < len(source):
        char = source[index]
        following = source[index + 1] if index + 1 < len(source) else ""
        if state == "code" and char == "/" and following == "/":
            output.extend((" ", " "))
            state = "line_comment"
            index += 2
            continue
        if state == "code" and char == "/" and following == "*":
            output.extend((" ", " "))
            state = "block_comment"
            index += 2
            continue
        if state == "line_comment":
            output.append("\n" if char == "\n" else " ")
            if char == "\n":
                state = "code"
            index += 1
            continue
        if state == "block_comment":
            if char == "*" and following == "/":
                output.extend((" ", " "))
                state = "code"
                index += 2
            else:
                output.append("\n" if char == "\n" else " ")
                index += 1
            continue
        output.append(char)
        if char == '"' and (index == 0 or source[index - 1] != "\\"):
            state = "string" if state == "code" else "code"
        index += 1
    if state == "block_comment":
        raise ValueError("unterminated Kotlin block comment")
    if state == "string":
        raise ValueError("unterminated Kotlin string")
    return "".join(output)


def exact_constant(source: str, name: str) -> str:
    uncommented = strip_kotlin_comments(source)
    declaration = re.compile(
        rf"^[ \t]*const[ \t]+val[ \t]+{re.escape(name)}"
        rf"(?:[ \t]*:[ \t]*String)?[ \t]*=[ \t]*\"([^\"\\\r\n]*)\"[ \t]*$",
        re.MULTILINE,
    )
    matches = declaration.findall(uncommented)
    if len(matches) != 1:
        raise ValueError(f"{name} must have exactly one uncommented, exact const val declaration")
    return matches[0]


def public_values(source: str) -> tuple[str, str]:
    url = exact_constant(source, "SUPABASE_URL")
    key = exact_constant(source, "SUPABASE_PUBLISHABLE_KEY")
    forbidden = re.compile(r"[\r\n$()\\;]")
    if forbidden.search(url) or forbidden.search(key):
        raise ValueError("public client values contain forbidden xcconfig syntax")
    parsed = urllib.parse.urlsplit(url)
    if (
        parsed.scheme != "https"
        or parsed.username is not None
        or parsed.password is not None
        or parsed.port not in (None, 443)
        or parsed.query
        or parsed.fragment
        or parsed.path not in ("", "/")
        or not parsed.hostname
        or re.fullmatch(r"[a-z0-9-]+\.supabase\.co", parsed.hostname) is None
    ):
        raise ValueError("public client URL must be a canonical Supabase HTTPS project URL")
    if (
        re.fullmatch(r"[A-Za-z0-9._-]{20,512}", key) is None
        or re.search(r"service[_-]?role|jwt", key, re.IGNORECASE)
    ):
        raise ValueError("client key is not an allowed publishable value")
    return url, key


def write_xcconfig(source_path: pathlib.Path, output_path: pathlib.Path) -> None:
    url, key = public_values(source_path.read_text(encoding="utf-8"))
    encoded_url = url.replace(
        "https://",
        "https:$(QUATA_XCCONFIG_SLASH)$(QUATA_XCCONFIG_SLASH)",
        1,
    )
    output_path.write_text(
        "// Generated transiently by run-ios-public-simulator-matrix.sh. Never commit.\n"
        f"QUATA_SUPABASE_URL = {encoded_url}\n"
        f"QUATA_SUPABASE_PUBLISHABLE_KEY = {key}\n",
        encoding="utf-8",
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=pathlib.Path, required=True)
    parser.add_argument("--output", type=pathlib.Path, required=True)
    args = parser.parse_args()
    write_xcconfig(args.source, args.output)


if __name__ == "__main__":
    main()
