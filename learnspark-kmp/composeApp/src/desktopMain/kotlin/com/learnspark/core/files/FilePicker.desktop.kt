package com.learnspark.core.files

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * R4c Desktop：使用 AWT FileDialog。
 *
 * 限制：单文件选择；modal dialog；通过 AWT 线程调度避免阻塞 EDT。
 */
actual object FilePicker {
    actual suspend fun pickFile(
        title: String,
        allowedExtensions: Set<String>,
    ): PickedFile? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val frame = Frame()
            val dialog = FileDialog(frame, title, FileDialog.LOAD).apply {
                isModal = true
                isMultipleMode = false
                if (allowedExtensions.isNotEmpty()) {
                    setFilenameFilter { _, name ->
                        val ext = name.substringAfterLast('.', "").lowercase()
                        ext.isNotEmpty() && allowedExtensions.contains(ext)
                    }
                }
            }
            try {
                dialog.isVisible = true
                val dir = dialog.directory
                val file = dialog.file
                if (dir == null || file == null) return@withContext null
                val f = File(dir, file)
                PickedFile(name = f.name, bytes = f.readBytes())
            } finally {
                dialog.dispose()
                frame.dispose()
            }
        }
    }
}
