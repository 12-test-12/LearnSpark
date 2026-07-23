package com.learnspark.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * LearnSpark 后端主入口。
 *
 * 阶段 2.1：双进程部署
 * - 默认 profile (api)：app-api 模式，提供 REST API
 * - --spring.profiles.active=worker：app-worker 模式，仅跑定时轮询
 *   关键差异：worker profile 设了 `spring.main.web-application-type=none`，不启动 Web 容器
 *
 * 启动命令：
 *   java -Xmx512m  -jar app.jar                                  # API 服务
 *   java -Xmx1024m -jar app.jar --spring.profiles.active=worker  # Worker 服务
 *
 * 注意：class 必须 open（Spring @Configuration 需要 CGLIB 代理）
 *
 * 阶段 R2：
 * - @EnableAsync 让 SubmissionService.triggerAiReviewAsync 真正异步执行
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
@ConfigurationPropertiesScan
open class LearnSparkApplication

fun main(args: Array<String>) {
    runApplication<LearnSparkApplication>(*args)
}



