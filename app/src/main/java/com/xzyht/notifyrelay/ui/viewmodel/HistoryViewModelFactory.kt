package com.xzyht.notifyrelay.ui.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xzyht.notifyrelay.feature.notification.history.AndroidNotificationHistoryPort
import com.xzyht.notifyrelay.feature.notification.history.AndroidSuperIslandHistoryPort
import notifyrelay.shared.ui.viewmodel.NotificationHistoryViewModel
import notifyrelay.shared.ui.viewmodel.SuperIslandHistoryViewModel

/** 构造通知历史 ViewModel。 */
@Composable
fun notificationHistoryViewModel(): NotificationHistoryViewModel {
    val context = LocalContext.current.applicationContext
    return viewModel(
        factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = NotificationHistoryViewModel(AndroidNotificationHistoryPort(context)) as T
            },
    )
}

/** 构造超级岛历史 ViewModel。 */
@Composable
fun superIslandHistoryViewModel(): SuperIslandHistoryViewModel {
    val context = LocalContext.current.applicationContext
    return viewModel(
        factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = SuperIslandHistoryViewModel(AndroidSuperIslandHistoryPort(context)) as T
            },
    )
}
