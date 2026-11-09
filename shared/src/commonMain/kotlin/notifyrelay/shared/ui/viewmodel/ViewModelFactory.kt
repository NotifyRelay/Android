package notifyrelay.shared.ui.viewmodel

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras

/**
 * 由无参构造函数构造 ViewModel 的工厂。
 *
 * KMP 的 `viewModel()` 只接受显式的 [ViewModelProvider.Factory]；缺少它时无法在公共源集
 * 直接取到需要端口注入的 ViewModel。所有参数都来自端口注册表，因此构造本身无参。
 *
 * @param create 构造实例的函数。
 * @returns 只处理该类型的工厂。
 */
inline fun <reified VM : ViewModel> factoryOf(crossinline create: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: kotlin.reflect.KClass<T>,
            extras: CreationExtras,
        ): T = create() as T
    }

/**
 * 远端过滤 ViewModel：三个端口都从注册表取，任一未注册即报错。
 *
 * Android 与桌面各自在组合期调用本函数即可，无需各写一份工厂。
 *
 * @returns 绑定到当前组合的 [RemoteFilterViewModel]。
 */
@Composable
fun remoteFilterViewModel(): RemoteFilterViewModel =
    androidx.lifecycle.viewmodel.compose.viewModel(
        factory =
            factoryOf {
                RemoteFilterViewModel(
                    notifyrelay.domain.feature.notification.filter.RemoteFilterPortRegistry.current
                        ?: error("RemoteFilterPort 未注册"),
                    notifyrelay.shared.feature.appslist.LocalAppsPortRegistry.current
                        ?: error("LocalAppsPort 未注册"),
                    notifyrelay.shared.feature.appslist.AppIconPortRegistry.current
                        ?: error("AppIconPort 未注册"),
                )
            },
    )
