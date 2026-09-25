package com.xzyht.notifyrelay.ui.devtools

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.StatusBarNotification
import android.widget.RemoteViews
import notifyrelay.base.util.image.ImageUtils
import notifyrelay.base.util.image.toBitmapOrDefault
import org.json.JSONArray
import org.json.JSONObject

/**
 * 通知「全字段」转储器（开发者选项 → 通知字段转储页专用）。
 *
 * 用途：把当前通知栏某条通知（[StatusBarNotification]）的元信息、`Notification` 字段与
 * `extras` 全部键值逐项展开，供超级岛 / Apple Watch 转发链路的结构适配排查。
 *
 * 设计要点：
 * - **单一数据源**：先构造一棵 [JSONObject] 树（[buildDumpJson]），
 *   文本（[renderDumpText]）与 JSON（[buildDumpJson] + [JSONObject.toString]）都从该树渲染，
 *   避免两份实现字段漂移。
 * - **二进制可选**：`includeBinary = false` 时 Bitmap / Icon / byte[] 等只给
 *   「类型 + 尺寸」摘要；为 `true` 时转成 data URI / base64 内联（体积可能很大，由调用方显式开启）。
 * - **只读**：不修改通知，不写任何存储（页面侧同样只在内存中保留本次载入结果）。
 *
 * 注意：`Bundle.get(String)` 在 API 33 起被标记废弃，但**枚举未知类型的 extras 键值没有等价替代**，
 * 故按项目既有做法（`SuperIslandManager` 同类场景）做局部 `@Suppress("DEPRECATION")`。
 */
internal object NotificationFieldDump {
    private const val INDENT = "  "

    /** 递归展开 Bundle 的最大深度，防御异常自嵌套结构。 */
    private const val MAX_DEPTH = 8

    /**
     * 构造一条通知的完整字段 JSON 树。
     *
     * @param context 用于加载 [Icon] 位图（仅在 [includeBinary] 为 true 时使用）。
     * @param sbn 目标通知。
     * @param includeBinary 是否内联二进制内容（Bitmap / Icon / byte[]）。
     */
    fun buildDumpJson(
        context: Context,
        sbn: StatusBarNotification,
        includeBinary: Boolean,
    ): JSONObject {
        val root = JSONObject()
        root.put("dumpTime", System.currentTimeMillis())
        root.put("includeBinary", includeBinary)
        root.put("statusBarNotification", buildSbnJson(sbn))
        root.put("notification", buildNotificationJson(context, sbn.notification, includeBinary))
        return root
    }

    /** 把字段树渲染为可读文本（`key: value` 缩进结构，内嵌 JSON 字符串会展开为嵌套块）。 */
    fun renderDumpText(root: JSONObject): String = buildString { appendJsonBlock(this, root, 0) }.trimEnd()

    // ==================== StatusBarNotification ====================

    private fun buildSbnJson(sbn: StatusBarNotification): JSONObject =
        JSONObject()
            .apply {
                put("key", sbn.key ?: "")
                put("id", sbn.id)
                put("tag", sbn.tag ?: "")
                put("packageName", sbn.packageName)
                put("postTime", sbn.postTime)
                put("isOngoing", sbn.isOngoing)
                put("isClearable", sbn.isClearable)
                put("groupKey", sbn.groupKey ?: "")
                put("overrideGroupKey", sbn.overrideGroupKey ?: "")
                put("user", sbn.user?.toString() ?: "")
            }

    // ==================== Notification ====================

    private fun buildNotificationJson(
        context: Context,
        notification: Notification,
        includeBinary: Boolean,
    ): JSONObject =
        JSONObject()
            .apply {
                put("when", notification.`when`)
                put("flags", notification.flags)
                put("flagsDecoded", JSONArray(decodeFlags(notification.flags)))
                put("category", notification.category ?: "")
                put("channelId", notification.channelId ?: "")
                put("color", notification.color)
                put("number", notification.number)
                put("tickerText", notification.tickerText?.toString() ?: "")
                put("visibility", notification.visibility)
                put("sortKey", notification.sortKey ?: "")
                put("group", notification.group ?: "")
                put("shortcutId", notification.shortcutId ?: "")
                put("isGroupSummary", notification.flags and Notification.FLAG_GROUP_SUMMARY != 0)
                put("isForegroundService", notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0)
                // 以下 4 个字段在 API 26 起标记废弃（分别由 NotificationChannel 的声音/灯光配置与
                // 自定义样式取代），但**全字段转储必须原样呈现**，且没有等价替代 getter，故保留。
                put("audioAttributes", notification.audioAttributes?.toString() ?: "")
                put("contentIntent", describeValue(context, notification.contentIntent, includeBinary, 0))
                put("deleteIntent", describeValue(context, notification.deleteIntent, includeBinary, 0))
                put("fullScreenIntent", describeValue(context, notification.fullScreenIntent, includeBinary, 0))
                put("contentView", describeValue(context, notification.contentView, includeBinary, 0))
                put("bigContentView", describeValue(context, notification.bigContentView, includeBinary, 0))
                put("headsUpContentView", describeValue(context, notification.headsUpContentView, includeBinary, 0))
                put("actions", buildActionsJson(context, notification.actions, includeBinary))
                put("extras", buildBundleJson(context, notification.extras, includeBinary, 0))
            }

    /**
     * 解码 `Notification.flags` 中**未废弃**的标志位名，便于人工判读。
     *
     * 刻意不含 `FLAG_SHOW_LIGHTS` / `FLAG_HIGH_PRIORITY`：二者自 API 26 起废弃且仅用于推导展示，
     * 原始 `flags` 整数值已完整输出，无需为它们引入新的废弃 API 警告。
     */
    private fun decodeFlags(flags: Int): List<String> {
        val known =
            listOf(
                Notification.FLAG_ONGOING_EVENT to "ONGOING_EVENT",
                Notification.FLAG_INSISTENT to "INSISTENT",
                Notification.FLAG_ONLY_ALERT_ONCE to "ONLY_ALERT_ONCE",
                Notification.FLAG_AUTO_CANCEL to "AUTO_CANCEL",
                Notification.FLAG_NO_CLEAR to "NO_CLEAR",
                Notification.FLAG_FOREGROUND_SERVICE to "FOREGROUND_SERVICE",
                Notification.FLAG_LOCAL_ONLY to "LOCAL_ONLY",
                Notification.FLAG_GROUP_SUMMARY to "GROUP_SUMMARY",
                Notification.FLAG_BUBBLE to "BUBBLE",
            )
        return known.filter { flags and it.first != 0 }.map { it.second }
    }

    private fun buildActionsJson(
        context: Context,
        actions: Array<Notification.Action>?,
        includeBinary: Boolean,
    ): JSONArray {
        val array = JSONArray()
        actions?.forEach { action ->
            array.put(
                JSONObject()
                    .apply {
                        put("title", action.title?.toString() ?: "")
                        // 取 Icon 对象而非已废弃的 int icon 字段（后者无额外信息）
                        put("icon", describeValue(context, action.getIcon(), includeBinary, 0))
                        put("actionIntent", describeValue(context, action.actionIntent, includeBinary, 0))
                        put("semanticAction", action.semanticAction)
                        put("isContextual", action.isContextual)
                        put("allowGeneratedReplies", action.allowGeneratedReplies)
                        put("remoteInputs", describeValue(context, action.remoteInputs, includeBinary, 0))
                        put("extras", buildBundleJson(context, action.extras, includeBinary, 0))
                    },
            )
        }
        return array
    }

    // ==================== Bundle 递归 ====================

    private fun buildBundleJson(
        context: Context,
        bundle: Bundle?,
        includeBinary: Boolean,
        depth: Int,
    ): JSONObject {
        val json = JSONObject()
        if (bundle == null) return json
        if (depth >= MAX_DEPTH) {
            json.put("<truncated>", "超过最大递归深度 $MAX_DEPTH")
            return json
        }
        // Bundle.get(String) 在 API 33 起废弃，但枚举未知类型 extras 无等价替代（见类注释）
        @Suppress("DEPRECATION")
        bundle.keySet().forEach { key ->
            val value =
                try {
                    bundle.get(key)
                } catch (e: Exception) {
                    "<读取失败: ${e.javaClass.simpleName}>"
                }
            json.put(key, describeValue(context, value, includeBinary, depth + 1))
        }
        return json
    }

    /**
     * 把任意 extras / 字段值转成 JSON 可承载的表示。
     *
     * 返回值类型：`JSONObject.NULL` / String / JSONObject / JSONArray / Boolean / Int / Long / Double。
     */
    private fun describeValue(
        context: Context,
        value: Any?,
        includeBinary: Boolean,
        depth: Int,
    ): Any {
        if (value == null) return JSONObject.NULL
        if (depth > MAX_DEPTH) return "<超过最大递归深度 $MAX_DEPTH>"
        return when (value) {
            is String -> value
            is CharSequence -> value.toString()
            is Boolean, is Int, is Long, is Double -> value
            is Float -> value.toDouble()
            is Byte, is Short -> value.toInt()
            is Bundle -> buildBundleJson(context, value, includeBinary, depth)
            is Bitmap -> describeBitmap(value, includeBinary)
            is Icon -> describeIcon(context, value, includeBinary)
            is PendingIntent -> describePendingIntent(value)
            is RemoteViews -> describeRemoteViews(value)
            is ByteArray -> describeBytes(value, includeBinary)
            is IntArray -> JSONArray(value.toList())
            is LongArray -> JSONArray(value.toList())
            is FloatArray -> JSONArray(value.map { it.toDouble() })
            is DoubleArray -> JSONArray(value.toList())
            is BooleanArray -> JSONArray(value.toList())
            is CharArray -> JSONArray(value.map { it.toString() })
            is ShortArray -> JSONArray(value.map { it.toInt() })
            is Array<*> -> JSONArray(value.map { describeValue(context, it, includeBinary, depth + 1) })
            is Collection<*> -> JSONArray(value.map { describeValue(context, it, includeBinary, depth + 1) })
            is Parcelable -> "<${value.javaClass.name}: $value>"
            else -> "<${value.javaClass.name}: $value>"
        }
    }

    private fun describeBitmap(
        bitmap: Bitmap,
        includeBinary: Boolean,
    ): String =
        if (includeBinary) {
            ImageUtils.bitmapToDataUri(bitmap).ifEmpty { "<Bitmap 编码失败 ${bitmap.width}x${bitmap.height}>" }
        } else {
            "<Bitmap ${bitmap.width}x${bitmap.height} ${bitmap.config}>"
        }

    private fun describeIcon(
        context: Context,
        icon: Icon,
        includeBinary: Boolean,
    ): String {
        val header = "<Icon type=${icon.type} res=${icon.resId} pkg=${icon.resPackage}>"
        if (!includeBinary) return header
        return try {
            val drawable = icon.loadDrawable(context) ?: return header
            val bitmap =
                if (drawable is BitmapDrawable) {
                    drawable.bitmap
                } else {
                    drawable.toBitmapOrDefault(96)
                }
            ImageUtils.bitmapToDataUri(bitmap).ifEmpty { header }
        } catch (e: Exception) {
            "$header (加载失败: ${e.javaClass.simpleName})"
        }
    }

    private fun describePendingIntent(pendingIntent: PendingIntent): String =
        "<PendingIntent creator=${pendingIntent.creatorPackage} activity=${pendingIntent.isActivity} " +
            "service=${pendingIntent.isService} broadcast=${pendingIntent.isBroadcast} $pendingIntent>"

    private fun describeRemoteViews(remoteViews: RemoteViews): String = "<RemoteViews pkg=${remoteViews.`package`} layoutId=${remoteViews.layoutId}>"

    private fun describeBytes(
        bytes: ByteArray,
        includeBinary: Boolean,
    ): String =
        if (includeBinary) {
            ImageUtils.bytesToDataUrl(bytes, "application/octet-stream")
        } else {
            "<ByteArray ${bytes.size} bytes>"
        }

    // ==================== 文本渲染 ====================

    private fun appendJsonBlock(
        builder: StringBuilder,
        json: JSONObject,
        depth: Int,
    ) {
        for (key in json.keys()) {
            appendValue(builder, key, json.opt(key), depth)
        }
    }

    private fun appendValue(
        builder: StringBuilder,
        label: String,
        value: Any?,
        depth: Int,
    ) {
        val indent = INDENT.repeat(depth)
        when (value) {
            null, JSONObject.NULL -> builder.append(indent).append(label).append(": null\n")
            is JSONObject -> {
                builder.append(indent).append(label).append(":\n")
                appendJsonBlock(builder, value, depth + 1)
            }
            is JSONArray -> {
                builder.append(indent).append(label).append(":\n")
                for (index in 0 until value.length()) {
                    appendValue(builder, "[$index]", value.opt(index), depth + 1)
                }
            }
            is String -> {
                val nested = parseNestedJson(value)
                if (nested != null) {
                    builder.append(indent).append(label).append(": (JSON)\n")
                    appendJsonBlock(builder, nested, depth + 1)
                } else {
                    builder
                        .append(indent)
                        .append(label)
                        .append(": ")
                        .append(value)
                        .append('\n')
                }
            }
            else ->
                builder
                    .append(indent)
                    .append(label)
                    .append(": ")
                    .append(value.toString())
                    .append('\n')
        }
    }

    /**
     * 字符串值本身是 JSON（如 `miui.focus.param`）时解析为对象，供文本渲染展开为嵌套块；
     * 不是 JSON 时返回 null，按普通字符串输出。
     */
    private fun parseNestedJson(text: String): JSONObject? {
        val trimmed = text.trim()
        if (trimmed.length < 2 || trimmed.first() != '{' || trimmed.last() != '}') return null
        return try {
            JSONObject(trimmed)
        } catch (_: Exception) {
            null
        }
    }
}
