package com.learnspark.plan.repository;

import com.learnspark.plan.entity.Phase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 阶段仓库。
 */
@Repository
public interface PhaseRepository extends JpaRepository<Phase, String> {

    /** 按项目 ID 查询阶段列表（按 sort_order 排序） */
    List<Phase> findByProjectIdOrderBySortOrderAsc(String projectId);

    /** 按项目 ID 删除所有阶段（重新生成路线时清理旧数据） */
    void deleteByProjectId(String projectId);
}
