package com.xzyht.notifyrelay.feature.appslist.launch

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import notifyrelay.shared.ui.pages.remoteapps.AppLaunchPort

/** 以 [Context] 实现 [AppLaunchPort]。 */
class AndroidAppLaunchPort(
    private val context: Context,
) : AppLaunchPort {
    override fun launchLocalApp(
        packageName: String,
        displayId: Int,
    ) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            // minSdk = 31（> O），makeBasic/launchDisplayId 恒可用，无需版本判断
            val options = ActivityOptions.makeBasic()
            options.launchDisplayId = displayId
            context.startActivity(intent, options.toBundle())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
