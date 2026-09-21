// 桌面小组件本体：一张白色卡片，展示本学年的成果概览。
//
// 与 ADR-0001 原始约定的一处差异：小组件现在会**读** Room（只读，不写）。
// 展示型组件必须有数据可读，而"每次刷新都唤起 Activity 去取数"更重也更卡。
// 这次放松由 ADR-0002 记录；0001 的核心（录入入口统一由 MainActivity 承接）不变。
//
// 数据变化后由 AppViewModel.refreshWidget() 主动推送刷新；updatePeriodMillis 仍为 0。
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
// 这四个值对应 Theme.kt 的 surface / onSurface / onSurfaceVariant / primary。
private val CardColor = Color(0xFFFFFFFF)
private val InkColor = Color(0xFF18202B)
private val MutedColor = Color(0xFF5D6875)
private val PrimaryColor = Color(0xFF2D5F9A)

/** 小组件一屏要用的数据。没有记录时 count = 0、latestName = null。 */
private data class WidgetSummary(
    val year: String,
    val count: Int,
    val covered: Int,
    val latestName: String?
)

class JicunWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // 查库失败不能让小组件变成一片空白 —— 退化成"空档案"，至少还点得进 App。
        val summary = runCatching { loadSummary(context) }
            .getOrElse { WidgetSummary(AcademicYear.LABEL, 0, 0, null) }
        provideContent { JicunWidgetContent(summary) }
    }
}

private suspend fun loadSummary(context: Context): WidgetSummary {
    val items = AppDatabase.get(context).awardDao().allWithPhotos().first()
    val year = AcademicYear.LABEL
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
private fun JicunWidgetContent(summary: WidgetSummary) {
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
            // 并挤掉同行其它子项（v1.2.1 踩过，见 JicunWidget 的历史注释）。
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = summary.year,
                style = TextStyle(color = ColorProvider(MutedColor), fontSize = 11.sp)
            )
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

// 点整块卡片 → 直接落到成果页。显式组件 + action 字符串，
// MainActivity 靠 action 分辨要切到哪个 tab（见 WidgetActions）。
private fun openAchievementAction(context: Context): Action =
    actionStartActivity(
        Intent(context, MainActivity::class.java)
            .setAction(WidgetActions.OPEN_ACHIEVEMENT)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
