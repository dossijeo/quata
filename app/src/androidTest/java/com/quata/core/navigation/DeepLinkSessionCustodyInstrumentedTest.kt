package com.quata.core.navigation

import android.content.Context
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.Bundle
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quata.core.model.AuthSession
import com.quata.core.preferences.SessionPreferences
import com.quata.core.preferences.AndroidKeystorePreferenceValueCipher
import java.security.KeyStore
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Timer
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.schedule

/** Test-only session storage maintenance. Never creates an Activity or delivers an Intent. */
@RunWith(AndroidJUnit4::class)
class DeepLinkSessionCustodyInstrumentedTest {
    private fun matchesEncryptedSnapshot(snapshot: Map<String, *>, expected: AuthSession,
        keyAlias: String = "quata_session_aes_gcm_v1"): Boolean {
        val fields = mapOf("token" to expected.token, "user_id" to expected.userId,
            "email" to expected.email, "display_name" to expected.displayName,
            "auth_user_id" to expected.authUserId, "access_token" to expected.accessToken,
            "refresh_token" to expected.refreshToken)
        if (snapshot.keys != fields.keys + setOf("expires_at", "is_official")) return false
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(keyAlias)) return false
        val cipher = AndroidKeystorePreferenceValueCipher(keyAlias)
        return fields.all { (key, value) ->
            val encrypted = snapshot[key] as? String ?: return@all false
            cipher.isEncrypted(encrypted) && cipher.decrypt(encrypted) == value
        } && snapshot["expires_at"] == expected.expiresAt && snapshot["is_official"] == expected.isOfficial
    }

    /** Synthetic guard test: isolated transient key, no session preferences or network. */
    @Test
    fun rejectsMixedReceiptAndReplacedSession() {
        val alias = "quata_deeplink_guard_${java.util.UUID.randomUUID()}"
        val claims = JSONObject().put("sub", "actor").put("session_id", "session-one").put("exp", 2_000_000_000L)
        val access = "synthetic." + Base64.encodeToString(claims.toString().toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP) + ".synthetic"
        val input = JSONObject().put("authUserId", "actor").put("profileId", "profile")
            .put("authSessionId", "session-one").put("expiresAt", 2_000_000_000L)
            .put("accessToken", access).put("refreshToken", "synthetic-refresh")
            .put("email", "fixture@example.invalid").put("displayName", "Fixture").put("isOfficial", false)
        val session = expectedSession(input)
        try {
            val cipher = AndroidKeystorePreferenceValueCipher(alias)
            val snapshot = mapOf("token" to cipher.encrypt(access), "user_id" to cipher.encrypt("profile"),
                "email" to cipher.encrypt(session.email), "display_name" to cipher.encrypt(session.displayName),
                "auth_user_id" to cipher.encrypt("actor"), "access_token" to cipher.encrypt(access),
                "refresh_token" to cipher.encrypt("synthetic-refresh"), "expires_at" to session.expiresAt,
                "is_official" to false)
            check(matchesEncryptedSnapshot(snapshot, session, alias))
            check(!matchesEncryptedSnapshot(snapshot, session.copy(refreshToken = "later-refresh"), alias))
            check(!matchesEncryptedSnapshot(snapshot, session.copy(accessToken = "later-login"), alias))
            check(!matchesEncryptedSnapshot(snapshot, session.copy(expiresAt = 2_000_000_001L), alias))
            check(!matchesEncryptedSnapshot(snapshot + ("token" to "plaintext"), session, alias))
            check(!matchesEncryptedSnapshot(emptyMap<String, Any>(), session, alias))
            for ((field, value) in listOf("authUserId" to "other", "authSessionId" to "session-two", "expiresAt" to 2_000_000_001L)) {
                check(runCatching { expectedSession(JSONObject(input.toString()).put(field, value)) }.isFailure)
            }
        } finally {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        }
    }

    private fun expectedSession(input: JSONObject): AuthSession {
        val access = input.getString("accessToken")
        val parts = access.split('.')
        check(parts.size == 3)
        val claims = JSONObject(String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8))
        check(claims.getString("sub") == input.getString("authUserId"))
        check(claims.getString("session_id") == input.getString("authSessionId"))
        check(claims.getLong("exp") == input.getLong("expiresAt"))
        val refresh = input.getString("refreshToken")
        check(refresh.isNotBlank())
        return AuthSession(token = access, userId = input.getString("profileId"),
            email = input.getString("email"), displayName = input.getString("displayName"),
            authUserId = input.getString("authUserId"), accessToken = access, refreshToken = refresh,
            expiresAt = input.getLong("expiresAt"), isOfficial = input.getBoolean("isOfficial"))
    }

    // Read raw encrypted values without restoreSession(), migration, refresh or writes.
    // The coordinator must validate the returned bearer at Auth and journal it privately.
    private fun readOwnedSession(snapshot: Map<String, *>, input: JSONObject,
        alias: String = "quata_session_aes_gcm_v1"): JSONObject {
        check(KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(alias))
        val cipher = AndroidKeystorePreferenceValueCipher(alias)
        fun read(key: String): String {
            val value = snapshot[key] as? String ?: error("missing_encrypted_field")
            check(cipher.isEncrypted(value))
            return cipher.decrypt(value) ?: error("unreadable_encrypted_field")
        }
        val access = read("access_token")
        val parts = access.split('.')
        check(parts.size == 3)
        val claims = JSONObject(String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8))
        val session = JSONObject().put("profileId", read("user_id")).put("authUserId", read("auth_user_id"))
            .put("authSessionId", claims.getString("session_id")).put("accessToken", access)
            .put("refreshToken", read("refresh_token")).put("expiresAt", snapshot["expires_at"])
            .put("email", read("email")).put("displayName", read("display_name"))
            .put("isOfficial", snapshot["is_official"])
        check(session.getString("profileId") == input.getString("profileId"))
        check(session.getString("authUserId") == input.getString("authUserId"))
        check(session.getString("authSessionId").matches(Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")))
        check(matchesEncryptedSnapshot(snapshot, expectedSession(session), alias))
        return session
    }

    @Test
    fun readsOnlyExactEncryptedOwnedSession() {
        val alias = "quata_deeplink_read_guard_${java.util.UUID.randomUUID()}"
        val profile = java.util.UUID.randomUUID().toString()
        val actor = java.util.UUID.randomUUID().toString()
        val sessionId = java.util.UUID.randomUUID().toString()
        val claims = JSONObject().put("sub", actor).put("session_id", sessionId).put("exp", 2_000_000_000L)
        val access = "synthetic." + Base64.encodeToString(claims.toString().toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP) + ".synthetic"
        val input = JSONObject().put("profileId", profile).put("authUserId", actor)
        try {
            val cipher = AndroidKeystorePreferenceValueCipher(alias)
            val snapshot = mapOf("token" to cipher.encrypt(access), "user_id" to cipher.encrypt(profile),
                "email" to cipher.encrypt("fixture@example.invalid"), "display_name" to cipher.encrypt("Fixture"),
                "auth_user_id" to cipher.encrypt(actor), "access_token" to cipher.encrypt(access),
                "refresh_token" to cipher.encrypt("synthetic-only-refresh"), "expires_at" to 2_000_000_000L,
                "is_official" to false)
            val before = snapshot.toMap()
            val read = readOwnedSession(snapshot, input, alias)
            check(read.getString("accessToken") == access && read.getString("authSessionId") == sessionId)
            check(snapshot == before)
            for (field in listOf("profileId", "authUserId")) {
                check(runCatching { readOwnedSession(snapshot, JSONObject(input.toString()).put(field, java.util.UUID.randomUUID().toString()), alias) }.isFailure)
            }
            for (invalid in listOf(snapshot + ("token" to "plaintext"), snapshot + ("expires_at" to 2_000_000_001L),
                snapshot + ("unexpected" to "value"), snapshot - "refresh_token")) {
                check(runCatching { readOwnedSession(invalid, input, alias) }.isFailure)
            }
        } finally {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        }
    }

    @Test(timeout = 90_000)
    fun onePrivateSessionStep() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val name = InstrumentationRegistry.getArguments().getString("custodySocket").orEmpty()
        check(name.matches(Regex("quata-deeplink-[0-9a-f-]{36}")))
        val context = instrumentation.targetContext
        check(context.applicationContext.javaClass == android.app.Application::class.java)
        val storage = SessionPreferences(context)
        val prefs = context.getSharedPreferences("quata_session", Context.MODE_PRIVATE)
        try {
            LocalServerSocket(name).use { server ->
                val accepted = AtomicReference<LocalSocket?>()
                val deadline = Timer("deep-link-private-step", true)
                deadline.schedule(80_000) {
                    runCatching { accepted.get()?.close() }
                    runCatching { server.close() }
                }
                try {
                    instrumentation.sendStatus(0, Bundle().apply { putString("deepLinkCustodySocketReady", name) })
                    server.accept().use { socket ->
                        accepted.set(socket)
                        socket.soTimeout = 30_000
                        val reader = socket.inputStream.bufferedReader()
                        val line = StringBuilder()
                        while (true) {
                            val char = reader.read()
                            check(char >= 0 && line.length < 32_768)
                            if (char == 10) break
                            line.append(char.toChar())
                        }
                        val input = JSONObject(line.toString())
                        val stage = input.getString("stage")
                        val uuid = Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")
                        check(listOf("runId", "stepId").all { input.getString(it).matches(uuid) })
                        var privateSession: JSONObject? = null
                        when (stage) {
                            "probe-empty" -> check(prefs.all.isEmpty())
                            "read-owned" -> {
                                check(input.keys().asSequence().toSet() == setOf("runId", "stepId", "stage", "profileId", "authUserId"))
                                check(listOf("profileId", "authUserId").all { input.getString(it).matches(uuid) })
                                val snapshot = prefs.all
                                privateSession = readOwnedSession(snapshot, input)
                                check(prefs.all == snapshot)
                            }
                            "install", "clear" -> {
                                check(listOf("profileId", "authUserId", "authSessionId").all { input.getString(it).matches(uuid) })
                                val session = expectedSession(input)
                                if (stage == "install") {
                                    check(prefs.all.isEmpty())
                                    check(session.expiresAt!! > System.currentTimeMillis() / 1000 + 120)
                                    storage.saveSession(session)
                                    check(prefs.edit().commit())
                                    check(matchesEncryptedSnapshot(prefs.all, session))
                                } else {
                                    // A later login or refresh, including the same actor, is not ours to clear.
                                    val snapshot = prefs.all
                                    check(matchesEncryptedSnapshot(snapshot, session))
                                    check(prefs.all == snapshot)
                                    storage.clear()
                                    check(prefs.edit().commit())
                                    check(prefs.all.isEmpty())
                                }
                            }
                            else -> error("unsupported_step")
                        }
                        val receipt = JSONObject().put("runId", input.getString("runId"))
                            .put("stepId", input.getString("stepId")).put("stage", stage).put("verified", true)
                        // Never send this payload via instrumentation status or a file.
                        privateSession?.let { receipt.put("privateSession", it) }
                        socket.outputStream.bufferedWriter().use { writer ->
                            writer.write(receipt.toString()); writer.newLine(); writer.flush()
                        }
                    }
                } finally { deadline.cancel() }
            }
        } catch (_: Throwable) {
            // No exception causes, request values, session or UI tree in instrumentation logs.
            throw AssertionError("deep_link_session_step_unresolved")
        }
    }
}
