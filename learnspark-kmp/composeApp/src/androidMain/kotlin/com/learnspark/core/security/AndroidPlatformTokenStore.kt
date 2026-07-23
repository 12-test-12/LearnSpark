package com.learnspark.core.security

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Android 平台 [PlatformTokenStore] 实现：DataStore Preferences 持久化（明文）。
 *
 * 由 [EncryptedTokenStore] 装饰后对外提供加密的 [TokenStore] 视图。
 */
private val Context.authDataStore by preferencesDataStore(name = "learnspark.auth")

class AndroidPlatformTokenStore(private val context: Context) : PlatformTokenStore {

    override suspend fun getAccessRaw(): String? =
        context.authDataStore.data.map { it[KEY_ACCESS] }.first()

    override suspend fun getRefreshRaw(): String? =
        context.authDataStore.data.map { it[KEY_REFRESH] }.first()

    override suspend fun setRaw(access: String?, refresh: String?) {
        context.authDataStore.edit {
            if (access != null) it[KEY_ACCESS] = access else it.remove(KEY_ACCESS)
            if (refresh != null) it[KEY_REFRESH] = refresh else it.remove(KEY_REFRESH)
        }
    }

    override suspend fun clearRaw() {
        context.authDataStore.edit { it.clear() }
    }

    private companion object {
        val KEY_ACCESS = stringPreferencesKey("access_token")
        val KEY_REFRESH = stringPreferencesKey("refresh_token")
    }
}
