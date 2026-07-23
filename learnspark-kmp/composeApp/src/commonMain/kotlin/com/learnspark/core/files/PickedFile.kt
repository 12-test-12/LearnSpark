package com.learnspark.core.files

/**
 * R4c：跨平台文件选择结果。
 *
 * - name: 用户选择的文件名（用于服务端落盘 + UI 显示）
 * - bytes: 文件原始字节
 */
data class PickedFile(
    val name: String,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PickedFile) return false
        return name == other.name && bytes.contentEquals(other.bytes)
    }
    override fun hashCode(): Int = name.hashCode() * 31 + bytes.contentHashCode()
}
