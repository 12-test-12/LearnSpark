package com.learnspark.server.service.parsers

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * 阶段 2.3：Tesseract OCR 占位实现。
 *
 * 完整实现需要：
 * 1. 引入 tess4j 依赖（含 native lib）
 * 2. 系统安装 Tesseract + 中英文语言包
 * 3. 数据路径：TESSDATA_PREFIX
 *
 * 当前阶段：保留接口（ParserRouter 已注册 .png/.jpg 等），实际解析返回错误，
 * 上层会重试 3 次后标记 FAILED。
 *
 * 引入 tess4j 的步骤（待 v3 阶段）：
 *   implementation(libs.tess4j)
 *   并把 [parse] 替换为真实 Tesseract.doOCR(...) 调用
 */
@Component
class TesseractOcrParser(
    @Value("\${learnspark.parser.tesseract-data-path:./data/tessdata}")
    private val tessDataPath: String,

    @Value("\${learnspark.parser.tesseract-language:chi_sim+eng}")
    private val language: String,
) : DocumentParser {

    private val log = LoggerFactory.getLogger(TesseractOcrParser::class.java)

    override val supportedExtensions: Set<String> = setOf(".png", ".jpg", ".jpeg", ".bmp", ".tiff", ".tif", ".webp")

    override fun parse(filePath: String): ParseResult {
        log.warn(
            "[OCR] Tesseract OCR is a placeholder. file={} dataPath={} language={}",
            filePath, tessDataPath, language
        )
        error(
            "Tesseract OCR not yet wired in this build. " +
                "Add tess4j dependency + install Tesseract system binary to enable. file=$filePath"
        )
    }
}
