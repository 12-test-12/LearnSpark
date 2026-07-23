package com.learnspark.server.service.sync

import com.learnspark.server.api.SyncController

/**
 * R1：同步表 handler 接口。
 *
 * 每张可同步表对应一个 @Component handler，SyncController 通过 tableName 路由。
 * 这样新增表只需添加一个 handler，无需修改 SyncController 的路由逻辑（OCP）。
 *
 * - apply: 接收客户端 push 的 [SyncController.Change]，返回 [SyncController.UploadResult]
 * - toPayload: 把实体序列化为 pull/payload 字典
 * - tableName: 表名（与 SyncController.Change.table 匹配）
 */
interface SyncTableHandler {
    val tableName: String
    fun apply(userId: String, change: SyncController.Change): SyncController.UploadResult
    fun toPayload(entity: Any): Map<String, Any?>
}
