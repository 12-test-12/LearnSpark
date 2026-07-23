package com.learnspark.server.service.parsers

/**
 * 解析结果。
 *
 * - text: 提取出的纯文本
 * - pages: 分页数（PDF/PPT 等）；纯文本/Markdown 时为 null
 * - metadata: 文件元数据（作者、创建时间等）
 */
data class ParseResult(
    val text: String,
    val pages: Int? = null,
    val metadata: Map<String, String> = emptyMap(),
) {
    companion object {
        val EMPTY = ParseResult(text = "")
    }
}

/**
 * 解析器接口。
 *
 * 阶段 2.2/2.3 共有 4 个实现：PdfBox / Tika / Tesseract / Markdown。
 */
interface DocumentParser {
    /** 解析器支持的扩展名（小写，含点号，如 ".pdf"） */
    val supportedExtensions: Set<String>

    /** 解析文件 */
    fun parse(filePath: String): ParseResult

    /** 是否支持该文件 */
    fun supports(fileType: String): Boolean = supportedExtensions.any { it.equals(fileType, ignoreCase = true) }
}
