// 桌面小组件之二：我的成果（只读）。
//
// 布局按 PRD §4.2 线框图（4×4 / 250×250dp）：标题行 → 统计行 → 分隔线 → 列表区 → 底部查看全部。
// 组件只读 Room，不写 award_records / award_photos，唯一允许写的是学年偏好（T03 的箭头）。
package com.zongce.app.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.PreviewSizeMode
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.zongce.app.MainActivity
import com.zongce.app.R
import com.zongce.app.WidgetActions
import kotlin.math.floor

class AchievementWidget : GlanceAppWidget() {

    /**
     * 必须是 Responsive：默认 Single 只按 minWidth/minHeight 渲染一档，
     * 组件被拉大时不会重渲染，列表区会被裁掉。
     */
    override val sizeMode: SizeMode = SizeMode.Responsive(SUPPORTED_SIZES)

    /**
     * 预览（API 35+ generated preview）也必须 Responsive —— 官方 troubleshooting 点名的坑：
     * 漏了这条，预览会按最小尺寸渲染，列表被裁，且现象首见于真机而不是编译期。
     */
    override val previewSizeMode: PreviewSizeMode = SizeMode.Responsive(SUPPORTED_SIZES)

    /**
     * 查库在 provideContent **之前**完成（T01 实测的 ANR 教训）：
     * provideGlance 受 WorkManager 十分钟预算保护，而 provideContent 之后 composition 只活
     * 约 45 秒；把 Room 查询放进 composition 里等于拿 45 秒的窗口去赌冷启动首帧。
     */
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = AchievementWidgetLoader.load(context)
        provideContent {
            AchievementWidgetContent(snapshot = snapshot)
        }
    }

    /** API 35+ 的选择器预览走这里：单次组合，没有 GlanceId，所以只能读全局 SP（学年）。 */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val snapshot = AchievementWidgetLoader.load(context)
        provideContent {
            AchievementWidgetContent(snapshot = snapshot)
        }
    }

    override fun onCompositionError(
        context: Context,
        glanceId: GlanceId,
        appWidgetId: Int,
        throwable: Throwable
    ) {
        // 默认实现会把异常吃掉后静默显示 errorUiLayout。这里留一条日志，
        // 否则"桌面上组件变成一块占位、但日志里什么都没有"会无从归因。
        Log.e(TAG, "composition error, appWidgetId=$appWidgetId", throwable)
    }

    companion object {
        private const val TAG = "AchievementWidget"

        /**
         * 列表形态开关。
         *
         * 取值依据 T01 真机 spike 结论（ADR-0003 文末回填表）：原生/类原生档①②③④四项全过、
         * 没有任何一档判否，因此维持默认的 LazyColumn（L1）。
         * 若将来某台 launcher 判否 → 改这里为 false，走 §3.4 的静态行数方案（L3）。
         * **注意：L2「共 X 条 · 查看全部」是无条件常驻的，不受这个开关影响** ——
         * 它是"用户不一定会去滑"的保险丝，翻开关不会让功能残废。
         */
        const val USE_LAZY_COLUMN = true

        /**
         * 列表硬上限，不是懒加载条数。
         *
         * API 32+ 走 RemoteCollectionItems，一次 Binder 事务把全部 item 推给桌面，
         * 官方 javadoc 明确警示事务里不能有大/多 Bitmaps；列表内因此**绝不出现照片缩略图**。
         */
        const val WIDGET_MAX_ROWS = 20

        /** 三档落地尺寸（4×3 最小 / 4×4 默认 / 4×5 拉宽），与 sizeMode 共用。 */
        private val SUPPORTED_SIZES = setOf(
            DpSize(250.dp, 180.dp),
            DpSize(250.dp, 250.dp),
            DpSize(320.dp, 250.dp)
        )

        /** L3 静态降级用的行高（§3.4 公式）。 */
        internal const val ROW_HEIGHT_DP = 44f

        /** L3 静态降级时，列表区之外被占用的高度：padding 12×2 + 标题 24 + 6 + 统计 44 + 6 + 分隔线 1 + 底部行 22。 */
        internal const val CHROME_HEIGHT_DP = 24f + 24f + 6f + 44f + 6f + 1f + 22f
    }
}

@Composable
private fun AchievementWidgetContent(snapshot: AchievementWidgetSnapshot) {
    val context = LocalContext.current
    val openAchievement = openAchievementAction(context)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(WidgetPalette.Card))
            .cornerRadius(22.dp)
            .padding(12.dp)
    ) {
        HeaderRow(snapshot = snapshot)
        Spacer(GlanceModifier.height(6.dp))
        StatsRow(snapshot = snapshot, context = context)
        Spacer(GlanceModifier.height(6.dp))
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ColorProvider(WidgetPalette.Divider))
        ) {}

        if (snapshot.rows.isEmpty()) {
            EmptyBody(context = context, snapshot = snapshot, onClick = openAchievement)
        } else if (AchievementWidget.USE_LAZY_COLUMN) {
            LazyListBody(snapshot = snapshot, context = context)
        } else {
            StaticListBody(snapshot = snapshot, context = context)
        }

        Spacer(GlanceModifier.height(4.dp))
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(openAchievement),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = context.getString(
                    R.string.achievement_widget_more,
                    snapshot.recordCount
                ),
                style = TextStyle(
                    color = ColorProvider(WidgetPalette.Primary),
                    fontSize = 11.sp
                )
            )
        }
    }
}

/** 标题行：组件独立学年，左右箭头直接在桌面切换。 */
@Composable
private fun HeaderRow(
    snapshot: AchievementWidgetSnapshot
) {
    val previous = snapshot.years.indexOf(snapshot.year) - 1
    val next = snapshot.years.indexOf(snapshot.year) + 1
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        YearArrow(
            label = "‹",
            enabled = previous >= 0,
            action = previous.takeIf { it >= 0 }?.let { yearAction(-1) }
        )
        Text(
            text = snapshot.year,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(
                color = ColorProvider(WidgetPalette.Ink),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.glance.text.TextAlign.Center
            )
        )
        YearArrow(
            label = "›",
            enabled = next < snapshot.years.size,
            action = next.takeIf { it < snapshot.years.size }?.let { yearAction(1) }
        )
    }
}

@Composable
private fun YearArrow(label: String, enabled: Boolean, action: Action?) {
    val modifier = GlanceModifier.size(28.dp)
    Text(
        text = label,
        modifier = if (enabled && action != null) modifier.clickable(action) else modifier,
        style = TextStyle(
            color = ColorProvider(if (enabled) WidgetPalette.Ink else WidgetPalette.Disabled),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.glance.text.TextAlign.Center
        )
    )
}

private fun yearAction(delta: Int): Action =
    actionRunCallback<WidgetYearActionCallback>(
        actionParametersOf(WidgetYearActionCallback.DELTA_KEY to delta)
    )

/** 统计行：「12 条成果 │ 覆盖 4 育」。空学年也保留这一行（PRD 空态口径）。 */
@Composable
private fun StatsRow(snapshot: AchievementWidgetSnapshot, context: Context) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(44.dp)
            .background(ColorProvider(WidgetPalette.StatTile))
            .cornerRadius(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatCell(
            leading = snapshot.recordCount.toString(),
            trailing = context.getString(R.string.achievement_widget_stat_count)
        )
        StatCell(
            leading = context.getString(R.string.achievement_widget_stat_cover_prefix),
            number = snapshot.coveredWuyu.toString(),
            trailing = context.getString(R.string.achievement_widget_stat_cover_suffix)
        )
    }
}

@Composable
private fun StatCell(leading: String, trailing: String, number: String? = null) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (number == null) {
            BigNumber(leading)
        } else {
            Text(
                text = leading,
                style = TextStyle(
                    color = ColorProvider(WidgetPalette.SecondaryText),
                    fontSize = 11.sp
                )
            )
            Spacer(GlanceModifier.width(3.dp))
            BigNumber(number)
        }
        Spacer(GlanceModifier.width(3.dp))
        Text(
            text = trailing,
            style = TextStyle(
                color = ColorProvider(WidgetPalette.SecondaryText),
                fontSize = 11.sp
            )
        )
    }
}

@Composable
private fun BigNumber(text: String) {
    Text(
        text = text,
        style = TextStyle(
            color = ColorProvider(WidgetPalette.Ink),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    )
}

/** L1 默认形态：Glance LazyColumn，最多 WIDGET_MAX_ROWS 条，itemId 用 record.id。 */
@Composable
private fun LazyListBody(snapshot: AchievementWidgetSnapshot, context: Context) {
    LazyColumn(modifier = GlanceModifier.fillMaxWidth()) {
        items(
            items = snapshot.rows,
            itemId = { it.id }
        ) { row ->
            RecordRow(row = row, onClick = openRecordAction(context, row.id))
        }
    }
}

/**
 * L3 降级形态：静态 Column，行数按剩余高度算（§3.4）。
 * 只有 USE_LAZY_COLUMN 被翻成 false 时才走到这里。
 */
@Composable
private fun StaticListBody(snapshot: AchievementWidgetSnapshot, context: Context) {
    val listH = LocalSize.current.height.value -
        AchievementWidget.CHROME_HEIGHT_DP
    val rows = floor(listH / AchievementWidget.ROW_HEIGHT_DP)
        .toInt()
        .coerceAtLeast(1)
        .coerceAtMost(AchievementWidget.WIDGET_MAX_ROWS)
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        snapshot.rows.take(rows).forEach { row ->
            RecordRow(row = row, onClick = openRecordAction(context, row.id))
        }
    }
}

/**
 * 列表里的一行：五育色点 + 获奖名称 + 「五育 · 日期 · 等级」。
 *
 * ⚠️ 必须是**单个 Row 根节点**：Glance 的 LazyListTranslator 里
 * `require(children.size == 1 && alignment == Alignment.CenterStart)` 会直接抛异常。
 * ⚠️ 列表内绝不出现照片缩略图（Binder 事务膨胀，见 WIDGET_MAX_ROWS 注释）。
 */
@Composable
private fun RecordRow(row: AchievementWidgetRow, onClick: Action) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 6.dp)
            .clickable(onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .size(8.dp)
                .background(ColorProvider(Color(row.wuyuColor)))
                .cornerRadius(4.dp)
        ) {}
        Spacer(GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = row.name,
                style = TextStyle(
                    color = ColorProvider(WidgetPalette.Ink),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Text(
                text = row.subline,
                style = TextStyle(
                    color = ColorProvider(WidgetPalette.SecondaryText),
                    fontSize = 11.sp
                ),
                maxLines = 1
            )
        }
    }
}

/** 空态：统计行保留，列表区换成文案，整块可点进 App（PRD §4.2 空态线框）。 */
@Composable
private fun EmptyBody(
    context: Context,
    snapshot: AchievementWidgetSnapshot,
    onClick: androidx.glance.action.Action
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = context.getString(
                R.string.achievement_widget_empty_title,
                snapshot.year
            ),
            style = TextStyle(
                color = ColorProvider(WidgetPalette.Ink),
                fontSize = 13.sp
            )
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = context.getString(R.string.achievement_widget_empty_hint),
            style = TextStyle(
                color = ColorProvider(WidgetPalette.SecondaryText),
                fontSize = 11.sp
            )
        )
    }
}

/**
 * 点整块进成果页。零新增路由：复用 MainActivity 的 singleTop + action 协议，
 * 与现有快速录入组件完全同一套写法。
 */
private fun openAchievementAction(context: Context): androidx.glance.action.Action =
    actionStartActivity(
        Intent(context, MainActivity::class.java)
            .setAction(WidgetActions.OPEN_ACHIEVEMENT)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )

private fun openRecordAction(context: Context, recordId: Long): Action =
    actionStartActivity(
        Intent(context, MainActivity::class.java)
            .setAction(WidgetActions.OPEN_RECORD)
            .putExtra(WidgetActions.RECORD_ID_EXTRA, recordId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
