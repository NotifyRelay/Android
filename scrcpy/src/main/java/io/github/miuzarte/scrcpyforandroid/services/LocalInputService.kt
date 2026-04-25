package io.github.miuzarte.scrcpyforandroid.services

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager

object LocalInputService {
    fun showSoftKeyboard(view: View) {
        view.requestFocus()
        val inputManager =
            view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                ?: return
        inputManager.showSoftInput(view, 0)
    }

    fun getClipboardText(context: Context): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return null
        if (!clipboard.hasPrimaryClip()) {
            return null
        }
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount <= 0) {
            return null
        }
        val description = clipboard.primaryClipDescription
        if (description != null &&
            !description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) &&
            !description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML) &&
            !description.hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST)
        ) {
            return null
        }
        return clip.getItemAt(0).coerceToText(context)?.toString()
    }

    fun setClipboardText(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("scrcpy", text))
    }
}
