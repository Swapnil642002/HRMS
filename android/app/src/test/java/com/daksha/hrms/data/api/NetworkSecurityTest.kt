package com.daksha.hrms.data.api

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class NetworkSecurityTest {
    @Test fun cookiesKeepTheirScopeAcrossRestart() {
        var stored = emptySet<String>()
        val url = "https://hrms.micromap.in/api/auth/login".toHttpUrl()
        val jar = SessionCookieJar({ stored }, { stored = it })
        val cookie = Cookie.parse(url, "aster_session=synthetic; Path=/api; Secure; HttpOnly; Max-Age=3600")!!
        jar.saveFromResponse(url, listOf(cookie))
        val restored = SessionCookieJar({ stored }, { stored = it })
        assertEquals(1, restored.loadForRequest(url).size)
        assertTrue(restored.loadForRequest("https://staging-hrms.micromap.in/api/".toHttpUrl()).isEmpty())
        assertTrue(restored.loadForRequest("https://other.hrms.micromap.in/api/".toHttpUrl()).isEmpty())
        assertTrue(restored.loadForRequest("http://hrms.micromap.in/api/".toHttpUrl()).isEmpty())
        assertTrue(restored.loadForRequest("https://hrms.micromap.in/elsewhere".toHttpUrl()).isEmpty())
        val loaded = restored.loadForRequest(url).single()
        assertTrue(loaded.hostOnly); assertTrue(loaded.secure); assertTrue(loaded.httpOnly)
        restored.saveFromResponse(url, listOf(Cookie.parse(url, "aster_session=; Path=/api; Secure; Max-Age=0")!!))
        assertTrue(restored.loadForRequest(url).isEmpty())
        assertTrue(stored.isEmpty())
    }

    @Test fun insecureDevelopmentCookiesStayInsecureAfterRestart() {
        var stored = emptySet<String>()
        val url = "http://10.0.2.2:3000/api/".toHttpUrl()
        val jar = SessionCookieJar({ stored }, { stored = it })
        jar.saveFromResponse(url, listOf(Cookie.parse(url, "aster_session=synthetic; Path=/; Max-Age=3600")!!))
        val cookie = SessionCookieJar({ stored }, { stored = it }).loadForRequest(url).single()
        assertFalse(cookie.secure); assertFalse(cookie.httpOnly); assertTrue(cookie.hostOnly)
    }

    @Test fun legacyCookiesAreDiscarded() {
        val jar = SessionCookieJar({ setOf("aster_session=legacy; domain=hrms.micromap.in") }, {})
        assertTrue(jar.loadForRequest("https://hrms.micromap.in/api/".toHttpUrl()).isEmpty())
    }

    @Test fun login429HasActionableMessageWithoutRetrying() {
        assertNull(loginRateLimitMessage(401, null))
        assertTrue(loginRateLimitMessage(429, "60")!!.contains("60 seconds"))
        assertTrue(loginRateLimitMessage(429, null)!!.contains("15 minutes"))
        assertTrue(loginRateLimitMessage(429, "untrusted")!!.contains("15 minutes"))
    }
}
