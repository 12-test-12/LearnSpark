package com.learnspark.data.db

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.learnspark.data.model.KnowledgeFolderDto
import com.learnspark.db.LearnSparkDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * R3a：知识库文件夹仓库（SQLDelight + 服务端同步）。
 *
 * - 树结构维护在 client 端（由 service 调用 KnowledgeFolderService.refreshPathAndDescendants）
 * - 本表做"客户端缓存 + 同步状态"
 */
class KnowledgeFolderRepository(
    private val db: LearnSparkDb,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    fun observeAll(): Flow<List<KnowledgeFolderDto>> =
        db.knowledgeFolderQueries.selectAllFolders()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::rowToDto) }

    suspend fun getAll(): List<KnowledgeFolderDto> =
        db.knowledgeFolderQueries.selectAllFolders().executeAsList().map(::rowToDto)

    suspend fun getById(id: String): KnowledgeFolderDto? =
        db.knowledgeFolderQueries.selectFolderById(id).executeAsOneOrNull()?.let(::rowToDto)

    suspend fun upsert(dto: KnowledgeFolderDto) {
        db.knowledgeFolderQueries.upsertFolder(
            id = dto.id,
            userId = dto.userId,
            parentId = dto.parentId,
            name = dto.name,
            color = dto.color,
            icon = dto.icon,
            sortOrder = dto.sortOrder.toLong(),
            path = dto.path,
            depth = dto.depth.toLong(),
            serverVersion = dto.version,
            localVersion = dto.version,
            isDirty = 0L,
            createdAt = dto.createdAt ?: isoNow(),
            updatedAt = dto.updatedAt ?: isoNow(),
        )
    }

    suspend fun markDirty(dto: KnowledgeFolderDto) {
        db.knowledgeFolderQueries.markFolderDirty(dto.id, isoNow())
    }

    suspend fun softDelete(id: String) {
        db.knowledgeFolderQueries.softDeleteFolder(id, isoNow())
    }

    suspend fun getDirty(): List<KnowledgeFolderDto> =
        db.knowledgeFolderQueries.selectDirtyFolders().executeAsList().map(::rowToDto)

    private fun rowToDto(row: com.learnspark.db.KnowledgeFolder) = KnowledgeFolderDto(
        id = row.id,
        userId = row.userId,
        parentId = row.parentId,
        name = row.name,
        color = row.color,
        icon = row.icon,
        sortOrder = row.sortOrder.toInt(),
        path = row.path,
        depth = row.depth.toInt(),
        version = row.serverVersion,
        createdAt = row.createdAt,
        updatedAt = row.updatedAt,
    )

    private fun isoNow(): String {
        val instant = java.time.Instant.ofEpochMilli(clock())
        return java.time.OffsetDateTime.ofInstant(instant, java.time.ZoneOffset.UTC).toString()
    }
}
