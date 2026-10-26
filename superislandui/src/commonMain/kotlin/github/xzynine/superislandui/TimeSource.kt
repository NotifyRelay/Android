package github.xzynine.superislandui

import notifyrelay.base.util.nowMillis

// 当前墙钟时间的模块内入口：实现位于 :shared，commonMain 不可直接使用 System.currentTimeMillis()。
internal fun currentTimeMillis(): Long = nowMillis()
