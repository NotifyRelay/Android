package notifyrelay.shared.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 应用导航容器：背景、返回栈与系统返回手势的一致处理。
 *
 * 两端导航图的差异只有外层容器（Android 直接铺满，桌面要扣掉 Scaffold 的 padding），
 * 因此把容器修饰符留给调用方，其余骨架在此统一。
 *
 * 返回只在这一层判定：栈深大于 1 才 pop，否则交给系统处理（桌面关窗、Android 退栈到空）。
 *
 * @param navigator 当前导航器。
 * @param modifier 外层 [Box] 的修饰符；调用方在此叠加自身的布局约束。
 * @param content 各路由的 `entry<T> { ... }` 注册。
 */
@Composable
fun AppNavDisplay(
    navigator: Navigator,
    modifier: Modifier = Modifier,
    content: NavEntryBuilder.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().background(MiuixTheme.colorScheme.background)) {
        NavDisplay(
            backStack = navigator.backStack,
            onBack = {
                if (navigator.backStackSize() > 1) {
                    navigator.pop()
                }
            },
            content = content,
        )
    }
}
