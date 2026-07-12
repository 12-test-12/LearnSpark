package com.learnspark.submission.service;

import com.learnspark.common.exception.BusinessException;
import com.learnspark.common.exception.ErrorCode;
import com.learnspark.gamification.service.ScoreService;
import com.learnspark.knowledge.entity.KnowledgeEntry;
import com.learnspark.knowledge.repository.KnowledgeEntryRepository;
import com.learnspark.plan.entity.Task;
import com.learnspark.plan.repository.TaskRepository;
import com.learnspark.plan.service.TaskService;
import com.learnspark.submission.dto.ReviewResult;
import com.learnspark.submission.dto.SubmissionRequest;
import com.learnspark.submission.dto.SubmissionResponse;
import com.learnspark.submission.entity.Submission;
import com.learnspark.submission.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 提交审核服务。
 *
 * <p>提交流程：校验任务归属 → 创建提交记录 → AI 审核（DeepSeek 真实 / Mock 降级）→ 更新任务状态 → 写入知识库。
 * 审核策略：优先调用 DeepSeek 真实审核；未配置 Key 或调用失败时降级到 Mock（passed=true, score=9）。
 * Key 无效时不降级，抛 AI_KEY_INVALID 提示用户修正配置。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final KnowledgeEntryRepository knowledgeEntryRepository;
    private final TaskRepository taskRepository;
    private final TaskService taskService;
    private final DeepSeekReviewService deepSeekReviewService;
    private final ScoreService scoreService;

    /**
     * 提交任务并触发 AI 审核。
     *
     * @param userId  当前用户 ID
     * @param taskId  任务 ID
     * @param request 提交内容
     * @return 提交响应（含审核结果与知识条目 ID）
     */
    @Transactional
    public SubmissionResponse submit(String userId, String taskId, SubmissionRequest request) {
        // 1. 校验任务归属
        Task task = taskService.getTaskForUser(userId, taskId);

        // 2. 已通过的任务不允许重复提交
        if ("passed".equalsIgnoreCase(task.getStatus())) {
            throw new BusinessException(ErrorCode.TASK_ALREADY_PASSED);
        }

        // 3. AI 审核：优先使用 DeepSeek 真实审核，未配置 Key 或调用失败时降级到 Mock
        ReviewResult review = doReview(userId, task, request.getContent());

        // 4. 创建提交记录
        Submission submission = Submission.builder()
                .taskId(taskId)
                .userId(userId)
                .content(request.getContent())
                .attachmentUrls(request.getAttachmentUrls())
                .aiFeedback(review.feedback())
                .aiScore(review.score())
                .passed(review.passed())
                .aiModel(review.model())
                .aiRawResponse(review.rawResponse())
                .reviewedAt(LocalDateTime.now())
                .build();
        submission = submissionRepository.save(submission);
        log.info("任务提交成功: taskId={}, submissionId={}, passed={}", taskId, submission.getId(), review.passed());

        // 5. 更新任务状态
        if (review.passed()) {
            task.setStatus("passed");
            task.setCompletedAt(LocalDateTime.now());
        } else {
            task.setStatus("submitted");
        }
        taskRepository.save(task);

        // 5.5 通过则加分并更新连续打卡（触发徽章检查）
        if (review.passed()) {
            scoreService.awardOnPass(userId, task.getProjectId(), review.score());
        }

        // 6. 将提交内容写入知识库
        KnowledgeEntry entry = createKnowledgeEntryFromSubmission(userId, task, submission);
        knowledgeEntryRepository.save(entry);
        log.info("知识条目已创建: entryId={}, sourceType=submission, sourceId={}", entry.getId(), submission.getId());

        return SubmissionResponse.from(submission, task.getStatus(), entry.getId());
    }

    /**
     * 查询任务的历史提交记录。
     */
    @Transactional(readOnly = true)
    public List<Submission> listByTask(String userId, String taskId) {
        // 校验任务归属
        taskService.getTaskForUser(userId, taskId);
        return submissionRepository.findByTaskIdOrderBySubmittedAtDesc(taskId);
    }

    /**
     * 执行 AI 审核：优先 DeepSeek 真实审核，降级到 Mock。
     *
     * <p>降级场景：
     * <ul>
     *   <li>未配置 API Key / 本地模式 → Mock</li>
     *   <li>调用超时 / 网络错误 / 限流 → Mock</li>
     *   <li>Key 无效 → 抛 AI_KEY_INVALID（不降级，需用户修正配置）</li>
     * </ul>
     */
    private ReviewResult doReview(String userId, Task task, String content) {
        ReviewResult result = deepSeekReviewService.review(userId, task, content);
        if (result != null) {
            return result;
        }
        return mockReview();
    }

    /**
     * Mock 审核：固定返回通过、9 分。
     *
     * <p>当用户未配置 DeepSeek Key 或调用失败时作为降级方案。
     */
    private ReviewResult mockReview() {
        return new ReviewResult(
                true,
                9,
                "（Mock 模式）自动审核通过，请继续加油！",
                "mock",
                null
        );
    }

    /** 从提交内容创建知识条目 */
    private KnowledgeEntry createKnowledgeEntryFromSubmission(String userId, Task task, Submission submission) {
        String title = StringUtils.hasText(task.getTitle())
                ? "任务提交：" + task.getTitle()
                : "任务提交：" + task.getDescription().substring(0, Math.min(30, task.getDescription().length()));

        return KnowledgeEntry.builder()
                .userId(userId)
                .projectId(task.getProjectId())
                .title(title)
                .content(submission.getContent())
                .contentMd(submission.getContent())
                .sourceType("submission")
                .sourceId(submission.getId())
                .wordCount(countWords(submission.getContent()))
                .parseStatus("done")
                .build();
    }

    /** 统计字数（按非空白字符计数，适配中英文混合） */
    private int countWords(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return content.replaceAll("\\s+", "").length();
    }
}
