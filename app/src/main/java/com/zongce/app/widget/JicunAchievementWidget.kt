// 第二个桌面小组件：成果概览，支持左右箭头切换学年。
//
// 与 JicunWidget（拍照/相册入口）并列存在，两者职责刻意分开：
//   JicunWidget            —— 写入口。只发 Intent，不碰数据库（ADR-0001）。
//   JicunAchievementWidget —— 只读展示。会读 Room，但绝不写入、不参与照片生命周期（ADR-0002）。
//
// 交互分工：点箭头切学年（不打开 App），点卡片其余部分进成果页。
// 数据变化后由 AppViewModel.refreshWidget() 主动推送刷新；updatePeriodMillis 仍为 0，
// 不做轮询 —— 桌面上的数字必须和 App 里看到的一致，靠推送不靠定时拉。
package com.zongce.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
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
import com.zongce.app.core.AcademicYear
import com.zongce.app.data.AppDatabase
import kotlinx.coroutines.flow.first

// 小组件由桌面进程渲染，拿不到 App 的 Material 主题，色值只能显式写死。
// 这几个值对应 Theme.kt 的 surface / onSurface / onSurfaceVariant / primary / outline。
private val CardColor = Color(0xFFFFFFFF)
private val InkColor = Color(0xFF18202B)
private val MutedColor = Color(0xFF5D6875)
private val DisabledColor = Color(0xFFC7D0DA)
private val PrimaryColor = Color(0xFF2D5F9A)

/** 切换方向：-1 更早的学年，+1 更晚的学年。 */
private val DIRECTION = ActionParameters.Key<Int>("widget_year_direction")

/**
 * 小组件当前展示的学年。
 *
 * 用 SharedPreferences 存：ActionCallback 写完立刻调用 update()，provideGlance 在同一进程
 * 重新执行，因此读到的就是最新值，不会出现"点了一下没反应"。
 */
private object AchievementYearStore {
    private const val PREFS = "jicun_achievement_widget"
    private const val KEY = "selected_year"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * 存的学年可能已经因为删记录而不存在了 —— 这种情况回落到当前目标学年，
     * 否则小组件会永远停在一个空学年上。
     */
    fun current(context: Context, years: List<String>): String {
        val saved = prefs(context).getString(KEY, null)
        return if (saved != null && saved in years) saved else AcademicYear.LABEL
    }

    suspend fun shift(context: Context, direction: Int) {
        // 学年列表要按实际数据重新算，不能靠 index 硬移 ——
        // 用户完全可能在 App 里删掉了某个学年的全部记录。
        val items = runCatching { AppDatabase.get(context).awardDao().allWithPhotos().first() }
            .getOrDefault(emptyList())
        val years = AcademicYear.yearsOf(items.map { it.record.awardDate })
        val now = current(context, years)
        val index = years.indexOf(now).takeIf { it >= 0 } ?: return
        val next = (index + direction).coerceIn(0, years.lastIndex)
        prefs(context).edit().putString(KEY, years[next]).apply()
    }
}

/** 点箭头切换学年。不打开 App —— 用户要的是在桌面上翻一翻，不是每次都被拽进应用。 */
class SwitchYearAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val direction = parameters[DIRECTION] ?: return
        AchievementYearStore.shift(context, direction)
        JicunAchievementWidget().update(context, glanceId)
    }
}

/** 小组件一屏要用的数据。 */
private data class WidgetSummary(
    val year: String,
    val count: Int,
    val covered: Int,
    val latestName: String?,
    val canGoPrev: Boolean,
    val canGoNext: Boolean
)

class JicunAchievementWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // 查库失败不能让小组件变成一片空白 —— 退化成"空档案"，至少还点得进 App。
        val summary = runCatching { loadSummary(context) }
            .getOrElse {
                WidgetSummary(AcademicYear.LABEL, 0, 0, null, false, false)
            }
        provideContent { AchievementWidgetContent(summary) }
    }
}

private suspend fun loadSummary(context: Context): WidgetSummary {
    val items = AppDatabase.get(context).awardDao().allWithPhotos().first()
    val years = AcademicYear.yearsOf(items.map { it.record.awardDate })
    val year = AchievementYearStore.current(context, years)
    // 与成果页同一套口径：先按学年归属过滤，再按获奖时间倒序取最近一条。
    val ofYear = items
        .filter { AcademicYear.belongsTo(it.record.awardDate, year) }
        .sortedByDescending { it.record.awardDate }
    val index = years.indexOf(year)
    return WidgetSummary(
        year = year,
        count = ofYear.size,
        covered = ofYear.map { it.record.wuyu }.distinct().size,
        latestName = ofYear.firstOrNull()?.record?.awardName?.takeIf { it.isNotBlank() },
        canGoPrev = index > 0,
        canGoNext = index in 0 until years.lastIndex
    )
}

@Composable
private fun AchievementWidgetContent(summary: WidgetSummary) {
    val context = LocalContext.current

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(CardColor))
            .cornerRadius(22.dp)
            .clickable(openAchievementAction(context))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(R.drawable.widget_logo),
                contentDescription = null,
                modifier = GlanceModifier.size(18.dp)
            )
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = context.getString(R.string.app_name),
                style = TextStyle(
                    color = ColorProvider(InkColor),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            // 只用 defaultWeight 推右，不叠 fillMaxSize —— 两者同叠会把宽度撑成 MATCH_PARENT
            // 并挤掉同行其它子项（v1.2.1 踩过这个坑）。
            Spacer(GlanceModifier.defaultWeight())

            YearArrow(glyph = "‹", direction = -1, enabled = summary.canGoPrev)
            Spacer(GlanceModifier.width(2.dp))
            Text(
                text = summary.year,
                style = TextStyle(color = ColorProvider(MutedColor), fontSize = 11.sp)
            )
            Spacer(GlanceModifier.width(2.dp))
            YearArrow(glyph = "›", direction = 1, enabled = summary.canGoNext)
        }

        Spacer(GlanceModifier.height(8.dp))

        if (summary.count == 0) {
            // 空状态说清"这里本该有什么"，而不是甩一个 0 给用户。
            Text(
                text = "还没有记录",
                style = TextStyle(
                    color = ColorProvider(InkColor),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = "拍下证书就会出现在这里",
                style = TextStyle(color = ColorProvider(MutedColor), fontSize = 11.sp)
            )
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = summary.count.toString(),
                    style = TextStyle(
                        color = ColorProvider(PrimaryColor),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(GlanceModifier.width(4.dp))
                Text(
                    text = "条成果 · 覆盖 ${summary.covered} 育",
                    style = TextStyle(color = ColorProvider(MutedColor), fontSize = 11.sp)
                )
            }
            Spacer(GlanceModifier.height(6.dp))
            Text(
                text = "最近 ${summary.latestName.orEmpty()}",
                style = TextStyle(color = ColorProvider(InkColor), fontSize = 11.sp),
                maxLines = 1
            )
        }
    }
}

/**
 * 学年箭头。到头的那一侧会变灰且不可点 ——
 * 与其点了没反应让人以为坏了，不如直接告诉用户"到头了"。
 */
@Composable
private fun YearArrow(glyph: String, direction: Int, enabled: Boolean) {
    val box = GlanceModifier.padding(horizontal = 5.dp, vertical = 3.dp)
    Box(
        modifier = if (enabled) {
            box.clickable(
                actionRunCallback<SwitchYearAction>(actionParametersOf(DIRECTION to direction))
            )
        } else {
            box
        }
    ) {
        Text(
            text = glyph,
            style = TextStyle(
                color = ColorProvider(if (enabled) InkColor else DisabledColor),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

// 点卡片其余部分 → 落到成果页。显式组件 + action 字符串，
// MainActivity 靠 action 分辨要落到哪个 tab（见 WidgetActions）。
private fun openAchievementAction(context: Context): Action =
    actionStartActivity(
        Intent(context, MainActivity::class.java)
            .setAction(WidgetActions.OPEN_ACHIEVEMENT)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
