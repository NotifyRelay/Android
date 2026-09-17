package com.xzyht.notifyrelay.feature.notification.filter

/**
 * 标准化标题：去除应用名称前缀，如"(微博)" -> ""
 * 被去重缓存、占位队列、待撤回监控等多处共用，故抽为包级 internal 函数。
 */
internal fun normalizeTitle(title: String): String {
    val prefixPattern = Regex("^\\([^)]+\\)")
    return title.replace(prefixPattern, "").trim()
}
