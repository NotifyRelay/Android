package com.xzyht.notifyrelay.feature.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.AudioPlaybackCaptureConfiguration
import android.media.projection.MediaProjection
import com.xzyht.notifyrelay.nativecore.NativeCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import notifyrelay.base.util.Logger

class AudioRelayPlayer {

    private var audioTrack: AudioTrack? = null
    private var audioRecord: AudioRecord? = null
    private var isRunning = false
    private var audioScope: CoroutineScope? = null
    private var audioJob: Job? = null
    private var captureJob: Job? = null

    private var callbackFrames = 0
    private var totalEnergy = 0L
    private var writeErrors = 0

    fun start(
        direction: String,
        sampleRate: Int = 48000,
        channels: Int = 2,
        remoteUuid: String = ""
    ): Boolean {
        if (isRunning) return false
        isRunning = true
        callbackFrames = 0
        totalEnergy = 0L
        writeErrors = 0

        when (direction) {
            "recv" -> {
                val ctx = NativeCore.getContext()
                if (ctx == null) {
                    isRunning = false
                    return false
                }
                val ret = NativeCore.audioStart("recv", 23335, sampleRate, channels, remoteUuid)
                if (ret != 0) {
                    isRunning = false
                    return false
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
                    callbackFrames++
                    totalEnergy += pcmData.take(200).sumOf { kotlin.math.abs(it.toInt()) }
                    try {
                        val ret = audioTrack?.write(pcmData, 0, pcmData.size)
                        if (ret != null && ret < 0) writeErrors++
                    } catch (_: Exception) {
                        writeErrors++
                    }
                }
            }
            "send" -> {
                val ctx = NativeCore.getContext()
                if (ctx == null) {
                    isRunning = false
                    return false
                }
                val ret = NativeCore.audioStart("send", 23335, sampleRate, channels, remoteUuid)
                if (ret != 0) {
                    isRunning = false
                    return false
                }
            }
        }
        return true
    }

    fun startSendCapture(mediaProjection: MediaProjection, sampleRate: Int = 48000, channels: Int = 2) {
        if (!isRunning) return
        val channelConfig = if (channels == 2) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT)
        val bufferSize = (minBuffer * 2).coerceAtLeast(8192)

        val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        audioRecord = AudioRecord.Builder()
            .setAudioPlaybackCaptureConfig(config)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .build()

        audioRecord?.startRecording()

        val buf = ByteArray(bufferSize)
        captureJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive && isRunning) {
                val read = audioRecord?.read(buf, 0, buf.size) ?: -1
                if (read <= 0) break
                try {
                    NativeCore.audioWriteFrame(buf.take(read).toByteArray())
                } catch (_: Exception) {}
            }
            try {
                audioRecord?.stop()
            } catch (_: Exception) {}
            try {
                audioRecord?.release()
            } catch (_: Exception) {}
            audioRecord = null
        }
    }

    fun stop() {
        isRunning = false
        captureJob?.cancel()
        captureJob = null
        audioJob?.cancel()
        audioJob = null
        audioScope = null
        try {
            NativeCore.audioStop()
        } catch (_: Exception) {}
        try {
            audioRecord?.stop()
        } catch (_: Exception) {}
        try {
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        try {
            audioTrack?.stop()
        } catch (_: Exception) {}
        try {
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null

        val avgEnergy = if (callbackFrames > 0) totalEnergy / callbackFrames else 0L
        Logger.i("AudioRelay", "会话结束: callbackFrames=$callbackFrames, avgEnergy=$avgEnergy, writeErrors=$writeErrors")
    }

    val isActive: Boolean
        get() = isRunning && NativeCore.audioIsActive()
}
