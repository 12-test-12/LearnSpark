package com.learnspark.server.api

import com.learnspark.server.service.MigrationService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 阶段 3.1：旧版 Vue3 数据迁移端点（按文档 §11.2.2）。
 *
 * - POST /api/v1/migration/import
 *   Body: 旧版导出的 JSON 字符串（text/plain 或 application/json）
 *   Header: X-User-Id（必填，校验导入数据归属）
 *
 * 流程（按文档 §11.2.2）：
 *   1. 校验 user_id 匹配（数据中 user_id 必须等于 X-User-Id）
 *   2. 字段映射（content_md → content 等）
 *   3. 冲突处理：服务端已有数据 → 跳过（以服务端为准）
 *   4. 返回插入/跳过的记录数
 */
@RestController
@RequestMapping("/api/v1/migration")
class MigrationController(
    private val migrationService: MigrationService,
) {
    private val log = LoggerFactory.getLogger(MigrationController::class.java)

    @PostMapping("/import", consumes = ["application/json", "text/plain", "text/json"])
    fun import(
        @RequestHeader("X-User-Id") userId: String,
        @RequestBody body: String,
    ): ResponseEntity<Any> {
        if (body.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "empty_body"))
        }
        return try {
            val result = migrationService.importLegacy(userId, body)
            log.info("[migration] user={} inserted={} skipped={}", userId, result.inserted.sum, result.skipped.sum)
            ResponseEntity.ok(
                mapOf(
                    "status" to "ok",
                    "exportedAt" to result.exportedAt,
                    "sourceVersion" to result.sourceVersion,
                    "inserted" to mapOf(
                        "projects" to result.inserted.projects,
                        "phases" to result.inserted.phases,
                        "tasks" to result.inserted.tasks,
                        "submissions" to result.inserted.submissions,
                        "knowledgeEntries" to result.inserted.knowledgeEntries,
                        "total" to result.inserted.sum,
                    ),
                    "skipped" to mapOf(
                        "projects" to result.skipped.projects,
                        "phases" to result.skipped.phases,
                        "tasks" to result.skipped.tasks,
                        "submissions" to result.skipped.submissions,
                        "knowledgeEntries" to result.skipped.knowledgeEntries,
                        "total" to result.skipped.sum,
                    ),
                )
            )
        } catch (e: Exception) {
            log.error("[migration] import failed for user={}", userId, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                mapOf("error" to "import_failed", "message" to (e.message ?: e.javaClass.simpleName))
            )
        }
    }
}
