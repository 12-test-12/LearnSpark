package com.learnspark.server.service.parsers

import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files as NioFiles
import java.util.zip.ZipInputStream

/**
 * R4b：ZIP 笔记压缩包解析器。
 *
 * 常见用途：
 *   - Notion 导出（zip 内含 .md + .csv + images/）
 *   - Obsidian vault 备份（zip 内 .md + attachments/）
 *   - 飞书 / 语雀 / 印象笔记批量导出
 *   - Apple Notes 批量导出（zip 内 .html）
 *   - Joplin 导出（zip 内 .md）
 *
 * 策略：
 *   1. 解压到临时目录
 *   2. 收集所有"文本类"文件（md / html / txt / enex / rst）
 *   3. 按文件 → 通过 [ParserRouter] 递归调用对应解析器
 *   4. 拼接结果 + 相对路径作为段落分隔
 *   5. 删除临时目录
 *
 * 限制：单 zip 解压后总大小 ≤ 200MB，单文件 ≤ 10MB（防 zip bomb）
 */
@Component
class ZipNoteParser(
    private val parserRouter: ParserRouter,
) : DocumentParser {

    override val supportedExtensions: Set<String> = setOf(".zip")

    private val innerExts = setOf(".md", ".markdown", ".txt", ".html", ".htm", ".enex", ".rst", ".log")

    private val totalUnzippedLimit = 200L * 1024 * 1024
    private val perFileLimit = 10L * 1024 * 1024

    override fun parse(filePath: String): ParseResult {
        val file = File(filePath)
        require(file.exists()) { "Zip file not found: $filePath" }

        val tempDir: File = NioFiles.createTempDirectory("learnspark-zip-").toFile()
        var totalSize = 0L
        val extractedFiles = mutableListOf<File>()

        try {
            file.inputStream().use { input ->
                ZipInputStream(input).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && !entry.name.contains("__MACOSX")) {
                            val safeName = entry.name.replace("..", "_")
                            val outFile = File(tempDir, safeName)
                            outFile.parentFile?.mkdirs()
                            val buffer = ByteArrayOutputStream()
                            val data = ByteArray(4096)
                            var read: Int
                            while (zis.read(data).also { read = it } > 0) {
                                buffer.write(data, 0, read)
                                totalSize += read
                                if (totalSize > totalUnzippedLimit) {
                                    error("Zip 解压后总大小超过 ${totalUnzippedLimit / 1024 / 1024}MB，可能是 zip bomb")
                                }
                            }
                            val bytes = buffer.toByteArray()
                            if (bytes.size <= perFileLimit) {
                                outFile.outputStream().use { it.write(bytes) }
                                extractedFiles.add(outFile)
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            val sections = mutableListOf<String>()
            var parsedCount = 0
            for (f in extractedFiles) {
                val ext = f.extension.lowercase().let { if (it.isBlank()) "" else ".$it" }
                if (ext !in innerExts) continue
                if (!parserRouter.supports(ext)) continue
                val relativePath = f.relativeTo(tempDir).invariantSeparatorsPath
                try {
                    val parser = parserRouter.route(ext)
                    val result = parser.parse(f.absolutePath)
                    sections += "----- $relativePath -----\n${result.text}\n"
                    parsedCount += 1
                } catch (e: Exception) {
                    sections += "----- $relativePath (解析失败: ${e.message}) -----\n"
                }
            }
            return ParseResult(
                text = sections.joinToString("\n"),
                metadata = mapOf(
                    "format" to "zip",
                    "fileCount" to extractedFiles.size.toString(),
                    "parsedCount" to parsedCount.toString(),
                ),
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
