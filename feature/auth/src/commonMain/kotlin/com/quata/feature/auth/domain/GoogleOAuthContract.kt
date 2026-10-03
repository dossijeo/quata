package com.quata.feature.auth.domain

import com.quata.core.model.AuthSession
import com.quata.core.model.currentEpochSeconds
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class GoogleOAuthRequest(
    val authorizationUrl: String,
    val redirectUri: String,
    val codeVerifier: String,
)

data class GoogleOAuthTokenSet(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
)

data class GoogleFederatedAuthentication(
    val session: AuthSession,
    val webSessionToken: String? = null,
)

fun buildGoogleOAuthRequest(
    supabaseUrl: String,
    redirectUri: String,
    randomBytes: (Int) -> ByteArray,
): GoogleOAuthRequest {
    val baseUrl = supabaseUrl.trim().trimEnd('/').takeIf(String::isNotBlank)
        ?: error("google_oauth_supabase_url_missing")
    val redirect = redirectUri.trim().takeIf(String::isNotBlank)
        ?: error("google_oauth_redirect_uri_missing")
    val verifier = randomBytes(64).requireSize(64).base64Url()
    val challenge = sha256(verifier.encodeToByteArray()).base64Url()
    val query = listOf(
        "provider" to "google",
        "redirect_to" to redirect,
        "code_challenge" to challenge,
        "code_challenge_method" to "s256",
    ).joinToString("&") { (name, value) -> "${name.percentEncode()}=${value.percentEncode()}" }
    return GoogleOAuthRequest(
        authorizationUrl = "$baseUrl/auth/v1/authorize?$query",
        redirectUri = redirect,
        codeVerifier = verifier,
    )
}

fun buildGoogleIdentityLinkRequest(
    supabaseUrl: String,
    redirectUri: String,
    randomBytes: (Int) -> ByteArray,
): GoogleOAuthRequest {
    val signIn = buildGoogleOAuthRequest(supabaseUrl, redirectUri, randomBytes)
    return signIn.copy(
        authorizationUrl = signIn.authorizationUrl
            .replace("/auth/v1/authorize?", "/auth/v1/user/identities/authorize?") +
            "&skip_http_redirect=true",
    )
}

fun parseGoogleIdentityAuthorizationUrl(payload: String): String =
    Json.parseToJsonElement(payload).jsonObject["url"]?.jsonPrimitive?.contentOrNull
        ?.takeIf(String::isNotBlank)
        ?: error("google_identity_authorization_url_missing")

fun parseGoogleOAuthCallback(callbackUrl: String, request: GoogleOAuthRequest): String {
    val callback = callbackUrl.trim()
    val callbackBase = callback.substringBefore('?').substringBefore('#')
    require(callbackBase == request.redirectUri.substringBefore('?').substringBefore('#')) {
        "google_oauth_redirect_mismatch"
    }
    val values = callback.substringAfter('?', "").substringBefore('#')
        .split('&')
        .filter(String::isNotBlank)
        .map { item ->
            val parts = item.split('=', limit = 2)
            parts[0].percentDecode() to parts.getOrElse(1) { "" }.percentDecode()
        }
        .groupBy({ it.first }, { it.second })
    values["error"]?.singleOrNull()?.takeIf(String::isNotBlank)?.let { error("google_oauth_$it") }
    return values["code"]?.singleOrNull()?.takeIf(String::isNotBlank)
        ?: error("google_oauth_code_missing")
}

fun parseGoogleOAuthTokenSet(payload: String): GoogleOAuthTokenSet {
    val root = Json.parseToJsonElement(payload).jsonObject
    val accessToken = root["access_token"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
        ?: error("google_oauth_access_token_missing")
    val refreshToken = root["refresh_token"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
        ?: error("google_oauth_refresh_token_missing")
    val expiresAt = root["expires_at"]?.jsonPrimitive?.longOrNull
        ?: root["expires_in"]?.jsonPrimitive?.longOrNull?.let { currentEpochSeconds() + it }
        ?: error("google_oauth_expiry_missing")
    return GoogleOAuthTokenSet(accessToken, refreshToken, expiresAt)
}

fun parseGoogleAuthUserId(payload: String): String =
    Json.parseToJsonElement(payload).jsonObject.let { root ->
        (root["user"]?.jsonObject ?: root)["id"]?.jsonPrimitive?.contentOrNull
            ?.takeIf(String::isNotBlank)
            ?: error("google_identity_user_id_missing")
    }

fun AuthSession.acceptLinkedGoogleSession(tokenPayload: String): AuthSession {
    val expectedAuthUserId = authUserId?.takeIf(String::isNotBlank)
        ?: error("google_identity_auth_user_id_missing")
    val root = Json.parseToJsonElement(tokenPayload).jsonObject
    val returnedAuthUserId = root["user"]?.jsonObject
        ?.get("id")?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
        ?: error("google_identity_user_id_missing")
    require(returnedAuthUserId == expectedAuthUserId) { "google_identity_user_mismatch" }
    val tokens = parseGoogleOAuthTokenSet(tokenPayload)
    return copy(
        token = tokens.accessToken,
        accessToken = tokens.accessToken,
        refreshToken = tokens.refreshToken,
        expiresAt = tokens.expiresAt,
    )
}

fun GoogleOAuthTokenSet.acceptFederatedProfile(payload: String): GoogleFederatedAuthentication {
    val root = Json.parseToJsonElement(payload).jsonObject
    val profile = root["profile"]?.jsonObject ?: error("google_oauth_profile_missing")
    val user = root["user"]?.jsonObject ?: error("google_oauth_user_missing")
    val profileId = profile["id"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
        ?: error("google_oauth_profile_id_missing")
    val authUserId = profile["auth_user_id"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
        ?: error("google_oauth_auth_user_id_missing")
    val returnedUserId = user["id"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
        ?: error("google_oauth_user_id_missing")
    require(authUserId == returnedUserId) { "google_oauth_identity_mismatch" }
    val displayName = profile["display_name"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotBlank)
        ?: "Usuario"
    val email = user["email"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotBlank)
        ?: "federated-$profileId@profile.quata.app"
    val webSessionToken = root["web_session"]?.jsonObject
        ?.get("token")?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
    return GoogleFederatedAuthentication(
        session = AuthSession(
            token = accessToken,
            userId = profileId,
            authUserId = authUserId,
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAt = expiresAt,
            email = email,
            displayName = displayName,
            isOfficial = profile["is_official"]?.jsonPrimitive?.booleanOrNull == true,
        ),
        webSessionToken = webSessionToken,
    )
}

private fun ByteArray.requireSize(expected: Int): ByteArray = also {
    require(size == expected) { "google_oauth_random_source_invalid" }
}

private fun String.percentEncode(): String = encodeToByteArray().joinToString("") { byte ->
    val value = byte.toInt() and 0xff
    if (value in 'a'.code..'z'.code || value in 'A'.code..'Z'.code ||
        value in '0'.code..'9'.code || value == '-'.code || value == '.'.code ||
        value == '_'.code || value == '~'.code
    ) value.toChar().toString() else "%${value.toString(16).uppercase().padStart(2, '0')}"
}

private fun String.percentDecode(): String {
    val bytes = mutableListOf<Byte>()
    var index = 0
    while (index < length) {
        if (this[index] == '%') {
            require(index + 2 < length) { "google_oauth_callback_invalid" }
            bytes += substring(index + 1, index + 3).toInt(16).toByte()
            index += 3
        } else {
            val encoded = this[index].toString().encodeToByteArray()
            bytes.addAll(encoded.toList())
            index += 1
        }
    }
    return bytes.toByteArray().decodeToString()
}

private fun ByteArray.base64Url(): String {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    val output = StringBuilder((size * 4 + 2) / 3)
    var index = 0
    while (index < size) {
        val first = this[index++].toInt() and 0xff
        val second = if (index < size) this[index++].toInt() and 0xff else -1
        val third = if (index < size) this[index++].toInt() and 0xff else -1
        output.append(alphabet[first ushr 2])
        output.append(alphabet[((first and 3) shl 4) or if (second >= 0) second ushr 4 else 0])
        if (second >= 0) output.append(alphabet[((second and 15) shl 2) or if (third >= 0) third ushr 6 else 0])
        if (third >= 0) output.append(alphabet[third and 63])
    }
    return output.toString()
}

// Small portable SHA-256 used only for RFC 7636 PKCE challenges.
private fun sha256(input: ByteArray): ByteArray {
    val constants = intArrayOf(
        0x428a2f98, 0x71374491, 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(), 0x3956c25b, 0x59f111f1, 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
        0xd807aa98.toInt(), 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
        0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(), 0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e.toInt(), 0x92722c85.toInt(),
        0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(), 0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814.toInt(), 0x8cc70208.toInt(), 0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt(),
    )
    val bitLength = input.size.toLong() * 8
    val paddedSize = ((input.size + 9 + 63) / 64) * 64
    val padded = ByteArray(paddedSize)
    input.copyInto(padded)
    padded[input.size] = 0x80.toByte()
    for (index in 0 until 8) padded[padded.lastIndex - index] = (bitLength ushr (index * 8)).toByte()
    val hash = intArrayOf(0x6a09e667, 0xbb67ae85.toInt(), 0x3c6ef372, 0xa54ff53a.toInt(), 0x510e527f, 0x9b05688c.toInt(), 0x1f83d9ab, 0x5be0cd19)
    val words = IntArray(64)
    for (offset in padded.indices step 64) {
        for (index in 0 until 16) {
            val start = offset + index * 4
            words[index] = ((padded[start].toInt() and 0xff) shl 24) or
                ((padded[start + 1].toInt() and 0xff) shl 16) or
                ((padded[start + 2].toInt() and 0xff) shl 8) or
                (padded[start + 3].toInt() and 0xff)
        }
        for (index in 16 until 64) {
            val a = words[index - 15]
            val b = words[index - 2]
            val s0 = a.rotateRight(7) xor a.rotateRight(18) xor (a ushr 3)
            val s1 = b.rotateRight(17) xor b.rotateRight(19) xor (b ushr 10)
            words[index] = words[index - 16] + s0 + words[index - 7] + s1
        }
        var a = hash[0]; var b = hash[1]; var c = hash[2]; var d = hash[3]
        var e = hash[4]; var f = hash[5]; var g = hash[6]; var h = hash[7]
        for (index in 0 until 64) {
            val sum1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
            val choice = (e and f) xor (e.inv() and g)
            val temp1 = h + sum1 + choice + constants[index] + words[index]
            val sum0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
            val majority = (a and b) xor (a and c) xor (b and c)
            val temp2 = sum0 + majority
            h = g; g = f; f = e; e = d + temp1; d = c; c = b; b = a; a = temp1 + temp2
        }
        hash[0] += a; hash[1] += b; hash[2] += c; hash[3] += d
        hash[4] += e; hash[5] += f; hash[6] += g; hash[7] += h
    }
    return ByteArray(32).also { output ->
        hash.forEachIndexed { index, value ->
            output[index * 4] = (value ushr 24).toByte()
            output[index * 4 + 1] = (value ushr 16).toByte()
            output[index * 4 + 2] = (value ushr 8).toByte()
            output[index * 4 + 3] = value.toByte()
        }
    }
}
