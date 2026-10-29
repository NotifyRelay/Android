package notifyrelay.shared.ui.viewmodel

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import notifyrelay.domain.feature.appslist.RemoteAppsPortRegistry
import notifyrelay.domain.feature.media.AudioRelayPortRegistry
import notifyrelay.domain.feature.media.MediaControlPortRegistry
import notifyrelay.domain.feature.notification.history.SuperIslandHistoryPortRegistry
import notifyrelay.domain.feature.notification.superisland.SuperIslandConfigPortRegistry
import notifyrelay.shared.feature.appslist.AppIconPortRegistry
import notifyrelay.shared.feature.appslist.LocalAppsPortRegistry

/**
 * 公共端 ViewModel 的默认构造入口。
 *
 * 这些 ViewModel 的构造依赖平台端口，无法由默认工厂反射构造；
 * 统一在此处经端口注册表装配，调用方直接使用 `= xxxViewModel()` 默认值即可。
 */
@Composable
private inline fun <reified VM : ViewModel> registryViewModel(crossinline create: () -> VM): VM =
    viewModel(
        factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: kotlin.reflect.KClass<T>,
                    extras: CreationExtras,
                ): T = create() as T
            },
    )

/** 超级岛历史承载。 */
@Composable
fun superIslandHistoryViewModel(): SuperIslandHistoryViewModel =
    registryViewModel {
        SuperIslandHistoryViewModel(SuperIslandHistoryPortRegistry.current ?: error("SuperIslandHistoryPort 未注册"))
    }

/** 超级岛设置承载。 */
@Composable
fun superIslandSettingsViewModel(): SuperIslandSettingsViewModel =
    registryViewModel {
        SuperIslandSettingsViewModel(
            SuperIslandConfigPortRegistry.current ?: error("SuperIslandConfigPort 未注册"),
            LocalAppsPortRegistry.current ?: error("LocalAppsPort 未注册"),
            AppIconPortRegistry.current ?: error("AppIconPort 未注册"),
        )
    }

/** 应用图标承载。 */
@Composable
fun appIconViewModel(): AppIconViewModel =
    registryViewModel {
        AppIconViewModel(AppIconPortRegistry.current ?: error("AppIconPort 未注册"))
    }

/** 本机应用列表承载。 */
@Composable
fun localAppsViewModel(): LocalAppsViewModel =
    registryViewModel {
        LocalAppsViewModel(LocalAppsPortRegistry.current ?: error("LocalAppsPort 未注册"))
    }

/** 远端应用列表承载。 */
@Composable
fun remoteAppsViewModel(): RemoteAppsViewModel =
    registryViewModel {
        RemoteAppsViewModel(RemoteAppsPortRegistry.current ?: error("RemoteAppsPort 未注册"))
    }

/** 应用选择弹窗承载。 */
@Composable
fun appPickerViewModel(): AppPickerViewModel =
    registryViewModel {
        AppPickerViewModel(
            LocalAppsPortRegistry.current ?: error("LocalAppsPort 未注册"),
            AppIconPortRegistry.current ?: error("AppIconPort 未注册"),
        )
    }

/** 音频转发承载。 */
@Composable
fun audioRelayViewModel(): AudioRelayViewModel =
    registryViewModel {
        AudioRelayViewModel(AudioRelayPortRegistry.current ?: error("AudioRelayPort 未注册"))
    }

/** 媒体控制承载。 */
@Composable
fun mediaControlViewModel(): MediaControlViewModel =
    registryViewModel {
        MediaControlViewModel(MediaControlPortRegistry.current ?: error("MediaControlPort 未注册"))
    }
