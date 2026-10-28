package notifyrelay.base.util

import java.security.MessageDigest

actual fun sha256(input: String): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(input.toByteArray(Charsets.UTF_8))
        .toHex()
