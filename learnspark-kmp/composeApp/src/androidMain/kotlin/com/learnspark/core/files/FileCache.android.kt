package com.learnspark.core.files

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.learnspark.core.di.androidContextLazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * R5c Android：FileCache 使用 context.cacheDir/files/{key}。
 *
 * Context 通过 MainActivity.onCreate 中的 setAndroidContext 注入。
 */
actual class FileCache {
    private fun ctx(): Context = androidContextLazy.get()
        ?: error("AndroidContextHolder not initialized (call setAndroidContext in MainActivity)")

    private fun dir(): File {
        val d = File(ctx().cacheDir, "files")
        if (!d.exists()) d.mkdirs()
        return d
    }

    private fun file(key: String): File = File(dir(), key)

    actual suspend fun put(key: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val f = file(key)
        f.parentFile?.mkdirs()
        f.writeBytes(bytes)
    }

    actual suspend fun get(key: String): ByteArray? = withContext(Dispatchers.IO) {
        val f = file(key)
        if (f.exists()) f.readBytes() else null
    }

    actual suspend fun exists(key: String): Boolean = withContext(Dispatchers.IO) {
        file(key).exists()
    }

    actual suspend fun pathFor(key: String): String? = withContext(Dispatchers.IO) {
        val f = file(key)
        if (f.exists()) f.absolutePath else null
    }

    /**
     * 复制缓存文件到应用外部目录（getExternalFilesDir 下的 "exports"）。
     * 适合"分享给他人"场景；如果要真正保存到公共目录，需要走 MediaStore API。
     */
    actual suspend fun exportToDocuments(key: String, fileName: String): String? = withContext(Dispatchers.IO) {
        val c = ctx()
        val exports = c.getExternalFilesDir("exports") ?: return@withContext null
        if (!exports.exists()) exports.mkdirs()
        val src = file(key)
        if (!src.exists()) return@withContext null
        val dst = File(exports, fileName)
        src.copyTo(dst, overwrite = true)
        dst.absolutePath
    }

    actual suspend fun clear() {
        withContext(Dispatchers.IO) {
            dir().listFiles()?.forEach { it.delete() }
            Unit
        }
    }

    actual suspend fun rootDir(): String = dir().absolutePath
}

/**
 * R5d Android：用 Intent.ACTION_VIEW 调起系统默认应用。
 * 文件以 content:// URI 通过 FileProvider 暴露给目标应用。
 */
actual suspend fun openWithSystem(path: String, mimeType: String): Boolean {
    return withContext(Dispatchers.IO) {
        val c = androidContextLazy.get() ?: return@withContext false
        val file = File(path)
        if (!file.exists()) return@withContext false
        val authority = "${c.packageName}.fileprovider"
        val uri: Uri = try {
            FileProvider.getUriForFile(c, authority, file)
        } catch (e: Exception) {
            return@withContext false
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            c.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: Exception) {
            false
        }
    }
}
