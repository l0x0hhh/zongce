// 学年选择器：成果概览小组件点击学年主块后弹出的透明壳 Activity。
//
// 小组件（Glance/RemoteViews）无法直接弹对话框，业界标准做法是启动一个透明主题的
// Activity，在里面弹 AlertDialog。选完写回 AchievementYearStore、主动刷新小组件，
// 然后 finish —— 全程不进入 App 主界面。
//
// 工程未引入 appcompat，这里用平台 AlertDialog + ComponentActivity，
// 透明主题（Manifest 里声明的是框架自带 Theme.Translucent.NoTitleBar）与之兼容。
package com.zongce.app.widget

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import com.zongce.app.core.AcademicYear
import com.zongce.app.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class YearPickerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 透明壳：没有自己的界面，只负责承载对话框，转场一律不做动画。
        overridePendingTransition(0, 0)

        lifecycleScope.launch {
            // 数据库调用挪到 IO 线程；学年列表按实际数据算，不信任任何缓存。
            val years = withContext(Dispatchers.IO) {
                runCatching {
                    val items = AppDatabase.get(this@YearPickerActivity)
                        .awardDao().allWithPhotos().first()
                    AcademicYear.yearsOf(items.map { it.record.awardDate })
                }.getOrDefault(emptyList())
            }

            // 只有一个学年（或一个都没有）时没有可选的，提示完直接收场。
            if (years.size < 2) {
                Toast.makeText(
                    this@YearPickerActivity,
                    "当前只有一个学年，无需切换",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
                return@launch
            }

            showYearDialog(years)
        }
    }

    /** 弹单选对话框。默认勾选小组件当前存储的学年。 */
    private fun showYearDialog(years: List<String>) {
        val checkedIndex = years.indexOf(
            AchievementYearStore.current(this, years)
        ).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("选择学年")
            .setSingleChoiceItems(years.toTypedArray(), checkedIndex) { dialog, which ->
                dialog.dismiss()
                lifecycleScope.launch {
                    AchievementYearStore.select(this@YearPickerActivity, years[which])
                    // 写回后立刻推送刷新，桌面上的数字和学年马上对齐。
                    JicunAchievementWidget().updateAll(this@YearPickerActivity)
                    finish()
                }
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setOnCancelListener {
                // 点对话框外面 / 按返回键关闭，同样收场。
                finish()
            }
            .show()
    }

    // finish 必须发生在对话框点击回调里，不能在 onCreate 里提前结束，
    // 否则对话框根本来不及展示。
    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}
