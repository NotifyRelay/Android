package com.xzyht.notifyrelay.feature.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.AudioPlaybackCaptureConfiguration
import android.media.projection.MediaProjection
import androidx.core.content.ContextCompat
import com.xzyht.notifyrelay.nativecore.NativeCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import notifyrelay.base.util.Logger
import notifyrelay.base.util.ToastUtils

class AudioRelayPlayer(private val context: Context) {

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
            else -> {
                Logger.w("AudioRelay", "未知音频方向: $direction")
                isRunning = false
                return false
            }
        }
        return true
    }

    fun startSendCapture(mediaProjection: MediaProjection, sampleRate: Int = 48000, channels: Int = 2) {
        if (!isRunning) return
        if (captureJob?.isActive == true || audioRecord != null) {
            Logger.w("AudioRelay", "屏幕音频捕获已在运行，忽略重复启动")
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Logger.w("AudioRelay", "缺少 RECORD_AUDIO 权限，无法启动屏幕音频捕获")
            ToastUtils.showShortToast(context, "缺少录音权限，无法捕获屏幕声音，请在系统设置中允许")
            return
        }
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

        // 静默时补发的静音 PCM 块大小（20ms，与 Opus 帧对齐），维持 RTP 流连续
        val bytesPerFrame = sampleRate * channels * 2 * 20 / 1000
        val silenceChunk = ByteArray(bytesPerFrame)
        val buf = ByteArray(bytesPerFrame)
        val frameIntervalMs = 20L
        var nextFrameAt = 0L
        captureJob = CoroutineScope(Dispatchers.IO).launch {
            val record = audioRecord ?: return@launch
            try {
                while (isActive && isRunning) {
                    // 按实时速率节流发送（50 帧/秒），避免积压突发导致对端 UDP 缓冲溢出丢包
                    val now = System.currentTimeMillis()
                    if (nextFrameAt > now) {
                        delay(nextFrameAt - now)
                    } else if (now - nextFrameAt > 200) {
                        // 发送落后过多：重置节奏，避免积压导致突发
                        nextFrameAt = now
                    }
                    nextFrameAt += frameIntervalMs

                    val read = record.read(buf, 0, buf.size)
                    if (read < 0) {
                        Logger.w("AudioRelay", "屏幕音频捕获读取错误: $read")
                        break
                    }
                    try {
                        // AudioPlaybackCapture 在无音频源时 read 返回 0（正常静默），
                        // 补发静音帧维持 RTP 流连续，等待而非终止
                        val frame = if (read > 0) buf.copyOf(read) else silenceChunk
                        NativeCore.audioWriteFrame(frame)
                    } catch (_: Exception) {}
                }
            } finally {
                try {
                    record.stop()
                } catch (_: Exception) {}
                try {
                    record.release()
                } catch (_: Exception) {}
                if (audioRecord === record) audioRecord = null
            }
        }
    }

    fun stopSendCapture() {
        val record = audioRecord
        audioRecord = null
        try {
            record?.stop()
        } catch (_: Exception) {}
        captureJob?.cancel()
        captureJob = null
    }

    suspend fun stopSendCaptureAndJoin() {
        val record = audioRecord
        audioRecord = null
        try {
            record?.stop()
        } catch (_: Exception) {}
        captureJob?.cancelAndJoin()
        captureJob = null
    }

    fun stop() {
        isRunning = false
        val record = audioRecord
        audioRecord = null
        try {
            record?.stop()
        } catch (_: Exception) {}
        captureJob?.cancel()
        captureJob = null
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

        val avgEnergy = if (callbackFrames > 0) totalEnergy / callbackFrames else 0L
        Logger.i("AudioRelay", "会话结束: callbackFrames=$callbackFrames, avgEnergy=$avgEnergy, writeErrors=$writeErrors")
    }

    val isActive: Boolean
        get() = isRunning && NativeCore.audioIsActive()
}
