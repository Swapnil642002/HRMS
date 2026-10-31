package com.daksha.hrms.data.api

// Do not automatically retry login requests: retries consume the same throttle budget.
fun loginRateLimitMessage(status: Int, retryAfter: String?): String? {
    if (status != 429) return null
    val seconds = retryAfter?.toLongOrNull()?.takeIf { it > 0 && it <= 86400 }
    return if (seconds != null) "Too many login attempts. Try again in $seconds seconds."
    else "Too many login attempts. Try again in 15 minutes."
}
