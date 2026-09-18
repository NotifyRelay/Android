package com.xzyht.notifyrelay.ui.pages.remoteapps

import android.content.Context
import android.hardware.display.DisplayManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * `RemoteAppsPage` 的页面级状态 holder：搜索词、上下文菜单目标、显示器列表与选中显示器。
 *
 * 持有 Compose 的 [MutableState]，使页面可以继续用 `by` 委托读写这些状态。
 */
@Stable
internal class RemoteAppsPageState(
    val searchQuery: MutableState<String>,
    val showMenuForApp: MutableState<Any?>,
    val selectedDisplayId: MutableIntState,
    val displays: SnapshotStateList<DisplayInfo>,
)

/**
 * 创建并记住 [RemoteAppsPageState]，同时按 [isLocalMode] 注册显示器监听副作用。
 */
@Composable
internal fun rememberRemoteAppsPageState(
    isLocalMode: Boolean,
    context: Context,
): RemoteAppsPageState {
    val searchQuery = remember { mutableStateOf("") }
    val showMenuForApp = remember { mutableStateOf<Any?>(null) }
    val selectedDisplayId = remember { mutableIntStateOf(0) }
    val displays = remember { mutableStateListOf<DisplayInfo>() }

    DisposableEffect(isLocalMode) {
        if (isLocalMode) {
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

            fun updateDisplays() {
                val allDisplays = displayManager.displays
                android.util.Log.d("RemoteAppsPage", "所有显示器: ${allDisplays.map { "id=${it.displayId}, name=${it.name}, flags=${it.flags}" }}")

                val displayList =
                    allDisplays
                        .map { display ->
                            DisplayInfo(
                                id = display.displayId,
                                name =
                                    display.name.ifEmpty {
                                        if (display.displayId == 0) "内置显示器" else "显示器 ${display.displayId}"
                                    },
                                isBuiltIn = display.displayId == 0,
                            )
                        }.sortedBy { it.id }

                android.util.Log.d("RemoteAppsPage", "显示器列表: $displayList")
                displays.clear()
                displays.addAll(displayList)

                val validDisplayIds = displayList.map { it.id }
                if (selectedDisplayId.intValue !in validDisplayIds) {
                    selectedDisplayId.intValue =
                        displayList.find { it.id == 0 }?.id
                            ?: displayList.firstOrNull()?.id
                            ?: 0
                    android.util.Log.d("RemoteAppsPage", "selectedDisplayId 不在有效列表中，重置为: ${selectedDisplayId.intValue}")
                }
            }
            updateDisplays()
            val displayListener =
                object : DisplayManager.DisplayListener {
                    override fun onDisplayAdded(displayId: Int) = updateDisplays()

                    override fun onDisplayRemoved(displayId: Int) = updateDisplays()

                    override fun onDisplayChanged(displayId: Int) = updateDisplays()
                }
            displayManager.registerDisplayListener(displayListener, null)
            onDispose {
                displayManager.unregisterDisplayListener(displayListener)
            }
        } else {
            onDispose {}
        }
    }

    return remember { RemoteAppsPageState(searchQuery, showMenuForApp, selectedDisplayId, displays) }
}
