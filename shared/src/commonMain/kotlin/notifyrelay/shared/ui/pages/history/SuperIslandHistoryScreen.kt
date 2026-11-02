package notifyrelay.shared.ui.pages.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import notifyrelay.base.SuperIslandStorageKeys
import notifyrelay.domain.feature.notification.history.SuperIslandHistoryEntry
import notifyrelay.shared.ui.common.DoubleClickConfirmButton
import notifyrelay.shared.ui.pages.rememberDeleteSwipeWidth
import notifyrelay.shared.ui.pages.superisland.SuperIslandHistoryListBlock
import notifyrelay.shared.ui.pages.superisland.buildEntryCopyText
import notifyrelay.shared.ui.port.LocalClipboardPort
import notifyrelay.shared.ui.port.LocalPreferencePort
import notifyrelay.shared.ui.port.LocalToastPort
import notifyrelay.shared.ui.viewmodel.SuperIslandHistoryViewModel
import notifyrelay.shared.ui.viewmodel.superIslandHistoryViewModel
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 历史条目复制的默认剪贴板标签。 */
private const val CLIPBOARD_LABEL = "super_island_entry"

/**
 * 超级岛历史页。
 *
 * 页面只依赖端口：剪贴板、提示与偏好均由宿主提供；
 * 「点击条目复刻超级岛」为平台专属动作，经 [onReplicaRequest] 注入，传 null 时不响应点击。
 */
@Composable
fun SuperIslandHistoryScreen(
    onReplicaRequest: ((SuperIslandHistoryEntry) -> Unit)? = null,
    viewModel: SuperIslandHistoryViewModel = superIslandHistoryViewModel(),
) {
    val preferencePort = LocalPreferencePort.current
    val clipboardPort = LocalClipboardPort.current
    val toastPort = LocalToastPort.current

    var includeImageDataOnCopy by remember {
        mutableStateOf(preferencePort?.getBoolean(SuperIslandStorageKeys.COPY_IMAGE_DATA, false) ?: false)
    }

    val uiState by viewModel.uiState.collectAsState()
    val pagingItems = viewModel.groupedPagingFlow.collectAsLazyPagingItems()

    val groupPackages =
        pagingItems.itemSnapshotList.items
            .map { it.packageName }
            .distinct()
    LaunchedEffect(groupPackages) {
        viewModel.preloadAppIcons(groupPackages)
    }

    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    val (deleteWidthPx, deleteWidth) = rememberDeleteSwipeWidth()

    val copyEntry: (SuperIslandHistoryEntry) -> Unit = { entry ->
        val content = buildEntryCopyText(entry, includeImageDataOnCopy)
        if (content.isBlank()) {
            toastPort.short("当前条目无可复制内容")
        } else if (clipboardPort.copyText(CLIPBOARD_LABEL, content)) {
            toastPort.short("已复制原始消息到剪贴板")
        } else {
            toastPort.short("复制失败")
        }
    }

    Scaffold(
        containerColor = colorScheme.background,
        floatingToolbar = {
            if (pagingItems.itemCount > 0) {
                FloatingToolbar(
                    color = colorScheme.primary,
                    cornerRadius = 20.dp,
                    showDivider = false,
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DoubleClickConfirmButton(
                            text = "清空超级岛历史",
                            confirmText = "确认?",
                            onClick = {},
                            onConfirm = { viewModel.clearHistory() },
                            colors = ButtonDefaults.buttonColors(color = colorScheme.onSurface),
                            confirmColors = ButtonDefaults.buttonColors(color = colorScheme.error),
                        )
                    }
                }
            }
        },
        floatingToolbarPosition = ToolbarPosition.BottomEnd,
        content = { paddingValues ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(12.dp),
            ) {
                SwitchPreference(
                    title = "复制图片详细信息",
                    checked = includeImageDataOnCopy,
                    onCheckedChange = {
                        includeImageDataOnCopy = it
                        preferencePort?.putBoolean(SuperIslandStorageKeys.COPY_IMAGE_DATA, it)
                    },
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (pagingItems.itemCount == 0) {
                    Text(
                        "暂无超级岛历史记录",
                        style = textStyles.body2,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                } else {
                    SuperIslandHistoryListBlock(
                        pagingItems = pagingItems,
                        expandedGroups = uiState.expandedGroups,
                        includeImageDataOnCopy = includeImageDataOnCopy,
                        onToggleGroup = { packageName -> viewModel.toggleGroupExpansion(packageName) },
                        onDeleteGroup = { packageName -> viewModel.deleteGroup(packageName) },
                        onDeleteEntry = { id -> viewModel.deleteEntry(id) },
                        loadEntryDetail = { id -> viewModel.loadEntryDetail(id) },
                        deleteWidthPx = deleteWidthPx,
                        deleteWidth = deleteWidth,
                        onReplicaRequest = onReplicaRequest,
                        onCopyEntry = copyEntry,
                    )
                }
            }
        },
    )
}
