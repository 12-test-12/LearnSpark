package com.learnspark.data.db

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.learnspark.data.model.ProjectDto
import com.learnspark.db.LearnSparkDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 跨端项目仓库（封装 SQLDelight + 同步元数据）。
 *
 * - commonMain 共享：Android + Desktop 共用同一份业务逻辑
 * - 由 Koin 注入 [LearnSparkDb] (SQLDelight Database)
 */
class ProjectRepository(
    private val db: LearnSparkDb,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    fun observeActiveProjects(): Flow<List<ProjectDto>> =
        db.projectQueries.selectActiveProjects()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::rowToDto) }

    suspend fun getActiveProjects(): List<ProjectDto> =
        db.projectQueries.selectActiveProjects().executeAsList().map(::rowToDto)

    suspend fun getById(id: String): ProjectDto? =
        db.projectQueries.selectById(id).executeAsOneOrNull()?.let(::rowToDto)

    suspend fun upsert(p: ProjectDto) {
        db.projectQueries.upsertProject(
            id = p.id,
            name = p.name,
            goal = p.goal,
            dailyHours = p.dailyHours.toLong(),
            isAiGenerated = if (p.isAiGenerated) 1L else 0L,
            status = p.status,
            coverColor = p.coverColor,
            serverVersion = p.serverVersion.toLong(),
            localVersion = p.localVersion.toLong(),
            isDirty = if (p.isDirty) 1L else 0L,
            deletedAt = p.deletedAt,
            createdAt = p.createdAt,
            updatedAt = p.updatedAt,
        )
    }

    suspend fun markClean(id: String) {
        db.projectQueries.markClean(id, isoNow())
    }

    suspend fun markDirty(id: String) {
        db.projectQueries.markDirty(id, isoNow())
    }

    suspend fun softDelete(id: String) {
        db.projectQueries.softDelete(isoNow(), id, isoNow())
    }

    suspend fun getDirtyProjects(): List<ProjectDto> =
        db.projectQueries.selectDirty().executeAsList().map(::rowToDto)

    private fun rowToDto(row: com.learnspark.db.Project): ProjectDto = ProjectDto(
        id = row.id,
        name = row.name,
        goal = row.goal,
        dailyHours = row.dailyHours.toInt(),
        isAiGenerated = row.isAiGenerated == 1L,
        status = row.status,
        coverColor = row.coverColor,
        serverVersion = row.serverVersion.toInt(),
        localVersion = row.localVersion.toInt(),
        isDirty = row.isDirty == 1L,
        deletedAt = row.deletedAt,
        createdAt = row.createdAt,
        updatedAt = row.updatedAt,
    )

    private fun isoNow(): String {
        val instant = java.time.Instant.ofEpochMilli(clock())
        return java.time.OffsetDateTime.ofInstant(instant, java.time.ZoneOffset.UTC).toString()
    }
}

