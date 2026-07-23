package com.learnspark.core.files

/**
 * R5c：跨端文件本地缓存。
 *
 * 用途：把从服务端下载的文件缓存到本地，避免重复下载；同时作为"系统应用打开"的临时文件源。
 *
 * 路径策略：
 *  - Desktop: `~/.learnspark/cache/files/{key}`
 *  - Android: `context.cacheDir/files/{key}`
 *
 * Key 格式建议："{kind}:{id}"，例如 "knowledge:abc-123" / "task_upload:def-456"。
 */
expect class FileCache() {
    suspend fun put(key: String, bytes: ByteArray)
    suspend fun get(key: String): ByteArray?
    suspend fun exists(key: String): Boolean

    /**
     * 返回缓存文件的绝对路径（用于"用系统应用打开"或"分享"）。
     * 若文件不存在返回 null。
     */
    suspend fun pathFor(key: String): String?

    /**
     * 复制缓存文件到用户可见目录（如 Desktop 的 Documents、Android 的 Downloads）。
     * 返回目标绝对路径。
     */
    suspend fun exportToDocuments(key: String, fileName: String): String?

    suspend fun clear()

    /** 缓存根目录（用于 UI 展示）。 */
    suspend fun rootDir(): String
}

/**
 * R5d：用系统默认应用打开本地文件。
 *
 * - Desktop: java.awt.Desktop.open(File)
 * - Android: Intent.ACTION_VIEW + Uri (FileProvider 共享)
 *
 * @param path 本地文件绝对路径
 * @param mimeType 目标应用的 MIME（未知类型时退化为通配）
 * @return true 表示已派发
 */
expect suspend fun openWithSystem(path: String, mimeType: String): Boolean
