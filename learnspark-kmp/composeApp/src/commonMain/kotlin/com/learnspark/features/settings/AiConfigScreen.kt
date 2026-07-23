package com.learnspark.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.learnspark.data.model.AiProviderDto

/**
 * R4a：AI 服务商选择 + 配置面板。
 *
 * 入口：Settings → 「AI 服务」
 *
 * 上半部分：列出已配置的 AI 通道（provider + model + ***last4 + 启用状态），支持删除
 * 下半部分：选择服务商下拉框 → 自动填入 defaultBaseUrl + defaultModel → 用户输入 apiKey → 保存
 * 顶部：「+ 新增」按钮触发配置对话框
 */
object AiConfigScreen : Screen {
    private fun readResolve(): Any = AiConfigScreen

    @Composable
    override fun Content() {
        val vm: AiConfigViewModel = org.koin.compose.koinInject()
        val providers by vm.providers.collectAsState()
        val configs by vm.configs.collectAsState()
        val ui by vm.ui.collectAsState()
        var showAdd by remember { mutableStateOf(false) }
        // R7 修复：安全获取 navigator
        val navigator = runCatching { LocalNavigator.currentOrThrow }.getOrNull()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("AI 服务") },
                    navigationIcon = {
                        IconButton(onClick = { navigator?.pop() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
                        IconButton(onClick = { showAdd = true }) {
                            Icon(Icons.Default.Add, contentDescription = "新增配置")
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            ) {
                ui.error?.let { msg ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        backgroundColor = MaterialTheme.colors.error,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(msg, color = MaterialTheme.colors.onError, modifier = Modifier.weight(1f))
                            TextButton(onClick = { vm.dismissError() }) { Text("关闭") }
                        }
                    }
                }
                if (ui.loading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("同步中…", style = MaterialTheme.typography.caption)
                    }
                }
                Text(
                    "支持多 provider：DeepSeek / OpenAI / Qwen / GLM / Moonshot / 自定义 OpenAI 兼容端点。\n" +
                        "每个用户每 provider 最多一条配置；可在任务级选择本次使用的 provider。",
                    style = MaterialTheme.typography.caption,
                )
                Spacer(Modifier.height(8.dp))
                if (configs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("还没有配置任何 AI 服务", style = MaterialTheme.typography.body1)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { showAdd = true }) { Text("+ 新增配置") }
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(configs, key = { it.id }) { cfg ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "${cfg.provider} · ${cfg.model}",
                                            style = MaterialTheme.typography.subtitle1,
                                        )
                                        Text(
                                            "Key ${cfg.apiKeyMasked} · ${if (cfg.enabled) "启用" else "停用"} · v${cfg.version}",
                                            style = MaterialTheme.typography.caption,
                                        )
                                        cfg.baseUrl?.let { Text("端点: $it", style = MaterialTheme.typography.caption) }
                                    }
                                    IconButton(onClick = { vm.deleteConfig(cfg.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "删除")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAdd) {
            AddConfigDialog(
                providers = providers,
                onDismiss = { showAdd = false },
                onSave = { provider, apiKey, model, baseUrl ->
                    vm.upsertConfig(provider, apiKey, model, baseUrl)
                    showAdd = false
                },
            )
        }
    }
}

@Composable
private fun AddConfigDialog(
    providers: List<AiProviderDto>,
    onDismiss: () -> Unit,
    onSave: (provider: String, apiKey: String, model: String, baseUrl: String?) -> Unit,
) {
    var selected by remember { mutableStateOf<AiProviderDto?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    val p = selected

    LaunchedEffect(p) {
        if (p != null) {
            if (model.isBlank()) model = p.defaultModel
            if (baseUrl.isBlank()) baseUrl = p.defaultBaseUrl
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增 AI 服务") },
        text = {
            Column {
                Text("选择服务商", style = MaterialTheme.typography.caption)
                Box {
                    OutlinedTextField(
                        value = p?.displayName ?: "",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("服务商") },
                        trailingIcon = {
                            IconButton(onClick = { menuExpanded = !menuExpanded }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                    )
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        providers.forEach { pp ->
                            DropdownMenuItem(onClick = {
                                selected = pp
                                model = pp.defaultModel
                                baseUrl = pp.defaultBaseUrl
                                menuExpanded = false
                            }) { Text(pp.displayName) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    placeholder = { Text(p?.apiKeyHint ?: "输入 API Key") },
                )
                Spacer(Modifier.height(8.dp))
                if (p != null && p.popularModels.isNotEmpty()) {
                    Text("推荐模型", style = MaterialTheme.typography.caption)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        p.popularModels.take(4).forEach { m ->
                            TextButton(onClick = { model = m }) { Text(m) }
                        }
                    }
                }
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("模型名") },
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("端点（可留空使用默认）") },
                )
            }
        },
        confirmButton = {
            Button(
                enabled = p != null && apiKey.isNotBlank() && model.isNotBlank(),
                onClick = {
                    onSave(
                        p!!.id,
                        apiKey.trim(),
                        model.trim(),
                        baseUrl.trim().ifBlank { null },
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
