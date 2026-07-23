package com.learnspark.server.service.parsers

import org.springframework.stereotype.Component

/**
 * 阶段 2.2/2.3：解析路由。
 *
 * 根据 fileType 派发到对应解析器。
 * 路由策略按文档 §5.4：
 *   - pdf         -> PdfBoxParser
 *   - docx/xlsx/pptx/html/... -> TikaDocumentParser
 *   - png/jpg/... -> TesseractOcrParser
 *   - md/txt      -> MarkdownParser
 */
@Component
class ParserRouter(
    private val parsers: List<DocumentParser>,
) {
    private val byExt: Map<String, DocumentParser> = buildMap {
        parsers.forEach { p ->
            p.supportedExtensions.forEach { ext -> put(ext.lowercase(), p) }
        }
    }

    fun route(fileType: String): DocumentParser {
        val ext = if (fileType.startsWith(".")) fileType.lowercase() else ".${fileType.lowercase()}"
        return byExt[ext]
            ?: error("No parser registered for file type: $fileType (supported: ${byExt.keys.sorted()})")
    }

    fun supports(fileType: String): Boolean {
        val ext = if (fileType.startsWith(".")) fileType.lowercase() else ".${fileType.lowercase()}"
        return ext in byExt
    }

    fun supportedExtensions(): Set<String> = byExt.keys.toSortedSet()
}
