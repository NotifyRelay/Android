package github.xzynine.superislandui.common

import github.xzynine.superislandui.model.components.ActionInfo
import github.xzynine.superislandui.model.components.AnimIconInfo
import github.xzynine.superislandui.model.components.AnimTextInfo
import github.xzynine.superislandui.model.components.MediaSessionData
import github.xzynine.superislandui.model.components.MultiProgressInfo
import github.xzynine.superislandui.model.components.ProgressInfo
import github.xzynine.superislandui.model.components.TextButton
import github.xzynine.superislandui.model.components.TimerInfo
import github.xzynine.superislandui.model.core.ParamV2
import github.xzynine.superislandui.model.templates.BaseInfo
import github.xzynine.superislandui.model.templates.ChatInfo
import github.xzynine.superislandui.model.templates.CoverInfo
import github.xzynine.superislandui.model.templates.HighlightInfo
import github.xzynine.superislandui.model.templates.HighlightInfoV3
import github.xzynine.superislandui.model.templates.HintInfo
import github.xzynine.superislandui.model.templates.IconTextAnimIcon
import github.xzynine.superislandui.model.templates.IconTextInfo
import github.xzynine.superislandui.model.templates.PicInfo

object PreviewData {
    val samplePicMap =
        mapOf(
            "icon_key" to "https://example.com/icon.png",
            "miui.focus.pic_test" to "https://example.com/test.png",
        )

    val sampleTimerInfo =
        TimerInfo(
            timerType = 1,
            timerWhen = System.currentTimeMillis() - 60000,
            timerTotal = 300000,
            timerSystemCurrent = System.currentTimeMillis(),
        )

    val sampleTimerInfoCountdown =
        TimerInfo(
            timerType = -1,
            timerWhen = System.currentTimeMillis() + 300000,
            timerTotal = 300000,
            timerSystemCurrent = System.currentTimeMillis(),
        )

    val sampleActionInfo =
        ActionInfo(
            actionTitle = "点击操作",
            actionTitleColor = "FFFFFF",
            actionBgColor = "3482FF",
        )

    val sampleActions =
        listOf(
            ActionInfo(
                actionTitle = "确认",
                actionTitleColor = "FFFFFF",
                actionBgColor = "3482FF",
            ),
            ActionInfo(
                actionTitle = "取消",
                actionTitleColor = "FFFFFF",
                actionBgColor = "666666",
            ),
        )

    val sampleBaseInfo =
        BaseInfo(
            type = 1,
            title = "主标题",
            subTitle = "副标题",
            extraTitle = "补充信息",
            specialTitle = "标签",
            content = "次要内容",
            subContent = "次要描述",
            showDivider = true,
            showContentDivider = false,
        )

    val sampleBaseInfoType2 =
        BaseInfo(
            type = 2,
            title = "主要信息",
            subTitle = "关键数据",
            content = "前置描述",
            subContent = "补充说明",
        )

    val sampleChatInfo =
        ChatInfo(
            title = "消息标题",
            content = "这是消息内容，显示最新的聊天信息",
            colorTitle = "FFFFFF",
            colorContent = "DDDDDD",
        )

    val sampleHighlightInfo =
        HighlightInfo(
            title = "高亮标题",
            content = "辅助信息",
            subContent = "状态信息",
            colorTitle = "FFFFFF",
            colorContent = "DDDDDD",
            colorSubContent = "9EA3FF",
        )

    val sampleHighlightInfoWithTimer =
        HighlightInfo(
            title = "计时中",
            content = "进行中",
            timerInfo = sampleTimerInfo,
            colorTitle = "FFFFFF",
        )

    val sampleHintInfo =
        HintInfo(
            type = 1,
            title = "9:20开场",
            content = "时间",
            colorContentBg = "333482FF",
            actionInfo = sampleActionInfo,
        )

    val sampleHintInfoType2 =
        HintInfo(
            type = 2,
            title = "00:30:59",
            content = "用时",
            subTitle = "--",
            subContent = "配速",
            actionInfo = sampleActionInfo,
        )

    val samplePicInfo =
        PicInfo(
            type = 1,
            title = "图片标题",
        )

    val samplePicInfoType2 =
        PicInfo(
            type = 2,
            pic = "icon_key",
            title = "自定义图片",
        )

    val sampleProgressInfo =
        ProgressInfo(
            progress = 60,
            colorProgress = "00FF00",
        )

    val sampleProgressInfoLow =
        ProgressInfo(
            progress = 25,
            colorProgress = "FFA500",
        )

    val sampleProgressInfoWithNodes =
        ProgressInfo(
            progress = 40,
            colorProgress = "FF8514",
            colorProgressEnd = "FF8514",
            picForward = "miui.focus.pic_forward_v2",
            picMiddle = "miui.focus.pic_middle_v2",
            picMiddleUnselected = "miui.focus.pic_middle_unselected_v2",
            picEnd = "miui.focus.pic_end_v2",
            picEndUnselected = "miui.focus.pic_end_unselected_v2",
        )

    val sampleMultiProgressInfo =
        MultiProgressInfo(
            title = "配送进度",
            progress = 50,
            color = "0ABAFF",
            points = 3,
        )

    val sampleMultiProgressInfoComplete =
        MultiProgressInfo(
            title = "已完成",
            progress = 100,
            color = "00FF00",
            points = 4,
        )

    val sampleAnimTextInfo =
        AnimTextInfo(
            icon = AnimIconInfo(src = "icon_key"),
            title = "动画文本标题",
            content = "动画文本内容",
        )

    val sampleAnimTextInfoWithTimer =
        AnimTextInfo(
            icon = AnimIconInfo(src = "icon_key"),
            title = null,
            content = "计时中",
            timerInfo = sampleTimerInfo,
        )

    val sampleParamV2 =
        ParamV2(
            baseInfo = sampleBaseInfo,
            chatInfo = sampleChatInfo,
            highlightInfo = sampleHighlightInfo,
            progressInfo = sampleProgressInfo,
            actions = sampleActions,
        )

    val sampleParamV2WithChat =
        ParamV2(
            chatInfo = sampleChatInfo,
            progressInfo = sampleProgressInfo,
        )

    val sampleMediaSessionData =
        MediaSessionData(
            packageName = "com.example.music",
            appName = "音乐播放器",
            title = "正在播放的歌曲名称",
            text = "艺术家名称",
            coverUrl = null,
            deviceName = "本地设备",
        )

    val sampleTextButton =
        TextButton(
            actions = sampleActions,
        )

    val sampleIconTextInfo =
        IconTextInfo(
            animIconInfo =
                IconTextAnimIcon(
                    type = 0,
                    src = "icon_key",
                ),
            title = "手电筒",
            content = "已打开",
            subContent = "长按关闭",
            colorTitle = "FFFFFF",
            colorContent = "DDDDDD",
        )

    val sampleCoverInfo =
        CoverInfo(
            picCover = "miui.focus.pic_forward_v2",
            title = "哪吒闹海",
            content = "1号厅",
            subContent = "21排322座",
            colorTitle = "FFFFFF",
            colorContent = "DDDDDD",
            colorSubContent = "DDDDDD",
        )

    val sampleHighlightInfoV3 =
        HighlightInfoV3(
            primaryText = "4899元",
            secondaryText = "4999元",
            showSecondaryLine = true,
            highLightText = "限时优惠",
            primaryColor = "3482FF",
            secondaryColor = "DDDDDD",
            highLightTextColor = "FF8A3D",
            highLightbgColor = "33FF8A3D",
            actionInfo =
                ActionInfo(
                    actionTitle = "去支付",
                    actionTitleColor = "FFFFFF",
                    actionBgColor = "3482FF",
                ),
        )
}
