// 桌面小组件本体：一张白色卡片，内含「拍照」「相册」两个入口。
// 只负责发 Intent 给主 Activity，不碰数据库、不保存照片（见 docs/adr/0001）。
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

// 与 Theme.kt 的轻档案配色保持一致，但小组件不依赖 Compose Material 主题，
// 所以这里显式写死色值（RemoteViews 由桌面渲染，拿不到 App 的 CompositionLocal）。
private val CardColor = Color(0xFFFFFFFF)
private val InkColor = Color(0xFF1B2430)
private val PrimaryColor = Color(0xFF2D5F9A)
private val PrimaryContainerColor = Color(0xFFDDEAFF)

class JicunWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { JicunWidgetContent() }
    }
}

@Composable
private fun JicunWidgetContent() {
    val context = LocalContext.current

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(CardColor))
            .cornerRadius(22.dp)
            .padding(14.dp)
    ) {
        // 标题行：点哪里都能打开 App（不带 action，走正常启动）。
        Row(
            modifier = GlanceModifier.clickable(openAppAction(context)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(R.drawable.widget_logo),
                contentDescription = null,
                modifier = GlanceModifier.size(22.dp)
            )
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = context.getString(R.string.app_name),
                style = TextStyle(
                    color = ColorProvider(InkColor),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        Spacer(GlanceModifier.height(12.dp))

        // 两个入口：拍照是主路径，用主色填充；相册是次路径，用浅蓝底。
        Row(modifier = GlanceModifier.fillMaxSize()) {
            EntryTile(
                label = context.getString(R.string.widget_capture),
                iconRes = R.drawable.ic_widget_camera,
                backgroundColor = PrimaryColor,
                contentColor = Color.White,
                modifier = GlanceModifier.defaultWeight(),
                onClick = entryAction(context, WidgetActions.CAPTURE)
            )
            Spacer(GlanceModifier.width(10.dp))
            EntryTile(
                label = context.getString(R.string.widget_pick_photos),
                iconRes = R.drawable.ic_widget_gallery,
                backgroundColor = PrimaryContainerColor,
                contentColor = PrimaryColor,
                modifier = GlanceModifier.defaultWeight(),
                onClick = entryAction(context, WidgetActions.PICK_PHOTOS)
            )
        }
    }
}

@Composable
private fun EntryTile(
    label: String,
    iconRes: Int,
    backgroundColor: Color,
    contentColor: Color,
    modifier: GlanceModifier,
    onClick: Action
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ColorProvider(backgroundColor))
            .cornerRadius(16.dp)
            .clickable(onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = null,
            modifier = GlanceModifier.size(20.dp)
        )
        Spacer(GlanceModifier.height(6.dp))
        Text(
            text = label,
            style = TextStyle(
                color = ColorProvider(contentColor),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

// 入口 Intent 用显式组件 + action 字符串，主 Activity 靠 action 分辨是拍照还是相册。
// 加 NEW_TASK 是为了复用已打开的 App 任务栈（配合 launchMode="singleTop" 走 onNewIntent），
// 否则从桌面点开会再起一个实例。
private fun entryAction(context: Context, action: String): Action =
    actionStartActivity(
        Intent(context, MainActivity::class.java)
            .setAction(action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )

private fun openAppAction(context: Context): Action =
    actionStartActivity(
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
