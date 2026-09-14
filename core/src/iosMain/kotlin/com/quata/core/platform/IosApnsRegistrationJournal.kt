package com.quata.core.platform

import com.quata.core.preferences.*
import kotlinx.cinterop.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.CoreFoundation.*
import platform.Foundation.NSData
import platform.Security.*

@Serializable
private data class ApnsJournalSnapshot(val version: Int = 1, val records: List<ApnsPendingRegistration>)

/** Separate, device-only Keychain namespace; contains cleanup metadata, never bearer/refresh tokens. */
@OptIn(ExperimentalForeignApi::class)
class IosApnsRegistrationJournal(private val backendUrl: String) : ApnsRegistrationJournal {
    override fun read(): List<ApnsPendingRegistration> = memScoped {
        val result = alloc<ObjCObjectVar<Any?>>()
        val status = withQuery(listOf(
            keychainEntry(kSecReturnData, kCFBooleanTrue), keychainEntry(kSecMatchLimit, kSecMatchLimitOne),
        )) { SecItemCopyMatching(it, result.ptr.reinterpret()) }
        if (status == errSecItemNotFound) return@memScoped emptyList()
        check(status == errSecSuccess) { "apns_journal_read_failed" }
        val data = result.value as? NSData ?: error("apns_journal_invalid")
        val bytes = data.bytes?.readBytes(data.length.toInt()) ?: error("apns_journal_invalid")
        val snapshot = try { Json.decodeFromString<ApnsJournalSnapshot>(bytes.decodeToString()) }
        catch (_: Exception) { error("apns_journal_invalid") }
        check(snapshot.version == 1 && snapshot.records.all {
            it.profileId.isNotBlank() && it.token.isNotBlank() &&
                it.token == it.token.trim().lowercase() &&
                ApnsEnvironment.entries.any { environment -> environment.wireValue == it.environment }
        }) { "apns_journal_invalid" }
        snapshot.records
    }

    override fun write(records: List<ApnsPendingRegistration>): Boolean {
        val data = Json.encodeToString(ApnsJournalSnapshot(records = records)).toKeychainData()
        return try {
            val status = withQuery { query ->
                withKeychainDictionary(listOf(keychainEntry(kSecValueData, data))) { attributes ->
                    when (val updated = SecItemUpdate(query, attributes)) {
                        errSecItemNotFound -> withQuery(listOf(
                            keychainEntry(kSecValueData, data),
                            keychainEntry(kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly),
                        )) { SecItemAdd(it, null) }
                        else -> updated
                    }
                }
            }
            status == errSecSuccess && read() == records
        } catch (_: Exception) { false } finally { CFRelease(data) }
    }

    private inline fun <T> withQuery(
        extra: List<KeychainDictionaryEntry> = emptyList(), block: (CFDictionaryRef) -> T,
    ): T {
        val service = "com.quata.apns-cleanup:${backendUrl.trim().trimEnd('/')}".toKeychainString()
        val account = "registrations-v1".toKeychainString()
        return try {
            withKeychainDictionary(listOf(
                keychainEntry(kSecClass, kSecClassGenericPassword),
                keychainEntry(kSecAttrService, service), keychainEntry(kSecAttrAccount, account),
            ) + extra, block)
        } finally { CFRelease(service); CFRelease(account) }
    }
}
