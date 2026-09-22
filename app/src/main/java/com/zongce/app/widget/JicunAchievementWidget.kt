// 第二个桌面小组件：成果概览，点击学年弹出选择器切换学年。
//
// 与 JicunWidget（拍照/相册入口）并列存在，两者职责刻意分开：
//   JicunWidget            —— 写入口。只发 Intent，不碰数据库（ADR-0001）。
//   JicunAchievementWidget —— 只读展示。会读 Room，但绝不写入、不参与照片生命周期（ADR-0002）。
//
// 交互分工：点学年主块弹学年选择器（透明壳 YearPickerActivity，不直接进 App），
// 点卡片其余部分进成果页。学年是本组件的视觉焦点（22sp 加粗主色），
// 垂直方向用 defaultWeight 弹性空隙把内容均匀撑开，避免组件拉大后下半空白。
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
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
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
// 这几个值对应 Theme.kt 的 surface / onSurface / onSurfaceVariant / primary。
private val CardColor = Color(0xFFFFFFFF)
private val InkColor = Color(0xFF18202B)
private val MutedColor = Color(0xFF5D6875)
private val PrimaryColor = Color(0xFF2D5F9A)

/**
 * 小组件当前展示的学年。
 *
 * 用 SharedPreferences 存：选择器 Activity 写完立刻触发 updateAll()，provideGlance 在
 * 同一进程重新执行，因此读到的就是最新值，不会出现"选了一下没反应"。
 *
 * internal：同包的 YearPickerActivity（透明壳选择器）也要读写它，单独再存一份
 * SharedPreferences 只会造成两个存储点。
 */
internal object AchievementYearStore {
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

    /** 选中某个学年。校验合法性后再写入，防止外部传进来一个根本不存在的学年。 */
    suspend fun select(context: Context, year: String) {
        val items = runCatching { AppDatabase.get(context).awardDao().allWithPhotos().first() }
            .getOrDefault(emptyList())
        val years = AcademicYear.yearsOf(items.map { it.record.awardDate })
        if (year in years) {
            prefs(context).edit().putString(KEY, year).apply()
        }
    }
}

/** 小组件一屏要用的数据。 */
private data class WidgetSummary(
    val year: String,
    val count: Int,
    val covered: Int,
    val latestName: String?
)

class JicunAchievementWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // 查库失败不能让小组件变成一片空白 —— 退化成"空档案"，至少还点得进 App。
        val summary = runCatching { loadSummary(context) }
            .getOrElse { WidgetSummary(AcademicYear.LABEL, 0, 0, null) }
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
    return WidgetSummary(
        year = year,
        count = ofYear.size,
        covered = ofYear.map { it.record.wuyu }.distinct().size,
        latestName = ofYear.firstOrNull()?.record?.awardName?.takeIf { it.isNotBlank() }
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
            .padding(14.dp)
    ) {
        // 头部：logo + 应用名，保持与其他小组件一致的品牌行。
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
        }

        // 头部与主体之间的弹性空隙。defaultWeight 只用于 Column 的纵向弹性，
        // 不与 fillMaxSize 叠在 Row 子项上（v1.2.1 踩过：会撑成 MATCH_PARENT 挤掉同排元素）。
        Spacer(GlanceModifier.defaultWeight())

        // 学年主块：全组件的视觉焦点，整块可点，弹学年选择器。
        Row(
            modifier = GlanceModifier.clickable(openYearPickerAction(context)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = summary.year,
                style = TextStyle(
                    color = ColorProvider(PrimaryColor),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(GlanceModifier.width(6.dp))
            // "▾" 暗示这里可以点开选择，而不是一个纯展示的年份。
            Text(
                text = "▾",
                style = TextStyle(color = ColorProvider(MutedColor), fontSize = 12.sp)
            )
        }

        Spacer(GlanceModifier.height(6.dp))

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
            Spacer(GlanceModifier.height(4.dp))
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
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(GlanceModifier.width(4.dp))
                Text(
                    text = "条成果 · 覆盖 ${summary.covered} 育",
                    style = TextStyle(color = ColorProvider(MutedColor), fontSize = 11.sp)
                )
            }
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = "最近 ${summary.latestName.orEmpty()}",
                style = TextStyle(color = ColorProvider(InkColor), fontSize = 11.sp),
                maxLines = 1
            )
        }

        // 底部弹性空隙：内容被均匀夹在中间，组件拉大也不留整片空白。
        Spacer(GlanceModifier.defaultWeight())
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

// 点学年主块 → 透明壳 Activity 里弹学年选择器。小组件本身无法弹对话框，
// 标准做法就是启动一个透明主题的 Activity 承载 AlertDialog。
private fun openYearPickerAction(context: Context): Action =
    actionStartActivity(
        Intent(context, YearPickerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
