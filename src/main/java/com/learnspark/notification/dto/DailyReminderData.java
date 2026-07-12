package com.learnspark.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 每日提醒邮件数据（传给 Thymeleaf 模板渲染）。
 *
 * @param nickname    用户昵称
 * @param today       今日日期
 * @param tasks       今日待完成任务列表
 * @param taskCount   任务总数
 * @param passedDays  连续打卡天数（预留，阶段三实现）
 * @param encouragement AI 鼓励语
 */
@Data
@Builder
@AllArgsConstructor
public class DailyReminderData {

    private String nickname;
    private LocalDate today;
    private List<TaskBrief> tasks;
    private int taskCount;
    private Integer passedDays;
    private String encouragement;

    /** 任务简要信息（邮件中展示） */
    @Data
    @Builder
    @AllArgsConstructor
    public static class TaskBrief {
        private String title;
        private String description;
        private Integer dayNumber;
        private String projectName;
    }
}
