package com.learnspark.data.migration

import android.content.Context
import com.learnspark.core.di.androidContextLazy
import java.io.File

/**
 * Android 实际：app internal filesDir 下的 `legacy-export.json`。
 */
actual class LegacyDataLocator actual constructor() {
    actual fun locateLegacyExport(): String? {
        val ctx: Context = androidContextLazy.get() ?: return null
        val candidate = File(ctx.filesDir, "legacy-export.json")
        return if (candidate.exists() && candidate.isFile && candidate.canRead()) {
            candidate.absolutePath
        } else {
            null
        }
    }
}

actual val platformFileReader: PlatformFileReader = object : PlatformFileReader {
    override fun read(path: String): String {
        val ctx = androidContextLazy.get() ?: error("Android context not initialized")
        val file = File(path)
        // 安全：只允许读取 filesDir 下的文件
        val resolved = if (file.absolutePath.startsWith(ctx.filesDir.absolutePath)) file
        else File(ctx.filesDir, file.name)
        return resolved.readText(Charsets.UTF_8)
    }
}
