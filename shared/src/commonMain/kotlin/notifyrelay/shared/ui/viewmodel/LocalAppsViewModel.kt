package notifyrelay.shared.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import notifyrelay.domain.feature.appslist.model.LocalAppInfo
import notifyrelay.shared.feature.appslist.LocalAppsPort

/**
 * 本地已安装应用列表的 ViewModel（平台无关）。
 */
class LocalAppsViewModel(
    private val port: LocalAppsPort,
) : ViewModel() {
    private val appsMachine =
        AppsStateMachine<LocalAppInfo, Unit> {
            // 先刷新端口数据源，再按端口当前快照返回
            port.load()
            port.filtered("", true)
        }

    val state: StateFlow<AppsState<LocalAppInfo>> = appsMachine.state

    init {
        // 端口按已同步应用表持续更新：订阅后列表随数据到达自动刷新
        viewModelScope.launch {
            port.apps.collect { list ->
                appsMachine.update { current -> current.copy(apps = list, isLoading = false) }
            }
        }
    }

    fun loadApps() {
        viewModelScope.launch {
            appsMachine.load(Unit)
            // loader 内部会刷新端口；订阅可能先于此到达，取一次最终快照
            appsMachine.update { current -> current.copy(apps = port.filtered("", true), isLoading = false) }
        }
    }

    fun searchApps(query: String) = appsMachine.searchApps(query)
}
