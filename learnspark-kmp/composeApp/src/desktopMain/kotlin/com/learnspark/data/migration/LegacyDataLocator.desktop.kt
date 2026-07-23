package com.learnspark.data.migration

import java.io.File

/**
 * Desktop 实际：用户家目录下 `.learnspark/legacy-export.json`。
 */
actual class LegacyDataLocator actual constructor() {
    actual fun locateLegacyExport(): String? {
        val home = System.getProperty("user.home") ?: return null
        val candidate = File(home, ".learnspark/legacy-export.json")
        return if (candidate.exists() && candidate.isFile && candidate.canRead()) {
            candidate.absolutePath
        } else {
            null
        }
    }
}

actual val platformFileReader: PlatformFileReader = object : PlatformFileReader {
    override fun read(path: String): String = File(path).readText(Charsets.UTF_8)
}
