package com.quata.core.preferences

import com.quata.core.model.AuthSession
import kotlinx.cinterop.COpaque
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Foundation.NSData
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Keychain-backed [SessionStorage] for an iOS authenticated composition root.
 *
 * Values are stored as one versioned opaque Keychain item, never in NSUserDefaults. The service
 * and account are constructor parameters so different app targets/app groups can choose their own
 * namespace without shipping tokens or backend configuration in this adapter.
 */
@OptIn(ExperimentalForeignApi::class)
class IosKeychainSessionStorage(
    private val service: String = "com.quata.auth-session",
    private val account: String = "current-user",
) : SessionStorage {
    /** Diagnostic only: `SessionStorage` cannot return failures, so callers can inspect this safely. */
    var lastStatus: Int? = null
        private set

    override fun saveSession(session: AuthSession) {
        val data = session.toKeychainPayload().toKeychainData()
        try {
            lastStatus = withBaseQuery { query ->
                withKeychainDictionary(listOf(keychainEntry(kSecValueData, data))) { attributes ->
                    when (val updated = SecItemUpdate(query, attributes)) {
                        errSecSuccess -> null
                        errSecItemNotFound -> withBaseQuery(
                            listOf(
                                keychainEntry(kSecValueData, data),
                                keychainEntry(
                                    kSecAttrAccessible,
                                    kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                                ),
                            ),
                        ) { addAttributes ->
                            SecItemAdd(addAttributes, null).takeUnless { it == errSecSuccess }
                        }
                        else -> updated
                    }
                }
            }
        } finally {
            // CFDataCreate follows Create Rule; Security has synchronously consumed it now.
            CFRelease(data)
        }
    }

    override fun getSession(): AuthSession? = memScoped {
        val result = alloc<ObjCObjectVar<Any?>>()
        val status = withBaseQuery(
            listOf(
                keychainEntry(kSecReturnData, kCFBooleanTrue),
                keychainEntry(kSecMatchLimit, kSecMatchLimitOne),
            ),
        ) { query ->
            SecItemCopyMatching(query, result.ptr.reinterpret())
        }
        if (status != errSecSuccess) {
            lastStatus = status.takeUnless { it == errSecItemNotFound }
            return@memScoped null
        }
        val value = result.value ?: run {
            lastStatus = IosKeychainPayloadMissing
            return@memScoped null
        }
        val payload = (value as? NSData)?.toByteArray()?.decodeToString()
            ?: run {
                lastStatus = IosKeychainPayloadInvalid
                return@memScoped null
            }
        return@memScoped payload.toAuthSessionOrNull().also {
            lastStatus = if (it == null) IosKeychainPayloadInvalid else null
        }
    }

    override fun clear() {
        val status = withBaseQuery { query ->
            SecItemDelete(query)
        }
        lastStatus = when (status) {
            errSecSuccess, errSecItemNotFound -> null
            else -> status
        }
    }

    private inline fun <T> withBaseQuery(
        extraEntries: List<KeychainDictionaryEntry> = emptyList(),
        block: (CFDictionaryRef) -> T,
    ): T = memScoped {
        val serviceRef = service.toKeychainString()
        val accountRef = account.toKeychainString()
        try {
            withKeychainDictionary(
                listOf(
                    keychainEntry(kSecClass, kSecClassGenericPassword),
                    keychainEntry(kSecAttrService, serviceRef),
                    keychainEntry(kSecAttrAccount, accountRef),
                ) + extraEntries,
                block,
            )
        } finally {
            CFRelease(serviceRef)
            CFRelease(accountRef)
        }
    }
}

/**
 * Creates a real Core Foundation dictionary for exactly one synchronous Security call.
 *
 * A Kotlin [Map] is not a `CFDictionaryRef`: casting it to a native pointer leaves Security
 * dereferencing Kotlin heap memory. This uses `CFDictionaryCreateMutable` and inserts only Core
 * Foundation objects through `CFDictionaryAddValue`. The dictionary has no callbacks because all
 * inserted objects are held explicitly until the synchronous Security call has returned.
 */
@OptIn(ExperimentalForeignApi::class)
private inline fun <T> withKeychainDictionary(
    entries: List<KeychainDictionaryEntry>,
    block: (CFDictionaryRef) -> T,
): T {
    val dictionary = CFDictionaryCreateMutable(null, entries.size.toLong(), null, null)
        ?: error("Core Foundation could not create a Keychain dictionary")
    return try {
        entries.forEach { entry ->
            CFDictionaryAddValue(dictionary, entry.key, entry.value)
        }
        block(dictionary)
    } finally {
        CFRelease(dictionary)
    }
}

/**
 * `CFDictionaryAddValue` accepts opaque CF object pointers. Security constants, CFString and
 * CFData all satisfy that contract; Kotlin objects never cross this boundary.
 */
@OptIn(ExperimentalForeignApi::class)
private data class KeychainDictionaryEntry(
    val key: CPointer<COpaque>,
    val value: CPointer<COpaque>,
)

@OptIn(ExperimentalForeignApi::class)
private fun keychainEntry(key: Any?, value: Any?): KeychainDictionaryEntry = KeychainDictionaryEntry(
    key = key.toKeychainPointer(),
    value = value.toKeychainPointer(),
)

@OptIn(ExperimentalForeignApi::class)
private fun Any?.toKeychainPointer(): CPointer<COpaque> = (this as? CPointer<*>)
    ?.reinterpret()
    ?: error("Keychain attributes must be Core Foundation objects")

@OptIn(ExperimentalForeignApi::class)
private fun String.toKeychainString(): CFStringRef =
    CFStringCreateWithCString(
        null,
        this,
        kCFStringEncodingUTF8,
    ) ?: error("Core Foundation could not create a Keychain string")

@OptIn(ExperimentalForeignApi::class)
private fun String.toKeychainData(): CFDataRef {
    val payload = encodeToByteArray()
    return payload.usePinned { pinned ->
        CFDataCreate(null, pinned.addressOf(0).reinterpret(), payload.size.toLong())!!
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    if (length == 0uL) return ByteArray(0)
    return bytes?.readBytes(length.toInt()) ?: ByteArray(0)
}

/** Stable, versioned and delimiter-safe representation of every [AuthSession] field. */
private fun AuthSession.toKeychainPayload(): String = listOf(
    IosKeychainPayloadVersion,
    token.toHexUtf8(),
    userId.toHexUtf8(),
    email.toHexUtf8(),
    displayName.toHexUtf8(),
    authUserId.toOptionalHexUtf8(),
    accessToken.toOptionalHexUtf8(),
    refreshToken.toOptionalHexUtf8(),
    expiresAt?.toString() ?: IosKeychainNull,
    isOfficial.toString(),
).joinToString(separator = "\n")

private fun String.toAuthSessionOrNull(): AuthSession? = runCatching {
    val fields = split('\n')
    require(fields.size in IosKeychainPayloadLegacyFieldCount..IosKeychainPayloadFieldCount && fields.first() == IosKeychainPayloadVersion)
    AuthSession(
        token = fields[1].fromHexUtf8(),
        userId = fields[2].fromHexUtf8(),
        email = fields[3].fromHexUtf8(),
        displayName = fields[4].fromHexUtf8(),
        authUserId = fields[5].fromOptionalHexUtf8(),
        accessToken = fields[6].fromOptionalHexUtf8(),
        refreshToken = fields[7].fromOptionalHexUtf8(),
        expiresAt = fields[8].takeUnless { it == IosKeychainNull }?.toLong(),
        isOfficial = fields.getOrNull(9)?.toBooleanStrictOrNull() == true,
    )
}.getOrNull()

private fun String.toHexUtf8(): String = encodeToByteArray().joinToString(separator = "") { byte ->
    (byte.toInt() and 0xff).toString(16).padStart(2, '0')
}

private fun String?.toOptionalHexUtf8(): String = this?.toHexUtf8() ?: IosKeychainNull

private fun String.fromHexUtf8(): String {
    require(length % 2 == 0)
    return ByteArray(length / 2) { index ->
        substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }.decodeToString()
}

private fun String.fromOptionalHexUtf8(): String? = takeUnless { it == IosKeychainNull }?.fromHexUtf8()

private const val IosKeychainPayloadVersion = "quata-auth-session-v1"
private const val IosKeychainPayloadLegacyFieldCount = 9
private const val IosKeychainPayloadFieldCount = 10
private const val IosKeychainNull = "~"
private const val IosKeychainPayloadMissing = -1_001
private const val IosKeychainPayloadInvalid = -1_002
