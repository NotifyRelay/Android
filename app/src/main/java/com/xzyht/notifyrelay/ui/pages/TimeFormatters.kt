package com.xzyht.notifyrelay.ui.pages

import notifyrelay.base.util.TimestampFormatter

/** 格式化时间戳为 `yyyy-MM-dd HH:mm:ss`；唯一实现见 [TimestampFormatter]。 */
internal fun TimestampFormatter.format(timestamp: Long): String = TimestampFormatter.format(timestamp)
