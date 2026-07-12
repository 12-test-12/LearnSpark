package com.learnspark.notification.service;

import com.learnspark.auth.entity.User;
import com.learnspark.auth.repository.UserRepository;
import com.learnspark.notification.dto.DailyReminderData;
import com.learnspark.notification.dto.ReminderRequest;
import com.learnspark.notification.dto.ReminderResponse;
import com.learnspark.notification.entity.ReminderSetting;
import com.learnspark.notification.repository.ReminderSettingRepository;
import com.learnspark.plan.entity.Project;
import com.learnspark.plan.entity.Task;
import com.learnspark.plan.repository.ProjectRepository;
import com.learnspark.plan.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 提醒服务：管理用户提醒设置 + 定时扫描发送。
 *
 * <p>1.7.1：GET/PUT 提醒设置（CRUD）
 * <p>1.7.2：扫描到点用户，组装今日任务数据，委托 {@link ReminderMailService} 发送邮件
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderService {

    private final ReminderSettingRepository reminderRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final ReminderMailService mailService;

    /**
     * 获取用户提醒设置（不存在则返回默认值）。
     */
    @Transactional(readOnly = true)
    public ReminderResponse getReminder(String userId) {
        ReminderSetting setting = reminderRepository.findById(userId).orElse(null);
        if (setting == null) {
            // 未配置时用用户注册邮箱作为默认
            User user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                ReminderResponse resp = ReminderResponse.from(null);
                resp.setEmail(user.getEmail());
                return resp;
            }
        }
        return ReminderResponse.from(setting);
    }

    /**
     * 保存（创建或更新）用户提醒设置。
     */
    @Transactional
    public ReminderResponse saveReminder(String userId, ReminderRequest request) {
        ReminderSetting setting = reminderRepository.findById(userId).orElseGet(() -> {
            ReminderSetting r = new ReminderSetting();
            r.setUserId(userId);
            return r;
        });

        setting.setEmail(request.getEmail());
        setting.setReminderTime(request.getReminderTime());
        if (StringUtils.hasText(request.getTimezone())) {
            setting.setTimezone(request.getTimezone());
        }
        if (request.getEnabled() != null) {
            setting.setEnabled(request.getEnabled());
        }

        setting = reminderRepository.save(setting);
        log.info("提醒设置已更新: userId={}, time={}, enabled={}", userId, setting.getReminderTime(), setting.getEnabled());
        return ReminderResponse.from(setting);
    }

    /**
     * 扫描当前分钟内到点的用户并发送提醒邮件。
     * 由 {@link com.learnspark.notification.scheduler.ReminderScheduler} 每分钟调用。
     *
     * <p>防重复策略：检查 last_sent_at 是否为今天，是则跳过。
     */
    @Transactional
    public int scanAndSendReminders() {
        LocalTime now = LocalTime.now().withSecond(0).withNano(0);
        LocalTime windowStart = now;
        LocalTime windowEnd = now.plusMinutes(1);

        List<ReminderSetting> pending = reminderRepository.findPendingReminders(windowStart, windowEnd);
        if (pending.isEmpty()) {
            return 0;
        }

        log.info("扫描到 {} 条待发送提醒（时间窗口 {}-{}）", pending.size(), windowStart, windowEnd);

        int sent = 0;
        LocalDate today = LocalDate.now();
        for (ReminderSetting setting : pending) {
            // 防重复：今天已发送过则跳过
            if (setting.getLastSentAt() != null
                    && setting.getLastSentAt().toLocalDate().equals(today)) {
                continue;
            }
            try {
                boolean success = sendReminderForUser(setting, today);
                if (success) {
                    setting.setLastSentAt(LocalDateTime.now());
                    reminderRepository.save(setting);
                    sent++;
                }
            } catch (Exception e) {
                log.warn("发送提醒邮件失败: userId={}, error={}", setting.getUserId(), e.getMessage());
            }
        }
        log.info("本次共发送 {} 封提醒邮件", sent);
        return sent;
    }

    /**
     * 为单个用户组装数据并发送提醒邮件（含重试）。
     *
     * @return true 表示发送成功
     */
    private boolean sendReminderForUser(ReminderSetting setting, LocalDate today) {
        String userId = setting.getUserId();
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("用户不存在，跳过提醒: userId={}", userId);
            return false;
        }

        // 查询今日待完成任务
        List<Task> todayTasks = taskRepository.findTodayPendingTasksByUserId(userId, today);
        // 批量查询相关项目（用于展示项目名）
        List<Project> projects = projectRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Map<String, Project> projectMap = projects.stream()
                .collect(Collectors.toMap(Project::getId, Function.identity()));

        // 组装任务简要信息
        List<DailyReminderData.TaskBrief> taskBriefs = new ArrayList<>();
        for (Task t : todayTasks) {
            Project p = projectMap.get(t.getProjectId());
            taskBriefs.add(DailyReminderData.TaskBrief.builder()
                    .title(t.getTitle() != null ? t.getTitle() : "未命名任务")
                    .description(truncate(t.getDescription(), 100))
                    .dayNumber(t.getDayNumber())
                    .projectName(p != null ? p.getName() : "")
                    .build());
        }

        DailyReminderData data = DailyReminderData.builder()
                .nickname(StringUtils.hasText(user.getNickname()) ? user.getNickname() : "学习者")
                .today(today)
                .tasks(taskBriefs)
                .taskCount(taskBriefs.size())
                .passedDays(null) // 阶段三实现连续打卡
                .encouragement(pickEncouragement(todayTasks.size()))
                .build();

        return mailService.sendReminderWithRetry(setting.getEmail(), data);
    }

    /** 根据任务数量挑选鼓励语 */
    private String pickEncouragement(int taskCount) {
        if (taskCount == 0) {
            return "今天没有待完成任务，可以利用时间复习或探索新知识，保持学习的节奏！";
        }
        if (taskCount == 1) {
            return "今天有 1 个任务待完成，专注搞定它，今天的学习就成功了一半！";
        }
        return String.format("今天有 %d 个任务待完成，按优先级逐个击破吧，加油！", taskCount);
    }

    /** 截断字符串到指定长度 */
    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
