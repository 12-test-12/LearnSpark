package com.learnspark.ai.dto;

/**
 * DeepSeek 调用所需配置（解密后的明文）。
 *
 * @param apiKey  解密后的 API Key
 * @param baseUrl API 基础 URL
 * @param model   模型名称
 */
public record DeepSeekConfig(String apiKey, String baseUrl, String model) {
}
