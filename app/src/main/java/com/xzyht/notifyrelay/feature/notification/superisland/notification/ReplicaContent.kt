package com.xzyht.notifyrelay.feature.notification.superisland.notification

import github.xzynine.superislandui.model.core.ParamV2

/**
 * 一条复刻通知的展示内容。
 *
 * 通用复刻与媒体复刻共用同一组入参：两者只在渲染细节上不同，参数逐项重复会随实现漂移。
 * [localSampleMs] 是本条内容在本地被接收的时刻，计时器据此把基准换算到本地时钟。
 */
internal class ReplicaContent(
    val title: String?,
    val text: String?,
    val appName: String?,
    val paramV2Raw: String?,
    val picMap: Map<String, String>?,
    val paramV2: ParamV2?,
    val localSampleMs: Long,
)
