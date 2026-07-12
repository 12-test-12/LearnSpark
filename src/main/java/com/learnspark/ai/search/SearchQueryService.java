package com.learnspark.ai.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnspark.ai.dto.DeepSeekConfig;
import com.learnspark.ai.service.AiConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 搜索词生成服务（任务 2.1.2）。
 *
 * <p>调用 DeepSeek 根据学习目标和资料摘要生成 3-5 个搜索关键词，
 * 供 {@link BingSearchService} 检索最新资料。
 * 失败时降级返回空列表（不抛异常），保证路线生成主流程不被阻断。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchQueryService {

    private final AiConfigService aiConfigService;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
            你是一位学习规划专家。根据用户的学习目标和已有资料摘要，生成 3-5 个精准的网络搜索关键词，
            用于检索最新的学习资源、教程和实践指南。
            请严格返回 JSON 格式：{"queries": ["关键词1", "关键词2", "关键词3"]}
            关键词应覆盖：基础概念、实践教程、最新进展、常见问题。""";

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 20_000;
    private static final int MAX_QUERIES = 5;
    private static final int SUMMARY_MAX_LENGTH = 2000;

    /**
     * 生成搜索关键词。
     *
     * @param userId          用户 ID
     * @param goal            学习目标
     * @param materialSummary 资料摘要（将截断至 2000 字）
     * @return 搜索关键词列表，失败返回空列表
     */
    public List<String> generateQueries(String userId, String goal, String materialSummary) {
        DeepSeekConfig config = aiConfigService.getDeepSeekConfig(userId);
        if (config == null) {
            log.debug("未配置 DeepSeek Key，跳过搜索词生成: userId={}", userId);
            return List.of();
        }

        try {
            String userPrompt = buildUserPrompt(goal, materialSummary);
            String aiContent = callDeepSeek(config, userPrompt);
            return parseQueries(aiContent);
        } catch (Exception e) {
            log.warn("搜索词生成失败，降级返回空列表: userId={}, error={}", userId, e.getMessage());
            return List.of();
        }
    }

    /** 构建用户提示词 */
    private String buildUserPrompt(String goal, String materialSummary) {
        StringBuilder sb = new StringBuilder();
        sb.append("【学习目标】\n").append(StringUtils.hasText(goal) ? goal : "（未指定）").append("\n\n");
        sb.append("【资料摘要】\n");
        if (StringUtils.hasText(materialSummary)) {
            String summary = materialSummary.length() > SUMMARY_MAX_LENGTH
                    ? materialSummary.substring(0, SUMMARY_MAX_LENGTH) + "..."
                    : materialSummary;
            sb.append(summary);
        } else {
            sb.append("（无资料）");
        }
        return sb.toString();
    }

    /** 调用 DeepSeek chat/completions 接口 */
    private String callDeepSeek(DeepSeekConfig config, String userPrompt) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);

        RestClient client = RestClient.builder()
                .requestFactory(factory)
                .build();

        Map<String, Object> requestBody = Map.of(
                "model", config.model(),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "response_format", Map.of("type", "json_object"),
                "temperature", 0.3
        );

        try {
            JsonNode response = client.post()
                    .uri(config.baseUrl() + "/chat/completions")
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) {
                throw new RuntimeException("DeepSeek 返回空响应");
            }

            String content = response.path("choices").path(0).path("message").path("content").asText();
            if (!StringUtils.hasText(content)) {
                throw new RuntimeException("DeepSeek 响应中未找到 content");
            }
            return content;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                log.warn("DeepSeek API Key 无效（401），跳过搜索词生成");
            } else {
                log.warn("DeepSeek API 错误: status={}", e.getStatusCode());
            }
            throw new RuntimeException("DeepSeek 调用失败: " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            throw new RuntimeException("DeepSeek 网络错误: " + e.getMessage(), e);
        }
    }

    /** 解析 AI 返回的 JSON 查询列表 */
    private List<String> parseQueries(String aiContent) {
        try {
            String json = extractJson(aiContent);
            JsonNode node = objectMapper.readTree(json);
            JsonNode queriesNode = node.path("queries");

            if (!queriesNode.isArray() || queriesNode.isEmpty()) {
                return List.of();
            }

            List<String> queries = new ArrayList<>();
            for (JsonNode q : queriesNode) {
                String query = q.asText("").trim();
                if (StringUtils.hasText(query)) {
                    queries.add(query);
                }
                if (queries.size() >= MAX_QUERIES) {
                    break;
                }
            }
            return queries;
        } catch (Exception e) {
            log.warn("解析搜索词 JSON 失败: content={}, error={}", aiContent, e.getMessage());
            return List.of();
        }
    }

    /** 提取 JSON 字符串（去除可能的 markdown 代码块包裹） */
    private String extractJson(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }
}
