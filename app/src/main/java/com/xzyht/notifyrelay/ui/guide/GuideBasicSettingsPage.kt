package com.xzyht.notifyrelay.ui.guide

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import notifyrelay.base.util.ThemeSettingsManager
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GuideBasicSettingsPage(
    selectedThemeIndex: Int,
    onThemeSelected: (Int) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val themeOptions = listOf(
        Triple(ThemeSettingsManager.THEME_FOLLOW_SYSTEM, "跟随系统", "随系统深色模式自动切换"),
        Triple(ThemeSettingsManager.THEME_LIGHT, "浅色模式", "始终使用浅色外观"),
        Triple(ThemeSettingsManager.THEME_DARK, "深色模式", "始终使用深色外观")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        GuidePageHeader(
            stepLabel = "5 / 6",
            title = "基础设置",
            subtitle = "按你的偏好设置外观，进入应用后仍可随时修改"
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            GuideSectionLabel(
                title = "外观",
                description = "选择应用使用的明暗模式"
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    themeOptions.forEach { (index, title, description) ->
                        GuideThemeOption(
                            title = title,
                            description = description,
                            selected = selectedThemeIndex == index,
                            onClick = { onThemeSelected(index) }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        GuidePageFooter(
            hint = null,
            nextText = "下一步",
            nextEnabled = true,
            onBack = onBack,
            onNext = onNext
        )
    }
}
