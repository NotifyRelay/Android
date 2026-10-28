package notifyrelay.desktop

import kotlinx.coroutines.runBlocking
import notifyrelay.data.database.entity.NotificationRecordEntity
import notifyrelay.data.database.repository.DatabaseRepository

/**
 * 桌面端示例数据。
 *
 * 桌面端尚无通知来源，先注入一批示例记录用于验证历史页渲染；
 * 接入真实通知监听后删除本对象。
 */
object SampleData {
    private const val DEVICE = "本机"

    /** 若库中无记录则写入示例数据。 */
    fun seedIfEmpty(repository: DatabaseRepository) {
        runBlocking {
            if (repository.getPackageCountByDevice(DEVICE).isNotEmpty()) return@runBlocking

            val now = System.currentTimeMillis()
            val samples =
                listOf(
                    sample("com.tencent.mm", "微信", "张三", "今晚的会议改到 8 点", now - 2 * 60_000),
                    sample("com.tencent.mm", "微信", "李四", "文件已发你邮箱", now - 15 * 60_000),
                    sample("com.tencent.mm", "微信", "工作群", "王五: 明天记得带材料", now - 40 * 60_000),
                    sample("com.tencent.mobileqq", "QQ", "技术交流群", "有人了解 Compose Multiplatform 吗", now - 5 * 60_000),
                    sample("com.taobao.taobao", "淘宝", "订单提醒", "您的包裹已到达菜鸟驿站", now - 30 * 60_000),
                    sample("com.eg.android.AlipayGphone", "支付宝", "到账通知", "收款 128.00 元", now - 60 * 60_000),
                    sample("tv.danmaku.bili", "哔哩哔哩", "关注的 UP 主", "发布了新视频：Kotlin 进阶", now - 90 * 60_000),
                    sample("com.xingin.xhs", "小红书", "系统通知", "你有 3 条新互动", now - 3 * 60 * 60_000),
                    sample("com.github.metacubex.clash.meta", "Clash Meta", "运行状态", "已连接 12 个节点", now - 4 * 60 * 60_000),
                    sample("com.miui.securitycenter", "安全中心", "电量提醒", "电量已低于 20%", now - 5 * 60 * 60_000),
                )
            repository.saveNotifications(samples)
            println("[示例数据] 已写入 " + samples.size + " 条通知记录")
        }
    }

    private fun sample(
        pkg: String,
        appName: String,
        title: String,
        text: String,
        time: Long,
    ) = NotificationRecordEntity(
        key = "sample|" + pkg + "|" + time,
        deviceUuid = DEVICE,
        packageName = pkg,
        appName = appName,
        title = title,
        text = text,
        time = time,
        createdAt = time,
    )
}
