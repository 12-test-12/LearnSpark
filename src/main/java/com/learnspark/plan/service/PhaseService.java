package com.learnspark.plan.service;

import com.learnspark.common.exception.BusinessException;
import com.learnspark.common.exception.ErrorCode;
import com.learnspark.plan.dto.PhaseRequest;
import com.learnspark.plan.dto.PhaseResponse;
import com.learnspark.plan.entity.Phase;
import com.learnspark.plan.entity.Project;
import com.learnspark.plan.repository.PhaseRepository;
import com.learnspark.plan.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 阶段服务：CRUD + 项目归属校验。
 */
@Service
@RequiredArgsConstructor
public class PhaseService {

    private final PhaseRepository phaseRepository;
    private final ProjectRepository projectRepository;

    @Transactional(readOnly = true)
    public List<PhaseResponse> listByProject(String userId, String projectId) {
        verifyProjectOwnership(userId, projectId);
        return phaseRepository.findByProjectIdOrderBySortOrderAsc(projectId).stream()
                .map(PhaseResponse::from)
                .toList();
    }

    @Transactional
    public PhaseResponse create(String userId, PhaseRequest request) {
        verifyProjectOwnership(userId, request.getProjectId());
        Phase phase = Phase.builder()
                .projectId(request.getProjectId())
                .name(request.getName())
                .objective(request.getObjective())
                .sortOrder(request.getSortOrder())
                .build();
        phase = phaseRepository.save(phase);
        return PhaseResponse.from(phase);
    }

    @Transactional
    public PhaseResponse update(String userId, String phaseId, PhaseRequest request) {
        Phase phase = getOwnedPhase(userId, phaseId);
        phase.setName(request.getName());
        phase.setObjective(request.getObjective());
        if (request.getSortOrder() != null) {
            phase.setSortOrder(request.getSortOrder());
        }
        phase = phaseRepository.save(phase);
        return PhaseResponse.from(phase);
    }

    @Transactional
    public void delete(String userId, String phaseId) {
        Phase phase = getOwnedPhase(userId, phaseId);
        phaseRepository.delete(phase);
    }

    /** 获取阶段并校验项目归属 */
    private Phase getOwnedPhase(String userId, String phaseId) {
        Phase phase = phaseRepository.findById(phaseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "阶段不存在"));
        verifyProjectOwnership(userId, phase.getProjectId());
        return phase;
    }

    /** 校验项目归属：项目必须存在且属于当前用户 */
    private void verifyProjectOwnership(String userId, String projectId) {
        projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }
}
