package notifyrelay.shared.ui.port

import androidx.compose.runtime.staticCompositionLocalOf

/** 键值偏好设置端口。 */
interface PreferencePort {
    fun getBoolean(
        key: String,
        default: Boolean = false,
    ): Boolean

    fun putBoolean(
        key: String,
        value: Boolean,
    )

    fun getInt(
        key: String,
        default: Int = 0,
    ): Int

    fun putInt(
        key: String,
        value: Int,
    )

    fun getString(
        key: String,
        default: String = "",
    ): String

    fun putString(
        key: String,
        value: String,
    )
}

/** 由宿主提供实现。 */
val LocalPreferencePort = staticCompositionLocalOf<PreferencePort?> { null }
