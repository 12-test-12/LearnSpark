package com.learnspark.server.service.parsers

import org.springframework.stereotype.Component
import java.io.File

/**
 * 阶段 2.2：Markdown / TXT 解析器。
 *
 * 简单直读：UTF-8 解码 + 保留原始结构（不做 AST 转换，由前端渲染）。
 */
@Component
class MarkdownParser : DocumentParser {

    override val supportedExtensions: Set<String> = setOf(
        ".md", ".markdown",         // CommonMark / GFM
        ".txt", ".log",             // 纯文本
        ".rst",                     // reStructuredText（Python 文档 / Sphinx）
        ".adoc", ".asciidoc",       // AsciiDoc
        ".org",                     // Emacs Org-mode
        ".tex",                     // LaTeX 笔记（剥离命令后保留文本）
    )

    override fun parse(filePath: String): ParseResult {
        val file = File(filePath)
        require(file.exists()) { "Text file not found: $filePath" }
        // 限制单文件最大 10MB，避免 OOM
        val maxBytes = 10L * 1024 * 1024
        require(file.length() <= maxBytes) { "Text file too large: ${file.length()} > $maxBytes" }

        val text = file.readText(Charsets.UTF_8)
        return ParseResult(text = text)
    }
}
