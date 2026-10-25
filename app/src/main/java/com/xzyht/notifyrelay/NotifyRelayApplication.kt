package com.xzyht.notifyrelay

import android.app.Application
import notifyrelay.data.DataLayerAndroid

/**
 * 应用入口：进程启动时初始化数据层（Room 数据库与公共端的存储/仓储单例）。
 */
class NotifyRelayApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DataLayerAndroid.initialize(this)
    }
}
