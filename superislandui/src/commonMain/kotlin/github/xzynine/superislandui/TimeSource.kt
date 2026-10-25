package github.xzynine.superislandui

// 当前墙钟时间的跨源集入口：commonMain 不可直接使用 System.currentTimeMillis()。
// 供计时器格式化与媒体会话时间戳等需要当前时间的公共代码调用。
internal expect fun currentTimeMillis(): Long
