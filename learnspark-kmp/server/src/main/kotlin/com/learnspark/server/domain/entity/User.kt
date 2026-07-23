package com.learnspark.server.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 用户实体。
 *
 * 密码使用 BCrypt 哈希存储。阶段一先用简化的 password 字段，
 * 阶段二后端增强可加 last_login_at、email_verified 等。
 */
@Entity
@Table(name = "users")
class User(
    @Id
    var id: String = "",

    @Column(nullable = false, unique = true)
    var email: String = "",

    @Column(nullable = false)
    var username: String = "",

    @Column(name = "password_hash", nullable = false)
    var passwordHash: String = "",

    @Column(name = "avatar_url")
    var avatarUrl: String? = null,

    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at", insertable = false, updatable = false)
    var updatedAt: Instant? = null,
)
