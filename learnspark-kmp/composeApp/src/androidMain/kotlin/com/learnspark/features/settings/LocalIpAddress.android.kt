package com.learnspark.features.settings

/**
 * Android：不需要显示本机 IP（手机是客户端，连接 PC 的 IP）。
 */
actual fun getLocalIpAddress(): String = "N/A（请在电脑端查看 IP）"
