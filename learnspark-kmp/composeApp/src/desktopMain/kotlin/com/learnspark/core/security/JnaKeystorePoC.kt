package com.learnspark.core.security

import com.learnspark.core.di.coreModule
import com.learnspark.core.di.platformModule
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.java.KoinJavaComponent.inject

/**
 * 阶段 0.3 PoC 自测入口。
 *
 * 通过系统属性 `learnspark.poc.keystore=1` 触发。
 * 用法：
 *   java -Dlearnspark.poc.keystore=1 -cp LearnSpark-windows-x64-1.0.0.jar com.learnspark.MainKt
 *
 * 验证项：
 * 1. 平台检测正确（Windows: win-dpapi；其他: fallback-pbkdf2-aes）
 * 2. encrypt -> decrypt 往返一致
 * 3. JNA DPAPI 走通 CryptProtectData / CryptUnprotectData
 * 4. Fallback 走通 PBKDF2 + AES-GCM
 */
fun runSecureKeystorePoC(): Int = runBlocking {
    val ks = SecureStorage()
    val platform = ks.platformName()
    println("[0.3 PoC] platform=${platform}")

    if (platform == "fallback-pbkdf2-aes") {
        val pwd = System.getenv("LEARNSPARK_MASTER_PWD")
        if (pwd == null) {
            println("[0.3 PoC] SKIP: LEARNSPARK_MASTER_PWD not set. Fallback path not tested.")
            return@runBlocking 0
        }
    }

    val samples = listOf(
        "hello" to "world",
        "RefreshToken" to "eyJhbGciOiJIUzI1NiJ9.payload.signature",
        "中文/emoji 测试 " to "敏感数据"
    )

    var failed = 0
    for ((label, value) in samples) {
        val cipher = ks.encrypt(value)
        val plain = ks.decrypt(cipher)
        val ok = plain == value
        println("[0.3 PoC] ${if (ok) "PASS" else "FAIL"} $label: cipher.len=${cipher.length} -> '$plain'")
        if (!ok) failed++
    }

    // 篡改检测
    val cipher = ks.encrypt("integrity-test")
    val tampered = cipher.dropLast(4) + "AAAA"
    val tamperedOk = try {
        ks.decrypt(tampered); true
    } catch (e: Exception) {
        println("[0.3 PoC] PASS tamper: rejected (${e.javaClass.simpleName})")
        false
    }
    if (tamperedOk) {
        println("[0.3 PoC] FAIL tamper: tampered ciphertext was accepted!")
        failed++
    }

    if (failed == 0) {
        println("[0.3 PoC] ALL OK")
        0
    } else {
        println("[0.3 PoC] FAILED=$failed")
        1
    }
}

/**
 * 阶段 1.1.6 PoC：Koin 启动 + SecureStorage + EncryptedTokenStore 端到端。
 *
 * 通过系统属性 `learnspark.poc.koin=1` 触发。
 * 用法：
 *   java -Dlearnspark.poc.koin=1 -cp LearnSpark-windows-x64-1.0.0.jar com.learnspark.MainKt
 *
 * 验证项：
 * 1. Koin 加载 coreModule + platformModule 成功
 * 2. SecureStorage / TokenStore / ProjectRepository 都能从 Koin 取出
 * 3. EncryptedTokenStore.set -> get 往返一致（明文经过 SecureStorage 加密再走 PlatformTokenStore）
 * 4. 退出登录后 clearRaw 后再读，TokenStore 应返回 null
 */
fun runKoinPoC(): Int = runBlocking {
    println("[1.1.6 PoC] starting Koin ...")
    startKoin {
        modules(coreModule, platformModule())
    }
    try {
        val secure: SecureStorage by inject(SecureStorage::class.java)
        val tokenStore: TokenStore by inject(TokenStore::class.java)
        val platformToken: PlatformTokenStore by inject(PlatformTokenStore::class.java)
        val repo: com.learnspark.data.db.ProjectRepository by inject(
            com.learnspark.data.db.ProjectRepository::class.java
        )

        println("[1.1.6 PoC] secure.platform=${secure.platformName()}")
        println("[1.1.6 PoC] tokenStore=${tokenStore::class.simpleName}")
        println("[1.1.6 PoC] platformToken=${platformToken::class.simpleName}")
        println("[1.1.6 PoC] repository=${repo::class.simpleName}")

        // 1) 初始状态：clear
        tokenStore.clear()
        check(tokenStore.getAccessToken() == null) { "Expected null access token after clear" }
        check(tokenStore.getRefreshToken() == null) { "Expected null refresh token after clear" }
        println("[1.1.6 PoC] PASS clear -> both null")

        // 2) setTokens -> 写入密文 + 读取明文
        val accessIn = "access-${System.nanoTime()}"
        val refreshIn = "refresh-${System.nanoTime()}"
        tokenStore.setTokens(accessIn, refreshIn)

        val accessOut = tokenStore.getAccessToken()
        val refreshOut = tokenStore.getRefreshToken()
        check(accessOut == accessIn) { "access mismatch: $accessOut vs $accessIn" }
        check(refreshOut == refreshIn) { "refresh mismatch: $refreshOut vs $refreshIn" }
        println("[1.1.6 PoC] PASS set/get roundtrip access.len=${accessOut?.length} refresh.len=${refreshOut?.length}")

        // 3) 验证底层存储是密文（不能明文）
        val rawAccess = platformToken.getAccessRaw()
        val rawRefresh = platformToken.getRefreshRaw()
        check(rawAccess != null && rawAccess != accessIn) { "raw access should be encrypted, got=$rawAccess" }
        check(rawRefresh != null && rawRefresh != refreshIn) { "raw refresh should be encrypted, got=$rawRefresh" }
        println("[1.1.6 PoC] PASS raw stored as ciphertext (len=${rawAccess?.length}/${rawRefresh?.length})")

        // 4) 篡改检测：直接改写底层密文，TokenStore.getXxx 应返回 null 并自动清理
        val tampered = (rawAccess!!).dropLast(4) + "AAAA"
        platformToken.setRaw(tampered, rawRefresh)
        val tamperedAccess = tokenStore.getAccessToken()
        check(tamperedAccess == null) { "tampered access should be cleared, got=$tamperedAccess" }
        // 同时 raw 也应被清掉
        check(platformToken.getAccessRaw() == null) { "tampered raw access should be cleared" }
        println("[1.1.6 PoC] PASS tamper detection -> cleared")

        // 5) Repository 业务注入
        val now = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString()
        val dto = com.learnspark.data.model.ProjectDto(
            id = "poc-${System.nanoTime()}",
            name = "Koin PoC",
            goal = "verify Koin DI",
            createdAt = now,
            updatedAt = now,
        )
        repo.upsert(dto)
        val loaded = repo.getById(dto.id)
        check(loaded != null) { "upsert/getById roundtrip failed" }
        repo.softDelete(dto.id)
        println("[1.1.6 PoC] PASS repository.upsert/getById/softDelete")

        println("[1.1.6 PoC] ALL OK")
        0
    } catch (e: Throwable) {
        println("[1.1.6 PoC] FAILED: ${e.javaClass.simpleName}: ${e.message}")
        e.printStackTrace()
        1
    } finally {
        stopKoin()
    }
}
