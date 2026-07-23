package com.learnspark.features.viewer

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Android：使用 BitmapFactory 解码 PNG/JPG/GIF/WEBP/BMP（API 21+）。
 */
actual fun platformDecodeImage(bytes: ByteArray): ImageBitmap? {
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    return bmp.asImageBitmap()
}
