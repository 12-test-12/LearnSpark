package com.learnspark.core.di

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import java.util.prefs.Preferences

/**
 * Desktop 实际：使用 JDK Preferences（与现有 PlatformTokenStore 保持一致）。
 */
actual fun createSettings(): Settings = PreferencesSettings(
    Preferences.userRoot().node("com.learnspark.kmp.migration")
)
