package com.learnspark.core.files

/**
 * R4c：跨平台文件选择器。
 *
 * - Desktop: AWT FileDialog
 * - Android: ActivityResultContracts.OpenDocument
 *
 * 平台实现见 androidMain / desktopMain 的 actual fun pickFile()。
 */
expect object FilePicker {
    /**
     * 弹出文件选择器。
     * @param title 标题（仅 Desktop 显示）
     * @param allowedExtensions 允许的扩展名集合（不含点号，例如 {"pdf","md","enex"}）
     * @return 用户选择结果；取消时返回 null
     */
    suspend fun pickFile(
        title: String = "选择文件",
        allowedExtensions: Set<String> = emptySet(),
    ): PickedFile?

    /**
     * 弹出多文件选择器（支持批量选择）。
     * @param title 标题（仅 Desktop 显示）
     * @param allowedExtensions 允许的扩展名集合
     * @return 用户选择的结果列表；取消时返回空列表
     */
    suspend fun pickFiles(
        title: String = "选择文件",
        allowedExtensions: Set<String> = emptySet(),
    ): List<PickedFile>
}
