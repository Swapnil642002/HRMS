package com.daksha.hrms.data.api

import com.daksha.hrms.data.local.SecurePreferences
import com.google.gson.Gson
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class SessionCookieJar(
    private val read: () -> Set<String>,
    private val write: (Set<String>) -> Unit
) : CookieJar {
    constructor(preferences: SecurePreferences) : this(
        { preferences.savedCookies }, { preferences.savedCookies = it }
    )

    private data class StoredCookie(
        val name: String, val value: String, val domain: String, val path: String,
        val expiresAt: Long, val secure: Boolean, val httpOnly: Boolean, val hostOnly: Boolean
    )
    private val gson = Gson()
    private val cookies = mutableMapOf<String, Cookie>()
    private fun key(cookie: Cookie) = "${cookie.name}\n${cookie.domain}\n${cookie.path}"

    init {
        // Legacy serialization lost hostOnly and boolean values; discard it and require login.
        for (raw in read()) {
            runCatching {
                val saved = gson.fromJson(raw, StoredCookie::class.java)
                require(saved.expiresAt > System.currentTimeMillis())
                val builder = Cookie.Builder().name(saved.name).value(saved.value)
                    .path(saved.path).expiresAt(saved.expiresAt)
                if (saved.hostOnly) builder.hostOnlyDomain(saved.domain) else builder.domain(saved.domain)
                if (saved.secure) builder.secure()
                if (saved.httpOnly) builder.httpOnly()
                val cookie = builder.build()
                cookies[key(cookie)] = cookie
            }
        }
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (cookie in cookies) {
            if (!cookie.matches(url)) continue
            if (cookie.expiresAt <= System.currentTimeMillis()) this.cookies.remove(key(cookie))
            else this.cookies[key(cookie)] = cookie
        }
        persist()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        cookies.entries.removeAll { it.value.expiresAt <= System.currentTimeMillis() }
        return cookies.values.filter { it.matches(url) }
    }

    @Synchronized
    fun clear() {
        cookies.clear()
        write(emptySet())
    }

    private fun persist() {
        write(cookies.values.map { cookie ->
            gson.toJson(StoredCookie(cookie.name, cookie.value, cookie.domain, cookie.path,
                cookie.expiresAt, cookie.secure, cookie.httpOnly, cookie.hostOnly))
        }.toSet())
    }
}
