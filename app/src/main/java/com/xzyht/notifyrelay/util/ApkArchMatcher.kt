package com.xzyht.notifyrelay.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import github.xzynine.checkupdata.model.ReleaseInfo

object ApkArchMatcher {
    
    val SUPPORTED_ABIS: Set<String> = setOf(
        "arm64-v8a",
        "armeabi-v7a",
        "x86_64",
        "x86"
    )
    
    val ABI_PATTERNS: Map<String, List<String>> = mapOf(
        "arm64-v8a" to listOf("arm64", "aarch64", "arm64-v8a"),
        "armeabi-v7a" to listOf("arm", "armeabi", "armeabi-v7a", "armv7"),
        "x86_64" to listOf("x86_64", "x64", "amd64"),
        "x86" to listOf("x86", "i386", "i686")
    )
    
    fun getDeviceAbi(): String {
        return Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
    }
    
    fun getInstalledAppAbi(context: Context): String? {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_SHARED_LIBRARY_FILES
            )
            
            val nativeLibraryDir = appInfo.nativeLibraryDir
            when {
                nativeLibraryDir.contains("arm64") -> "arm64-v8a"
                nativeLibraryDir.contains("armeabi") || nativeLibraryDir.contains("arm") -> "armeabi-v7a"
                nativeLibraryDir.contains("x86_64") || nativeLibraryDir.contains("x64") -> "x86_64"
                nativeLibraryDir.contains("x86") || nativeLibraryDir.contains("i386") -> "x86"
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    fun getInstalledAppAbiOrDevice(context: Context): String {
        return getInstalledAppAbi(context) ?: getDeviceAbi()
    }
    
    fun matchAssetByArch(assets: List<ReleaseInfo.ReleaseAsset>, preferredAbi: String? = null): ReleaseInfo.ReleaseAsset? {
        val allApks = assets.filter { 
            it.name.endsWith(".apk", ignoreCase = true) 
        }
        
        val universalApk = allApks.find { 
            val name = it.name.lowercase()
            name.contains("universal") || name.contains("noarch")
        }
        
        val patterns = ABI_PATTERNS[preferredAbi] ?: emptyList()
        val matchedApk = allApks.find { asset ->
            val name = asset.name.lowercase()
            patterns.any { pattern -> name.contains(pattern) }
        }
        
        return matchedApk ?: universalApk ?: allApks.firstOrNull()
    }
    
    fun createAssetFilter(preferredAbi: String): (ReleaseInfo.ReleaseAsset) -> Boolean {
        val patterns = ABI_PATTERNS[preferredAbi] ?: emptyList()
        
        return { asset ->
            if (!asset.name.endsWith(".apk", ignoreCase = true)) {
                false
            } else {
                val name = asset.name.lowercase()
                val isUniversal = name.contains("universal") || name.contains("noarch")
                val matchesArch = patterns.any { pattern -> name.contains(pattern) }
                isUniversal || matchesArch
            }
        }
    }
}
