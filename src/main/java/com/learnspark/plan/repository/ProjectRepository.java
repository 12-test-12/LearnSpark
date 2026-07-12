package com.learnspark.plan.repository;

import com.learnspark.plan.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 学习项目仓库，所有查询自动过滤已软删除记录（通过实体上的 @SQLRestriction）。
 */
@Repository
public interface ProjectRepository extends JpaRepository<Project, String> {

    /** 获取用户的所有项目（自动排除已删除） */
    List<Project> findByUserIdOrderByCreatedAtDesc(String userId);

    /** 按 ID 和 userId 查询，确保用户只能访问自己的项目 */
    Optional<Project> findByIdAndUserId(String id, String userId);

    /** 判断项目是否属于指定用户（用于统计接口的归属校验，轻量查询） */
    boolean existsByIdAndUserId(String id, String userId);
}
