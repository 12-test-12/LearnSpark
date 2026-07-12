package com.learnspark.knowledge.dto;

import com.learnspark.knowledge.entity.KnowledgeEntry;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识条目响应（列表 & 详情）。
 */
@Builder
public record KnowledgeEntryResponse(
        String id,
        String title,
        String summary,
        String content,
        String contentMd,
        String sourceType,
        String filePath,
        String mimeType,
        List<String> tags,
        Integer wordCount,
        String parseStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static KnowledgeEntryResponse from(KnowledgeEntry entry) {
        return from(entry, false);
    }

    /**
     * @param includeContent 是否包含正文（详情页 true，列表页 false）
     */
    public static KnowledgeEntryResponse from(KnowledgeEntry entry, boolean includeContent) {
        return KnowledgeEntryResponse.builder()
                .id(entry.getId())
                .title(entry.getTitle())
                .summary(entry.getSummary())
                .content(includeContent ? entry.getContent() : null)
                .contentMd(includeContent ? entry.getContentMd() : null)
                .sourceType(entry.getSourceType())
                .filePath(entry.getFilePath())
                .mimeType(entry.getMimeType())
                .tags(entry.getTags())
                .wordCount(entry.getWordCount())
                .parseStatus(entry.getParseStatus())
                .createdAt(entry.getCreatedAt())
                .updatedAt(entry.getUpdatedAt())
                .build();
    }
}
