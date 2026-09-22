// 导出页：把范围、体检、打包和分享状态收进一条清晰流程。
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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

@Composable
fun ExportScreen(vm: AppViewModel, items: List<RecordWithPhotos>) {
    val context = LocalContext.current
    val state by vm.exportState.collectAsState()
    // 学年列表只由 AcademicYear.yearsOf 生成，不再在 UI 里复刻一遍（保证与组件/成果页同源）。
    val availableYears = remember(items) {
        AcademicYear.yearsOf(items.map { it.record.awardDate })
    }
    var targetYear by remember { mutableStateOf(AcademicYear.targetLabel()) }
    LaunchedEffect(availableYears) {
        if (targetYear !in availableYears) targetYear = availableYears.first()
    }
    // 范围计数必须与打包范围同源：直接问 ExportCheck.targetItems，绝不另写谓词。
    // 它会把空日期/越界记录也算进来，正好和实际打包范围一致 —— 这正是本次修复的目的。
    val selectedCount = ExportCheck.targetItems(items, targetYear).size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.page, vertical = Space.lg)
    ) {
        Column {
            Text("导出", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(Space.xs))
            Text(
                "把记录整理成可提交的文件夹",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(Space.xl))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("本次导出范围", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "$targetYear · $selectedCount 条记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "${items.sumOf { it.photos.size }} 张照片",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        // 学年选择只在"还能重新体检"的状态下开放。一旦进了确认/导出流程，
        // 显示的范围和实际打包的范围必须是同一个 —— 否则用户改了下拉框，
        // 打出来的还是已确认的旧范围。
        if (state is ExportState.Idle || state is ExportState.Blocked) {
            DropdownField(
                label = "目标评价学年",
                value = targetYear,
                options = availableYears,
                onChange = { targetYear = it }
            )
            Spacer(Modifier.height(20.dp))
        }

        when (val current = state) {
            is ExportState.Idle -> {
                ExportSectionTitle("准备导出", "先检查字段和照片，再生成 ZIP 材料包。")
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { vm.checkBeforeExport(items, targetYear) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    androidx.compose.material3.Icon(Icons.Default.Archive, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("检查并导出")
                }
            }

            is ExportState.Blocked -> {
                BlockedList(current.issues)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { vm.resetExport() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    androidx.compose.material3.Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("返回记录修改")
                }
            }

            is ExportState.Confirm -> {
                ExportSectionTitle(
                    "导出前请过目",
                    "本次范围 ${current.targetYear}。这些不影响导出，确认无误即可继续。"
                )
                Spacer(Modifier.height(12.dp))
                BlockedList(current.issues)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { vm.confirmExport() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    androidx.compose.material3.Icon(Icons.Default.Archive, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("继续导出")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { vm.resetExport() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    androidx.compose.material3.Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("返回记录修改")
                }
            }

            is ExportState.Exporting -> {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Icon(
                                Icons.Default.Archive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("正在整理材料", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(16.dp))
                        val total = current.total.coerceAtLeast(1)
                        LinearProgressIndicator(
                            progress = { current.done.toFloat() / total },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "已处理 ${current.done} / ${current.total} 张照片",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            is ExportState.Done -> {
                val result = current.result
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("导出完成", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            ExportMetric("${result.recordCount}", "条记录")
                            ExportMetric("${result.photoCount}", "张照片")
                            ExportMetric("${result.wuyuCount}", "育")
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "ZIP 大小 ${"%.1f".format(result.sizeBytes / 1024f / 1024f)} MB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("各育记录", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        result.perWuyu.forEach { (wuyu, count) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(wuyu, modifier = Modifier.weight(1f))
                                Text(
                                    "$count 条",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            result.file
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "发送材料包到…"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    androidx.compose.material3.Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("分享材料包")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { vm.resetExport() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("导出其他学年") }
            }

            is ExportState.Error -> {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        androidx.compose.material3.Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("导出失败", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            Text(current.message, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { vm.resetExport() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("重新开始") }
            }
        }

        Spacer(Modifier.height(20.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                androidx.compose.material3.Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "文件夹按五育归类，照片会统一整理为 JPG，并附带填报核对.txt。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ExportSectionTitle(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ExportMetric(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BlockedList(issues: List<ExportCheck.Issue>) {
    val blocks = issues.filter { it.level == ExportCheck.Level.BLOCK }
    val warns = issues.filter { it.level == ExportCheck.Level.WARN }

    if (blocks.isNotEmpty()) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("有 ${blocks.size} 项需要先处理", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(10.dp))
                blocks.forEach { issue ->
                    Text("• ${issue.message}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(5.dp))
                }
            }
        }
    }
    if (warns.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(
                        Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("导出提醒", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(10.dp))
                warns.forEach { issue ->
                    Text("• ${issue.message}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(5.dp))
                }
            }
        }
    }
}
