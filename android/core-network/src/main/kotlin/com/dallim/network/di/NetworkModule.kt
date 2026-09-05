package com.dallim.network.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.dallim.network.BuildConfig
import com.dallim.network.auth.AuthApi
import com.dallim.network.auth.AuthInterceptor
import com.dallim.network.auth.DataStoreTokenProvider
import com.dallim.network.auth.RefreshApi
import com.dallim.network.auth.TokenAuthenticator
import com.dallim.network.auth.TokenProvider
import com.dallim.network.dallimbook.DallimbookApi
import com.dallim.network.home.HomeApi
import com.dallim.network.meetup.MeetupApi
import com.dallim.network.notification.NotificationApi
import com.dallim.network.racerecord.RaceRecordApi
import com.dallim.network.route.RouteApi
import com.dallim.network.run.RunApi
import com.dallim.network.user.UserApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * OkHttp's 10s default read timeout is too tight for `POST /routes/draw-convert`
 * (docs/02-api-spec.md 8장): the backend's OSRM map-matching call can legitimately take 15-20s+ for
 * a finger-drawn trace through a dense downtown road network (e.g. central Seoul), and the backend
 * side of this same timeout was raised to match (see backend HttpClientFactory). Every other
 * endpoint responds in well under a second, so raising the ceiling here doesn't change their
 * effective behavior — it only stops legitimate-but-slow OSRM calls from failing client-side with
 * a generic "네트워크 연결을 확인해주세요" error.
 */
private const val READ_TIMEOUT_SECONDS = 45L
private const val CONNECT_TIMEOUT_SECONDS = 10L

private val Context.dallimTokenDataStore: DataStore<Preferences> by preferencesDataStore(name = "dallim_tokens")

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RefreshRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RefreshOkHttp

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkBindModule {
    @Binds
    @Singleton
    abstract fun bindTokenProvider(impl: DataStoreTokenProvider): TokenProvider
}

/**
 * Retrofit + OkHttp wiring for docs/02-api-spec.md.
 *
 * Two separate Retrofit/OkHttpClient stacks are provided:
 * - the "main" one (AuthInterceptor + TokenAuthenticator) used for every real endpoint,
 * - a bare "refresh" one used ONLY for POST /auth/refresh, so refreshing a token can never
 *   itself 401-loop back into TokenAuthenticator (see RefreshApi doc comment).
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideTokenDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dallimTokenDataStore

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            // NEVER log request/response BODIES in a build users can install — auth requests
            // carry passwords/tokens (CLAUDE.md rule 5). android-dev: gate BODY level behind
            // a debug-only BuildConfig flag if deeper request debugging is needed locally.
            level = HttpLoggingInterceptor.Level.BASIC
        }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator,
        loggingInterceptor: HttpLoggingInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .authenticator(tokenAuthenticator)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    // --- refresh-only stack (no auth interceptor/authenticator attached) ---

    @Provides
    @Singleton
    @RefreshOkHttp
    fun provideRefreshOkHttpClient(loggingInterceptor: HttpLoggingInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()

    @Provides
    @Singleton
    @RefreshRetrofit
    fun provideRefreshRetrofit(
        @RefreshOkHttp okHttpClient: OkHttpClient,
        json: Json,
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideRefreshApi(@RefreshRetrofit retrofit: Retrofit): RefreshApi =
        retrofit.create(RefreshApi::class.java)

    // --- docs/02-api-spec.md endpoint groups, all on the main (auth-attached) Retrofit ---

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideUserApi(retrofit: Retrofit): UserApi = retrofit.create(UserApi::class.java)

    @Provides
    @Singleton
    fun provideHomeApi(retrofit: Retrofit): HomeApi = retrofit.create(HomeApi::class.java)

    @Provides
    @Singleton
    fun provideRouteApi(retrofit: Retrofit): RouteApi = retrofit.create(RouteApi::class.java)

    @Provides
    @Singleton
    fun provideRunApi(retrofit: Retrofit): RunApi = retrofit.create(RunApi::class.java)

    @Provides
    @Singleton
    fun provideDallimbookApi(retrofit: Retrofit): DallimbookApi = retrofit.create(DallimbookApi::class.java)

    @Provides
    @Singleton
    fun provideNotificationApi(retrofit: Retrofit): NotificationApi = retrofit.create(NotificationApi::class.java)

    @Provides
    @Singleton
    fun provideMeetupApi(retrofit: Retrofit): MeetupApi = retrofit.create(MeetupApi::class.java)

    @Provides
    @Singleton
    fun provideRaceRecordApi(retrofit: Retrofit): RaceRecordApi = retrofit.create(RaceRecordApi::class.java)
}
