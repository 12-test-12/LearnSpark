package com.learnspark.ai.dto;

import com.learnspark.ai.entity.UserAiConfig;
import com.learnspark.common.util.AesCryptoUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * AI 配置响应体（Key 已脱敏）。
 *
 * <pre>
 * GET /api/v1/user/ai-config
 * {
 *   "deepseekApiKey": "****abcd",
 *   "hasDeepseekKey": true,
 *   "searchApiKey": "",
 *   "hasSearchKey": false,
 *   "deepseekBaseUrl": "https://api.deepseek.com/v1",
 *   "deepseekModel": "deepseek-chat",
 *   "searchProvider": "bing",
 *   "localMode": false,
 *   "embeddingModel": "bge-large-zh"
 * }
 * </pre>
 */
@Data
@Builder
@AllArgsConstructor
public class AiConfigResponse {

    private String deepseekApiKey;
    private boolean hasDeepseekKey;

    private String searchApiKey;
    private boolean hasSearchKey;

    private String deepseekBaseUrl;
    private String deepseekModel;
    private String searchProvider;
    private Boolean localMode;
    private String embeddingModel;

    /**
     * 从实体构造响应，Key 解密后脱敏（仅显示后 4 位）。
     *
     * @param config 实体（可能为 null，返回默认配置）
     * @param encryptedKey 解密后的明文 Key（由 Service 传入，避免 Response 层直接依赖 AesCryptoUtil 实例）
     */
    public static AiConfigResponse from(UserAiConfig config, String decryptedDeepseekKey, String decryptedSearchKey) {
        if (config == null) {
            return AiConfigResponse.builder()
                    .deepseekApiKey("")
                    .hasDeepseekKey(false)
                    .searchApiKey("")
                    .hasSearchKey(false)
                    .deepseekBaseUrl("https://api.deepseek.com/v1")
                    .deepseekModel("deepseek-chat")
                    .searchProvider("bing")
                    .localMode(false)
                    .embeddingModel("bge-large-zh")
                    .build();
        }
        return AiConfigResponse.builder()
                .deepseekApiKey(AesCryptoUtil.mask(decryptedDeepseekKey))
                .hasDeepseekKey(decryptedDeepseekKey != null && !decryptedDeepseekKey.isEmpty())
                .searchApiKey(AesCryptoUtil.mask(decryptedSearchKey))
                .hasSearchKey(decryptedSearchKey != null && !decryptedSearchKey.isEmpty())
                .deepseekBaseUrl(config.getDeepseekBaseUrl())
                .deepseekModel(config.getDeepseekModel())
                .searchProvider(config.getSearchProvider())
                .localMode(config.getLocalMode())
                .embeddingModel(config.getEmbeddingModel())
                .build();
    }
}
