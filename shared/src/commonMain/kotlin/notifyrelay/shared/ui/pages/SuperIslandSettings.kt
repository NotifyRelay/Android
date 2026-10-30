package notifyrelay.shared.ui.pages

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import notifyrelay.base.SuperIslandStorageKeys
import notifyrelay.base.config.FilterConfigDefaults
import notifyrelay.data.config.SuperIslandConfigPolicy
import notifyrelay.data.database.entity.SuperIslandMirrorFilterEntity
import notifyrelay.data.repository.MirrorFilterRepository
import notifyrelay.domain.feature.notification.superisland.SpecInjectionMode
import notifyrelay.domain.platform.FormFactorPortRegistry
import notifyrelay.shared.ui.common.DebugFlags
import notifyrelay.shared.ui.dialog.AppPickerDialog
import notifyrelay.shared.ui.port.LocalPreferencePort
import notifyrelay.shared.ui.port.LocalToastPort
import notifyrelay.shared.ui.viewmodel.SuperIslandSettingsViewModel
import notifyrelay.shared.ui.viewmodel.superIslandSettingsViewModel
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 镜像应用的默认过滤包名。 */
private val DEFAULT_MIRROR_PACKAGES: List<String>
    get() = FilterConfigDefaults.defaultMirrorPackages

/** 注入方式选项。 */
val specInjectionOptions =
    listOf(
        "仅超级岛规范信息注入" to SpecInjectionMode.SUPER_ISLAND,
        "仅Live Updates规范信息注入" to SpecInjectionMode.LIVE_UPDATES,
    )

/**
 * 超级岛设置页。
 *
 * 配置读写经公共策略与仓储；平台专属的「测试超级岛分支」入口经 [onOpenTestBranch] 注入，
 * 传 null 时不显示该项。
 */
@Composable
fun UISuperIslandSettings(
    viewModel: SuperIslandSettingsViewModel = superIslandSettingsViewModel(),
    onOpenTestBranch: (() -> Unit)? = null,
) {
    val preferencePort = LocalPreferencePort.current
    val toastPort = LocalToastPort.current
    val formFactor = FormFactorPortRegistry.current

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    var enabled by remember { mutableStateOf(preferencePort?.getBoolean(SuperIslandStorageKeys.ENABLED, true) ?: true) }
    val showSuperIsland = viewModel.showSuperIsland
    val floatingWindowEnabled = viewModel.floatingWindowEnabled
    val notificationListEnabled = viewModel.notificationListEnabled
    val specInjectionMode = viewModel.specInjectionMode

    val hasFloatingWindowSetting = preferencePort?.getString(SuperIslandStorageKeys.FLOATING_WINDOW, "")?.isNotEmpty() ?: false
    val defaultFloatingWindowEnabled = SuperIslandConfigPolicy.defaultFloatingWindowEnabled(formFactor)

    var mirrorFilterEnabled by remember {
        mutableStateOf(preferencePort?.getBoolean(SuperIslandStorageKeys.MIRROR_FILTER_ENABLED, true) ?: true)
    }
    var customPackages by remember { mutableStateOf<List<SuperIslandMirrorFilterEntity>>(emptyList()) }
    var showAppPicker by remember { mutableStateOf(false) }

    val floatingWindowSummary =
        run {
            val baseSummary = "用于a16的livedata通知api被系统支持前使用浮窗展示超级岛"
            if (DebugFlags.DEBUG_UI_ENABLED.value) {
                val currentOsVersion = formFactor?.osVersion() ?: "未知"
                "$baseSummary (当前版本: $currentOsVersion, 有用户设置: ${if (hasFloatingWindowSetting) "是" else "否"}, 预设默认值: ${if (defaultFloatingWindowEnabled) "开启" else "关闭"})"
            } else {
                baseSummary
            }
        }

    suspend fun loadCustomPackages() {
        customPackages = MirrorFilterRepository.getAll()
    }

    LaunchedEffect(Unit) {
        loadCustomPackages()
    }

    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles

    Surface(color = colorScheme.background) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .verticalScroll(remember { ScrollState(0) }),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SwitchPreference(
                title = "超级岛读取",
                summary = "控制是否尝试从本机通知中读取小米超级岛数据并转发",
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    preferencePort?.putBoolean(SuperIslandStorageKeys.ENABLED, it)
                },
            )

            SwitchPreference(
                title = "超级岛显示",
                summary = "控制是否显示来自远端的超级岛",
                checked = showSuperIsland,
                onCheckedChange = { viewModel.updateShowSuperIsland(it) },
            )

            SwitchPreference(
                title = "浮窗兼容",
                summary = floatingWindowSummary,
                checked = floatingWindowEnabled,
                onCheckedChange = { viewModel.updateFloatingWindowEnabled(it) },
            )

            SwitchPreference(
                title = "超级岛列表模式",
                summary = "平板默认开启，开启后系统外显仅保留一条超级岛，点击切换",
                checked = notificationListEnabled,
                onCheckedChange = { viewModel.updateNotificationListEnabled(it) },
            )

            WindowDropdownPreference(
                title = "规范信息注入方式",
                summary = "控制通知中注入的规范信息类型",
                items = specInjectionOptions.map { it.first },
                selectedIndex = specInjectionOptions.indexOfFirst { it.second == specInjectionMode },
                onSelectedIndexChange = { index ->
                    if (index in specInjectionOptions.indices) {
                        val mode = specInjectionOptions[index].second
                        viewModel.updateSpecInjectionMode(mode)
                        if (mode == SpecInjectionMode.SUPER_ISLAND) {
                            toastPort.long("请通过lsp模块关闭超级岛白名单否则大概率无法展示")
                        }
                    }
                },
            )

            if (onOpenTestBranch != null) {
                ArrowPreference(
                    title = "测试超级岛分支",
                    onClick = onOpenTestBranch,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            SwitchPreference(
                title = "镜像应用过滤",
                summary = "过滤双向对称应用的远程超级岛复刻（仅本地也存在同包名超级岛时触发）",
                checked = mirrorFilterEnabled,
                onCheckedChange = {
                    mirrorFilterEnabled = it
                    SuperIslandConfigPolicy.setMirrorFilterEnabled(it)
                },
            )

            Text(
                "过滤包名列表",
                style = textStyles.main,
                color = colorScheme.onSurfaceSecondary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            val installedPackages = remember { viewModel.installedPackages() }

            DEFAULT_MIRROR_PACKAGES.forEach { packageName ->
                val isInstalled = installedPackages.contains(packageName)
                var iconBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
                val isEnabled = customPackages.find { it.packageName == packageName }?.enabled ?: true

                LaunchedEffect(packageName) {
                    if (iconBitmap == null) {
                        viewModel.loadIcon(packageName) { iconBitmap = it }
                    }
                }

                SwitchPreference(
                    title = packageName,
                    titleColor =
                        BasicComponentDefaults.titleColor(
                            color = if (isInstalled) colorScheme.primary else colorScheme.onSurface,
                        ),
                    checked = isEnabled,
                    onCheckedChange = { value ->
                        viewModel.viewModelScope.launch {
                            MirrorFilterRepository.upsert(packageName, value)
                            loadCustomPackages()
                        }
                    },
                    startAction = {
                        iconBitmap?.let {
                            Image(
                                bitmap = it,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp).padding(end = 8.dp),
                            )
                        }
                    },
                )
            }

            customPackages.filter { it.packageName !in DEFAULT_MIRROR_PACKAGES }.forEach { entity ->
                key(entity.packageName) {
                    val packageName = entity.packageName
                    val isInstalled = installedPackages.contains(packageName)
                    var iconBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
                    var packageEnabled by remember(entity.packageName, entity.enabled) { mutableStateOf(entity.enabled) }

                    LaunchedEffect(packageName) {
                        if (iconBitmap == null) {
                            viewModel.loadIcon(packageName) { iconBitmap = it }
                        }
                    }

                    SwitchPreference(
                        title = packageName,
                        titleColor =
                            BasicComponentDefaults.titleColor(
                                color = if (isInstalled) colorScheme.primary else colorScheme.onSurface,
                            ),
                        checked = packageEnabled,
                        onCheckedChange = { value ->
                            packageEnabled = value
                            viewModel.viewModelScope.launch {
                                MirrorFilterRepository.setEnabled(packageName, value)
                            }
                        },
                        startAction = {
                            iconBitmap?.let {
                                Image(
                                    bitmap = it,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp).padding(end = 8.dp),
                                )
                            }
                        },
                        endActions = {
                            IconButton(onClick = {
                                viewModel.viewModelScope.launch {
                                    MirrorFilterRepository.delete(packageName)
                                    loadCustomPackages()
                                }
                            }) {
                                Icon(
                                    imageVector = MiuixIcons.Delete,
                                    contentDescription = "删除",
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { showAppPicker = true }) {
                    Text("选择应用")
                }
            }
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            visible = true,
            onDismiss = { showAppPicker = false },
            onAppSelected = { packageName ->
                showAppPicker = false
                viewModel.viewModelScope.launch {
                    MirrorFilterRepository.upsert(packageName, true)
                    loadCustomPackages()
                }
            },
            title = "选择镜像过滤应用",
        )
    }
}
