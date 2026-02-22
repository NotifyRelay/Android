package com.xzyht.notifyrelay.feature.notification.superisland.floating.BigIsland.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xzyht.notifyrelay.feature.notification.superisland.model.templates.BaseInfo
import com.xzyht.notifyrelay.feature.notification.superisland.floating.common.CommonImageCompose
import com.xzyht.notifyrelay.feature.notification.superisland.floating.common.SuperIslandImageUtil

/**
 * BaseInfo的Compose实现
 * 支持文本组件1（type=1）和文本组件2（type=2）
 */
@Composable
fun BaseInfoCompose(
    baseInfo: BaseInfo,
    picMap: Map<String, String>?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .wrapContentHeight()
    ) {
        // 根据type字段显示不同的布局结构
        if (baseInfo.type == 1) {
            // 文本组件1：次要文本在顶部，主要文本在底部
            // 次要文本区域（顶部）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .align(Alignment.Start),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 次要文本1：前置描述
                baseInfo.content?.let {
                    Text(
                        text = SuperIslandImageUtil.parseSimpleHtmlToAnnotatedString(it),
                        color = Color(SuperIslandImageUtil.parseColor(baseInfo.colorContent) ?: 0xFFDDDDDD.toInt()),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.wrapContentWidth()
                    )
                }

                // 次要文本2：前置描述
                baseInfo.subContent?.let {
                    if (baseInfo.content != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = SuperIslandImageUtil.parseSimpleHtmlToAnnotatedString(it),
                        color = Color(SuperIslandImageUtil.parseColor(baseInfo.colorSubContent) ?: 0xFFDDDDDD.toInt()),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.wrapContentWidth()
                    )
                }

                // 功能图标
                baseInfo.picFunction?.let {
                    if (baseInfo.content != null || baseInfo.subContent != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    CommonImageCompose(
                        picKey = it,
                        picMap = picMap,
                        size = 24.dp,
                        isFocusIcon = false,
                        contentDescription = null
                    )
                }
            }

            // 显示主要文本和补充文本的分割符
            if (baseInfo.showContentDivider == true && (baseInfo.content != null || baseInfo.subContent != null) && (baseInfo.title != null || baseInfo.subTitle != null || baseInfo.extraTitle != null || baseInfo.specialTitle != null)) {
                Spacer(modifier = Modifier.height(8.dp))
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(color = Color(0xFFDDDDDD))
                )
                Spacer(modifier = Modifier.height(8.dp))
            } else if ((baseInfo.content != null || baseInfo.subContent != null) && (baseInfo.title != null || baseInfo.subTitle != null || baseInfo.extraTitle != null || baseInfo.specialTitle != null)) {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 主要文本区域（底部）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .align(Alignment.Start),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 主要文本1：关键信息
                baseInfo.title?.let {
                    Text(
                        text = SuperIslandImageUtil.parseSimpleHtmlToAnnotatedString(it),
                        color = Color(SuperIslandImageUtil.parseColor(baseInfo.colorTitle) ?: 0xFFFFFFFF.toInt()),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.wrapContentWidth()
                    )
                }

                // 显示主要文本间的分割符
                if (baseInfo.showDivider == true && baseInfo.title != null && (baseInfo.subTitle != null || baseInfo.extraTitle != null || baseInfo.specialTitle != null)) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "|",
                        color = Color(0xFFDDDDDD),
                        fontSize = 14.sp,
                        modifier = Modifier.wrapContentWidth()
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // 主要文本2：关键信息
                baseInfo.subTitle?.let {
                    Text(
                        text = SuperIslandImageUtil.parseSimpleHtmlToAnnotatedString(it),
                        color = Color(SuperIslandImageUtil.parseColor(baseInfo.colorSubTitle) ?: 0xFFFFFFFF.toInt()),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.wrapContentWidth()
                    )
                }

                // 补充文本
                baseInfo.extraTitle?.let {
                    if (baseInfo.subTitle != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = SuperIslandImageUtil.parseSimpleHtmlToAnnotatedString(it),
                        color = Color(SuperIslandImageUtil.parseColor(baseInfo.colorExtraTitle) ?: 0xFFFFFFFF.toInt()),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.wrapContentWidth()
                    )
                }

                // 特殊标签
                baseInfo.specialTitle?.let {
                    if (baseInfo.extraTitle != null || baseInfo.subTitle != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = SuperIslandImageUtil.parseSimpleHtmlToAnnotatedString(it),
                        color = Color(SuperIslandImageUtil.parseColor(baseInfo.colorSpecialTitle) ?: 0xFFFFFFFF.toInt()),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .wrapContentWidth()
                            .background(
                                color = Color(SuperIslandImageUtil.parseColor(baseInfo.colorSpecialBg) ?: 0xFFDDDDDD.toInt()),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        } else {
            // 文本组件2：主要文本在顶部，次要文本在底部
            // 主要文本区域（顶部）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .align(Alignment.Start),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 主要文本1：关键信息
                baseInfo.title?.let {
                    Text(
                        text = SuperIslandImageUtil.parseSimpleHtmlToAnnotatedString(it),
                        color = Color(SuperIslandImageUtil.parseColor(baseInfo.colorTitle) ?: 0xFFFFFFFF.toInt()),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.wrapContentWidth()
                    )
                }

                // 显示主要文本间的分割符
                if (baseInfo.showDivider == true && baseInfo.title != null && (baseInfo.subTitle != null || baseInfo.extraTitle != null || baseInfo.specialTitle != null)) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "|",
                        color = Color(0xFFDDDDDD),
                        fontSize = 14.sp,
                        modifier = Modifier.wrapContentWidth()
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // 主要文本2：关键信息
                baseInfo.subTitle?.let {
                    Text(
                        text = SuperIslandImageUtil.parseSimpleHtmlToAnnotatedString(it),
                        color = Color(SuperIslandImageUtil.parseColor(baseInfo.colorSubTitle) ?: 0xFFFFFFFF.toInt()),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.wrapContentWidth()
                    )
                }

                // 补充文本
                baseInfo.extraTitle?.let {
                    if (baseInfo.subTitle != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = SuperIslandImageUtil.parseSimpleHtmlToAnnotatedString(it),
                        color = Color(SuperIslandImageUtil.parseColor(baseInfo.colorExtraTitle) ?: 0xFFFFFFFF.toInt()),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.wrapContentWidth()
                    )
                }

                // 特殊标签
                baseInfo.specialTitle?.let {
                    if (baseInfo.extraTitle != null || baseInfo.subTitle != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = SuperIslandImageUtil.parseSimpleHtmlToAnnotatedString(it),
                        color = Color(SuperIslandImageUtil.parseColor(baseInfo.colorSpecialTitle) ?: 0xFFFFFFFF.toInt()),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .wrapContentWidth()
                            .background(
                                color = Color(SuperIslandImageUtil.parseColor(baseInfo.colorSpecialBg) ?: 0xFFDDDDDD.toInt()),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // 显示主要文本和补充文本的分割符
            if (baseInfo.showContentDivider == true && (baseInfo.content != null || baseInfo.subContent != null) && (baseInfo.title != null || baseInfo.subTitle != null || baseInfo.extraTitle != null || baseInfo.specialTitle != null)) {
                Spacer(modifier = Modifier.height(8.dp))
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(color = Color(0xFFDDDDDD))
                )
                Spacer(modifier = Modifier.height(8.dp))
            } else if ((baseInfo.content != null || baseInfo.subContent != null) && (baseInfo.title != null || baseInfo.subTitle != null || baseInfo.extraTitle != null || baseInfo.specialTitle != null)) {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 次要文本区域（底部）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .align(Alignment.Start),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 次要文本1：前置描述
                baseInfo.content?.let {
                    Text(
                        text = SuperIslandImageUtil.parseSimpleHtmlToAnnotatedString(it),
                        color = Color(SuperIslandImageUtil.parseColor(baseInfo.colorContent) ?: 0xFFDDDDDD.toInt()),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.wrapContentWidth()
                    )
                }

                // 次要文本2：前置描述
                baseInfo.subContent?.let {
                    if (baseInfo.content != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = SuperIslandImageUtil.parseSimpleHtmlToAnnotatedString(it),
                        color = Color(SuperIslandImageUtil.parseColor(baseInfo.colorSubContent) ?: 0xFFDDDDDD.toInt()),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.wrapContentWidth()
                    )
                }

                // 功能图标
                baseInfo.picFunction?.let {
                    if (baseInfo.content != null || baseInfo.subContent != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    CommonImageCompose(
                        picKey = it,
                        picMap = picMap,
                        size = 24.dp,
                        isFocusIcon = false,
                        contentDescription = null
                    )
                }
            }
        }
    }
}