# Community emoji atlas attribution

The eight `quata_community_emoji_atlas_*.png` resources are derived from the transparent
512 px PNG glyphs in [Google's noto-emoji repository](https://github.com/googlefonts/noto-emoji),
pinned to commit `8998f5dd683424a73e2314a8c1f1e359c19e8742`.

The upstream repository's primary [LICENSE](https://github.com/googlefonts/noto-emoji/blob/8998f5dd683424a73e2314a8c1f1e359c19e8742/LICENSE)
is the Apache License, Version 2.0. Noto's ordinary emoji glyphs use that source directly.
Its full distribution text is included in [Apache-2.0.txt](Apache-2.0.txt).
Noto does not publish regional-indicator country flags in its `png/512` directory; the flags
section therefore uses the repository's explicitly documented
[`third_party/region-flags/png`](https://github.com/googlefonts/noto-emoji/tree/8998f5dd683424a73e2314a8c1f1e359c19e8742/third_party/region-flags/png)
assets, whose upstream `README.third_party` identifies them as Public Domain. This is a pinned,
documented source rule, not a fallback font or invented glyph. The atlas layout changes only
transparent canvas placement and downscales each original glyph; it does not claim artwork as Quata-created.
The checked-in PNGs are deterministically reduced to a 64-colour indexed palette after placement;
they remain 72 px per atlas cell so no lower-resolution substitute is distributed.

Regenerate after an intentional catalog or source update with:

```powershell
python tools/generate_community_emoji_atlases.py
```

The generator uses the pinned tooling declared in `tools/requirements-community-emoji-atlas.txt`.

`--verify` is offline and validates the checked-in manifest, catalog correspondence and SHA-256
hashes. Gradle never downloads Noto artwork.
