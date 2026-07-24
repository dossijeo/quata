package com.quata.core.preferences

import com.quata.core.model.AuthSession
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
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
        val data = session.toKeychainPayload().encodeToByteArray().toNsData()
        val update = mapOf<Any?, Any?>(kSecValueData to data)
        val updated = SecItemUpdate(baseQuery().asCfDictionary(), update.asCfDictionary())
        lastStatus = when (updated) {
            errSecSuccess -> null
            errSecItemNotFound -> {
                val added = SecItemAdd(
                    (baseQuery() + mapOf(
                        kSecValueData to data,
                        kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                    )).asCfDictionary(),
                    null,
                )
                added.takeUnless { it == errSecSuccess }
            }
            else -> updated
        }
    }

    override fun getSession(): AuthSession? = memScoped {
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(
            (baseQuery() + mapOf(
                kSecReturnData to kCFBooleanTrue,
                kSecMatchLimit to kSecMatchLimitOne,
            )).asCfDictionary(),
            result.ptr,
        )
        if (status != errSecSuccess) {
            lastStatus = status.takeUnless { it == errSecItemNotFound }
            return@memScoped null
        }
        val value = result.value ?: run {
            lastStatus = IosKeychainPayloadMissing
            return@memScoped null
        }
        try {
            val payload = (value as? NSData)?.toByteArray()?.decodeToString()
                ?: run {
                    lastStatus = IosKeychainPayloadInvalid
                    return@memScoped null
                }
            payload.toAuthSessionOrNull().also {
                lastStatus = if (it == null) IosKeychainPayloadInvalid else null
            }
        } finally {
            CFRelease(value)
        }
    }

    override fun clear() {
        val status = SecItemDelete(baseQuery().asCfDictionary())
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

@OptIn(ExperimentalForeignApi::class)
private fun Map<Any?, Any?>.asCfDictionary(): CFDictionaryRef = this as CFDictionaryRef

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNsData(): NSData = usePinned { pinned ->
    NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val bytes = ByteArray(length.toInt())
    bytes.usePinned { pinned -> getBytes(pinned.addressOf(0), length) }
    return bytes
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
