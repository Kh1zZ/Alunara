package com.alunara.app.update

import androidx.compose.runtime.Immutable
import java.io.File
import java.net.URI
import java.util.Locale

@Immutable
data class GitHubReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
)

@Immutable
data class AppUpdateInfo(
    val versionName: String,
    val tagName: String,
    val releasePageUrl: String,
    val releaseNotes: String,
    val asset: GitHubReleaseAsset,
)

sealed interface AppUpdateUiState {
    data object Idle : AppUpdateUiState

    data object Checking : AppUpdateUiState

    data object UpToDate : AppUpdateUiState

    data class UpdateAvailable(
        val info: AppUpdateInfo,
    ) : AppUpdateUiState

    data class Downloading(
        val info: AppUpdateInfo,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : AppUpdateUiState

    data class PermissionRequired(
        val info: AppUpdateInfo,
        val apkFile: File,
    ) : AppUpdateUiState

    data class ReadyToInstall(
        val info: AppUpdateInfo,
        val apkFile: File,
        val launchNonce: Int,
    ) : AppUpdateUiState

    data class Error(
        val message: String,
        val retryInfo: AppUpdateInfo? = null,
    ) : AppUpdateUiState
}

/**
 * Pure SemVer comparator supporting optional leading `v` prefix and `-prerelease` suffixes.
 * Treats stable releases as strictly newer than prereleases sharing the same `(major, minor, patch)`.
 */
object AppVersionComparator {
    data class SemVer(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val isPrerelease: Boolean,
    ) : Comparable<SemVer> {
        override fun compareTo(other: SemVer): Int {
            if (major != other.major) return major.compareTo(other.major)
            if (minor != other.minor) return minor.compareTo(other.minor)
            if (patch != other.patch) return patch.compareTo(other.patch)
            // Stable (isPrerelease = false) > prerelease (isPrerelease = true)
            return other.isPrerelease.compareTo(isPrerelease)
        }
    }

    fun parse(raw: String?): SemVer? {
        val cleaned =
            raw
                ?.trim()
                ?.removePrefix("v")
                ?.removePrefix("V")
                ?.takeIf { it.isNotEmpty() }
                ?: return null
        val hyphenIndex = cleaned.indexOf('-')
        val basePart = if (hyphenIndex >= 0) cleaned.substring(0, hyphenIndex) else cleaned
        val isPrerelease = hyphenIndex >= 0
        if (isPrerelease && hyphenIndex == cleaned.lastIndex) return null

        val parts = basePart.split('.')
        if (parts.size !in 2..3) return null
        val numericParts =
            parts.map { part ->
                if (part.isEmpty() || !part.all { it in '0'..'9' }) return null
                part.toIntOrNull() ?: return null
            }
        return SemVer(
            major = numericParts[0],
            minor = numericParts[1],
            patch = numericParts.getOrElse(2) { 0 },
            isPrerelease = isPrerelease,
        )
    }

    fun isNewer(
        candidateVersion: String,
        currentVersion: String,
    ): Boolean {
        val candidate = parse(candidateVersion) ?: return false
        val current = parse(currentVersion) ?: return false
        return candidate > current
    }
}

/**
 * Selects the best split APK matching [Build.SUPPORTED_ABIS] priority order (`armv8a` / `arm64-v8a` vs
 * `armv7a` / `armeabi-v7a`) and validates that all downloads originate from trusted GitHub HTTPS hosts.
 */
object ReleaseApkAssetSelector {
    private val TRUSTED_EXACT_HOSTS =
        setOf(
            "github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com",
        )

    fun isTrustedDownloadUrl(url: String): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() } ?: return false
        return host in TRUSTED_EXACT_HOSTS ||
            host.endsWith(".githubusercontent.com") ||
            host.endsWith(".github.com")
    }

    fun selectBestAsset(
        assets: List<GitHubReleaseAsset>,
        supportedAbis: Array<String>,
    ): GitHubReleaseAsset? {
        val validApks =
            assets.filter { asset ->
                asset.name.lowercase(Locale.ROOT).endsWith(".apk") &&
                    isTrustedDownloadUrl(asset.downloadUrl)
            }
        if (validApks.isEmpty()) return null

        for (rawAbi in supportedAbis) {
            val abi = rawAbi.trim().lowercase(Locale.ROOT)
            val matched =
                validApks.firstOrNull { asset ->
                    val name = asset.name.lowercase(Locale.ROOT)
                    matchesAbiToken(assetName = name, abi = abi)
                }
            if (matched != null) return matched
        }

        // Fall back only to a universal APK (avoiding mismatched arch-specific builds)
        return validApks.firstOrNull { asset ->
            val name = asset.name.lowercase(Locale.ROOT)
            !hasExplicitAbiTag(name)
        }
    }

    private fun matchesAbiToken(
        assetName: String,
        abi: String,
    ): Boolean =
        when (abi) {
            "arm64-v8a" ->
                assetName.contains("armv8a") ||
                    assetName.contains("arm64-v8a") ||
                    assetName.contains("arm64")
            "armeabi-v7a" ->
                assetName.contains("armv7a") ||
                    assetName.contains("armeabi-v7a") ||
                    assetName.contains("armeabi")
            "x86_64" ->
                assetName.contains("x86_64") || assetName.contains("x86-64")
            "x86" ->
                assetName.contains("x86") &&
                    !assetName.contains("x86_64") &&
                    !assetName.contains("x86-64")
            else -> false
        }

    private fun hasExplicitAbiTag(assetName: String): Boolean =
        assetName.contains("armv8a") ||
            assetName.contains("arm64") ||
            assetName.contains("armv7a") ||
            assetName.contains("armeabi") ||
            assetName.contains("x86")
}

fun formatByteSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024L
    if (kb < 1024L) return "$kb KB"
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return String.format(Locale.ROOT, "%.1f MB", mb)
}
