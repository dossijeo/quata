package com.quata.feature.chat.domain

const val CONTACT_DISCOVERY_BATCH_SIZE = 500

fun normalizeContactPhoneKey(value: String): String = value.filter(Char::isDigit)

fun prepareContactDiscoveryBatches(phoneCandidates: Collection<String>): List<List<String>> =
    phoneCandidates
        .asSequence()
        .map(::normalizeContactPhoneKey)
        .filter { it.length in 6..20 }
        .distinct()
        .chunked(CONTACT_DISCOVERY_BATCH_SIZE)
        .toList()
