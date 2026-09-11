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
                        when (stage) {
                            "probe-empty" -> check(prefs.all.isEmpty())
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
