package notifyrelay.shared.util

import android.util.Log

@PublishedApi
internal actual fun sharedLogW(
    tag: String,
    message: String,
) {
    Log.w(tag, message)
}
