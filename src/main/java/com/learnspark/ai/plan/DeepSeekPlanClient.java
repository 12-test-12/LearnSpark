package com.learnspark.ai.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.learnspark.ai.dto.DeepSeekConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * DeepSeek 学习路线生成客户端（任务 2.3.1 调用层）。
 *
 * <p>独立于 {@link com.learnspark.submission.service.DeepSeekReviewService}，
 * 路线生成 prompt 更长、响应更慢，因此 readTimeout 设为 60s。
 * 调用成功返回 AI 响应的原始 content 字符串，由 {@link PlanGenerationService} 解析。
 *
 * <p>异常处理：
 * <ul>
 *   <li>401 → 抛 {@link com.learnspark.common.exception.ErrorCode#AI_KEY_INVALID}</li>
 *   <li>其它错误 → 抛 RuntimeException（由上层包装为 AI_GENERATE_FAILED）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeepSeekPlanClient {

    private static final String SYSTEM_PROMPT = """
            你是一位专业的学习规划专家。根据用户的学习目标、每日学习时长、目标天数和参考资料，生成一份结构化的学习路线。

            请严格返回以下 JSON 格式：
            {
              "phases": [
                {
                  "name": "阶段名称",
                  "objective": "该阶段的学习目标",
                  "tasks": [
                    {
                      "day_number": 1,
                      "title": "任务标题",
                      "description": "详细的任务描述，包括学习内容和实践要求",
                      "verification_criteria": "验证标准，描述如何判断该任务完成"
                    }
                  ]
                }
              ]
            }

            要求：
            1. 根据目标天数合理分配任务，每个任务对应一天（day_number 从 1 开始递增）
            2. 阶段数量根据学习内容复杂度决定，通常 3-6 个阶段
            3. 每个阶段包含若干任务，任务总数应大致覆盖目标天数
            4. 任务描述要具体可执行，验证标准要明确可衡量
            5. 参考资料中的内容应融入任务设计
            6. 如果提供了网络搜索结果，结合最新资源丰富任务内容
            7. 只返回 JSON，不要包含任何其它文字说明""";

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 60_000;

    /**
     * 调用 DeepSeek 生成学习路线。
     *
     * @param config     DeepSeek 配置（含 API Key）
     * @param userPrompt 用户提示词（目标 + 资料 + 搜索摘要）
     * @return AI 响应的原始 content 字符串（JSON 格式）
     * @throws com.learnspark.common.exception.BusinessException AI_KEY_INVALID 当 API Key 无效时
     * @throws RuntimeException                                   其它调用失败
     */
    public String generatePlan(DeepSeekConfig config, String userPrompt) {
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
                "temperature", 0.4
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
            log.info("DeepSeek 路线生成完成: model={}, contentLength={}", config.model(), content.length());
            return content;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                log.warn("DeepSeek API Key 无效（401）");
                throw new com.learnspark.common.exception.BusinessException(
                        com.learnspark.common.exception.ErrorCode.AI_KEY_INVALID);
            }
            log.warn("DeepSeek API 错误: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("DeepSeek API 错误: " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            throw new RuntimeException("DeepSeek 网络错误: " + e.getMessage(), e);
        }
    }
}
