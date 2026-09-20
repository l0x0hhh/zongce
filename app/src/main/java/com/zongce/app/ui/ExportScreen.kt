// 导出页展示体检结果、进度和最终材料包操作。
package com.zongce.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.zongce.app.core.AcademicYear
import com.zongce.app.data.RecordWithPhotos
import com.zongce.app.export.ExportCheck

/**
 * 导出 = 英雄时刻。
 * 用户辛苦一年的照片，价值在这一刻兑现：一键 → 进度 → 清点页 → 分享面板。
 * 中间不要有多余的确认。
 */
@Composable
fun ExportScreen(vm: AppViewModel, items: List<RecordWithPhotos>) {
    val context = LocalContext.current
    val state by vm.exportState.collectAsState()
    // 导出目标学年可回看，避免新学年开始后无法导出上一学年的材料。
    val availableYears = remember(items) {
        (items.mapNotNull { item ->
            item.record.awardDate.takeIf { it.isNotBlank() }
                ?.let { AcademicYear.check(it).academicYear.takeIf(String::isNotBlank) }
        } + AcademicYear.targetLabel()).distinct().sortedDescending()
    }
    var targetYear by remember { mutableStateOf(AcademicYear.targetLabel()) }
    LaunchedEffect(availableYears) {
        if (targetYear !in availableYears) targetYear = availableYears.first()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("导出材料包", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "解压就是五育文件夹 + 填报核对.txt\n目标评价学年 ${AcademicYear.LABEL}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))

        DropdownField("目标评价学年", targetYear, availableYears, onChange = { targetYear = it })
        Spacer(Modifier.height(12.dp))

        when (val s = state) {
            is ExportState.Idle -> {
                val selectedCount = items.count { AcademicYear.belongsTo(it.record.awardDate, targetYear) }
                Text("目标学年有 $selectedCount 条记录，导出前会自动体检。")
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { vm.checkBeforeExport(items, targetYear) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("一键导出") }
            }

            is ExportState.Blocked -> {
                BlockedList(s.issues)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { vm.resetExport() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("回去改") }
            }

            is ExportState.Exporting -> {
                val total = s.total.coerceAtLeast(1)
                LinearProgressIndicator(
                    progress = s.done.toFloat() / total,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("正在处理照片 ${s.done} / ${s.total}",
                    style = MaterialTheme.typography.bodyMedium)
            }

            is ExportState.Done -> {
                val r = s.result
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("导出完成", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("${r.wuyuCount} 育 · ${r.recordCount} 条 · ${r.photoCount} 张照片")
                        Text("包大小 ${"%.1f".format(r.sizeBytes / 1024f / 1024f)} MB")
                        Spacer(Modifier.height(8.dp))
                        r.perWuyu.forEach { (w, n) -> Text("$w：$n 条") }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        val uri = FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", r.file
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "发送材料包到…"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("发送到微信 / 网盘") }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { vm.resetExport() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("再来一次") }
            }

            is ExportState.Error -> {
                Text(s.message, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { vm.resetExport() }) { Text("知道了") }
            }
        }

        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("导出做了什么", style = MaterialTheme.typography.labelLarge)
            Text("· 每张图压到 3.5MB 内、统一转 JPG（系统限制 4M，仅收图片）",
                style = MaterialTheme.typography.bodySmall)
            Text("· 按「获奖时间_获奖名称_等级」命名，放你选的那一育文件夹",
                style = MaterialTheme.typography.bodySmall)
            Text("· 填报核对.txt 带照片相对路径，电脑上照着抄",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BlockedList(issues: List<ExportCheck.Issue>) {
    val blocks = issues.filter { it.level == ExportCheck.Level.BLOCK }
    val warns = issues.filter { it.level == ExportCheck.Level.WARN }

    if (blocks.isNotEmpty()) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("有 ${blocks.size} 个问题必须改完", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(8.dp))
                blocks.forEach {
                    Text("· ${it.message}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
    if (warns.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("提醒（不阻塞）", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                warns.forEach {
                    Text("· ${it.message}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}
