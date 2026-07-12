package com.learnspark.plan.service;

import com.learnspark.common.exception.BusinessException;
import com.learnspark.common.exception.ErrorCode;
import com.learnspark.plan.dto.ProjectRequest;
import com.learnspark.plan.dto.ProjectResponse;
import com.learnspark.plan.entity.Project;
import com.learnspark.plan.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 学习项目服务：CRUD + 用户隔离 + 软删除。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;

    @Transactional(readOnly = true)
    public List<ProjectResponse> list(String userId) {
        return projectRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(ProjectResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(String userId, String projectId) {
        return ProjectResponse.from(getOwnedProject(userId, projectId));
    }

    @Transactional
    public ProjectResponse create(String userId, ProjectRequest request) {
        Project project = Project.builder()
                .userId(userId)
                .name(request.getName())
                .description(request.getDescription())
                .goal(request.getGoal())
                .dailyHours(request.getDailyHours())
                .coverColor(request.getCoverColor())
                .build();
        project = projectRepository.save(project);
        log.info("项目创建: id={}, userId={}", project.getId(), userId);
        return ProjectResponse.from(project);
    }

    @Transactional
    public ProjectResponse update(String userId, String projectId, ProjectRequest request) {
        Project project = getOwnedProject(userId, projectId);
        project.setName(request.getName());
        project.setDescription(request.getDescription());
        project.setGoal(request.getGoal());
        if (request.getDailyHours() != null) {
            project.setDailyHours(request.getDailyHours());
        }
        if (request.getCoverColor() != null) {
            project.setCoverColor(request.getCoverColor());
        }
        project = projectRepository.save(project);
        log.info("项目更新: id={}, userId={}", project.getId(), userId);
        return ProjectResponse.from(project);
    }

    @Transactional
    public void delete(String userId, String projectId) {
        Project project = getOwnedProject(userId, projectId);
        project.setDeletedAt(LocalDateTime.now());
        projectRepository.save(project);
        log.info("项目删除(软删除): id={}, userId={}", projectId, userId);
    }

    /** 获取用户拥有的项目，不存在或无权访问时抛异常 */
    private Project getOwnedProject(String userId, String projectId) {
        return projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }
}
