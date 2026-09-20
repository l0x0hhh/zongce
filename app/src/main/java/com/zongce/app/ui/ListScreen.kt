package com.zongce.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zongce.app.data.PhotoStore
import com.zongce.app.data.RecordWithPhotos
import com.zongce.app.data.WUYU_LIST

@Composable
fun ListScreen(
    items: List<RecordWithPhotos>,
    vm: AppViewModel,
    onEdit: (Long) -> Unit
) {
    val context = LocalContext.current
    val photoStore = remember { PhotoStore(context) }
    var filter by remember { mutableStateOf("全部") }
    var onlyIncomplete by remember { mutableStateOf(false) }

    val incompleteCount = items.count { it.record.missingFields().isNotEmpty() }

    val shown = items
        .filter { filter == "全部" || it.record.wuyu == filter }
        .filter { !onlyIncomplete || it.record.missingFields().isNotEmpty() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        // 待补充横幅（顶部常驻）
        if (incompleteCount > 0) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onlyIncomplete = !onlyIncomplete }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "待补充 $incompleteCount 条（缺级别/等级/角色）",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (onlyIncomplete) "显示全部" else "只看这些",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // 五育筛选
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("全部").plus(WUYU_LIST).forEach { w ->
                FilterChip(selected = filter == w, onClick = { filter = w }, label = { Text(w) })
            }
        }

        Spacer(Modifier.height(12.dp))

        if (shown.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("这里还没有记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("去拍一张证书吧", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.record.id }) { item ->
                    RecordRow(
                        item = item,
                        photoStore = photoStore,
                        onEdit = { onEdit(item.record.id) },
                        onDelete = { vm.deleteRecord(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordRow(
    item: RecordWithPhotos,
    photoStore: PhotoStore,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val r = item.record
    val miss = r.missingFields()
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onEdit() }
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (item.photos.isNotEmpty()) {
                PhotoThumb(file = photoStore.photoFile(item.photos.first().fileName), size = 56)
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(r.awardName.ifBlank { "（缺获奖名称）" },
                    style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${r.wuyu} · ${r.awardDate.ifBlank { "缺时间" }}" +
                        (if (r.level.isNotBlank()) " · ${r.level}" else "") +
                        (if (r.grade.isNotBlank()) " ${r.grade}" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (miss.isNotEmpty()) {
                    Text("待补充：${miss.joinToString("、")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary)
                }
                if (item.photos.size > 1) {
                    Text("${item.photos.size} 张证明",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除")
            }
        }
    }
}
