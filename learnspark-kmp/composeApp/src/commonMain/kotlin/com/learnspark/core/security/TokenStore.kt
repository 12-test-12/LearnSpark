package com.learnspark.core.security

/**
 * 跨端 Token 存储接口（应用层）。
 *
 * - Android 实际：DataStore Preferences（敏感字段由 [SecureStorage] 二次加密）
 * - Desktop 实际：multiplatform-settings (Preferences)（敏感字段由 [SecureStorage] 二次加密）
 *
 * 阶段 1.1.3：所有敏感字段写入时通过 [SecureStorage.encrypt] 加密、读取时解密。
 * 阶段 1.2 之后：配合后端 RefreshToken 轮换 + 设备表管理。
 */
interface TokenStore {
    suspend fun getAccessToken(): String?
    suspend fun getRefreshToken(): String?
    suspend fun setTokens(access: String, refresh: String)
    suspend fun clear()
}

/**
 * 平台底层明文存储接口（由 [EncryptedTokenStore] 装饰后暴露为 [TokenStore]）。
 *
 * - Android 实际：DataStore Preferences
 * - Desktop 实际：JDK Preferences (via multiplatform-settings)
 */
interface PlatformTokenStore {
    suspend fun getAccessRaw(): String?
    suspend fun getRefreshRaw(): String?
    suspend fun setRaw(access: String?, refresh: String?)
    suspend fun clearRaw()
}

/**
 * 加密装饰器：把 [PlatformTokenStore] 包装成 [TokenStore]。
 *
 * - 写入：先 [SecureStorage.encrypt] 再调 delegate.setRaw
 * - 读取：先 delegate.getXxxRaw 再 [SecureStorage.decrypt]
 * - 解密失败（key 变更 / 数据被破坏）时返回 null，并清理 raw 字段
 */
class EncryptedTokenStore(
    private val secure: SecureStorage,
    private val delegate: PlatformTokenStore,
) : TokenStore {

    override suspend fun getAccessToken(): String? = decryptOrClear(delegate::getAccessRaw)

    override suspend fun getRefreshToken(): String? = decryptOrClear(delegate::getRefreshRaw)

    override suspend fun setTokens(access: String, refresh: String) {
        val encAccess = secure.encrypt(access)
        val encRefresh = secure.encrypt(refresh)
        delegate.setRaw(encAccess, encRefresh)
    }

    override suspend fun clear() = delegate.clearRaw()

    private suspend fun decryptOrClear(getter: suspend () -> String?): String? {
        val raw = getter() ?: return null
        return try {
            secure.decrypt(raw)
        } catch (e: Exception) {
            // 密钥变更 / 密文被破坏：清理后返回 null，让上层走重新登录流程
            delegate.clearRaw()
            null
        }
    }
}
