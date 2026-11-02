package com.xzyht.notifyrelay.feature.settings

import android.content.Context
import notifyrelay.data.Storage
import notifyrelay.shared.ui.screen.SettingsPort

/** 以 [Storage] 实现 [SettingsPort]。 */
class AndroidSettingsPort(
    private val context: Context,
) : SettingsPort {
    override fun isDeveloperModeEnabled(): Boolean = Storage.getBoolean("developer_mode_enabled", false)
}
