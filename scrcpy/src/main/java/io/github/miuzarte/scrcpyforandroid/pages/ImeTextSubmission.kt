package io.github.miuzarte.scrcpyforandroid.pages

import android.view.KeyEvent
import io.github.miuzarte.scrcpyforandroid.scrcpy.ClientOptions
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun submitImeText(
    scrcpy: Scrcpy,
    text: String,
    keyInjectMode: ClientOptions.KeyInjectMode = ClientOptions.KeyInjectMode.MIXED,
    onFailure: suspend (error: Throwable, useClipboardPaste: Boolean) -> Unit,
) {
    if (text.isBlank() || keyInjectMode == ClientOptions.KeyInjectMode.RAW) {
        return
    }
    val useClipboardPaste = text.any { it.code > 0x7F }
    runCatching {
        withContext(Dispatchers.IO) {
            if (useClipboardPaste) {
                scrcpy.setClipboard(text, paste = true)
            } else {
                scrcpy.injectText(text)
            }
        }
    }.onFailure { error ->
        onFailure(error, useClipboardPaste)
    }
}

internal suspend fun submitImeKeyEvent(
    scrcpy: Scrcpy,
    event: KeyEvent,
    keyInjectMode: ClientOptions.KeyInjectMode,
    forwardKeyRepeat: Boolean,
): Boolean {
    if (keyInjectMode == ClientOptions.KeyInjectMode.PREFER_TEXT) {
        return false
    }
    if (!forwardKeyRepeat && event.repeatCount > 0) {
        return true
    }
    withContext(Dispatchers.IO) {
        scrcpy.injectKeycode(
            action = event.action,
            keycode = event.keyCode,
            repeat = if (forwardKeyRepeat) event.repeatCount else 0,
            metaState = event.metaState,
        )
    }
    return true
}

internal suspend fun submitImeDeleteSurroundingText(
    scrcpy: Scrcpy,
    beforeLength: Int,
    afterLength: Int,
) {
    withContext(Dispatchers.IO) {
        repeat(beforeLength.coerceAtLeast(0)) {
            scrcpy.injectKeycode(0, KeyEvent.KEYCODE_DEL)
            scrcpy.injectKeycode(1, KeyEvent.KEYCODE_DEL)
        }
        repeat(afterLength.coerceAtLeast(0)) {
            scrcpy.injectKeycode(0, KeyEvent.KEYCODE_FORWARD_DEL)
            scrcpy.injectKeycode(1, KeyEvent.KEYCODE_FORWARD_DEL)
        }
    }
}
