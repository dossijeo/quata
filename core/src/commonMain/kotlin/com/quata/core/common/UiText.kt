package com.quata.core.common

sealed class UiText {
    data class DynamicString(val value: String) : UiText()
}
