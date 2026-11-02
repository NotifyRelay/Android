package notifyrelay.base.util

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * 当前墙钟时间（毫秒）。
 *
 * 以标准库 [Clock] 取值，全平台共用这一份实现。
 * 命名避开 `kotlin.time.TimeSource`，以免与标准库符号混淆。
 */
@OptIn(ExperimentalTime::class)
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
