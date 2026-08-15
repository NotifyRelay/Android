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
import com.xzyht.notifyrelay.ui.pages.UIAppearance
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GuideBasicSettingsPage(
    selectedThemeIndex: Int,
    onThemeSelected: (Int) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
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
            // 外观设置直接复用设置页的 UIAppearance，避免重复实现；
            // 此处外层已有滚动容器，关闭 UIAppearance 内部滚动避免嵌套滚动崩溃
            Card(modifier = Modifier.fillMaxWidth()) {
                UIAppearance(
                    themeBaseIndex = selectedThemeIndex,
                    onThemeSelected = onThemeSelected,
                    scrollable = false
                )
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
