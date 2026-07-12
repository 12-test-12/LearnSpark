package com.learnspark.knowledge.service;

import com.learnspark.common.exception.BusinessException;
import com.learnspark.common.exception.ErrorCode;
import com.learnspark.common.result.PageResult;
import com.learnspark.knowledge.dto.KnowledgeEntryResponse;
import com.learnspark.knowledge.dto.KnowledgeLinksResponse;
import com.learnspark.knowledge.dto.SearchResult;
import com.learnspark.knowledge.dto.UploadResponse;
import com.learnspark.knowledge.entity.KnowledgeEntry;
import com.learnspark.knowledge.entity.KnowledgeLink;
import com.learnspark.knowledge.entity.KnowledgeLinkId;
import com.learnspark.knowledge.repository.KnowledgeEntryRepository;
import com.learnspark.knowledge.repository.KnowledgeLinkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识库服务：文件上传 → 解析 → 入库 → 建立双向链接。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final FileStorageService fileStorageService;
    private final MarkdownParserService markdownParserService;
    private final KnowledgeEntryRepository entryRepository;
    private final KnowledgeLinkRepository linkRepository;

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("md", "txt", "markdown");

    /**
     * 上传文件并解析入库。
     *
     * <p>流程：
     * <ol>
     *   <li>校验文件类型（.md / .txt）</li>
     *   <li>读取文件内容（UTF-8）</li>
     *   <li>上传到文件存储</li>
     *   <li>解析 Markdown / 纯文本，提取标题、正文、[[链接]]、字数</li>
     *   <li>写入 knowledge_entries</li>
     *   <li>解析 [[链接]] → 匹配已有条目 → 写入 knowledge_links</li>
     * </ol>
     */
    @Transactional
    public UploadResponse upload(String userId, MultipartFile file) {
        // 1. 校验
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        String ext = extractExtension(originalFilename);
        if (!SUPPORTED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(ErrorCode.KB_UNSUPPORTED_TYPE,
                    "仅支持 .md / .txt 文件，当前: " + ext);
        }

        // 2. 读取内容（字节只读一次，复用于解析和上传）
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            log.error("读取文件内容失败", e);
            throw new BusinessException(ErrorCode.KB_PARSE_FAILED);
        }
        String textContent = new String(fileBytes, StandardCharsets.UTF_8);

        // 3. 上传到存储
        String filePath = fileStorageService.upload(userId, originalFilename, fileBytes,
                file.getContentType());

        // 4. 解析
        String fallbackTitle = stripExtension(originalFilename);
        MarkdownParserService.ParseResult parseResult;
        if ("txt".equals(ext)) {
            parseResult = markdownParserService.parsePlainText(textContent, fallbackTitle);
        } else {
            parseResult = markdownParserService.parseMarkdown(textContent, fallbackTitle);
        }

        // 5. 创建知识条目
        KnowledgeEntry entry = KnowledgeEntry.builder()
                .userId(userId)
                .title(parseResult.title())
                .content(parseResult.content())
                .contentMd(parseResult.contentMd())
                .sourceType("upload")
                .filePath(filePath)
                .mimeType(file.getContentType())
                .tags(parseResult.tags())
                .wordCount(parseResult.wordCount())
                .parseStatus("done")
                .build();
        entry = entryRepository.save(entry);
        log.info("知识条目已创建: id={}, title={}, words={}", entry.getId(), entry.getTitle(), entry.getWordCount());

        // 6. 建立双向链接：出链 + 回扫已有笔记形成反链
        List<String> linkTexts = parseResult.tags();
        createKnowledgeLinks(userId, entry.getId(), linkTexts);
        createBacklinks(userId, entry);

        return UploadResponse.from(entry, linkTexts);
    }

    /**
     * 为 [[链接]] 文本匹配已有知识条目并创建链接记录。
     *
     * <p>匹配规则：同用户下 title 相同的知识条目。未匹配的链接跳过（不创建占位条目）。
     */
    private void createKnowledgeLinks(String userId, String sourceEntryId, List<String> linkTexts) {
        for (String linkText : linkTexts) {
            Optional<KnowledgeEntry> target = entryRepository.findByUserIdAndTitle(userId, linkText);
            if (target.isPresent() && !target.get().getId().equals(sourceEntryId)) {
                KnowledgeLink link = KnowledgeLink.builder()
                        .sourceEntryId(sourceEntryId)
                        .targetEntryId(target.get().getId())
                        .linkText(linkText)
                        .build();
                linkRepository.save(link);
                log.debug("知识链接已创建: {} → {} ({})", sourceEntryId, target.get().getId(), linkText);
            }
        }
    }

    /**
     * 回扫反链：新建条目后，查找已有笔记中引用了本条目标题的 [[链接]]，建立反向链接。
     *
     * <p>场景：用户先写了笔记 A（含 {@code [[React Hooks]]}），后上传笔记 B（标题 "React Hooks"）。
     * 此时需自动建立 A → B 的链接，无需用户回头编辑 A。
     */
    private void createBacklinks(String userId, KnowledgeEntry newEntry) {
        String pattern = "[[" + newEntry.getTitle() + "]]";
        List<KnowledgeEntry> referrers = entryRepository.findEntriesContainingWikilink(userId, pattern);
        for (KnowledgeEntry referrer : referrers) {
            if (referrer.getId().equals(newEntry.getId())) {
                continue; // 不自引用
            }
            saveLinkIfNotExists(referrer.getId(), newEntry.getId(), newEntry.getTitle());
        }
    }

    /** 保存链接记录（去重：同一 source→target 不重复创建） */
    private void saveLinkIfNotExists(String sourceId, String targetId, String linkText) {
        KnowledgeLinkId id = new KnowledgeLinkId(sourceId, targetId);
        if (linkRepository.existsById(id)) {
            return;
        }
        KnowledgeLink link = KnowledgeLink.builder()
                .sourceEntryId(sourceId)
                .targetEntryId(targetId)
                .linkText(linkText)
                .build();
        linkRepository.save(link);
        log.debug("反链已创建: {} → {} ({})", sourceId, targetId, linkText);
    }

    /**
     * 查询知识条目的双向链接（出链 + 反链）。
     *
     * <p>出链：从本笔记正文解析 {@code [[链接]]}，匹配目标条目（未匹配则标记 exists=false）。
     * 反链：查询 knowledge_links 中 target = 本条目的记录，关联来源条目标题。
     */
    @Transactional(readOnly = true)
    public KnowledgeLinksResponse getLinks(String userId, String entryId) {
        KnowledgeEntry entry = getOwnedEntry(userId, entryId);
        List<KnowledgeLinksResponse.LinkItem> outgoing = resolveOutgoingLinks(userId, entry);
        List<KnowledgeLinksResponse.LinkItem> incoming = resolveIncomingLinks(entryId);
        return new KnowledgeLinksResponse(outgoing, incoming);
    }

    /** 解析出链：从正文提取 [[链接]] 文本，逐个匹配目标条目 */
    private List<KnowledgeLinksResponse.LinkItem> resolveOutgoingLinks(String userId, KnowledgeEntry entry) {
        String md = entry.getContentMd() != null ? entry.getContentMd() : entry.getContent();
        Set<String> linkTexts = markdownParserService.extractWikilinks(md);
        return linkTexts.stream()
                .map(text -> resolveLinkItem(userId, text))
                .collect(Collectors.toList());
    }

    /** 将单个 [[链接文本]] 解析为 LinkItem（匹配目标条目） */
    private KnowledgeLinksResponse.LinkItem resolveLinkItem(String userId, String linkText) {
        Optional<KnowledgeEntry> target = entryRepository.findByUserIdAndTitle(userId, linkText);
        if (target.isPresent()) {
            return new KnowledgeLinksResponse.LinkItem(target.get().getId(), target.get().getTitle(), linkText, true);
        }
        return new KnowledgeLinksResponse.LinkItem(null, linkText, linkText, false);
    }

    /** 解析反链：查询指向本条目的链接记录，关联来源条目标题 */
    private List<KnowledgeLinksResponse.LinkItem> resolveIncomingLinks(String entryId) {
        List<KnowledgeLink> backlinks = linkRepository.findByTargetEntryId(entryId);
        return backlinks.stream()
                .map(link -> toIncomingItem(link))
                .collect(Collectors.toList());
    }

    /** 将反链记录转为 LinkItem（关联来源条目标题） */
    private KnowledgeLinksResponse.LinkItem toIncomingItem(KnowledgeLink link) {
        Optional<KnowledgeEntry> source = entryRepository.findById(link.getSourceEntryId());
        String title = source.map(KnowledgeEntry::getTitle).orElse(link.getLinkText());
        return new KnowledgeLinksResponse.LinkItem(link.getSourceEntryId(), title, link.getLinkText(), true);
    }

    /** 获取用户拥有的知识条目（校验归属） */
    private KnowledgeEntry getOwnedEntry(String userId, String entryId) {
        KnowledgeEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.KB_ENTRY_NOT_FOUND));
        if (!entry.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return entry;
    }

    /**
     * 查询单条知识条目详情（含正文）。
     */
    public KnowledgeEntryResponse getEntry(String userId, String entryId) {
        KnowledgeEntry entry = getOwnedEntry(userId, entryId);
        return KnowledgeEntryResponse.from(entry, true);
    }

    /**
     * 查询用户的所有知识条目（不含正文，列表用）。
     */
    public List<KnowledgeEntryResponse> listEntries(String userId) {
        return entryRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(e -> KnowledgeEntryResponse.from(e, false))
                .collect(Collectors.toList());
    }

    /**
     * 软删除知识条目。
     */
    @Transactional
    public void deleteEntry(String userId, String entryId) {
        KnowledgeEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.KB_ENTRY_NOT_FOUND));
        if (!entry.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        // 软删除：设置 deleted_at（@SQLRestriction 会自动过滤）
        entry.setDeletedAt(java.time.LocalDateTime.now());
        entryRepository.save(entry);
        log.info("知识条目已软删除: id={}", entryId);
    }

    /**
     * 全文检索知识条目（MySQL FULLTEXT + ngram 分词器，支持中文）。
     *
     * <p>返回分页结果，每条结果包含高亮摘要：在正文中定位关键字，
     * 截取前后各 ~100 字符的片段，并用 {@code <mark>} 标签包裹关键字。
     *
     * @param userId 当前用户 ID
     * @param query  搜索关键词
     * @param page   页码（从 0 开始）
     * @param size   每页条数
     * @return 分页搜索结果
     */
    public PageResult<SearchResult> search(String userId, String query, int page, int size) {
        if (query == null || query.isBlank()) {
            return PageResult.of(List.of(), 0, page, size);
        }
        String trimmedQuery = query.trim();
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        Page<KnowledgeEntry> entries = entryRepository.searchByFulltext(userId, trimmedQuery, pageable);
        List<SearchResult> results = entries.getContent().stream()
                .map(e -> SearchResult.from(e, buildHighlightedSummary(e.getContent(), trimmedQuery)))
                .collect(Collectors.toList());
        log.info("全文检索: userId={}, query='{}', hits={}", userId, trimmedQuery, entries.getTotalElements());
        return PageResult.of(results, entries.getTotalElements(), page, size);
    }

    /**
     * 构建高亮摘要：在 content 中查找关键字首次出现位置，
     * 截取前后各 ~100 字符的片段，并用 {@code <mark>} 包裹所有出现的关键字。
     *
     * <p>若 content 中未找到关键字（仅标题命中），返回 content 前 200 字符。
     */
    private String buildHighlightedSummary(String content, String query) {
        if (content == null || content.isBlank()) {
            return "";
        }
        // 清理可能残留的 UTF-8 BOM（旧数据可能带 BOM 字符）
        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }
        // 折叠空白便于展示
        String plain = content.replaceAll("\\s+", " ").trim();
        final int RADIUS = 100; // 关键字前后各取 100 字符

        int idx = plain.toLowerCase().indexOf(query.toLowerCase());
        String snippet;
        if (idx >= 0) {
            int start = Math.max(0, idx - RADIUS);
            int end = Math.min(plain.length(), idx + query.length() + RADIUS);
            snippet = plain.substring(start, end);
            if (start > 0) {
                snippet = "..." + snippet;
            }
            if (end < plain.length()) {
                snippet = snippet + "...";
            }
        } else {
            // 正文未命中（仅标题命中），取前 200 字符作为摘要
            snippet = plain.substring(0, Math.min(200, plain.length()));
            if (plain.length() > 200) {
                snippet = snippet + "...";
            }
        }
        return highlightKeyword(snippet, query);
    }

    /**
     * 将片段中所有出现的关键字（大小写不敏感）用 {@code <mark>} 标签包裹。
     */
    private String highlightKeyword(String snippet, String query) {
        if (snippet == null || snippet.isEmpty() || query == null || query.isEmpty()) {
            return snippet;
        }
        // Pattern.quote 转义正则特殊字符；(?i) 大小写不敏感；$0 引用整段匹配
        String escapedQuery = java.util.regex.Pattern.quote(query);
        return snippet.replaceAll("(?i)" + escapedQuery, "<mark>$0</mark>");
    }

    private String extractExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex >= 0 ? filename.substring(dotIndex + 1).toLowerCase() : "";
    }

    private String stripExtension(String filename) {
        if (filename == null) {
            return "未命名";
        }
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
    }
}
