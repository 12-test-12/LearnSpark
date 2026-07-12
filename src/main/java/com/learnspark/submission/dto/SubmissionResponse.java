package com.learnspark.submission.dto;

import com.learnspark.submission.entity.Submission;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务提交响应体。
 *
 * <p>包含提交记录信息 + AI 审核结果 + 关联的知识条目 ID。
 */
@Data
@Builder
@AllArgsConstructor
public class SubmissionResponse {

    private String submissionId;
    private String taskId;
    private String content;
    private List<String> attachmentUrls;

    /** AI 审核结果 */
    private Boolean passed;
    private Integer score;
    private String feedback;
    private String aiModel;

    /** 任务当前状态（提交后更新） */
    private String taskStatus;

    /** 自动生成的知识条目 ID */
    private String knowledgeEntryId;

    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;

    public static SubmissionResponse from(Submission s, String taskStatus, String knowledgeEntryId) {
        return SubmissionResponse.builder()
                .submissionId(s.getId())
                .taskId(s.getTaskId())
                .content(s.getContent())
                .attachmentUrls(s.getAttachmentUrls())
                .passed(s.getPassed())
                .score(s.getAiScore())
                .feedback(s.getAiFeedback())
                .aiModel(s.getAiModel())
                .taskStatus(taskStatus)
                .knowledgeEntryId(knowledgeEntryId)
                .submittedAt(s.getSubmittedAt())
                .reviewedAt(s.getReviewedAt())
                .build();
    }
}
