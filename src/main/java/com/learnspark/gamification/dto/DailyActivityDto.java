package com.learnspark.gamification.dto;

import java.time.LocalDate;

/**
 * 每日活动统计（用于仪表盘热力图）。
 *
 * @param date  日期
 * @param count 当天提交次数
 */
public record DailyActivityDto(LocalDate date, long count) {
}
