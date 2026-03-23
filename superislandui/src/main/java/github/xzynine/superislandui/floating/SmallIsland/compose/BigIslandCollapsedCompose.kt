package github.xzynine.superislandui.floating.SmallIsland.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.xzynine.superislandui.common.PreviewData
import github.xzynine.superislandui.floating.SmallIsland.left.AComponent
import github.xzynine.superislandui.floating.SmallIsland.left.AImageText1
import github.xzynine.superislandui.floating.SmallIsland.left.AImageText5
import github.xzynine.superislandui.floating.SmallIsland.right.BComponent
import github.xzynine.superislandui.floating.SmallIsland.right.BEmpty
import github.xzynine.superislandui.floating.SmallIsland.right.BFixedWidthDigitInfo
import github.xzynine.superislandui.floating.SmallIsland.right.BImageText2
import github.xzynine.superislandui.floating.SmallIsland.right.BImageText3
import github.xzynine.superislandui.floating.SmallIsland.right.BImageText6
import github.xzynine.superislandui.floating.SmallIsland.right.BPicInfo
import github.xzynine.superislandui.floating.SmallIsland.right.BProgressTextInfo
import github.xzynine.superislandui.floating.SmallIsland.right.BSameWidthDigitInfo
import github.xzynine.superislandui.floating.SmallIsland.right.BTextInfo
import github.xzynine.superislandui.model.parseAComponent
import github.xzynine.superislandui.model.parseBComponent
import org.json.JSONObject

/**
 * 超级岛 摘要/收起态总装配渲染器：将 A区 与 B区 的组件解析并组装为一个横向容器
 * Compose实现版本
 */
@Composable
fun BigIslandCollapsedCompose(
    bigIsland: JSONObject?,
    picMap: Map<String, String>? = null,
    fallbackTitle: String? = null,
    fallbackContent: String? = null,
    isOverlapping: Boolean = false,
    picFunction: String? = null,
    aodPic: String? = null
) {
    // 使用真正的圆角形状
    val cornerRadius = 999.dp
    val roundedShape = RoundedCornerShape(cornerRadius)
    
    // 根据重叠状态选择背景色
    val backgroundColor = if (isOverlapping) {
        Color(0xEEFF0000.toInt()) // 半透明红色
    } else {
        Color(0xCC000000.toInt()) // 半透明黑
    }
    
    // 解析A区和B区组件，传入 picFunction 和 aodPic 作为 fallback
    var aComp = parseAComponent(bigIsland, picFunction, aodPic)
    var bComp = parseBComponent(bigIsland, picFunction, aodPic)
    
    // 如果A区组件为空，创建一个默认的AImageText1对象来显示兜底应用图标
    if (aComp == null) {
        aComp = AImageText1(picKey = null)
    }
    
    // 如果 B 为空且存在兜底文本，则用兜底文本填充 B
    val bIsEmptyInitial = (bComp is BEmpty)
    if (bIsEmptyInitial) {
        val titleOrNull = fallbackTitle?.takeIf { it.isNotBlank() }
        val contentOrNull = fallbackContent?.takeIf { it.isNotBlank() }
        if (titleOrNull != null || contentOrNull != null) {
            bComp = BTextInfo(
                title = titleOrNull ?: contentOrNull.orEmpty(),
                content = if (titleOrNull != null) contentOrNull else null
            )
        }
    }
    
    // 主布局：保证长侧显示完全，加宽侧与链接处的空隙宽度
    Box(
        modifier = Modifier
            .shadow(elevation = 6.dp, shape = roundedShape)
            .background(
                color = backgroundColor,
                shape = roundedShape
            )
            .border(
                width = 1.dp,
                color = Color(0x80FFFFFF), // 半透明白色边框
                shape = roundedShape
            )
            .clip(roundedShape)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .wrapContentWidth()
    ) {
        // 主布局：使用Row实现保证长侧显示完全，加宽侧与链接处的空隙宽度
        Row(
            modifier = Modifier.wrapContentWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            // 左侧：A区内容，保证显示完全
            Box(
                modifier = Modifier.wrapContentWidth(),
                contentAlignment = Alignment.CenterStart
            ) {
                ACompose(aComp, picMap)
            }
            
            // 只有当B区存在内容时，才显示中间间距和B区
            if (bComp != null && bComp !is BEmpty) {
                // 动态中间间距：根据两侧内容宽度调整
                val dynamicSpacing = 48.dp // 加宽侧与链接处的空隙宽度
                Spacer(modifier = Modifier.width(dynamicSpacing))
                
                // 右侧：B区内容，保证显示完全
                Box(
                    modifier = Modifier.wrapContentWidth(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    BCompose(bComp, picMap)
                }
            }
        }
    }
}

/**
 * 直接使用A区和B区组件渲染摘要态（用于预览）
 */
@Composable
fun BigIslandCollapsedFromComponents(
    aComp: AComponent?,
    bComp: BComponent?,
    picMap: Map<String, String>? = null,
    isOverlapping: Boolean = false
) {
    val cornerRadius = 999.dp
    val roundedShape = RoundedCornerShape(cornerRadius)
    
    val backgroundColor = if (isOverlapping) {
        Color(0xEEFF0000.toInt())
    } else {
        Color(0xCC000000.toInt())
    }
    
    Box(
        modifier = Modifier
            .shadow(elevation = 6.dp, shape = roundedShape)
            .background(
                color = backgroundColor,
                shape = roundedShape
            )
            .border(
                width = 1.dp,
                color = Color(0x80FFFFFF),
                shape = roundedShape
            )
            .clip(roundedShape)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .wrapContentWidth()
    ) {
        Row(
            modifier = Modifier.wrapContentWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Box(
                modifier = Modifier.wrapContentWidth(),
                contentAlignment = Alignment.CenterStart
            ) {
                ACompose(aComp, picMap)
            }
            
            if (bComp != null && bComp !is BEmpty) {
                val dynamicSpacing = 48.dp
                Spacer(modifier = Modifier.width(dynamicSpacing))
                
                Box(
                    modifier = Modifier.wrapContentWidth(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    BCompose(bComp, picMap)
                }
            }
        }
    }
}

// ==================== 9种预定义组合预览 ====================
// 注：预览模式下图片会显示灰色占位符，实际运行时会加载真实图片或应用图标

// 组合1: 图文组件1 + 空
@Preview(name = "组合1-图文组件1+空", showBackground = true, backgroundColor = 0xFF000000, widthDp = 400)
@Composable
fun Preview_Combination1_AImageText1_Empty() {
    BigIslandCollapsedFromComponents(
        aComp = AImageText1(
            title = "接驾中",
            content = "5分钟"
        ),
        bComp = BEmpty,
        picMap = PreviewData.samplePicMap
    )
}

// 组合2: 图文组件1 + 文本组件
@Preview(name = "组合2-图文组件1+文本组件", showBackground = true, backgroundColor = 0xFF000000, widthDp = 400)
@Composable
fun Preview_Combination2_AImageText1_TextInfo() {
    BigIslandCollapsedFromComponents(
        aComp = AImageText1(
            title = "外卖",
            content = "配送中"
        ),
        bComp = BTextInfo(
            frontTitle = "预计",
            title = "12:30",
            content = "送达"
        ),
        picMap = PreviewData.samplePicMap
    )
}

// 组合3: 图文组件1 + 图文组件2
@Preview(name = "组合3-图文组件1+图文组件2", showBackground = true, backgroundColor = 0xFF000000, widthDp = 400)
@Composable
fun Preview_Combination3_AImageText1_ImageText2() {
    BigIslandCollapsedFromComponents(
        aComp = AImageText1(
            title = "充电中",
            content = "24%"
        ),
        bComp = BImageText2(
            frontTitle = "剩余",
            title = "5分钟",
            content = "充满",
            picKey = "icon_key"
        ),
        picMap = PreviewData.samplePicMap
    )
}

// 组合4: 图文组件1 + 图文组件3
@Preview(name = "组合4-图文组件1+图文组件3", showBackground = true, backgroundColor = 0xFF000000, widthDp = 400)
@Composable
fun Preview_Combination4_AImageText1_ImageText3() {
    BigIslandCollapsedFromComponents(
        aComp = AImageText1(
            title = "导航",
            content = "2公里"
        ),
        bComp = BImageText3(
            title = "3分钟",
            picKey = "icon_key"
        ),
        picMap = PreviewData.samplePicMap
    )
}

// 组合5: 图文组件1 + 进度文本组件
@Preview(name = "组合5-图文组件1+进度文本组件", showBackground = true, backgroundColor = 0xFF000000, widthDp = 400)
@Composable
fun Preview_Combination5_AImageText1_ProgressTextInfo() {
    BigIslandCollapsedFromComponents(
        aComp = AImageText1(
            title = "下载",
            content = "进行中"
        ),
        bComp = BProgressTextInfo(
            frontTitle = null,
            title = "下载中",
            content = "60%",
            progress = 60,
            picKey = "icon_key"
        ),
        picMap = PreviewData.samplePicMap
    )
}

// 组合6: 图文组件1 + 等宽数字文本组件
@Preview(name = "组合6-图文组件1+等宽数字文本组件", showBackground = true, backgroundColor = 0xFF000000, widthDp = 400)
@Composable
fun Preview_Combination6_AImageText1_SameWidthDigitInfo() {
    BigIslandCollapsedFromComponents(
        aComp = AImageText1(
            title = "计时",
            content = "进行中"
        ),
        bComp = BSameWidthDigitInfo(
            digit = "05:32",
            content = "剩余",
            showHighlightColor = true
        ),
        picMap = PreviewData.samplePicMap
    )
}

// 组合7: 图文组件1 + 定宽数字文本组件
@Preview(name = "组合7-图文组件1+定宽数字文本组件", showBackground = true, backgroundColor = 0xFF000000, widthDp = 400)
@Composable
fun Preview_Combination7_AImageText1_FixedWidthDigitInfo() {
    BigIslandCollapsedFromComponents(
        aComp = AImageText1(
            title = "速度",
            content = "行驶中"
        ),
        bComp = BFixedWidthDigitInfo(
            digit = "99.9",
            content = "km/h",
            showHighlightColor = true
        ),
        picMap = PreviewData.samplePicMap
    )
}

// 组合8: 图文组件1 + 大图组件
@Preview(name = "组合8-图文组件1+大图组件", showBackground = true, backgroundColor = 0xFF000000, widthDp = 400)
@Composable
fun Preview_Combination8_AImageText1_PicInfo() {
    BigIslandCollapsedFromComponents(
        aComp = AImageText1(
            title = "音乐",
            content = "播放中"
        ),
        bComp = BPicInfo(
            picKey = "icon_key",
            type = 1
        ),
        picMap = PreviewData.samplePicMap
    )
}

// 组合9: 图文组件5 + 图文组件6
@Preview(name = "组合9-图文组件5+图文组件6", showBackground = true, backgroundColor = 0xFF000000, widthDp = 400)
@Composable
fun Preview_Combination9_AImageText5_ImageText6() {
    BigIslandCollapsedFromComponents(
        aComp = AImageText5(
            title = "5",
            content = "公里",
            picKey = "icon_key"
        ),
        bComp = BImageText6(
            title = "34",
            picKey = "icon_key"
        ),
        picMap = PreviewData.samplePicMap
    )
}

// ==================== A区组件分支预览 ====================

// AImageText1: 仅图标（无文本）
@Preview(name = "A区-图文组件1-仅图标", showBackground = true, backgroundColor = 0xFF000000, widthDp = 400)
@Composable
fun Preview_AImageText1_IconOnly() {
    BigIslandCollapsedFromComponents(
        aComp = AImageText1(
            picKey = "icon_key"
        ),
        bComp = BEmpty,
        picMap = PreviewData.samplePicMap
    )
}

// AImageText1: 仅文本（无图标）
@Preview(name = "A区-图文组件1-仅文本", showBackground = true, backgroundColor = 0xFF000000, widthDp = 400)
@Composable
fun Preview_AImageText1_TextOnly() {
    BigIslandCollapsedFromComponents(
        aComp = AImageText1(
            title = "通知",
            content = "内容"
        ),
        bComp = BEmpty,
        picMap = PreviewData.samplePicMap
    )
}

// AImageText1: 强调色
@Preview(name = "A区-图文组件1-强调色", showBackground = true, backgroundColor = 0xFF000000, widthDp = 400)
@Composable
fun Preview_AImageText1_Highlight() {
    BigIslandCollapsedFromComponents(
        aComp = AImageText1(
            title = "验证码",
            content = "123456",
            showHighlightColor = true
        ),
        bComp = BEmpty,
        picMap = PreviewData.samplePicMap
    )
}

// AImageText1: 窄字体
@Preview(name = "A区-图文组件1-窄字体", showBackground = true, backgroundColor = 0xFF000000, widthDp = 400)
@Composable
fun Preview_AImageText1_NarrowFont() {
    BigIslandCollapsedFromComponents(
        aComp = AImageText1(
            title = "12345",
            content = "数字",
            narrowFont = true
        ),
        bComp = BEmpty,
        picMap = PreviewData.samplePicMap
    )
}

// AImageText5: 完整配置
@Preview(name = "A区-图文组件5-完整", showBackground = true, backgroundColor = 0xFF000000, widthDp = 400)
@Composable
fun Preview_AImageText5_Full() {
    BigIslandCollapsedFromComponents(
        aComp = AImageText5(
            title = "88",
            content = "分",
            showHighlightColor = true,
            picKey = "icon_key"
        ),
        bComp = BEmpty,
        picMap = PreviewData.samplePicMap
    )
}

// ==================== B区组件分支预览 ====================

// BTextInfo: 无前置标题
@Preview(name = "B区-文本组件-无前置标题", showBackground = true, backgroundColor = 0xFF000000, widthDp = 400)
@Composable
fun Preview_BTextInfo_NoFrontTitle() {
    BigIslandCollapsedFromComponents(
        aComp = AImageText1(title = "应用"),
        bComp = BTextInfo(
            title = "主标题",
            content = "副标题"
        ),
        picMap = PreviewData.samplePicMap
    )
}

// BTextInfo: 强调色
@Preview(name = "B区-文本组件-强调色", showBackground = true, backgroundColor = 0xFF000000, widthDp = 400)
@Composable
fun Preview_BTextInfo_Highlight() {
    BigIslandCollapsedFromComponents(
        aComp = AImageText1(title = "应用"),
        bComp = BTextInfo(
            title = "重要信息",
            content = "描述",
            showHighlightColor = true
        ),
        picMap = PreviewData.samplePicMap
    )
}

// BImageText2: 无前置标题
@Preview(name = "B区-图文组件2-无前置标题", showBackground = true, backgroundColor = 0xFF000000, widthDp = 400)
@Composable
fun Preview_BImageText2_NoFrontTitle() {
    BigIslandCollapsedFromComponents(
        aComp = AImageText1(title = "应用"),
        bComp = BImageText2(
            title = "主标题",
            content = "副标题",
            picKey = "icon_key"
        ),
        picMap = PreviewData.samplePicMap
    )
}

// BImageText3: 强调色
@Preview(name = "B区-图文组件3-强调色", showBackground = true, backgroundColor = 0xFF000000, widthDp = 400)
@Composable
fun Preview_BImageText3_Highlight() {
    BigIslandCollapsedFromComponents(
        aComp = AImageText1(title = "应用"),
        bComp = BImageText3(
            title = "重要信息",
            showHighlightColor = true,
            picKey = "icon_key"
        ),
        picMap = PreviewData.samplePicMap
    )
}

// BProgressTextInfo: 无图标
@Preview(name = "B区-进度文本组件-无图标", showBackground = true, backgroundColor = 0xFF000000, widthDp = 400)
@Composable
fun Preview_BProgressTextInfo_NoIcon() {
    BigIslandCollapsedFromComponents(
        aComp = AImageText1(title = "应用"),
        bComp = BProgressTextInfo(
            title = "下载中",
            content = "30%",
            progress = 30
        ),
        picMap = PreviewData.samplePicMap
    )
}

// BProgressTextInfo: 高进度
@Preview(name = "B区-进度文本组件-高进度", showBackground = true, backgroundColor = 0xFF000000, widthDp = 400)
@Composable
fun Preview_BProgressTextInfo_HighProgress() {
    BigIslandCollapsedFromComponents(
        aComp = AImageText1(title = "应用"),
        bComp = BProgressTextInfo(
            title = "即将完成",
            content = "90%",
            progress = 90,
            picKey = "icon_key"
        ),
        picMap = PreviewData.samplePicMap
    )
}

// BSameWidthDigitInfo: 计时器
@Preview(name = "B区-等宽数字-计时器", showBackground = true, backgroundColor = 0xFF000000, widthDp = 400)
@Composable
fun Preview_BSameWidthDigitInfo_Timer() {
    BigIslandCollapsedFromComponents(
        aComp = AImageText1(title = "应用"),
        bComp = BSameWidthDigitInfo(
            timer = PreviewData.sampleTimerInfo,
            content = "已用时间"
        ),
        picMap = PreviewData.samplePicMap
    )
}

// BFixedWidthDigitInfo: 无后置文本
@Preview(name = "B区-定宽数字-无后置文本", showBackground = true, backgroundColor = 0xFF000000, widthDp = 400)
@Composable
fun Preview_BFixedWidthDigitInfo_NoContent() {
    BigIslandCollapsedFromComponents(
        aComp = AImageText1(title = "应用"),
        bComp = BFixedWidthDigitInfo(
            digit = "100"
        ),
        picMap = PreviewData.samplePicMap
    )
}

// ==================== 其他预览 ====================

@Preview(name = "摘要态默认", showBackground = true, backgroundColor = 0xFF000000, widthDp = 400)
@Composable
fun BigIslandCollapsedComposePreview() {
    BigIslandCollapsedCompose(
        bigIsland = null,
        picMap = PreviewData.samplePicMap,
        fallbackTitle = "通知标题",
        fallbackContent = "通知内容"
    )
}

@Preview(name = "摘要态重叠", showBackground = true, backgroundColor = 0xFF000000, widthDp = 400)
@Composable
fun BigIslandCollapsedComposeOverlappingPreview() {
    BigIslandCollapsedCompose(
        bigIsland = null,
        picMap = PreviewData.samplePicMap,
        fallbackTitle = "重叠通知",
        fallbackContent = "重叠内容",
        isOverlapping = true
    )
}
