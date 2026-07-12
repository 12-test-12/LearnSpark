package com.learnspark.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AES 加密配置属性，绑定 application.yml 中的 app.aes。
 *
 * <p>主密钥用于加密用户存储的第三方 API Key（如 DeepSeek、Bing），
 * 至少 32 字符，生产环境应通过环境变量 {@code APP_AES_SECRET} 注入。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.aes")
public class AesProperties {

    /** AES 主密钥（经 SHA-256 派生为 256-bit 密钥），至少 32 字符 */
    private String secret;
}
