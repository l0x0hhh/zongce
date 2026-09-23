// 导出后「要不要删这一学年」的闸门用例。
//
// 这个类是 P0-4 / P0-5 的全部规则所在，而它的规则是**时序**规则 ——
// 三条回来路径（真分享完返回 / 取消分享返回 / 按 Home 切走再回）各弹一次且仅一次、
// 点「保留」后不再追问、进程被杀后冷启动不弹不删 ——
// 这些在真机上只能靠人手一遍遍点，且每一次改动都可能静默回归。抽成纯类之后在这里钉死。
package com.zongce.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YearDeletePromptGateTest {

    // 学年显式钉死：任何"当前学年"式的取值都会在某个 9 月 1 日把测试变红。
    private val year = "2025-2026"

    @Test
    fun aPlainResumeBeforeAnyShareNeverPrompts() {
        // 「回到 App 才弹」的另一半：根本没分享过，就不该弹。
        // 挡的就是"分享之前的那一次普通 resume"（进页面、锁屏再解锁都会走到）。
        val gate = recorder()

        repeat(3) { assertFalse(gate.onReturnedFromShare()) }

        assertTrue(gate.promptedYears.isEmpty())
    }

    @Test
    fun returningAfterASharePromptsExactlyOnce() {
        val gate = recorder()
        gate.markShared(year)

        assertTrue(gate.onReturnedFromShare())
        // 幂等：同一个函数会被 launcher 回调和 ON_RESUME 各调一次，
        // 某些 ROM 还会在一次返回里送出多次 resume —— 只能放行第一次。
        repeat(5) { assertFalse(gate.onReturnedFromShare()) }

        assertEquals(listOf(year), gate.promptedYears)
    }

    @Test
    fun launcherCallbackAndLifecycleResumeShareOneIdempotentGate() {
        // 两条触发源必须打到同一个闸门：各弹一次是最典型的回归，
        // 表现是用户关掉弹窗后它又弹回来（像是"关不掉"）。
        val gate = recorder()
        gate.markShared(year)

        gate.onReturnedFromShare()   // ActivityResultLauncher 回调
        gate.onReturnedFromShare()   // ON_RESUME 兜底（同一帧内紧接着到）

        assertEquals(listOf(year), gate.promptedYears)
    }

    @Test
    fun cancellingTheShareStillCountsAsComingBack() {
        // 取消分享也算"回来了"，照样问 —— 用户亲口定的硬约束。
        val gate = recorder()
        gate.markShared(year)

        assertTrue("取消分享返回也必须弹一次", gate.onReturnedFromShare())

        assertEquals(listOf(year), gate.promptedYears)
    }

    @Test
    fun pressingHomeAndComingBackStillCountsAsComingBack() {
        // 按 Home 切走再回来：同样是"回到 App"。
        val gate = recorder()
        gate.markShared(year)

        assertTrue(gate.onReturnedFromShare())

        assertEquals(listOf(year), gate.promptedYears)
    }

    @Test
    fun thePromptIsRaisedSynchronouslyInsideTheReturn() {
        // 「先置位、再回调」的顺序不能反：置位放到回调之后的话，
        // 回调里起飞的那一次异步查库期间，第二次 resume 会穿透守卫。
        val events = mutableListOf<String>()
        val gate = YearDeletePromptGate { events += "prompt" }
        gate.markShared(year)

        gate.onReturnedFromShare()

        // 返回值为 true 时回调已经发生过了 —— 此刻再 resume 一定被挡住。
        events += "second-return"
        assertFalse(gate.onReturnedFromShare())
        assertEquals(listOf("prompt", "second-return"), events)
    }

    @Test
    fun keepingTheRecordsStopsAnyFurtherPromptForThisExport() {
        // 点「保留」后再次分享不该再被追问 —— 那会让"保留"这个选择看起来没被尊重。
        val gate = recorder()
        gate.markShared(year)
        gate.onReturnedFromShare()
        gate.dismiss()

        gate.markShared(year)                       // 用户又点了一次「分享材料包」
        repeat(2) { assertFalse(gate.onReturnedFromShare()) }

        assertEquals(listOf(year), gate.promptedYears)
    }

    @Test
    fun sharingTwiceBeforeReturningStillPromptsOnce() {
        // 连点两次「分享材料包」：chooser 叠了两层，只问一次。
        val gate = recorder()
        gate.markShared(year)
        gate.markShared(year)

        assertTrue(gate.onReturnedFromShare())
        assertFalse(gate.onReturnedFromShare())

        assertEquals(listOf(year), gate.promptedYears)
    }

    @Test
    fun deletingTheYearClosesTheGateForGood() {
        // 删除成功：弹窗收掉、闸门关闭，之后无论怎么 resume 都不再问。
        val gate = recorder()
        gate.markShared(year)
        gate.onReturnedFromShare()
        gate.dismiss()

        repeat(3) { assertFalse(gate.onReturnedFromShare()) }

        assertEquals(listOf(year), gate.promptedYears)
    }

    @Test
    fun aFailedDeleteReopensTheGateButStillNeedsANewShare() {
        // 删除失败要能重试，但重试不能变成"追着用户弹"：
        // 放开闸门之后，下一次询问仍必须由一次新的分享 + 回来触发。
        val gate = recorder()
        gate.markShared(year)
        gate.onReturnedFromShare()
        gate.allowRetry()   // deleteYear 的 catch
        gate.dismiss()      // deleteYear 的 finally

        assertFalse("光靠一次普通 resume 不能重新弹", gate.onReturnedFromShare())

        gate.markShared(year)
        assertTrue("重新分享并回来才允许再问一次", gate.onReturnedFromShare())

        assertEquals(listOf(year, year), gate.promptedYears)
    }

    @Test
    fun retryingAfterAFailureStillPromptsOnlyOnce() {
        // 上面那条验证"重问要不要再分享一次"，这条把重试路径的**幂等性**补上：
        // 失败重试不是"放开一次"，而是完整重开一轮 —— 重开之后同样只问一次，
        // 不会因为走过一轮失败就退化成"每次回来都弹"。
        val gate = recorder()
        gate.markShared(year)
        gate.onReturnedFromShare()      // 第 1 次询问
        gate.allowRetry()               // deleteYear 的 catch
        gate.dismiss()                  // deleteYear 的 finally

        repeat(3) { assertFalse(gate.onReturnedFromShare()) }

        gate.markShared(year)
        assertTrue(gate.onReturnedFromShare())    // 第 2 次询问（重试）
        repeat(3) { assertFalse(gate.onReturnedFromShare()) }

        assertEquals(listOf(year, year), gate.promptedYears)
    }

    @Test
    fun allowRetryAloneDoesNotArmTheGateForTheNextResume() {
        // 放开门的保证必须内聚在闸门里，不能靠调用方"记得再调一次 dismiss()"撑着：
        // allowRetry() 自己做完整清理，所以单独调它之后闸门是**下了膛**的 ——
        // 紧接着的一次普通 resume（锁屏再亮、切出去再回来）绝不能弹窗，
        // 重新询问必须由一次新的分享 + 回来触发。
        //
        // （这条的前身记的是相反的行为：旧实现只放开 promptConsumed，
        //  于是它会把闸门留在"上了膛"状态。实现收口后本用例同步翻成断言"不触发"。）
        val gate = recorder()
        gate.markShared(year)
        gate.onReturnedFromShare()
        gate.allowRetry()

        assertFalse("allowRetry 之后不能靠一次普通 resume 就再弹", gate.onReturnedFromShare())

        gate.markShared(year)
        assertTrue("重新分享并回来才允许再问一次", gate.onReturnedFromShare())

        assertEquals(listOf(year, year), gate.promptedYears)
    }

    @Test
    fun resetReopensTheGateForAnotherExport() {
        // 「导出其他学年」走 resetExport()：本次导出生命期结束，重新分享会重新问。
        val gate = recorder()
        gate.markShared(year)
        gate.onReturnedFromShare()
        gate.reset()

        gate.markShared(year)
        assertTrue(gate.onReturnedFromShare())
        assertFalse(gate.onReturnedFromShare())

        assertEquals(listOf(year, year), gate.promptedYears)
    }

    @Test
    fun thePromptedYearIsTheOneFromTheShareNotTheOneOnScreen() {
        // 弹窗问的学年必须来自 ExportState.Done 里那个 targetYear：
        // UI 局部的 targetYear 会被 LaunchedEffect(availableYears) 的回落改掉，
        // 用它会问错学年。
        val gate = recorder()
        gate.markShared("2025-2026")
        gate.onReturnedFromShare()
        gate.reset()
        gate.markShared("2024-2025")
        gate.onReturnedFromShare()

        assertEquals(listOf("2025-2026", "2024-2025"), gate.promptedYears)
    }

    @Test
    fun theGateHasNoPersistenceOutlet() {
        // 「三个状态只活在内存里」—— 进程被杀后冷启动必须不弹不删。
        // 这条没法靠行为测（JVM 上杀不了进程），但可以守住结构：
        // 这个类不许有任何 Android 类型的字段或入参，于是它没有可持久化的出口。
        val cls = YearDeletePromptGate::class.java

        cls.declaredFields.forEach { field ->
            assertFalse(
                "闸门不许持有 Android 类型字段（${field.name}: ${field.type.name}）",
                field.type.name.startsWith("android.")
            )
        }
        cls.declaredMethods.forEach { method ->
            method.parameterTypes.forEach { type ->
                assertFalse(
                    "闸门不许接收 Android 类型入参（${method.name}: ${type.name}）",
                    type.name.startsWith("android.")
                )
            }
        }

        // 三个状态字段必须还在：少一个就会退化成"每次回来都弹"。
        val names = cls.declaredFields.map { it.name }.toSet()
        assertTrue(names.contains("pendingDeleteYear"))
        assertTrue(names.contains("shareLaunched"))
        assertTrue(names.contains("promptConsumed"))
    }

    /** 一个把每次弹窗的学年记下来的闸门。 */
    private fun recorder(): RecordingGate {
        val prompted = mutableListOf<String>()
        val gate = YearDeletePromptGate { prompted += it }
        return RecordingGate(gate, prompted)
    }

    private class RecordingGate(
        val gate: YearDeletePromptGate,
        val promptedYears: List<String>
    ) {
        fun markShared(year: String) = gate.markShared(year)
        fun onReturnedFromShare(): Boolean = gate.onReturnedFromShare()
        fun dismiss() = gate.dismiss()
        fun allowRetry() = gate.allowRetry()
        fun reset() = gate.reset()
    }
}
