package com.quata.feature.profile.domain

import platform.Foundation.NSString
import platform.Foundation.precomposedStringWithCompatibilityMapping

internal actual fun normalizeProfileCompatibility(value: String): String =
    (value as NSString).precomposedStringWithCompatibilityMapping
