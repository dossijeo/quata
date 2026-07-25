package com.quata.core.preferences

import com.quata.core.model.AuthSession
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
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFRetain
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSMutableDictionary
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
import platform.darwin.NSCopyingProtocol

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
        val update = mapOf<Any?, Any?>(kSecValueData to data)
        lastStatus = withKeychainDictionary(baseQuery()) { query ->
            withKeychainDictionary(update) { attributes ->
                when (val updated = SecItemUpdate(query, attributes)) {
                    errSecSuccess -> null
                    errSecItemNotFound -> withKeychainDictionary(
                        baseQuery() + mapOf(
                            kSecValueData to data,
                            kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                        ),
                    ) { addAttributes ->
                        SecItemAdd(addAttributes, null).takeUnless { it == errSecSuccess }
                    }
                    else -> updated
                }
            }
        }
    }

    override fun getSession(): AuthSession? = memScoped {
        val result = alloc<ObjCObjectVar<Any?>>()
        val status = withKeychainDictionary(
            baseQuery() + mapOf(
                kSecReturnData to kCFBooleanTrue,
                kSecMatchLimit to kSecMatchLimitOne,
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
        val status = withKeychainDictionary(baseQuery()) { query ->
            SecItemDelete(query)
        }
        lastStatus = when (status) {
            errSecSuccess, errSecItemNotFound -> null
            else -> status
        }
    }

    private fun baseQuery(): Map<Any?, Any?> = mapOf(
        kSecClass to kSecClassGenericPassword,
        kSecAttrService to service,
        kSecAttrAccount to account,
    )
}

/**
 * Creates a real Foundation dictionary, retains its Core Foundation view for exactly one Security
 * call, and releases that retain afterwards.
 *
 * A Kotlin [Map] is not a `CFDictionaryRef`: casting it to a native pointer happens to compile but
 * leaves Security dereferencing Kotlin heap memory. `NSDictionary` is toll-free bridged with
 * `CFDictionary`, so `CFBridgingRetain` gives the C API an owned, valid dictionary for the whole
 * call. The `finally` is essential because `CFBridgingRetain` transfers a +1 retain to us.
 */
@OptIn(ExperimentalForeignApi::class)
private inline fun <T> withKeychainDictionary(
    entries: Map<Any?, Any?>,
    block: (CFDictionaryRef) -> T,
): T {
    val dictionary = NSMutableDictionary()
    entries.forEach { (key, value) ->
        dictionary.setObject(
            value.toFoundationKeychainObject(),
            key.toFoundationKeychainObject() as NSCopyingProtocol,
        )
    }
    val retained = CFBridgingRetain(dictionary) as? CFDictionaryRef
        ?: error("Foundation dictionary could not bridge to Core Foundation")
    return try {
        block(retained)
    } finally {
        CFRelease(retained)
    }
}

/**
 * Security's constants are Core Foundation pointers. Retain each one and hand that retain to ARC
 * through `CFBridgingRelease`, so Foundation receives its real toll-free bridged object instead of
 * a Kotlin `CPointer` wrapper. Non-CF values (String and NSData) already bridge normally.
 */
@OptIn(ExperimentalForeignApi::class)
private fun Any?.toFoundationKeychainObject(): Any = when (this) {
    null -> error("Keychain dictionaries cannot contain null keys or values")
    is CPointer<*> -> CFBridgingRelease(CFRetain(this as CFTypeRef))
        ?: error("Core Foundation keychain value could not bridge to Foundation")
    else -> this
}

@OptIn(ExperimentalForeignApi::class)
private fun String.toKeychainData(): NSData {
    val payload = encodeToByteArray()
    return payload.usePinned { pinned ->
        // This is the Foundation bridge shape used by the other iOS transports in this project.
        CFDataCreate(null, pinned.addressOf(0).reinterpret(), payload.size.toLong())!! as NSData
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
).joinToString(separator = "\n")

private fun String.toAuthSessionOrNull(): AuthSession? = runCatching {
    val fields = split('\n')
    require(fields.size == IosKeychainPayloadFieldCount && fields.first() == IosKeychainPayloadVersion)
    AuthSession(
        token = fields[1].fromHexUtf8(),
        userId = fields[2].fromHexUtf8(),
        email = fields[3].fromHexUtf8(),
        displayName = fields[4].fromHexUtf8(),
        authUserId = fields[5].fromOptionalHexUtf8(),
        accessToken = fields[6].fromOptionalHexUtf8(),
        refreshToken = fields[7].fromOptionalHexUtf8(),
        expiresAt = fields[8].takeUnless { it == IosKeychainNull }?.toLong(),
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
private const val IosKeychainPayloadFieldCount = 9
private const val IosKeychainNull = "~"
private const val IosKeychainPayloadMissing = -1_001
private const val IosKeychainPayloadInvalid = -1_002
