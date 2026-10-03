// 更新域 ViewModel：更新检查、下载、节流与手动/静默并发保护。
// 由原 AppViewModel 按功能域拆出（见 docs/design/architecture-refactor-step1-vm-split.md）。
// 只依赖 update 包（UpdateChecker / UpdateThrottle），不摸 DAO、不碰记录域。
package com.zongce.app.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zongce.app.update.UpdateCheckResult
import com.zongce.app.update.UpdateChecker
import com.zongce.app.update.UpdateInfo
import com.zongce.app.update.UpdateThrottle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    /**
     * 是否有一次"静默自动检查"正在后台进行。
     *
     * 手动检查会立刻把状态置成 Checking，靠状态就能判出来；而自动检查是静默的、
     * 不改状态，所以必须单独记一个标志位，用来挡住"自动检查还没跑完又被触发一次" ——
     * 例如旋转屏幕导致 Activity 重建、App 重新组合，LaunchedEffect(Unit) 会再触发一次。
     * 全部在主线程读写（调用方都在主线程），无需额外同步。
     */
    private var silentCheckRunning = false

    /**
     * 每次"手动检查"开始就自增。
     *
     * 静默检查开始时会记下它的值，完成时若发现已经变了，说明这段静默结果已被用户的手动
     * 操作取代（用户查过了、甚至已经把弹窗点掉了），必须作废——否则晚到的静默结果会把
     * 用户刚关掉的弹窗又弹回来（像是"关不掉"），或覆盖掉手动检查正在显示的结果。
     * 全靠主线程读写（调用方都在主线程），无需额外同步。
     */
    private var manualCheckEpoch = 0

    /**
     * 手动"检查更新"：照常显示 Checking、照常提示"已经是最新版本"、失败照常报错。
     * 不受"每天最多自动检查一次"的限制 —— 用户主动点，就该立刻查。
     */
    fun checkForUpdate() {
        // 只挡"正在显示的手动检查/下载"。若此刻只是后台静默自动检查在跑，让用户这次
        // 手动操作照常进行 —— 不要因为一次静默检查把用户主动点的按钮吞掉。
        if (_updateState.value is UpdateUiState.Checking ||
            _updateState.value is UpdateUiState.Downloading
        ) return
        // 记一笔"用户发起了手动检查"：让在飞的静默检查知道自己的结果已作废。
        manualCheckEpoch++
        _updateState.value = UpdateUiState.Checking
        viewModelScope.launch {
            runUpdateCheck()
                .onSuccess { result ->
                    _updateState.value = when (result) {
                        is UpdateCheckResult.UpToDate -> UpdateUiState.UpToDate(result.currentVersion)
                        is UpdateCheckResult.Available -> UpdateUiState.Available(result.info)
                    }
                    // 手动查完也把"上次检查日期"刷新到今天：这样用户刚手动查完、
                    // 紧接着重开 App，就不会又自动查一遍。
                    UpdateThrottle.markChecked(getApplication(), today())
                }
                .onFailure { error ->
                    _updateState.value = UpdateUiState.Error(
                        error.message ?: "请检查网络后重试"
                    )
                }
        }
    }

    /**
     * 启动时的静默自动检查。三条语义：
     * 1. 静默：不置 Checking（否则启动瞬间会弹出一个转圈的"正在检查更新"弹窗）；
     *    无新版、检查失败、断网、更新源没配置 —— 一律保持 Idle，不弹任何东西，
     *    失败只留一条日志；只有确实有新版本才置 Available，复用现有 UpdateDialog 弹出。
     * 2. 节流：每天最多一次，今天已经自动查过就直接跳过、不发网络请求。
     * 3. 并发保护：若正在检查/下载，直接跳过，不要打断用户操作。
     */
    fun autoCheckForUpdate() {
        if (isUpdateBusy()) return

        val app = getApplication<Application>()
        val today = today()
        if (!UpdateThrottle.shouldAutoCheck(UpdateThrottle.lastCheckDate(app), today)) return

        val epochAtStart = manualCheckEpoch
        silentCheckRunning = true
        viewModelScope.launch {
            try {
                runUpdateCheck()
                    .onSuccess { result ->
                        // 静默结果若已被用户的手动操作取代（期间用户点过手动检查，甚至已把弹窗
                        // 点掉），整体作废 —— 连"今天已查"的日期也不记。
                        // 为什么作废时也不记：被取代意味着用户手动检查过 —— 手动成功时手动路径
                        // 自己已经记过日期，不会白查；手动失败时不记日期，下次启动才会重查，
                        // 而"补一次"正是我们想要的（那个已发现新版的静默结果已被丢弃）。
                        if (manualCheckEpoch != epochAtStart) return@onSuccess
                        // 检查确实成功完成了，记下今天的日期，避免同一天反复查。
                        UpdateThrottle.markChecked(app, today)
                        when (result) {
                            // 静默：已经是最新，什么都不做，保持 Idle。
                            is UpdateCheckResult.UpToDate -> Unit
                            is UpdateCheckResult.Available ->
                                // 双保险：epoch 未变时状态理论上必为 Idle（静默不改状态，下载中会被
                                // isUpdateBusy 拦掉），这里再确认一次，防止将来改动流程时误覆盖用户
                                // 正在看的弹窗。
                                if (_updateState.value is UpdateUiState.Idle) {
                                    _updateState.value = UpdateUiState.Available(result.info)
                                }
                        }
                    }
                    .onFailure { error ->
                        // 静默失败：自动检查不该打扰用户，只打一条日志。
                        Log.i(TAG, "自动检查更新失败，已忽略：${error.message}")
                    }
            } finally {
                silentCheckRunning = false
            }
        }
    }

    /**
     * 真正发起一次更新检查。手动与自动两条路径共用，避免两处逻辑各自漂移。
     * 只做网络请求、不碰 UI 状态 —— 状态的落法由各自的调用方决定。
     */
    private suspend fun runUpdateCheck(): Result<UpdateCheckResult> =
        runCatching { UpdateChecker.check() }

    /** 是否有检查/下载正在进行中（含后台静默自动检查），自动检查靠它做并发保护。 */
    private fun isUpdateBusy(): Boolean =
        silentCheckRunning ||
            _updateState.value is UpdateUiState.Checking ||
            _updateState.value is UpdateUiState.Downloading

    /** 今天的本地日期，ISO-8601（yyyy-MM-dd）。minSdk 26 可直接用 java.time，无需 desugaring。 */
    private fun today(): String = LocalDate.now().toString()

    fun downloadUpdate(info: UpdateInfo) {
        _updateState.value = UpdateUiState.Downloading(info, -1)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                UpdateChecker.download(getApplication(), info) { progress ->
                    _updateState.value = UpdateUiState.Downloading(info, progress)
                }
            }.onSuccess { file ->
                _updateState.value = UpdateUiState.Ready(info, file)
            }.onFailure { error ->
                _updateState.value = UpdateUiState.Error(
                    error.message ?: "下载失败，请稍后重试"
                )
            }
        }
    }

    fun resetUpdate() {
        _updateState.value = UpdateUiState.Idle
    }

    private companion object {
        /** 项目目前没有引入日志库，用系统的 Log 打静默自动检查的失败信息即可。 */
        const val TAG = "UpdateChecker"
    }
}
