package com.learnspark.data.api

import com.learnspark.core.network.createHttpClient
import com.learnspark.core.security.TokenStore
import com.learnspark.data.model.ProjectDto
import com.learnspark.data.model.ProjectListResponse
import com.learnspark.data.model.ProjectPushResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * LearnSpark API 客户端（Ktor + JWT + RefreshToken 自动刷新）。
 *
 * - 平台无关：只依赖 [TokenStore] 与 [createHttpClient]
 * - 401 自动使用 RefreshToken 刷新后重放
 */
class LearnSparkApi(
    private val baseUrl: String,
    private val tokenStore: TokenStore,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    val httpClient: HttpClient = createHttpClient {
        install(ContentNegotiation) { json(json) }
        install(Auth) {
            bearer {
                loadTokens {
                    val a = tokenStore.getAccessToken()
                    val r = tokenStore.getRefreshToken()
                    if (!a.isNullOrEmpty() && !r.isNullOrEmpty()) BearerTokens(a, r) else null
                }
                refreshTokens {
                    val refresh = oldTokens?.refreshToken ?: return@refreshTokens null
                    val resp = client.post("$baseUrl/api/v1/auth/refresh") {
                        contentType(ContentType.Application.Json)
                        setBody(RefreshRequest(refresh))
                    }
                    if (resp.status == HttpStatusCode.OK) {
                        val body: RefreshResponse = resp.body()
                        tokenStore.setTokens(body.accessToken, body.refreshToken)
                        BearerTokens(body.accessToken, body.refreshToken)
                    } else {
                        tokenStore.clear()
                        null
                    }
                }
                sendWithoutRequest { req -> req.url.toString().contains("/auth/") }
            }
        }
    }

    suspend fun pullProjects(cursor: String?): ProjectListResponse {
        val url = if (cursor == null) "$baseUrl/api/v1/sync/projects"
        else "$baseUrl/api/v1/sync/projects?cursor=$cursor"
        return httpClient.get(url).body()
    }

    suspend fun pushProjects(projects: List<ProjectDto>): ProjectPushResponse =
        httpClient.post("$baseUrl/api/v1/sync/projects") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("changes" to projects))
        }.body()

    /**
     * 阶段 3.1：旧版 Vue3 数据迁移导入（按文档 §11.2.2）。
     *
     * 服务端 /api/v1/migration/import 接收旧版导出的 JSON，
     * 校验 userId、字段映射、写入新表。冲突以服务端为准。
     */
    suspend fun importLegacy(jsonText: String, userId: String): MigrationResult {
        val resp = httpClient.post("$baseUrl/api/v1/migration/import") {
            contentType(ContentType.Application.Json)
            // 服务端按 X-User-Id 校验当前用户，避免跨用户数据污染
            headers { append("X-User-Id", userId) }
            setBody(jsonText)
        }
        val body: JsonObject = resp.body()
        val insertedNode = body["inserted"] as? JsonObject
        val skippedNode = body["skipped"] as? JsonObject
        val insertedTotal = (insertedNode?.get("total") as? JsonPrimitive)?.intOrNull ?: 0
        val skippedTotal = (skippedNode?.get("total") as? JsonPrimitive)?.intOrNull ?: 0
        val status = (body["status"] as? JsonPrimitive)?.contentOrNull ?: "ok"
        return MigrationResult(
            status = status,
            insertedTotal = insertedTotal,
            skippedTotal = skippedTotal,
        )
    }

    // === R3a：知识库文件夹 ===

    suspend fun listFolders(userId: String): List<Map<String, Any?>> {
        val body: JsonObject = httpClient.get("$baseUrl/api/v1/knowledge/folders/tree") {
            headers { append("X-User-Id", userId) }
        }.body()
        @Suppress("UNCHECKED_CAST")
        return (body["items"] as? kotlinx.serialization.json.JsonArray)
            ?.map { (it as JsonObject).toMap() } ?: emptyList()
    }

    suspend fun createFolder(
        userId: String,
        name: String,
        parentId: String? = null,
        color: String? = null,
        icon: String? = null,
    ): Map<String, Any?> {
        val resp = httpClient.post("$baseUrl/api/v1/knowledge/folders") {
            contentType(ContentType.Application.Json)
            headers { append("X-User-Id", userId) }
            setBody(
                kotlinx.serialization.json.buildJsonObject {
                    put("name", kotlinx.serialization.json.JsonPrimitive(name))
                    parentId?.let { put("parentId", kotlinx.serialization.json.JsonPrimitive(it)) }
                    color?.let { put("color", kotlinx.serialization.json.JsonPrimitive(it)) }
                    icon?.let { put("icon", kotlinx.serialization.json.JsonPrimitive(it)) }
                }
            )
        }
        return (resp.body() as JsonObject).toMap()
    }

    suspend fun updateFolder(
        userId: String,
        id: String,
        name: String? = null,
        color: String? = null,
        sortOrder: Int? = null,
    ): Map<String, Any?> {
        val resp = httpClient.patch("$baseUrl/api/v1/knowledge/folders/$id") {
            contentType(ContentType.Application.Json)
            headers { append("X-User-Id", userId) }
            setBody(
                kotlinx.serialization.json.buildJsonObject {
                    name?.let { put("name", kotlinx.serialization.json.JsonPrimitive(it)) }
                    color?.let { put("color", kotlinx.serialization.json.JsonPrimitive(it)) }
                    sortOrder?.let { put("sortOrder", kotlinx.serialization.json.JsonPrimitive(it)) }
                }
            )
        }
        return (resp.body() as JsonObject).toMap()
    }

    suspend fun moveFolder(userId: String, id: String, newParentId: String?): Map<String, Any?> {
        val resp = httpClient.post("$baseUrl/api/v1/knowledge/folders/$id/move") {
            contentType(ContentType.Application.Json)
            headers { append("X-User-Id", userId) }
            setBody(
                kotlinx.serialization.json.buildJsonObject {
                    if (newParentId != null) put("newParentId", kotlinx.serialization.json.JsonPrimitive(newParentId))
                    else put("newParentId", kotlinx.serialization.json.JsonNull)
                }
            )
        }
        return (resp.body() as JsonObject).toMap()
    }

    suspend fun deleteFolder(userId: String, id: String) {
        httpClient.delete("$baseUrl/api/v1/knowledge/folders/$id") {
            headers { append("X-User-Id", userId) }
        }
    }

    // === R3b：提醒 ===

    suspend fun listReminderSettings(userId: String): List<Map<String, Any?>> {
        val body: JsonObject = httpClient.get("$baseUrl/api/v1/reminders/settings") {
            headers { append("X-User-Id", userId) }
        }.body()
        @Suppress("UNCHECKED_CAST")
        return (body["items"] as? kotlinx.serialization.json.JsonArray)
            ?.map { (it as JsonObject).toMap() } ?: emptyList()
    }

    suspend fun createReminderSetting(
        userId: String,
        title: String,
        message: String?,
        triggerTime: String,        // "HH:mm"
        repeatPattern: String,      // daily/weekdays/weekly/once
        weekdayMask: Int,
        enabled: Boolean,
        targetId: String? = null,
        type: String = "CUSTOM",
    ): Map<String, Any?> {
        val resp = httpClient.post("$baseUrl/api/v1/reminders/settings") {
            contentType(ContentType.Application.Json)
            headers { append("X-User-Id", userId) }
            setBody(
                kotlinx.serialization.json.buildJsonObject {
                    put("title", kotlinx.serialization.json.JsonPrimitive(title))
                    message?.let { put("message", kotlinx.serialization.json.JsonPrimitive(it)) }
                    put("triggerTime", kotlinx.serialization.json.JsonPrimitive(triggerTime))
                    put("repeatPattern", kotlinx.serialization.json.JsonPrimitive(repeatPattern))
                    put("weekdayMask", kotlinx.serialization.json.JsonPrimitive(weekdayMask))
                    put("enabled", kotlinx.serialization.json.JsonPrimitive(enabled))
                    put("type", kotlinx.serialization.json.JsonPrimitive(type))
                    targetId?.let { put("targetId", kotlinx.serialization.json.JsonPrimitive(it)) }
                }
            )
        }
        return (resp.body() as JsonObject).toMap()
    }

    suspend fun updateReminderSetting(
        userId: String,
        id: String,
        title: String? = null,
        message: String? = null,
        triggerTime: String? = null,
        repeatPattern: String? = null,
        weekdayMask: Int? = null,
        enabled: Boolean? = null,
    ): Map<String, Any?> {
        val resp = httpClient.patch("$baseUrl/api/v1/reminders/settings/$id") {
            contentType(ContentType.Application.Json)
            headers { append("X-User-Id", userId) }
            setBody(
                kotlinx.serialization.json.buildJsonObject {
                    title?.let { put("title", kotlinx.serialization.json.JsonPrimitive(it)) }
                    message?.let { put("message", kotlinx.serialization.json.JsonPrimitive(it)) }
                    triggerTime?.let { put("triggerTime", kotlinx.serialization.json.JsonPrimitive(it)) }
                    repeatPattern?.let { put("repeatPattern", kotlinx.serialization.json.JsonPrimitive(it)) }
                    weekdayMask?.let { put("weekdayMask", kotlinx.serialization.json.JsonPrimitive(it)) }
                    enabled?.let { put("enabled", kotlinx.serialization.json.JsonPrimitive(it)) }
                }
            )
        }
        return (resp.body() as JsonObject).toMap()
    }

    suspend fun deleteReminderSetting(userId: String, id: String) {
        httpClient.delete("$baseUrl/api/v1/reminders/settings/$id") {
            headers { append("X-User-Id", userId) }
        }
    }

    suspend fun listReminderLogs(userId: String, pendingOnly: Boolean): List<Map<String, Any?>> {
        val body: JsonObject = httpClient.get(
            if (pendingOnly) "$baseUrl/api/v1/reminders/logs?pending=true"
            else "$baseUrl/api/v1/reminders/logs"
        ) {
            headers { append("X-User-Id", userId) }
        }.body()
        @Suppress("UNCHECKED_CAST")
        return (body["items"] as? kotlinx.serialization.json.JsonArray)
            ?.map { (it as JsonObject).toMap() } ?: emptyList()
    }

    suspend fun ackReminderLog(userId: String, id: String) {
        httpClient.post("$baseUrl/api/v1/reminders/logs/$id/ack") {
            headers { append("X-User-Id", userId) }
        }
    }

    suspend fun ackAllReminderLogs(userId: String) {
        httpClient.post("$baseUrl/api/v1/reminders/logs/ack-all") {
            headers { append("X-User-Id", userId) }
        }
    }

    // === R3c：知识库 AI 整理 ===

    suspend fun suggestOrganize(
        userId: String,
        entryIds: List<String>?,
    ): List<Map<String, Any?>> {
        val resp = httpClient.post("$baseUrl/api/v1/knowledge/organize/suggest") {
            contentType(ContentType.Application.Json)
            headers { append("X-User-Id", userId) }
            setBody(
                kotlinx.serialization.json.buildJsonObject {
                    if (entryIds == null) put("entryIds", kotlinx.serialization.json.JsonNull)
                    else put(
                        "entryIds",
                        kotlinx.serialization.json.JsonArray(
                            entryIds.map { kotlinx.serialization.json.JsonPrimitive(it) }
                        )
                    )
                }
            )
        }
        val body: JsonObject = resp.body()
        @Suppress("UNCHECKED_CAST")
        return (body["suggestions"] as? kotlinx.serialization.json.JsonArray)
            ?.map { (it as JsonObject).toMap() } ?: emptyList()
    }

    suspend fun applyOrganize(
        userId: String,
        acceptances: List<Triple<String, String?, String?>>,
    ): Map<String, Any?> {
        val resp = httpClient.post("$baseUrl/api/v1/knowledge/organize/apply") {
            contentType(ContentType.Application.Json)
            headers { append("X-User-Id", userId) }
            setBody(
                kotlinx.serialization.json.buildJsonObject {
                    put(
                        "acceptances",
                        kotlinx.serialization.json.JsonArray(
                            acceptances.map { (eid, fid, _) ->
                                kotlinx.serialization.json.buildJsonObject {
                                    put("entryId", kotlinx.serialization.json.JsonPrimitive(eid))
                                    if (fid != null) put("folderId", kotlinx.serialization.json.JsonPrimitive(fid))
                                    else put("folderId", kotlinx.serialization.json.JsonNull)
                                }
                            }
                        )
                    )
                }
            )
        }
        return (resp.body() as JsonObject).toMap()
    }

    // === R4a：AI provider 元数据 ===

    suspend fun listAiProviders(): List<Map<String, Any?>> {
        val body: JsonObject = httpClient.get("$baseUrl/api/v1/ai/providers").body()
        @Suppress("UNCHECKED_CAST")
        return (body["items"] as? kotlinx.serialization.json.JsonArray)
            ?.map { (it as JsonObject).toMap() } ?: emptyList()
    }

    /**
     * 列出当前用户已配置的 AI 通道。
     */
    suspend fun listAiConfigs(userId: String): List<Map<String, Any?>> {
        val body: JsonObject = httpClient.get("$baseUrl/api/v1/ai/configs") {
            headers { append("X-User-Id", userId) }
        }.body()
        @Suppress("UNCHECKED_CAST")
        return (body["items"] as? kotlinx.serialization.json.JsonArray)
            ?.map { (it as JsonObject).toMap() } ?: emptyList()
    }

    /**
     * 新建或更新 AI 配置（按 userId+provider upsert）。
     */
    suspend fun upsertAiConfig(
        userId: String,
        provider: String,
        apiKey: String,
        model: String,
        baseUrl: String? = null,
        maxTokens: Int = 2048,
        temperature: Double = 0.7,
    ): Map<String, Any?> {
        val resp = httpClient.post("$baseUrl/api/v1/ai/configs") {
            contentType(ContentType.Application.Json)
            headers { append("X-User-Id", userId) }
            setBody(
                kotlinx.serialization.json.buildJsonObject {
                    put("provider", kotlinx.serialization.json.JsonPrimitive(provider))
                    put("apiKey", kotlinx.serialization.json.JsonPrimitive(apiKey))
                    put("model", kotlinx.serialization.json.JsonPrimitive(model))
                    if (baseUrl != null) put("baseUrl", kotlinx.serialization.json.JsonPrimitive(baseUrl))
                    put("maxTokens", kotlinx.serialization.json.JsonPrimitive(maxTokens))
                    put("temperature", kotlinx.serialization.json.JsonPrimitive(temperature))
                    put("enabled", kotlinx.serialization.json.JsonPrimitive(true))
                }
            )
        }
        return (resp.body() as JsonObject).toMap()
    }

    suspend fun deleteAiConfig(userId: String, id: String) {
        httpClient.delete("$baseUrl/api/v1/ai/configs/$id") {
            headers { append("X-User-Id", userId) }
        }
    }

    // === R4c：任务上传 + AI 标注可参考文章 ===

    /**
     * 上传文件到 task（绑定到 task + 可选 folderId）。
     * fileBytes: 文件原始字节
     * fileName: 含扩展名的文件名
     * folderId: 可选，归属的知识库文件夹
     */
    suspend fun uploadToTask(
        userId: String,
        taskId: String,
        fileBytes: ByteArray,
        fileName: String,
        folderId: String? = null,
    ): Map<String, Any?> {
        val resp = httpClient.post("$baseUrl/api/v1/tasks/$taskId/uploads") {
            headers { append("X-User-Id", userId) }
            setBody(
                io.ktor.client.request.forms.MultiPartFormDataContent(
                    io.ktor.client.request.forms.formData {
                        append(
                            "file",
                            fileBytes,
                            io.ktor.http.Headers.build {
                                append(io.ktor.http.HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                                append(io.ktor.http.HttpHeaders.ContentType, "application/octet-stream")
                            }
                        )
                        folderId?.let { append("folderId", it) }
                    }
                )
            )
        }
        return (resp.body() as JsonObject).toMap()
    }

    suspend fun listTaskUploads(userId: String, taskId: String): List<Map<String, Any?>> {
        val body: JsonObject = httpClient.get("$baseUrl/api/v1/tasks/$taskId/uploads") {
            headers { append("X-User-Id", userId) }
        }.body()
        @Suppress("UNCHECKED_CAST")
        return (body["items"] as? kotlinx.serialization.json.JsonArray)
            ?.map { (it as JsonObject).toMap() } ?: emptyList()
    }

    suspend fun deleteTaskUpload(userId: String, taskId: String, uploadId: String) {
        httpClient.delete("$baseUrl/api/v1/tasks/$taskId/uploads/$uploadId") {
            headers { append("X-User-Id", userId) }
        }
    }

    /**
     * 触发 AI 扫描，AI 推荐 top-K 知识库文章。
     * @param provider 可选，传入时使用对应 provider（"deepseek"/"openai"/...），否则用默认
     */
    suspend fun suggestArticleLinks(
        userId: String,
        taskId: String,
        provider: String? = null,
    ): Map<String, Any?> {
        val resp = httpClient.post("$baseUrl/api/v1/tasks/$taskId/article-links/suggest") {
            contentType(ContentType.Application.Json)
            headers { append("X-User-Id", userId) }
            setBody(
                kotlinx.serialization.json.buildJsonObject {
                    if (provider != null) put("provider", kotlinx.serialization.json.JsonPrimitive(provider))
                    else put("provider", kotlinx.serialization.json.JsonNull)
                }
            )
        }
        return (resp.body() as JsonObject).toMap()
    }

    suspend fun listArticleLinks(userId: String, taskId: String): List<Map<String, Any?>> {
        val body: JsonObject = httpClient.get("$baseUrl/api/v1/tasks/$taskId/article-links") {
            headers { append("X-User-Id", userId) }
        }.body()
        @Suppress("UNCHECKED_CAST")
        return (body["items"] as? kotlinx.serialization.json.JsonArray)
            ?.map { (it as JsonObject).toMap() } ?: emptyList()
    }

    suspend fun deleteArticleLink(userId: String, taskId: String, entryId: String) {
        httpClient.delete("$baseUrl/api/v1/tasks/$taskId/article-links/$entryId") {
            headers { append("X-User-Id", userId) }
        }
    }

    suspend fun addManualArticleLink(
        userId: String,
        taskId: String,
        entryId: String,
        reason: String,
    ): Map<String, Any?> {
        val resp = httpClient.post("$baseUrl/api/v1/tasks/$taskId/article-links") {
            contentType(ContentType.Application.Json)
            headers { append("X-User-Id", userId) }
            setBody(
                kotlinx.serialization.json.buildJsonObject {
                    put("entryId", kotlinx.serialization.json.JsonPrimitive(entryId))
                    put("reason", kotlinx.serialization.json.JsonPrimitive(reason))
                }
            )
        }
        return (resp.body() as JsonObject).toMap()
    }

    // === R5c：跨端文件下载 ===

    /**
     * 下载知识库条目的原始文件字节（PDF / 图片 / 二进制等）。
     * 内部走流式 IOChannel 读取，避免 OOM。
     */
    suspend fun downloadKnowledgeFile(userId: String, entryId: String): ByteArray {
        val resp = httpClient.get("$baseUrl/api/v1/knowledge/$entryId/file") {
            headers { append("X-User-Id", userId) }
        }
        return withContext(Dispatchers.IO) {
            resp.bodyAsChannel().toInputStream().use { it.readBytes() }
        }
    }

    /**
     * 获取知识库条目已解析的纯文本。
     * 返回 null 表示服务端返回 422（尚未解析成功或非文本类型）。
     */
    suspend fun getKnowledgeText(userId: String, entryId: String): String? {
        val resp = httpClient.get("$baseUrl/api/v1/knowledge/$entryId/text") {
            headers { append("X-User-Id", userId) }
        }
        if (resp.status == HttpStatusCode.UnprocessableEntity || resp.status == HttpStatusCode.NotFound) {
            return null
        }
        return resp.bodyAsText()
    }

    /**
     * 获取知识库条目元数据（用于客户端决定走哪条查看路径）。
     */
    suspend fun getKnowledgeMeta(userId: String, entryId: String): Map<String, Any?> {
        val resp = httpClient.get("$baseUrl/api/v1/knowledge/$entryId/meta") {
            headers { append("X-User-Id", userId) }
        }
        return (resp.body() as JsonObject).toMap()
    }

    /**
     * 列出知识库条目。folderId 为 null 时查询"未分类"，省略时列出全部。
     */
    suspend fun listKnowledgeEntries(
        userId: String,
        page: Int = 0,
        size: Int = 50,
    ): List<Map<String, Any?>> {
        val body: JsonObject = httpClient.get("$baseUrl/api/v1/knowledge?page=$page&size=$size") {
            headers { append("X-User-Id", userId) }
        }.body()
        @Suppress("UNCHECKED_CAST")
        return (body["items"] as? kotlinx.serialization.json.JsonArray)
            ?.map { (it as JsonObject).toMap() } ?: emptyList()
    }

    /**
     * 删除知识库条目。
     */
    suspend fun deleteKnowledgeEntry(userId: String, id: String) {
        httpClient.delete("$baseUrl/api/v1/knowledge/$id") {
            headers { append("X-User-Id", userId) }
        }
    }

    /**
     * R8：上传文件到知识库（支持批量调用）。
     * 调用 POST /api/v1/knowledge/upload，服务端保存文件并创建 KnowledgeEntry。
     */
    suspend fun uploadKnowledgeFile(
        userId: String,
        fileBytes: ByteArray,
        fileName: String,
        title: String? = null,
    ): Map<String, Any?> {
        val resp = httpClient.post("$baseUrl/api/v1/knowledge/upload") {
            headers { append("X-User-Id", userId) }
            setBody(
                io.ktor.client.request.forms.MultiPartFormDataContent(
                    io.ktor.client.request.forms.formData {
                        append(
                            "file",
                            fileBytes,
                            io.ktor.http.Headers.build {
                                append(io.ktor.http.HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                                append(io.ktor.http.HttpHeaders.ContentType, "application/octet-stream")
                            }
                        )
                        title?.let { append("title", it) }
                    }
                )
            )
        }
        return (resp.body() as JsonObject).toMap()
    }

    /**
     * 下载 task 上传的文件字节。
     */
    suspend fun downloadTaskUploadFile(userId: String, taskId: String, uploadId: String): ByteArray {
        val resp = httpClient.get("$baseUrl/api/v1/tasks/$taskId/uploads/$uploadId/file") {
            headers { append("X-User-Id", userId) }
        }
        return withContext(Dispatchers.IO) {
            resp.bodyAsChannel().toInputStream().use { it.readBytes() }
        }
    }

    fun close() = httpClient.close()

    @Serializable
    private data class RefreshRequest(val refreshToken: String)

    @Serializable
    private data class RefreshResponse(
        val accessToken: String,
        val refreshToken: String,
    )
}
