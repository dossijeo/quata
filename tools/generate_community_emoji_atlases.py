#!/usr/bin/env python3
"""Generate the checked-in Noto Emoji atlas resources for the common emoji picker.

This tool is deliberately not part of Gradle: builds never download artwork.  It reads the
single product catalog in CommunityEmojiCatalog.kt, fetches the exact upstream PNGs pinned
below, and writes eight transparent 72 px-cell atlases plus a hash manifest.  A missing or
ambiguous upstream glyph is an error; do not replace it with a host-font glyph.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import sys
import tempfile
import urllib.error
import urllib.request
from pathlib import Path

from PIL import Image, __version__ as PILLOW_VERSION

ROOT = Path(__file__).resolve().parent.parent
CATALOG = ROOT / "designsystem/src/commonMain/kotlin/com/quata/core/ui/components/CommunityEmojiCatalog.kt"
OUTPUT = ROOT / "designsystem/src/commonMain/composeResources/drawable"
MANIFEST = ROOT / "tools/community_emoji_atlases.manifest.json"
NOTICE = ROOT / "docs/licenses/NotoEmojiAtlas-APACHE-2.0.md"
APACHE_LICENSE = ROOT / "docs/licenses/Apache-2.0.txt"
COMMIT = "8998f5dd683424a73e2314a8c1f1e359c19e8742"
BASE_URL = f"https://raw.githubusercontent.com/googlefonts/noto-emoji/{COMMIT}/png/512"
REGION_FLAG_BASE_URL = f"https://raw.githubusercontent.com/googlefonts/noto-emoji/{COMMIT}/third_party/region-flags/png"
CELL_PX = 72
COLUMNS = 6
SECTION_ORDER = ("recent", "frequent", "gestures", "people", "animals_nature", "food_drink", "objects_symbols", "flags")
FLAG_CODES = ("ES", "US", "GB", "FR", "DE", "IT", "PT", "BR", "AR", "CO", "MX", "EC", "PE", "CL", "UY", "PY", "BO", "VE", "DO", "CU", "MA", "DZ", "EG", "NG", "ZA", "CM", "GA", "GQ", "JP", "KR", "CN", "IN", "AU", "CA")


def quoted(value: str) -> list[str]:
    return re.findall(r'"([^\"]*)"', value)


def catalog_sections() -> dict[str, list[str]]:
    text = CATALOG.read_text(encoding="utf-8")
    frequent_match = re.search(r"private val frequentEmojis = listOf\((.*?)\)\s*\n", text, re.S)
    if not frequent_match:
        raise RuntimeError("Cannot locate frequentEmojis in CommunityEmojiCatalog.kt")
    frequent = quoted(frequent_match.group(1))
    result: dict[str, list[str]] = {"recent": frequent[:24], "frequent": frequent}
    for section in ("gestures", "people", "animals_nature", "food_drink", "objects_symbols"):
        match = re.search(rf'QuataEmojiSection\("{section}",.*?listOf\((.*?)\)\),', text, re.S)
        if not match:
            raise RuntimeError(f"Cannot locate {section} in CommunityEmojiCatalog.kt")
        result[section] = quoted(match.group(1))
    result["flags"] = [flag_emoji(code) for code in FLAG_CODES]
    if tuple(result) != SECTION_ORDER or sum(map(len, result.values())) != 338:
        raise RuntimeError(f"Unexpected catalog shape: {[len(result[key]) for key in SECTION_ORDER]}")
    return result


def flag_emoji(code: str) -> str:
    return "".join(chr(0x1F1E6 + ord(char) - ord("A")) for char in code)


def codepoint_filename(emoji: str) -> str:
    # Noto's PNG filenames intentionally omit U+FE0F variation selectors.  This is the only
    # normalization permitted: ZWJ, skin tone, keycap and regional-indicator code points stay.
    points = [f"{ord(char):x}" for char in emoji if ord(char) != 0xFE0F]
    return "emoji_u" + "_".join(points) + ".png"


def regional_flag_code(emoji: str) -> str | None:
    points = [ord(char) for char in emoji]
    if len(points) != 2 or not all(0x1F1E6 <= point <= 0x1F1FF for point in points):
        return None
    return "".join(chr(ord("A") + point - 0x1F1E6) for point in points)


def download_png(emoji: str, cache: Path) -> Path:
    country_code = regional_flag_code(emoji)
    filename = f"flag_{country_code}.png" if country_code else codepoint_filename(emoji)
    url = f"{REGION_FLAG_BASE_URL}/{country_code}.png" if country_code else f"{BASE_URL}/{filename}"
    destination = cache / filename
    if destination.exists():
        return destination
    try:
        with urllib.request.urlopen(url, timeout=30) as response:
            payload = response.read()
    except urllib.error.HTTPError as error:
        raise RuntimeError(f"Noto PNG missing for {emoji!r}: {url} ({error.code})") from error
    if not payload.startswith(b"\x89PNG\r\n\x1a\n"):
        raise RuntimeError(f"Noto response was not a PNG for {emoji!r}: {filename}")
    destination.write_bytes(payload)
    return destination


def make_atlas(emojis: list[str], destination: Path, cache: Path) -> dict:
    rows = math.ceil(len(emojis) / COLUMNS)
    atlas = Image.new("RGBA", (COLUMNS * CELL_PX, rows * CELL_PX), (0, 0, 0, 0))
    for index, emoji in enumerate(emojis):
        with Image.open(download_png(emoji, cache)) as source:
            glyph = source.convert("RGBA")
            glyph.thumbnail((CELL_PX, CELL_PX), Image.Resampling.LANCZOS)
            x = (index % COLUMNS) * CELL_PX + (CELL_PX - glyph.width) // 2
            y = (index // COLUMNS) * CELL_PX + (CELL_PX - glyph.height) // 2
            atlas.alpha_composite(glyph, (x, y))
    atlas.save(destination, format="PNG", optimize=True)
    return {
        "file": destination.name,
        "sha256": hashlib.sha256(destination.read_bytes()).hexdigest(),
        "bytes": destination.stat().st_size,
        "columns": COLUMNS,
        "rows": rows,
        "cellPx": CELL_PX,
        "emojis": emojis,
    }


def expected_files() -> list[Path]:
    return [OUTPUT / f"quata_community_emoji_atlas_{key}.png" for key in SECTION_ORDER]


def verify() -> None:
    if not MANIFEST.exists():
        raise RuntimeError(f"Missing manifest: {MANIFEST}")
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    if manifest.get("notoEmojiCommit") != COMMIT or not str(manifest.get("license", "")).startswith("Apache-2.0"):
        raise RuntimeError("Manifest does not pin the expected Noto Emoji source and license")
    sections = catalog_sections()
    if list(manifest.get("sections", {})) != list(SECTION_ORDER):
        raise RuntimeError("Manifest section order differs from the product catalog")
    for key, emojis in sections.items():
        entry = manifest["sections"][key]
        path = OUTPUT / entry["file"]
        if entry.get("emojis") != emojis or not path.exists() or path.stat().st_size == 0:
            raise RuntimeError(f"Invalid atlas manifest entry for {key}")
        if hashlib.sha256(path.read_bytes()).hexdigest() != entry.get("sha256"):
            raise RuntimeError(f"Hash mismatch for {path.name}; regenerate the atlas")
    if not NOTICE.exists() or not APACHE_LICENSE.exists() or "Apache License" not in APACHE_LICENSE.read_text(encoding="utf-8"):
        raise RuntimeError("Missing Apache-2.0 attribution notice")
    print("Community emoji atlas manifest verified (338 catalog entries, 8 PNGs).")


def generate() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    sections = catalog_sections()
    with tempfile.TemporaryDirectory(prefix="quata-noto-emoji-") as temporary:
        cache = Path(temporary)
        entries = {}
        for key in SECTION_ORDER:
            destination = OUTPUT / f"quata_community_emoji_atlas_{key}.png"
            entries[key] = make_atlas(sections[key], destination, cache)
    manifest = {
        "schemaVersion": 1,
        "source": "https://github.com/googlefonts/noto-emoji",
        "notoEmojiCommit": COMMIT,
        "license": "Apache-2.0 (Noto glyphs); Public Domain (upstream region flag assets)",
        "cellPx": CELL_PX,
        "columns": COLUMNS,
        "generator": {"python": f"{sys.version_info.major}.{sys.version_info.minor}", "pillow": PILLOW_VERSION},
        "sections": entries,
    }
    MANIFEST.write_text(json.dumps(manifest, ensure_ascii=True, indent=2) + "\n", encoding="utf-8")
    verify()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--verify", action="store_true", help="verify checked-in PNGs and manifest without network")
    args = parser.parse_args()
    if args.verify:
        verify()
    else:
        generate()
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"error: {error}", file=sys.stderr)
        raise SystemExit(1)
