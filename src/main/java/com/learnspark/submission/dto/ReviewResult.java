package com.learnspark.submission.dto;

import java.util.Map;

/**
 * AI 审核结果（内部使用，Mock 与真实 DeepSeek 审核统一此结构）。
 *
 * @param passed      是否通过
 * @param score       评分 1-10
 * @param feedback    评语
 * @param model       审核模型名称（mock / deepseek-chat 等）
 * @param rawResponse AI 原始响应（用于调试与存档）
 */
public record ReviewResult(boolean passed, int score, String feedback, String model,
                           Map<String, Object> rawResponse) {
}
