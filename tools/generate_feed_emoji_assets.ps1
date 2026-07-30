$ErrorActionPreference = 'Stop'

# Noto Color Emoji, version 2.051 (the source used by quata_feed_emoji_subset.ttf).
# Pinned to a Git commit so the checked-in raster assets can be reproduced exactly.
$notoEmojiCommit = '8998f5dd683424a73e2314a8c1f1e359c19e8742'
$assets = [ordered]@{
    sos = '1f6a8'
    rank = '1f525'
    location = '1f4cd'
    note = '1f4dd'
    document = '1f4c4'
}
$outputDirectory = Join-Path $PSScriptRoot '..\designsystem\src\commonMain\composeResources\drawable'
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null

foreach ($asset in $assets.GetEnumerator()) {
    $source = "https://raw.githubusercontent.com/googlefonts/noto-emoji/$notoEmojiCommit/png/512/emoji_u$($asset.Value).png"
    Invoke-WebRequest -Uri $source -OutFile (Join-Path $outputDirectory "quata_feed_emoji_$($asset.Key).png")
}
