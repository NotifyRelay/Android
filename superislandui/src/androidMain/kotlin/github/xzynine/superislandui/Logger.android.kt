package github.xzynine.superislandui

import notifyrelay.base.util.Logger as BaseLogger

// Android 侧实现：委托既有 notifyrelay.base.util.Logger，
// 保持现有日志级别过滤、Timber 分发与开关行为不变。

internal actual fun platformLogV(
    tag: String,
    message: String,
    throwable: Throwable?,
) {
    if (throwable == null) {
        BaseLogger.v(tag, message)
    } else {
        BaseLogger.v(tag, message, throwable)
    }
}

internal actual fun platformLogD(
    tag: String,
    message: String,
    throwable: Throwable?,
) {
    if (throwable == null) {
        BaseLogger.d(tag, message)
    } else {
        BaseLogger.d(tag, message, throwable)
    }
}

internal actual fun platformLogI(
    tag: String,
    message: String,
    throwable: Throwable?,
) {
    if (throwable == null) {
        BaseLogger.i(tag, message)
    } else {
        BaseLogger.i(tag, message, throwable)
    }
}

internal actual fun platformLogW(
    tag: String,
    message: String,
    throwable: Throwable?,
) {
    if (throwable == null) {
        BaseLogger.w(tag, message)
    } else {
        BaseLogger.w(tag, message, throwable)
    }
}

internal actual fun platformLogE(
    tag: String,
    message: String,
    throwable: Throwable?,
) {
    if (throwable == null) {
        BaseLogger.e(tag, message)
    } else {
        BaseLogger.e(tag, message, throwable)
    }
}
