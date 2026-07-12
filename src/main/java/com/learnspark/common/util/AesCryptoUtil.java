package com.learnspark.common.util;

import com.learnspark.common.config.AesProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 加密工具。
 *
 * <p>使用 GCM 模式提供机密性 + 完整性保护。每次加密生成随机 12 字节 IV，
 * 密文格式为 {@code Base64(IV || ciphertext+authTag)}。
 *
 * <p>SSOT：项目内所有对称加密都走本类，避免散落多处加密逻辑。
 */
@Component
@RequiredArgsConstructor
public class AesCryptoUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private final AesProperties aesProperties;

    private SecretKey secretKey;
    private SecureRandom secureRandom;

    @PostConstruct
    void init() {
        try {
            byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                    .digest(aesProperties.getSecret().getBytes(StandardCharsets.UTF_8));
            this.secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
            this.secureRandom = new SecureRandom();
        } catch (Exception ex) {
            throw new IllegalStateException("AES 密钥初始化失败", ex);
        }
    }

    /**
     * 加密明文，返回 Base64 编码的密文（含 IV）。
     *
     * @param plaintext 明文，为 null 或空时原样返回
     * @return Base64 密文，或 null/空字符串
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));

            byte[] cipherBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherBytes, 0, combined, iv.length, cipherBytes.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception ex) {
            throw new IllegalStateException("AES 加密失败", ex);
        }
    }

    /**
     * 解密 Base64 密文，返回明文。
     *
     * @param ciphertext Base64 密文，为 null 或空时原样返回
     * @return 明文，或 null/空字符串
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isEmpty()) {
            return ciphertext;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);

            byte[] iv = new byte[IV_BYTES];
            byte[] cipherBytes = new byte[combined.length - IV_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_BYTES);
            System.arraycopy(combined, IV_BYTES, cipherBytes, 0, cipherBytes.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));

            return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("AES 解密失败", ex);
        }
    }

    /**
     * 对 API Key 做脱敏处理：仅保留后 4 位，前面用 **** 代替。
     *
     * @param key 原始 key
     * @return 脱敏后的字符串，如 {@code ****abcd}
     */
    public static String mask(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        if (key.length() <= 4) {
            return "****";
        }
        return "****" + key.substring(key.length() - 4);
    }
}
