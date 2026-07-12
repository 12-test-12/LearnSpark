package com.learnspark.plan.controller;

import com.learnspark.ai.plan.PlanGenerationService;
import com.learnspark.ai.plan.dto.GeneratePlanResponse;
import com.learnspark.common.result.ApiResult;
import com.learnspark.common.security.CurrentUser;
import com.learnspark.plan.dto.PhaseResponse;
import com.learnspark.plan.dto.ProjectRequest;
import com.learnspark.plan.dto.ProjectResponse;
import com.learnspark.plan.dto.TaskResponse;
import com.learnspark.plan.service.PhaseService;
import com.learnspark.plan.service.ProjectService;
import com.learnspark.plan.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * 学习项目控制器：CRUD，按当前用户隔离。
 *
 * <p>完整路径 /api/v1/projects/**，需携带 token。
 */
@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final PhaseService phaseService;
    private final TaskService taskService;
    private final PlanGenerationService planGenerationService;

    @GetMapping
    public ApiResult<List<ProjectResponse>> list(@CurrentUser String userId) {
        return ApiResult.success(projectService.list(userId));
    }

    @GetMapping("/{id}")
    public ApiResult<ProjectResponse> get(@CurrentUser String userId, @PathVariable String id) {
        return ApiResult.success(projectService.get(userId, id));
    }

    @PostMapping
    public ApiResult<ProjectResponse> create(@CurrentUser String userId,
                                             @Valid @RequestBody ProjectRequest request) {
        return ApiResult.success(projectService.create(userId, request));
    }

    @PutMapping("/{id}")
    public ApiResult<ProjectResponse> update(@CurrentUser String userId, @PathVariable String id,
                                             @Valid @RequestBody ProjectRequest request) {
        return ApiResult.success(projectService.update(userId, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@CurrentUser String userId, @PathVariable String id) {
        projectService.delete(userId, id);
        return ApiResult.success();
    }

    /** 获取项目下的阶段列表 */
    @GetMapping("/{id}/phases")
    public ApiResult<List<PhaseResponse>> listPhases(@CurrentUser String userId, @PathVariable String id) {
        return ApiResult.success(phaseService.listByProject(userId, id));
    }

    /** 获取项目下的任务列表（可选按日期过滤） */
    @GetMapping("/{id}/tasks")
    public ApiResult<List<TaskResponse>> listTasks(@CurrentUser String userId, @PathVariable String id,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResult.success(taskService.listByProject(userId, id, date));
    }

    /**
     * AI 生成学习路线。
     *
     * <p>上传 .md/.txt 资料 + 可选联网搜索，调用 DeepSeek 生成结构化阶段和任务。
     * multipart/form-data 格式：files 为文件部分，其余为表单字段。
     *
     * @param userId       当前用户 ID
     * @param id           项目 ID
     * @param files        上传的资料文件（可选，支持 .md/.txt）
     * @param useWebSearch 是否启用联网搜索（默认 false）
     * @param targetDays   目标天数（可选）
     * @param replaceMode  是否替换已有阶段和任务（默认 true）
     * @return 生成结果（含阶段和任务列表）
     */
    @PostMapping("/{id}/generate-plan")
    public ApiResult<GeneratePlanResponse> generatePlan(
            @CurrentUser String userId,
            @PathVariable String id,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @RequestParam(value = "useWebSearch", defaultValue = "false") boolean useWebSearch,
            @RequestParam(value = "targetDays", required = false) Integer targetDays,
            @RequestParam(value = "replaceMode", defaultValue = "true") boolean replaceMode) {
        return ApiResult.success(
                planGenerationService.generatePlan(userId, id, files, useWebSearch, targetDays, replaceMode));
    }
}
