package com.learnspark.submission.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnspark.ai.dto.DeepSeekConfig;
import com.learnspark.ai.service.AiConfigService;
import com.learnspark.common.exception.BusinessException;
import com.learnspark.common.exception.ErrorCode;
import com.learnspark.plan.entity.Task;
import com.learnspark.submission.dto.ReviewResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * DeepSeek AI 审核服务。
 *
 * <p>使用用户配置的 DeepSeek API Key 调用 chat/completions 接口，
 * Prompt = 任务描述 + 验证标准 + 学员提交内容，要求模型返回 JSON 审核结果。
 *
 * <p>异常处理策略：
 * <ul>
 *   <li>未配置 Key / 本地模式 → 返回 null，由调用方降级到 Mock</li>
 *   <li>Key 无效（401）→ 抛 AI_KEY_INVALID，提示用户修正配置</li>
 *   <li>超时 / 限流 / 其他错误 → 返回 null，降级到 Mock</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeepSeekReviewService {

    private final AiConfigService aiConfigService;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
            你是一位严谨而鼓励的学习任务审核员。根据任务描述、验证标准和学员提交的内容，评估该任务是否通过。
            请严格返回 JSON 格式：{"passed": boolean, "score": 1-10的整数, "feedback": "中文评语，先肯定优点，再指出改进建议"}
            评分标准：9-10优秀，7-8良好，5-6一般达标，3-4较差，1-2不合格。passed为true当且仅当score>=6。""";

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    /**
     * 尝试用 DeepSeek 审核任务提交。
     *
     * @param userId            用户 ID
     * @param task              任务实体
     * @param submissionContent 提交内容
     * @return 审核结果，或 null（未配置 Key 或调用失败需降级）
     * @throws BusinessException AI_KEY_INVALID 当 API Key 无效时
     */
    public ReviewResult review(String userId, Task task, String submissionContent) {
        DeepSeekConfig config = aiConfigService.getDeepSeekConfig(userId);
        if (config == null) {
            log.debug("用户未配置 DeepSeek Key，将使用 Mock 审核: userId={}", userId);
            return null;
        }

        try {
            String userPrompt = buildUserPrompt(task, submissionContent);
            String aiContent = callDeepSeekApi(config, userPrompt);
            ReviewResult result = parseReviewResult(aiContent, config.model());
            log.info("DeepSeek 审核完成: userId={}, taskId={}, passed={}, score={}",
                    userId, task.getId(), result.passed(), result.score());
            return result;
        } catch (BusinessException e) {
            throw e; // AI_KEY_INVALID 向上抛
        } catch (Exception e) {
            log.warn("DeepSeek 调用失败，将降级到 Mock: userId={}, error={}", userId, e.getMessage());
            return null;
        }
    }

    /** 构建用户提示词 */
    private String buildUserPrompt(Task task, String content) {
        StringBuilder sb = new StringBuilder();
        sb.append("【任务描述】\n").append(task.getDescription()).append("\n\n");
        if (StringUtils.hasText(task.getVerificationCriteria())) {
            sb.append("【验证标准】\n").append(task.getVerificationCriteria()).append("\n\n");
        } else {
            sb.append("【验证标准】\n（未设置具体标准，请根据任务描述合理评估）\n\n");
        }
        sb.append("【学员提交】\n").append(content);
        return sb.toString();
    }

    /** 调用 DeepSeek chat/completions 接口 */
    private String callDeepSeekApi(DeepSeekConfig config, String userPrompt) {
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
                log.warn("DeepSeek API Key 无效（401）");
                throw new BusinessException(ErrorCode.AI_KEY_INVALID);
            }
            log.warn("DeepSeek API 客户端错误: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("DeepSeek API 错误: " + e.getStatusCode());
        } catch (RestClientException e) {
            throw new RuntimeException("DeepSeek 网络错误: " + e.getMessage(), e);
        }
    }

    /** 解析 AI 返回的 JSON 审核结果 */
    private ReviewResult parseReviewResult(String aiContent, String model) {
        try {
            String json = extractJson(aiContent);
            JsonNode node = objectMapper.readTree(json);

            boolean passed = node.has("passed") && node.get("passed").asBoolean();
            int score = node.has("score") ? node.get("score").asInt() : 7;
            // 限制 score 在 1-10 范围
            score = Math.max(1, Math.min(10, score));
            String feedback = node.has("feedback") ? node.get("feedback").asText() : "AI 审核完成";

            Map<String, Object> rawResponse = Map.of("content", aiContent);
            return new ReviewResult(passed, score, feedback, model, rawResponse);
        } catch (Exception e) {
            log.warn("解析 DeepSeek 审核结果失败: content={}, error={}", aiContent, e.getMessage());
            throw new RuntimeException("AI 响应解析失败", e);
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
