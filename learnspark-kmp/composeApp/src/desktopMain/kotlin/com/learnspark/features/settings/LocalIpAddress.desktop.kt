package com.learnspark.features.settings

import java.net.InetAddress

/**
 * Desktop：遍历本机网卡，找到局域网 IPv4 地址。
 * 跳过回环、IPv6、虚拟网卡。
 */
actual fun getLocalIpAddress(): String {
    return try {
        val all = InetAddress.getAllByName(InetAddress.getLocalHost().hostName)
        all.firstOrNull { addr ->
            !addr.isLoopbackAddress &&
                addr.address.size == 4 && // IPv4
                !addr.hostAddress.startsWith("169.254") // 跳过 APIPA
        }?.hostAddress ?: "127.0.0.1"
    } catch (e: Exception) {
        // 备选方案：通过建立 Socket 探测本机出口 IP
        try {
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress("8.8.8.8", 53))
                socket.localAddress.hostAddress
            }
        } catch (_: Exception) {
            "127.0.0.1"
        }
    }
}
