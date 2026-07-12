package com.learnspark.knowledge.service;

import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.ast.TextCollectingVisitor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown / 纯文本解析服务。
 *
 * <p>使用 Flexmark 解析 Markdown，提取：
 * <ul>
 *   <li>标题（第一个 H1/H2，或文件名）</li>
 *   <li>纯文本正文（去除 Markdown 语法）</li>
 *   <li>{@code [[双向链接]]} 文本 → 标签 + 知识链接</li>
 *   <li>字数统计</li>
 * </ul>
 */
@Slf4j
@Service
public class MarkdownParserService {

    private static final Pattern WIKILINK_PATTERN = Pattern.compile("\\[\\[([^\\]]+)\\]\\]");

    private final Parser parser;

    public MarkdownParserService() {
        this.parser = Parser.builder().build();
    }

    private static final String BOM = "\uFEFF";

    /**
     * 解析 Markdown 文本，返回结构化结果。
     */
    public ParseResult parseMarkdown(String markdownText, String fallbackTitle) {
        // 剥离 UTF-8 BOM（Windows 编辑器常带 BOM，会导致首行 # 不被识别为标题）
        if (markdownText != null && markdownText.startsWith(BOM)) {
            markdownText = markdownText.substring(1);
        }
        Node document = parser.parse(markdownText);

        // 提取标题：第一个 H1/H2，否则用 fallbackTitle
        String title = extractFirstHeading(document);
        if (title == null || title.isBlank()) {
            title = fallbackTitle;
        }

        // 提取纯文本
        TextCollectingVisitor textCollector = new TextCollectingVisitor();
        String plainText = textCollector.collectAndGetText(document);
        if (plainText == null || plainText.isBlank()) {
            plainText = markdownText;
        }

        // 提取 [[双向链接]]
        Set<String> links = extractWikilinks(markdownText);

        // 字数统计（中文按字符计，去除空白）
        int wordCount = countWords(plainText);

        return new ParseResult(title, plainText, markdownText, new ArrayList<>(links), wordCount);
    }

    /**
     * 解析纯文本文件（.txt），不做 Markdown 语法处理。
     */
    public ParseResult parsePlainText(String text, String fallbackTitle) {
        // 剥离 UTF-8 BOM
        if (text != null && text.startsWith(BOM)) {
            text = text.substring(1);
        }
        // 提取第一行作为标题
        String title = fallbackTitle;
        if (text != null && !text.isBlank()) {
            String firstLine = text.lines().findFirst().orElse("").trim();
            if (!firstLine.isEmpty() && firstLine.length() <= 200) {
                title = firstLine;
            }
        }

        // 提取 [[双向链接]]
        Set<String> links = extractWikilinks(text);

        int wordCount = countWords(text);

        return new ParseResult(title, text, null, new ArrayList<>(links), wordCount);
    }

    private String extractFirstHeading(Node document) {
        for (Node child = document.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof Heading heading && heading.getLevel() <= 2) {
                // 使用 TextCollectingVisitor 可靠地提取标题文本（兼容 Flexmark 各版本）
                TextCollectingVisitor visitor = new TextCollectingVisitor();
                String text = visitor.collectAndGetText(heading);
                log.debug("发现标题: level={}, text='{}'", heading.getLevel(), text);
                if (text != null && !text.trim().isEmpty()) {
                    return text.trim();
                }
            }
        }
        return null;
    }

    /**
     * 从文本中提取 [[双向链接]] 文本列表。
     *
     * <p>供 KnowledgeService 查询笔记出链时复用，保证 wikilink 解析逻辑 SSOT。
     */
    public Set<String> extractWikilinks(String text) {
        Set<String> links = new LinkedHashSet<>();
        if (text == null) {
            return links;
        }
        Matcher matcher = WIKILINK_PATTERN.matcher(text);
        while (matcher.find()) {
            String linkText = matcher.group(1).trim();
            if (!linkText.isEmpty()) {
                links.add(linkText);
            }
        }
        return links;
    }

    /**
     * 字数统计：去除所有空白字符后的长度（适用于中英文混合）。
     */
    private int countWords(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.replaceAll("\\s+", "").length();
    }

    /**
     * 解析结果。
     *
     * @param title    标题
     * @param content  纯文本正文
     * @param contentMd Markdown 原文（.txt 文件为 null）
     * @param tags     从 [[链接]] 提取的标签列表
     * @param wordCount 字数
     */
    public record ParseResult(String title, String content, String contentMd,
                              List<String> tags, int wordCount) {
    }
}
