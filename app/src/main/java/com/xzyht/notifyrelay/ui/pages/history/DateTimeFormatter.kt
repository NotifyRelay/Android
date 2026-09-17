package com.xzyht.notifyrelay.ui.pages.history

import java.time.format.DateTimeFormatter
import java.util.Locale

// 日期格式化工具（线程安全）
internal val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)
