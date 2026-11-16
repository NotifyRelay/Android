package notifyrelay.domain.feature.notification.superisland.replica

/**
 * 超级岛复刻展示的时限契约（唯一来源）。
 *
 * 全部时长都以「来源刷新周期」为参照：来源在 [SOURCE_REFRESH_INTERVAL_MS] 内会重新投递一次，
 * 展示侧的撤回时限必须**长于**它，否则每一轮刷新前都先撤一次，表现为胶囊周期性消失再出现；
 * 关闭侧的屏蔽窗口则必须**覆盖**刷新周期，否则关闭后下一轮刷新把已关闭的条目又拉起来。
 */
object ReplicaTiming {
    /**
     * 来源刷新周期：通知监听服务定时拉取活跃通知的间隔。
     *
     * 媒体通知不参与已处理缓存去重，只要来源通知仍在通知栏，每轮都会重新走一次展示。
     */
    const val SOURCE_REFRESH_INTERVAL_MS = 30_000L

    /** 媒体展示的兜底撤回时限：须大于刷新周期，稳态下每次刷新先重置计时器而不撤回。 */
    const val MEDIA_VIEW_TTL_MS = 45_000L

    /** 关闭后的屏蔽窗口：须覆盖刷新周期。 */
    const val CLOSED_SOURCE_TTL_MS = SOURCE_REFRESH_INTERVAL_MS + 15_000L

    /** 用户主动隐藏后的静默窗口。 */
    const val BLOCKED_INSTANCE_TTL_MS = 15_000L
}
