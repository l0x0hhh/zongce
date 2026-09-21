// 暨存的表单与选择组件：iOS 填充式字段、分组选择面板、照片缩略图。
//
// 上一版的问题：选择面板是「纯白底 + 白色选项行」，选项之间零分隔、分组标题只有一行小字，
// 于是「负责人（排名第一）/ 主要成员（第二、三）/ 一般成员」糊成一块，
// 用户根本看不出这是三个可点的选项。
// 这一版用灰底浮白卡 + 组内分隔线 + 独立分组标题把层级拆开，
// 并把所有可点行拉到 44dp 以上（HIG 下限）。
package com.zongce.app.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zongce.app.core.ImageTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 字段标签：独立成行，不用浮动标签。中文浮动标签会和输入内容抢同一条基线。 */
@Composable
fun FieldLabel(text: String, required: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        if (required) "$text *" else text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

/**
 * 填充式输入框（iOS 观感）：浅灰底、无描边、10dp 圆角。
 * 聚焦时才起一条品牌色描边 —— 未聚焦时留同宽透明边框，避免聚焦瞬间布局跳动。
 */
@Composable
fun JicunTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String? = null,
    required: Boolean = false,
    placeholder: String = "",
    supporting: String? = null,
    singleLine: Boolean = true,
    minHeight: Dp = 52.dp,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = MaterialTheme.shapes.small
    val stroke = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent

    Column(modifier = modifier.fillMaxWidth()) {
        if (label != null) {
            FieldLabel(label, required)
            Spacer(Modifier.height(Space.sm))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            interactionSource = interaction,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = minHeight)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.5.dp, stroke, shape)
                .padding(horizontal = Space.lg, vertical = Space.md),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    inner()
                }
            }
        )
        if (supporting != null) {
            Spacer(Modifier.height(Space.xs))
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 选择型字段：外观与输入框完全一致（同高、同底、同圆角），只有右侧换成箭头。
 * 「看起来一样的元素行为必须一样」—— 两个字段长得不同会让用户以为它们不是一类。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    groups: List<Pair<String, List<String>>> = emptyList(),
    placeholder: String = "请选择"
) {
    var expanded by remember { mutableStateOf(false) }
    val sections = if (groups.isEmpty()) listOf("" to options) else groups

    Column(modifier = modifier.fillMaxWidth()) {
        FieldLabel(label)
        Spacer(Modifier.height(Space.sm))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { expanded = true }
                .padding(horizontal = Space.lg, vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value.ifBlank { placeholder },
                style = MaterialTheme.typography.bodyLarge,
                color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(Space.sm))
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }

    if (expanded) {
        ModalBottomSheet(
            onDismissRequest = { expanded = false },
            // 灰底：面板靠"灰底浮白卡"表达分组，纯白底会让每一组都失去边界。
            containerColor = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.page)
                    .padding(bottom = Space.xxxl)
            ) {
                Text(label, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(Space.xs))
                Text(
                    if (value.isBlank()) "还没有选择" else "当前 $value",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                sections.forEachIndexed { index, (section, values) ->
                    Spacer(Modifier.height(Space.xxl))
                    if (section.isNotBlank()) {
                        Text(
                            section,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = Space.xs, bottom = Space.sm)
                        )
                    }
                    OptionGroup(values = values, selected = value) { picked ->
                        onChange(picked)
                        expanded = false
                    }
                    if (index == sections.lastIndex) Spacer(Modifier.height(Space.lg))
                }
            }
        }
    }
}

/** 一组选项共用一张白卡，组内用内缩分隔线切开 —— iOS 分组列表的标准做法。 */
@Composable
private fun OptionGroup(
    values: List<String>,
    selected: String,
    onPick: (String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            values.forEachIndexed { index, option ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = Space.lg, end = Space.lg),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                OptionRow(text = option, selected = option == selected) { onPick(option) }
            }
        }
    }
}

/**
 * 选项行：最小高度 44dp。
 * 按下反馈用整行变色而不是涟漪 —— 涟漪是圆形扩散，在整行宽度上会显得散；
 * 行高亮才和"我点的是这一行"对应得上。
 */
@Composable
private fun OptionRow(text: String, selected: Boolean, onClick: () -> Unit) {
    JicunRow(
        onClick = onClick,
        modifier = Modifier.heightIn(min = Space.touch)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Spacer(Modifier.width(Space.sm))
            Icon(
                Icons.Default.Check,
                contentDescription = "已选择",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 胶囊选择器：学年切换、五育筛选共用同一颗组件。
 * 选中是品牌实心、未选中是浅灰 —— 不用描边，描边在浅底上会显得毛糙。
 * 36dp 高：低于 44dp 的点击目标下限，但胶囊本身左右留白充足（横向点击面远大于 44dp），
 * 密集筛选场景下这个取舍是值得的。
 */
@Composable
fun JicunChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val background = when {
        selected -> MaterialTheme.colorScheme.primary
        pressed -> MaterialTheme.colorScheme.surfaceContainerHighest
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground = if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(background)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = Space.lg, vertical = Space.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = foreground)
    }
}

/**
 * 可点列表行：整行高亮，不用涟漪。
 * 涟漪是圆形扩散，铺在一整行的宽度上会显得散；行高亮才对得上"我点的是这一行"。
 * 内边距放在点击区之内 —— 否则高亮只有内容区那一条，按到空白处毫无反馈。
 */
@Composable
fun JicunRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = Space.lg,
        vertical = Space.md
    ),
    content: @Composable RowScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (pressed) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
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
            Icon(
                Icons.Default.PhotoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size((size / 2.6).dp)
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
