package com.xzyht.notifyrelay.feature.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.xzyht.notifyrelay.nativecore.NativeCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AudioRelayPlayer {

    private var audioTrack: AudioTrack? = null
    private var isRunning = false
    private var audioScope: CoroutineScope? = null
    private var audioJob: Job? = null

    fun start(
        direction: String,
        sampleRate: Int = 48000,
        channels: Int = 2,
        deviceIp: String = "",
        remoteUuid: String = ""
    ) {
        if (isRunning) return
        isRunning = true

        when (direction) {
            "recv" -> {
                // 接收方：连接发送方的 :23335
                audioScope = CoroutineScope(Dispatchers.IO)
                audioJob = audioScope?.launch {
                    Thread.sleep(500) // 等发送方就绪
                    val ctx = NativeCore.getContext()
                    if (ctx != null) {
                        NativeCore.audioStart("recv", deviceIp, 23335, sampleRate, channels, remoteUuid)
                    }
                }

                val channelConfig = if (channels == 2) {
                    AudioFormat.CHANNEL_OUT_STEREO
                } else {
                    AudioFormat.CHANNEL_OUT_MONO
                }
                val bufferSize = AudioTrack.getMinBufferSize(
                    sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT
                )
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelConfig).build()
                    )
                    .setBufferSizeInBytes(bufferSize.coerceAtLeast(4096))
                    .setTransferMode(AudioTrack.MODE_STREAM).build()
                audioTrack?.play()

                NativeCore.registerAudioDataCallback { pcmData, sr, ch ->
                    audioTrack?.write(pcmData, 0, pcmData.size)
                }
            }
            "send" -> {
                // 发送方：监听 :23335，等接收方来连
                audioScope = CoroutineScope(Dispatchers.IO)
                audioJob = audioScope?.launch {
                    val ctx = NativeCore.getContext()
                    if (ctx != null) {
                        NativeCore.audioStart("send", "", 23335, sampleRate, channels, remoteUuid)
                    }
                }
            }
        }
    }

    fun stop() {
        isRunning = false
        NativeCore.audioStop()
        try {
            audioTrack?.stop()
        } catch (_: Exception) {}
        try {
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
        audioJob?.cancel()
        audioScope = null
    }

    val isActive: Boolean
        get() = isRunning && NativeCore.audioIsActive()
}
