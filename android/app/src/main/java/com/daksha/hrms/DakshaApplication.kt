package com.daksha.hrms

import android.app.Application
import com.daksha.hrms.data.api.NetworkModule
import com.daksha.hrms.data.local.SecurePreferences

class DakshaApplication : Application() {

    lateinit var securePreferences: SecurePreferences
        private set

    lateinit var networkModule: NetworkModule
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        securePreferences = SecurePreferences(this)
        networkModule = NetworkModule(this, securePreferences)
    }

    companion object {
        lateinit var instance: DakshaApplication
            private set
    }
}
