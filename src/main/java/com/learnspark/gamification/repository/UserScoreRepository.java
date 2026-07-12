package com.learnspark.gamification.repository;

import com.learnspark.gamification.entity.UserScore;
import com.learnspark.gamification.entity.UserScoreId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 用户积分仓库。
 */
@Repository
public interface UserScoreRepository extends JpaRepository<UserScore, UserScoreId> {

    /** 查询用户在某项目下的积分记录 */
    Optional<UserScore> findByUserIdAndProjectId(String userId, String projectId);

    /** 查询用户所有项目的积分记录（用于汇总总积分） */
    List<UserScore> findByUserId(String userId);

    /**
     * 汇总用户跨项目总积分。
     * 用 SUM 走数据库聚合，避免拉全量记录到内存。
     */
    @Query("SELECT COALESCE(SUM(s.totalPoints), 0) FROM UserScore s WHERE s.userId = :userId")
    int sumTotalPointsByUserId(@Param("userId") String userId);

    /**
     * 查询用户最大连续打卡天数（跨项目取最大值）。
     */
    @Query("SELECT COALESCE(MAX(s.streakDays), 0) FROM UserScore s WHERE s.userId = :userId")
    int maxStreakDaysByUserId(@Param("userId") String userId);
}
