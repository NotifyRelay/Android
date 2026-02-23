package github.xzynine.checkupdata.version

import github.xzynine.checkupdata.model.ReleaseInfo
import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.toVersion

object VersionComparator {
    
    fun compare(currentVersion: String, targetVersion: String): Int {
        return try {
            val current = parseVersion(currentVersion)
            val target = parseVersion(targetVersion)
            current.compareTo(target)
        } catch (e: Exception) {
            currentVersion.compareTo(targetVersion)
        }
    }
    
    fun isNewer(currentVersion: String, targetVersion: String): Boolean {
        return compare(currentVersion, targetVersion) < 0
    }
    
    fun isPreRelease(version: String): Boolean {
        return try {
            val semver = parseVersion(version)
            semver.preRelease != null
        } catch (e: Exception) {
            version.contains("-") || 
                version.contains("alpha", ignoreCase = true) ||
                version.contains("beta", ignoreCase = true) ||
                version.contains("rc", ignoreCase = true) ||
                version.contains("preview", ignoreCase = true) ||
                version.contains("snapshot", ignoreCase = true)
        }
    }
    
    fun findLatestRelease(
        releases: List<ReleaseInfo>,
        currentVersion: String,
        rule: VersionRule
    ): ReleaseInfo? {
        val filteredReleases = filterReleasesByRule(releases, rule)
        
        return filteredReleases
            .filter { isNewer(currentVersion, it.version) }
            .maxWithOrNull(compareByReleaseVersion())
    }
    
    fun getRemoteVersion(
        releases: List<ReleaseInfo>,
        rule: VersionRule
    ): String? {
        return getLatestReleaseInfo(releases, rule)?.version
    }
    
    fun getLatestReleaseInfo(
        releases: List<ReleaseInfo>,
        rule: VersionRule
    ): ReleaseInfo? {
        val filteredReleases = filterReleasesByRule(releases, rule)
        return filteredReleases.maxWithOrNull(compareByReleaseVersion())
    }
    
    private fun filterReleasesByRule(releases: List<ReleaseInfo>, rule: VersionRule): List<ReleaseInfo> {
        return when (rule) {
            VersionRule.STABLE -> releases.filter { !it.isPrerelease && !it.isDraft }
            VersionRule.LATEST -> releases.filter { !it.isDraft }
            VersionRule.PRERELEASE -> releases.filter { it.isPrerelease && !it.isDraft }
        }
    }
    
    private fun compareByReleaseVersion(): Comparator<ReleaseInfo> {
        return compareBy { release ->
            try {
                parseVersion(release.version)
            } catch (e: Exception) {
                Version(0, 0, 0)
            }
        }
    }
    
    private fun parseVersion(version: String): Version {
        var normalizedVersion = version.trim()
        
        if (normalizedVersion.startsWith("v", ignoreCase = true)) {
            normalizedVersion = normalizedVersion.substring(1)
        }
        
        return try {
            normalizedVersion.toVersion()
        } catch (e: Exception) {
            val parts = normalizedVersion.split(".", "-", "_")
            val major = parts.getOrNull(0)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
            val minor = parts.getOrNull(1)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
            val patch = parts.getOrNull(2)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
            Version(major, minor, patch)
        }
    }
}
