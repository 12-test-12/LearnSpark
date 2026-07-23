package com.learnspark.core.security

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.set
import java.util.prefs.Preferences

/**
 * Desktop 平台 [PlatformTokenStore] 实现：JDK Preferences (用户级注册表) 持久化（明文）。
 *
 * 由 [EncryptedTokenStore] 装饰后对外提供加密的 [TokenStore] 视图。
 */
class DesktopPlatformTokenStore : PlatformTokenStore {

    private val settings = PreferencesSettings(
        Preferences.userRoot().node("com.learnspark.auth")
    )

    override suspend fun getAccessRaw(): String? = settings.getStringOrNull(KEY_ACCESS)

    override suspend fun getRefreshRaw(): String? = settings.getStringOrNull(KEY_REFRESH)

    override suspend fun setRaw(access: String?, refresh: String?) {
        if (access != null) settings[KEY_ACCESS] = access else settings.remove(KEY_ACCESS)
        if (refresh != null) settings[KEY_REFRESH] = refresh else settings.remove(KEY_REFRESH)
    }

    override suspend fun clearRaw() {
        settings.remove(KEY_ACCESS)
        settings.remove(KEY_REFRESH)
    }

    private companion object {
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
    }
}
