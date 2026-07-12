package com.learnspark.ai.search.dto;

/**
 * 网络搜索结果项。
 *
 * @param title   结果标题
 * @param url     结果链接
 * @param snippet 摘要（已截断）
 */
public record SearchResultItem(String title, String url, String snippet) {
}
