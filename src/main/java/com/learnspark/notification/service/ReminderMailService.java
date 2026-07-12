package com.learnspark.notification.service;

import com.learnspark.notification.config.ReminderProperties;
import com.learnspark.notification.dto.DailyReminderData;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * 提醒邮件发送服务。
 *
 * <p>使用 Thymeleaf 渲染 HTML 邮件，通过 SMTP 发送。
 * 发送失败时按 {@link ReminderProperties#getMaxRetry()} 重试。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderMailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final ReminderProperties reminderProperties;

    private static final String TEMPLATE_NAME = "email/reminder";
    private static final String MAIL_SUBJECT = "LearnSpark 每日学习提醒";

    /**
     * 发送提醒邮件（含重试）。
     *
     * @param toEmail 收件人
     * @param data    邮件数据
     * @return true 表示发送成功
     */
    public boolean sendReminderWithRetry(String toEmail, DailyReminderData data) {
        int maxRetry = Math.max(1, reminderProperties.getMaxRetry());
        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            try {
                sendReminder(toEmail, data);
                log.info("提醒邮件发送成功: to={}, attempt={}", toEmail, attempt);
                return true;
            } catch (Exception e) {
                log.warn("提醒邮件发送失败: to={}, attempt={}/{}, error={}",
                        toEmail, attempt, maxRetry, e.getMessage());
                if (attempt < maxRetry) {
                    try {
                        Thread.sleep(1000L * attempt); // 递增等待
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 发送单次提醒邮件。
     */
    private void sendReminder(String toEmail, DailyReminderData data) throws MessagingException {
        Context context = new Context();
        context.setVariable("data", data);

        String htmlContent = templateEngine.process(TEMPLATE_NAME, context);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(reminderProperties.getMailFrom());
        helper.setTo(toEmail);
        helper.setSubject(MAIL_SUBJECT);
        helper.setText(htmlContent, true);

        mailSender.send(message);
    }
}
