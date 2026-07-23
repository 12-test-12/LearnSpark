package com.learnspark.server.service.parsers

import org.springframework.stereotype.Component
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * R4b：Evernote 笔记解析器（.enex）。
 *
 * .enex 是 Evernote 导出的 XML 格式，结构：
 * ```xml
 * <en-export>
 *   <note>
 *     <title>...</title>
 *     <content><![CDATA[ <?xml version="1.0"?><!DOCTYPE en-note SYSTEM "...">
 *       <en-note>... HTML 片段 ...</en-note> ]]></content>
 *     <created>20240101T120000Z</created>
 *     <tag>tag1</tag>
 *   </note>
 *   <note>...</note>
 * </en-export>
 * ```
 *
 * 解析策略：
 *   - 每条 `<note>` 输出为一条 "===== TITLE =====\n{content}" 段落
 *   - `<content>` 内部是 ENML（Evernote HTML 变体），用 [enmlToText] 转纯文本
 */
@Component
class EvernoteNoteParser : DocumentParser {

    override val supportedExtensions: Set<String> = setOf(".enex")

    override fun parse(filePath: String): ParseResult {
        val file = File(filePath)
        require(file.exists()) { "ENEX file not found: $filePath" }
        val maxBytes = 50L * 1024 * 1024
        require(file.length() <= maxBytes) { "ENEX file too large: ${file.length()} > $maxBytes" }

        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(file)
        val root = doc.documentElement
        val notes = root.getElementsByTagName("note")
        val sections = mutableListOf<String>()
        for (i in 0 until notes.length) {
            val noteEl = notes.item(i) as? Element ?: continue
            val title = firstText(noteEl, "title").trim()
            val content = firstText(noteEl, "content")
            val tags = collectTextList(noteEl, "tag").joinToString(", ") { it.trim() }
            val text = enmlToText(content)
            sections += buildString {
                append("===== ").append(title.ifBlank { "(无标题)" }).append(" =====")
                if (tags.isNotEmpty()) append("\n标签：").append(tags)
                append("\n\n").append(text).append("\n")
            }
        }
        return ParseResult(
            text = sections.joinToString("\n"),
            metadata = mapOf("format" to "enex", "noteCount" to sections.size.toString()),
        )
    }

    private fun firstText(el: Element, tag: String): String {
        val list = el.getElementsByTagName(tag)
        return if (list.length > 0) (list.item(0) as Node).textContent ?: "" else ""
    }

    private fun collectTextList(el: Element, tag: String): List<String> {
        val list = el.getElementsByTagName(tag)
        return (0 until list.length).map { (list.item(it) as? Node)?.textContent ?: "" }
    }

    /**
     * ENML 内部是 XHTML 片段，转纯文本：
     *   1. 去除 CDATA 包裹
     *   2. 剥离 XML 声明 / DOCTYPE / <en-note> 外壳
     *   3. 走通用 HTML 标签剥离（与 [HtmlNoteParser] 共享逻辑）
     */
    private fun enmlToText(enml: String): String {
        var s = enml
        val cdataStart = s.indexOf("<![CDATA[")
        val cdataEnd = s.indexOf("]]>")
        if (cdataStart != -1 && cdataEnd != -1 && cdataEnd > cdataStart) {
            s = s.substring(cdataStart + "<![CDATA[".length, cdataEnd)
        }
        s = XML_DECL.replace(s, "")
            .replace(DOCTYPE_DECL, "")
            .replace(EN_NOTE_OPEN, "")
            .replace("</en-note>", "")
        return htmlToPlain(s)
    }

    companion object {
        private val XML_DECL = Regex("<\\?xml[^?]*\\?>")
        private val DOCTYPE_DECL = Regex("<!DOCTYPE[^>]*>")
        private val EN_NOTE_OPEN = Regex("<en-note[^>]*>")
    }
}

/**
 * ENML/HTML 通用纯文本提取（在 ENEX 与 HTML 解析器之间共享）。
 * 与 [HtmlNoteParser] 逻辑等价但使用 Kotlin Regex API（避免 Java Pattern 重复定义）。
 */
internal fun htmlToPlain(html: String): String {
    var s = html
    s = SCRIPT_RE.replace(s, " ")
    s = STYLE_RE.replace(s, " ")
    s = COMMENT_RE.replace(s, " ")
    s = BR_RE.replace(s, "\n")
    s = LI_RE.replace(s, "- ")
    s = BLOCK_RE.replace(s, "\n")
    s = TAG_RE.replace(s, "")
    s = s.replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
    return s.lines().map { it.trimEnd() }.filter { it.isNotBlank() }.joinToString("\n")
}

private val SCRIPT_RE = Regex("<script\\b[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE)
private val STYLE_RE = Regex("<style\\b[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE)
private val COMMENT_RE = Regex("<!--[\\s\\S]*?-->")
private val BR_RE = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
private val LI_RE = Regex("<li\\b[^>]*>", RegexOption.IGNORE_CASE)
private val BLOCK_RE = Regex("</?(p|div|h[1-6]|tr|li|ul|ol|table|thead|tbody|section|article|header|footer|nav|aside|main|pre|blockquote)[^>]*>", RegexOption.IGNORE_CASE)
private val TAG_RE = Regex("<[^>]+>")
