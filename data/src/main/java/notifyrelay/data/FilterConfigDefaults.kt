package notifyrelay.data

/**
 * 过滤配置相关默认常量
 * 供数据库迁移与各模块共用，避免常量分散导致迁移时无法引用
 */
object FilterConfigDefaults {
    /** 默认包名等价组（迁移时作为 isDefault 组入库） */
    val defaultPackageGroups: List<List<String>> =
        listOf(
            listOf("tv.danmaku.bilibilihd", "tv.danmaku.bili"),
            listOf("com.sina.weibo", "com.sina.weibog3", "com.weico.international", "com.sina.weibolite", "com.hengye.share", "com.caij.see"),
            listOf("com.tencent.mobileqq", "com.tencent.tim"),
        )

    /** 超级岛镜像过滤默认包名（迁移时作为行入库） */
    val defaultMirrorPackages: List<String> =
        listOf(
            "com.xiaomi.bluetooth",
            "com.miui.mishare.connectivity",
            "com.xiaomi.mirror",
        )
}
