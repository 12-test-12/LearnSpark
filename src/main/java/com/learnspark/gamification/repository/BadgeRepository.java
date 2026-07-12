package com.learnspark.gamification.repository;

import com.learnspark.gamification.entity.Badge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 徽章定义仓库。
 */
@Repository
public interface BadgeRepository extends JpaRepository<Badge, String> {

    /** 查询全部徽章（按 category 分组、rule_value 排序，便于前端展示） */
    List<Badge> findAllByOrderByCategoryAscRuleValueAsc();

    /** 按 code 查询徽章（code 唯一） */
    Optional<Badge> findByCode(String code);
}
