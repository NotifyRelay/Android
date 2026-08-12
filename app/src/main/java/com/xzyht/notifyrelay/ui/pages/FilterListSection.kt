package com.xzyht.notifyrelay.ui.pages

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xzyht.notifyrelay.servers.appslist.AppRepository
import com.xzyht.notifyrelay.ui.dialog.AppPickerDialog
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/**
 * 通用黑名单条目（keyword 可空，packageName 可空）
 */
data class FilterEntryItem(
    val keyword: String,
    val packageName: String,
)

/**
 * 名单配置区（提取自本机过滤黑名单 UI，供本地/远程过滤复用）
 * 包含：关键词输入 + 选择应用 + 添加，默认黑名单与手动黑名单抽屉（SwitchPreference 开关 + 删除）
 *
 * @param defaultEntries 内置默认条目（为空则不显示默认黑名单入口）
 * @param manualEntries 手动条目（为空则不显示手动黑名单入口）
 * @param entryEnabled 条目开关状态
 * @param onEntryEnabledChange 开关切换回调（null 时开关不可切换，如远程黑白名单模式）
 * @param onAddEntry 添加条目（关键词、包名）
 * @param onRemoveEntry 删除手动条目
 */
@Composable
fun FilterListSection(
    defaultEntries: List<FilterEntryItem> = emptyList(),
    manualEntries: List<FilterEntryItem>,
    entryEnabled: (FilterEntryItem) -> Boolean = { true },
    onEntryEnabledChange: ((FilterEntryItem, Boolean) -> Unit)? = null,
    onAddEntry: (keyword: String, packageName: String) -> Unit,
    onRemoveEntry: (FilterEntryItem) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var newKeyword by remember { mutableStateOf("") }
    var newPackage by remember { mutableStateOf("") }
    var newPackageIcon by remember { mutableStateOf<ImageBitmap?>(null) }
    var showBuiltinSheet by remember { mutableStateOf(false) }
    var showCustomSheet by remember { mutableStateOf(false) }
    var showAppPickerDialog by remember { mutableStateOf(false) }

    val pm = context.packageManager
    val defaultAppIconBitmap = remember {
        val drawable = try { pm.defaultActivityIcon
        } catch (_: Exception) { null }
        if (drawable is BitmapDrawable) {
            drawable.bitmap.asImageBitmap()
        } else {
            val width = drawable?.intrinsicWidth?.takeIf { it > 0 } ?: 48
            val height = drawable?.intrinsicHeight?.takeIf { it > 0 } ?: 48
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable?.setBounds(0, 0, width, height)
            drawable?.draw(canvas)
            bmp.asImageBitmap()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 文本输入独占一行，避免被按钮压缩
        TextField(
            value = newKeyword,
            onValueChange = { newKeyword = it },
            label = "关键词(可空)",
            colors = TextFieldDefaults.textFieldColors(backgroundColor = MiuixTheme.colorScheme.surfaceContainerHighest),
            textStyle = MiuixTheme.textStyles.main.copy(color = MiuixTheme.colorScheme.onSurfaceContainerHighest),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // 按钮行：选择应用与添加
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { showAppPickerDialog = true },
                colors = ButtonDefaults.buttonColors()
            ) {
                if (newPackage.isBlank()) {
                    Text("选择应用(可空)")
                } else {
                    newPackageIcon?.let { bmp ->
                        Image(
                            bitmap = bmp,
                            contentDescription = "已选应用图标",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("已选")
                }
            }

            Button(
                onClick = {
                    onAddEntry(newKeyword.trim(), newPackage.trim())
                    newKeyword = ""
                    newPackage = ""
                    newPackageIcon = null
                },
                enabled = newKeyword.isNotBlank() || newPackage.isNotBlank(),
                colors = if (newKeyword.isNotBlank() || newPackage.isNotBlank()) ButtonDefaults.buttonColorsPrimary() else ButtonDefaults.buttonColors()
            ) {
                Text("添加")
            }
        }

        // 默认黑名单（内置条目）
        if (defaultEntries.isNotEmpty()) {
            ArrowPreference(
                title = "默认黑名单",
                summary = "共 ${defaultEntries.size} 项",
                onClick = { showBuiltinSheet = true }
            )
        }

        // 手动黑名单（自定义条目）
        if (manualEntries.isNotEmpty()) {
            ArrowPreference(
                title = "手动黑名单",
                summary = "共 ${manualEntries.size} 项",
                onClick = { showCustomSheet = true }
            )
        }
    }

    // 应用选择弹窗
    AppPickerDialog(
        visible = showAppPickerDialog,
        onDismiss = { showAppPickerDialog = false },
        onAppSelected = { packageName ->
            newPackage = packageName
            newPackageIcon = defaultAppIconBitmap
            coroutineScope.launch {
                try {
                    val bmp = AppRepository.getAppIconAsync(context, packageName)
                    newPackageIcon = bmp?.asImageBitmap() ?: defaultAppIconBitmap
                } catch (_: Exception) {
                    newPackageIcon = defaultAppIconBitmap
                }
            }
        },
        title = "选择要过滤的应用"
    )

    // 默认黑名单底部抽屉
    WindowBottomSheet(
        show = showBuiltinSheet,
        title = "默认黑名单",
        onDismissRequest = { showBuiltinSheet = false }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            defaultEntries.forEach { entry ->
                key(entry) {
                    var iconBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
                    LaunchedEffect(entry) {
                        if (entry.packageName.isNotBlank() && iconBitmap == null) {
                            iconBitmap = runCatching {
                                AppRepository.getAppIconAsync(context, entry.packageName)?.asImageBitmap()
                            }.getOrNull() ?: defaultAppIconBitmap
                        }
                    }
                    SwitchPreference(
                        title = entryLabel(entry),
                        startAction = {
                            if (entry.packageName.isNotBlank()) {
                                iconBitmap?.let {
                                    Image(
                                        bitmap = it,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        },
                        checked = entryEnabled(entry),
                        onCheckedChange = { enabled -> onEntryEnabledChange?.invoke(entry, enabled) }
                    )
                }
            }
        }
    }

    // 手动黑名单底部抽屉
    WindowBottomSheet(
        show = showCustomSheet,
        title = "手动黑名单",
        onDismissRequest = { showCustomSheet = false }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            manualEntries.forEach { entry ->
                key(entry) {
                    var iconBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
                    LaunchedEffect(entry) {
                        if (entry.packageName.isNotBlank() && iconBitmap == null) {
                            iconBitmap = runCatching {
                                AppRepository.getAppIconAsync(context, entry.packageName)?.asImageBitmap()
                            }.getOrNull() ?: defaultAppIconBitmap
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SwitchPreference(
                            title = entryLabel(entry),
                            startAction = {
                                if (entry.packageName.isNotBlank()) {
                                    iconBitmap?.let {
                                        Image(
                                            bitmap = it,
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            },
                            checked = entryEnabled(entry),
                            onCheckedChange = { enabled -> onEntryEnabledChange?.invoke(entry, enabled) },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onRemoveEntry(entry) }
                        ) {
                            Text("删除", color = MiuixTheme.colorScheme.onBackground)
                        }
                    }
                }
            }
        }
    }
}

private fun entryLabel(item: FilterEntryItem): String = buildString {
    if (item.keyword.isNotBlank()) {
        append(item.keyword)
        if (item.packageName.isNotBlank()) append(" / ")
    }
    if (item.packageName.isNotBlank()) append(item.packageName)
}