package notifyrelay.desktop.overlay

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 叠加层所有「到点自动变化」的唯一时钟。

 * 展开态的超时收起、条目的超时移除都由此推进：状态持有在进程级对象里，
 * 由单个循环按墙钟巡检，组合树只读取结果。
 *
 * 不能挂在组合树的 remember / LaunchedEffect 上：录制尺寸变化会整体重建
 * OverlaySceneRecorder，组合状态随之归零且作用域被取消，计时就永久停摆。
 * 也不能各业务各起一个循环，否则同一份墙钟被多处以不同周期采样。
 */
internal object OverlayClock {
    /** 巡检粒度：决定到点变化的响应延迟上限。 */
    private const val TICK_MS = 100L

    /**
     * 巡检专用的作用域。
     *
     * 独立于任何组合树：计时必须在窗口与录制器重建后继续运行。
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 每个巡检周期被调用的观察者；遍历期间允许增删，故复制一份。 */
    private val listeners = mutableListOf<(Long) -> Unit>()

    @Volatile
    private var started = false

    /**
     * 注册一个随每个周期推进的观察者。
     *
     * 由业务状态对象在初始化时登记，不重复登记同一实例。
     */
    @Synchronized
    fun register(listener: (Long) -> Unit) {
        if (listeners.contains(listener)) return
        listeners += listener
        ensureStarted()
    }

    /** 当前时刻；与巡检使用同一来源，业务侧不必各自取时。 */
    fun now(): Long = System.currentTimeMillis()

    /** 启动巡检循环；重复调用无操作。 */
    private fun ensureStarted() {
        if (started) return
        started = true
        scope.launch {
            while (true) {
                delay(TICK_MS)
                val now = now()
                // 复制后再遍历：观察者可能在回调里注册新的观察者
                val snapshot = synchronized(this@OverlayClock) { listeners.toList() }
                snapshot.forEach { runCatching { it(now) } }
            }
        }
    }
}
