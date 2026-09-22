// 记录页：全部记录的检索与编辑入口。
// 顶部只留两件事——五育筛选、哪些记录还缺字段。其余信息一律压进副标题，
// 因为这一页的任务是"找到那条要改的记录"，不是给用户看统计报表。
package com.zongce.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zongce.app.data.RecordWithPhotos
import com.zongce.app.data.WUYU_LIST
import java.io.File

@Composable
fun ListScreen(
    items: List<RecordWithPhotos>,
    vm: AppViewModel,
    onEdit: (Long) -> Unit
) {
    var filter by remember { mutableStateOf("全部") }
    var onlyIncomplete by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<RecordWithPhotos?>(null) }

    val incompleteCount = items.count { it.record.missingFields().isNotEmpty() }
    val photoCount = items.sumOf { it.photos.size }
    val shown = items
        .filter { filter == "全部" || it.record.wuyu == filter }
        .filter { !onlyIncomplete || it.record.missingFields().isNotEmpty() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Space.page, vertical = Space.lg)
    ) {
        Text("记录", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(Space.xs))
        Text(
            if (items.isEmpty()) "存下的证明材料会列在这里"
            else "共 ${items.size} 条 · $photoCount 张证明材料",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(Space.lg))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            listOf("全部").plus(WUYU_LIST).forEach { wuyu ->
                JicunChip(
                    text = wuyu,
                    selected = filter == wuyu,
                    onClick = { filter = wuyu }
                )
            }
        }

        // 缺字段提示做成一行轻文字，不再占一条色块 —— 它是个次要入口，不该和主筛选抢注意力。
        if (incompleteCount > 0) {
            Spacer(Modifier.height(Space.sm))
            Row(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onlyIncomplete = !onlyIncomplete }
                    .padding(vertical = Space.sm, horizontal = Space.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(Space.sm))
                Text(
                    "$incompleteCount 条记录还缺字段",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(Space.sm))
                Text(
                    if (onlyIncomplete) "显示全部" else "只看它们",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (shown.isEmpty()) {
            EmptyState(hasAnyRecord = items.isNotEmpty())
        } else {
            Spacer(Modifier.height(Space.md))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = Space.xxl)
            ) {
                itemsIndexed(shown, key = { _, it -> it.record.id }) { index, item ->
                    // 首尾行各自带一侧圆角，中间行直角 —— 视觉上连成一整张卡，
                    // 但每行仍是独立的 Lazy 项，记录再多也不会一次性渲染。
                    val shape = RoundedCornerShape(
                        topStart = if (index == 0) 20.dp else 0.dp,
                        topEnd = if (index == 0) 20.dp else 0.dp,
                        bottomStart = if (index == shown.lastIndex) 20.dp else 0.dp,
                        bottomEnd = if (index == shown.lastIndex) 20.dp else 0.dp
                    )
                    Surface(color = MaterialTheme.colorScheme.surface, shape = shape) {
                        Column {
                            RecordRow(
                                item = item,
                                photoFile = vm::photoFile,
                                onEdit = { onEdit(item.record.id) },
                                onDelete = { pendingDelete = item }
                            )
                            if (index != shown.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(
                                        start = Space.lg,
                                        end = Space.lg
                                    ),
                                    thickness = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 删除会连带删掉照片文件，不可恢复 —— 必须先问一次。
    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这条记录？") },
            text = {
                Text(
                    "「${target.record.awardName.ifBlank { "未填写获奖名称" }}」" +
                        "和它的 ${target.photos.size} 张照片会一起删除，无法恢复"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteRecord(target)
                    pendingDelete = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun RecordRow(
    item: RecordWithPhotos,
    photoFile: (String) -> File,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val record = item.record
    val missing = record.missingFields()

    JicunRow(onClick = onEdit) {
        if (item.photos.isNotEmpty()) {
            PhotoThumb(
                file = photoFile(item.photos.first().fileName),
                size = 56
            )
        } else {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxSize()
                ) {}
                Icon(
                    Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.width(Space.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                record.awardName.ifBlank { "未填写获奖名称" },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(Space.xxs))
            Text(
                listOfNotNull(
                    record.wuyu.ifBlank { null },
                    record.awardDate.ifBlank { "未填写时间" },
                    record.level.takeIf { it.isNotBlank() },
                    record.grade.takeIf { it.isNotBlank() }
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (missing.isNotEmpty() || item.photos.size > 1) {
                Spacer(Modifier.height(Space.xxs))
                Text(
                    buildString {
                        if (missing.isNotEmpty()) append("待补充：${missing.joinToString("、")}")
                        if (missing.isNotEmpty() && item.photos.size > 1) append("  ·  ")
                        if (item.photos.size > 1) append("${item.photos.size} 张证明")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (missing.isNotEmpty()) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        // 去掉原来的 chevron：整行已经可点，再放一个箭头是重复表达。
        IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun EmptyState(hasAnyRecord: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.PhotoLibrary,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.height(Space.md))
        Text(
            if (hasAnyRecord) "这个筛选下没有记录" else "还没有记录",
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            if (hasAnyRecord) "换个五育筛选试试" else "去拍一张证书，把努力存下来",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
