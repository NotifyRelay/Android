package com.xzyht.notifyrelay.ui.pages

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 历史记录时间戳格式化（线程安全）。统一到固定 Locale.US，避免部分 ROM 的本地数字渲染差异。 */
internal val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)

/** 格式化时间戳为 `yyyy-MM-dd HH:mm:ss`；异常时回退为时间戳字面量。 */
internal fun formatTimestamp(timestamp: Long): String =
    try {
        dateTimeFormatter.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
    } catch (_: Exception) {
        timestamp.toString()
    }
