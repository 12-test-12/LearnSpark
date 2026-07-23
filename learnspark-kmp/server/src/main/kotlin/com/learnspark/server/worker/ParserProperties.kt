package com.learnspark.server.worker

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 阶段 2.1：app-worker 配置属性。
 *
 * - enabled: 是否启用轮询（worker profile 才有意义）
 * - pollIntervalMs: 轮询间隔（默认 5000ms = 5 秒）
 * - batchSize: 单次最多处理任务数（默认 3）
 * - maxRetry: 失败最大重试次数（默认 3）
 */
@ConfigurationProperties(prefix = "learnspark.parser")
data class ParserProperties(
    var enabled: Boolean = false,
    var pollIntervalMs: Long = 5000,
    var batchSize: Int = 3,
    var maxRetry: Int = 3,
    var tesseractDataPath: String = "./data/tessdata",
    var tesseractLanguage: String = "chi_sim+eng",
)
