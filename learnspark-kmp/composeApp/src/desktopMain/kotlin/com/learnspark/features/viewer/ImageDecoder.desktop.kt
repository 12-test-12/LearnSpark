package com.learnspark.features.viewer

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.loadImageBitmap
import java.io.ByteArrayInputStream

/**
 * Desktop：通过 androidx.compose.ui.res.loadImageBitmap 解码 PNG/JPG/BMP/GIF/WEBP。
 * 该函数在 Compose Multiplatform Desktop 上由 Skia 实现，比 javax.imageio 更现代。
 */
actual fun platformDecodeImage(bytes: ByteArray): ImageBitmap? {
    return runCatching {
        loadImageBitmap(ByteArrayInputStream(bytes))
    }.getOrNull()
}
