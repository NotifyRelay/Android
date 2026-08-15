package com.xzyht.notifyrelay.ui.guide

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xzyht.notifyrelay.ui.pages.UIAppearance
import com.xzyht.notifyrelay.ui.pages.UILocalFilter
import com.xzyht.notifyrelay.ui.pages.UIRemoteFilter
import com.xzyht.notifyrelay.ui.pages.UISuperIslandSettings
import io.github.miuzarte.scrcpyforandroid.pages.ScrcpyRootScreen
import io.github.miuzarte.scrcpyforandroid.pages.ScrcpyScreenHost
import io.github.miuzarte.scrcpyforandroid.pages.ScrcpyUiViewModel
import notifyrelay.base.util.Logger
import notifyrelay.data.StorageManager
import notifyrelay.data.config.ScrcpyPreferenceKeys
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.preference.ArrowPreference

/**
 * 引导页设置项通用容器。
 * 与 SettingsScreen 的子页保持一致，但完全使用引导页内部的“上一步 / 下一步”进行控制。
 */
@Composable
internal fun GuideSettingsPage(
    stepLabel: String,
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onNext: () -> Unit,
    nextText: String = "下一步",
    content: @Composable BoxScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        GuidePageHeader(
            stepLabel = stepLabel,
            title = title,
            subtitle = subtitle
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            content()
        }

        GuidePageFooter(
            hint = null,
            nextText = nextText,
            nextEnabled = true,
            onBack = onBack,
            onNext = onNext
        )
    }
}

/**
 * 基础设置总览页：参考 SettingsScreen 的设置项列表，
 * 每一项都进入引导页内部的对应设置页。
 */
@Composable
internal fun GuideSettingsOverviewPage(
    onOpenAppearance: () -> Unit,
    onOpenRemoteFilter: () -> Unit,
    onOpenLocalFilter: () -> Unit,
    onOpenSuperIsland: () -> Unit,
    onOpenScrcpy: () -> Unit,
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
            subtitle = "按你的偏好调整以下设置，进入应用后仍可随时修改"
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            GuideSectionLabel(
                title = "设置项",
                description = "选择需要调整的设置，也可以直接下一步继续",
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            ArrowPreference(
                title = "远程过滤",
                summary = "远程通知过滤与黑白名单",
                onClick = onOpenRemoteFilter,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 32.dp))
            ArrowPreference(
                title = "本地过滤",
                summary = "本机通知过滤设置",
                onClick = onOpenLocalFilter,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 32.dp))
            ArrowPreference(
                title = "超级岛",
                summary = "超级岛读取、显示与镜像过滤",
                onClick = onOpenSuperIsland,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 32.dp))
            ArrowPreference(
                title = "屏幕镜像",
                summary = "Scrcpy 屏幕镜像设置",
                onClick = onOpenScrcpy,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 32.dp))
            ArrowPreference(
                title = "外观",
                summary = "外观模式设置",
                onClick = onOpenAppearance,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        GuidePageFooter(
            hint = "可直接下一步跳过设置",
            nextText = "下一步",
            nextEnabled = true,
            onBack = onBack,
            onNext = onNext
        )
    }
}

@Composable
internal fun GuideAppearancePage(
    selectedThemeIndex: Int,
    onThemeSelected: (Int) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    GuideSettingsPage(
        stepLabel = "5 / 6",
        title = "外观",
        subtitle = "选择应用使用的明暗模式",
        onBack = onBack,
        onNext = onNext
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            UIAppearance(
                themeBaseIndex = selectedThemeIndex,
                onThemeSelected = onThemeSelected,
                scrollable = false
            )
        }
    }
}

@Composable
internal fun GuideRemoteFilterPage(
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    GuideSettingsPage(
        stepLabel = "5 / 6",
        title = "远程过滤",
        subtitle = "远程通知过滤与黑白名单",
        onBack = onBack,
        onNext = onNext
    ) {
        UIRemoteFilter(modifier = Modifier.fillMaxSize())
    }
}

@Composable
internal fun GuideLocalFilterPage(
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    GuideSettingsPage(
        stepLabel = "5 / 6",
        title = "本地过滤",
        subtitle = "本机通知过滤设置",
        onBack = onBack,
        onNext = onNext
    ) {
        UILocalFilter(modifier = Modifier.fillMaxSize())
    }
}

@Composable
internal fun GuideSuperIslandPage(
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    GuideSettingsPage(
        stepLabel = "5 / 6",
        title = "超级岛",
        subtitle = "超级岛读取、显示与镜像过滤",
        onBack = onBack,
        onNext = onNext
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            UISuperIslandSettings()
        }
    }
}

@Composable
internal fun GuideScrcpyPage(
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    val serverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            val uriString = uri.toString()
            StorageManager.putString(
                context,
                ScrcpyPreferenceKeys.CUSTOM_SERVER_URI,
                uriString,
                StorageManager.PrefsType.SCRCPY
            )
            val app = context.applicationContext as android.app.Application
            ScrcpyUiViewModel.getInstance(app).customServerUri = uriString
        }.onFailure { e ->
            Logger.e("GuideScrcpyPage", "scrcpy server URI 保存失败: uri=$uri", e)
        }
    }

    GuideSettingsPage(
        stepLabel = "5 / 6",
        title = "屏幕镜像",
        subtitle = "Scrcpy 屏幕镜像设置",
        onBack = onBack,
        onNext = onNext
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ScrcpyScreenHost(
                startScreen = ScrcpyRootScreen.Settings,
                onPickServer = { serverPicker.launch(arrayOf("application/java-archive", "application/octet-stream", "*/*")) },
                onExit = onBack
            )
        }
    }
}
