package com.learnspark.plan.controller;

import com.learnspark.common.result.ApiResult;
import com.learnspark.common.security.CurrentUser;
import com.learnspark.plan.dto.TaskRequest;
import com.learnspark.plan.dto.TaskResponse;
import com.learnspark.plan.service.TaskService;
import com.learnspark.submission.dto.SubmissionRequest;
import com.learnspark.submission.dto.SubmissionResponse;
import com.learnspark.submission.service.SubmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务控制器：增删改使用顶级路径 /tasks。
 *
 * <p>列表查询在 {@link ProjectController} 中：GET /projects/{projectId}/tasks?date=YYYY-MM-DD
 * <p>任务提交审核：POST /tasks/{id}/submit（委托 {@link SubmissionService}）
 */
@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final SubmissionService submissionService;

    @GetMapping("/{id}")
    public ApiResult<TaskResponse> get(@CurrentUser String userId, @PathVariable String id) {
        return ApiResult.success(taskService.getTask(userId, id));
    }

    @PostMapping
    public ApiResult<TaskResponse> create(@CurrentUser String userId,
                                          @Valid @RequestBody TaskRequest request) {
        return ApiResult.success(taskService.create(userId, request));
    }

    @PutMapping("/{id}")
    public ApiResult<TaskResponse> update(@CurrentUser String userId, @PathVariable String id,
                                          @Valid @RequestBody TaskRequest request) {
        return ApiResult.success(taskService.update(userId, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@CurrentUser String userId, @PathVariable String id) {
        taskService.delete(userId, id);
        return ApiResult.success();
    }

    /**
     * 提交任务并触发 AI 审核。
     * <p>完整路径：POST /api/v1/tasks/{id}/submit
     */
    @PostMapping("/{id}/submit")
    public ApiResult<SubmissionResponse> submit(@CurrentUser String userId,
                                                @PathVariable String id,
                                                @Valid @RequestBody SubmissionRequest request) {
        return ApiResult.success(submissionService.submit(userId, id, request));
    }
}
