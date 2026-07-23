package com.learnspark.core.network

/**
 * 平台 API Base URL。
 *
 * - Android 模拟器：`10.0.2.2:8080`（访问宿主机 8080 端口）
 * - Desktop / 物理设备：`localhost:8080`
 *
 * 通过 expect/actual 注入 Koin `LearnSparkApi`。
 */
expect fun apiBaseUrl(): String
