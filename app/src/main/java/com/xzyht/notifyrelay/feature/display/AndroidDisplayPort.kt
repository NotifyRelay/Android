package com.xzyht.notifyrelay.feature.display

import android.content.Context
import android.hardware.display.DisplayManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import notifyrelay.base.log.Logger
import notifyrelay.shared.ui.pages.remoteapps.DisplayInfo
import notifyrelay.shared.ui.pages.remoteapps.DisplayPort

/** 以 [DisplayManager] 实现 [DisplayPort]。 */
class AndroidDisplayPort(
    private val context: Context,
) : DisplayPort {
    private val _displays = MutableStateFlow<List<DisplayInfo>>(emptyList())
    override val displays: StateFlow<List<DisplayInfo>> = _displays.asStateFlow()

    private var manager: DisplayManager? = null
    private var listener: DisplayManager.DisplayListener? = null

    override fun start() {
        if (manager != null) return
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val displayListener =
            object : DisplayManager.DisplayListener {
                override fun onDisplayAdded(displayId: Int) = refresh()

                override fun onDisplayRemoved(displayId: Int) = refresh()

                override fun onDisplayChanged(displayId: Int) = refresh()
            }
        displayManager.registerDisplayListener(displayListener, null)
        manager = displayManager
        listener = displayListener
        refresh()
    }

    override fun stop() {
        val displayManager = manager ?: return
        listener?.let { displayManager.unregisterDisplayListener(it) }
        manager = null
        listener = null
    }

    private fun refresh() {
        val displayManager = manager ?: return
        val allDisplays = displayManager.displays
        Logger.d(TAG, "所有显示器: " + allDisplays.map { "id=" + it.displayId + ", name=" + it.name + ", flags=" + it.flags })
        val list =
            allDisplays
                .map { display ->
                    DisplayInfo(
                        id = display.displayId,
                        name =
                            display.name.ifEmpty {
                                if (display.displayId == 0) "内置显示器" else "显示器 " + display.displayId
                            },
                        isBuiltIn = display.displayId == 0,
                    )
                }.sortedBy { it.id }
        Logger.d(TAG, "显示器列表: " + list)
        _displays.value = list
    }

    private companion object {
        const val TAG = "RemoteAppsPage"
    }
}
