# Noto Color Emoji feed assets

`designsystem/src/commonMain/composeResources/font/quata_header_logo_q_subset.ttf` is a
10,820-byte Roboto Flex subset instantiated at weight 900, containing U+0051 and U+0308 only.

Attribution: Noto Color Emoji, Google LLC and the Noto Project Authors.
Attribution: Roboto Flex, Google LLC and the Roboto Project Authors.

Licensed under the [SIL Open Font License, Version 1.1](https://openfontlicense.org/open-font-license-official-text/).
The font has not been renamed or presented as an original Quata typeface; it is a deterministic
resource subset for Compose rendering.
The five `quata_feed_emoji_*.png` assets are unmodified, transparent 512 px PNG glyphs
from Noto Color Emoji. They are pinned to Google Fonts' `noto-emoji` commit
`8998f5dd683424a73e2314a8c1f1e359c19e8742` and can be regenerated with
`powershell -ExecutionPolicy Bypass -File tools/generate_feed_emoji_assets.ps1`.
