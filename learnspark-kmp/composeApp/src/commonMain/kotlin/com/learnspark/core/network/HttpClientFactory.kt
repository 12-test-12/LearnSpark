package com.learnspark.core.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

/**
 * 跨端 Ktor HttpClient 工厂
 * - Android: 实际使用 OkHttp 引擎
 * - Desktop: 实际使用 CIO 引擎
 */
expect fun createHttpClient(block: HttpClientConfig<*>.() -> Unit = {}): HttpClient