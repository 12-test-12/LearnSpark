package com.learnspark.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * AI 配置测试响应体。
 *
 * @param success   是否通过
 * @param message   描述信息（成功或失败原因）
 * @param latencyMs 耗时（毫秒）
 */
@Data
@Builder
@AllArgsConstructor
public class AiConfigTestResponse {

    private boolean success;
    private String message;
    private long latencyMs;
}
