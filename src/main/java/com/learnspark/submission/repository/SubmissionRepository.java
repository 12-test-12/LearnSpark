package com.learnspark.submission.repository;

import com.learnspark.submission.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 提交记录仓库。
 */
@Repository
public interface SubmissionRepository extends JpaRepository<Submission, String> {

    /** 按任务查询所有提交（按提交时间倒序，最新在前） */
    List<Submission> findByTaskIdOrderBySubmittedAtDesc(String taskId);

    /** 按用户查询所有提交（按提交时间倒序） */
    List<Submission> findByUserIdOrderBySubmittedAtDesc(String userId);

    /** 统计用户已通过审核的提交数（用于 first_pass 徽章） */
    long countByUserIdAndPassedTrue(String userId);

    /** 统计用户总提交数（用于 AI 好评率计算） */
    long countByUserId(String userId);

    /** 查询用户最近 N 条提交（用于 perfect_review_5 徽章：检查连续满分） */
    List<Submission> findTop5ByUserIdOrderBySubmittedAtDesc(String userId);

    /** 查询用户在指定时间之后的所有提交（用于热力图活动统计） */
    List<Submission> findByUserIdAndSubmittedAtAfter(String userId, LocalDateTime since);
}

