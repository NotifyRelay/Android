package com.xzyht.notifyrelay.ui.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import notifyrelay.base.util.ThemeSettingsManager
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference

private val THEME_BASE_OPTIONS =
    listOf(
        "跟随系统" to 0,
        "浅色模式" to 1,
        "深色模式" to 2,
    )

/**
 * 外观设置
 * 提供外观模式选择等界面
 */
@Composable
fun UIAppearance() {
    val context = LocalContext.current
    var themeBaseIndex by remember { mutableIntStateOf(ThemeSettingsManager.getThemeBaseIndex(context)) }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp),
    ) {
        WindowDropdownPreference(
            title = "外观模式",
            summary = THEME_BASE_OPTIONS.find { it.second == themeBaseIndex }?.first ?: "跟随系统",
            items = THEME_BASE_OPTIONS.map { it.first },
            selectedIndex = themeBaseIndex.coerceIn(0, THEME_BASE_OPTIONS.lastIndex),
            onSelectedIndexChange = { newIndex ->
                themeBaseIndex = newIndex
                ThemeSettingsManager.setThemeBaseIndex(context, newIndex)
            },
        )
    }
}
