package com.learnspark.ai.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 配置更新请求体。
 *
 * <pre>
 * PUT /api/v1/user/ai-config
 * {
 *   "deepseekApiKey": "sk-xxxxx",
 *   "searchApiKey": "yyyyy",
 *   "deepseekBaseUrl": "https://api.deepseek.com/v1",
 *   "deepseekModel": "deepseek-chat",
 *   "searchProvider": "bing",
 *   "localMode": false,
 *   "embeddingModel": "bge-large-zh"
 * }
 * </pre>
 *
 * <p>Key 字段为 null 或空时保留已有值；localMode=true 时清空 Key。
 */
@Data
public class AiConfigRequest {

    /** DeepSeek API Key（明文，服务端加密存储） */
    private String deepseekApiKey;

    /** 搜索 API Key（明文，服务端加密存储） */
    private String searchApiKey;

    @Size(max = 255, message = "baseUrl 最长 255 字符")
    private String deepseekBaseUrl;

    @Size(max = 100, message = "model 最长 100 字符")
    private String deepseekModel;

    @Size(max = 50, message = "searchProvider 最长 50 字符")
    private String searchProvider;

    /** 本地模式：true 时不存储 Key */
    private Boolean localMode;

    @Size(max = 100, message = "embeddingModel 最长 100 字符")
    private String embeddingModel;
}
