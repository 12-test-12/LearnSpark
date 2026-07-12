package com.learnspark.notification.controller;

import com.learnspark.common.result.ApiResult;
import com.learnspark.common.security.CurrentUser;
import com.learnspark.notification.dto.ReminderRequest;
import com.learnspark.notification.dto.ReminderResponse;
import com.learnspark.notification.service.ReminderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提醒设置控制器。
 *
 * <p>路径前缀 /user/reminder，完整路径 /api/v1/user/reminder，需携带 token。
 */
@RestController
@RequestMapping("/user/reminder")
@RequiredArgsConstructor
public class ReminderController {

    private final ReminderService reminderService;

    /**
     * 获取当前用户提醒设置。
     *
     * <pre>
     * GET /api/v1/user/reminder
     * </pre>
     */
    @GetMapping
    public ApiResult<ReminderResponse> getReminder(@CurrentUser String userId) {
        return ApiResult.success(reminderService.getReminder(userId));
    }

    /**
     * 更新当前用户提醒设置。
     *
     * <pre>
     * PUT /api/v1/user/reminder
     * { "email": "user@example.com", "reminderTime": "08:00:00", "timezone": "Asia/Shanghai", "enabled": true }
     * </pre>
     */
    @PutMapping
    public ApiResult<ReminderResponse> saveReminder(@CurrentUser String userId,
                                                     @Valid @RequestBody ReminderRequest request) {
        return ApiResult.success(reminderService.saveReminder(userId, request));
    }
}
