// 成果页：按学年回看已存的档案。
// 学年是学校综测的唯一时间口径，所以它是这一页的主轴 —— 用户来这里的问句
// 永远是"我这一学年攒下了什么"，而不是"我全部有多少条"。
//
// 多选删除（v1.4.0）：选择态与选择集都在 AppViewModel 里，本页只负责渲染与转发点击。
// 页面没有自己的 topBar / bottomBar（Scaffold 与 NavHost 在 MainActivity），
// 所以「选择」按钮进标题行、底部操作条用 Box 覆盖层 —— 不要给本页加 Scaffold，
// 那会和 MainActivity 的 NavHost padding 打架（双份 contentPadding）。
package com.zongce.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zongce.app.core.AcademicYear
import com.zongce.app.data.RecordWithPhotos

/** 选择模式下给列表补的底部留白，避免最后一行被操作条压住。 */
private val SELECTION_BAR_SPACE = 88.dp

@Composable
fun AchievementScreen(
    items: List<RecordWithPhotos>,
    vm: AppViewModel,
    onOpenRecord: (Long) -> Unit,
    onAddRecord: () -> Unit
) {
    val years = remember(items) { AcademicYear.yearsOf(items.map { it.record.awardDate }) }
    // null 表示初始学年还没从学年偏好读回，展示时临时回落到当前目标学年。
    var selectedYear by remember { mutableStateOf<String?>(null) }

    // 初值以持久化的学年偏好为准 —— 成果页刚打开时就停在用户上次看的学年，
    // 而不是每次都跳回当前学年。
    LaunchedEffect(Unit) {
        val initial = vm.initialAchievementYear()
        // 用户可能在读回之前就点了 chip，那时本地状态已经是真实选择，不能被覆盖
        if (selectedYear == null) selectedYear = initial
    }

    // 记录变动后，选中的学年可能已经不在列表里（比如删掉了唯一一条跨学年记录）。
    // 读回初值之前不做回落：Room 首帧是空列表，会把存储里的学年误判成"已不存在"。
    LaunchedEffect(years) {
        val current = selectedYear ?: return@LaunchedEffect
        if (current !in years) selectedYear = years.firstOrNull() ?: AcademicYear.LABEL
    }

    val year = selectedYear ?: AcademicYear.LABEL

    // 学年过滤走 AcademicYear.inYear（展示 / 多选 / 学年删除三处同一份口径）。
    // 排序放在过滤之后 —— inYear 保序不重排，排哪一端由展示决定。
    val ofYear = remember(items, year) {
        AcademicYear.inYear(items, year) { it.record.awardDate }
            .sortedByDescending { it.record.awardDate }
    }

    val selectionMode by vm.selectionMode.collectAsState()
    val selectedIds by vm.selectedIds.collectAsState()
    val deleting by vm.deleting.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }

    // 切学年清空选择集：选择集的意义是"当前学年里的几条"，跨学年留着会让人误删别的学年。
    LaunchedEffect(year) { vm.clearSelection() }

    // 删除成功后 VM 会退出选择态，弹窗必须跟着收掉（否则会挂在已消失的选择模式上）。
    LaunchedEffect(selectionMode) { if (!selectionMode) confirmDelete = false }

    // 返回键优先退出选择态，而不是直接退出页面 —— 用户按下返回时想撤销的是"选择"这个动作。
    BackHandler(enabled = selectionMode) { vm.exitSelectionMode() }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Space.page,
                end = Space.page,
                top = Space.lg,
                bottom = if (selectionMode) SELECTION_BAR_SPACE else Space.xxxl
            ),
            verticalArrangement = Arrangement.spacedBy(Space.lg)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("成果", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(Space.xs))
                        Text(
                            if (items.isEmpty()) "还没有存入任何记录"
                            else "共 ${items.size} 条记录 · 跨 ${years.size} 个学年",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // 空学年没有可选项，按钮就不出现 —— 一个点了没反应的按钮比没有按钮更糟。
                    if (ofYear.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                if (selectionMode) vm.exitSelectionMode() else vm.enterSelectionMode()
                            },
                            enabled = !deleting
                        ) {
                            Text(if (selectionMode) "取消" else "选择")
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm)
                ) {
                    years.forEach { year ->
                        JicunChip(
                            text = year,
                            selected = year == selectedYear,
                            onClick = {
                                selectedYear = year
                                // 写入学年偏好（成果页自己的 UI 偏好，杀 App 重开仍停在这个学年）。
                                // 只在点击时触发一次，不在重组路径上。
                                vm.saveAchievementYear(year)
                            }
                        )
                    }
                }
            }

            item { YearSummary(records = ofYear, year = year) }

            if (ofYear.isEmpty()) {
                item { EmptyYear(year = year, onAddRecord = onAddRecord) }
            } else {
                item {
                    RecordGroup(
                        records = ofYear,
                        selectionMode = selectionMode,
                        selectedIds = selectedIds,
                        onToggle = vm::toggleSelected,
                        onOpen = onOpenRecord
                    )
                }
            }
        }

        if (selectionMode) {
            SelectionBar(
                selectedCount = selectedIds.size,
                deleting = deleting,
                modifier = Modifier.align(Alignment.BottomCenter),
                onSelectAll = { vm.selectAll(ofYear.map { it.record.id }) },
                onDelete = { confirmDelete = true }
            )
        }
    }

    if (confirmDelete) {
        val photoCount = ofYear.filter { it.record.id in selectedIds }.sumOf { it.photos.size }
        DeleteConfirmDialog(
            recordCount = selectedIds.size,
            photoCount = photoCount,
            deleting = deleting,
            onConfirm = { vm.deleteSelected(selectedIds.toList()) },
            onDismiss = { confirmDelete = false }
        )
    }
}

/**
 * 底部操作条：全选 + 删除（N）。
 * 用 Box 覆盖层而不是导航条 —— 本页没有自己的 Scaffold，塞进 bottomBar
 * 会和 MainActivity 的那一条玻璃导航栏叠在一起。
 */
@Composable
private fun SelectionBar(
    selectedCount: Int,
    deleting: Boolean,
    modifier: Modifier = Modifier,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 3.dp,
        modifier = modifier
            .padding(horizontal = Space.page)
            .padding(bottom = Space.lg)
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onSelectAll, enabled = !deleting) { Text("全选") }
            Spacer(Modifier.weight(1f))
            Text(
                if (selectedCount == 0) "未选择" else "已选 $selectedCount 条",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(Space.md))
            Button(onClick = onDelete, enabled = selectedCount > 0 && !deleting) {
                Text("删除${if (selectedCount > 0) "（$selectedCount）" else ""}")
            }
        }
    }
}

/**
 * 多选删除的确认弹窗。
 * 必须把「多少条记录 + 多少张照片」都说出来：删除不可逆，用户要在点下去之前
 * 就能算出自己将要失去什么。取消按钮在删除期间保持可点（用户仍可反悔），
 * 只有「删除」按钮转圈并禁用。
 */
@Composable
private fun DeleteConfirmDialog(
    recordCount: Int,
    photoCount: Int,
    deleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        title = { Text("删除这 $recordCount 条记录？") },
        text = {
            Text(
                "已选 $recordCount 条记录和它们的 $photoCount 张照片会一起删除，无法恢复。",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !deleting) {
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
            TextButton(onClick = onDismiss, enabled = !deleting) { Text("取消") }
        }
    )
}

/** 该学年的两个数：攒了多少条、覆盖了几育。 */
@Composable
private fun YearSummary(records: List<RecordWithPhotos>, year: String) {
    val covered = records.map { it.record.wuyu }.distinct().size
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(vertical = Space.xl),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SummaryMetric(records.size.toString(), "条成果", Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(36.dp)
                    .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
            )
            SummaryMetric("$covered 育", "覆盖五育", Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(Modifier.height(Space.xxs))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/** 一学年一张白卡，组内靠内缩分隔线切分 —— iOS 分组列表的标准做法，比逐条飘卡片安静得多。 */
@Composable
private fun RecordGroup(
    records: List<RecordWithPhotos>,
    selectionMode: Boolean,
    selectedIds: Set<Long>,
    onToggle: (Long) -> Unit,
    onOpen: (Long) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            records.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = Space.lg, end = Space.lg),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                RecordRow(
                    item = item,
                    selectionMode = selectionMode,
                    checked = item.record.id in selectedIds,
                    onClick = {
                        // 选择模式下点行是勾选，不是进详情 ——
                        // 一边勾选一边跳页，用户会以为刚才那一下勾选没生效。
                        if (selectionMode) onToggle(item.record.id) else onOpen(item.record.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun RecordRow(
    item: RecordWithPhotos,
    selectionMode: Boolean,
    checked: Boolean,
    onClick: () -> Unit
) {
    val record = item.record
    JicunRow(
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = 64.dp)
            .padding(horizontal = Space.lg, vertical = Space.md)
    ) {
        if (selectionMode) {
            Checkbox(checked = checked, onCheckedChange = null)
            Spacer(Modifier.width(Space.sm))
        }
        // 圆点的颜色就是这条记录属于哪一育 —— 颜色在这里是信息，不是装饰。
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(WuyuPalette.of(record.wuyu))
        )
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
                    record.grade.takeIf { it.isNotBlank() }
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // 选择模式下收起箭头：它意味着"点进去看详情"，而此刻点行是勾选。
        if (!selectionMode) {
            Spacer(Modifier.width(Space.sm))
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** 空状态要给出下一步，而不是只说"没有"。 */
@Composable
private fun EmptyYear(year: String, onAddRecord: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.lg, vertical = Space.xxxl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.EmojiEvents,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(Space.md))
            Text("$year 学年还没有记录", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(Space.xs))
            Text(
                "拍下证书，它就会出现在这里",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Space.xs))
            TextButton(onClick = onAddRecord) { Text("新增记录") }
        }
    }
}
