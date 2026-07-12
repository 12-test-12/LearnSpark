package com.learnspark.ai.repository;

import com.learnspark.ai.entity.UserAiConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 用户 AI 配置仓库。
 */
@Repository
public interface UserAiConfigRepository extends JpaRepository<UserAiConfig, String> {
}
