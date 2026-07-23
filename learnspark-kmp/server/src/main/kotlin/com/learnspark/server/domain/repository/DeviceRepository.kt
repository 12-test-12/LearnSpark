package com.learnspark.server.domain.repository

import com.learnspark.server.domain.entity.Device
import org.springframework.data.jpa.repository.JpaRepository

interface DeviceRepository : JpaRepository<Device, String> {

    fun findByUserId(userId: String): List<Device>

    fun findByUserIdAndDeviceFingerprint(userId: String, fingerprint: String): Device?

    fun findByRefreshTokenHash(tokenHash: String): Device?
}
