package com.ricebaby.video.ui.unlock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricebaby.video.R
import com.ricebaby.video.ui.theme.BrandNavy
import com.ricebaby.video.ui.theme.Dimens

/** 首次进入只问口令；站点地址已随安装包内置。 */
@Composable
fun UnlockScreen(viewModel: UnlockViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val shake = remember { Animatable(0f) }
    val appear = remember { Animatable(0f) }
    val focusRequester = remember { FocusRequester() }
    var showSelfHosted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { appear.animateTo(1f, tween(320)) }
    LaunchedEffect(uiState.errorTick) {
        if (uiState.errorTick == 0) return@LaunchedEffect
        focusRequester.requestFocus()
        shake.animateTo(-12f, tween(55))
        shake.animateTo(10f, tween(70))
        shake.animateTo(-6f, tween(70))
        shake.animateTo(0f, tween(55))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.xl)
            .alpha(appear.value),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BrandMark(size = 92.dp, corner = 26.dp)
        Spacer(Modifier.height(Dimens.lg))
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(Dimens.xs))
        Text(
            "输入口令，开始观影",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Dimens.xxl))
        Column(
            modifier = Modifier.fillMaxWidth().offset(x = shake.value.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.xs),
        ) {
            OutlinedTextField(
                value = uiState.passphrase,
                onValueChange = viewModel::setPassphrase,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                label = { Text("访问口令") },
                singleLine = true,
                isError = uiState.error != null,
                shape = RoundedCornerShape(Dimens.cardCorner),
                visualTransformation =
                    if (uiState.reveal) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (uiState.reveal) KeyboardType.Text else KeyboardType.Password,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboard?.hide()
                        viewModel.submit()
                    },
                ),
                trailingIcon = {
                    TextButton(onClick = viewModel::toggleReveal) {
                        Icon(
                            imageVector =
                                if (uiState.reveal) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (uiState.reveal) "隐藏口令" else "显示口令",
                        )
                    }
                },
            )
            Text(
                uiState.error ?: "忘记口令？进入后可在「设置」里管理站点。",
                style = MaterialTheme.typography.bodySmall,
                color = if (uiState.error != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(start = Dimens.md),
            )
        }
        Spacer(Modifier.height(Dimens.lg))
        Button(
            onClick = {
                keyboard?.hide()
                viewModel.submit()
            },
            enabled = uiState.passphrase.isNotBlank() && !uiState.busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(Dimens.pillCorner),
        ) {
            if (uiState.busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("进入", style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(Dimens.xxl))
        TextButton(onClick = { showSelfHosted = true }) {
            Text(
                "使用自己的站点地址",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Dimens.md))
    }

    LaunchedEffect(uiState.unlocked) {
        if (uiState.unlocked) showSelfHosted = false
    }

    if (showSelfHosted) {
        SelfHostedDialog(
            error = uiState.selfHostedError,
            busy = uiState.busy,
            onDismiss = {
                showSelfHosted = false
                viewModel.clearSelfHostedError()
            },
            onSubmit = viewModel::submitSelfHosted,
        )
    }
}

@Composable
private fun SelfHostedDialog(
    error: String?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自己的站点") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称，如：自建站") },
                    singleLine = true,
                )
                TextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("站点地址") },
                    singleLine = true,
                    isError = error != null,
                )
                if (error != null) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(name, url) }, enabled = url.isNotBlank() && !busy) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 冷启动读取解锁状态时先占住品牌位，避免出现空白帧或闪一下口令页。 */
@Composable
fun BrandSplash() {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(220)) }
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        BrandMark(size = 84.dp, corner = 24.dp, alpha = appear.value)
    }
}

@Composable
private fun BrandMark(size: Dp, corner: Dp, alpha: Float = 1f) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .alpha(alpha)
            .clip(RoundedCornerShape(corner))
            .background(BrandNavy),
    ) {
        Image(
            painter = painterResource(id = R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.matchParentSize(),
        )
    }
}
