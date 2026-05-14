package com.quata.feature.chat.domain

class SosRateLimitException(val remainingMillis: Long) : IllegalStateException("SOS recently sent")
