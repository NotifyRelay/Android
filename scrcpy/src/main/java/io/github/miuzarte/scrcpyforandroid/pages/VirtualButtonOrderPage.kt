package io.github.miuzarte.scrcpyforandroid.pages

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.miuzarte.scrcpyforandroid.scaffolds.AppPageLazyColumn
import io.github.miuzarte.scrcpyforandroid.widgets.ReorderableList
import io.github.miuzarte.scrcpyforandroid.widgets.VirtualButtonAction
import io.github.miuzarte.scrcpyforandroid.widgets.VirtualButtonActions
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.extra.SuperSwitch

@Composable
internal fun VirtualButtonOrderPage(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    layoutString: String,
    onLayoutChange: (String) -> Unit,
    showPreviewText: Boolean,
    onShowPreviewTextChange: (Boolean) -> Unit,
) {
    var buttonItems by remember(layoutString) {
        mutableStateOf(VirtualButtonActions.parseStoredLayout(layoutString))
    }

    fun emitChanges() {
        onLayoutChange(VirtualButtonActions.encodeStoredLayout(buttonItems))
    }

    AppPageLazyColumn(
        contentPadding = contentPadding,
        scrollBehavior = scrollBehavior,
    ) {
        // 按钮显示文本开关
        item {
            Card {
                SuperSwitch(
                    title = "按钮显示文本",
                    summary = "超过3个建议关闭，只对预览卡下方的虚拟按钮生效",
                    checked = showPreviewText,
                    onCheckedChange = onShowPreviewTextChange,
                )
            }
        }

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
    }
}
