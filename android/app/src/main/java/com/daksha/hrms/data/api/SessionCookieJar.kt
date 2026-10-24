package com.daksha.hrms.data.api

import com.daksha.hrms.data.local.SecurePreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class SessionCookieJar(private val securePreferences: SecurePreferences) : CookieJar {

    private val cookieMap = mutableMapOf<String, Cookie>()

    init {
        loadFromPreferences()
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (cookie in cookies) {
            cookieMap[cookie.name] = cookie
        }
        saveToPreferences()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val validCookies = cookieMap.values.filter { it.expiresAt > now }
        return validCookies
    }

    @Synchronized
    fun clear() {
        cookieMap.clear()
        securePreferences.savedCookies = emptySet()
    }

    private fun saveToPreferences() {
        val serialized = cookieMap.values.map { cookie ->
            "${cookie.name}=${cookie.value}; domain=${cookie.domain}; path=${cookie.path}; expires=${cookie.expiresAt}; secure=${cookie.secure}; httpOnly=${cookie.httpOnly}"
        }.toSet()
        securePreferences.savedCookies = serialized
    }

    private fun loadFromPreferences() {
        val rawSet = securePreferences.savedCookies
        for (raw in rawSet) {
            val parts = raw.split("; ")
            if (parts.isEmpty()) continue
            val keyVal = parts[0].split("=")
            if (keyVal.size < 2) continue
            val name = keyVal[0]
            val value = keyVal[1]

            var domain = ""
            var path = "/"
            var expiresAt = System.currentTimeMillis() + 8 * 3600 * 1000L
            var secure = false
            var httpOnly = true

            for (i in 1 until parts.size) {
                val kv = parts[i].split("=")
                when (kv[0].lowercase()) {
                    "domain" -> if (kv.size > 1) domain = kv[1]
                    "path" -> if (kv.size > 1) path = kv[1]
                    "expires" -> if (kv.size > 1) expiresAt = kv[1].toLongOrNull() ?: expiresAt
                    "secure" -> secure = true
                    "httponly" -> httpOnly = true
                }
            }

            val builder = Cookie.Builder()
                .name(name)
                .value(value)
                .path(path)
                .expiresAt(expiresAt)

            if (domain.isNotBlank()) {
                builder.domain(domain)
            } else {
                builder.hostOnlyDomain("10.0.2.2")
            }

            if (secure) builder.secure()
            if (httpOnly) builder.httpOnly()

            try {
                val cookie = builder.build()
                cookieMap[name] = cookie
            } catch (_: Exception) {
            }
        }
    }
}
