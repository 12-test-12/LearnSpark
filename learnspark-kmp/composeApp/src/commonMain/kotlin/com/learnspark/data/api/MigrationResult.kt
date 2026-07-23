package com.learnspark.data.api

/**
 * 阶段 3.1：迁移导入结果。
 */
data class MigrationResult(
    val status: String,
    val insertedTotal: Int,
    val skippedTotal: Int,
)
