package com.learnspark.core.security

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.win32.StdCallLibrary
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Desktop 平台实现：跨 Windows / macOS / Linux。
 *
 * - Windows: JNA 调用 crypt32.dll 的 CryptProtectData / CryptUnprotectData (DPAPI)
 * - macOS/Linux: 降级方案 PBKDF2 600k + AES-256-GCM + 主密码
 */
actual class SecureStorage {

    private val backend: Backend = detectBackend()

    actual suspend fun encrypt(plaintext: String): String = withContext(Dispatchers.IO) {
        when (backend) {
            Backend.WIN_DPAPI -> encryptDpapi(plaintext)
            Backend.FALLBACK_PBKDF2 -> encryptFallback(plaintext)
        }
    }

    actual suspend fun decrypt(ciphertext: String): String = withContext(Dispatchers.IO) {
        when (backend) {
            Backend.WIN_DPAPI -> decryptDpapi(ciphertext)
            Backend.FALLBACK_PBKDF2 -> decryptFallback(ciphertext)
        }
    }

    actual fun platformName(): String = when (backend) {
        Backend.WIN_DPAPI -> "win-dpapi"
        Backend.FALLBACK_PBKDF2 -> "fallback-pbkdf2-aes"
    }

    // --- DPAPI (Windows) -----------------------------------------------------

    private fun encryptDpapi(plaintext: String): String {
        val plain = plaintext.toByteArray(Charsets.UTF_8)
        val memIn = Memory(plain.size.toLong()).apply { write(0, plain, 0, plain.size) }
        val dataIn = DataBlob(plain.size, memIn)
        val dataOut = DataBlob()
        try {
            val ok = Crypt32.INSTANCE.CryptProtectData(
                dataIn,
                "LearnSpark",
                null,
                null,
                null,
                0,
                dataOut
            )
            check(ok) { "CryptProtectData failed (GetLastError=${Kernel32.INSTANCE.GetLastError()})" }
            dataOut.read()
            val outPtr = dataOut.pbData ?: error("CryptProtectData returned null pbData")
            val cipher = outPtr.getByteArray(0, dataOut.cbData)
            return Base64.getEncoder().encodeToString(cipher)
        } finally {
            dataOut.pbData?.let { Kernel32.INSTANCE.LocalFree(it) }
        }
    }

    private fun decryptDpapi(ciphertext: String): String {
        val cipher = Base64.getDecoder().decode(ciphertext)
        val memIn = Memory(cipher.size.toLong()).apply { write(0, cipher, 0, cipher.size) }
        val dataIn = DataBlob(cipher.size, memIn)
        val dataOut = DataBlob()
        try {
            val ok = Crypt32.INSTANCE.CryptUnprotectData(
                dataIn,
                null,
                null,
                null,
                null,
                0,
                dataOut
            )
            check(ok) { "CryptUnprotectData failed (GetLastError=${Kernel32.INSTANCE.GetLastError()})" }
            dataOut.read()
            val outPtr = dataOut.pbData ?: error("CryptUnprotectData returned null pbData")
            val plain = outPtr.getByteArray(0, dataOut.cbData)
            return String(plain, Charsets.UTF_8)
        } finally {
            dataOut.pbData?.let { Kernel32.INSTANCE.LocalFree(it) }
        }
    }

    // --- Fallback (macOS / Linux) -------------------------------------------

    private val masterPassword: String by lazy {
        // PoC 阶段从环境变量读取；阶段 1 接入用户主密码 UI
        System.getenv("LEARNSPARK_MASTER_PWD")
            ?: error("LEARNSPARK_MASTER_PWD not set. Desktop fallback requires a master password.")
    }

    private fun deriveKey(salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(masterPassword.toCharArray(), salt, PBKDF2_ITER, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun encryptFallback(plaintext: String): String {
        val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val key = deriveKey(salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val cipherText = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val out = ByteArray(salt.size + iv.size + cipherText.size)
        System.arraycopy(salt, 0, out, 0, salt.size)
        System.arraycopy(iv, 0, out, salt.size, iv.size)
        System.arraycopy(cipherText, 0, out, salt.size + iv.size, cipherText.size)
        return Base64.getEncoder().encodeToString(out)
    }

    private fun decryptFallback(ciphertext: String): String {
        val all = Base64.getDecoder().decode(ciphertext)
        require(all.size > 28) { "Ciphertext too short" }
        val salt = all.copyOfRange(0, 16)
        val iv = all.copyOfRange(16, 28)
        val cipherText = all.copyOfRange(28, all.size)
        val key = deriveKey(salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    // --- Backend detection ---------------------------------------------------

    private fun detectBackend(): Backend {
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("win") -> Backend.WIN_DPAPI
            else -> Backend.FALLBACK_PBKDF2
        }
    }

    private enum class Backend { WIN_DPAPI, FALLBACK_PBKDF2 }

    // --- JNA bindings --------------------------------------------------------

    private interface Crypt32 : StdCallLibrary {
        fun CryptProtectData(
            pDataIn: DataBlob,
            szDataDescr: String?,
            pOptionalEntropy: DataBlob?,
            pvReserved: Pointer?,
            pPromptStruct: Pointer?,
            dwFlags: Int,
            pDataOut: DataBlob
        ): Boolean

        fun CryptUnprotectData(
            pDataIn: DataBlob,
            ppszDataDescr: Pointer?,
            pOptionalEntropy: DataBlob?,
            pvReserved: Pointer?,
            pPromptStruct: Pointer?,
            dwFlags: Int,
            pDataOut: DataBlob
        ): Boolean

        companion object {
            val INSTANCE: Crypt32 = Native.load("Crypt32", Crypt32::class.java)
        }
    }

    private interface Kernel32 : StdCallLibrary {
        fun LocalFree(p: Pointer?): Pointer?
        fun GetLastError(): Int

        companion object {
            val INSTANCE: Kernel32 = Native.load("kernel32", Kernel32::class.java)
        }
    }

    private companion object {
        const val PBKDF2_ITER = 600_000
    }
}

actual fun createSecureStorage(): SecureStorage = SecureStorage()

/**
 * Win32 DATA_BLOB 结构 (顶层类，避免 inner class 隐式持有外层引用)。
 */
@Structure.FieldOrder("cbData", "pbData")
class DataBlob : Structure {
    @JvmField var cbData: Int = 0
    @JvmField var pbData: Pointer? = null

    constructor() : super()
    constructor(size: Int, data: Pointer) : super() {
        cbData = size
        pbData = data
    }
}
