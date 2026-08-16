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
 * 外观设置内容
 *
 * 默认自管理主题状态并持久化；传入 [themeBaseIndex] / [onThemeSelected] 时进入受控模式，
 * 由调用方（如引导页）持有状态并处理变更，便于即时换肤。
 * 默认自带垂直滚动；当嵌入到外层滚动容器（如引导页基础设置）时需传 [scrollable] = false，
 * 避免嵌套滚动导致无限高度约束崩溃。
 */
@Composable
fun UIAppearance(
    themeBaseIndex: Int? = null,
    onThemeSelected: ((Int) -> Unit)? = null,
    scrollable: Boolean = true,
) {
    val context = LocalContext.current
    var localThemeBaseIndex by remember {
        mutableIntStateOf(themeBaseIndex ?: ThemeSettingsManager.getThemeBaseIndex(context))
    }
    // 外部受控时以外部传入值为准
    val selectedIndex = themeBaseIndex ?: localThemeBaseIndex

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (scrollable) {
                        Modifier.verticalScroll(rememberScrollState())
                    } else {
                        Modifier
                    },
                ).padding(top = 12.dp),
    ) {
        WindowDropdownPreference(
            title = "外观模式",
            summary = THEME_BASE_OPTIONS.find { it.second == selectedIndex }?.first ?: "跟随系统",
            items = THEME_BASE_OPTIONS.map { it.first },
            selectedIndex = selectedIndex.coerceIn(0, THEME_BASE_OPTIONS.lastIndex),
            onSelectedIndexChange = { newIndex ->
                if (onThemeSelected != null) {
                    onThemeSelected(newIndex)
                } else {
                    localThemeBaseIndex = newIndex
                    ThemeSettingsManager.setThemeBaseIndex(context, newIndex)
                }
            },
        )
    }
}
