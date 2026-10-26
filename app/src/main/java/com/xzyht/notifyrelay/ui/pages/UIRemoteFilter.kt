package com.xzyht.notifyrelay.ui.pages

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xzyht.notifyrelay.ui.dialog.AppPickerDialog
import com.xzyht.notifyrelay.ui.viewmodel.RemoteFilterViewModel
import notifyrelay.shared.ui.viewmodel.AppIconViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 将 DeviceForwardFragment 中原有的远程过滤内联实现移动到这里：
 * 该组件负责读取/写入 RemoteFilterConfig 并提供完整的远程过滤 UI
 */
@Composable
fun UIRemoteFilter(
    modifier: Modifier = Modifier,
    viewModel: RemoteFilterViewModel = viewModel(),
    iconViewModel: AppIconViewModel = viewModel(),
) {
    val context = LocalContext.current

    val filterMode = viewModel.filterMode
    val enableDedup = viewModel.enableDedup
    val enablePackageGroupMapping = viewModel.enablePackageGroupMapping
    val allGroups = viewModel.allGroups
    val allGroupEnabled = viewModel.allGroupEnabled
    val enableLockScreenOnly = viewModel.enableLockScreenOnly
    val filterItems = viewModel.filterItems
    val enabledKeys = viewModel.enabledKeys

    LaunchedEffect(Unit) {
        viewModel.load(context)
    }

    var showAppPickerForGroup by remember { mutableStateOf<Pair<Boolean, Int>>(false to -1) }

    // UI: 使用滚动列承载全部设置项
    val scrollState = rememberScrollState()

    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(top = 12.dp),
    ) {
        // 延迟去重
        SwitchPreference(
            title = "智能去重",
            checked = enableDedup,
            summary = "智能去重，避免重复通知",
            onCheckedChange = {
                viewModel.setEnableDedup(context, it)
            },
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 32.dp))

        // 锁屏通知过滤
        SwitchPreference(
            title = "仅复刻锁屏通知到通知栏",
            checked = enableLockScreenOnly,
            summary = "仅复刻锁屏状态的通知到通知栏",
            onCheckedChange = {
                viewModel.setEnableLockScreenOnly(context, it)
            },
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 32.dp))
        // 过滤模式选择与黑白名单编辑
        val modes = listOf("none" to "无", "black" to "黑名单", "white" to "白名单", "peer" to "对等")
        val modeLabels = modes.map { it.second }
        val selectedModeIndex = modes.indexOfFirst { it.first == filterMode }.coerceIn(0, modes.lastIndex)
        HorizontalDivider(modifier = Modifier.padding(horizontal = 32.dp))
        WindowDropdownPreference(
            title = "过滤模式",
            summary = "选择过滤模式",
            items = modeLabels,
            selectedIndex = selectedModeIndex,
            onSelectedIndexChange = { idx ->
                val safeIdx = idx.coerceIn(0, modes.lastIndex)
                val (value, _) = modes[safeIdx]
                viewModel.setFilterMode(context, value)
            },
        )

        if (filterMode == "black" || filterMode == "white") {
            // 黑白名单配置区（添加 + 手动黑名单抽屉 + 条目禁用），复用本机过滤的公共组件
            FilterListSection(
                manualEntries = filterItems.map { FilterEntryItem(it.second ?: "", it.first) },
                entryEnabled = { item -> enabledKeys.contains("${item.packageName}|${item.keyword}") },
                onEntryEnabledChange = { item, enabled ->
                    viewModel.setEntryEnabled(context, item.packageName, item.keyword, enabled)
                },
                onAddEntry = { keyword, pkg ->
                    viewModel.addEntry(context, pkg, keyword)
                },
                onRemoveEntry = { item ->
                    viewModel.removeEntry(context, item.packageName, item.keyword)
                },
            )
        }
        // 包名等价功能总开关
        SwitchPreference(
            title = "启用包名等价映射",
            checked = enablePackageGroupMapping,
            summary = "启用包名等价映射功能",
            onCheckedChange = {
                viewModel.setEnablePackageGroupMapping(context, it)
            },
            modifier = Modifier.padding(top = 16.dp),
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 32.dp))

        // 包名组配置
        Column(modifier = Modifier.fillMaxWidth()) {
            allGroups.forEachIndexed { idx, group ->
                val groupName = if (idx < viewModel.defaultGroupSize) "默认组${idx + 1}" else "自定义组${idx + 1 - viewModel.defaultGroupSize}"

                Column(modifier = Modifier.fillMaxWidth()) {
                    // 将CheckboxPreference和操作按钮放在同一行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // CheckboxPreference占据大部分空间
                        CheckboxPreference(
                            title = groupName,
                            checked = allGroupEnabled[idx],
                            onCheckedChange = { v ->
                                val newEnabled = allGroupEnabled.toMutableList().apply { set(idx, v) }
                                viewModel.setGroupEnabled(context, newEnabled)
                            },
                            enabled = enablePackageGroupMapping,
                            modifier = Modifier.weight(1f),
                        )

                        // 右侧操作按钮组
                        Row(
                            modifier = Modifier.padding(start = 4.dp, end = 4.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (idx >= viewModel.defaultGroupSize) {
                                Button(
                                    onClick = { showAppPickerForGroup = true to idx },
                                    modifier = Modifier.defaultMinSize(minWidth = 28.dp, minHeight = 28.dp),
                                    enabled = enablePackageGroupMapping,
                                ) {
                                    Text("+")
                                }
                                Button(
                                    onClick = {
                                        val newGroups = allGroups.toMutableList().apply { removeAt(idx) }
                                        val newEnabled = allGroupEnabled.toMutableList().apply { removeAt(idx) }
                                        viewModel.setCustomGroups(context, newGroups, newEnabled)
                                    },
                                    modifier = Modifier.defaultMinSize(minWidth = 28.dp, minHeight = 28.dp).padding(start = 2.dp),
                                    enabled = enablePackageGroupMapping,
                                ) {
                                    Text("×")
                                }
                            }
                        }
                    }

                    // 包名自动换行显示
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(start = 60.dp, top = 0.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        // 监听已安装应用仓库的状态，确保数据已加载
                        val installedPkgs by viewModel.installedPkgs.collectAsState()
                        val installedPkgSet = installedPkgs.map { it.packageName }.toSet()

                        // 确保应用列表已加载
                        LaunchedEffect(Unit) {
                            if (!viewModel.isDataLoaded()) {
                                viewModel.loadApps(context)
                            }
                        }

                        group.forEach { pkg ->
                            val isInstalled = installedPkgSet.contains(pkg)
                            // 使用mutableStateOf保存图标状态，这样更新时会触发UI重新渲染
                            var iconBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

                            // 异步加载缺失的图标，并在加载完成后更新状态
                            LaunchedEffect(pkg) {
                                if (iconBitmap == null) {
                                    iconViewModel.loadIcon(pkg) { icon ->
                                        iconBitmap = icon
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 6.dp)) {
                                iconBitmap?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                Text(pkg, style = textStyles.body2, color = if (isInstalled) colorScheme.primary else colorScheme.onSurface, modifier = Modifier.padding(start = 2.dp))
                            }
                        }
                    }

                    // 在每组之间添加分割线，最后一组除外
                    if (idx < allGroups.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        // 添加新组按钮
        ArrowPreference(
            title = "添加新组",
            onClick = {
                val newGroups = allGroups.toMutableList().apply { add(mutableListOf()) }
                val newEnabled = allGroupEnabled.toMutableList().apply { add(true) }
                viewModel.setCustomGroups(context, newGroups, newEnabled)
            },
            modifier = Modifier.padding(vertical = 2.dp),
            enabled = enablePackageGroupMapping,
        )

        // 在添加新组按钮下方添加分割线
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        // 应用选择弹窗（封装组件调用）
        if (showAppPickerForGroup.first) {
            val groupIdx = showAppPickerForGroup.second
            AppPickerDialog(
                visible = true,
                onDismiss = { showAppPickerForGroup = false to -1 },
                onAppSelected = { pkg: String ->
                    val newGroups =
                        allGroups.toMutableList().apply {
                            if (groupIdx in indices && !this[groupIdx].contains(pkg)) {
                                this[groupIdx] = (this[groupIdx] + pkg).toMutableList()
                            }
                        }
                    // 更新后端状态
                    viewModel.setCustomGroups(context, newGroups, allGroupEnabled)
                    showAppPickerForGroup = false to -1
                },
                title = "选择应用",
            )
        }
    }
}
