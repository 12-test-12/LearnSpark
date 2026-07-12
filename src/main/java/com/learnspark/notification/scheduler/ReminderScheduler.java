package com.learnspark.notification.scheduler;

import com.learnspark.notification.config.ReminderProperties;
import com.learnspark.notification.service.ReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 提醒定时任务：每分钟扫描到点用户并发送邮件。
 *
 * <p>cron 表达式由 app.reminder.cron 配置，默认 "0 * * * * ?"（每分钟整点）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderScheduler {

    private final ReminderService reminderService;
    private final ReminderProperties reminderProperties;

    /**
     * 每分钟扫描并发送提醒。
     *
     * <p>注意：cron 通过属性配置注入，避免硬编码。
     */
    @Scheduled(cron = "${app.reminder.cron:0 * * * * ?}")
    public void scanAndSend() {
        try {
            int sent = reminderService.scanAndSendReminders();
            if (sent > 0) {
                log.info("定时提醒任务完成: 发送 {} 封邮件", sent);
            }
        } catch (Exception e) {
            log.error("定时提醒任务异常: {}", e.getMessage(), e);
        }
    }
}
