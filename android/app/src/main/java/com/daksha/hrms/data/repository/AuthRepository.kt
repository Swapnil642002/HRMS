package com.daksha.hrms.data.repository

import com.daksha.hrms.data.api.DakshaApiService
import com.daksha.hrms.data.api.SessionCookieJar
import com.daksha.hrms.data.api.loginRateLimitMessage
import com.daksha.hrms.data.api.models.*
import com.daksha.hrms.data.local.SecurePreferences

class AuthRepository(
    private val api: DakshaApiService,
    private val securePreferences: SecurePreferences,
    private val cookieJar: SessionCookieJar
) {

    suspend fun login(email: String, pass: String): Result<Boolean> {
        return try {
            val response = api.login(LoginRequest(email.trim().lowercase(), pass))
            if (response.isSuccessful && response.body()?.ok == true) {
                securePreferences.userEmail = email
                Result.success(true)
            } else {
                val rateLimit = loginRateLimitMessage(response.code(), response.headers()["Retry-After"])
                if (rateLimit != null) return Result.failure(Exception(rateLimit))
                val errorMsg = response.errorBody()?.string() ?: "Invalid login credentials"
                Result.failure(Exception(extractErrorMessage(errorMsg)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMe(): Result<UserResponse> {
        return try {
            val response = api.getMe()
            if (response.isSuccessful && response.body() != null) {
                val user = response.body()!!
                if (securePreferences.activeCompanyId == null && user.memberships.isNotEmpty()) {
                    securePreferences.activeCompanyId = user.memberships.first().company.id
                }
                Result.success(user)
            } else {
                Result.failure(Exception("Unauthorized"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout(): Result<Boolean> {
        return try {
            api.logout()
            cookieJar.clear()
            securePreferences.clearAll()
            Result.success(true)
        } catch (e: Exception) {
            cookieJar.clear()
            securePreferences.clearAll()
            Result.success(true)
        }
    }

    suspend fun requestAccess(companyId: String, code: String, name: String, email: String): Result<String> {
        return try {
            val response = api.requestAccess(AccessRequestInput(companyId, code, name, email))
            if (response.isSuccessful) {
                Result.success(response.body()?.get("message") ?: "Request submitted successfully")
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to submit access request"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun forgotPassword(email: String): Result<String> {
        return try {
            val response = api.forgotPassword(ForgotPasswordInput(email))
            if (response.isSuccessful) {
                Result.success(response.body()?.get("message") ?: "Instructions sent to email")
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to request password reset"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun changePassword(current: String, newPass: String): Result<String> {
        return try {
            val response = api.changePassword(ChangePasswordInput(current, newPass))
            if (response.isSuccessful) {
                Result.success(response.body()?.get("message") ?: "Password changed successfully")
            } else {
                Result.failure(Exception(extractErrorMessage(response.errorBody()?.string() ?: "")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractErrorMessage(raw: String): String {
        return try {
            if (raw.contains("\"message\":")) {
                val idx = raw.indexOf("\"message\":\"")
                if (idx != -1) {
                    val start = idx + 11
                    val end = raw.indexOf("\"", start)
                    if (end != -1) return raw.substring(start, end)
                }
            }
            raw.ifBlank { "An error occurred" }
        } catch (_: Exception) {
            raw
        }
    }
}
