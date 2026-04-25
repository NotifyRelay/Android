package io.github.miuzarte.scrcpyforandroid.services

import io.github.miuzarte.scrcpyforandroid.scrcpy.ClientOptions.RecordFormat

object NativeRecordingSupport {
    val supportedFormats: List<RecordFormat> = listOf(
        RecordFormat.AUTO,
        RecordFormat.MP4,
        RecordFormat.M4A,
        RecordFormat.AAC,
        RecordFormat.WAV,
    )

    fun isSupported(format: RecordFormat): Boolean {
        return format in supportedFormats
    }
}
