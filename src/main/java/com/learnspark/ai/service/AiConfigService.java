package com.learnspark.ai.service;

import com.learnspark.ai.dto.AiConfigRequest;
import com.learnspark.ai.dto.AiConfigResponse;
import com.learnspark.ai.dto.AiConfigTestRequest;
import com.learnspark.ai.dto.AiConfigTestResponse;
import com.learnspark.ai.dto.DeepSeekConfig;
import com.learnspark.ai.dto.SearchConfig;
import com.learnspark.ai.entity.UserAiConfig;
import com.learnspark.ai.repository.UserAiConfigRepository;
import com.learnspark.common.util.AesCryptoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * AI 配置服务：查询与更新用户 AI 配置。
 *
 * <p>Key 存储流程：localMode=false 时明文 Key 经 AES-256-GCM 加密后存入 DB；
 * localMode=true 时不存储 Key（清空已有值），仅在客户端使用。
 * 查询时解密 Key 并脱敏（仅显示后 4 位）后返回。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiConfigService {

    private final UserAiConfigRepository configRepository;
    private final AesCryptoUtil aesCryptoUtil;

    /**
     * 获取用户 AI 配置（Key 脱敏）。
     *
     * @param userId 用户 ID
     * @return 配置响应（不存在则返回默认值）
     */
    @Transactional(readOnly = true)
    public AiConfigResponse getConfig(String userId) {
        UserAiConfig config = configRepository.findById(userId).orElse(null);
        String deepseekKey = decryptSafely(config != null ? config.getDeepseekApiKeyEncrypted() : null);
        String searchKey = decryptSafely(config != null ? config.getSearchApiKeyEncrypted() : null);
        return AiConfigResponse.from(config, deepseekKey, searchKey);
    }

    /**
     * 更新用户 AI 配置。
     *
     * @param userId  用户 ID
     * @param request 配置请求
     * @return 更新后的配置（Key 脱敏）
     */
    @Transactional
    public AiConfigResponse saveConfig(String userId, AiConfigRequest request) {
        UserAiConfig config = configRepository.findById(userId).orElseGet(() -> {
            UserAiConfig newConfig = new UserAiConfig();
            newConfig.setUserId(userId);
            return newConfig;
        });

        // 更新非 Key 字段
        if (StringUtils.hasText(request.getDeepseekBaseUrl())) {
            config.setDeepseekBaseUrl(request.getDeepseekBaseUrl());
        }
        if (StringUtils.hasText(request.getDeepseekModel())) {
            config.setDeepseekModel(request.getDeepseekModel());
        }
        if (StringUtils.hasText(request.getSearchProvider())) {
            config.setSearchProvider(request.getSearchProvider());
        }
        if (StringUtils.hasText(request.getEmbeddingModel())) {
            config.setEmbeddingModel(request.getEmbeddingModel());
        }
        if (request.getLocalMode() != null) {
            config.setLocalMode(request.getLocalMode());
        }

        // 处理 Key 存储
        boolean localMode = Boolean.TRUE.equals(config.getLocalMode());
        if (localMode) {
            // 本地模式：不存储 Key
            config.setDeepseekApiKeyEncrypted(null);
            config.setSearchApiKeyEncrypted(null);
        } else {
            // 非本地模式：有新 Key 则加密存储，无则保留
            if (StringUtils.hasText(request.getDeepseekApiKey())) {
                config.setDeepseekApiKeyEncrypted(aesCryptoUtil.encrypt(request.getDeepseekApiKey()));
            }
            if (StringUtils.hasText(request.getSearchApiKey())) {
                config.setSearchApiKeyEncrypted(aesCryptoUtil.encrypt(request.getSearchApiKey()));
            }
        }

        config = configRepository.save(config);
        log.info("AI 配置已更新: userId={}, localMode={}", userId, localMode);

        String deepseekKey = decryptSafely(config.getDeepseekApiKeyEncrypted());
        String searchKey = decryptSafely(config.getSearchApiKeyEncrypted());
        return AiConfigResponse.from(config, deepseekKey, searchKey);
    }

    /**
     * 获取用户 DeepSeek 调用配置（含解密后的 API Key）。
     *
     * @param userId 用户 ID
     * @return DeepSeek 配置，未配置 Key 或本地模式时返回 null
     */
    @Transactional(readOnly = true)
    public DeepSeekConfig getDeepSeekConfig(String userId) {
        UserAiConfig config = configRepository.findById(userId).orElse(null);
        if (config == null || Boolean.TRUE.equals(config.getLocalMode())) {
            return null;
        }
        String apiKey = decryptSafely(config.getDeepseekApiKeyEncrypted());
        if (!StringUtils.hasText(apiKey)) {
            return null;
        }
        String baseUrl = StringUtils.hasText(config.getDeepseekBaseUrl())
                ? config.getDeepseekBaseUrl()
                : "https://api.deepseek.com/v1";
        String model = StringUtils.hasText(config.getDeepseekModel())
                ? config.getDeepseekModel()
                : "deepseek-chat";
        return new DeepSeekConfig(apiKey, baseUrl, model);
    }

    /**
     * 获取用户搜索引擎调用配置（含解密后的 API Key）。
     *
     * @param userId 用户 ID
     * @return 搜索配置，未配置 Key 或本地模式时返回 null
     */
    @Transactional(readOnly = true)
    public SearchConfig getSearchConfig(String userId) {
        UserAiConfig config = configRepository.findById(userId).orElse(null);
        if (config == null || Boolean.TRUE.equals(config.getLocalMode())) {
            return null;
        }
        String apiKey = decryptSafely(config.getSearchApiKeyEncrypted());
        if (!StringUtils.hasText(apiKey)) {
            return null;
        }
        String provider = StringUtils.hasText(config.getSearchProvider())
                ? config.getSearchProvider()
                : "bing";
        return new SearchConfig(apiKey, provider);
    }

    /** 安全解密：null/空返回 null，异常时记录日志并返回 null */
    private String decryptSafely(String encrypted) {
        if (!StringUtils.hasText(encrypted)) {
            return null;
        }
        try {
            return aesCryptoUtil.decrypt(encrypted);
        } catch (Exception ex) {
            log.warn("Key 解密失败，可能密钥已变更: {}", ex.getMessage());
            return null;
        }
    }

    private static final int TEST_CONNECT_TIMEOUT_MS = 8_000;
    private static final int TEST_READ_TIMEOUT_MS = 15_000;

    /**
     * 测试 AI 配置连通性（不落库）。
     *
     * <p>provider=deepseek 时，发起一次最小 chat/completions 请求验证 Key；
     * provider=search 时返回待实现提示（阶段二的 Bing 封装完成后再支持）。
     *
     * @param userId  用户 ID（用于回查已存储的 Key）
     * @param request 测试请求（provider 必填；apiKey 可选，为空则用已存储的）
     * @return 测试结果
     */
    public AiConfigTestResponse testConfig(String userId, AiConfigTestRequest request) {
        long start = System.currentTimeMillis();
        String provider = request.getProvider();

        if ("search".equalsIgnoreCase(provider)) {
            return AiConfigTestResponse.builder()
                    .success(false)
                    .message("搜索 API 连通性测试将在阶段二（Bing 封装）实现")
                    .latencyMs(System.currentTimeMillis() - start)
                    .build();
        }

        if (!"deepseek".equalsIgnoreCase(provider)) {
            return AiConfigTestResponse.builder()
                    .success(false)
                    .message("不支持的 provider: " + provider)
                    .latencyMs(System.currentTimeMillis() - start)
                    .build();
        }

        // 解析测试用 Key：请求体 > 已存储
        UserAiConfig config = configRepository.findById(userId).orElse(null);
        String apiKey = StringUtils.hasText(request.getApiKey()) ? request.getApiKey() : decryptSafely(config != null ? config.getDeepseekApiKeyEncrypted() : null);
        if (!StringUtils.hasText(apiKey)) {
            return AiConfigTestResponse.builder()
                    .success(false)
                    .message("未配置 DeepSeek API Key，请先填写后再测试")
                    .latencyMs(System.currentTimeMillis() - start)
                    .build();
        }

        // 解析 baseUrl：请求体 > 已存储 > 默认
        String baseUrl = StringUtils.hasText(request.getBaseUrl()) ? request.getBaseUrl()
                : (config != null && StringUtils.hasText(config.getDeepseekBaseUrl()) ? config.getDeepseekBaseUrl()
                        : "https://api.deepseek.com/v1");

        try {
            callDeepSeekForTest(apiKey, baseUrl);
            return AiConfigTestResponse.builder()
                    .success(true)
                    .message("DeepSeek 连接成功，API Key 有效")
                    .latencyMs(System.currentTimeMillis() - start)
                    .build();
        } catch (HttpClientErrorException e) {
            String msg;
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                msg = "API Key 无效（401），请检查后重试";
            } else if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                msg = "请求被限流（429），Key 有效但稍后再试";
            } else {
                msg = "DeepSeek 返回错误: " + e.getStatusCode();
            }
            log.warn("DeepSeek 测试失败: userId={}, status={}", userId, e.getStatusCode());
            return AiConfigTestResponse.builder()
                    .success(false)
                    .message(msg)
                    .latencyMs(System.currentTimeMillis() - start)
                    .build();
        } catch (RestClientException e) {
            log.warn("DeepSeek 测试网络异常: userId={}, error={}", userId, e.getMessage());
            return AiConfigTestResponse.builder()
                    .success(false)
                    .message("网络错误: " + e.getMessage())
                    .latencyMs(System.currentTimeMillis() - start)
                    .build();
        } catch (Exception e) {
            log.warn("DeepSeek 测试异常: userId={}, error={}", userId, e.getMessage());
            return AiConfigTestResponse.builder()
                    .success(false)
                    .message("测试失败: " + e.getMessage())
                    .latencyMs(System.currentTimeMillis() - start)
                    .build();
        }
    }

    /**
     * 发起一次最小 chat/completions 请求以验证 Key。
     * 使用固定 prompt "ping"，max_tokens=1，最小化开销。
     */
    private void callDeepSeekForTest(String apiKey, String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TEST_CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(TEST_READ_TIMEOUT_MS);

        RestClient client = RestClient.builder()
                .requestFactory(factory)
                .build();

        Map<String, Object> requestBody = Map.of(
                "model", "deepseek-chat",
                "messages", List.of(Map.of("role", "user", "content", "ping")),
                "max_tokens", 1,
                "temperature", 0
        );

        client.post()
                .uri(baseUrl + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(requestBody)
                .retrieve()
                .body(String.class);
    }
}
