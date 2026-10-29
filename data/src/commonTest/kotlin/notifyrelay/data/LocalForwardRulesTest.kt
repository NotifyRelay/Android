package notifyrelay.data

import notifyrelay.data.filter.LocalForwardRules
import notifyrelay.domain.feature.notification.filter.FilterEntry
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 本机通知转发判定规则测试。
 *
 * 断言对齐 Windows `BackendLocalFilter.ShouldForward` 与 `FilterEntry.Matches`，
 * 防止规则漂移。
 */
class LocalForwardRulesTest {
    private fun forward(
        packageName: String = "com.example.app",
        appName: String = "示例应用",
        title: String = "标题",
        text: String = "内容",
        entries: Set<FilterEntry> = emptySet(),
        filterSelf: Boolean = true,
        filterNoTitleOrText: Boolean = true,
    ): Boolean =
        LocalForwardRules.shouldForward(
            filterSelf = filterSelf,
            filterNoTitleOrText = filterNoTitleOrText,
            packageName = packageName,
            appName = appName,
            title = title,
            text = text,
            enabledEntries = entries,
        )

    @Test
    fun ordinaryNotificationIsForwarded() {
        assertTrue(forward())
    }

    @Test
    fun selfNotificationIsFilteredByPackageOrAppName() {
        // 包名含 notifyrelay
        assertFalse(forward(packageName = "com.xzyht.notifyrelay"))
        // 应用名含 notifyrelay
        assertFalse(forward(appName = "NotifyRelay"))
        // 关闭自身过滤后放行
        assertTrue(forward(appName = "NotifyRelay", filterSelf = false))
    }

    @Test
    fun emptyTitleAndTextIsFiltered() {
        assertFalse(forward(title = "", text = ""))
        // 仅其一为空不算空通知
        assertTrue(forward(title = "", text = "内容"))
        assertTrue(forward(title = "标题", text = ""))
    }

    @Test
    fun keywordEntryMatchesTitleOrTextIgnoringCase() {
        assertFalse(forward(entries = setOf(FilterEntry("标题", ""))))
        assertFalse(forward(entries = setOf(FilterEntry("内容", ""))))
        assertTrue(forward(entries = setOf(FilterEntry("无关", ""))))
    }

    @Test
    fun packageEntryMatchesPackageName() {
        assertFalse(forward(entries = setOf(FilterEntry("", "com.example"))))
        assertTrue(forward(entries = setOf(FilterEntry("", "com.other"))))
    }

    @Test
    fun entryWithBothFieldsRequiresBothToMatch() {
        // 关键词命中但包名不命中 → 不整条命中
        assertTrue(forward(entries = setOf(FilterEntry("标题", "com.other"))))
        // 两者都命中 → 命中
        assertFalse(forward(entries = setOf(FilterEntry("标题", "com.example"))))
    }

    @Test
    fun emptyEntryNeverMatches() {
        assertTrue(forward(entries = setOf(FilterEntry("", ""))))
    }
}
