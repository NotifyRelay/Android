package notifyrelay.shared.ui.pages.superisland

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import notifyrelay.base.util.TimestampFormatter
import notifyrelay.domain.feature.notification.history.SuperIslandHistoryEntry

/** 组装历史条目的复制文本。 */
fun buildEntryCopyText(
    entry: SuperIslandHistoryEntry,
    includeImageDataOnCopy: Boolean,
): String =
    buildString {
        appendLine("id: " + entry.id)
        appendLine("timestamp: " + TimestampFormatter.format(entry.id))
        entry.sourceDeviceUuid?.takeIf { it.isNotBlank() }?.let {
            appendLine("sourceDeviceUuid: " + it)
        }
        entry.originalPackage?.takeIf { it.isNotBlank() }?.let {
            appendLine("originalPackage: " + it)
        }
        entry.mappedPackage?.takeIf { it.isNotBlank() }?.let {
            appendLine("mappedPackage: " + it)
        }
        entry.appName?.takeIf { it.isNotBlank() }?.let {
            appendLine("appName: " + it)
        }
        entry.title?.takeIf { it.isNotBlank() }?.let {
            appendLine("title: " + it)
        }
        entry.text?.takeIf { it.isNotBlank() }?.let {
            appendLine("text: " + sanitizeImageContent(it, includeImageDataOnCopy))
        }
        if (entry.picMap.isNotEmpty()) {
            appendLine("picMap:")
            entry.picMap.forEach { (label, data) ->
                val finalLabel = label.ifBlank { "(未命名图片)" }
                val finalData = if (includeImageDataOnCopy) data else "图片"
                appendLine("  " + finalLabel + ": " + finalData)
            }
        }
        entry.paramV2Raw?.takeIf { it.isNotBlank() }?.let {
            appendMultilineField("paramV2Raw", it, includeImageDataOnCopy)
        }
        entry.rawPayload?.takeIf { it.isNotBlank() }?.let {
            appendMultilineField("rawPayload", it, includeImageDataOnCopy)
        }
    }.trim()

/** 把图片原始数据替换为占位文本。 */
fun sanitizeImageContent(
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

private val prettyJson = Json { prettyPrint = true }

private fun formatMultilineContent(content: String): List<String> {
    if (content.isBlank()) return emptyList()
    prettyPrintJson(content)?.let { return it }
    return wrapPlainText(content)
}

private fun prettyPrintJson(text: String): List<String>? {
    val firstNonWhitespace = text.firstOrNull { !it.isWhitespace() } ?: return emptyList()
    if (firstNonWhitespace != '{' && firstNonWhitespace != '[') return null
    return try {
        val element = prettyJson.parseToJsonElement(text)
        prettyJson
            .encodeToString(JsonElement.serializer(), element)
            .lineSequence()
            .flatMap { wrapPlainText(it).asSequence() }
            .toList()
    } catch (e: IllegalArgumentException) {
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

private const val SANITIZED_LINE_WRAP = 80
private val WRAP_BREAK_CHARS = charArrayOf(',', ' ', ';', ')', ']', '}', '"')

private fun StringBuilder.appendMultilineField(
    label: String,
    content: String,
    includeImageDataOnCopy: Boolean,
) {
    val sanitized = sanitizeImageContent(content, includeImageDataOnCopy).trim()
    if (sanitized.isBlank()) return
    appendLine(label + ":")
    formatMultilineContent(sanitized).forEach {
        appendLine("  " + it)
    }
}
