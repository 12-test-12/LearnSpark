package com.learnspark.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 全局错误码。
 *
 * <p>分段约定：
 * <ul>
 *   <li>10xxx 认证与权限</li>
 *   <li>20xxx 学习项目与任务</li>
 *   <li>30xxx AI 与审核</li>
 *   <li>40xxx 知识库</li>
 *   <li>50xxx 提醒与通知</li>
 *   <li>60xxx 积分与成就</li>
 *   <li>90xxx 系统与通用</li>
 * </ul>
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    // 通用
    SUCCESS(0, "ok"),
    BAD_REQUEST(40000, "请求参数错误"),
    UNAUTHORIZED(40100, "未登录或登录已过期"),
    FORBIDDEN(40300, "无权访问"),
    NOT_FOUND(40400, "资源不存在"),
    CONFLICT(40900, "资源冲突"),
    INTERNAL_ERROR(50000, "服务器内部错误"),

    // 认证 10xxx
    EMAIL_ALREADY_REGISTERED(10001, "邮箱已注册"),
    EMAIL_OR_PASSWORD_WRONG(10002, "邮箱或密码错误"),
    ACCOUNT_DISABLED(10003, "账号已禁用"),
    TOKEN_INVALID(10004, "Token 无效"),

    // 学习项目 20xxx
    PROJECT_NOT_FOUND(20001, "项目不存在"),
    PROJECT_NAME_EMPTY(20002, "项目名称不能为空"),
    TASK_NOT_FOUND(20003, "任务不存在"),
    TASK_ALREADY_PASSED(20004, "任务已通过，无需重复提交"),

    // AI 30xxx
    AI_CONFIG_NOT_SET(30001, "未配置 AI 密钥"),
    AI_CALL_FAILED(30002, "AI 调用失败"),
    AI_KEY_INVALID(30003, "AI 密钥无效"),
    AI_GENERATE_FAILED(30004, "AI 生成学习路线失败"),
    SEARCH_FAILED(30005, "网络搜索失败"),

    // 知识库 40xxx
    KB_ENTRY_NOT_FOUND(40001, "知识条目不存在"),
    KB_FILE_TOO_LARGE(40002, "文件过大"),
    KB_PARSE_FAILED(40003, "文件解析失败"),
    KB_UNSUPPORTED_TYPE(40004, "不支持的文件类型"),
    KB_UPLOAD_FAILED(40005, "文件上传失败"),

    // 提醒 50xxx
    REMINDER_NOT_ENABLED(50001, "提醒未开启"),

    // 成就 60xxx
    BADGE_NOT_FOUND(60001, "徽章不存在");

    private final int code;
    private final String message;
}
