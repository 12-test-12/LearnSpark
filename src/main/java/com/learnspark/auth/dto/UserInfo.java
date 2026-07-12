package com.learnspark.auth.dto;

import com.learnspark.auth.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * 用户信息（脱敏后），用于响应体。
 *
 * <p>不包含 passwordHash 等敏感字段，确保密码哈希不会泄露到前端。
 */
@Data
@Builder
@AllArgsConstructor
public class UserInfo {

    private String id;
    private String email;
    private String nickname;
    private String avatarUrl;
    private String timezone;

    /** 从 User 实体构造，自动屏蔽敏感字段 */
    public static UserInfo from(User user) {
        return UserInfo.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .avatarUrl(user.getAvatarUrl())
                .timezone(user.getTimezone())
                .build();
    }
}
