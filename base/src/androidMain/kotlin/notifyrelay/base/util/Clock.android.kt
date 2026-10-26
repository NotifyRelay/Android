package notifyrelay.base.util

// Android 侧实现：与改动前的 System.currentTimeMillis() 完全一致。
actual fun nowMillis(): Long = System.currentTimeMillis()
