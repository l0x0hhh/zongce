// 展示更新检查、下载进度和系统安装入口。
package com.zongce.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zongce.app.update.UpdateInfo
import java.io.File

sealed class UpdateUiState {
    object Idle : UpdateUiState()
    object Checking : UpdateUiState()
    data class UpToDate(val version: String) : UpdateUiState()
    data class Available(val info: UpdateInfo) : UpdateUiState()
    data class Downloading(val info: UpdateInfo, val progress: Int) : UpdateUiState()
    data class Ready(val info: UpdateInfo, val file: File) : UpdateUiState()
    data class Error(val message: String) : UpdateUiState()
}

@Composable
fun UpdateDialog(
    state: UpdateUiState,
    onDownload: (UpdateInfo) -> Unit,
    onInstall: (File) -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    if (state is UpdateUiState.Idle) return

    val title = when (state) {
        UpdateUiState.Checking -> "正在检查更新"
        is UpdateUiState.UpToDate -> "已经是最新版本"
        is UpdateUiState.Available -> "发现新版本 ${state.info.version}"
        is UpdateUiState.Downloading -> "正在下载更新"
        is UpdateUiState.Ready -> "更新已下载"
        is UpdateUiState.Error -> "检查更新失败"
        UpdateUiState.Idle -> ""
    }

    AlertDialog(
        onDismissRequest = {
            if (state !is UpdateUiState.Checking && state !is UpdateUiState.Downloading) onDismiss()
        },
        title = { Text(title) },
        text = {
            when (state) {
                UpdateUiState.Checking -> CircularProgressIndicator()
                is UpdateUiState.UpToDate -> Text("当前版本 ${state.version}，可以继续使用")
                is UpdateUiState.Available -> UpdateAvailableText(state.info)
                is UpdateUiState.Downloading -> {
                    Column {
                        Text("版本 ${state.info.version}")
                        Spacer(Modifier.height(12.dp))
                        if (state.progress < 0) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(
                                progress = { state.progress / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("${state.progress}%", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                is UpdateUiState.Ready -> Text("版本 ${state.info.version} 已下载，交给系统完成安装")
                is UpdateUiState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
                UpdateUiState.Idle -> Unit
            }
        },
        confirmButton = {
            when (state) {
                UpdateUiState.Checking, is UpdateUiState.Downloading -> Unit
                is UpdateUiState.Available -> Button(onClick = { onDownload(state.info) }) { Text("下载更新") }
                is UpdateUiState.Ready -> Button(onClick = { onInstall(state.file) }) { Text("安装更新") }
                is UpdateUiState.Error -> TextButton(onClick = onRetry) { Text("重试") }
                else -> TextButton(onClick = onDismiss) { Text("知道了") }
            }
        },
        dismissButton = {
            if (state is UpdateUiState.Available || state is UpdateUiState.Ready || state is UpdateUiState.Error) {
                TextButton(onClick = onDismiss) { Text("稍后") }
            }
        }
    )
}

@Composable
private fun UpdateAvailableText(info: UpdateInfo) {
    Column {
        Text(info.title)
        if (info.notes.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                info.notes.trim().take(240),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
