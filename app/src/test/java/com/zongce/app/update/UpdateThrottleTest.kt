// 覆盖"每天最多自动检查一次"的纯逻辑判断。它不碰 SharedPreferences、不碰网络，
// 所以能在 JVM 单测里直接跑。
package com.zongce.app.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateThrottleTest {

    @Test
    fun neverCheckedBeforeShouldCheck() {
        // 全新安装：从没记录过日期，第一次启动要检查。
        assertTrue(UpdateThrottle.shouldAutoCheck(null, "2026-02-14"))
    }

    @Test
    fun checkedTodayShouldNotCheck() {
        // 今天已经自动查过了，本次启动直接跳过，避免反复请求被限流。
        assertFalse(UpdateThrottle.shouldAutoCheck("2026-02-14", "2026-02-14"))
    }

    @Test
    fun checkedYesterdayShouldCheck() {
        assertTrue(UpdateThrottle.shouldAutoCheck("2026-02-13", "2026-02-14"))
    }

    @Test
    fun checkedLastMonthShouldCheck() {
        assertTrue(UpdateThrottle.shouldAutoCheck("2026-01-14", "2026-02-14"))
    }

    @Test
    fun checkedLastYearShouldCheck() {
        assertTrue(UpdateThrottle.shouldAutoCheck("2025-02-14", "2026-02-14"))
    }

    @Test
    fun emptyStringIsTreatedAsNeverChecked() {
        // 空串代表脏数据 / 从未写入，应等价于"没查过"，不能因为非 null 就当成"今天查过"。
        assertTrue(UpdateThrottle.shouldAutoCheck("", "2026-02-14"))
    }

    @Test
    fun blankStringIsTreatedAsNeverChecked() {
        assertTrue(UpdateThrottle.shouldAutoCheck("   ", "2026-02-14"))
    }

    @Test
    fun otherDayInSameMonthStillChecks() {
        assertTrue(UpdateThrottle.shouldAutoCheck("2026-02-01", "2026-02-02"))
    }
}
