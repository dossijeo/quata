@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.feature.profile.domain

internal actual fun normalizeProfileCompatibility(value: String): String =
    js("value.normalize('NFKC')")
