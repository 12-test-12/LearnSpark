package com.learnspark.server.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.learnspark.server.domain.entity.Phase
import com.learnspark.server.domain.entity.Project
import com.learnspark.server.domain.entity.Submission
import com.learnspark.server.domain.entity.Task
import com.learnspark.server.domain.repository.PhaseRepository
import com.learnspark.server.domain.repository.ProjectRepository
import com.learnspark.server.domain.repository.SubmissionRepository
import com.learnspark.server.domain.repository.TaskRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * 阶段 3.1：旧版 Vue3 数据迁移服务（按文档 §11.2）。
 *
 * 旧版 Web 端导出 JSON 结构（参考 §11.2.1）：
 * ```
 * {
 *   "exportedAt": "2026-07-01T00:00:00.000Z",
 *   "version": 1,
 *   "data": {
 *     "projects":         [ { id, user_id, name, description, goal, cover_color, daily_hours, is_ai_generated, status, created_at, updated_at }, ... ],
 *     "phases":           [ { id, project_id, name, description, sort_order, start_date, end_date, status }, ... ],
 *     "tasks":            [ { id, phase_id, title, description, sort_order, estimated_hours, actual_hours, status, due_date }, ... ],
 *     "submissions":      [ { id, task_id, user_id, content_md, ai_score, ai_feedback, ai_highlights, reviewed_at }, ... ],
 *     "knowledge_entries":[ { ... }, ... ]
 *   }
 * }
 * ```
 *
 * 字段映射（按文档 §11.2.3）：
 * - projects.cover_color      →  projects.cover_color       （不变）
 * - projects.is_ai_generated  →  projects.is_ai_generated   （0/1 → boolean）
 * - phases.project_id         →  phases.project_id          （不变）
 * - tasks.phase_id            →  tasks.phase_id             （不变）
 * - submissions.content_md    →  submissions.content        （统一字段命名）
 * - knowledge_entries.title   →  knowledge_entries.title    （旧版可能为空，从首行提取）
 *
 * 冲突策略（按文档 §11.2.2）：
 * - 服务端已有数据 → 以服务端为准，导入数据只补充缺失记录
 */
@Service
class MigrationService(
    private val projectRepository: ProjectRepository,
    private val phaseRepository: PhaseRepository,
    private val taskRepository: TaskRepository,
    private val submissionRepository: SubmissionRepository,
    private val knowledgeService: KnowledgeService,
) {
    private val log = LoggerFactory.getLogger(MigrationService::class.java)
    private val mapper = ObjectMapper()
    private val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * 导入旧版 JSON 导出文件。
     *
     * @param userId 当前用户 ID（来自 X-User-Id header）
     * @param jsonText 旧版导出的 JSON 字符串
     */
    @Transactional
    fun importLegacy(userId: String, jsonText: String): ImportResult {
        val root = mapper.readTree(jsonText)
        val data = root.path("data")

        log.info("[migration] start import for user={} payloadVersion={}", userId, root.path("version").asInt(-1))

        val projectResult = importProjects(userId, data.path("projects"))
        val phaseResult = importPhases(userId, data.path("phases"))
        val taskResult = importTasks(data.path("tasks"))
        val submissionResult = importSubmissions(userId, data.path("submissions"))
        val knowledgeResult = importKnowledgeEntries(userId, data.path("knowledge_entries"))

        val total = ImportResult.Totals(
            projects = projectResult.total,
            phases = phaseResult.total,
            tasks = taskResult.total,
            submissions = submissionResult.total,
            knowledgeEntries = knowledgeResult.total,
        )
        val inserted = ImportResult.Totals(
            projects = projectResult.inserted,
            phases = phaseResult.inserted,
            tasks = taskResult.inserted,
            submissions = submissionResult.inserted,
            knowledgeEntries = knowledgeResult.inserted,
        )
        val skipped = ImportResult.Totals(
            projects = projectResult.skipped,
            phases = phaseResult.skipped,
            tasks = taskResult.skipped,
            submissions = submissionResult.skipped,
            knowledgeEntries = knowledgeResult.skipped,
        )
        log.info(
            "[migration] done user={} totals={} inserted={} skipped={}",
            userId, total, inserted, skipped
        )
        return ImportResult(
            exportedAt = root.path("exportedAt").asText(null),
            sourceVersion = root.path("version").asInt(-1),
            totals = total,
            inserted = inserted,
            skipped = skipped,
        )
    }

    // ====== Projects ======

    private fun importProjects(userId: String, node: com.fasterxml.jackson.databind.JsonNode): TableResult {
        if (node.isMissingNode || !node.isArray) return TableResult(0, 0, 0)
        var inserted = 0
        var skipped = 0
        for (p in node) {
            val id = p.path("id").asText(null) ?: continue
            val owner = p.path("user_id").asText(null)
            // 校验 ownership：不匹配则跳过（避免跨用户数据污染）
            if (owner != null && owner != userId) {
                log.warn("[migration] skip project={} owner mismatch ({} != {})", id, owner, userId)
                skipped++
                continue
            }
            // 已存在 → 跳过（以服务端为准）
            if (projectRepository.existsById(id)) {
                skipped++
                continue
            }
            val entity = Project(
                id = id,
                userId = userId,
                name = p.path("name").asText(""),
                description = p.path("description").asText(null),
                goal = p.path("goal").asText(null),
                coverColor = p.path("cover_color").asText(null),
                dailyHours = p.path("daily_hours").asInt(2),
                isAiGenerated = p.path("is_ai_generated").asInt(0) != 0,
                status = p.path("status").asText("active"),
                version = 0,
            )
            projectRepository.save(entity)
            inserted++
        }
        return TableResult(node.size(), inserted, skipped)
    }

    // ====== Phases ======

    private fun importPhases(userId: String, node: com.fasterxml.jackson.databind.JsonNode): TableResult {
        if (node.isMissingNode || !node.isArray) return TableResult(0, 0, 0)
        var inserted = 0
        var skipped = 0
        for (p in node) {
            val id = p.path("id").asText(null) ?: continue
            val projectId = p.path("project_id").asText(null) ?: continue
            // 父 project 必须存在且属于当前用户
            val project = projectRepository.findById(projectId).orElse(null)
            if (project == null || project.userId != userId) {
                log.warn("[migration] skip phase={} parent project missing or not owned by user", id)
                skipped++
                continue
            }
            if (phaseRepository.existsById(id)) {
                skipped++
                continue
            }
            val entity = Phase(
                id = id,
                projectId = projectId,
                name = p.path("name").asText(""),
                description = p.path("description").asText(null),
                sortOrder = p.path("sort_order").asInt(0),
                startDate = p.path("start_date").asText(null)?.let { runCatching { LocalDate.parse(it, dateFmt) }.getOrNull() },
                endDate = p.path("end_date").asText(null)?.let { runCatching { LocalDate.parse(it, dateFmt) }.getOrNull() },
                status = p.path("status").asText("pending"),
                version = 0,
            )
            phaseRepository.save(entity)
            inserted++
        }
        return TableResult(node.size(), inserted, skipped)
    }

    // ====== Tasks ======

    private fun importTasks(node: com.fasterxml.jackson.databind.JsonNode): TableResult {
        if (node.isMissingNode || !node.isArray) return TableResult(0, 0, 0)
        var inserted = 0
        var skipped = 0
        for (p in node) {
            val id = p.path("id").asText(null) ?: continue
            val phaseId = p.path("phase_id").asText(null) ?: continue
            // 父 phase 必须存在
            if (!phaseRepository.existsById(phaseId)) {
                log.warn("[migration] skip task={} parent phase missing", id)
                skipped++
                continue
            }
            if (taskRepository.existsById(id)) {
                skipped++
                continue
            }
            val entity = Task(
                id = id,
                phaseId = phaseId,
                title = p.path("title").asText(""),
                description = p.path("description").asText(null),
                sortOrder = p.path("sort_order").asInt(0),
                estimatedHours = p.path("estimated_hours").asInt(1),
                actualHours = p.path("actual_hours").asInt(0),
                status = p.path("status").asText("pending"),
                dueDate = p.path("due_date").asText(null)?.let { runCatching { LocalDate.parse(it, dateFmt) }.getOrNull() },
                version = 0,
            )
            taskRepository.save(entity)
            inserted++
        }
        return TableResult(node.size(), inserted, skipped)
    }

    // ====== Submissions ======

    private fun importSubmissions(userId: String, node: com.fasterxml.jackson.databind.JsonNode): TableResult {
        if (node.isMissingNode || !node.isArray) return TableResult(0, 0, 0)
        var inserted = 0
        var skipped = 0
        for (p in node) {
            val id = p.path("id").asText(null) ?: continue
            val taskId = p.path("task_id").asText(null) ?: continue
            val owner = p.path("user_id").asText(null)
            if (owner != null && owner != userId) {
                log.warn("[migration] skip submission={} owner mismatch", id)
                skipped++
                continue
            }
            if (!taskRepository.existsById(taskId)) {
                log.warn("[migration] skip submission={} parent task missing", id)
                skipped++
                continue
            }
            if (submissionRepository.existsById(id)) {
                skipped++
                continue
            }
            // 字段映射：content_md → content
            val content = p.path("content").asText(null) ?: p.path("content_md").asText("")
            val entity = Submission(
                id = id,
                taskId = taskId,
                userId = userId,
                content = content,
                aiScore = p.path("ai_score").asInt(-1).takeIf { it >= 0 },
                aiFeedback = p.path("ai_feedback").asText(null),
                aiHighlights = p.path("ai_highlights").asText(null),
                reviewedAt = p.path("reviewed_at").asText(null)?.let { runCatching { java.time.Instant.parse(it) }.getOrNull() },
                version = 0,
            )
            submissionRepository.save(entity)
            inserted++
        }
        return TableResult(node.size(), inserted, skipped)
    }

    // ====== Knowledge Entries ======

    private fun importKnowledgeEntries(userId: String, node: com.fasterxml.jackson.databind.JsonNode): TableResult {
        if (node.isMissingNode || !node.isArray) return TableResult(0, 0, 0)
        var inserted = 0
        var skipped = 0
        for (p in node) {
            val id = p.path("id").asText(null) ?: continue
            val owner = p.path("user_id").asText(null)
            if (owner != null && owner != userId) {
                log.warn("[migration] skip knowledge={} owner mismatch", id)
                skipped++
                continue
            }
            // 通过 knowledgeService 内部判重
            if (knowledgeService.get(id) != null) {
                skipped++
                continue
            }
            // 字段映射：title 为空时从 content 首行提取
            val content = p.path("content").asText(null)
            val titleFromContent = content?.lineSequence()?.firstOrNull()?.take(255)
            val title = p.path("title").asText(null) ?: titleFromContent ?: "(无标题)"
            knowledgeService.createLegacy(
                id = id,
                userId = userId,
                title = title,
                content = content,
                sourceType = p.path("source_type").asText("MANUAL"),
                sourcePath = p.path("source_path").asText(null),
                fileSize = p.path("file_size").asLong(-1).takeIf { it >= 0 },
                fileType = p.path("file_type").asText(null),
                tags = p.path("tags").asText(null),
            )
            inserted++
        }
        return TableResult(node.size(), inserted, skipped)
    }

    private data class TableResult(val total: Int, val inserted: Int, val skipped: Int)

    data class ImportResult(
        val exportedAt: String?,
        val sourceVersion: Int,
        val totals: Totals,
        val inserted: Totals,
        val skipped: Totals,
    ) {
        data class Totals(
            val projects: Int = 0,
            val phases: Int = 0,
            val tasks: Int = 0,
            val submissions: Int = 0,
            val knowledgeEntries: Int = 0,
        ) {
            val sum: Int get() = projects + phases + tasks + submissions + knowledgeEntries
        }
    }
}
