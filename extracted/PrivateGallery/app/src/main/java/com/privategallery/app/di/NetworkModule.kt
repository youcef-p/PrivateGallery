package com.privategallery.app.di

import com.privategallery.app.BuildConfig
import com.privategallery.app.data.remote.api.AuthApi
import com.privategallery.app.data.remote.api.FolderApi
import com.privategallery.app.data.remote.api.MediaApi
import com.privategallery.app.security.SecurePreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.CertificatePinner
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun provideAuthInterceptor(securePreferences: SecurePreferences): Interceptor = Interceptor { chain ->
        val token = securePreferences.getAccessToken()
        val request = if (token != null) {
            chain.request().newBuilder().addHeader("Authorization", "Bearer $token").build()
        } else chain.request()
        chain.proceed(request)
    }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor = HttpLoggingInterceptor().apply {
        // NEVER log bodies in this app: they may contain ciphertext blobs, tokens, or key
        // material even if "just encrypted bytes" — headers-only, and only in debug builds.
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS else HttpLoggingInterceptor.Level.NONE
    }

    @Provides
    @Singleton
    fun provideCertificatePinner(): CertificatePinner = CertificatePinner.Builder()
        // Real pins are added per environment once the production certificate is issued — see
        // docs/SETUP.md. An empty pinner here still enforces standard system-trust TLS validation;
        // it just doesn't add the extra pinning layer until pins are configured.
        .build()

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: Interceptor,
        loggingInterceptor: HttpLoggingInterceptor,
        certificatePinner: CertificatePinner
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .certificatePinner(certificatePinner)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides @Singleton fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)
    @Provides @Singleton fun provideFolderApi(retrofit: Retrofit): FolderApi = retrofit.create(FolderApi::class.java)
    @Provides @Singleton fun provideMediaApi(retrofit: Retrofit): MediaApi = retrofit.create(MediaApi::class.java)
}
