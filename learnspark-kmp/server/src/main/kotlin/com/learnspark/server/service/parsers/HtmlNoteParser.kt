package com.learnspark.server.service.parsers

import org.springframework.stereotype.Component
import java.io.File

/**
 * R4b：HTML 笔记解析器。
 *
 * 支持常见笔记应用导出的 HTML：
 *   - Apple Notes（导出为 .html）
 *   - 语雀 / 飞书文档导出
 *   - Notion 单页导出
 *   - Evernote 单条导出
 *
 * 为什么不引入 jsoup：单文件解析 + 体积有限，10MB 内文本用正则足够。
 * 标签剥离逻辑与 [EvernoteNoteParser] 共享 [htmlToPlain]。
 */
@Component
class HtmlNoteParser : DocumentParser {

    override val supportedExtensions: Set<String> = setOf(".html", ".htm", ".xhtml")

    override fun parse(filePath: String): ParseResult {
        val file = File(filePath)
        require(file.exists()) { "HTML file not found: $filePath" }
        val maxBytes = 10L * 1024 * 1024
        require(file.length() <= maxBytes) { "HTML file too large: ${file.length()} > $maxBytes" }

        val raw = file.readText(Charsets.UTF_8)
        return ParseResult(
            text = htmlToPlain(raw),
            metadata = mapOf("format" to "html"),
        )
    }
}
