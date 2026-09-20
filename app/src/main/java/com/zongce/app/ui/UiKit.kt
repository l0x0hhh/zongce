// 暨存的通用表单、选项面板和照片预览组件。
package com.zongce.app.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zongce.app.core.ImageTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 下拉字段使用底部选项面板，避免长菜单遮挡当前表单。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    groups: List<Pair<String, List<String>>> = emptyList()
) {
    var expanded by remember { mutableStateOf(false) }
    val sections = if (groups.isEmpty()) listOf("" to options) else groups

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .heightIn(min = 56.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = value.ifBlank { "请选择" },
                color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }

    if (expanded) {
        ModalBottomSheet(onDismissRequest = { expanded = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                sections.forEach { (section, values) ->
                    if (section.isNotBlank()) {
                        Text(
                            section,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                        )
                    }
                    values.forEach { option ->
                        ListItem(
                            headlineContent = { Text(option) },
                            trailingContent = {
                                if (option == value) {
                                    Icon(Icons.Default.Check, contentDescription = "已选择")
                                }
                            },
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    onChange(option)
                                    expanded = false
                                }
                        )
                    }
                }
            }
        }
    }
}

/** 将角色按使用场景分组，减少长列表的阅读负担。 */
fun roleOptionGroups(options: List<String>): List<Pair<String, List<String>>> = listOf(
    "项目 / 竞赛" to listOf("负责人（排名第一）", "主要成员（第二、三）", "一般成员"),
    "队伍" to listOf("主力队员", "一般队员"),
    "论文 / 作品" to listOf("第一作者", "第二作者", "其他作者"),
    "个人" to listOf("个人项目")
).mapNotNull { (title, values) ->
    title to values.filter { it in options }
}.filter { it.second.isNotEmpty() }

/** 列表和录入页的本地照片缩略图。 */
@Composable
fun PhotoThumb(file: File, size: Int = 56, modifier: Modifier = Modifier) {
    val bmp: Bitmap? by produceState<Bitmap?>(null, file.path) {
        value = withContext(Dispatchers.IO) {
            runCatching { ImageTools.thumbnail(file, 400) }.getOrNull()
        }
    }
    BitmapThumb(bitmap = bmp, size = size, modifier = modifier)
}

/** 录入页新选照片的缩略图，直接从 Uri 异步解码。 */
@Composable
fun UriPhotoThumb(uri: Uri, size: Int = 72, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bmp: Bitmap? by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching { ImageTools.thumbnail(context, uri, 400) }.getOrNull()
        }
    }
    BitmapThumb(bitmap = bmp, size = size, modifier = modifier)
}

@Composable
private fun BitmapThumb(bitmap: Bitmap?, size: Int, modifier: Modifier) {
    if (bitmap == null) {
        Box(
            modifier = modifier
                .size(size.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "图片",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size.dp)
                .clip(MaterialTheme.shapes.small)
        )
    }
}
