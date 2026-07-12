package com.learnspark.knowledge.dto;

import java.util.List;

/**
 * 知识条目双向链接响应。
 *
 * <p>包含两类链接：
 * <ul>
 *   <li>outgoing：本笔记中 {@code [[链接]]} 指向的目标（含是否存在）</li>
 *   <li>incoming：其他笔记通过 {@code [[本笔记标题]]} 指向本笔记的来源</li>
 * </ul>
 */
public record KnowledgeLinksResponse(
        List<LinkItem> outgoing,
        List<LinkItem> incoming
) {

    /**
     * 单条链接项。
     *
     * @param entryId  目标/来源条目 ID（不存在时为 null）
     * @param title    条目标题（不存在时用 linkText 占位）
     * @param linkText {@code [[链接文本]]}
     * @param exists   目标条目是否存在
     */
    public record LinkItem(
            String entryId,
            String title,
            String linkText,
            boolean exists
    ) {}
}
