package github.xzynine.superislandui.common

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 当前超级岛内容在本地被接收的时刻（epoch 毫秒）。
 *
 * 计时器字段取发送端时钟，接收端需要知道「这份内容是什么时候到的」，才能把基准换算到
 * 本地时钟。卡片所在容器登记接收时刻，计时展示读取它；未登记时返回 null，
 * 计时器退回按 [github.xzynine.superislandui.model.components.TimerInfo.timerSystemCurrent]
 * 处理，即视作与本地同一时钟。
 */
val LocalSuperIslandReceivedAtMs =
    staticCompositionLocalOf<Long?> { null }
