package com.quata.feature.profile.domain

import java.text.Normalizer

internal actual fun normalizeProfileCompatibility(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFKC)
