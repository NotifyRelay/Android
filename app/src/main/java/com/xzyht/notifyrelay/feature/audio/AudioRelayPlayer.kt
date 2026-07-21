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
                val minBuffer = AudioTrack.getMinBufferSize(
                    sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = (minBuffer * 4).coerceAtLeast(65536)
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
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM).build()
                audioTrack?.play()

                NativeCore.registerAudioDataCallback { pcmData, sr, ch ->
                    try {
                        audioTrack?.write(pcmData, 0, pcmData.size)
                    } catch (_: Exception) {}
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
        audioJob?.cancel()
        audioJob = null
        audioScope = null
        try {
            NativeCore.audioStop()
        } catch (_: Exception) {}
        try {
            audioTrack?.stop()
        } catch (_: Exception) {}
        try {
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }

    val isActive: Boolean
        get() = isRunning && NativeCore.audioIsActive()
}
