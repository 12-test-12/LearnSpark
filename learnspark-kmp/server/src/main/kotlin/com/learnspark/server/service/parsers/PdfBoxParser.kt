package com.learnspark.server.service.parsers

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.stereotype.Component
import java.io.File

/**
 * 阶段 2.2：PDF 解析器（PDFBox 3.x）。
 *
 * PDFBox 3.x 用 Loader.loadPDF(file) 替代 2.x 的 PDDocument.load(file)。
 */
@Component
class PdfBoxParser : DocumentParser {

    override val supportedExtensions: Set<String> = setOf(".pdf")

    override fun parse(filePath: String): ParseResult {
        val file = File(filePath)
        require(file.exists()) { "PDF not found: $filePath" }

        val document: PDDocument = Loader.loadPDF(file)
        return document.use { doc ->
            val stripper = PDFTextStripper()
            val text = stripper.getText(doc)
            val metadata = doc.documentInformation.let { info ->
                buildMap {
                    info.author?.let { put("author", it) }
                    info.title?.let { put("title", it) }
                    info.subject?.let { put("subject", it) }
                    info.creator?.let { put("creator", it) }
                    info.producer?.let { put("producer", it) }
                    info.creationDate?.let { put("created", it.time.toString()) }
                }
            }
            ParseResult(
                text = text.trim(),
                pages = doc.numberOfPages,
                metadata = metadata,
            )
        }
    }
}
