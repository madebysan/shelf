package com.madebysan.shelf.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.madebysan.shelf.data.remote.GoogleDriveApi
import com.madebysan.shelf.service.auth.GoogleAuthManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(authManager: GoogleAuthManager): OkHttpClient {
        val authInterceptor = Interceptor { chain ->
            val token = runBlocking { authManager.getAccessToken() }
            val request = if (token != null) {
                chain.request().newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }

            val response = chain.proceed(request)

            // If 401, invalidate token and retry once with a fresh one
            if (response.code == 401) {
                response.close()
                val freshToken = runBlocking { authManager.invalidateAndRefreshToken() }
                if (freshToken != null) {
                    val retryRequest = chain.request().newBuilder()
                        .header("Authorization", "Bearer $freshToken")
                        .build()
                    return@Interceptor chain.proceed(retryRequest)
                }
            }

            response
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideGoogleDriveApi(retrofit: Retrofit): GoogleDriveApi {
        return retrofit.create(GoogleDriveApi::class.java)
    }
}
