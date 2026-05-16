package com.quata.bettermessages

import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

internal fun String.toRequestBodyCompat(): RequestBody = this.toRequestBody(JSON_MEDIA_TYPE)
internal fun ByteArray.toRequestBodyCompat(): RequestBody = this.toRequestBody(null)
