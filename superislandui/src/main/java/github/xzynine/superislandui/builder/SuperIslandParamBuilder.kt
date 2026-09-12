package github.xzynine.superislandui.builder

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
import org.json.JSONObject

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
        fun business(value: String): WithBusiness =
            WithBusiness(
                paramV2 = JSONObject().apply { put("business", value) },
                outerType = null,
            )

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
            base.put("business", business)
            return WithBusiness(paramV2 = base, outerType = extractOuterType(raw))
        }

        /**
         * 诊断用：校验任意 `miui.focus.param` / `param_v2` JSON 的必传项，返回问题列表（空表示合规）。
         * 仅用于日志/排查；合规性主要由类型阶段在编译期保证。
         */
        fun validate(raw: String?): List<String> {
            if (raw.isNullOrBlank()) return listOf("param_v2 为空")
            return try {
                val json = JSONObject(raw)
                val inner = json.optJSONObject("param_v2") ?: json
                buildList {
                    if (inner.optString("business").isBlank()) add("缺少必传字段 business（运营场景）")
                    if (!inner.has("protocol")) add("缺少 protocol（建议默认 1）")
                    val island = inner.optJSONObject("param_island")
                    if (island == null) {
                        add("缺少必传 param_island（岛数据）")
                    } else {
                        if (!island.has("bigIslandArea")) add("param_island 缺少必传 bigIslandArea（大岛内容）")
                        if (!island.has("smallIslandArea")) add("param_island 缺少必传 smallIslandArea（小岛内容）")
                    }
                }
            } catch (e: Exception) {
                listOf("param_v2 解析失败: ${e.message}")
            }
        }

        /** 从原始 param 中提取 `business`（复刻时透传；缺省用 [fallback]） */
        fun businessOf(
            raw: String?,
            fallback: String = "replica",
        ): String = extractInnerParamV2(raw).optString("business", "").takeIf { it.isNotBlank() } ?: fallback

        /** 从原始 param 中提取 `param_island`（复刻时透传；缺失返回空对象） */
        fun paramIslandOf(raw: String?): JSONObject = extractInnerParamV2(raw).optJSONObject("param_island") ?: JSONObject()

        private fun extractInnerParamV2(raw: String?): JSONObject {
            if (raw.isNullOrBlank()) return JSONObject()
            return try {
                val json = JSONObject(raw)
                json.optJSONObject("param_v2") ?: json
            } catch (e: Exception) {
                JSONObject()
            }
        }

        private fun extractOuterType(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            return try {
                JSONObject(raw).optString("type", "").takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 阶段 1：`business` 已就绪。
     * 必须提供 `param_island`（大岛/小岛内容）后才能进入 [Complete] 并构建 —— 编译期强制。
     */
    class WithBusiness internal constructor(
        private val paramV2: JSONObject,
        private val outerType: String?,
    ) {
        /** 设置岛数据（大岛/小岛内容）—— 必传 */
        fun island(json: JSONObject): Complete = Complete(paramV2.apply { put("param_island", json) }, outerType)

        /** 以类型化模型设置岛数据 —— 必传 */
        fun island(spec: ParamIslandSpec): Complete = island(spec.toJson())

        /** 以构建块方式设置岛数据 —— 必传 */
        fun island(block: ParamIslandBuilder.() -> Unit): Complete {
            val islandBuilder = ParamIslandBuilder(paramV2.optJSONObject("param_island") ?: JSONObject())
            islandBuilder.block()
            return Complete(paramV2.apply { put("param_island", islandBuilder.build()) }, outerType)
        }
    }

    /**
     * 阶段 2：必传项（business + param_island）已齐备，可设置可选字段并构建。
     */
    class Complete internal constructor(
        private val paramV2: JSONObject,
        private var outerType: String?,
    ) {
        fun focusType(type: String?) = apply { outerType = type }

        fun protocol(value: Int) = apply { paramV2.put("protocol", value) }

        fun ticker(value: String?) = apply { putString("ticker", value) }

        /** 仅当 ticker 为空时写入（复刻场景避免覆盖原值） */
        fun tickerIfBlank(value: String?) =
            apply {
                if (paramV2.optString("ticker").isBlank()) putString("ticker", value)
            }

        fun tickerPic(value: String?) = apply { putString("tickerPic", value) }

        fun tickerPicDark(value: String?) = apply { putString("tickerPicDark", value) }

        fun aodTitle(value: String?) = apply { putString("aodTitle", value) }

        /** 仅当 aodTitle 为空时写入 */
        fun aodTitleIfBlank(value: String?) =
            apply {
                if (paramV2.optString("aodTitle").isBlank()) putString("aodTitle", value)
            }

        fun aodPic(value: String?) = apply { putString("aodPic", value) }

        fun enableFloat(value: Boolean) = apply { paramV2.put("enableFloat", value) }

        fun islandFirstFloat(value: Boolean) = apply { paramV2.put("islandFirstFloat", value) }

        fun updatable(value: Boolean) = apply { paramV2.put("updatable", value) }

        fun reopen(value: String) = apply { paramV2.put("reopen", value) }

        fun timeout(minutes: Int) = apply { paramV2.put("timeout", minutes) }

        fun extraInfo(json: JSONObject) = apply { paramV2.put("extraInfo", json) }

        /** 写入任意模板组件（如 baseInfo / chatInfo / highlightInfo / actions ...） */
        fun component(
            key: String,
            value: Any,
        ) = apply { paramV2.put(key, value) }

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

        /** 直接修改内层 param_v2（高级用法，谨慎使用） */
        fun edit(block: (JSONObject) -> Unit) = apply { block(paramV2) }

        /** 构建合规的 `miui.focus.param` JSON 字符串（自动补默认值） */
        fun build(): String {
            applyDefaults()
            val root = JSONObject()
            outerType?.let { root.put("type", it) }
            root.put("param_v2", paramV2)
            return root.toString()
        }

        /** 仅返回内层 param_v2（需要自行包裹时使用） */
        fun buildParamV2(): JSONObject {
            applyDefaults()
            return paramV2
        }

        private fun applyDefaults() {
            if (!paramV2.has("protocol")) paramV2.put("protocol", 1)
            if (!paramV2.has("updatable")) paramV2.put("updatable", true)
            if (!paramV2.has("reopen")) paramV2.put("reopen", "close")
            if (!paramV2.has("enableFloat")) paramV2.put("enableFloat", false)
            if (!paramV2.has("islandFirstFloat")) paramV2.put("islandFirstFloat", false)
        }

        private fun putString(
            key: String,
            value: String?,
        ) {
            if (value != null) paramV2.put(key, value)
        }
    }
}

/**
 * `param_island` 构建块（岛数据）。
 * 规范要求其中至少包含 `bigIslandArea`（大岛内容）与 `smallIslandArea`（小岛内容）。
 */
class ParamIslandBuilder(
    private val json: JSONObject,
) {
    fun islandProperty(value: Int) = apply { json.put("islandProperty", value) }

    fun islandOrder(value: Boolean) = apply { json.put("islandOrder", value) }

    fun islandTimeout(seconds: Int) = apply { json.put("islandTimeout", seconds) }

    fun dismissIsland(value: Boolean) = apply { json.put("dismissIsland", value) }

    fun highlightColor(value: String) = apply { json.put("highlightColor", value) }

    fun bigIslandArea(area: JSONObject) = apply { json.put("bigIslandArea", area) }

    fun smallIslandArea(area: JSONObject) = apply { json.put("smallIslandArea", area) }

    fun shareData(value: JSONObject) = apply { json.put("shareData", value) }

    /** 写入任意字段 */
    fun put(
        key: String,
        value: Any?,
    ) = apply {
        if (value != null) json.put(key, value)
    }

    fun build(): JSONObject = json
}
