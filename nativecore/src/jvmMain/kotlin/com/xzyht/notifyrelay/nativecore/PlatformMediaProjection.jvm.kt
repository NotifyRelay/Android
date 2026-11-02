package com.xzyht.notifyrelay.nativecore

/**
 * 桌面端的屏幕采集授权句柄。
 *
 * 桌面端没有 Android 的 MediaProjection 授权模型，屏幕采集能力另行接入；
 * 此处保留占位类型以满足 public 签名的一致性。
 */
actual class PlatformMediaProjection internal constructor()
