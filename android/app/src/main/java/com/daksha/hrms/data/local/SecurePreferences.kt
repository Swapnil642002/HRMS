package com.daksha.hrms.data.local

import android.content.Context
import android.content.SharedPreferences

class SecurePreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    var activeCompanyId: String?
        get() = prefs.getString(KEY_ACTIVE_COMPANY, null)
        set(value) = prefs.edit().putString(KEY_ACTIVE_COMPANY, value).apply()

    var savedCookies: Set<String>
        get() = prefs.getStringSet(KEY_COOKIES, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_COOKIES, value).apply()

    var isBiometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, value).apply()

    var userEmail: String?
        get() = prefs.getString(KEY_USER_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_USER_EMAIL, value).apply()

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREF_NAME = "daksha_secure_prefs"
        private const val KEY_ACTIVE_COMPANY = "active_company_id"
        private const val KEY_COOKIES = "session_cookies"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_USER_EMAIL = "user_email"
    }
}
