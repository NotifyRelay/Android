package com.xzyht.notifyrelay.ui.pages

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xzyht.notifyrelay.BuildConfig
import com.xzyht.notifyrelay.ui.dialog.UpdateDialog
import github.xzynine.checkupdata.CheckUpdateManager
import github.xzynine.checkupdata.model.ReleaseInfo
import github.xzynine.checkupdata.model.UpdateResult
import github.xzynine.checkupdata.version.VersionRule
import kotlinx.coroutines.launch
import notifyrelay.data.StorageManager
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Date

@Composable
fun UIAbout(onDeveloperModeTriggered: () -> Unit = {}) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    
    var clickCount by remember { mutableStateOf(0) }
    var lastClickTime by remember { mutableStateOf(0L) }
    var isDeveloperModeEnabled by remember {
        mutableStateOf(StorageManager.getBoolean(context, "developer_mode_enabled", false))
    }
    
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var latestReleaseInfo by remember { mutableStateOf<ReleaseInfo?>(null) }
    var hasUpdate by remember { mutableStateOf(false) }
    var includePrerelease by remember {
        mutableStateOf(StorageManager.getBoolean(context, "check_update_include_prerelease", false))
    }
    
    val checkUpdateManager = remember { CheckUpdateManager(context) }

    MiuixTheme {
        val colorScheme = MiuixTheme.colorScheme
        val textStyles = MiuixTheme.textStyles

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            Text(
                text = "Notify Relay",
                style = textStyles.title1,
                color = colorScheme.primary,
                modifier = Modifier
                    .padding(top = 24.dp, bottom = 16.dp)
                    .align(Alignment.CenterHorizontally)
            )

            SuperArrow(
                title = "版本信息",
                summary = "主版本: ${BuildConfig.VERSION_NAME}\n内部版本: ${BuildConfig.VERSION_CODE}",
                onClick = {
                    val currentTime = Date().time
                    if (currentTime - lastClickTime < 3000) {
                        clickCount++
                    } else {
                        clickCount = 1
                    }
                    lastClickTime = currentTime
                    
                    if (clickCount >= 3 && clickCount < 5) {
                        Toast.makeText(context, "再点击 ${5 - clickCount} 次进入开发者模式", Toast.LENGTH_SHORT).show()
                    } else if (clickCount >= 5) {
                        Toast.makeText(context, "开发者模式已激活", Toast.LENGTH_SHORT).show()
                        isDeveloperModeEnabled = true
                        StorageManager.putBoolean(context, "developer_mode_enabled", true)
                        clickCount = 0
                    }
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            SuperArrow(
                title = "检测更新",
                summary = if (isCheckingUpdate) "检查中..." else "点击检查是否有新版本",
                onClick = {
                    if (isCheckingUpdate) return@SuperArrow
                    
                    isCheckingUpdate = true
                    
                    coroutineScope.launch {
                        val rule = if (includePrerelease) VersionRule.LATEST else VersionRule.STABLE
                        val result = checkUpdateManager.checkUpdate(
                            owner = "NotifyRelay",
                            repo = "Android",
                            currentVersion = BuildConfig.VERSION_NAME,
                            rule = rule
                        )
                        
                        isCheckingUpdate = false
                        
                        when (result) {
                            is UpdateResult.HasUpdate -> {
                                hasUpdate = true
                                latestReleaseInfo = result.releaseInfo
                                showUpdateDialog = true
                            }
                            is UpdateResult.NoUpdate -> {
                                hasUpdate = false
                                latestReleaseInfo = result.releaseInfo
                                showUpdateDialog = true
                            }
                            is UpdateResult.Error -> {
                                Toast.makeText(context, "检查失败: ${result.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            SuperSwitch(
                title = "包含预发布版本",
                checked = includePrerelease,
                summary = "检测更新时包含预发布版本(极其不稳定)",
                onCheckedChange = {
                    includePrerelease = it
                    StorageManager.putBoolean(context, "check_update_include_prerelease", it)
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            if (isDeveloperModeEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                SuperArrow(
                    title = "开发者模式",
                    summary = "点击进入开发者模式设置",
                    onClick = {
                        onDeveloperModeTriggered()
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            Text(
                text = "© 2026 Notify Relay",
                style = textStyles.body2,
                color = colorScheme.onSurfaceSecondary,
                modifier = Modifier
                    .padding(24.dp)
                    .align(Alignment.CenterHorizontally)
            )
        }
        
        if (showUpdateDialog) {
            val dialogState = remember { mutableStateOf(true) }
            
            UpdateDialog(
                showDialog = dialogState,
                releaseInfo = latestReleaseInfo,
                currentVersion = BuildConfig.VERSION_NAME,
                hasUpdate = hasUpdate,
                onDownload = { info ->
                    coroutineScope.launch {
                        checkUpdateManager.downloadRelease(info, useProxy = true)
                    }
                },
                onDismiss = {
                    showUpdateDialog = false
                    latestReleaseInfo = null
                }
            )
        }
    }
}
