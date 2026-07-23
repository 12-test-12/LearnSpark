package com.learnspark.core.files

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * R4c Desktop：使用 AWT FileDialog。
 *
 * pickFile：单选
 * pickFiles：多选（isMultipleMode = true）
 */
actual object FilePicker {
    actual suspend fun pickFile(
        title: String,
        allowedExtensions: Set<String>,
    ): PickedFile? {
        return pickFilesInternal(title, allowedExtensions, multiple = false).firstOrNull()
    }

    actual suspend fun pickFiles(
        title: String,
        allowedExtensions: Set<String>,
    ): List<PickedFile> {
        return pickFilesInternal(title, allowedExtensions, multiple = true)
    }

    private suspend fun pickFilesInternal(
        title: String,
        allowedExtensions: Set<String>,
        multiple: Boolean,
    ): List<PickedFile> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val frame = Frame()
        val dialog = FileDialog(frame, title, FileDialog.LOAD).apply {
            isModal = true
            isMultipleMode = multiple
            if (allowedExtensions.isNotEmpty()) {
                setFilenameFilter { _, name ->
                    val ext = name.substringAfterLast('.', "").lowercase()
                    ext.isNotEmpty() && allowedExtensions.contains(ext)
                }
            }
        }
        try {
            dialog.isVisible = true
            val dir = dialog.directory ?: return@withContext emptyList()
            val files = dialog.files
            if (files.isEmpty()) return@withContext emptyList()
            files.map { f -> PickedFile(name = f.name, bytes = f.readBytes()) }
        } finally {
            dialog.dispose()
            frame.dispose()
        }
    }
}
