package com.learnspark.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 跨端项目 DTO。
 *
 * - commonMain 共享：客户端本地缓存 + 同步线协议 + 业务 UI 共用
 * - 与服务端 [com.learnspark.plan.dto.ProjectResponse] 字段一一对应
 */
@Serializable
data class ProjectDto(
    val id: String,
    val name: String,
    val goal: String? = null,
    val dailyHours: Int = 2,
    val isAiGenerated: Boolean = false,
    val status: String = "active",
    val coverColor: String = "#18a058",
    val serverVersion: Int = 0,
    val localVersion: Int = 1,
    val isDirty: Boolean = true,
    val deletedAt: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class ProjectListResponse(
    val projects: List<ProjectDto> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class ProjectPushResponse(
    val accepted: List<ProjectDto> = emptyList(),
    val conflicts: List<ProjectConflict> = emptyList(),
)

@Serializable
data class ProjectConflict(
    val id: String,
    @SerialName("serverVersion") val serverVersion: Int,
    val server: ProjectDto,
)
