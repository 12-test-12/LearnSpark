package com.learnspark.ai.controller;

import com.learnspark.ai.dto.AiConfigRequest;
import com.learnspark.ai.dto.AiConfigResponse;
import com.learnspark.ai.dto.AiConfigTestRequest;
import com.learnspark.ai.dto.AiConfigTestResponse;
import com.learnspark.ai.service.AiConfigService;
import com.learnspark.common.result.ApiResult;
import com.learnspark.common.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 配置控制器：查询与更新当前用户的 AI 配置。
 *
 * <p>路径前缀 /user/ai-config，完整路径 /api/v1/user/ai-config，需携带 token。
 */
@RestController
@RequestMapping("/user/ai-config")
@RequiredArgsConstructor
public class AiConfigController {

    private final AiConfigService aiConfigService;

    /**
     * 获取当前用户的 AI 配置（Key 脱敏显示后 4 位）。
     *
     * <pre>
     * GET /api/v1/user/ai-config
     * </pre>
     */
    @GetMapping
    public ApiResult<AiConfigResponse> getConfig(@CurrentUser String userId) {
        return ApiResult.success(aiConfigService.getConfig(userId));
    }

    /**
     * 更新当前用户的 AI 配置。
     *
     * <pre>
     * PUT /api/v1/user/ai-config
     * { "deepseekApiKey": "sk-xxx", "localMode": false, ... }
     * </pre>
     */
    @PutMapping
    public ApiResult<AiConfigResponse> saveConfig(@CurrentUser String userId,
                                                   @Valid @RequestBody AiConfigRequest request) {
        return ApiResult.success(aiConfigService.saveConfig(userId, request));
    }

    /**
     * 测试 AI 配置连通性（不落库）。
     *
     * <pre>
     * POST /api/v1/user/ai-config/test
     * { "provider": "deepseek", "apiKey": "sk-xxx（可选）" }
     * </pre>
     */
    @PostMapping("/test")
    public ApiResult<AiConfigTestResponse> testConfig(@CurrentUser String userId,
                                                       @Valid @RequestBody AiConfigTestRequest request) {
        return ApiResult.success(aiConfigService.testConfig(userId, request));
    }
}
