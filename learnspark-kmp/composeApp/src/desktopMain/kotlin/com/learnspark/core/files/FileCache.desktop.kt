package com.learnspark.core.files

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

/**
 * R5c Desktop：FileCache 使用 ~/.learnspark/cache/files/{key} 作为本地缓存。
 */
actual class FileCache {
    private fun root(): File {
        val home = System.getProperty("user.home") ?: "."
        val dir = File(Paths.get(home, ".learnspark", "cache", "files").toString())
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun file(key: String): File = File(root(), key)

    actual suspend fun put(key: String, bytes: ByteArray) {
        withContext(Dispatchers.IO) {
            val f = file(key)
            f.parentFile?.mkdirs()
            Files.write(f.toPath(), bytes)
        }
    }

    actual suspend fun get(key: String): ByteArray? = withContext(Dispatchers.IO) {
        val f = file(key)
        if (f.exists()) Files.readAllBytes(f.toPath()) else null
    }

    actual suspend fun exists(key: String): Boolean = withContext(Dispatchers.IO) {
        file(key).exists()
    }

    actual suspend fun pathFor(key: String): String? = withContext(Dispatchers.IO) {
        val f = file(key)
        if (f.exists()) f.absolutePath else null
    }

    actual suspend fun exportToDocuments(key: String, fileName: String): String? = withContext(Dispatchers.IO) {
        val home = System.getProperty("user.home") ?: return@withContext null
        val docs = File(Paths.get(home, "Documents", "LearnSpark").toString())
        if (!docs.exists()) docs.mkdirs()
        val src = file(key)
        if (!src.exists()) return@withContext null
        val dst = File(docs, fileName)
        Files.copy(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING)
        dst.absolutePath
    }

    actual suspend fun clear() {
        withContext(Dispatchers.IO) {
            root().listFiles()?.forEach { it.delete() }
            Unit
        }
    }

    actual suspend fun rootDir(): String = root().absolutePath
}

actual suspend fun openWithSystem(path: String, mimeType: String): Boolean {
    return withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) return@withContext false
        if (!Desktop.isDesktopSupported()) return@withContext false
        try {
            Desktop.getDesktop().open(file)
            true
        } catch (e: Exception) {
            false
        }
    }
}
