package com.learnspark.core.security

/**
 * 跨端安全存储抽象。
 *
 * 用途：保护本地 RefreshToken/AccessToken、用户主密码派生密钥等敏感数据。
 *
 * 平台实现策略：
 * - Android: Android Keystore (AES/GCM/NoPadding + KeyStore)
 * - Desktop Windows: JNA 调用 crypt32.dll 的 CryptProtectData / CryptUnprotectData (DPAPI)
 * - Desktop macOS: JNA 调用 Security.framework 的 SecKeychain (预留)
 * - Desktop Linux: 降级方案 PBKDF2 600k + AES-256-GCM + 主密码
 *
 * 返回值采用 Base64 编码的密文，方便存储到 DataStore / 文件。
 */
expect class SecureStorage {

    /**
     * 加密明文，返回 Base64 编码的密文。
     *
     * @param plaintext UTF-8 字符串
     * @return Base64 字符串
     */
    suspend fun encrypt(plaintext: String): String

    /**
     * 解密 Base64 编码的密文，返回 UTF-8 字符串。
     *
     * @param ciphertext Base64 字符串
     * @return 原文
     */
    suspend fun decrypt(ciphertext: String): String

    /**
     * 平台能力描述，仅用于诊断与日志。
     * - "android-keystore"
     * - "win-dpapi"
     * - "macos-keychain"
     * - "fallback-pbkdf2-aes"
     */
    fun platformName(): String
}

/**
 * 平台工厂：由各 actual 文件提供具体构造逻辑。
 * 用于 Koin DI 注入（阶段 1.1.6）。
 */
expect fun createSecureStorage(): SecureStorage
