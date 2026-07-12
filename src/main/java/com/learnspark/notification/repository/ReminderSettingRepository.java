package com.learnspark.notification.repository;

import com.learnspark.notification.entity.ReminderSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalTime;
import java.util.List;

/**
 * 提醒设置 Repository。
 *
 * <p>核心查询：扫描当前分钟内需要发送提醒的启用记录。
 */
public interface ReminderSettingRepository extends JpaRepository<ReminderSetting, String> {

    /**
     * 查询已启用且提醒时间在 [start, end) 范围内的设置。
     * 用于定时任务每分钟扫描到点用户。
     *
     * @param start  起始时间（含）
     * @param end    结束时间（不含）
     * @return 待发送提醒列表
     */
    @Query("SELECT r FROM ReminderSetting r WHERE r.enabled = true AND r.reminderTime >= :start AND r.reminderTime < :end")
    List<ReminderSetting> findPendingReminders(@Param("start") LocalTime start, @Param("end") LocalTime end);
}
