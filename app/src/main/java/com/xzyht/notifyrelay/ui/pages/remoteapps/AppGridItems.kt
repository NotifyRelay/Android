package com.xzyht.notifyrelay.ui.pages.remoteapps

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import notifyrelay.domain.feature.appslist.model.LocalAppInfo

internal fun openLocalApp(
    context: Context,
    app: LocalAppInfo,
    displayId: Int,
) {
    try {
        val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
        intent?.let {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            // minSdk = 31（> O），makeBasic/launchDisplayId 恒可用，无需版本判断
            val options = ActivityOptions.makeBasic()
            options.launchDisplayId = displayId
            context.startActivity(intent, options.toBundle())
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
