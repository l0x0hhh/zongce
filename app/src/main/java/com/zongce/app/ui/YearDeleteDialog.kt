// 导出并分享之后询问「要不要把这一学年的记录删掉」。
//
// 与 UpdateDialog 同级、同样在 MainActivity.App() 里渲染：用户从系统分享面板回来时
// 可能落在任何一个 tab，弹窗挂在页面里就看不见了。
//
// 文案必须把学口号、记录数、照片数都说出来 —— 删除不可逆，用户要在点下去之前
// 就能算出自己将要失去什么；同时必须点明"材料包已导出"，否则没人敢点删除。
package com.zongce.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun YearDeleteDialog(
    prompt: YearDeletePrompt?,
    deleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (prompt == null) return

    AlertDialog(
        // 删除进行中不允许靠点外面关掉：此刻正在动数据库，让弹窗消失会让人以为已经结束。
        onDismissRequest = { if (!deleting) onDismiss() },
        title = { Text("删除 ${prompt.year} 学年的记录？") },
        text = {
            Text(
                "${prompt.year} 学年的 ${prompt.recordCount} 条记录和它们的 " +
                    "${prompt.photoCount} 张照片会一起删除，无法恢复。材料包已导出。",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !deleting) {
                // 按钮内 loading，不做全屏进度条：整屏遮罩会让人以为 App 卡住了。
                if (deleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("删除")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !deleting) { Text("保留") }
        }
    )
}
