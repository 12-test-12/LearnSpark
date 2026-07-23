package com.learnspark.server.api

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.LinkedHashMap

/**
 * 健康检查。
 *
 * 阶段 2.1：用于 Docker Compose / k8s liveness 探针。
 * 也用于前端联调时确认服务在线。
 */
@RestController
@RequestMapping("/api/v1")
class HealthController {

    @GetMapping("/health")
    fun health(): Map<String, Any> = linkedMapOf(
        "status" to "UP",
        "service" to "learnspark-server",
        "version" to "1.0.0",
        "time" to Instant.now().toString(),
    )
}
