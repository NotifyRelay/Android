package notifyrelay.desktop.platform

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import notifyrelay.data.Storage
import notifyrelay.domain.feature.appslist.RemoteAppsPort
import notifyrelay.domain.feature.appslist.model.RemoteAppInfo
import notifyrelay.domain.feature.device.model.DeviceInfo
import notifyrelay.domain.feature.media.AudioOperationResult
import notifyrelay.domain.feature.media.AudioRelayPort
import notifyrelay.domain.feature.media.MediaControlAction
import notifyrelay.domain.feature.media.MediaControlPort
import notifyrelay.domain.feature.media.MediaMessageReceiveMode
import notifyrelay.domain.feature.media.MediaSessionSnapshot
import notifyrelay.domain.feature.notification.filter.FilterEntry
import notifyrelay.domain.feature.notification.filter.LocalFilterPort
import notifyrelay.domain.feature.notification.history.AppIconInfo
import notifyrelay.domain.feature.notification.history.GroupedSuperIslandHistory
import notifyrelay.domain.feature.notification.history.SuperIslandHistoryEntry
import notifyrelay.domain.feature.notification.history.SuperIslandHistoryPort
import notifyrelay.domain.feature.notification.superisland.SpecInjectionMode
import notifyrelay.domain.feature.notification.superisland.SuperIslandConfigPort
import notifyrelay.shared.feature.appslist.PinnedAppsPort

// 桌面端当前不承载远端应用列表、置顶、本地过滤、音频中继、媒体控制与超级岛展示；
// 这些能力在公共端 UI 中以端口访问，除超级岛配置落到 Storage 外，其余给出中性实现以保证界面可渲染。

/** 置顶应用：桌面端无置顶语义，恒为空。 */
class DesktopPinnedAppsPort : PinnedAppsPort {
    private val empty = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    override val pinnedApps: StateFlow<Map<String, Set<String>>> = empty

    override fun load(deviceUuid: String) = Unit

    override fun pin(
        deviceUuid: String,
        packageName: String,
    ) = Unit

    override fun unpin(
        deviceUuid: String,
        packageName: String,
    ) = Unit

    override fun isPinned(
        deviceUuid: String,
        packageName: String,
    ): Boolean = false
}

/** 远端应用列表：桌面端尚无应用列表协商，返回空列表。 */
class DesktopRemoteAppsPort : RemoteAppsPort {
    private val noIconUpdates = MutableStateFlow<Pair<String, Long>?>(null)
    private val noPinned = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    override val iconUpdates: StateFlow<Pair<String, Long>?> = noIconUpdates
    override val pinnedApps: StateFlow<Map<String, Set<String>>> = noPinned

    override suspend fun remoteApps(deviceUuid: String): List<RemoteAppInfo> = emptyList()

    override fun loadPinned(deviceUuid: String) = Unit

    override fun pin(
        deviceUuid: String,
        packageName: String,
    ) = Unit

    override fun unpin(
        deviceUuid: String,
        packageName: String,
    ) = Unit

    override suspend fun requestAppList(deviceUuid: String): Boolean = false

    override fun findOnlineDevice(deviceUuid: String): DeviceInfo? = null

    override fun launchApp(
        app: RemoteAppInfo,
        deviceIp: String,
        useScrcpyStartApp: Boolean,
    ) = Unit
}

/** 本地过滤：桌面端无通知监听源，过滤条目恒为空。 */
class DesktopLocalFilterPort : LocalFilterPort {
    override var filterSelf: Boolean = false
    override var filterOngoing: Boolean = false
    override var filterNoTitleOrText: Boolean = false
    override var filterImportanceNone: Boolean = false

    override fun builtinKeywords(): Set<String> = emptySet()

    override fun defaultPackageFilters(): Set<String> = emptySet()

    override suspend fun entries(): Set<FilterEntry> = emptySet()

    override suspend fun enabledEntries(): Set<FilterEntry> = emptySet()

    override suspend fun setEntryEnabled(
        entry: FilterEntry,
        enabled: Boolean,
    ) = Unit

    override suspend fun addEntry(
        keyword: String,
        packageName: String,
    ) = Unit

    override suspend fun removeEntry(
        keyword: String,
        packageName: String,
    ) = Unit
}

/** 音频中继：桌面端未接入音频通道，一律返回失败原因。 */
class DesktopAudioRelayPort : AudioRelayPort {
    override fun relayMode(): Int = 0

    override fun requestSendAudio(device: DeviceInfo): AudioOperationResult = AudioOperationResult.Failure("桌面端暂不支持音频转发")

    override fun requestReceiveAudio(device: DeviceInfo): AudioOperationResult = AudioOperationResult.Failure("桌面端暂不支持音频转发")

    override fun stopAudio() = Unit
}

/** 媒体控制：桌面端未接入媒体会话，会话为空、控制为空操作。 */
class DesktopMediaControlPort : MediaControlPort {
    private val noSession = MutableStateFlow<MediaSessionSnapshot?>(null)
    override val sessionUpdates: StateFlow<MediaSessionSnapshot?> = noSession

    override fun currentSession(): MediaSessionSnapshot? = null

    override fun currentDevice(): DeviceInfo? = null

    override fun isEnabled(): Boolean = false

    override fun receiveMode(): MediaMessageReceiveMode = MediaMessageReceiveMode.Off

    override fun setReceiveMode(mode: MediaMessageReceiveMode) = Unit

    override fun localControl(action: MediaControlAction) = Unit

    override fun sendToRemote(
        device: DeviceInfo,
        action: MediaControlAction,
    ): Boolean = false
}

/**
 * 超级岛配置：键与互斥推导与 Android 侧同源，桌面端落到公共端 Storage。
 *
 * 默认值按桌面平台取值：无 OS 版本判定（浮窗默认开）、非平板（列表模式默认关）。
 * 展示层未接线，[dismissAllRemote] 与 [switchChannel] 保持空操作。
 */
class DesktopSuperIslandConfigPort : SuperIslandConfigPort {
    private companion object {
        /** 持久化键与 :app 的 SuperIslandConfigUtils 一致，值不可改。 */
        const val KEY_FLOATING_WINDOW = "super_island_floating_window"
        const val KEY_NOTIFICATION_LIST = "super_island_notification_list"
        const val KEY_SPEC_INJECTION = "spec_injection_mode"
        const val KEY_REMOTE_SHOW = "superisland_show"

        /** 注入方式的显式编码，与枚举序数解耦（0/1 沿用历史序数，3 为 NONE）。 */
        const val CODE_SUPER_ISLAND = 0
        const val CODE_LIVE_UPDATES = 1
        const val CODE_NONE = 3

        /** 旧版本默认值（两者都注入），视为「未显式修改」，跟随当前默认。 */
        const val LEGACY_BOTH_ORDINAL = 2

        /** 桌面端无 OS 版本判定与平板判定。 */
        const val DEFAULT_FLOATING_WINDOW = true
        const val IS_TABLET = false
    }

    override fun isRemoteDisplayEnabled(): Boolean = Storage.getBoolean(KEY_REMOTE_SHOW, true)

    override fun setRemoteDisplayEnabled(enabled: Boolean) {
        Storage.putBoolean(KEY_REMOTE_SHOW, enabled)
    }

    override fun isFloatingWindowEnabled(): Boolean = Storage.getBoolean(KEY_FLOATING_WINDOW, DEFAULT_FLOATING_WINDOW)

    override fun setFloatingWindowEnabled(enabled: Boolean): Boolean {
        val previousChannel = resolveDisplayChannel()
        // 列表模式默认值依赖浮窗开关，须在写入浮窗开关之前判定
        val listModeWillClose = enabled && isNotificationListMode()
        Storage.putBoolean(KEY_FLOATING_WINDOW, enabled)
        if (listModeWillClose) {
            Storage.putBoolean(KEY_NOTIFICATION_LIST, false)
        }
        return resolveDisplayChannel() != previousChannel
    }

    override fun isNotificationListMode(): Boolean {
        val defaultListMode = IS_TABLET && !isFloatingWindowEnabled()
        return Storage.getBoolean(KEY_NOTIFICATION_LIST, defaultListMode)
    }

    override fun setNotificationListMode(enabled: Boolean): Boolean {
        val previousChannel = resolveDisplayChannel()
        if (enabled && isFloatingWindowEnabled()) {
            Storage.putBoolean(KEY_FLOATING_WINDOW, false)
        }
        Storage.putBoolean(KEY_NOTIFICATION_LIST, enabled)
        return resolveDisplayChannel() != previousChannel
    }

    override fun specInjectionMode(): SpecInjectionMode {
        val code = Storage.getInt(KEY_SPEC_INJECTION, -1)
        // 先解码 NONE：它是独立编码，不得落入历史值判定
        if (code == CODE_NONE) return SpecInjectionMode.NONE
        if (code == -1 || code == LEGACY_BOTH_ORDINAL) return SpecInjectionMode.LIVE_UPDATES
        return when (code) {
            CODE_SUPER_ISLAND -> SpecInjectionMode.SUPER_ISLAND
            CODE_LIVE_UPDATES -> SpecInjectionMode.LIVE_UPDATES
            else -> SpecInjectionMode.LIVE_UPDATES
        }
    }

    override fun setSpecInjectionMode(mode: SpecInjectionMode) {
        val code =
            when (mode) {
                SpecInjectionMode.SUPER_ISLAND -> CODE_SUPER_ISLAND
                SpecInjectionMode.LIVE_UPDATES -> CODE_LIVE_UPDATES
                SpecInjectionMode.NONE -> CODE_NONE
            }
        Storage.putInt(KEY_SPEC_INJECTION, code)
    }

    override fun dismissAllRemote() = Unit

    override fun switchChannel() = Unit

    /** 展示通道：三者互斥，由浮窗与列表两个开关推导（仅本类内部使用）。 */
    private enum class DisplayChannel {
        FLOATING,
        LIST,
        NOTIFICATION,
    }

    private fun resolveDisplayChannel(): DisplayChannel =
        when {
            isFloatingWindowEnabled() -> DisplayChannel.FLOATING
            isNotificationListMode() -> DisplayChannel.LIST
            else -> DisplayChannel.NOTIFICATION
        }
}

/** 超级岛历史：桌面端无超级岛数据源，恒为空。 */
class DesktopSuperIslandHistoryPort : SuperIslandHistoryPort {
    override val historyChanged: StateFlow<Long> = MutableStateFlow(0L)
    override val iconUpdates: StateFlow<Pair<String, Long>?> = MutableStateFlow(null)

    override fun pagingSource(): PagingSource<Int, GroupedSuperIslandHistory> = EmptyPagingSource()

    override suspend fun appIcons(packageNames: List<String>): Map<String, AppIconInfo> = emptyMap()

    override suspend fun loadEntryDetail(id: Long): SuperIslandHistoryEntry? = null

    override suspend fun deleteEntry(id: Long) = Unit

    override suspend fun deleteGroup(packageName: String) = Unit

    override suspend fun clearHistory() = Unit
}

/** 恒为空页的分页源。 */
private class EmptyPagingSource<T : Any> : PagingSource<Int, T>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> = LoadResult.Page(emptyList(), prevKey = null, nextKey = null)

    override fun getRefreshKey(state: PagingState<Int, T>): Int? = null
}
