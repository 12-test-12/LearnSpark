package com.learnspark.data.migration

import com.learnspark.data.api.LearnSparkApi
import com.learnspark.data.api.MigrationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 阶段 3.1：迁移客户端门面。
 *
 * 流程（按文档 §11.2.2）：
 *   1. 首次启动 → [LegacyDataLocator] 探测本地是否有旧版导出文件
 *   2. UI 提示用户 → 用户确认后调用 [importIfPresent]
 *   3. 上传到服务端 /api/v1/migration/import
 *   4. 成功后本地标记为「已迁移」，下次不再提示
 */
class MigrationService(
    private val api: LearnSparkApi,
    private val locator: LegacyDataLocator,
    private val userIdProvider: () -> String?,
    private val onSuccess: () -> Unit = {},
) {
    /**
     * 返回是否检测到旧版导出文件。
     */
    fun hasLegacyExport(): Boolean = locator.locateLegacyExport() != null

    /**
     * 上传本地旧版导出到服务端。
     * 成功回调 [onSuccess]（用于标记本地已迁移，避免重复提示）。
     */
    suspend fun importIfPresent(): MigrationResult? = withContext(Dispatchers.IO) {
        val path = locator.locateLegacyExport() ?: return@withContext null
        val userId = userIdProvider() ?: return@withContext null
        val json = readFileSafely(path) ?: return@withContext null
        val result = api.importLegacy(json, userId)
        if (result.insertedTotal > 0 || result.skippedTotal > 0) {
            onSuccess()
        }
        result
    }

    private fun readFileSafely(path: String): String? = try {
        // 跨平台读文件：Desktop 直接 java.io.File；Android 通过 Context.openInputStream
        readTextFromPath(path)
    } catch (e: Exception) {
        null
    }

    /**
     * 跨平台读文件。
     * commonMain 不直接使用 java.io.File，改用 expect/actual 桥接。
     */
    private fun readTextFromPath(path: String): String = platformFileReader.read(path)
}

/**
 * 平台文件读取桥接（避免在 commonMain 写 java.io.File）。
 */
expect val platformFileReader: PlatformFileReader

interface PlatformFileReader {
    fun read(path: String): String
}
