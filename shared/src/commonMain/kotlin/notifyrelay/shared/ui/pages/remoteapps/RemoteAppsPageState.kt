package notifyrelay.shared.ui.pages.remoteapps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * `RemoteAppsPage` 的页面级状态 holder：上下文菜单目标、显示器列表与选中显示器。
 */
@Stable
class RemoteAppsPageState(
    val showMenuForApp: MutableState<Any?>,
    val selectedDisplayId: MutableIntState,
    val displays: SnapshotStateList<DisplayInfo>,
)

/**
 * 创建并记住 [RemoteAppsPageState]，同时按 [isLocalMode] 注册显示器监听副作用。
 */
@Composable
fun rememberRemoteAppsPageState(
    isLocalMode: Boolean,
    displayPort: DisplayPort?,
): RemoteAppsPageState {
    val showMenuForApp = remember { mutableStateOf<Any?>(null) }
    val selectedDisplayId = remember { mutableIntStateOf(0) }
    val displays = remember { mutableStateListOf<DisplayInfo>() }

    DisposableEffect(isLocalMode, displayPort) {
        if (isLocalMode && displayPort != null) {
            displayPort.start()
            onDispose { displayPort.stop() }
        } else {
            onDispose {}
        }
    }

    LaunchedEffect(isLocalMode, displayPort) {
        if (!isLocalMode || displayPort == null) return@LaunchedEffect
        displayPort.displays.collect { list ->
            displays.clear()
            displays.addAll(list)
            val validIds = list.map { it.id }
            if (selectedDisplayId.intValue !in validIds) {
                selectedDisplayId.intValue = list.find { it.id == 0 }?.id ?: list.firstOrNull()?.id ?: 0
            }
        }
    }

    return remember { RemoteAppsPageState(showMenuForApp, selectedDisplayId, displays) }
}
