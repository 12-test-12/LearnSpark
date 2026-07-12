package com.learnspark.ai.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnspark.ai.dto.DeepSeekConfig;
import com.learnspark.ai.dto.SearchConfig;
import com.learnspark.ai.plan.dto.GeneratePlanResponse;
import com.learnspark.ai.search.BingSearchService;
import com.learnspark.ai.search.SearchQueryService;
import com.learnspark.ai.search.dto.SearchResultItem;
import com.learnspark.ai.service.AiConfigService;
import com.learnspark.common.exception.BusinessException;
import com.learnspark.common.exception.ErrorCode;
import com.learnspark.knowledge.service.MarkdownParserService;
import com.learnspark.plan.dto.PhaseResponse;
import com.learnspark.plan.dto.TaskResponse;
import com.learnspark.plan.entity.Phase;
import com.learnspark.plan.entity.Project;
import com.learnspark.plan.entity.Task;
import com.learnspark.plan.repository.PhaseRepository;
import com.learnspark.plan.repository.ProjectRepository;
import com.learnspark.plan.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * AI 学习路线生成服务（任务 2.3.1 核心）。
 *
 * <p>编排流程：校验项目 → 获取 AI 配置 → 解析文件 → 可选联网搜索 →
 * 调 DeepSeek 生成路线 → 解析 JSON → 持久化 phases+tasks → 标记项目。
 *
 * <p>整个方法在 {@code @Transactional} 内执行，确保 phases/tasks 写入的原子性。
 * 外部 HTTP 调用（DeepSeek/Bing）不写库，不影响事务一致性。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanGenerationService {

    private final ProjectRepository projectRepository;
    private final PhaseRepository phaseRepository;
    private final TaskRepository taskRepository;
    private final AiConfigService aiConfigService;
    private final MarkdownParserService markdownParserService;
    private final DeepSeekPlanClient deepSeekPlanClient;
    private final BingSearchService bingSearchService;
    private final SearchQueryService searchQueryService;
    private final ObjectMapper objectMapper;

    /** 单文件内容截断长度（字符） */
    private static final int SINGLE_FILE_MAX_LENGTH = 4_000;
    /** 合并后资料总长度上限（字符） */
    private static final int TOTAL_MATERIAL_MAX_LENGTH = 12_000;

    /**
     * 生成学习路线。
     *
     * @param userId       用户 ID
     * @param projectId    项目 ID
     * @param files        上传的资料文件（.md/.txt）
     * @param useWebSearch 是否启用联网搜索
     * @param targetDays   目标天数（可选）
     * @param replaceMode  是否替换已有阶段和任务
     * @return 生成结果（含阶段和任务列表）
     */
    @Transactional
    public GeneratePlanResponse generatePlan(String userId, String projectId,
                                             List<MultipartFile> files, boolean useWebSearch,
                                             Integer targetDays, boolean replaceMode) {
        // 1. 校验项目归属
        Project project = projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        // 2. 获取 DeepSeek 配置
        DeepSeekConfig deepSeekConfig = aiConfigService.getDeepSeekConfig(userId);
        if (deepSeekConfig == null) {
            throw new BusinessException(ErrorCode.AI_CONFIG_NOT_SET);
        }

        // 3. replaceMode 时清理旧数据
        if (replaceMode) {
            taskRepository.deleteByProjectId(projectId);
            phaseRepository.deleteByProjectId(projectId);
            log.info("已清理旧路线: projectId={}", projectId);
        }

        // 4. 解析上传文件 → 合并纯文本
        String materialText = parseFiles(files);

        // 5. 联网搜索（可选）
        String searchSummary = "";
        if (useWebSearch) {
            searchSummary = performWebSearch(userId, project.getGoal(), materialText);
        }

        // 6. 构建提示词
        String userPrompt = buildUserPrompt(project, targetDays, materialText, searchSummary);

        // 7. 调用 DeepSeek 生成路线
        String aiContent;
        try {
            aiContent = deepSeekPlanClient.generatePlan(deepSeekConfig, userPrompt);
        } catch (BusinessException e) {
            throw e; // AI_KEY_INVALID 向上抛
        } catch (Exception e) {
            log.error("DeepSeek 路线生成失败: projectId={}, error={}", projectId, e.getMessage());
            throw new BusinessException(ErrorCode.AI_GENERATE_FAILED);
        }

        // 8. 解析 JSON
        JsonNode planNode = parsePlanJson(aiContent);

        // 9. 持久化 phases + tasks
        List<Phase> savedPhases = new ArrayList<>();
        List<Task> savedTasks = new ArrayList<>();
        persistPlan(planNode, project, targetDays, savedPhases, savedTasks);

        // 10. 标记项目为 AI 生成
        project.setIsAiGenerated(true);
        projectRepository.save(project);

        log.info("AI 路线生成完成: projectId={}, phases={}, tasks={}",
                projectId, savedPhases.size(), savedTasks.size());

        // 11. 构建响应
        List<PhaseResponse> phaseResponses = savedPhases.stream().map(PhaseResponse::from).toList();
        List<TaskResponse> taskResponses = savedTasks.stream().map(TaskResponse::from).toList();
        return GeneratePlanResponse.builder()
                .projectId(projectId)
                .phases(phaseResponses)
                .tasks(taskResponses)
                .phaseCount(phaseResponses.size())
                .taskCount(taskResponses.size())
                .build();
    }

    /** 解析上传文件，合并为纯文本 */
    private String parseFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (MultipartFile file : files) {
            String filename = file.getOriginalFilename();
            if (filename == null) {
                continue;
            }
            String lowerName = filename.toLowerCase();
            try {
                String content = new String(file.getBytes(), StandardCharsets.UTF_8);
                MarkdownParserService.ParseResult result;
                String fallbackTitle = filename.replaceFirst("\\.[^.]+$", "");
                if (lowerName.endsWith(".md")) {
                    result = markdownParserService.parseMarkdown(content, fallbackTitle);
                } else if (lowerName.endsWith(".txt")) {
                    result = markdownParserService.parsePlainText(content, fallbackTitle);
                } else {
                    log.warn("不支持的文件类型，跳过: {}", filename);
                    continue;
                }

                sb.append("【").append(result.title()).append("】\n");
                String text = result.content();
                if (text.length() > SINGLE_FILE_MAX_LENGTH) {
                    text = text.substring(0, SINGLE_FILE_MAX_LENGTH) + "...";
                }
                sb.append(text).append("\n\n");

                if (sb.length() >= TOTAL_MATERIAL_MAX_LENGTH) {
                    break;
                }
            } catch (IOException e) {
                log.warn("文件读取失败，跳过: {}, error={}", filename, e.getMessage());
            }
        }

        String result = sb.toString();
        if (result.length() > TOTAL_MATERIAL_MAX_LENGTH) {
            result = result.substring(0, TOTAL_MATERIAL_MAX_LENGTH) + "...";
        }
        return result;
    }

    /** 执行联网搜索：生成搜索词 → Bing 搜索 → 格式化摘要 */
    private String performWebSearch(String userId, String goal, String materialSummary) {
        try {
            SearchConfig searchConfig = aiConfigService.getSearchConfig(userId);
            if (searchConfig == null) {
                log.info("未配置搜索 Key，跳过联网搜索: userId={}", userId);
                return "";
            }

            List<String> queries = searchQueryService.generateQueries(userId, goal, materialSummary);
            if (queries.isEmpty()) {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            for (String query : queries) {
                List<SearchResultItem> results = bingSearchService.search(searchConfig.apiKey(), query);
                String formatted = bingSearchService.formatResults(results);
                if (StringUtils.hasText(formatted)) {
                    sb.append("搜索: ").append(query).append("\n").append(formatted).append("\n");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("联网搜索失败，降级跳过: userId={}, error={}", userId, e.getMessage());
            return "";
        }
    }

    /** 构建用户提示词 */
    private String buildUserPrompt(Project project, Integer targetDays,
                                   String materialText, String searchSummary) {
        StringBuilder sb = new StringBuilder();
        sb.append("【项目名称】\n").append(project.getName()).append("\n\n");

        if (StringUtils.hasText(project.getGoal())) {
            sb.append("【学习目标】\n").append(project.getGoal()).append("\n\n");
        }
        if (StringUtils.hasText(project.getDescription())) {
            sb.append("【项目描述】\n").append(project.getDescription()).append("\n\n");
        }

        sb.append("【每日学习时长】").append(project.getDailyHours() != null ? project.getDailyHours() : 2).append(" 小时\n\n");

        if (targetDays != null && targetDays > 0) {
            sb.append("【目标天数】").append(targetDays).append(" 天\n\n");
        }

        if (StringUtils.hasText(materialText)) {
            sb.append("【参考资料】\n").append(materialText).append("\n");
        }

        if (StringUtils.hasText(searchSummary)) {
            sb.append("【网络搜索结果】\n").append(searchSummary).append("\n");
        }

        return sb.toString();
    }

    /** 解析 AI 返回的 JSON，校验 phases 非空 */
    private JsonNode parsePlanJson(String aiContent) {
        try {
            String json = extractJson(aiContent);
            JsonNode root = objectMapper.readTree(json);
            JsonNode phases = root.path("phases");
            if (!phases.isArray() || phases.isEmpty()) {
                log.error("AI 返回的 phases 为空: content={}", aiContent);
                throw new BusinessException(ErrorCode.AI_GENERATE_FAILED);
            }
            return root;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 响应 JSON 解析失败: content={}, error={}", aiContent, e.getMessage());
            throw new BusinessException(ErrorCode.AI_GENERATE_FAILED);
        }
    }

    /** 持久化阶段和任务 */
    private void persistPlan(JsonNode planNode, Project project, Integer targetDays,
                             List<Phase> savedPhases, List<Task> savedTasks) {
        JsonNode phasesNode = planNode.path("phases");
        LocalDate startDate = LocalDate.now();
        int phaseSortOrder = 0;
        int taskSortOrder = 0;
        int dayCounter = 0;

        for (JsonNode phaseNode : phasesNode) {
            Phase phase = new Phase();
            phase.setId(UUID.randomUUID().toString());
            phase.setProjectId(project.getId());
            phase.setName(getTextOrDefault(phaseNode, "name", "未命名阶段"));
            phase.setObjective(getTextOrDefault(phaseNode, "objective", ""));
            phase.setSortOrder(phaseSortOrder++);
            phase = phaseRepository.save(phase);
            savedPhases.add(phase);

            JsonNode tasksNode = phaseNode.path("tasks");
            if (tasksNode.isArray()) {
                for (JsonNode taskNode : tasksNode) {
                    Task task = new Task();
                    task.setId(UUID.randomUUID().toString());
                    task.setProjectId(project.getId());
                    task.setPhaseId(phase.getId());
                    task.setTitle(getTextOrDefault(taskNode, "title", "未命名任务"));
                    task.setDescription(getTextOrDefault(taskNode, "description", ""));
                    task.setVerificationCriteria(getTextOrDefault(taskNode, "verification_criteria", ""));

                    int dayNumber = taskNode.path("day_number").asInt(++dayCounter);
                    task.setDayNumber(dayNumber);
                    dayCounter = Math.max(dayCounter, dayNumber);

                    // 根据 dayNumber 计算截止日期
                    if (targetDays != null && targetDays > 0) {
                        int dayOffset = Math.min(dayNumber - 1, targetDays);
                        task.setDueDate(startDate.plusDays(dayOffset));
                    }

                    task.setStatus("pending");
                    task.setSortOrder(taskSortOrder++);
                    task = taskRepository.save(task);
                    savedTasks.add(task);
                }
            }
        }
    }

    /** 安全提取文本字段，缺失时返回默认值 */
    private String getTextOrDefault(JsonNode node, String field, String defaultValue) {
        JsonNode value = node.path(field);
        String text = value.asText("").trim();
        return StringUtils.hasText(text) ? text : defaultValue;
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
