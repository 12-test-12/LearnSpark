package com.learnspark.server.service

import com.learnspark.server.domain.entity.User
import com.learnspark.server.domain.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * R8：local profile 下的数据初始化器。
 *
 * 由于 local profile 禁用了 Flyway（迁移脚本含 MySQL 特定语法，H2 不兼容），
 * V1 迁移中 INSERT 的 dev 用户不会自动创建。这里用 CommandLineRunner 补上。
 *
 * dev 用户凭据（与 V1__init.sql 一致）：
 *   - id:       00000000-0000-0000-0000-000000000001
 *   - email:    dev@learnspark.local
 *   - username: dev
 *   - password: password  （BCrypt hash 来自 V1__init.sql）
 *
 * 仅在 local profile 生效；api profile（生产）仍由 Flyway 迁移管理 seed 数据。
 */
@Component
@Profile("local")
@Order(1)
class LocalDataInitializer(
    private val userRepository: UserRepository,
) : CommandLineRunner {

    private val log = LoggerFactory.getLogger(LocalDataInitializer::class.java)

    override fun run(vararg args: String?) {
        val devUserId = "00000000-0000-0000-0000-000000000001"
        if (userRepository.existsById(devUserId)) {
            log.debug("[LocalDataInitializer] dev 用户已存在，跳过 seed")
            return
        }
        // BCrypt hash for "password" —— 与 V1__init.sql 中的 hash 一致
        val bcryptHash = "\$2a\$10\$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
        val devUser = User(
            id = devUserId,
            email = "dev@learnspark.local",
            username = "dev",
            passwordHash = bcryptHash,
        )
        userRepository.save(devUser)
        log.info("[LocalDataInitializer] dev 用户已创建（id={}, email={}）", devUserId, devUser.email)
    }
}
