package com.learnspark.server.service.parsers

import org.apache.tika.Tika
import org.apache.tika.metadata.Metadata
import org.springframework.stereotype.Component
import java.io.File
import java.io.FileInputStream

/**
 * 阶段 2.2：Tika 文档解析器（docx/xlsx/pptx/html/rtf/odt 等）。
 *
 * 用 Tika 2.x 的 AutoDetectParser + BodyContentHandler 提取纯文本。
 */
@Component
class TikaDocumentParser : DocumentParser {

    private val tika = Tika()

    override val supportedExtensions: Set<String> = setOf(
        ".doc", ".docx",
        ".xls", ".xlsx",
        ".ppt", ".pptx",
        ".html", ".htm", ".xhtml",
        ".rtf", ".odt", ".ods", ".odp",
        ".epub", ".xml",
    )

    override fun parse(filePath: String): ParseResult {
        val file = File(filePath)
        require(file.exists()) { "Document not found: $filePath" }

        val metadata = Metadata()
        val text = FileInputStream(file).use { input ->
            tika.parseToString(input, metadata)
        }

        val meta = buildMap {
            metadata.get("dc:title")?.let { put("title", it) }
            metadata.get("dc:creator")?.let { put("author", it) }
            metadata.get("dcterms:created")?.let { put("created", it) }
            metadata.get("dcterms:modified")?.let { put("modified", it) }
        }
        return ParseResult(text = text.trim(), metadata = meta)
    }
}
