package com.learnspark.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置属性，绑定 application.yml 中的 app.jwt。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** 签名密钥（至少 32 字符） */
    private String secret;

    /** 过期时间（毫秒），默认 24 小时 */
    private long expiration = 86400000L;

    /** 请求头名称 */
    private String header = "Authorization";

    /** Token 前缀 */
    private String prefix = "Bearer ";
}
