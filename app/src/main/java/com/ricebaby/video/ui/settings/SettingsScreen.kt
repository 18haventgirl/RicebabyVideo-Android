package com.ricebaby.video.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
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
import com.ricebaby.video.ui.components.AppCard
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ricebaby.video.ui.theme.Dimens
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricebaby.video.data.model.SiteInstance

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingExport by remember { mutableStateOf<android.net.Uri?>(null) }
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
    ) { target ->
        val src = pendingExport
        if (src != null && target != null) {
            runCatching {
                context.contentResolver.openOutputStream(target)?.use { out ->
                    context.contentResolver.openInputStream(src)?.use { it.copyTo(out) }
                }
            }
        }
        pendingExport = null
    }
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importBackup) }
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
        // 外层 AppRoot 的 Scaffold 已避让系统栏，内层不能重复计算
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary) },
                text = { Text("添加站点", color = MaterialTheme.colorScheme.onPrimary) },
                containerColor = MaterialTheme.colorScheme.primary,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Dimens.screen),
        ) {
            Text(
                "设置",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = Dimens.md, bottom = Dimens.md),
            )
            Text(
                "观影站点",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = Dimens.sm),
            )
            Text(
                "连接后，搜索与播放都会经由该站点完成。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.sm)) {
                Row(
                    modifier = Modifier.padding(Dimens.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("视频资源：${uiState.sourceCount} 个", style = MaterialTheme.typography.titleMedium)
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
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Dimens.sm),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    bottom = Dimens.fabEnd,
                ),
            ) {
                item {
                    BackupCard(
                        message = uiState.backupMessage,
                        onExport = { viewModel.exportBackup { uri -> pendingExport = uri; exportLauncher.launch("ricebaby-backup.json") } },
                        onImport = { importLauncher.launch(arrayOf("application/json")) },
                    )
                }
                if (uiState.settings.instances.isEmpty()) {
                    item {
                        Text(
                            "还没有站点，点右下角「添加站点」开始。",
                            modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.xxl),
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
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(Dimens.md), verticalAlignment = Alignment.CenterVertically) {
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
                FilledTonalButton(onClick = onTest, modifier = Modifier.padding(bottom = Dimens.xs)) { Text("测试") }
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
private fun BackupCard(message: String?, onExport: () -> Unit, onImport: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.sm)) {
        Column(modifier = Modifier.padding(Dimens.md), verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            Text("数据备份", style = MaterialTheme.typography.titleMedium)
            Text(
                "站点、资源、收藏与观看记录可导出为 JSON 文件，重装后一键恢复。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onExport) { Text("导出备份") }
                TextButton(onClick = onImport) { Text("导入恢复") }
                message?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
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
