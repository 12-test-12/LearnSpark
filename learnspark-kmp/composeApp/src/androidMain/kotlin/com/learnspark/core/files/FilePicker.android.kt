package com.learnspark.core.files

import android.app.Activity
import android.net.Uri
import java.util.concurrent.atomic.AtomicReference

/**
 * R4c Android: file picker via ActivityResultContracts.OpenDocument / OpenMultipleDocuments.
 *
 * pickFile：单选（OpenDocument）
 * pickFiles：多选（OpenMultipleDocuments）
 */
actual object FilePicker {

    private val pendingResult: AtomicReference<((Uri?) -> Unit)?> = AtomicReference(null)
    private val pendingMultiResult: AtomicReference<((List<Uri>) -> Unit)?> = AtomicReference(null)

    actual suspend fun pickFile(
        title: String,
        allowedExtensions: Set<String>,
    ): PickedFile? {
        val launcher = IntentLauncherHolder.launcher ?: return null
        val uri: Uri? = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            pendingResult.set { result -> cont.resumeWith(Result.success(result)) }
            launcher(extensionsToMimeTypes(allowedExtensions))
        }
        if (uri == null) return null
        val activity = ActivityHolder.current ?: return null
        val name = queryDisplayName(activity, uri) ?: "upload-${System.currentTimeMillis()}"
        val bytes = activity.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return null
        return PickedFile(name = name, bytes = bytes)
    }

    actual suspend fun pickFiles(
        title: String,
        allowedExtensions: Set<String>,
    ): List<PickedFile> {
        val launcher = IntentLauncherHolder.multiLauncher ?: return emptyList()
        val uris: List<Uri> = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            pendingMultiResult.set { result -> cont.resumeWith(Result.success(result)) }
            launcher(extensionsToMimeTypes(allowedExtensions))
        }
        if (uris.isEmpty()) return emptyList()
        val activity = ActivityHolder.current ?: return emptyList()
        return uris.mapNotNull { uri ->
            val name = queryDisplayName(activity, uri) ?: "upload-${System.currentTimeMillis()}"
            val bytes = activity.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@mapNotNull null
            PickedFile(name = name, bytes = bytes)
        }
    }

    internal fun dispatchResult(uri: Uri?) {
        val cb = pendingResult.getAndSet(null) ?: return
        cb(uri)
    }

    internal fun dispatchMultiResult(uris: List<Uri>) {
        val cb = pendingMultiResult.getAndSet(null) ?: return
        cb(uris)
    }

    private fun queryDisplayName(activity: Activity, uri: Uri): String? {
        val cursor = activity.contentResolver.query(uri, null, null, null, null) ?: return null
        return cursor.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) it.getString(idx) else null
            } else null
        }
    }
}

object ActivityHolder {
    @Volatile
    var current: Activity? = null
}

object IntentLauncherHolder {
    @Volatile
    var launcher: ((Array<String>) -> Unit)? = null

    @Volatile
    var multiLauncher: ((Array<String>) -> Unit)? = null
}

/** Extension -> MIME type. Empty set or unknown extensions falls back to * / *. */
private fun extensionsToMimeTypes(allowed: Set<String>): Array<String> {
    if (allowed.isEmpty()) return arrayOf("*/*")
    val mimes: MutableSet<String> = allowed
        .map { it.lowercase() }
        .mapNotNull { ext -> extToMime(ext) }
        .toMutableSet()
    if (mimes.isEmpty()) return arrayOf("*/*")
    return mimes.toTypedArray()
}

private fun extToMime(ext: String): String? = when (ext) {
    "pdf" -> "application/pdf"
    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    "md", "markdown", "txt", "log", "rst", "adoc", "org", "tex" -> "text/plain"
    "html", "htm" -> "text/html"
    "enex" -> "application/xml"
    "zip" -> "application/zip"
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "bmp" -> "image/bmp"
    "tiff" -> "image/tiff"
    else -> null
}
