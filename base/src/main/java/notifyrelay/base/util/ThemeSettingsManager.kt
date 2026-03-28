package notifyrelay.base.util

import android.content.Context
import androidx.core.content.edit

object ThemeSettingsManager {
    private const val PREFS_NAME = "theme_settings"
    private const val KEY_THEME_BASE_INDEX = "theme_base_index"
    private const val KEY_MONET_ENABLED = "monet_enabled"

    const val THEME_FOLLOW_SYSTEM = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

    private val listeners = mutableMapOf<Context, MutableSet<ThemeChangeListener>>()

    fun interface ThemeChangeListener {
        fun onThemeChanged(themeBaseIndex: Int, monetEnabled: Boolean)
    }

    fun getThemeBaseIndex(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_THEME_BASE_INDEX, THEME_FOLLOW_SYSTEM)
    }

    fun setThemeBaseIndex(context: Context, index: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putInt(KEY_THEME_BASE_INDEX, index.coerceIn(0, 2)) }
        notifyListeners(context)
    }

    fun isMonetEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_MONET_ENABLED, false)
    }

    fun setMonetEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_MONET_ENABLED, enabled) }
        notifyListeners(context)
    }

    fun addThemeChangeListener(context: Context, listener: ThemeChangeListener) {
        val contextListeners = listeners.getOrPut(context.applicationContext) { mutableSetOf() }
        contextListeners.add(listener)
    }

    fun removeThemeChangeListener(context: Context, listener: ThemeChangeListener) {
        listeners[context.applicationContext]?.remove(listener)
    }

    private fun notifyListeners(context: Context) {
        val appContext = context.applicationContext
        val themeBaseIndex = getThemeBaseIndex(appContext)
        val monetEnabled = isMonetEnabled(appContext)
        listeners[appContext]?.forEach { listener ->
            listener.onThemeChanged(themeBaseIndex, monetEnabled)
        }
    }
}
