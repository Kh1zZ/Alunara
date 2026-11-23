package com.alunara.app.update

import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Queries official GitHub Releases for `Kh1zZ/Alunara` (with automatic web redirect fallback
 * when GitHub's anonymous REST API rate-limits) and streams signed split APKs to disk.
 */
class GitHubReleaseClient(
    private val okHttpClient: OkHttpClient,
) {
    private val downloadClient: OkHttpClient by lazy {
        okHttpClient
            .newBuilder()
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    suspend fun fetchLatestRelease(
        currentVersion: String,
        supportedAbis: Array<String> = Build.SUPPORTED_ABIS,
    ): Result<AppUpdateInfo?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val userAgent = "Alunara-AndroidTV/$currentVersion"
                val apiRequest =
                    Request
                        .Builder()
                        .url(LATEST_RELEASE_API_URL)
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", userAgent)
                        .build()

                val apiResult =
                    runCatching {
                        okHttpClient.newCall(apiRequest).execute().use { response ->
                            if (response.isSuccessful) {
                                val body = response.body?.string().orEmpty()
                                true to parseReleaseJson(body, supportedAbis)
                            } else {
                                false to null
                            }
                        }
                    }

                val parsedFromApi = apiResult.getOrNull()
                if (parsedFromApi != null && parsedFromApi.first) {
                    return@runCatching parsedFromApi.second
                }

                // Fallback via web redirect when API rate-limited (403/429) or unreachable
                val webRequest =
                    Request
                        .Builder()
                        .url(LATEST_RELEASE_WEB_URL)
                        .header("User-Agent", userAgent)
                        .build()
                okHttpClient.newCall(webRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("HTTP ${response.code}")
                    }
                    val finalUrl = response.request.url.toString()
                    parseReleaseFromRedirectUrl(finalUrl, supportedAbis)
                }
            }
        }

    suspend fun downloadApk(
        asset: GitHubReleaseAsset,
        destinationFile: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): Result<File> =
        withContext(Dispatchers.IO) {
            if (!ReleaseApkAssetSelector.isTrustedDownloadUrl(asset.downloadUrl)) {
                return@withContext Result.failure(IllegalArgumentException("Untrusted update download URL"))
            }
            val parent = destinationFile.parentFile ?: return@withContext Result.failure(IllegalStateException("Missing cache directory"))
            if (!parent.exists()) {
                parent.mkdirs()
            }
            val partFile = File(parent, "${destinationFile.name}.part")
            if (partFile.exists()) {
                partFile.delete()
            }

            try {
                val request =
                    Request
                        .Builder()
                        .url(asset.downloadUrl)
                        .header("Accept", "application/vnd.android.package-archive,application/octet-stream")
                        .build()

                downloadClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("Download failed (HTTP ${response.code})")
                    }
                    val body = response.body ?: error("Empty download response")
                    val contentLength = body.contentLength()
                    val resolvedTotalBytes =
                        when {
                            contentLength > 0L -> contentLength
                            asset.sizeBytes > 0L -> asset.sizeBytes
                            else -> -1L
                        }

                    body.byteStream().use { input ->
                        FileOutputStream(partFile).use { output ->
                            val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                            var downloadedBytes = 0L
                            var lastNotifyMs = 0L
                            onProgress(0L, resolvedTotalBytes)
                            while (true) {
                                coroutineContext.ensureActive()
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                downloadedBytes += read
                                val nowMs = System.currentTimeMillis()
                                if (nowMs - lastNotifyMs >= PROGRESS_THROTTLE_MS) {
                                    lastNotifyMs = nowMs
                                    onProgress(downloadedBytes, resolvedTotalBytes)
                                }
                            }
                            output.flush()
                            if (downloadedBytes <= 0L) {
                                error("Downloaded APK is empty")
                            }
                            onProgress(downloadedBytes, resolvedTotalBytes)
                        }
                    }
                }

                if (destinationFile.exists()) {
                    destinationFile.delete()
                }
                if (!partFile.renameTo(destinationFile)) {
                    partFile.copyTo(destinationFile, overwrite = true)
                    partFile.delete()
                }
                Result.success(destinationFile)
            } catch (cancel: CancellationException) {
                partFile.delete()
                throw cancel
            } catch (error: Throwable) {
                partFile.delete()
                Result.failure(error)
            }
        }

    companion object {
        const val REPO_OWNER = "Kh1zZ"
        const val REPO_NAME = "Alunara"
        const val LATEST_RELEASE_API_URL =
            "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
        const val LATEST_RELEASE_WEB_URL =
            "https://github.com/$REPO_OWNER/$REPO_NAME/releases/latest"

        private const val DOWNLOAD_BUFFER_BYTES = 32 * 1024
        private const val PROGRESS_THROTTLE_MS = 100L
        private const val MAX_NOTES_CHARS = 1200
        private val REDIRECT_TAG_REGEX = Regex("""/releases/tag/([^/?#]+)""")

        fun parseReleaseJson(
            jsonBody: String,
            supportedAbis: Array<String>,
        ): AppUpdateInfo? {
            val root = runCatching { Json.parseToJsonElement(jsonBody).jsonObject }.getOrNull() ?: return null
            if (root["draft"]?.jsonPrimitive?.booleanOrNull == true) return null
            if (root["prerelease"]?.jsonPrimitive?.booleanOrNull == true) return null

            val tagName = root["tag_name"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (AppVersionComparator.parse(tagName) == null) return null
            val versionName = tagName.removePrefix("v").removePrefix("V")

            val rawHtmlUrl = root["html_url"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val releasePageUrl =
                if (ReleaseApkAssetSelector.isTrustedDownloadUrl(rawHtmlUrl)) {
                    rawHtmlUrl
                } else {
                    "https://github.com/$REPO_OWNER/$REPO_NAME/releases/tag/$tagName"
                }

            val assets =
                root["assets"]
                    ?.jsonArray
                    ?.mapNotNull { element ->
                        val item = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
                        val name = item["name"]?.jsonPrimitive?.contentOrNull?.trim() ?: return@mapNotNull null
                        val url = item["browser_download_url"]?.jsonPrimitive?.contentOrNull?.trim() ?: return@mapNotNull null
                        val size = item["size"]?.jsonPrimitive?.longOrNull ?: 0L
                        GitHubReleaseAsset(
                            name = name,
                            downloadUrl = url,
                            sizeBytes = size,
                        )
                    }.orEmpty()

            val bestAsset = ReleaseApkAssetSelector.selectBestAsset(assets, supportedAbis) ?: return null
            val releaseNotes = formatReleaseNotes(root["body"]?.jsonPrimitive?.contentOrNull.orEmpty())

            return AppUpdateInfo(
                versionName = versionName,
                tagName = tagName,
                releasePageUrl = releasePageUrl,
                releaseNotes = releaseNotes,
                asset = bestAsset,
            )
        }

        fun parseReleaseFromRedirectUrl(
            finalUrl: String,
            supportedAbis: Array<String>,
        ): AppUpdateInfo? {
            if (!ReleaseApkAssetSelector.isTrustedDownloadUrl(finalUrl)) return null
            val tagName = REDIRECT_TAG_REGEX.find(finalUrl)?.groupValues?.getOrNull(1)?.trim() ?: return null
            if (AppVersionComparator.parse(tagName) == null) return null
            val versionName = tagName.removePrefix("v").removePrefix("V")
            val deterministicAssets =
                listOf(
                    GitHubReleaseAsset(
                        name = "alunara-$versionName.armv8a.apk",
                        downloadUrl =
                            "https://github.com/$REPO_OWNER/$REPO_NAME/releases/download/$tagName/alunara-$versionName.armv8a.apk",
                        sizeBytes = 0L,
                    ),
                    GitHubReleaseAsset(
                        name = "alunara-$versionName.armv7a.apk",
                        downloadUrl =
                            "https://github.com/$REPO_OWNER/$REPO_NAME/releases/download/$tagName/alunara-$versionName.armv7a.apk",
                        sizeBytes = 0L,
                    ),
                )
            val bestAsset = ReleaseApkAssetSelector.selectBestAsset(deterministicAssets, supportedAbis) ?: return null
            return AppUpdateInfo(
                versionName = versionName,
                tagName = tagName,
                releasePageUrl = "https://github.com/$REPO_OWNER/$REPO_NAME/releases/tag/$tagName",
                releaseNotes = "",
                asset = bestAsset,
            )
        }

        fun formatReleaseNotes(rawBody: String): String {
            if (rawBody.isBlank()) return ""
            val normalized = rawBody.replace("\r\n", "\n").trim()
            // Strip release.yml auto-generated footer (`---` onward) so only actual changelog notes display
            val dividerIdx = normalized.indexOf("\n---")
            val mainSection = if (dividerIdx >= 0) normalized.substring(0, dividerIdx) else normalized
            val cleanedLines =
                mainSection
                    .lines()
                    .mapNotNull { line ->
                        val trimmed = line.trim()
                        when {
                            trimmed.isEmpty() -> null
                            trimmed.startsWith("## Alunara v", ignoreCase = true) -> null
                            trimmed.startsWith("### ") -> trimmed.removePrefix("### ").trim()
                            trimmed.startsWith("- ") -> "• ${trimmed.removePrefix("- ").trim()}"
                            else -> trimmed
                        }
                    }.joinToString("\n")
                    .replace("`", "")
                    .replace("**", "")
            return if (cleanedLines.length > MAX_NOTES_CHARS) {
                "${cleanedLines.take(MAX_NOTES_CHARS - 1).trimEnd()}…"
            } else {
                cleanedLines
            }
        }
    }
}
