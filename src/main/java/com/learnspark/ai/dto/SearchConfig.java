package com.learnspark.ai.dto;

/**
 * 搜索引擎调用配置（含解密后的 API Key）。
 *
 * @param apiKey   解密后的搜索 API Key
 * @param provider 搜索引擎提供商（如 "bing"）
 */
public record SearchConfig(String apiKey, String provider) {
}
