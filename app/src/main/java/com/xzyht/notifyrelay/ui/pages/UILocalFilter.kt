package com.xzyht.notifyrelay.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xzyht.notifyrelay.feature.notification.backend.BackendLocalFilter
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * UI本机通知过滤设置
 * 提供本机通知过滤的UI界面
 */
@Composable
fun UILocalFilter(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // 状态管理
    var filterSelf by remember { mutableStateOf(BackendLocalFilter.filterSelf) }
    var filterOngoing by remember { mutableStateOf(BackendLocalFilter.filterOngoing) }
    var filterNoTitleOrText by remember { mutableStateOf(BackendLocalFilter.filterNoTitleOrText) }
    var filterImportanceNone by remember { mutableStateOf(BackendLocalFilter.filterImportanceNone) }

    // 过滤条目相关状态（统一管理关键词+包名）
    var allEntries by remember { mutableStateOf(BackendLocalFilter.getFilterEntries(context).toList()) }
    var enabledEntries by remember { mutableStateOf(BackendLocalFilter.getEnabledFilterEntries(context)) }

    val builtinKeywords = remember { BackendLocalFilter.getBuiltinKeywords() }
    val builtinPackages = remember { BackendLocalFilter.getDefaultPackageFilters() }
    val builtinDefaultEntries = remember(allEntries, builtinKeywords, builtinPackages) {
        allEntries.filter { entry -> (entry.keyword.isNotBlank() && builtinKeywords.contains(entry.keyword)) || (entry.packageName.isNotBlank() && builtinPackages.contains(entry.packageName)) }
    }

    // 分组：内置关键词条目、内置包名条目、自定义条目
    val builtinKeywordEntries = remember(allEntries, builtinKeywords) { allEntries.filter { it.packageName.isBlank() && builtinKeywords.contains(it.keyword) } }
    val builtinPackageEntries = remember(allEntries, builtinPackages) { allEntries.filter { it.keyword.isBlank() && builtinPackages.contains(it.packageName) } }
    val customEntries = remember(allEntries, builtinKeywordEntries, builtinPackageEntries) { allEntries.filter { !builtinKeywordEntries.contains(it) && !builtinPackageEntries.contains(it) } }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 本机通知过滤设置（标题 + 过滤开关）卡片
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.surfaceContainerHighest
                )
            ) {
                Text(
                    text = "本机通知过滤设置",
                    style = MiuixTheme.textStyles.title2,
                    color = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )

                // 过滤本应用通知
                CheckboxPreference(
                    title = "过滤本应用通知",
                    checked = filterSelf,
                    onCheckedChange = {
                        filterSelf = it
                        BackendLocalFilter.filterSelf = it
                    },
                    checkboxLocation = CheckboxLocation.End
                )

                // 过滤持久化通知
                CheckboxPreference(
                    title = "过滤持久化通知",
                    checked = filterOngoing,
                    onCheckedChange = {
                        filterOngoing = it
                        BackendLocalFilter.filterOngoing = it
                    },
                    checkboxLocation = CheckboxLocation.End
                )

                // 过滤无标题或无内容
                CheckboxPreference(
                    title = "过滤无标题或无内容",
                    checked = filterNoTitleOrText,
                    onCheckedChange = {
                        filterNoTitleOrText = it
                        BackendLocalFilter.filterNoTitleOrText = it
                    },
                    checkboxLocation = CheckboxLocation.End
                )

                // 过滤优先级为无的通知
                CheckboxPreference(
                    title = "过滤优先级为无的通知",
                    checked = filterImportanceNone,
                    onCheckedChange = {
                        filterImportanceNone = it
                        BackendLocalFilter.filterImportanceNone = it
                    },
                    checkboxLocation = CheckboxLocation.End
                )
            }
        }

        // 统一过滤条目设置：标题行
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "过滤条目(文本 + 应用包名)",
                    style = MiuixTheme.textStyles.title3,
                    color = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 黑名单配置区（添加 + 默认黑名单/手动黑名单抽屉），复用公共组件
        item {
            FilterListSection(
                defaultEntries = builtinDefaultEntries.map { FilterEntryItem(it.keyword, it.packageName) },
                manualEntries = customEntries.map { FilterEntryItem(it.keyword, it.packageName) },
                entryEnabled = { enabledEntries.contains(BackendLocalFilter.FilterEntry(it.keyword, it.packageName)) },
                onEntryEnabledChange = { item, enabled ->
                    BackendLocalFilter.setFilterEntryEnabled(context, BackendLocalFilter.FilterEntry(item.keyword, item.packageName), enabled)
                    enabledEntries = BackendLocalFilter.getEnabledFilterEntries(context)
                },
                onAddEntry = { keyword, pkg ->
                    BackendLocalFilter.addFilterEntry(context, keyword, pkg)
                    allEntries = BackendLocalFilter.getFilterEntries(context).toList()
                    enabledEntries = BackendLocalFilter.getEnabledFilterEntries(context)
                },
                onRemoveEntry = { item ->
                    BackendLocalFilter.removeFilterEntry(context, item.keyword, item.packageName)
                    allEntries = BackendLocalFilter.getFilterEntries(context).toList()
                    enabledEntries = BackendLocalFilter.getEnabledFilterEntries(context)
                }
            )
        }
    }
}