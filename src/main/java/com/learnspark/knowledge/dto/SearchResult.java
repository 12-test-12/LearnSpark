package com.learnspark.knowledge.dto;

import com.learnspark.knowledge.entity.KnowledgeEntry;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库搜索结果项（含高亮摘要）。
 */
@Builder
public record SearchResult(
        String id,
        String title,
        String summary,
        String highlightedSummary,
        String sourceType,
        List<String> tags,
        Integer wordCount,
        LocalDateTime createdAt
) {

    public static SearchResult from(KnowledgeEntry entry, String highlightedSummary) {
        return SearchResult.builder()
                .id(entry.getId())
                .title(entry.getTitle())
                .summary(entry.getSummary())
                .highlightedSummary(highlightedSummary)
                .sourceType(entry.getSourceType())
                .tags(entry.getTags())
                .wordCount(entry.getWordCount())
                .createdAt(entry.getCreatedAt())
                .build();
    }
}
