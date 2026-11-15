package com.xzyht.notifyrelay.feature.settings

import android.content.Context
import notifyrelay.data.Storage
import notifyrelay.shared.ui.screen.KEY_DEVELOPER_MODE_ENABLED
import notifyrelay.shared.ui.screen.SettingsPort

/** 以 [Storage] 实现 [SettingsPort]。 */
class AndroidSettingsPort(
    private val context: Context,
) : SettingsPort {
    override fun isDeveloperModeEnabled(): Boolean = Storage.getBoolean(KEY_DEVELOPER_MODE_ENABLED, false)

    override fun enableDeveloperMode() {
        Storage.putBoolean(KEY_DEVELOPER_MODE_ENABLED, true)
    }
}
