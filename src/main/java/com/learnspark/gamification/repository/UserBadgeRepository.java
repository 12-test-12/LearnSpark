package com.learnspark.gamification.repository;

import com.learnspark.gamification.entity.UserBadge;
import com.learnspark.gamification.entity.UserBadgeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 用户已获徽章仓库。
 *
 * <p>注意：UserBadge 使用 @IdClass（非 @EmbeddedId），主键字段 userId/badgeId
 * 直接在实体上，派生查询方法不能用 findById... 路径，需用 findByUserId 等直接属性。
 */
@Repository
public interface UserBadgeRepository extends JpaRepository<UserBadge, UserBadgeId> {

    /** 查询用户已获得的所有徽章记录 */
    List<UserBadge> findByUserId(String userId);

    /** 判断用户是否已获得某徽章 */
    boolean existsByUserIdAndBadgeId(String userId, String badgeId);
}
