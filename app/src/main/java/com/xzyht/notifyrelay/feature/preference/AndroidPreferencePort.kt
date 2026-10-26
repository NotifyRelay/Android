package com.xzyht.notifyrelay.feature.preference

import notifyrelay.data.Storage
import notifyrelay.shared.ui.port.PreferencePort

/** 以 [Storage] 实现 [PreferencePort]。 */
object AndroidPreferencePort : PreferencePort {
    override fun getBoolean(
        key: String,
        default: Boolean,
    ): Boolean = Storage.getBoolean(key, default)

    override fun putBoolean(
        key: String,
        value: Boolean,
    ) {
        Storage.putBoolean(key, value)
    }

    override fun getInt(
        key: String,
        default: Int,
    ): Int = Storage.getInt(key, default)

    override fun putInt(
        key: String,
        value: Int,
    ) {
        Storage.putInt(key, value)
    }

    override fun getString(
        key: String,
        default: String,
    ): String = Storage.getString(key, default)

    override fun putString(
        key: String,
        value: String,
    ) {
        Storage.putString(key, value)
    }
}
