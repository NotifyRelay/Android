package notifyrelay.shared.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import notifyrelay.domain.feature.notification.filter.FilterEntry
import notifyrelay.domain.feature.notification.filter.LocalFilterPortRegistry
import notifyrelay.shared.ui.viewmodel.LocalFilterViewModel
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
    modifier: Modifier = Modifier,
    viewModel: LocalFilterViewModel = localFilterViewModel(),
) {
    LaunchedEffect(Unit) {
        viewModel.load()
    }

    val builtinKeywords = viewModel.builtinKeywords
    val builtinPackages = viewModel.builtinPackages
    val allEntries = viewModel.allEntries

    val builtinDefaultEntries =
        remember(allEntries, builtinKeywords, builtinPackages) {
            allEntries.filter { entry ->
                (entry.keyword.isNotBlank() && builtinKeywords.contains(entry.keyword)) ||
                    (entry.packageName.isNotBlank() && builtinPackages.contains(entry.packageName))
            }
        }

    val builtinKeywordEntries =
        remember(allEntries, builtinKeywords) {
            allEntries.filter { it.packageName.isBlank() && builtinKeywords.contains(it.keyword) }
        }
    val builtinPackageEntries =
        remember(allEntries, builtinPackages) {
            allEntries.filter { it.keyword.isBlank() && builtinPackages.contains(it.packageName) }
        }
    val customEntries =
        remember(allEntries, builtinKeywordEntries, builtinPackageEntries) {
            allEntries.filter { !builtinKeywordEntries.contains(it) && !builtinPackageEntries.contains(it) }
        }

    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.surfaceContainerHighest,
                    ),
            ) {
                Text(
                    text = "本机通知过滤设置",
                    style = MiuixTheme.textStyles.title2,
                    color = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )

                CheckboxPreference(
                    title = "过滤本应用通知",
                    checked = viewModel.filterSelf,
                    onCheckedChange = { viewModel.updateFilterSelf(it) },
                    checkboxLocation = CheckboxLocation.End,
                )

                CheckboxPreference(
                    title = "过滤持久化通知",
                    checked = viewModel.filterOngoing,
                    onCheckedChange = { viewModel.updateFilterOngoing(it) },
                    checkboxLocation = CheckboxLocation.End,
                )

                CheckboxPreference(
                    title = "过滤无标题或无内容",
                    checked = viewModel.filterNoTitleOrText,
                    onCheckedChange = { viewModel.updateFilterNoTitleOrText(it) },
                    checkboxLocation = CheckboxLocation.End,
                )

                CheckboxPreference(
                    title = "过滤优先级为无的通知",
                    checked = viewModel.filterImportanceNone,
                    onCheckedChange = { viewModel.updateFilterImportanceNone(it) },
                    checkboxLocation = CheckboxLocation.End,
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "过滤条目(文本 + 应用包名)",
                    style = MiuixTheme.textStyles.title3,
                    color = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            FilterListSection(
                defaultEntries = builtinDefaultEntries.map { FilterEntryItem(it.keyword, it.packageName) },
                manualEntries = customEntries.map { FilterEntryItem(it.keyword, it.packageName) },
                entryEnabled = { item -> viewModel.enabledEntries.contains(FilterEntry(item.keyword, item.packageName)) },
                onEntryEnabledChange = { item, enabled ->
                    viewModel.setEntryEnabled(FilterEntry(item.keyword, item.packageName), enabled)
                },
                onAddEntry = { keyword, pkg ->
                    viewModel.addEntry(keyword, pkg)
                },
                onRemoveEntry = { item ->
                    viewModel.removeEntry(item.keyword, item.packageName)
                },
            )
        }
    }
}

/** 以已注册的 [LocalFilterPort] 构造 [LocalFilterViewModel]。 */
@Composable
private fun localFilterViewModel(): LocalFilterViewModel {
    val port =
        LocalFilterPortRegistry.current
            ?: error("LocalFilterPort 未注册")
    return viewModel(
        factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: kotlin.reflect.KClass<T>,
                    extras: androidx.lifecycle.viewmodel.CreationExtras,
                ): T = LocalFilterViewModel(port) as T
            },
    )
}
