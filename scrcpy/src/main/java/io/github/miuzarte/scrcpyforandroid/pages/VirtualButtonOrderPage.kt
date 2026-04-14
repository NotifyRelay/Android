package io.github.miuzarte.scrcpyforandroid.pages

import android.app.Application
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.scaffolds.AppPageLazyColumn
import io.github.miuzarte.scrcpyforandroid.widgets.ReorderableList
import io.github.miuzarte.scrcpyforandroid.widgets.VirtualButtonAction
import io.github.miuzarte.scrcpyforandroid.widgets.VirtualButtonActions
import notifyrelay.base.util.ThemeSettingsManager
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TopAppBar

@Composable
fun ScrcpyVirtualButtonOrderPage(
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val viewModel: ScrcpyUiViewModel = viewModel(factory = ScrcpyUiViewModel.Factory(app))
    val snackHostState = remember { SnackbarHostState() }
    val scrollBehavior = MiuixScrollBehavior(canScroll = { true })
    var themeBaseIndex by remember { mutableIntStateOf(ThemeSettingsManager.getThemeBaseIndex(context)) }

    DisposableEffect(context) {
        val listener = ThemeSettingsManager.ThemeChangeListener { newBaseIndex ->
            themeBaseIndex = newBaseIndex
        }
        ThemeSettingsManager.addThemeChangeListener(context, listener)
        onDispose {
            ThemeSettingsManager.removeThemeChangeListener(context, listener)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "虚拟按钮排序",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackHostState) },
    ) { pagePadding ->
        ProvideScrcpyUiEnvironment(
            viewModel = viewModel,
            contentPadding = pagePadding,
            scrollBehavior = scrollBehavior,
            snackHostState = snackHostState,
            themeBaseIndex = themeBaseIndex,
        ) {
            val contentPadding = LocalScrcpyPagePadding.current
            val layoutString = viewModel.virtualButtonsLayout
            var buttonItems by remember(layoutString) {
                mutableStateOf(VirtualButtonActions.parseStoredLayout(layoutString))
            }

            fun emitChanges() {
                viewModel.virtualButtonsLayout =
                    VirtualButtonActions.encodeStoredLayout(buttonItems)
            }

            AppPageLazyColumn(
                contentPadding = contentPadding,
                scrollBehavior = scrollBehavior,
            ) {
                item {
                    ReorderableList(
                        itemsProvider = {
                            buttonItems.map { item ->
                                val action = item.action
                                ReorderableList.Item(
                                    id = action.id,
                                    icon = action.icon,
                                    title = if (action.keycode == null) action.title else "${action.title} (${action.keycode})",
                                    subtitle = if (item.showOutside) "显示在外部" else "显示在更多菜单内",
                                    checked = item.showOutside,
                                    checkboxEnabled = action != VirtualButtonAction.MORE,
                                )
                            }
                        },
                        orientation = ReorderableList.Orientation.Column,
                        onSettle = { fromIndex, toIndex ->
                            buttonItems = buttonItems.toMutableList().apply {
                                add(toIndex, removeAt(fromIndex))
                            }
                            emitChanges()
                        },
                        showCheckbox = true,
                        onCheckboxChange = { id, checked ->
                            buttonItems = buttonItems.map { item ->
                                if (item.action.id == id) {
                                    item.copy(showOutside = checked)
                                } else {
                                    item
                                }
                            }
                            emitChanges()
                        },
                    )()
                }

                item { Spacer(Modifier.height(UiSpacing.BottomContent)) }
            }
        }
    }
}
