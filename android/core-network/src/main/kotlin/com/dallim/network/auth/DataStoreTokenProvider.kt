package com.dallim.network.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")

/**
 * DataStore-backed [TokenProvider]. See TokenProvider doc for why this uses runBlocking
 * instead of suspend functions (Interceptor/Authenticator run off the coroutine scope).
 */
@Singleton
class DataStoreTokenProvider @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : TokenProvider {

    override fun getAccessToken(): String? = runBlocking {
        dataStore.data.first()[ACCESS_TOKEN_KEY]
    }

    override fun getRefreshToken(): String? = runBlocking {
        dataStore.data.first()[REFRESH_TOKEN_KEY]
    }

    override fun saveTokens(accessToken: String, refreshToken: String) {
        runBlocking {
            dataStore.edit { prefs ->
                prefs[ACCESS_TOKEN_KEY] = accessToken
                prefs[REFRESH_TOKEN_KEY] = refreshToken
            }
        }
    }

    override fun clearTokens() {
        runBlocking {
            dataStore.edit { prefs ->
                prefs.remove(ACCESS_TOKEN_KEY)
                prefs.remove(REFRESH_TOKEN_KEY)
            }
        }
    }
}
