package github.xzynine.superislandui.model.templates

import github.xzynine.superislandui.json.optBooleanOr
import github.xzynine.superislandui.json.optIntOr
import github.xzynine.superislandui.json.optStringOr
import kotlinx.serialization.json.JsonObject

// 数据类定义模板组件，使用分支结构避免重复定义
// 基础信息模板：文本组件1和2，用于显示主要文本、次要文本等
data class BaseInfo(
    val type: Int, // 文本组件类型：1文本组件1，2文本组件2
    val title: String? = null, // 主要文本1：关键信息
    val subTitle: String? = null, // 主要文本2：关键信息
    val extraTitle: String? = null, // 补充文本
    val specialTitle: String? = null, // 特殊标签文本
    val content: String? = null, // 次要文本1：前置描述
    val subContent: String? = null, // 次要文本2：前置描述
    val picFunction: String? = null, // 功能图标资源key
    val picFunctionDark: String? = null, // 深色模式功能图标
    val colorTitle: String? = null, // 主要文本1颜色
    val colorTitleDark: String? = null, // 深色模式主要文本1颜色
    val colorSubTitle: String? = null, // 主要文本2颜色
    val colorSubTitleDark: String? = null, // 深色模式主要文本2颜色
    val colorExtraTitle: String? = null, // 补充文本颜色
    val colorExtraTitleDark: String? = null, // 深色模式补充文本颜色
    val colorSpecialTitle: String? = null, // 特殊标签文本颜色
    val colorSpecialTitleDark: String? = null, // 深色模式特殊标签文本颜色
    val colorSpecialBg: String? = null, // 特殊标签背景色
    val colorContent: String? = null, // 次要文本1颜色
    val colorContentDark: String? = null, // 深色模式次要文本1颜色
    val colorSubContent: String? = null, // 次要文本2颜色
    val colorSubContentDark: String? = null, // 深色模式次要文本2颜色
    val showDivider: Boolean? = null, // 是否显示主要文本间分割符
    val showContentDivider: Boolean? = null, // 是否显示主要文本和补充文本分割符
)

// 解析基础信息组件（文本组件1和2）
fun parseBaseInfo(json: JsonObject): BaseInfo =
    BaseInfo(
        type = json.optIntOr("type", 1),
        title = json.optStringOr("title").takeIf { it.isNotEmpty() },
        subTitle = json.optStringOr("subTitle").takeIf { it.isNotEmpty() },
        extraTitle = json.optStringOr("extraTitle").takeIf { it.isNotEmpty() },
        specialTitle = json.optStringOr("specialTitle").takeIf { it.isNotEmpty() },
        content = json.optStringOr("content").takeIf { it.isNotEmpty() },
        subContent = json.optStringOr("subContent").takeIf { it.isNotEmpty() },
        picFunction = json.optStringOr("picFunction").takeIf { it.isNotEmpty() },
        picFunctionDark = json.optStringOr("picFunctionDark").takeIf { it.isNotEmpty() },
        colorTitle = json.optStringOr("colorTitle").takeIf { it.isNotEmpty() },
        colorTitleDark = json.optStringOr("colorTitleDark").takeIf { it.isNotEmpty() },
        colorSubTitle = json.optStringOr("colorSubTitle").takeIf { it.isNotEmpty() },
        colorSubTitleDark = json.optStringOr("colorSubTitleDark").takeIf { it.isNotEmpty() },
        colorExtraTitle = json.optStringOr("colorExtraTitle").takeIf { it.isNotEmpty() },
        colorExtraTitleDark = json.optStringOr("colorExtraTitleDark").takeIf { it.isNotEmpty() },
        colorSpecialTitle = json.optStringOr("colorSpecialTitle").takeIf { it.isNotEmpty() },
        colorSpecialTitleDark = json.optStringOr("colorSpecialTitleDark").takeIf { it.isNotEmpty() },
        colorSpecialBg = json.optStringOr("colorSpecialBg").takeIf { it.isNotEmpty() },
        colorContent = json.optStringOr("colorContent").takeIf { it.isNotEmpty() },
        colorContentDark = json.optStringOr("colorContentDark").takeIf { it.isNotEmpty() },
        colorSubContent = json.optStringOr("colorSubContent").takeIf { it.isNotEmpty() },
        colorSubContentDark = json.optStringOr("colorSubContentDark").takeIf { it.isNotEmpty() },
        showDivider = json.optBooleanOr("showDivider", false),
        showContentDivider = json.optBooleanOr("showContentDivider", false),
    )
