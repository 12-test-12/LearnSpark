package com.learnspark.server.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 设备表（按文档 §4.1.2 devices 表）。
 *
 * 每个登录设备一条记录，绑定 refresh_token_hash，
 * 注销/退出登录时清空该设备的 refresh_token_hash。
 */
@Entity
@Table(name = "devices")
class Device(
    @Id
    var id: String = "",

    @Column(name = "user_id", nullable = false)
    var userId: String = "",

    @Column(name = "device_name", length = 100)
    var deviceName: String? = null,

    @Column(name = "device_type", nullable = false, length = 20)
    var deviceType: String = "unknown",

    @Column(name = "device_fingerprint", length = 255)
    var deviceFingerprint: String? = null,

    @Column(name = "refresh_token_hash", length = 255)
    var refreshTokenHash: String? = null,

    @Column(name = "last_active_at")
    var lastActiveAt: Instant? = null,

    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at", insertable = false, updatable = false)
    var updatedAt: Instant? = null,
)
