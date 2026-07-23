package com.learnspark.server.common.crypto

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * R2：API key 等敏感字段 at-rest 加密（AES-256-GCM）。
 *
 * 加密格式（base64 解码后）：
 *   [ 12 bytes IV ][ ciphertext+16bytes GCM auth tag ]
 *
 * 主密钥来源：环境变量 LEARNSPARK_AES_KEY（base64-encoded 32 bytes）。
 * 缺失时启动失败（fail-fast）——避免生产环境用临时密钥静默运行。
 *
 * 为什么 AES-256-GCM：
 * - GCM 自带认证（auth tag），可防篡改；CBC 需额外 HMAC
 * - 12 字节 IV 是 GCM 标准；每条消息随机生成，避免重放
 *
 * 为什么不用 @Convert / AttributeConverter：
 * 加密应支持外部 KMS / HSM 替换；本服务是接口，未来可换实现。
 */
@Service
class AesCryptoService(
    @Value("\${learnspark.aes.key:#{null}}")
    private val base64MasterKey: String?,
) {
    private val key: SecretKeySpec by lazy { loadKey() }
    private val secureRandom = SecureRandom()

    init {
        // 启动校验：避免主密钥缺失导致运行时错误
        requireNotNull(base64MasterKey) { "LEARNSPARK_AES_KEY must be set" }
        require(Base64.getDecoder().decode(base64MasterKey).size == 32) {
            "LEARNSPARK_AES_KEY must decode to 32 bytes (AES-256)"
        }
    }

    /**
     * 加密：返回 base64(iv + ciphertext+tag)
     */
    fun encrypt(plaintext: String): String {
        val iv = ByteArray(IV_LEN).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val out = ByteArray(iv.size + ct.size).also {
            System.arraycopy(iv, 0, it, 0, iv.size)
            System.arraycopy(ct, 0, it, iv.size, ct.size)
        }
        return Base64.getEncoder().encodeToString(out)
    }

    /**
     * 解密：从 base64(iv + ciphertext+tag) 还原原文
     */
    fun decrypt(base64Cipher: String): String {
        val bytes = Base64.getDecoder().decode(base64Cipher)
        require(bytes.size > IV_LEN) { "cipher too short" }
        val iv = bytes.copyOfRange(0, IV_LEN)
        val ct = bytes.copyOfRange(IV_LEN, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }

    /**
     * 脱敏：把明文 API key 转为 "sk-***last4"，用于 GET 响应
     */
    fun mask(plaintext: String): String {
        if (plaintext.length <= 8) return "***"
        val last4 = plaintext.takeLast(4)
        return "***$last4"
    }

    private fun loadKey(): SecretKeySpec {
        val keyBytes = Base64.getDecoder().decode(base64MasterKey)
        return SecretKeySpec(keyBytes, "AES")
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LEN = 12
        private const val TAG_BITS = 128
    }
}
