package com.learnspark.server.service.sync

import com.learnspark.server.api.SyncController
import com.learnspark.server.domain.entity.TaskUpload
import com.learnspark.server.domain.repository.TaskUploadRepository
import org.springframework.stereotype.Component

/**
 * R5b：task_uploads 表同步 handler。
 *
 * 让移动端通过 /api/v1/sync/pull 拉到 PC 端刚上传到 task 的文件元数据。
 * 实际文件字节走 FileDownloadController 的 /file 端点下载。
 */
@Component
class TaskUploadSyncHandler(
    private val repository: TaskUploadRepository,
) : SyncTableHandler {
    override val tableName: String = "task_uploads"

    override fun apply(userId: String, change: SyncController.Change): SyncController.UploadResult {
        val payloadUserId = change.payload["userId"] as? String
        if (payloadUserId != userId) {
            return SyncController.UploadResult(change.id, status = "forbidden", serverVersion = 0L)
        }
        val taskId = change.payload["taskId"] as? String
        if (taskId.isNullOrBlank()) {
            return SyncController.UploadResult(change.id, status = "bad_request", serverVersion = 0L)
        }
        val existing = repository.findById(change.id).orElse(null)
        return if (existing == null) {
            if (change.baseVersion != 0L) {
                return SyncController.UploadResult(change.id, status = "conflict", serverVersion = 0L)
            }
            repository.save(toEntity(change, userId, version = 1L))
            SyncController.UploadResult(change.id, status = "ok", serverVersion = 1L)
        } else {
            if (existing.userId != userId) {
                return SyncController.UploadResult(change.id, status = "forbidden", serverVersion = 0L)
            }
            // task_uploads 是不可变记录（创建后很少修改）；允许 status / parseError 字段更新
            if (change.baseVersion != existing.version) {
                return SyncController.UploadResult(
                    id = change.id,
                    status = "conflict",
                    serverVersion = existing.version,
                    latest = toPayload(existing),
                )
            }
            val merged = toEntity(change, userId, version = existing.version + 1)
            merged.id = existing.id
            merged.createdAt = existing.createdAt
            merged.updatedAt = existing.updatedAt
            repository.save(merged)
            SyncController.UploadResult(change.id, status = "ok", serverVersion = existing.version + 1)
        }
    }

    override fun toPayload(entity: Any): Map<String, Any?> = toPayload(entity as TaskUpload)

    fun toPayload(t: TaskUpload): Map<String, Any?> = mapOf(
        "id" to t.id,
        "taskId" to t.taskId,
        "userId" to t.userId,
        "knowledgeEntryId" to t.knowledgeEntryId,
        "folderId" to t.folderId,
        "fileName" to t.fileName,
        "fileType" to t.fileType,
        "fileSize" to t.fileSize,
        "uploadStatus" to t.uploadStatus,
        "parseError" to t.parseError,
        "version" to t.version,
        "createdAt" to t.createdAt?.toString(),
        "updatedAt" to t.updatedAt?.toString(),
    )

    private fun toEntity(change: SyncController.Change, userId: String, version: Long): TaskUpload = TaskUpload(
        id = change.id,
        userId = userId,
        taskId = change.payload["taskId"] as? String ?: "",
        knowledgeEntryId = change.payload["knowledgeEntryId"] as? String,
        folderId = change.payload["folderId"] as? String,
        fileName = change.payload["fileName"] as? String ?: "",
        filePath = change.payload["filePath"] as? String ?: "",
        fileType = change.payload["fileType"] as? String ?: "",
        fileSize = (change.payload["fileSize"] as? Number)?.toLong() ?: 0L,
        uploadStatus = change.payload["uploadStatus"] as? String ?: "pending",
        parseError = change.payload["parseError"] as? String,
        version = version,
    )
}
