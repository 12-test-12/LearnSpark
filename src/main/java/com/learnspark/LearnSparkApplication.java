package com.learnspark;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * LearnSpark 后端启动类。
 *
 * <p>采用单体模块化结构，按业务域划分顶层包：
 * <ul>
 *   <li>{@code auth}          用户与权限</li>
 *   <li>{@code plan}          学习项目、阶段、任务</li>
 *   <li>{@code ai}            AI 网关（DeepSeek / Bing Search）</li>
 *   <li>{@code kb}            知识库（文档解析、搜索）</li>
 *   <li>{@code notification}  通知与提醒</li>
 *   <li>{@code gamification}  积分与成就</li>
 *   <li>{@code common}        公共组件（配置、异常、安全、响应）</li>
 * </ul>
 */
@SpringBootApplication
@EnableJpaAuditing          // 启用 JPA 审计，自动维护 created_at / updated_at
@EnableScheduling           // 启用定时任务（邮件提醒等）
public class LearnSparkApplication {

    public static void main(String[] args) {
        SpringApplication.run(LearnSparkApplication.class, args);
    }
}
