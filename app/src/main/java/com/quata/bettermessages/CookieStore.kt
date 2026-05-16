package com.quata.bettermessages

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

interface PersistentCookieStore {
    fun save(url: HttpUrl, cookies: List<Cookie>)
    fun load(url: HttpUrl): List<Cookie>
    fun clear()
}

class InMemoryCookieStore : PersistentCookieStore {
    private val cookies = ConcurrentHashMap<String, Cookie>()

    override fun save(url: HttpUrl, cookies: List<Cookie>) {
        val now = System.currentTimeMillis()
        cookies.forEach { cookie ->
            val key = buildKey(cookie)
            if (cookie.expiresAt <= now) {
                this.cookies.remove(key)
            } else {
                this.cookies[key] = cookie
            }
        }
    }

    override fun load(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val out = mutableListOf<Cookie>()
        val iterator = cookies.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val cookie = entry.value
            if (cookie.expiresAt <= now) {
                iterator.remove()
                continue
            }
            if (cookie.matches(url)) out.add(cookie)
        }
        return out
    }

    override fun clear() {
        cookies.clear()
    }

    private fun buildKey(cookie: Cookie): String {
        return "${cookie.name}|${cookie.domain}|${cookie.path}"
    }
}

class SharedPreferencesCookieStore(
    context: Context,
    name: String = "better_messages_cookies",
    private val json: Json = BetterMessagesJson.default
) : PersistentCookieStore {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun save(url: HttpUrl, cookies: List<Cookie>) {
        val now = System.currentTimeMillis()
        val editor = preferences.edit()
        cookies.forEach { cookie ->
            val key = buildKey(cookie)
            if (cookie.expiresAt <= now) {
                editor.remove(key)
            } else {
                editor.putString(key, json.encodeToString(cookie.toStoredCookie()))
            }
        }
        editor.apply()
    }

    override fun load(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val editor = preferences.edit()
        var hasExpiredCookies = false
        val cookies = preferences.all.mapNotNull { (key, value) ->
            val stored = (value as? String)
                ?.let { runCatching { json.decodeFromString<StoredCookie>(it) }.getOrNull() }
                ?: return@mapNotNull null
            if (stored.expiresAt <= now) {
                editor.remove(key)
                hasExpiredCookies = true
                return@mapNotNull null
            }
            stored.toCookie()?.takeIf { it.matches(url) }
        }
        if (hasExpiredCookies) editor.apply()
        return cookies
    }

    override fun clear() {
        preferences.edit().clear().apply()
    }

    private fun buildKey(cookie: Cookie): String {
        return "${cookie.name}|${cookie.domain}|${cookie.path}"
    }
}

class BetterMessagesCookieJar(
    private val store: PersistentCookieStore
) : CookieJar {
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        store.save(url, cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return store.load(url)
    }
}

@Serializable
private data class StoredCookie(
    val name: String,
    val value: String,
    val expiresAt: Long,
    val domain: String,
    val path: String,
    val secure: Boolean,
    val httpOnly: Boolean,
    val hostOnly: Boolean
)

private fun Cookie.toStoredCookie(): StoredCookie = StoredCookie(
    name = name,
    value = value,
    expiresAt = expiresAt,
    domain = domain,
    path = path,
    secure = secure,
    httpOnly = httpOnly,
    hostOnly = hostOnly
)

private fun StoredCookie.toCookie(): Cookie? = runCatching {
    val builder = Cookie.Builder()
        .name(name)
        .value(value)
        .expiresAt(expiresAt)
        .path(path)

    if (hostOnly) {
        builder.hostOnlyDomain(domain)
    } else {
        builder.domain(domain)
    }

    if (secure) builder.secure()
    if (httpOnly) builder.httpOnly()
    builder.build()
}.getOrNull()
