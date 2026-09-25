package com.xzyht.notifyrelay.ui.devtools

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.SparseArray
import android.widget.RemoteViews
import notifyrelay.base.util.image.ImageUtils
import notifyrelay.base.util.image.toBitmapOrDefault
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Array as ReflectArray

internal object NotificationFieldDump {
    private const val INDENT = "  "

    private const val MAX_DEPTH = 12

    private const val MAX_ITEMS = 200

    private const val MAX_FIELDS = 4000

    private const val MAX_TRUNCATION_EVENTS = 100

    /** 这些类型不下钻，避免牵出进程级对象图。 */
    private val SKIPPED_VALUE_TYPES =
        setOf(
            "android.content.Context",
            "android.content.res.Resources",
            "android.os.Handler",
            "android.os.Looper",
            "android.view.View",
            "android.view.ViewGroup",
            "java.lang.Class",
            "java.lang.ClassLoader",
        )

    private var fieldBudget = MAX_FIELDS

    private val truncations = mutableListOf<JSONObject>()

    /** 超出 [MAX_TRUNCATION_EVENTS] 未逐条记录的截断数。 */
    private var truncationsOmitted = 0

    /** 遍历路径栈，供截断事件定位。 */
    private val pathStack = ArrayDeque<String>()

    /** 当前路径的可读形式。 */
    private fun currentPath(): String = if (pathStack.isEmpty()) "<root>" else pathStack.joinToString(".")

    /** 数组 / 集合被截断时插入的标记元素（`JSONArray` 只有单参数 `put`）。 */
    private fun truncationMarker(detail: String): JSONObject =
        JSONObject().apply {
            put("__truncated", detail)
        }

    /** 记录一次截断 / 跳过；输出会据此在开头给出显式警告。 */
    private fun recordTruncation(
        reason: String,
        detail: String,
    ) {
        if (truncations.size >= MAX_TRUNCATION_EVENTS) {
            truncationsOmitted++
            return
        }
        truncations +=
            JSONObject().apply {
                put("path", currentPath())
                put("reason", reason)
                put("detail", detail)
            }
    }

    /** 在 [block] 执行期间把 [segment] 压入路径栈。 */
    private inline fun <T> withPath(
        segment: String,
        block: () -> T,
    ): T {
        pathStack.addLast(segment)
        try {
            return block()
        } finally {
            pathStack.removeLast()
        }
    }

    /**
     * 构造一条通知的完整字段 JSON 树。
     *
     * 单例上的 [fieldBudget] / [truncations] / [pathStack] 是遍历期共享状态，故整体加锁。
     */
    @Synchronized
    fun buildDumpJson(
        context: Context,
        sbn: StatusBarNotification,
        includeBinary: Boolean,
        ranking: NotificationListenerService.Ranking? = null,
    ): JSONObject {
        fieldBudget = MAX_FIELDS
        truncations.clear()
        truncationsOmitted = 0
        pathStack.clear()

        val root = JSONObject()
        root.put("dumpTime", System.currentTimeMillis())
        root.put("includeBinary", includeBinary)
        root.put("statusBarNotification", reflectObject(context, sbn, includeBinary, 0, HashSet()))
        ranking?.let { root.put("ranking", reflectObject(context, it, includeBinary, 0, HashSet())) }

        val truncated = truncations.isNotEmpty() || truncationsOmitted > 0
        root.put("truncated", truncated)
        if (truncated) {
            root.put(
                "truncationSummary",
                "本次转储不完整：有 ${truncations.size + truncationsOmitted} 处触及上限，已省略部分字段",
            )
            root.put("truncations", JSONArray(truncations))
            if (truncationsOmitted > 0) root.put("truncationsOmitted", truncationsOmitted)
        }
        return root
    }

    /** 把字段树渲染为可读文本；已截断时开头输出警告与截断位置清单。 */
    fun renderDumpText(root: JSONObject): String =
        buildString {
            if (root.optBoolean("truncated", false)) {
                appendLine("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
                appendLine("!! 本次转储被截断，以下内容不完整，请勿当作全量数据 !!")
                appendLine("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
                root
                    .optString("truncationSummary")
                    .takeIf { it.isNotBlank() }
                    ?.let { appendLine(it) }
                root.optJSONArray("truncations")?.let { array ->
                    for (i in 0 until array.length()) {
                        val event = array.optJSONObject(i) ?: continue
                        appendLine("  - ${event.optString("path")}: ${event.optString("reason")} (${event.optString("detail")})")
                    }
                }
                root
                    .optInt("truncationsOmitted", 0)
                    .takeIf { it > 0 }
                    ?.let { appendLine("  ...另有 $it 处截断未逐条列出") }
                appendLine("--------------------------------------------")
            }
            appendJsonBlock(this, root, 0)
        }.trimEnd()

    // ==================== 遍历核心 ====================

    /**
     * 展开 [target]：先遍历公开 getter，再补充公开实例字段。
     *
     * hidden API 限制会在 `Class.getDeclaredFields()` 层剔除全部非 SDK 字段，
     * 而 `StatusBarNotification` / `Ranking` 在 SDK 中除静态常量外没有 public 实例字段，
     * 故必须以不受该限制的公开方法为骨架。
     */
    private fun reflectObject(
        context: Context,
        target: Any,
        includeBinary: Boolean,
        depth: Int,
        visited: MutableSet<Int>,
    ): Any {
        if (depth > MAX_DEPTH) {
            recordTruncation("超过最大递归深度", "MAX_DEPTH=$MAX_DEPTH，类型=${target.javaClass.name}")
            return "<超过最大递归深度 $MAX_DEPTH>"
        }
        val typeName = target.javaClass.name
        if (typeName in SKIPPED_VALUE_TYPES) {
            recordTruncation("类型不下钻", typeName)
            return "<$typeName 已跳过>"
        }

        val identity = System.identityHashCode(target)
        if (!visited.add(identity)) {
            recordTruncation("循环引用", target.javaClass.name)
            return "<循环引用 ${target.javaClass.simpleName}>"
        }
        try {
            val json = JSONObject()
            json.put("__class", typeName)

            // 1) 公开 getter
            var getterCount = 0
            for (method in publicGetters(target.javaClass)) {
                if (fieldBudget <= 0) {
                    recordTruncation("字段总数超上限", "MAX_FIELDS=$MAX_FIELDS，方法 ${method.name} 起未输出")
                    json.put("__truncated", "字段数超过上限 $MAX_FIELDS")
                    break
                }
                fieldBudget--
                getterCount++
                val name = getterName(method.name)
                val value =
                    try {
                        method.invoke(target)
                    } catch (e: Exception) {
                        recordTruncation("getter 调用失败", "${method.name}: ${e.javaClass.simpleName}")
                        "<调用失败: ${e.javaClass.simpleName}>"
                    }
                json.put(name, withPath(name) { describeValue(context, value, includeBinary, depth + 1, visited) })
            }

            // 2) 公开实例字段补充
            var fieldCount = 0
            for (field in publicInstanceFields(target.javaClass)) {
                if (fieldBudget <= 0) break
                val name = field.name
                if (json.has(name)) continue
                fieldBudget--
                fieldCount++
                val value =
                    try {
                        field.get(target)
                    } catch (e: Exception) {
                        recordTruncation("字段读取失败", "$name: ${e.javaClass.simpleName}")
                        "<读取失败: ${e.javaClass.simpleName}>"
                    }
                json.put(name, withPath(name) { describeValue(context, value, includeBinary, depth + 1, visited) })
            }

            if (getterCount == 0 && fieldCount == 0) {
                recordTruncation("无可读字段", typeName)
                json.put("__note", "无公开 getter 与公开实例字段")
            }
            return json
        } finally {
            visited.remove(identity)
        }
    }

    /**
     * 取类的公开无参 getter（`getXxx()` / 返回 boolean 的 `isXxx()`），含继承链。
     *
     * `isXxx` 限定返回 boolean，避免把 `islandOrder()` 这类普通方法误当字段。
     */
    private fun publicGetters(clazz: Class<*>): List<Method> {
        val result = mutableListOf<Method>()
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            for (method in current.declaredMethods) {
                if (!Modifier.isPublic(method.modifiers)) continue
                if (Modifier.isStatic(method.modifiers)) continue
                if (method.parameterCount != 0) continue
                if (method.returnType == Void.TYPE) continue
                val name = method.name
                val isGet = name.startsWith("get") && name.length > 3 && name[3].isUpperCase()
                val returnsBoolean = method.returnType.name == "boolean" || method.returnType.name == "java.lang.Boolean"
                val isIs =
                    name.startsWith("is") && name.length > 2 && name[2].isUpperCase() && returnsBoolean
                if (!isGet && !isIs) continue
                if (name == "getClass") continue
                result += method
            }
            current = current.superclass
        }
        return result.distinctBy { it.name }
    }

    /** `getFooBar` / `isFooBar` → `fooBar`。 */
    private fun getterName(methodName: String): String {
        val raw = if (methodName.startsWith("is")) methodName.substring(2) else methodName.substring(3)
        return raw.replaceFirstChar { it.lowercaseChar() }
    }

    /** 取类的公开实例字段（含继承链）。 */
    private fun publicInstanceFields(clazz: Class<*>): List<Field> {
        val result = mutableListOf<Field>()
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            for (field in current.declaredFields) {
                if (!Modifier.isPublic(field.modifiers)) continue
                if (Modifier.isStatic(field.modifiers)) continue
                result += field
            }
            current = current.superclass
        }
        return result
    }

    /** 把任意值转成 JSON 可承载的表示；未知对象继续递归。 */
    private fun describeValue(
        context: Context,
        value: Any?,
        includeBinary: Boolean,
        depth: Int,
        visited: MutableSet<Int>,
    ): Any {
        if (value == null) return JSONObject.NULL
        if (depth > MAX_DEPTH) {
            recordTruncation("超过最大递归深度", "MAX_DEPTH=$MAX_DEPTH")
            return "<超过最大递归深度 $MAX_DEPTH>"
        }
        return when (value) {
            is String -> value
            is CharSequence -> value.toString()
            is Boolean, is Int, is Long, is Double -> value
            is Float -> value.toDouble()
            is Byte, is Short -> value.toInt()
            is Enum<*> -> value.name
            is Bundle -> reflectBundle(context, value, includeBinary, depth, visited)
            is SparseArray<*> -> reflectSparseArray(context, value, includeBinary, depth, visited)
            is Bitmap -> describeBitmap(value, includeBinary)
            is Icon -> describeIcon(context, value, includeBinary)
            is PendingIntent -> describePendingIntent(value)
            is RemoteViews -> describeRemoteViews(value)
            is Drawable -> describeDrawable(value, includeBinary)
            is ByteArray -> describeBytes(value, includeBinary)
            is IntArray -> JSONArray(value.toList())
            is LongArray -> JSONArray(value.toList())
            is FloatArray -> JSONArray(value.map { it.toDouble() })
            is DoubleArray -> JSONArray(value.toList())
            is BooleanArray -> JSONArray(value.toList())
            is CharArray -> JSONArray(value.map { it.toString() })
            is ShortArray -> JSONArray(value.map { it.toInt() })
            is Array<*> -> reflectIterable(context, value.toList(), includeBinary, depth, visited)
            is Collection<*> -> reflectIterable(context, value, includeBinary, depth, visited)
            is Map<*, *> -> reflectMap(context, value, includeBinary, depth, visited)
            else -> {
                val clazz = value.javaClass
                if (clazz.isPrimitive || clazz.name.startsWith("java.lang.")) {
                    value.toString()
                } else if (clazz.isArray) {
                    val length = ReflectArray.getLength(value)
                    JSONArray().apply {
                        val limit = minOf(length, MAX_ITEMS)
                        for (i in 0 until limit) {
                            put(describeValue(context, ReflectArray.get(value, i), includeBinary, depth + 1, visited))
                        }
                        if (length > limit) {
                            recordTruncation("数组元素超上限", "共 $length 项，仅输出 $limit 项")
                            put(truncationMarker("共 $length 项，仅输出 $limit 项"))
                        }
                    }
                } else {
                    reflectObject(context, value, includeBinary, depth, visited)
                }
            }
        }
    }

    private fun reflectIterable(
        context: Context,
        items: Collection<*>,
        includeBinary: Boolean,
        depth: Int,
        visited: MutableSet<Int>,
    ): JSONArray =
        JSONArray().apply {
            val limit = minOf(items.size, MAX_ITEMS)
            items.take(limit).forEach { item ->
                put(describeValue(context, item, includeBinary, depth + 1, visited))
            }
            if (items.size > limit) {
                recordTruncation("集合元素超上限", "共 ${items.size} 项，仅输出 $limit 项")
                put(truncationMarker("共 ${items.size} 项，仅输出 $limit 项"))
            }
        }

    private fun reflectMap(
        context: Context,
        map: Map<*, *>,
        includeBinary: Boolean,
        depth: Int,
        visited: MutableSet<Int>,
    ): JSONObject =
        JSONObject().apply {
            val limit = minOf(map.size, MAX_ITEMS)
            map.entries.take(limit).forEach { (k, v) ->
                put(
                    k.toString(),
                    withPath(k.toString()) { describeValue(context, v, includeBinary, depth + 1, visited) },
                )
            }
            if (map.size > limit) {
                recordTruncation("Map 条目超上限", "共 ${map.size} 项，仅输出 $limit 项")
                put("<truncated>", "共 ${map.size} 项，仅输出 $limit 项")
            }
        }

    private fun reflectSparseArray(
        context: Context,
        array: SparseArray<*>,
        includeBinary: Boolean,
        depth: Int,
        visited: MutableSet<Int>,
    ): JSONObject =
        JSONObject().apply {
            val size = array.size()
            val limit = minOf(size, MAX_ITEMS)
            for (i in 0 until limit) {
                val key = array.keyAt(i).toString()
                put(
                    key,
                    withPath(key) { describeValue(context, array.valueAt(i), includeBinary, depth + 1, visited) },
                )
            }
            if (size > limit) {
                recordTruncation("SparseArray 元素超上限", "共 $size 项，仅输出 $limit 项")
                put("<truncated>", "共 $size 项，仅输出 $limit 项")
            }
        }

    /** Bundle 逐键展开；`get` / `keySet` 自 API 33 废弃，但枚举未知类型 extras 无等价替代。 */
    @Suppress("DEPRECATION")
    private fun reflectBundle(
        context: Context,
        bundle: Bundle,
        includeBinary: Boolean,
        depth: Int,
        visited: MutableSet<Int>,
    ): JSONObject {
        val json = JSONObject()
        val keys =
            try {
                bundle.keySet()
            } catch (e: Exception) {
                emptySet<String>()
            }
        if (keys.isEmpty()) {
            json.put("__note", "空 Bundle")
            return json
        }
        val limit = minOf(keys.size, MAX_ITEMS)
        keys.take(limit).forEach { key ->
            val value =
                try {
                    bundle.get(key)
                } catch (e: Exception) {
                    recordTruncation("Bundle 取值失败", "$key: ${e.javaClass.simpleName}")
                    "<读取失败: ${e.javaClass.simpleName}>"
                }
            json.put(
                key,
                try {
                    withPath(key) { describeValue(context, value, includeBinary, depth + 1, visited) }
                } catch (e: Exception) {
                    recordTruncation("Bundle 描述失败", "$key: ${e.javaClass.simpleName}")
                    "<描述失败(${e.javaClass.simpleName}): ${e.message}>"
                },
            )
        }
        if (keys.size > limit) {
            recordTruncation("Bundle 键数超上限", "共 ${keys.size} 键，仅输出 $limit 键")
            json.put("<truncated>", "共 ${keys.size} 键，仅输出 $limit 键")
        }
        return json
    }

    // ==================== 叶子类型语义化描述 ====================

    private fun describeBitmap(
        bitmap: Bitmap,
        includeBinary: Boolean,
    ): String =
        if (includeBinary) {
            ImageUtils.bitmapToDataUri(bitmap).ifEmpty { "<Bitmap 编码失败 ${bitmap.width}x${bitmap.height}>" }
        } else {
            "<Bitmap ${bitmap.width}x${bitmap.height} ${bitmap.config}>"
        }

    /** `getResId` / `getResPackage` 仅 `TYPE_RESOURCE` 合法，`getUri` 仅 URI 类型合法。 */
    private fun describeIcon(
        context: Context,
        icon: Icon,
        includeBinary: Boolean,
    ): String {
        val header =
            when (icon.type) {
                Icon.TYPE_RESOURCE -> "<Icon type=RESOURCE res=${icon.resId} pkg=${icon.resPackage}>"
                Icon.TYPE_URI, Icon.TYPE_URI_ADAPTIVE_BITMAP -> "<Icon type=URI uri=${icon.uri}>"
                Icon.TYPE_BITMAP, Icon.TYPE_ADAPTIVE_BITMAP -> "<Icon type=BITMAP>"
                Icon.TYPE_DATA -> "<Icon type=DATA>"
                else -> "<Icon type=${icon.type}>"
            }
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

    private fun describeDrawable(
        drawable: Drawable,
        includeBinary: Boolean,
    ): String =
        if (!includeBinary) {
            "<Drawable ${drawable.javaClass.name} intrinsic=${drawable.intrinsicWidth}x${drawable.intrinsicHeight}>"
        } else {
            try {
                val bitmap =
                    if (drawable is BitmapDrawable) {
                        drawable.bitmap
                    } else {
                        drawable.toBitmapOrDefault(96)
                    }
                ImageUtils.bitmapToDataUri(bitmap).ifEmpty { "<Drawable 编码失败>" }
            } catch (e: Exception) {
                "<Drawable 编码异常: ${e.javaClass.simpleName}>"
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
