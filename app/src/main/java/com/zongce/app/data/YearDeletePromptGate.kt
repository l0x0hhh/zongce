// 导出后「要不要问一句：删除这一学年？」的闸门。
//
// 抽成纯 Kotlin 类（零 Android 依赖）只有一个理由：这条规则是 P0-4 / P0-5 的核心，
// 而它原本藏在 AndroidViewModel 里 —— 本项目 JVM 单测是纯 JUnit4、没有 Robolectric，
// ViewModel 在 JVM 上起不来，于是"三种回来方式各弹一次且仅一次"只能靠真机碰运气。
// 抽出来之后它可以直接被单测钉死。
//
// 三条规则，一条都不能破：
//   1. 必须真的发起过分享才问（挡住"分享之前的一次普通 resume"）；
//   2. 一次导出生命期只问一次，无论用户选删除还是保留；
//   3. 三个状态只活在内存里 —— 进程被杀，冷启动不弹不删。
// 第 3 条由"这只是个普通对象、没有任何持久化"天然保证，不需要额外代码：
// 删除不可逆，"没得到用户明确确认就删"是绝不能犯的错，宁愿少一次便利。
package com.zongce.app.data

/**
 * @param onPrompt 需要弹窗时回调，参数是学年。回调在 [onReturnedFromShare] 里同步触发，
 *                 **最多一次**；查库算条数那一跳由调用方（ViewModel）自己异步做。
 */
class YearDeletePromptGate(
    private val onPrompt: (year: String) -> Unit
) {

    @Volatile
    private var pendingDeleteYear: String? = null

    /** 是否已经真正发起过分享。 */
    @Volatile
    private var shareLaunched = false

    /** 本次导出生命期内是否已经问过。 */
    @Volatile
    private var promptConsumed = false

    /**
     * 点「分享材料包」时调用。
     *
     * 刻意**不**重置 [promptConsumed]：一次导出生命期只问一次。用户在分享面板里取消、
     * 回来点"保留"、再分享一次 —— 不该被追问第二次，那会让"保留"这个选择看起来没被尊重。
     */
    fun markShared(year: String) {
        pendingDeleteYear = year
        shareLaunched = true
    }

    /**
     * 回到 App。ActivityResultLauncher 回调与 ON_RESUME 兜底**必须调这同一个函数**：
     * 部分 ROM 的 chooser 不回填 ActivityResult，只用 launcher 就永远不弹；
     * 只用生命周期又分不清"分享前的一次普通 resume" —— 所以两条路都要，且必须幂等。
     *
     * @return true 表示这一次触发了询问（供调用方与测试判读）
     */
    fun onReturnedFromShare(): Boolean {
        val year = pendingDeleteYear ?: return false
        if (!shareLaunched) return false
        if (promptConsumed) return false
        // 先置位再回调：置位放在回调之后的话，回调里起飞的那一次异步查库期间
        // 第二次 resume 会穿透守卫，弹窗就会弹两次。
        promptConsumed = true
        onPrompt(year)
        return true
    }

    /** 用户选了「保留」，或删除流程走完（成或败都算走完）：清掉"待问 / 已分享"两个标记。 */
    fun dismiss() {
        clearPending()
    }

    /**
     * 删除失败时放开闸门，让用户能再试一次。
     *
     * 做的是完整的 [reset]（三个状态全清），而不只是放开 [promptConsumed]：
     * 只放开 promptConsumed 会让闸门停在"上了膛"的状态 —— pendingDeleteYear 与
     * shareLaunched 都还在，于是**任何一次**普通 resume（锁屏再亮、切出去再回来）
     * 都会直接弹窗，不再需要一次新的分享。把这条保证内聚在闸门里，
     * 就不用依赖"调用方一定会紧接着再调一次 [dismiss]"这种调用点顺序。
     */
    fun allowRetry() {
        reset()
    }

    /** 「导出其他学年」等重置场景：三个状态全部清零，本次导出生命期结束。 */
    fun reset() {
        clearPending()
        promptConsumed = false
    }

    private fun clearPending() {
        pendingDeleteYear = null
        shareLaunched = false
    }
}
