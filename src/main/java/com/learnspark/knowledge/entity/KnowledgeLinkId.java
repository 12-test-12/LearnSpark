package com.learnspark.knowledge.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * knowledge_links 表复合主键（source_entry_id + target_entry_id）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeLinkId implements Serializable {
    private String sourceEntryId;
    private String targetEntryId;
}
