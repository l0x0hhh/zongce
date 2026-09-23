// 成果页：按学年回看已存的档案。
// 学年是学校综测的唯一时间口径，所以它是这一页的主轴 —— 用户来这里的问句
// 永远是"我这一学年攒下了什么"，而不是"我全部有多少条"。
package com.zongce.app.ui

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

@Composable
fun AchievementScreen(
    items: List<RecordWithPhotos>,
    vm: AppViewModel,
    onOpenRecord: (Long) -> Unit,
    onAddRecord: () -> Unit
) {
    val years = remember(items) { AcademicYear.yearsOf(items.map { it.record.awardDate }) }
    // null 表示初始学年还没从组件存储读回，展示时临时回落到当前目标学年。
    var selectedYear by remember { mutableStateOf<String?>(null) }

    // 初值以组件存储为准 —— 成果页刚打开时选中的就是桌面组件正在显示的学年，
    // 否则用户没点过 chip 之前，两边各显各的，看起来还是"不同步"。
    LaunchedEffect(Unit) {
        val initial = vm.initialWidgetYear()
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

    val ofYear = remember(items, year) {
        items.filter { AcademicYear.belongsTo(it.record.awardDate, year) }
            .sortedByDescending { it.record.awardDate }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Space.page,
            end = Space.page,
            top = Space.lg,
            bottom = Space.xxxl
        ),
        verticalArrangement = Arrangement.spacedBy(Space.lg)
    ) {
        item {
            Column {
                Text("成果", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(Space.xs))
                Text(
                    if (items.isEmpty()) "还没有存入任何记录"
                    else "共 ${items.size} 条记录 · 跨 ${years.size} 个学年",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                            // 写入组件共用的学年存储并立刻推送重渲染 ——
                            // 这样回到桌面，组件显示的就是刚选的这个学年。
                            // 只在点击时触发一次，不在重组路径上。
                            vm.syncWidgetYear(year)
                        }
                    )
                }
            }
        }

        item { YearSummary(records = ofYear, year = year) }

        if (ofYear.isEmpty()) {
            item { EmptyYear(year = year, onAddRecord = onAddRecord) }
        } else {
            item { RecordGroup(records = ofYear, onOpen = onOpenRecord) }
        }
    }
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
private fun RecordGroup(records: List<RecordWithPhotos>, onOpen: (Long) -> Unit) {
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
                RecordRow(item = item) { onOpen(item.record.id) }
            }
        }
    }
}

@Composable
private fun RecordRow(item: RecordWithPhotos, onClick: () -> Unit) {
    val record = item.record
    JicunRow(
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = 64.dp)
            .padding(horizontal = Space.lg, vertical = Space.md)
    ) {
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
        Spacer(Modifier.width(Space.sm))
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp)
        )
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
