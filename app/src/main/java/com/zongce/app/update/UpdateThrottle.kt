// 启动自动检查更新的节流判断与本地记录。
package com.zongce.app.update

import android.content.Context

/**
 * 自动检查更新的节流。
 *
 * 为什么需要节流：GitHub 未认证接口限流是每小时 60 次/IP，App 又面向全校学生，
 * 如果每次冷启动都查一次，高峰期很容易把 IP 额度耗光、导致所有人都查不到更新。
 * 所以规定"每天最多自动查一次"，把请求量压到可预期的一人一天一次。
 *
 * 为什么只记"成功完成的日期"：万一今天断网、检查失败，若不记时间戳，
 * 用户今天一整天都不会再自动检查；只记成功，才能让失败在下次启动时自然重试。
 */
internal object UpdateThrottle {

    private const val PREFS_NAME = "update_prefs"
    private const val KEY_LAST_CHECK_DATE = "last_auto_update_check_date"

    /**
     * 今天是否应当自动检查。
     *
     * 从未检查过（null/空）→ 要查；上次查的不是今天 → 要查；上次就是今天 → 不查。
     * 抽成不依赖 Android 的纯函数，是为了能被 JVM 单测直接覆盖。
     *
     * @param lastCheckedDate 上次"成功完成"检查的本地日期，格式 yyyy-MM-dd；从未记录为 null
     * @param today 今天的本地日期，格式 yyyy-MM-dd
     */
    internal fun shouldAutoCheck(lastCheckedDate: String?, today: String): Boolean =
        lastCheckedDate.isNullOrBlank() || lastCheckedDate != today

    /** 读上次成功检查的本地日期（yyyy-MM-dd）；从未记录过返回 null。 */
    internal fun lastCheckDate(context: Context): String? =
        prefs(context).getString(KEY_LAST_CHECK_DATE, null)

    /** 把"上次成功检查的日期"记为今天。失败路径不要调用它。 */
    internal fun markChecked(context: Context, today: String) {
        prefs(context).edit().putString(KEY_LAST_CHECK_DATE, today).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
