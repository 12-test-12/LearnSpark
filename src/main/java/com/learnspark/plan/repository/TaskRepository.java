package com.learnspark.plan.repository;

import com.learnspark.plan.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * 任务仓库（自动过滤已软删除记录）。
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, String> {

    /** 按项目查询所有任务（按 sort_order 排序） */
    List<Task> findByProjectIdOrderBySortOrderAsc(String projectId);

    /** 按项目 + 截止日期查询任务 */
    List<Task> findByProjectIdAndDueDateOrderBySortOrderAsc(String projectId, LocalDate dueDate);

    /** 按阶段查询任务 */
    List<Task> findByPhaseIdOrderBySortOrderAsc(String phaseId);

    /** 统计项目下任务总数（用于项目统计） */
    long countByProjectId(String projectId);

    /** 按项目 + 状态统计任务数（用于完成率计算） */
    long countByProjectIdAndStatus(String projectId, String status);

    /** 按项目 ID 删除所有任务（重新生成路线时清理旧数据） */
    void deleteByProjectId(String projectId);

    /**
     * 查询用户今日待完成任务（关联 Project.userId 过滤）。
     * 用于每日提醒邮件。
     */
    @Query("SELECT t FROM Task t WHERE t.projectId IN " +
           "(SELECT p.id FROM Project p WHERE p.userId = :userId) " +
           "AND t.dueDate = :date AND t.status = 'pending' " +
           "ORDER BY t.sortOrder ASC")
    List<Task> findTodayPendingTasksByUserId(@Param("userId") String userId, @Param("date") LocalDate date);
}
