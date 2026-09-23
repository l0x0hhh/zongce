// QA 追加的对抗性边界用例（Edward / software-qa-engineer）。
//
// 目标：把 shouldAutoCheck 这条纯字符串比较往边界上推，区分
//   [设计如此] —— 行为可接受、失败方向安全（宁可多查一次，也不永久卡死）
//   [真缺陷]   —— 会导致"该查不查"或"永久卡死"
// 结论先写在这里：本文件全部用例均判为 [设计如此]，未发现真缺陷。
// shouldAutoCheck = lastCheckedDate.isNullOrBlank() || lastCheckedDate != today
// 即：任何"不认识/脏"的旧值都只会让它返回 true（多查），不会返回 false（漏查）。
// 唯一能产生 false 的条件是两串严格相等，而这只会由 markChecked 用
// LocalDate.now().toString() 这一种规范写法写入 —— 所以漏查路径不可达。
package com.zongce.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateThrottleBoundaryTest {

    /**
     * 关键安全性：库里的日期是"未来"时，绝不能被当成"今天已查过"而永久跳过。
     * 可达来源：用户手动把系统时间往未来拨，之后又拨回来；或跨时区旅行。
     * 预期多查一次（true），不会把用户永久锁死在没有更新提示的状态。
     */
    @Test
    fun futureStoredDateDoesNotPermanentlyBlockChecks() {
        assertTrue(UpdateThrottle.shouldAutoCheck("2027-01-01", "2026-02-14"))
        // 时钟拨回"正常"后，未来日期仍然只是"不是今天"，照查。
        assertTrue(UpdateThrottle.shouldAutoCheck("2026-12-31", "2026-02-14"))
    }

    /**
     * 用户手动把本地时钟往回拨：今天被算成"昨天"，库里的日期变成"明天/未来"。
     * 结果仍是多查一次（失败方向安全），不会漏查。
     */
    @Test
    fun clockMovedBackwardsOnlyCausesExtraCheckNotMissedOne() {
        // 昨天已查过（记为 2026-02-13）；用户把时钟拨回到 02-13。
        assertFalse(UpdateThrottle.shouldAutoCheck("2026-02-13", "2026-02-13"))
        // 再把时钟拨回到 02-12，库里的 02-13 成了"未来" → 多查一次。
        assertTrue(UpdateThrottle.shouldAutoCheck("2026-02-13", "2026-02-12"))
    }

    /**
     * 前导零差异：规范写入方永远是零填充的 "2026-02-14"，非零填充只能来自
     * 篡改/历史脏数据。此时判定为"不同的一天"→ 多查一次。判为 [设计如此]：
     * 纯字符串比较的预期后果，失败方向安全，不构成"永久跳过"。
     */
    @Test
    fun unpaddedStoredDateIsTreatedAsDifferentDayAndChecksAgain() {
        assertTrue(UpdateThrottle.shouldAutoCheck("2026-2-14", "2026-02-14"))
        assertTrue(UpdateThrottle.shouldAutoCheck("2026-02-4", "2026-02-04"))
    }

    /** 首尾空白 / 其它分隔符格式：同样只会导致多查一次，方向安全。 */
    @Test
    fun malformedOrWhitespacedStoredValuesOnlyCauseExtraChecks() {
        assertTrue(UpdateThrottle.shouldAutoCheck("2026-02-14 ", "2026-02-14"))
        assertTrue(UpdateThrottle.shouldAutoCheck(" 2026-02-14", "2026-02-14"))
        assertTrue(UpdateThrottle.shouldAutoCheck("14-02-2026", "2026-02-14"))
        assertTrue(UpdateThrottle.shouldAutoCheck("2026/02/14", "2026-02-14"))
        assertTrue(UpdateThrottle.shouldAutoCheck("not-a-date", "2026-02-14"))
    }

    /**
     * 对称性：比较是纯粹的 "a != b"，所以对任意 (a, b)，
     * shouldAutoCheck(a, b) == shouldAutoCheck(b, a) 必须成立。
     * 这里显式验证，防止将来有人把它改成有方向性的比较而悄悄破坏语义。
     */
    @Test
    fun comparisonIsSymmetric() {
        val samples = listOf(
            "2026-02-14",
            "2026-02-13",
            "2025-02-14",
            "2026-02-14 ",
            "2026-2-14",
            "not-a-date",
            ""
        )
        for (a in samples) {
            for (b in samples) {
                assertEquals(
                    "shouldAutoCheck 应对称：a=$a, b=$b",
                    UpdateThrottle.shouldAutoCheck(a, b),
                    UpdateThrottle.shouldAutoCheck(b, a)
                )
            }
        }
    }

    /** 同一天的两个不同 String 实例（值相等）必须判为"查过"→ 不查。 */
    @Test
    fun equalValueDifferentInstancesAreTreatedAsSameDay() {
        val stored = StringBuilder("2026-02-14").toString()
        val today = "2026-02-14".substring(0)
        assertFalse(UpdateThrottle.shouldAutoCheck(stored, today))
    }

    /** 月份 / 年份 / 闰日边界：跨过去就是新的一天，应查。 */
    @Test
    fun calendarBoundariesAreTreatedAsNewDays() {
        assertTrue(UpdateThrottle.shouldAutoCheck("2025-12-31", "2026-01-01")) // 跨年
        assertTrue(UpdateThrottle.shouldAutoCheck("2026-01-31", "2026-02-01")) // 跨月
        assertTrue(UpdateThrottle.shouldAutoCheck("2024-02-29", "2024-03-01")) // 闰日次日
        assertFalse(UpdateThrottle.shouldAutoCheck("2024-02-29", "2024-02-29")) // 闰日当天
    }

    /**
     * 防御性：today 为空白（理论上 today() 不会产出，但契约里没约束）。
     * 空白 today 不会被误判成"今天已查过"而漏查 —— 只会不停查。方向安全。
     */
    @Test
    fun blankTodayIsNeverTreatedAsAlreadyChecked() {
        assertTrue(UpdateThrottle.shouldAutoCheck("2026-02-14", ""))
        assertTrue(UpdateThrottle.shouldAutoCheck("2026-02-14", "   "))
        assertTrue(UpdateThrottle.shouldAutoCheck("", ""))
        assertTrue(UpdateThrottle.shouldAutoCheck(null, ""))
    }

    /** null 与空串语义等价：都表示"从未成功检查过"，必须查。 */
    @Test
    fun nullAndBlankAreEquivalentNeverCheckedStates() {
        assertTrue(UpdateThrottle.shouldAutoCheck(null, "2026-02-14"))
        assertTrue(UpdateThrottle.shouldAutoCheck("", "2026-02-14"))
        assertTrue(UpdateThrottle.shouldAutoCheck("   ", "2026-02-14"))
    }
}
