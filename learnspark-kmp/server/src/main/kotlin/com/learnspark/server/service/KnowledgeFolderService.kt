package com.learnspark.server.service

import com.learnspark.server.domain.entity.KnowledgeFolder
import com.learnspark.server.domain.repository.KnowledgeFolderRepository
import com.learnspark.server.domain.repository.KnowledgeEntryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * R3a：知识库文件夹服务（多层树）。
 *
 * 树的不变量：
 * 1. parentId 形成的图无环（DB 唯一约束 + 应用层循环检测）
 * 2. depth ≤ 10
 * 3. 同级下 name 唯一
 * 4. path = "祖先path/本name"，depth = 父depth+1
 *
 * 移动/重命名：必须级联刷新所有后代的 path + depth
 */
@Service
class KnowledgeFolderService(
    private val folderRepository: KnowledgeFolderRepository,
    private val entryRepository: KnowledgeEntryRepository,
) {

    /** 列出用户全部文件夹（扁平） */
    fun list(userId: String): List<KnowledgeFolder> =
        folderRepository.findByUserId(userId).sortedWith(compareBy({ it.depth }, { it.sortOrder }, { it.name }))

    /**
     * 列出根目录下的直接子文件夹（树的一层）
     * parentId == null 即根目录
     */
    fun listByParent(userId: String, parentId: String?): List<KnowledgeFolder> =
        folderRepository.findByUserIdAndParentId(userId, parentId)
            .sortedWith(compareBy({ it.sortOrder }, { it.name }))

    fun get(id: String, userId: String): KnowledgeFolder? =
        folderRepository.findByIdAndUserId(id, userId)

    @Transactional
    fun create(
        userId: String,
        name: String,
        parentId: String? = null,
        color: String? = null,
        icon: String? = null,
        sortOrder: Int = 0,
    ): CreateResult {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return CreateResult.InvalidName
        val parent = parentId?.let { folderRepository.findByIdAndUserId(it, userId) }
        if (parentId != null && parent == null) return CreateResult.ParentNotFound
        if (parent != null && parent.depth + 1 > KnowledgeFolder.MAX_DEPTH) {
            return CreateResult.DepthExceeded
        }
        val newFolder = KnowledgeFolder(
            id = UUID.randomUUID().toString(),
            userId = userId,
            parentId = parentId,
            name = trimmed,
            color = color,
            icon = icon,
            sortOrder = sortOrder,
            path = if (parent == null) "/$trimmed" else "${parent.path}/$trimmed",
            depth = (parent?.depth ?: -1) + 1,
            version = 1L,
        )
        return try {
            CreateResult.Ok(folderRepository.save(newFolder))
        } catch (e: org.springframework.dao.DataIntegrityViolationException) {
            // 同级重名触发 UNIQUE 约束
            CreateResult.DuplicateName
        }
    }

    @Transactional
    fun update(
        id: String,
        userId: String,
        name: String? = null,
        color: String? = null,
        icon: String? = null,
        sortOrder: Int? = null,
    ): UpdateResult {
        val existing = get(id, userId) ?: return UpdateResult.NotFound
        name?.let { existing.name = it.trim() }
        color?.let { existing.color = it }
        icon?.let { existing.icon = it }
        sortOrder?.let { existing.sortOrder = it }
        existing.version = existing.version + 1
        if (name != null) refreshPathAndDescendants(existing)
        return try {
            UpdateResult.Ok(folderRepository.save(existing))
        } catch (e: org.springframework.dao.DataIntegrityViolationException) {
            UpdateResult.DuplicateName
        }
    }

    /**
     * 移动文件夹到新父节点。校验：
     * 1) 新父属于同一 user
     * 2) 新父不是自身
     * 3) 新父不是自身后代（防循环）
     * 4) 新 depth ≤ MAX_DEPTH
     */
    @Transactional
    fun move(id: String, userId: String, newParentId: String?): MoveResult {
        val moving = get(id, userId) ?: return MoveResult.NotFound
        if (newParentId == null) {
            moving.parentId = null
            moving.depth = 0
            moving.path = "/${moving.name}"
        } else {
            if (newParentId == id) return MoveResult.CycleDetected
            val newParent = folderRepository.findByIdAndUserId(newParentId, userId)
                ?: return MoveResult.ParentNotFound
            // 循环检测：newParent 的祖先链不能包含 moving
            if (isAncestorOf(moving.id, newParentId, userId)) return MoveResult.CycleDetected
            // 深度校验
            if (newParent.depth + 1 > KnowledgeFolder.MAX_DEPTH) return MoveResult.DepthExceeded
            moving.parentId = newParentId
            moving.depth = newParent.depth + 1
            moving.path = "${newParent.path}/${moving.name}"
        }
        moving.version = moving.version + 1
        val saved = folderRepository.save(moving)
        refreshPathAndDescendants(saved)
        return MoveResult.Ok(saved)
    }

    /**
     * 删除文件夹（保留其下条目：条目的 folderId 置 null 即回到根）
     */
    @Transactional
    fun delete(id: String, userId: String): Boolean {
        val existing = get(id, userId) ?: return false
        // 把条目 folderId 置 null（ON DELETE SET NULL 在 DB 也会做，这里显式做是防御性）
        entryRepository.findByFolderIdAndUserId(id, userId).forEach { entry ->
            entry.folderId = null
            entry.version = entry.version + 1
            entryRepository.save(entry)
        }
        // 子文件夹级联删除（DB ON DELETE CASCADE）
        folderRepository.delete(existing)
        return true
    }

    /**
     * 用 BFS 重算 folder 及其所有后代的 path + depth。
     * 重命名或移动后调用。
     */
    private fun refreshPathAndDescendants(root: KnowledgeFolder) {
        val all = folderRepository.findByUserId(root.userId).associateBy { it.id }
        // BFS
        val queue = ArrayDeque<String>().also { it.add(root.id) }
        while (queue.isNotEmpty()) {
            val fid = queue.removeFirst()
            val folder = all[fid] ?: continue
            val newPath = if (folder.parentId == null) "/${folder.name}"
                else (all[folder.parentId]?.path ?: "/${folder.name}") + "/${folder.name}"
            val newDepth = if (folder.parentId == null) 0
                else (all[folder.parentId]?.depth ?: -1) + 1
            if (folder.path != newPath || folder.depth != newDepth) {
                folder.path = newPath
                folder.depth = newDepth
                folder.version = folder.version + 1
                folderRepository.save(folder)
            }
            // 加入子节点
            all.values.filter { it.parentId == fid }.forEach { queue.add(it.id) }
        }
    }

    /**
     * 循环检测：candidateId 是否在 ancestorId 的祖先链上
     */
    private fun isAncestorOf(candidateId: String, ancestorId: String, userId: String): Boolean {
        val all = folderRepository.findByUserId(userId).associateBy { it.id }
        var current: KnowledgeFolder? = all[ancestorId]
        while (current != null) {
            if (current.id == candidateId) return true
            current = current.parentId?.let { all[it] }
        }
        return false
    }

    sealed class CreateResult {
        data class Ok(val folder: KnowledgeFolder) : CreateResult()
        data object InvalidName : CreateResult()
        data object ParentNotFound : CreateResult()
        data object DepthExceeded : CreateResult()
        data object DuplicateName : CreateResult()
    }

    sealed class UpdateResult {
        data class Ok(val folder: KnowledgeFolder) : UpdateResult()
        data object NotFound : UpdateResult()
        data object DuplicateName : UpdateResult()
    }

    sealed class MoveResult {
        data class Ok(val folder: KnowledgeFolder) : MoveResult()
        data object NotFound : MoveResult()
        data object ParentNotFound : MoveResult()
        data object CycleDetected : MoveResult()
        data object DepthExceeded : MoveResult()
    }
}
