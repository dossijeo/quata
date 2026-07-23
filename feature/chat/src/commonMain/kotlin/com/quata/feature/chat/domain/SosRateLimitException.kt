package com.quata.feature.chat.domain

/** Shared SOS rate-limit failure. */

class SosRateLimitException(val remainingMillis: Long) : IllegalStateException("SOS recently sent")
