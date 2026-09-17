package com.xzyht.notifyrelay.ui.pages.superisland

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.DateFormat
import android.widget.Toast
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.xzyht.notifyrelay.feature.notification.superisland.history.SuperIslandHistoryStoreEntry
import com.xzyht.notifyrelay.feature.notification.superisland.replica.FloatingReplicaManager
import notifyrelay.base.util.Logger
import java.util.Date

internal fun formatTimestamp(timestamp: Long): String =
    try {
        DateFormat.format("yyyy-MM-dd HH:mm:ss", Date(timestamp)).toString()
    } catch (_: Exception) {
        timestamp.toString()
    }

internal fun buildEntryCopyText(
    entry: SuperIslandHistoryStoreEntry,
    includeImageDataOnCopy: Boolean,
): String =
    buildString {
        appendLine("id: ${entry.id}")
        appendLine("timestamp: ${formatTimestamp(entry.id)}")
        entry.sourceDeviceUuid?.takeIf { it.isNotBlank() }?.let {
            appendLine("sourceDeviceUuid: $it")
        }
        entry.originalPackage?.takeIf { it.isNotBlank() }?.let {
            appendLine("originalPackage: $it")
        }
        entry.mappedPackage?.takeIf { it.isNotBlank() }?.let {
            appendLine("mappedPackage: $it")
        }
        entry.appName?.takeIf { it.isNotBlank() }?.let {
            appendLine("appName: $it")
        }
        entry.title?.takeIf { it.isNotBlank() }?.let {
            appendLine("title: $it")
        }
        entry.text?.takeIf { it.isNotBlank() }?.let {
            appendLine("text: ${sanitizeImageContent(it, includeImageDataOnCopy)}")
        }
        if (entry.picMap.isNotEmpty()) {
            appendLine("picMap:")
            entry.picMap.forEach { (label, data) ->
                val finalLabel = label.ifBlank { "(未命名图片)" }
                val finalData = if (includeImageDataOnCopy) data else "图片"
                appendLine("  $finalLabel: $finalData")
            }
        }
        entry.paramV2Raw?.takeIf { it.isNotBlank() }?.let {
            appendMultilineField("paramV2Raw", it, includeImageDataOnCopy)
        }
        entry.rawPayload?.takeIf { it.isNotBlank() }?.let {
            appendMultilineField("rawPayload", it, includeImageDataOnCopy)
        }
    }.trim()

internal fun copyEntryToClipboard(
    context: Context,
    content: String,
) {
    if (content.isBlank()) {
        Toast.makeText(context, "当前条目无可复制内容", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("super_island_entry", content)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "已复制原始消息到剪贴板", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Logger.e("NotifyRelay", "复制超级岛原始消息失败", e)
        Toast.makeText(context, "复制失败", Toast.LENGTH_SHORT).show()
    }
}

internal fun triggerFloatingReplica(
    context: Context,
    entry: SuperIslandHistoryStoreEntry,
) {
    val sourceId =
        entry.mappedPackage?.takeIf { it.isNotBlank() }
            ?: entry.originalPackage?.takeIf { it.isNotBlank() }
            ?: entry.appName?.takeIf { it.isNotBlank() }
            ?: entry.id.toString()
    val title =
        entry.title?.takeIf { it.isNotBlank() }
            ?: entry.appName?.takeIf { it.isNotBlank() }
            ?: entry.mappedPackage?.takeIf { it.isNotBlank() }
            ?: entry.originalPackage?.takeIf { it.isNotBlank() }
    FloatingReplicaManager.showFloating(
        context = context,
        sourceId = sourceId,
        title = title,
        text = entry.text,
        paramV2Raw = entry.paramV2Raw,
        picMap = entry.picMap.takeIf { it.isNotEmpty() },
        isLocked = false,
    )
}

internal fun sanitizeImageContent(
    source: String,
    includeImageDataOnCopy: Boolean,
): String {
    if (includeImageDataOnCopy) return source
    var sanitized = DATA_URL_REGEX.replace(source) { "图片" }
    sanitized = IMAGE_URL_REGEX.replace(sanitized) { "图片" }
    return sanitized
}

private val DATA_URL_REGEX =
    Regex(
        pattern = "data:[^,]+;base64,[^\\s\"]+",
        options = setOf(RegexOption.IGNORE_CASE),
    )

private val IMAGE_URL_REGEX =
    Regex(
        pattern = "https?:[^\\s\"]+\\.(?:png|jpe?g|gif|webp|bmp|svg)",
        options = setOf(RegexOption.IGNORE_CASE),
    )

private fun formatMultilineContent(content: String): List<String> {
    if (content.isBlank()) return emptyList()
    prettyPrintJson(content)?.let { return it }
    return wrapPlainText(content)
}

private fun prettyPrintJson(text: String): List<String>? {
    val firstNonWhitespace = text.firstOrNull { !it.isWhitespace() } ?: return emptyList()
    if (firstNonWhitespace != '{' && firstNonWhitespace != '[') return null
    return try {
        val jsonElement = JsonParser.parseString(text)
        val pretty = prettyGson.toJson(jsonElement)
        pretty
            .lineSequence()
            .flatMap { wrapPlainText(it).asSequence() }
            .toList()
    } catch (_: Exception) {
        null
    }
}

private fun wrapPlainText(text: String): List<String> {
    val firstNonWhitespaceIndex = text.indexOfFirst { !it.isWhitespace() }
    val indent = if (firstNonWhitespaceIndex > 0) text.substring(0, firstNonWhitespaceIndex) else ""
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return emptyList()
    if (trimmed.length <= SANITIZED_LINE_WRAP) return listOf(indent + trimmed)
    val result = mutableListOf<String>()
    var remaining = trimmed
    while (remaining.length > SANITIZED_LINE_WRAP) {
        val window = remaining.substring(0, SANITIZED_LINE_WRAP)
        val breakIndex = window.lastIndexOfAny(WRAP_BREAK_CHARS)
        val cut = if (breakIndex <= 0) SANITIZED_LINE_WRAP else breakIndex + 1
        val segment = remaining.substring(0, cut).trimEnd()
        result += indent + segment
        remaining = remaining.substring(cut).trimStart()
    }
    if (remaining.isNotEmpty()) {
        result += indent + remaining
    }
    return result
}

private val prettyGson by lazy { GsonBuilder().setPrettyPrinting().create() }

private const val SANITIZED_LINE_WRAP = 80
private val WRAP_BREAK_CHARS = charArrayOf(',', ' ', ';', ')', ']', '}', '"')

private fun StringBuilder.appendMultilineField(
    label: String,
    content: String,
    includeImageDataOnCopy: Boolean,
) {
    val sanitized = sanitizeImageContent(content, includeImageDataOnCopy).trim()
    if (sanitized.isBlank()) return
    appendLine("$label:")
    formatMultilineContent(sanitized).forEach {
        appendLine("  $it")
    }
}
