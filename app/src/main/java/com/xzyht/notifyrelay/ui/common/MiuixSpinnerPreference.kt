package com.xzyht.notifyrelay.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference

/**
 * miuix WindowDropdownPreference 的兼容封装层。
 *
 * miuix 0.9.1 起 Spinner 系列（SpinnerEntry 等）已废弃并迁移至 Dropdown 系列，
 * 后续升级 miuix 时只需修改本文件内部实现，无需改动业务调用方。
 */
@Composable
fun MiuixSpinnerPreference(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    summary: String? = null,
    onSelectedIndexChange: ((Int) -> Unit)? = null,
) {
    WindowDropdownPreference(
        title = title,
        items = items,
        selectedIndex = selectedIndex,
        modifier = modifier,
        summary = summary,
        onSelectedIndexChange = onSelectedIndexChange,
    )
}
