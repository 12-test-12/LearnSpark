package com.learnspark.features.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.learnspark.core.files.ViewKind
import org.koin.compose.koinInject

/**
 * R5d：文件查看器屏幕。
 *
 * - IMAGE（png/jpg/gif/bmp/webp）：in-app Image 渲染
 * - TEXT（md/txt/json/csv/xml/html/enex...）：in-app 滚动文本
 * - OPEN_EXTERNAL（pdf/docx/...）：在-app 提示，调用 openWithSystem 唤起系统应用
 */
class FileViewerScreen(
    private val source: FileViewerViewModel.Source,
    private val fileName: String,
    private val userId: String = "00000000-0000-0000-0000-000000000001",
) : Screen {
    override val key: String = "file-viewer:${fileName}:${System.identityHashCode(source)}"

    @Composable
    override fun Content() {
        val vm: FileViewerViewModel = koinInject()
        val state by vm.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        LaunchedEffect(source) {
            vm.load(userId, source)
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(fileName, maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (val s = state) {
                    is FileViewerViewModel.UiState.Loading -> LoadingPane()
                    is FileViewerViewModel.UiState.Error -> ErrorPane(s.message)
                    is FileViewerViewModel.UiState.Text -> TextContent(s.text, fileName)
                    is FileViewerViewModel.UiState.Bytes -> BytesContent(
                        state = s,
                        onOpen = { vm.openWithSystem() },
                        onExport = { vm.exportToDocuments() },
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingPane() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text("下载中…")
    }
}

@Composable
private fun ErrorPane(msg: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Description, contentDescription = null, tint = MaterialTheme.colors.error)
        Spacer(Modifier.height(8.dp))
        Text(msg, color = MaterialTheme.colors.error)
    }
}

@Composable
private fun TextContent(text: String, fileName: String) {
    val scroll = rememberScrollState()
    val isMarkdown = fileName.endsWith(".md", ignoreCase = true) || fileName.endsWith(".markdown", ignoreCase = true)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.surface)
    ) {
        Text(
            text = if (isMarkdown) "📝 Markdown 渲染（简易）" else "📄 文本内容",
            style = MaterialTheme.typography.caption,
            modifier = Modifier.padding(8.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
                .verticalScroll(scroll)
        ) {
            Text(
                text = if (isMarkdown) renderMarkdownLite(text) else text,
                fontFamily = if (isMarkdown) FontFamily.SansSerif else FontFamily.Monospace,
                style = MaterialTheme.typography.body1,
            )
        }
    }
}

@Composable
private fun BytesContent(
    state: FileViewerViewModel.UiState.Bytes,
    onOpen: () -> Unit,
    onExport: () -> Unit,
) {
    when (state.viewKind) {
        ViewKind.IMAGE -> {
            val bmp: ImageBitmap? = remember(state.bytes) {
                runCatching { state.bytes.toImageBitmap() }.getOrNull()
            }
            if (bmp != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = bmp,
                        contentDescription = state.fileName,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                OpenExternalPane(state, onOpen, onExport, "图片解析失败")
            }
        }
        ViewKind.TEXT -> {
            // 退化为文本展示（适用于 PDF/DOC 等无法用 Image 展示但服务端已抽出文本的情况）
            val text = runCatching { String(state.bytes, Charsets.UTF_8) }.getOrNull() ?: ""
            TextContent(text, state.fileName)
        }
        ViewKind.OPEN_EXTERNAL -> OpenExternalPane(state, onOpen, onExport, "此类型暂不内置渲染")
    }
}

@Composable
private fun OpenExternalPane(
    state: FileViewerViewModel.UiState.Bytes,
    onOpen: () -> Unit,
    onExport: () -> Unit,
    hint: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.Description,
            contentDescription = null,
            modifier = Modifier.height(72.dp),
            tint = MaterialTheme.colors.primary,
        )
        Spacer(Modifier.height(12.dp))
        Text(state.fileName, style = MaterialTheme.typography.h6)
        Spacer(Modifier.height(4.dp))
        Text(
            text = hint,
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.OpenInNew, contentDescription = null)
            Spacer(Modifier.padding(horizontal = 4.dp))
            Text("用系统应用打开")
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Save, contentDescription = null)
            Spacer(Modifier.padding(horizontal = 4.dp))
            Text("保存到本地")
        }
    }
}

/**
 * 极简 Markdown 渲染：仅做基础行处理（# 标题、列表、加粗斜体）。
 * 完整 Markdown 渲染需要 markdown-it 之类的 KMP 库，本期先保持轻量。
 */
private fun renderMarkdownLite(md: String): String = md
    .replace(Regex("(?m)^# (.+)$"), "【H1】$1")
    .replace(Regex("(?m)^## (.+)$"), "【H2】$1")
    .replace(Regex("(?m)^### (.+)$"), "【H3】$1")
    .replace(Regex("(?m)^[-*] (.+)$"), "  • $1")
    .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
    .replace(Regex("\\*([^*]+)\\*"), "$1")
    .replace(Regex("`([^`]+)`"), "$1")

/** 图像解码（KMP 共享）：Android 用 BitmapFactory，Desktop 用 ImageIO。 */
private fun ByteArray.toImageBitmap(): ImageBitmap {
    val bmp: Any = platformDecodeImage(this)
        ?: error("Image decode returned null")
    @Suppress("UNCHECKED_CAST")
    return bmp as ImageBitmap
}

/** 平台实现返回 androidx.compose.ui.graphics.ImageBitmap。 */
expect fun platformDecodeImage(bytes: ByteArray): ImageBitmap?
