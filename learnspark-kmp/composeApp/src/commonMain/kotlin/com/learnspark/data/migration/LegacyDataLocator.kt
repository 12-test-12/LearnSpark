package com.learnspark.data.migration

/**
 * 阶段 3.1：旧版 Vue3 数据兜底（按文档 §11.2）。
 *
 * 客户端首次启动时检测本地是否存在旧版导出的 JSON 文件。
 * - Desktop：用户家目录下的 `.learnspark/legacy-export.json`
 * - Android：app internal storage 下的 `legacy-export.json`
 *
 * expect/actual：commonMain 不直接依赖平台文件系统。
 */
expect class LegacyDataLocator() {
    /**
     * 返回旧版导出文件的绝对路径；不存在则返回 null。
     */
    fun locateLegacyExport(): String?
}
