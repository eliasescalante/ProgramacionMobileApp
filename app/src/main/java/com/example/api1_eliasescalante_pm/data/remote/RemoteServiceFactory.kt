package com.example.api1_eliasescalante_pm.data.remote

import com.example.api1_eliasescalante_pm.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Construye Retrofit + OkHttp con la URL base configurable.
 *
 * No hay credenciales ni tokens en el código. Si un servicio real los
 * necesitara, deben inyectarse desde una propiedad local y no versionarse.
 */
object RemoteServiceFactory {

    fun create(
        baseUrl: String = BuildConfig.REMOTE_BASE_URL
    ): ReminderApi {
        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(logging)
            .build()

        return Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ReminderApi::class.java)
    }
}
