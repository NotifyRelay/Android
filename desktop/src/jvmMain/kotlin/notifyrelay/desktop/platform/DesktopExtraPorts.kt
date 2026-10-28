package notifyrelay.desktop.platform

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

// 桌面端当前不承载远端应用列表、置顶、本地过滤、音频中继、媒体控制与超级岛；
// 这些能力在公共端 UI 中以端口访问，为保证界面可渲染而给出中性实现。

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

/** 超级岛配置：桌面端不展示超级岛，全部开关为关。 */
class DesktopSuperIslandConfigPort : SuperIslandConfigPort {
    private var remoteDisplayEnabled = false
    private var floatingWindowEnabled = false
    private var listMode = false
    private var injectionMode = SpecInjectionMode.NONE

    override fun isRemoteDisplayEnabled(): Boolean = remoteDisplayEnabled

    override fun setRemoteDisplayEnabled(enabled: Boolean) {
        remoteDisplayEnabled = enabled
    }

    override fun isFloatingWindowEnabled(): Boolean = floatingWindowEnabled

    override fun setFloatingWindowEnabled(enabled: Boolean): Boolean {
        floatingWindowEnabled = enabled
        return true
    }

    override fun isNotificationListMode(): Boolean = listMode

    override fun setNotificationListMode(enabled: Boolean): Boolean {
        listMode = enabled
        return true
    }

    override fun specInjectionMode(): SpecInjectionMode = injectionMode

    override fun setSpecInjectionMode(mode: SpecInjectionMode) {
        injectionMode = mode
    }

    override fun dismissAllRemote() = Unit

    override fun switchChannel() = Unit
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
