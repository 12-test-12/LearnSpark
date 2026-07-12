package com.learnspark.plan.service;

import com.learnspark.common.exception.BusinessException;
import com.learnspark.common.exception.ErrorCode;
import com.learnspark.plan.dto.TaskRequest;
import com.learnspark.plan.dto.TaskResponse;
import com.learnspark.plan.entity.Task;
import com.learnspark.plan.repository.ProjectRepository;
import com.learnspark.plan.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 任务服务：CRUD + 项目归属校验 + 软删除。
 *
 * <p>任务状态流转：pending → submitted → passed/failed（由审核模块驱动）。
 */
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;

    @Transactional(readOnly = true)
    public TaskResponse getTask(String userId, String taskId) {
        return TaskResponse.from(getOwnedTask(userId, taskId));
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> listByProject(String userId, String projectId, LocalDate date) {
        verifyProjectOwnership(userId, projectId);
        List<Task> tasks = (date != null)
                ? taskRepository.findByProjectIdAndDueDateOrderBySortOrderAsc(projectId, date)
                : taskRepository.findByProjectIdOrderBySortOrderAsc(projectId);
        return tasks.stream().map(TaskResponse::from).toList();
    }

    @Transactional
    public TaskResponse create(String userId, TaskRequest request) {
        verifyProjectOwnership(userId, request.getProjectId());
        Task task = Task.builder()
                .projectId(request.getProjectId())
                .phaseId(request.getPhaseId())
                .dayNumber(request.getDayNumber())
                .title(request.getTitle())
                .description(request.getDescription())
                .verificationCriteria(request.getVerificationCriteria())
                .dueDate(request.getDueDate())
                .sortOrder(request.getSortOrder())
                .build();
        task = taskRepository.save(task);
        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse update(String userId, String taskId, TaskRequest request) {
        Task task = getOwnedTask(userId, taskId);
        task.setPhaseId(request.getPhaseId());
        task.setDayNumber(request.getDayNumber());
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setVerificationCriteria(request.getVerificationCriteria());
        if (request.getDueDate() != null) {
            task.setDueDate(request.getDueDate());
        }
        if (request.getSortOrder() != null) {
            task.setSortOrder(request.getSortOrder());
        }
        task = taskRepository.save(task);
        return TaskResponse.from(task);
    }

    @Transactional
    public void delete(String userId, String taskId) {
        Task task = getOwnedTask(userId, taskId);
        task.setDeletedAt(java.time.LocalDateTime.now());
        taskRepository.save(task);
    }

    /** 获取任务并校验项目归属（供其他 Service 调用） */
    public Task getTaskForUser(String userId, String taskId) {
        return getOwnedTask(userId, taskId);
    }

    /** 获取任务并校验项目归属 */
    private Task getOwnedTask(String userId, String taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
        verifyProjectOwnership(userId, task.getProjectId());
        return task;
    }

    private void verifyProjectOwnership(String userId, String projectId) {
        projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }
}
