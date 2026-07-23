package com.learnspark.server.api

import com.learnspark.server.domain.repository.KnowledgeEntryRepository
import com.learnspark.server.domain.repository.KnowledgeFolderRepository
import com.learnspark.server.domain.repository.PhaseRepository
import com.learnspark.server.domain.repository.ProjectRepository
import com.learnspark.server.domain.repository.ReminderLogRepository
import com.learnspark.server.domain.repository.ReminderSettingRepository
import com.learnspark.server.domain.repository.SubmissionRepository
import com.learnspark.server.domain.repository.TaskRepository
import com.learnspark.server.domain.repository.TaskUploadRepository
import com.learnspark.server.service.sync.SyncTableHandler
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * R1：同步端点（按文档 §3.4）。
 *
 * - POST /api/v1/sync/upload
 *   单向上传：客户端推本地脏数据。
 *   - 校验 baseVersion 是否匹配（乐观锁）
 *   - 匹配 → 写 + version+1
 *   - 不匹配 → 409 conflict + 返回服务端最新
 *
 * - GET /api/v1/sync/pull?since=...&limit=200
 *   拉取：since 之后的所有变更（按 updatedAt DESC）。
 *   - 首次拉取：since 留空
 *   - 支持多表：projects / phases / tasks / knowledge_entries
 *
 * R1 重构：路由逻辑下沉到 [SyncTableHandler] 列表，新增表只加 handler。
 */
@RestController
@RequestMapping("/api/v1/sync")
class SyncController(
    handlers: List<SyncTableHandler>,
    private val projectRepository: ProjectRepository,
    private val phaseRepository: PhaseRepository,
    private val taskRepository: TaskRepository,
    private val knowledgeRepository: KnowledgeEntryRepository,
    private val knowledgeFolderRepository: KnowledgeFolderRepository,
    private val submissionRepository: SubmissionRepository,
    private val reminderSettingRepository: ReminderSettingRepository,
    private val reminderLogRepository: ReminderLogRepository,
    private val taskUploadRepository: TaskUploadRepository,
) {
    // 表名 → handler 的 O(1) 路由索引
    private val handlerByTable: Map<String, SyncTableHandler> = handlers.associateBy { it.tableName }

    @PostMapping("/upload")
    fun upload(
        @RequestHeader("X-User-Id") userId: String,
        @RequestBody req: UploadRequest,
    ): ResponseEntity<Any> {
        val results = req.changes.map { change ->
            val handler = handlerByTable[change.table]
            if (handler == null) {
                UploadResult(change.id, status = "bad_request", serverVersion = 0L)
            } else {
                handler.apply(userId, change)
            }
        }
        return ResponseEntity.ok(mapOf("results" to results))
    }

    @GetMapping("/pull")
    fun pull(
        @RequestHeader("X-User-Id") userId: String,
        @RequestParam(required = false) since: String?,
        @RequestParam(defaultValue = "200") limit: Int,
    ): Map<String, Any?> {
        val size = limit.coerceIn(1, 500)
        val page = PageRequest.of(0, size)
        // 简化：一次性返回该用户所有表的有序记录（量小可接受；生产可加 updatedAt 索引 + 索引扫描分页）
        val records = mutableListOf<SyncRecord>()
        var latestUpdated: Instant? = null

        // projects
        projectRepository.findByUserIdOrderByUpdatedAtDesc(userId, page).content.forEach { p ->
            records += SyncRecord("projects", "upsert", p.id, projectPayload(p), p.version, p.updatedAt?.toString() ?: "")
        }
        // knowledge_entries
        knowledgeRepository.findByUserIdOrderByUpdatedAtDesc(userId, page).content.forEach { k ->
            records += SyncRecord("knowledge_entries", "upsert", k.id, knowledgePayload(k), k.version, k.updatedAt?.toString() ?: "")
        }
        // phases（跨级权限：经由 user 拥有的 project 过滤）
        val ownedProjectIds = projectRepository.findByUserIdOrderByUpdatedAtDesc(userId, page)
            .content.map { it.id }.toSet()
        if (ownedProjectIds.isNotEmpty()) {
            ownedProjectIds.forEach { pid ->
                phaseRepository.findByProjectId(pid).forEach { ph ->
                    records += SyncRecord(
                        "phases", "upsert", ph.id, phasePayload(ph), ph.version,
                        ph.updatedAt?.toString() ?: ""
                    )
                }
            }
        }
        // tasks（同样跨级）
        ownedProjectIds.forEach { pid ->
            phaseRepository.findByProjectId(pid).forEach { ph ->
                taskRepository.findByPhaseId(ph.id).forEach { t ->
                    records += SyncRecord(
                        "tasks", "upsert", t.id, taskPayload(t), t.version,
                        t.updatedAt?.toString() ?: ""
                    )
                }
            }
        }
        // submissions（按 userId 直接查，独立路由，不需跨 project 展开）
        submissionRepository.findByUserIdOrderByUpdatedAtDesc(userId, page).content.forEach { s ->
            records += SyncRecord(
                "submissions", "upsert", s.id, submissionPayload(s), s.version,
                s.updatedAt?.toString() ?: ""
            )
        }
        // knowledge_folders（按 userId）
        knowledgeFolderRepository.findByUserId(userId).forEach { f ->
            records += SyncRecord(
                "knowledge_folders", "upsert", f.id, knowledgeFolderPayload(f), f.version,
                f.updatedAt?.toString() ?: ""
            )
        }
        // reminder_settings
        reminderSettingRepository.findByUserId(userId).forEach { s ->
            records += SyncRecord(
                "reminder_settings", "upsert", s.id, reminderSettingPayload(s), s.version,
                s.updatedAt?.toString() ?: ""
            )
        }
        // reminder_logs（包含 firedAt 作为更新字段，不参与乐观锁）
        reminderLogRepository.findByUserIdOrderByFiredAtDesc(userId).take(size).forEach { l ->
            records += SyncRecord(
                "reminder_logs", "upsert", l.id, reminderLogPayload(l), 0L,
                l.firedAt.toString()
            )
        }
        // 排序：updatedAt DESC
        records.sortByDescending { it.updatedAt }
        // 截断到 size
        val trimmed = records.take(size)
        latestUpdated = trimmed.firstOrNull()?.updatedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
        return mapOf(
            "records" to trimmed,
            "latestUpdatedAt" to (latestUpdated?.toString() ?: since),
            "hasMore" to (records.size > size),
        )
    }

    // === DTOs ===

    data class UploadRequest(val changes: List<Change>)

    data class Change(
        val table: String,
        val operation: String,           // upsert / delete
        val id: String,
        val payload: Map<String, Any?>,
        val baseVersion: Long,
    )

    data class UploadResult(
        val id: String,
        val status: String,             // ok / conflict / forbidden / bad_request
        val serverVersion: Long,
        val latest: Map<String, Any?>? = null,
    )

    data class SyncRecord(
        val table: String,
        val operation: String,
        val id: String,
        val payload: Map<String, Any?>,
        val version: Long,
        val updatedAt: String,
    )

    // === payload 序列化（与 handler 保持一致） ===

    private fun projectPayload(p: com.learnspark.server.domain.entity.Project) = mapOf(
        "id" to p.id,
        "userId" to p.userId,
        "name" to p.name,
        "description" to p.description,
        "goal" to p.goal,
        "coverColor" to p.coverColor,
        "dailyHours" to p.dailyHours,
        "isAiGenerated" to p.isAiGenerated,
        "status" to p.status,
        "version" to p.version,
        "createdAt" to p.createdAt?.toString(),
        "updatedAt" to p.updatedAt?.toString(),
    )

    private fun phasePayload(p: com.learnspark.server.domain.entity.Phase) = mapOf(
        "id" to p.id,
        "projectId" to p.projectId,
        "name" to p.name,
        "description" to p.description,
        "sortOrder" to p.sortOrder,
        "startDate" to p.startDate?.toString(),
        "endDate" to p.endDate?.toString(),
        "status" to p.status,
        "version" to p.version,
        "createdAt" to p.createdAt?.toString(),
        "updatedAt" to p.updatedAt?.toString(),
    )

    private fun taskPayload(t: com.learnspark.server.domain.entity.Task) = mapOf(
        "id" to t.id,
        "phaseId" to t.phaseId,
        "title" to t.title,
        "description" to t.description,
        "sortOrder" to t.sortOrder,
        "estimatedHours" to t.estimatedHours,
        "actualHours" to t.actualHours,
        "status" to t.status,
        "dueDate" to t.dueDate?.toString(),
        "version" to t.version,
        "createdAt" to t.createdAt?.toString(),
        "updatedAt" to t.updatedAt?.toString(),
    )

    private fun knowledgePayload(k: com.learnspark.server.domain.entity.KnowledgeEntry) = mapOf(
        "id" to k.id,
        "userId" to k.userId,
        "folderId" to k.folderId,
        "title" to k.title,
        "content" to k.content,
        "sourceType" to k.sourceType.name,
        "sourcePath" to k.sourcePath,
        "fileSize" to k.fileSize,
        "fileType" to k.fileType,
        "parseStatus" to k.parseStatus.name,
        "tags" to k.tags,
        "version" to k.version,
        "createdAt" to k.createdAt?.toString(),
        "updatedAt" to k.updatedAt?.toString(),
    )

    private fun submissionPayload(s: com.learnspark.server.domain.entity.Submission) = mapOf(
        "id" to s.id,
        "taskId" to s.taskId,
        "userId" to s.userId,
        "content" to s.content,
        "aiScore" to s.aiScore,
        "aiFeedback" to s.aiFeedback,
        "aiHighlights" to s.aiHighlights,
        "reviewedAt" to s.reviewedAt?.toString(),
        "version" to s.version,
        "createdAt" to s.createdAt?.toString(),
        "updatedAt" to s.updatedAt?.toString(),
    )

    private fun taskUploadPayload(u: com.learnspark.server.domain.entity.TaskUpload) = mapOf(
        "id" to u.id,
        "taskId" to u.taskId,
        "userId" to u.userId,
        "knowledgeEntryId" to u.knowledgeEntryId,
        "folderId" to u.folderId,
        "fileName" to u.fileName,
        "fileType" to u.fileType,
        "fileSize" to u.fileSize,
        "uploadStatus" to u.uploadStatus,
        "parseError" to u.parseError,
        "version" to u.version,
        "createdAt" to u.createdAt?.toString(),
        "updatedAt" to u.updatedAt?.toString(),
    )

    private fun knowledgeFolderPayload(f: com.learnspark.server.domain.entity.KnowledgeFolder) = mapOf(
        "id" to f.id,
        "userId" to f.userId,
        "parentId" to f.parentId,
        "name" to f.name,
        "color" to f.color,
        "icon" to f.icon,
        "sortOrder" to f.sortOrder,
        "path" to f.path,
        "depth" to f.depth,
        "version" to f.version,
        "createdAt" to f.createdAt?.toString(),
        "updatedAt" to f.updatedAt?.toString(),
    )

    private fun reminderSettingPayload(s: com.learnspark.server.domain.entity.ReminderSetting) = mapOf(
        "id" to s.id,
        "userId" to s.userId,
        "type" to s.type.name,
        "title" to s.title,
        "message" to s.message,
        "targetId" to s.targetId,
        "triggerTime" to s.triggerTime.toString(),
        "repeatPattern" to s.repeatPattern.name,
        "weekdayMask" to s.weekdayMask,
        "nextFireAt" to s.nextFireAt?.toString(),
        "enabled" to s.enabled,
        "version" to s.version,
        "createdAt" to s.createdAt?.toString(),
        "updatedAt" to s.updatedAt?.toString(),
    )

    private fun reminderLogPayload(l: com.learnspark.server.domain.entity.ReminderLog) = mapOf(
        "id" to l.id,
        "userId" to l.userId,
        "settingId" to l.settingId,
        "firedAt" to l.firedAt.toString(),
        "title" to l.title,
        "message" to l.message,
        "targetId" to l.targetId,
        "acknowledged" to l.acknowledged,
    )
}
