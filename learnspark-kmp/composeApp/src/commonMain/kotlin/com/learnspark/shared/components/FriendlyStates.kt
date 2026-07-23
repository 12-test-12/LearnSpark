package com.learnspark.shared.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 用户友好的错误横幅。
 *
 * 替代直接把后端 / Ktor 异常消息渲染到 UI 的做法——
 * 收到原始异常时，UI 层应当先解析为 [UserFacingError]，再传入此组件显示。
 *
 * - [UserFacingError.Network] → "网络连接受限，请检查 WiFi 或服务器地址"
 * - [UserFacingError.Server]  → "服务暂时不可用，请稍后重试"
 * - [UserFacingError.Unknown] → "出错了，请稍后再试"
 */
sealed class UserFacingError {
    data class Network(val onRetry: (() -> Unit)? = null) : UserFacingError()
    data class Server(val onRetry: (() -> Unit)? = null) : UserFacingError()
    data class Unknown(val raw: String? = null, val onRetry: (() -> Unit)? = null) : UserFacingError()
}

@Composable
fun ErrorBanner(
    error: UserFacingError,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val (title, action) = when (error) {
        is UserFacingError.Network -> "网络连接受限，请检查网络设置" to error.onRetry
        is UserFacingError.Server -> "服务暂时不可用，请稍后重试" to error.onRetry
        is UserFacingError.Unknown -> "出错了，请稍后再试" to error.onRetry
    }
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        backgroundColor = MaterialTheme.colors.error.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.WifiOff,
                contentDescription = null,
                tint = MaterialTheme.colors.error,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                color = MaterialTheme.colors.onSurface,
                style = MaterialTheme.typography.body2,
                modifier = Modifier.weight(1f),
            )
            if (action != null) {
                TextButton(onClick = action) { Text("重试") }
            }
            if (onDismiss != null) {
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    }
}

/**
 * 引导式空状态。
 *
 * 替代纯 "暂无内容" 的占位——首次进入时给用户一个明确的引导方向。
 */
@Composable
fun EmptyState(
    icon: ImageVector = Icons.Default.Inbox,
    title: String,
    description: String,
    primaryAction: Pair<String, () -> Unit>? = null,
    secondaryAction: Pair<String, () -> Unit>? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colors.primary.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.h6,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            description,
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
        if (primaryAction != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = primaryAction.second) { Text(primaryAction.first) }
        }
        if (secondaryAction != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = secondaryAction.second) { Text(secondaryAction.first) }
        }
    }
}
