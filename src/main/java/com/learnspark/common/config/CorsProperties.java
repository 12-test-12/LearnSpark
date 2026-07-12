package com.learnspark.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * CORS 配置属性，绑定 application.yml 中的 app.cors。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    /** 允许的来源，逗号分隔 */
    private String allowedOrigins = "http://localhost:5173";
}
