package github.xzynine.superislandui.builder

import github.xzynine.superislandui.json.optObjectOrNull
import github.xzynine.superislandui.json.optStringOr
import github.xzynine.superislandui.json.toOrgJsonString
import github.xzynine.superislandui.model.components.ActionInfo
import github.xzynine.superislandui.model.components.AnimTextInfo
import github.xzynine.superislandui.model.components.MultiProgressInfo
import github.xzynine.superislandui.model.components.ProgressInfo
import github.xzynine.superislandui.model.components.TextButton
import github.xzynine.superislandui.model.templates.BaseInfo
import github.xzynine.superislandui.model.templates.ChatInfo
import github.xzynine.superislandui.model.templates.CoverInfo
import github.xzynine.superislandui.model.templates.HighlightInfo
import github.xzynine.superislandui.model.templates.HighlightInfoV3
import github.xzynine.superislandui.model.templates.HintInfo
import github.xzynine.superislandui.model.templates.IconTextInfo
import github.xzynine.superislandui.model.templates.PicInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * 超级岛通知参数约束构建器（**分阶段类型化**）。
 *
 * 设计目标：把「必传项」从**运行时报错**提升为**编译期报错**。
 * 通过类型阶段串联，未设置必传字段前无法调用 [Complete.build]：
 *
 * ```
 * // 编译期强制：business -> param_island -> 才能 build
 * val payload = SuperIslandParamBuilder
 *     .business("music")            // 必传 1：运营场景
 *     .island {                     // 必传 2：岛数据（大岛/小岛内容）
 *         bigIslandArea(bigJson)
 *         smallIslandArea(smallJson)
 *     }
 *     .ticker("标题")                // 可选字段
 *     .build()
 * ```
 *
 * 若缺少 [business] 或 [island]，代码**无法通过编译**（方法不存在），而非运行期才返回问题列表。
 * 产出结构恒为合规的 `{"type"?: "...", "param_v2": {...}}`。
 *
 * 内部以 `LinkedHashMap<String, JsonElement>` 保存 `param_v2` 与 `param_island`，与 Android 内置
 * JSONObject 一致采用插入序、同键覆盖保持首次位置；输出文本经 [toOrgJsonString] 与运行时逐字节一致。
 */
class SuperIslandParamBuilder private constructor() {
    companion object {
        /** FocusTemplate V3 序列化标识（对齐 Xiaomi-SuperIsland-Playground） */
        const val FOCUS_V3_TYPE =
            "com.xzakota.hyper.notification.focus.FocusNotification.FocusTemplateFactory.V3"

        /**
         * 构建入口：`business`（运营场景）为必传项 —— 编译期强制。
         * @return 阶段对象 [WithBusiness]，在提供 [WithBusiness.island] 前无法构建
         */
        fun business(value: String): WithBusiness {
            require(value.isNotBlank()) { "business 不能为空白" }
            return WithBusiness(
                paramV2 = linkedMapOf("business" to JsonPrimitive(value)),
                outerType = null,
            )
        }

        /**
         * 复刻入口：从已有 `miui.focus.param` / 裸 `param_v2` 提取。
         * `business` 仍需显式提供（编译期强制）—— 复刻同样必须明确运营场景；
         * `param_island` 在下一步 [WithBusiness.island] 强制。
         *
         * @param raw 原始 `miui.focus.param` 或裸 param_v2（可为包裹结构）
         * @param business 运营场景（必传）
         */
        fun replica(
            raw: String?,
            business: String,
        ): WithBusiness {
            val base = extractInnerParamV2(raw)
            base["business"] = JsonPrimitive(business)
            return WithBusiness(paramV2 = base, outerType = extractOuterType(raw))
        }

        /**
         * 诊断用：校验任意 `miui.focus.param` / `param_v2` JSON 的必传项，返回问题列表（空表示合规）。
         * 仅用于日志/排查；合规性主要由类型阶段在编译期保证。
         */
        fun validate(raw: String?): List<String> {
            if (raw.isNullOrBlank()) return listOf("param_v2 为空")
            return try {
                val json = parseJsonObject(raw)
                val inner = json.optObjectOrNull("param_v2") ?: json
                buildList {
                    if (inner.optStringOr("business").isBlank()) add("缺少必传字段 business（运营场景）")
                    if (!inner.containsKey("protocol")) add("缺少 protocol（建议默认 1）")
                    val island = inner.optObjectOrNull("param_island")
                    if (island == null) {
                        add("缺少必传 param_island（岛数据）")
                    } else {
                        if (!island.containsKey("bigIslandArea")) add("param_island 缺少必传 bigIslandArea（大岛内容）")
                        if (!island.containsKey("smallIslandArea")) add("param_island 缺少必传 smallIslandArea（小岛内容）")
                    }
                }
            } catch (e: Exception) {
                listOf("param_v2 解析失败: " + e.message)
            }
        }

        /** 从原始 param 中提取 `business`（复刻时透传；缺省用 [fallback]） */
        fun businessOf(
            raw: String?,
            fallback: String = "replica",
        ): String = JsonObject(extractInnerParamV2(raw)).optStringOr("business").takeIf { it.isNotBlank() } ?: fallback

        /** 从原始 param 中提取 `param_island`（复刻时透传；缺失返回空对象） */
        fun paramIslandOf(raw: String?): JsonObject = JsonObject(extractInnerParamV2(raw)).optObjectOrNull("param_island") ?: JsonObject(emptyMap())

        private fun parseJsonObject(raw: String): JsonObject = Json.parseToJsonElement(raw).jsonObject

        private fun extractInnerParamV2(raw: String?): LinkedHashMap<String, JsonElement> {
            if (raw.isNullOrBlank()) return LinkedHashMap()
            // 解析失败不静默返回空对象：让上游 replica() / businessOf() / paramIslandOf()
            // 抛到调用方 catch，落到 rawData 原始兜底，避免用只有默认值的载荷覆盖损坏数据。
            val json = parseJsonObject(raw)
            val inner = json.optObjectOrNull("param_v2") ?: json
            return LinkedHashMap(inner)
        }

        private fun extractOuterType(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            return try {
                parseJsonObject(raw).optStringOr("type").takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * 阶段 1：`business` 已就绪。
     * 必须提供 `param_island`（大岛/小岛内容）后才能进入 [Complete] 并构建 —— 编译期强制。
     */
    class WithBusiness internal constructor(
        private val paramV2: LinkedHashMap<String, JsonElement>,
        private val outerType: String?,
    ) {
        /** 设置岛数据（大岛/小岛内容）—— 必传 */
        fun island(json: JsonObject): Complete {
            paramV2["param_island"] = json
            return Complete(paramV2, outerType)
        }

        /** 以类型化模型设置岛数据 —— 必传 */
        fun island(spec: ParamIslandSpec): Complete = island(spec.toJson())

        /** 以构建块方式设置岛数据 —— 必传 */
        fun island(block: ParamIslandBuilder.() -> Unit): Complete {
            val existing = paramV2["param_island"] as? JsonObject
            val islandBuilder = ParamIslandBuilder(if (existing != null) LinkedHashMap(existing) else LinkedHashMap())
            islandBuilder.block()
            return island(islandBuilder.build())
        }
    }

    /**
     * 阶段 2：必传项（business + param_island）已齐备，可设置可选字段并构建。
     */
    class Complete internal constructor(
        private val paramV2: LinkedHashMap<String, JsonElement>,
        private var outerType: String?,
    ) {
        fun focusType(type: String?) = apply { outerType = type }

        fun protocol(value: Int) = apply { paramV2["protocol"] = JsonPrimitive(value) }

        fun ticker(value: String?) = apply { putString("ticker", value) }

        /** 仅当 ticker 为空时写入（复刻场景避免覆盖原值） */
        fun tickerIfBlank(value: String?) =
            apply {
                if (JsonObject(paramV2).optStringOr("ticker").isBlank()) putString("ticker", value)
            }

        fun tickerPic(value: String?) = apply { putString("tickerPic", value) }

        fun tickerPicDark(value: String?) = apply { putString("tickerPicDark", value) }

        fun aodTitle(value: String?) = apply { putString("aodTitle", value) }

        /** 仅当 aodTitle 为空时写入 */
        fun aodTitleIfBlank(value: String?) =
            apply {
                if (JsonObject(paramV2).optStringOr("aodTitle").isBlank()) putString("aodTitle", value)
            }

        fun aodPic(value: String?) = apply { putString("aodPic", value) }

        fun enableFloat(value: Boolean) = apply { paramV2["enableFloat"] = JsonPrimitive(value) }

        fun islandFirstFloat(value: Boolean) = apply { paramV2["islandFirstFloat"] = JsonPrimitive(value) }

        fun updatable(value: Boolean) = apply { paramV2["updatable"] = JsonPrimitive(value) }

        fun reopen(value: String) = apply { paramV2["reopen"] = JsonPrimitive(value) }

        fun timeout(minutes: Int) = apply { paramV2["timeout"] = JsonPrimitive(minutes) }

        fun extraInfo(json: JsonObject) = apply { paramV2["extraInfo"] = json }

        /** 写入任意模板组件（如 baseInfo / chatInfo / highlightInfo / actions ...） */
        fun component(
            key: String,
            value: JsonElement,
        ) = apply { paramV2[key] = value }

        // ---- 类型化组件写入（编译期类型安全，自动序列化为规范 JSON）----

        fun baseInfo(info: BaseInfo) = component("baseInfo", info.toJson())

        fun chatInfo(info: ChatInfo) = component("chatInfo", info.toJson())

        fun highlightInfo(info: HighlightInfo) = component("highlightInfo", info.toJson())

        fun picInfo(info: PicInfo) = component("picInfo", info.toJson())

        fun animTextInfo(info: AnimTextInfo) = component("animTextInfo", info.toJson())

        fun hintInfo(info: HintInfo) = component("hintInfo", info.toJson())

        fun textButton(info: TextButton) = component("textButton", info.toJson())

        fun iconTextInfo(info: IconTextInfo) = component("iconTextInfo", info.toJson())

        fun coverInfo(info: CoverInfo) = component("coverInfo", info.toJson())

        fun highlightInfoV3(info: HighlightInfoV3) = component("highlightInfoV3", info.toJson())

        fun progressInfo(info: ProgressInfo) = component("progressInfo", info.toJson())

        fun multiProgressInfo(info: MultiProgressInfo) = component("multiProgressInfo", info.toJson())

        fun actions(actions: List<ActionInfo>) = component("actions", actions.toJsonArray())

        /** 构建合规的 `miui.focus.param` JSON 字符串（自动补默认值） */
        fun build(): String {
            applyDefaults()
            val root = LinkedHashMap<String, JsonElement>()
            outerType?.let { root["type"] = JsonPrimitive(it) }
            root["param_v2"] = JsonObject(LinkedHashMap(paramV2))
            return JsonObject(root).toOrgJsonString()
        }

        /** 仅返回内层 param_v2（需要自行包裹时使用；文本输出请用 toOrgJsonString()） */
        fun buildParamV2(): JsonObject {
            applyDefaults()
            return JsonObject(LinkedHashMap(paramV2))
        }

        private fun applyDefaults() {
            if (!paramV2.containsKey("protocol")) paramV2["protocol"] = JsonPrimitive(1)
            if (!paramV2.containsKey("updatable")) paramV2["updatable"] = JsonPrimitive(true)
            if (!paramV2.containsKey("reopen")) paramV2["reopen"] = JsonPrimitive("close")
            if (!paramV2.containsKey("enableFloat")) paramV2["enableFloat"] = JsonPrimitive(false)
            if (!paramV2.containsKey("islandFirstFloat")) paramV2["islandFirstFloat"] = JsonPrimitive(false)
        }

        private fun putString(
            key: String,
            value: String?,
        ) {
            if (value != null) paramV2[key] = JsonPrimitive(value)
        }
    }
}

/**
 * `param_island` 构建块（岛数据）。
 * 规范要求其中至少包含 `bigIslandArea`（大岛内容）与 `smallIslandArea`（小岛内容）。
 */
class ParamIslandBuilder internal constructor(
    private val json: LinkedHashMap<String, JsonElement>,
) {
    fun islandProperty(value: Int) = apply { json["islandProperty"] = JsonPrimitive(value) }

    fun islandOrder(value: Boolean) = apply { json["islandOrder"] = JsonPrimitive(value) }

    fun islandTimeout(seconds: Int) = apply { json["islandTimeout"] = JsonPrimitive(seconds) }

    fun dismissIsland(value: Boolean) = apply { json["dismissIsland"] = JsonPrimitive(value) }

    fun highlightColor(value: String) = apply { json["highlightColor"] = JsonPrimitive(value) }

    fun bigIslandArea(area: JsonObject) = apply { json["bigIslandArea"] = area }

    fun smallIslandArea(area: JsonObject) = apply { json["smallIslandArea"] = area }

    fun shareData(value: JsonObject) = apply { json["shareData"] = value }

    /** 写入任意字段 */
    fun put(
        key: String,
        value: JsonElement,
    ) = apply { json[key] = value }

    fun build(): JsonObject = JsonObject(LinkedHashMap(json))
}
