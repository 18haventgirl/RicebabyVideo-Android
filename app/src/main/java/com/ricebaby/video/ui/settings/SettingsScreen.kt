package com.ricebaby.video.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricebaby.video.data.model.SiteInstance

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var passwordTarget by remember { mutableStateOf<SiteInstance?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.lastAddInvalidUrl) {
        if (uiState.lastAddInvalidUrl) {
            snackbarHostState.showSnackbar("地址无法识别，示例：video.example.com 或 https://video.example.com")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("添加站点") },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                "观影站点",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            Text(
                "连接后，搜索、播放与直播都会经由该站点完成。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("视频资源：${uiState.sourceCount} 个", style = MaterialTheme.typography.titleSmall)
                        uiState.syncMessage?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    FilledTonalButton(onClick = viewModel::syncSources) { Text("同步资源") }
                }
            }
            ProxyCard(
                proxyHost = uiState.settings.proxyHost,
                proxyPort = uiState.settings.proxyPort,
                message = uiState.proxyMessage,
                onSave = viewModel::saveProxy,
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (uiState.settings.instances.isEmpty()) {
                    item {
                        Text(
                            "还没有站点，点右下角「添加站点」开始。",
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(uiState.settings.instances, key = { it.id }) { instance ->
                    InstanceCard(
                        instance = instance,
                        selected = uiState.settings.activeInstanceId == instance.id,
                        passwordSaved = uiState.settings.passwords.containsKey(instance.id),
                        status = uiState.testStatuses[instance.id] ?: TestStatus.Idle,
                        statusDetail = uiState.testDetails[instance.id],
                        onSelect = { viewModel.selectInstance(instance.id) },
                        onTest = { viewModel.testConnection(instance.id) },
                        onPassword = { passwordTarget = instance },
                        onDelete = { viewModel.removeInstance(instance.id) },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddInstanceDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, url ->
                viewModel.addInstance(name, url)
                showAddDialog = false
            },
        )
    }

    passwordTarget?.let { instance ->
        PasswordDialog(
            instance = instance,
            onDismiss = { passwordTarget = null },
            onSave = { password ->
                viewModel.savePassword(instance.id, password)
                passwordTarget = null
            },
        )
    }
}

@Composable
private fun statusText(status: TestStatus, detail: String?): String = when (status) {
    TestStatus.Idle -> "未测试"
    TestStatus.Testing -> "测试中…"
    TestStatus.ReachableNoAuth -> "可连接 · 无需密码"
    TestStatus.LoginOk -> "密码有效" + if (!detail.isNullOrBlank()) "（$detail）" else ""
    TestStatus.Rejected -> "密码不正确"
    TestStatus.NeedPassword -> detail ?: "请先填写密码"
    TestStatus.Unreachable -> detail ?: "无法连接"
}

@Composable
private fun InstanceCard(
    instance: SiteInstance,
    selected: Boolean,
    passwordSaved: Boolean,
    status: TestStatus,
    statusDetail: String?,
    onSelect: () -> Unit,
    onTest: () -> Unit,
    onPassword: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(modifier = Modifier.weight(1f)) {
                Text(instance.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    instance.baseUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    statusText(status, statusDetail) + if (passwordSaved) " · 已存密码" else " · 未存密码",
                    style = MaterialTheme.typography.bodySmall,
                    color = when (status) {
                        TestStatus.LoginOk, TestStatus.ReachableNoAuth -> MaterialTheme.colorScheme.primary
                        TestStatus.Rejected, TestStatus.Unreachable -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                FilledTonalButton(onClick = onTest, modifier = Modifier.padding(bottom = 4.dp)) { Text("测试") }
                TextButton(onClick = onPassword) { Text(if (passwordSaved) "改密码" else "存密码") }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Text("删除", modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun ProxyCard(
    proxyHost: String,
    proxyPort: Int,
    message: String?,
    onSave: (String, String) -> Unit,
) {
    var host by remember(proxyHost) { mutableStateOf(proxyHost) }
    var port by remember(proxyPort) { mutableStateOf(if (proxyPort > 0) proxyPort.toString() else "") }
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("网络代理（可选）", style = MaterialTheme.typography.titleSmall)
            Text(
                "部分资源直连受限时填写，如 127.0.0.1:7890；留空停用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("地址") },
                    singleLine = true,
                    modifier = Modifier.weight(2f),
                )
                TextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("端口") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onSave(host, port) }) { Text("保存代理") }
                message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AddInstanceDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加站点") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称，如：主站") },
                    singleLine = true,
                )
                TextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("站点地址") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.ifBlank { "站点" }, url) },
                enabled = url.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun PasswordDialog(instance: SiteInstance, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${instance.name} 的访问密码") },
        text = {
            TextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("留空表示清除") },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { onSave(password) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
