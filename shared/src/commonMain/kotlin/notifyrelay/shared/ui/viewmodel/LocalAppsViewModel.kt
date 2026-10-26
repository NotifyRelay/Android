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
            port.filtered("", true)
        }

    val state: StateFlow<AppsState<LocalAppInfo>> = appsMachine.state

    fun loadApps() {
        viewModelScope.launch { appsMachine.load(Unit) }
    }

    fun searchApps(query: String) = appsMachine.searchApps(query)
}
