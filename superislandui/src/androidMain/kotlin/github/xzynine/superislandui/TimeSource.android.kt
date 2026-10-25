package github.xzynine.superislandui

// Android 侧实现：与拆分前的 System.currentTimeMillis() 完全一致。
internal actual fun currentTimeMillis(): Long = System.currentTimeMillis()
