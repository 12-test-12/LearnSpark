package com.learnspark.plan.controller;

import com.learnspark.common.result.ApiResult;
import com.learnspark.common.security.CurrentUser;
import com.learnspark.plan.dto.PhaseRequest;
import com.learnspark.plan.dto.PhaseResponse;
import com.learnspark.plan.service.PhaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 阶段控制器：增删改使用顶级路径 /phases。
 *
 * <p>列表查询在 {@link ProjectController} 中：GET /projects/{projectId}/phases
 */
@RestController
@RequestMapping("/phases")
@RequiredArgsConstructor
public class PhaseController {

    private final PhaseService phaseService;

    @PostMapping
    public ApiResult<PhaseResponse> create(@CurrentUser String userId,
                                           @Valid @RequestBody PhaseRequest request) {
        return ApiResult.success(phaseService.create(userId, request));
    }

    @PutMapping("/{id}")
    public ApiResult<PhaseResponse> update(@CurrentUser String userId, @PathVariable String id,
                                           @Valid @RequestBody PhaseRequest request) {
        return ApiResult.success(phaseService.update(userId, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@CurrentUser String userId, @PathVariable String id) {
        phaseService.delete(userId, id);
        return ApiResult.success();
    }
}
