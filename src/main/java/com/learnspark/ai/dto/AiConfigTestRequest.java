package com.learnspark.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AI 配置测试请求体。
 *
 * <pre>
 * POST /api/v1/user/ai-config/test
 * { "provider": "deepseek", "apiKey": "sk-xxx（可选，不传则用已存储的）", "baseUrl": "https://...（可选）" }
 * </pre>
 *
 * <p>provider 取值：deepseek / search。search 的连通性测试将在阶段二（Bing 封装）实现。
 */
@Data
public class AiConfigTestRequest {

    /** 测试目标：deepseek 或 search */
    @NotBlank(message = "provider 不能为空")
    private String provider;

    /** 临时测试用 Key（不落库）；为空时使用已存储的 Key */
    private String apiKey;

    /** 临时测试用 baseUrl；为空时使用已存储或默认值 */
    private String baseUrl;
}
