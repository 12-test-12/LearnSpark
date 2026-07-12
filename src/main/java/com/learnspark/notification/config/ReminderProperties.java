package com.learnspark.notification.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 提醒功能配置属性。
 *
 * <p>对应 application.yml 中 app.reminder.* 配置项。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.reminder")
public class ReminderProperties {

    /** 发件人地址 */
    private String mailFrom;

    /** 定时扫描 cron 表达式（默认每分钟） */
    private String cron = "0 * * * * ?";

    /** 发送失败重试次数 */
    private int maxRetry = 3;
}
