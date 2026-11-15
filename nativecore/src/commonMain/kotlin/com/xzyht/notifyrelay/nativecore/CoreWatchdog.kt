package com.xzyht.notifyrelay.nativecore

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import notifyrelay.base.log.Logger as AppLogger

/**
 * 核心调用看门狗。
 *
 * 核心侧互斥锁可能因下层阻塞长时间不释放，调用方会在 native 方法里无限等待。
 * 看门狗只读原子量并调用 [NativeCore.requestShutdown]，不参与核心锁，
 * 因此不会成为新的死锁参与者。
 *
 * 触发条件：存在未返回的核心调用，且自该调用开始已超过 [BLOCKED_TIMEOUT_MS]。
 * 触发后请求关停核心，后续调用由平台侧重建实例恢复。
 */
object CoreWatchdog {
    private const val TAG = "CoreWatchdog"

    /** 判定核心调用已阻塞的时间上限（毫秒）。 */
    private const val BLOCKED_TIMEOUT_MS = 10_000L

    /** 监控轮询间隔（毫秒）。 */
    private const val POLL_INTERVAL_MS = 1_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val inFlight = atomic(0)
    private val blockedSince = atomic<TimeMark?>(null)
    private val fired = atomic(false)

    private val startLock = SynchronizedObject()
    private var monitorStarted = false

    /**
     * 包裹一次核心调用，用于统计在途数量与阻塞时长。
     *
     * 不捕获异常、不改变返回值，调用方的既有错误处理不受影响。
     */
    fun <T> track(block: () -> T): T {
        enter()
        try {
            return block()
        } finally {
            exit()
        }
    }

    /** 无返回值版本，语义同 [track]。 */
    fun trackUnit(block: () -> Unit) {
        track { block() }
    }

    private fun enter() {
        ensureMonitor()
        val count = inFlight.incrementAndGet()
        if (count == 1) {
            blockedSince.value = TimeSource.Monotonic.markNow()
        }
    }

    private fun exit() {
        val count = inFlight.decrementAndGet()
        if (count <= 0) {
            blockedSince.value = null
        }
    }

    /** 核心实例重建后复位统计，使新实例获得完整超时窗口。 */
    fun reset() {
        inFlight.value = 0
        blockedSince.value = null
        fired.value = false
    }

    private fun ensureMonitor() {
        if (monitorStarted) return
        synchronized(startLock) {
            if (monitorStarted) return
            monitorStarted = true
            scope.launch {
                while (true) {
                    delay(POLL_INTERVAL_MS)
                    check()
                }
            }
        }
    }

    private fun check() {
        if (fired.value) return
        if (inFlight.value <= 0) return
        val since = blockedSince.value ?: return
        if (since.elapsedNow().inWholeMilliseconds < BLOCKED_TIMEOUT_MS) return

        fired.value = true
        AppLogger.e(TAG, "核心调用阻塞超过 ${BLOCKED_TIMEOUT_MS}ms，请求关停核心")
        NativeCore.requestShutdown()
    }
}
