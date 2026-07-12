package com.learnspark.knowledge.dto;

import com.learnspark.knowledge.entity.KnowledgeEntry;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件上传并解析后的响应。
 */
@Builder
public record UploadResponse(
        String entryId,
        String title,
        String filePath,
        String sourceType,
        int wordCount,
        List<String> tags,
        List<String> links,
        LocalDateTime createdAt
) {

    public static UploadResponse from(KnowledgeEntry entry, List<String> links) {
        return UploadResponse.builder()
                .entryId(entry.getId())
                .title(entry.getTitle())
                .filePath(entry.getFilePath())
                .sourceType(entry.getSourceType())
                .wordCount(entry.getWordCount() != null ? entry.getWordCount() : 0)
                .tags(entry.getTags())
                .links(links)
                .createdAt(entry.getCreatedAt())
                .build();
    }
}
