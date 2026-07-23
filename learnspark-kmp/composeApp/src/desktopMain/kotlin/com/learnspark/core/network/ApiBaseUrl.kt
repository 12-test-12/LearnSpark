package com.learnspark.core.network

import com.learnspark.core.di.createSettings

/**
 * Desktop 默认：localhost（本机就是服务器）。
 * 用户可在设置中改为云服务器地址。
 */
actual fun apiBaseUrl(): String {
    val settings = createSettings()
    return ServerConfig.getBaseUrl(settings, "localhost")
}
