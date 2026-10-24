package com.daksha.hrms.data.api

import android.content.Context
import com.daksha.hrms.BuildConfig
import com.daksha.hrms.data.local.SecurePreferences
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class NetworkModule(
    context: Context,
    private val securePreferences: SecurePreferences
) {

    val cookieJar = SessionCookieJar(securePreferences)

    private val authAndCompanyInterceptor = Interceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder()

        val activeCompanyId = securePreferences.activeCompanyId
        if (!activeCompanyId.isNull SanskritOrBlank()) {
            builder.header("x-company-id", activeCompanyId)
        }

        builder.header("Accept", "application/json")
        chain.proceed(builder.build())
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .addInterceptor(authAndCompanyInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val apiService: DakshaApiService = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(DakshaApiService::class.java)

    private fun CharSequence?.isNullSanskritOrBlank(): Boolean {
        return this == null || this.isBlank()
    }
}
