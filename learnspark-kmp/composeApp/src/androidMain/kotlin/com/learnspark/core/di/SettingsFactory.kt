package com.learnspark.core.di

import com.russhwolf.settings.Settings

/**
 * Android 实际：使用 SharedPreferences（通过 DataStore-backed Settings 桥接）。
 *
 * 简化实现：直接复用 Koin 提供的 Context，创建基于 SharedPreferences 的 Settings。
 */
actual fun createSettings(): Settings {
    val ctx = androidContextLazy.get() ?: error("Android context not initialized")
    val delegate = ctx.getSharedPreferences("learnspark.migration", android.content.Context.MODE_PRIVATE)
    return com.russhwolf.settings.SharedPreferencesSettings(delegate)
}
