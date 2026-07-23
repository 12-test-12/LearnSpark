package com.learnspark.core.network

/**
 * 平台 API Base URL。
 *
 * 局域网同步方案：
 * - Desktop：默认绑定 `http://0.0.0.0:8080`（监听所有网卡，手机可通过 PC 的 IP 访问）
 * - Android：默认指向 `http://10.0.2.2:8080`（模拟器）或用户在设置中填写的 PC IP
 *
 * 运行时通过 [ServerConfig] 从 Settings 读取用户配置的服务器地址，
 * 用户可在设置页面修改以适配局域网 IP。
 * 将来部署到云服务器时，只需在设置中改为云端地址即可。
 */
expect fun apiBaseUrl(): String

/**
 * 服务器地址运行时配置（跨平台共享）。
 *
 * 存储在 multiplatform-settings 中，键为 `server.host`。
 * - Desktop 端：默认 `localhost`（本机就是服务器）
 * - Android 端：默认空字符串，用户需在设置中填写 PC 的局域网 IP（如 192.168.1.100）
 */
object ServerConfig {
    private const val KEY_HOST = "server.host"
    private const val KEY_PORT = "server.port"
    private const val DEFAULT_PORT = "8080"

    /**
     * 获取完整的服务器 URL。
     * @param settings Koin 注入的 Settings 实例
     * @param defaultHost 平台默认主机名（Desktop= localhost，Android= 10.0.2.2）
     */
    fun getBaseUrl(settings: com.russhwolf.settings.Settings, defaultHost: String): String {
        val host = settings.getString(KEY_HOST, defaultHost)
        val port = settings.getString(KEY_PORT, DEFAULT_PORT)
        return "http://$host:$port"
    }

    /**
     * 保存服务器地址。
     */
    fun setServerAddress(settings: com.russhwolf.settings.Settings, host: String, port: String = DEFAULT_PORT) {
        settings.putString(KEY_HOST, host)
        settings.putString(KEY_PORT, port)
    }

    /**
     * 获取当前配置的主机名（用于 UI 显示）。
     */
    fun getHost(settings: com.russhwolf.settings.Settings, defaultHost: String): String {
        return settings.getString(KEY_HOST, defaultHost)
    }

    /**
     * 获取当前配置的端口（用于 UI 显示）。
     */
    fun getPort(settings: com.russhwolf.settings.Settings): String {
        return settings.getString(KEY_PORT, DEFAULT_PORT)
    }
}
