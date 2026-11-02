package com.xzyht.notifyrelay.feature.media

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import notifyrelay.domain.feature.device.model.DeviceInfo
import notifyrelay.domain.feature.media.MediaControlAction
import notifyrelay.domain.feature.media.MediaControlPort
import notifyrelay.domain.feature.media.MediaMessageReceiveMode
import notifyrelay.domain.feature.media.MediaSessionSnapshot

/** 以 [RemoteMediaSessionManager] 与 [MediaControlUtil] 实现 [MediaControlPort]。 */
class AndroidMediaControlPort(
    private val context: Context,
) : MediaControlPort {
    private val _sessionUpdates = MutableStateFlow<MediaSessionSnapshot?>(null)
    override val sessionUpdates: StateFlow<MediaSessionSnapshot?> = _sessionUpdates.asStateFlow()

    override fun currentSession(): MediaSessionSnapshot? {
        val session = RemoteMediaSessionManager.getCurrentSession() ?: return null
        return MediaSessionSnapshot(
            packageName = session.packageName,
            title = session.title,
            artist = null,
        )
    }

    override fun currentDevice(): DeviceInfo? = RemoteMediaSessionManager.getCurrentDevice()

    override fun isEnabled(): Boolean = RemoteMediaSessionManager.isEnabled(context)

    override fun receiveMode(): MediaMessageReceiveMode = RemoteMediaSessionManager.getReceiveMode(context)

    override fun setReceiveMode(mode: MediaMessageReceiveMode) {
        RemoteMediaSessionManager.setReceiveMode(context, mode)
    }

    override fun localControl(action: MediaControlAction) {
        when (action) {
            MediaControlAction.PLAY_PAUSE -> MediaControlUtil.playPause()
            MediaControlAction.PREVIOUS -> MediaControlUtil.previous()
            MediaControlAction.NEXT -> MediaControlUtil.next()
        }
    }

    override fun sendToRemote(
        device: DeviceInfo,
        action: MediaControlAction,
    ): Boolean {
        val deviceManager =
            com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManagerSingleton
                .getDeviceMessage(context)
        when (action) {
            MediaControlAction.PLAY_PAUSE -> RemoteMediaSessionManager.onPlayPause(context, deviceManager)
            MediaControlAction.PREVIOUS -> RemoteMediaSessionManager.onPrevious(context, deviceManager)
            MediaControlAction.NEXT -> RemoteMediaSessionManager.onNext(context, deviceManager)
        }
        return true
    }
}
