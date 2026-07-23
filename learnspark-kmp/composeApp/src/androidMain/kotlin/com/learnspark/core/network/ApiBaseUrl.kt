package com.learnspark.core.network

import com.learnspark.core.di.createSettings

/**
 * Android 默认：10.0.2.2（模拟器访问宿主机）。
 * 真机用户需在设置中填写 PC 的局域网 IP（如 192.168.1.100）。
 * 将来部署到云服务器时，改为云端地址即可。
 */
actual fun apiBaseUrl(): String {
    val settings = createSettings()
    return ServerConfig.getBaseUrl(settings, "10.0.2.2")
}
