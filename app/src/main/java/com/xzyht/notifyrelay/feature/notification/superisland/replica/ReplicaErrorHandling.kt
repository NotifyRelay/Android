package com.xzyht.notifyrelay.feature.notification.superisland.replica

import notifyrelay.base.util.Logger

/**
 * 超级岛复刻包的统一异常吞没包装。
 *
 * 本包内原先有 5 份逐字相同的私有实现，只是 [Logger] 的 TAG 不同；
 * 现集中到此处，[tag] 作显式入参，保证日志输出**逐字不变**。
 *
 * 不下沉 `:base`：「超级岛: 」前缀是本包的业务语义，下沉会让 `:base` 承载不存在的领域概念。
 *
 * [block] 保持原实现的 `crossinline` 约束：全部调用点均以 `return@` 标签退出，
 * 无裸 `return` 非局部返回，故签名与原私有实现完全等价。
 *
 * @param tag 日志 TAG，由调用方传入自身 object 的私有常量，保证日志逐字不变。
 * @param actionName 动作名，用于拼接失败日志。
 * @param block 被保护的业务块，异常时吞没并记日志。
 */
internal inline fun runReplicaCatching(
    tag: String,
    actionName: String,
    crossinline block: () -> Unit,
) {
    try {
        block()
    } catch (e: Exception) {
        Logger.w(tag, "超级岛: $actionName 失败: ${e.message}")
    }
}

/**
 * [runReplicaCatching] 的挂起版本，语义与异常处理完全一致。
 *
 * @param tag 日志 TAG，由调用方传入自身 object 的私有常量，保证日志逐字不变。
 * @param actionName 动作名，用于拼接失败日志。
 * @param block 被保护的挂起业务块，异常时吞没并记日志。
 */
internal suspend inline fun runReplicaCatchingSuspend(
    tag: String,
    actionName: String,
    crossinline block: suspend () -> Unit,
) {
    try {
        block()
    } catch (e: Exception) {
        Logger.w(tag, "超级岛: $actionName 失败: ${e.message}")
    }
}
