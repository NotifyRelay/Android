package com.xzyht.notifyrelay.util

import android.content.Context
import android.net.Uri
import io.github.miuzarte.scrcpyforandroid.pages.ScrcpyUiViewModel
import notifyrelay.base.log.Logger
import notifyrelay.data.Storage
import notifyrelay.data.config.ScrcpyPreferenceKeys

/**
 * 保存用户选定的 scrcpy server jar。
 *
 * 同时写入偏好项并更新运行时单例，使下一次启动直接使用新 jar。
 *
 * @param context 调用方上下文。
 * @param uri 用户选定的文件。
 * @param tag 失败日志的标签。
 */
fun saveScrcpyServerUri(
    context: Context,
    uri: Uri,
    tag: String,
) {
    runCatching {
        val uriString = uri.toString()
        Storage.putString(
            ScrcpyPreferenceKeys.CUSTOM_SERVER_URI,
            uriString,
            Storage.PrefsType.SCRCPY,
        )
        val app = context.applicationContext as android.app.Application
        ScrcpyUiViewModel.getInstance(app).customServerUri = uriString
    }.onFailure { e ->
        Logger.e(tag, "scrcpy server URI 保存失败: uri=$uri", e)
    }
}
