package com.quata.core.preferences

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal interface PreferenceValueCipher {
    fun encrypt(value: String): String
    fun decrypt(value: String): String
    fun isEncrypted(value: String): Boolean
}

internal class AndroidKeystorePreferenceValueCipher(
    private val keyAlias: String = DEFAULT_KEY_ALIAS
) : PreferenceValueCipher {
    override fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return listOf(
            FORMAT_VERSION,
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
            Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        ).joinToString(SEPARATOR)
    }

    override fun decrypt(value: String): String {
        val parts = value.split(SEPARATOR, limit = 3)
        require(parts.size == 3 && parts[0] == FORMAT_VERSION) {
            "Unsupported encrypted preference format"
        }
        val initializationVector = Base64.decode(parts[1], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[2], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, initializationVector)
        )
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    override fun isEncrypted(value: String): Boolean = value.startsWith("$FORMAT_VERSION$SEPARATOR")

    private fun secretKey(): SecretKey = synchronized(KEY_LOCK) {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey) ?: generateSecretKey()
    }

    private fun generateSecretKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val DEFAULT_KEY_ALIAS = "quata_session_aes_gcm_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION = "v1"
        const val SEPARATOR = "."
        const val GCM_TAG_LENGTH_BITS = 128
        val KEY_LOCK = Any()
    }
}
