package com.learnspark.auth.entity;

import com.learnspark.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 用户实体，对应 users 表。
 *
 * <p>主键为 VARCHAR(36) UUID，由 {@code @PrePersist} 在 Java 侧生成；
 * 密码字段存 BCrypt 哈希，不存明文。
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    /** UUID 主键，持久化前由 @PrePersist 自动生成 */
    @Id
    @Column(name = "id", updatable = false, columnDefinition = "VARCHAR(36)")
    private String id;

    /** 邮箱（唯一索引 uk_users_email） */
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    /** BCrypt 哈希后的密码 */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    /** 昵称 */
    @Column(name = "nickname", length = 100)
    private String nickname;

    /** 头像 URL */
    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    /** 时区，默认 Asia/Shanghai */
    @Column(name = "timezone", length = 50)
    private String timezone;

    /** 状态：active / disabled */
    @Column(name = "status", length = 20)
    private String status;

    /** 最后登录时间 */
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /**
     * 持久化前自动生成 UUID 主键。
     *
     * <p>SSOT：id 生成逻辑收敛在实体内，避免 Service 层散落多处 UUID 生成代码，
     * 也避免依赖 DB DEFAULT（JPA insert 不传 id 时 MySQL DEFAULT 不一定生效）。
     */
    @PrePersist
    void generateId() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        if (this.status == null) {
            this.status = "active";
        }
        if (this.timezone == null) {
            this.timezone = "Asia/Shanghai";
        }
    }
}
