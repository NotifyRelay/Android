package com.xzyht.notifyrelay.ui.ViewModels

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xzyht.notifyrelay.servers.appslist.RemoteAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LocalAppState(
    val apps: List<LocalAppInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = ""
)

data class LocalAppInfo(
    val appName: String,
    val packageName: String,
    val isPinned: Boolean = false
)

class LocalAppsViewModel : ViewModel() {
    private val _state = MutableStateFlow(LocalAppState())
    val state: StateFlow<LocalAppState> = _state.asStateFlow()

    fun loadApps(context: Context) {
        _state.update { it.copy(isLoading = true, error = null) }
        
        viewModelScope.launch {
            try {
                val apps = withContext(Dispatchers.IO) {
                    val packageManager = context.packageManager
                    packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                        .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
                        .map { appInfo ->
                            val launchIntent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
                            if (launchIntent != null) {
                                LocalAppInfo(
                                    appName = appInfo.loadLabel(packageManager).toString(),
                                    packageName = appInfo.packageName
                                )
                            } else null
                        }
                        .filterNotNull()
                        .sortedBy { it.appName.lowercase() }
                }
                
                _state.update { it.copy(apps = apps, isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun searchApps(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }
}
