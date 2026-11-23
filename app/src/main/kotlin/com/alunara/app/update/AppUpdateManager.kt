package com.alunara.app.update

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.alunara.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class AlunaraUpdatePrefsStore(
    context: Context,
) {
    private val prefs: SharedPreferences =
        (context.applicationContext ?: context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isAutoUpdateEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_UPDATE_ENABLED, true)

    fun setAutoUpdateEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_UPDATE_ENABLED, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "alunara_update_prefs"
        private const val KEY_AUTO_UPDATE_ENABLED = "auto_update_enabled"
    }
}

/**
 * Coordinates startup/manual GitHub Release checks, in-app APK streaming downloads,
 * Android unknown-sources permission handoff, and direct system PackageInstaller launch.
 */
class AppUpdateManager(
    context: Context,
    okHttpClient: OkHttpClient,
    val currentVersionName: String = BuildConfig.VERSION_NAME,
    private val client: GitHubReleaseClient = GitHubReleaseClient(okHttpClient),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val appContext = context.applicationContext ?: context
    private val prefsStore = AlunaraUpdatePrefsStore(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val hasCheckedOnStartup = AtomicBoolean(false)

    private var checkJob: Job? = null
    private var downloadJob: Job? = null
    private var installLaunchNonce: Int = 0

    private val mutableUiState = MutableStateFlow<AppUpdateUiState>(AppUpdateUiState.Idle)
    val uiState: StateFlow<AppUpdateUiState> = mutableUiState.asStateFlow()

    private val mutableDialogVisible = MutableStateFlow(false)
    val dialogVisible: StateFlow<Boolean> = mutableDialogVisible.asStateFlow()

    private val mutableAutoUpdateEnabled = MutableStateFlow(prefsStore.isAutoUpdateEnabled())
    val autoUpdateEnabled: StateFlow<Boolean> = mutableAutoUpdateEnabled.asStateFlow()

    init {
        cleanStaleUpdatesAsync()
    }

    fun setAutoUpdateEnabled(enabled: Boolean) {
        prefsStore.setAutoUpdateEnabled(enabled)
        mutableAutoUpdateEnabled.value = enabled
    }

    fun checkOnStartup() {
        if (!mutableAutoUpdateEnabled.value) return
        if (!hasCheckedOnStartup.compareAndSet(false, true)) return
        checkJob?.cancel()
        checkJob =
            scope.launch {
                val result = client.fetchLatestRelease(currentVersion = currentVersionName)
                val info = result.getOrNull() ?: return@launch
                if (AppVersionComparator.isNewer(info.versionName, currentVersionName)) {
                    mutableUiState.value = AppUpdateUiState.UpdateAvailable(info)
                    // Option B: Prompt first showing version & changelog when a new release is detected
                    mutableDialogVisible.value = true
                }
            }
    }

    fun checkForUpdatesManual() {
        if (mutableUiState.value is AppUpdateUiState.Downloading) {
            mutableDialogVisible.value = true
            return
        }
        checkJob?.cancel()
        checkJob =
            scope.launch {
                mutableUiState.value = AppUpdateUiState.Checking
                val result = client.fetchLatestRelease(currentVersion = currentVersionName)
                result.fold(
                    onSuccess = { info ->
                        if (info != null && AppVersionComparator.isNewer(info.versionName, currentVersionName)) {
                            mutableUiState.value = AppUpdateUiState.UpdateAvailable(info)
                            mutableDialogVisible.value = true
                        } else {
                            mutableUiState.value = AppUpdateUiState.UpToDate
                            mutableDialogVisible.value = false
                        }
                    },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        mutableUiState.value =
                            AppUpdateUiState.Error(
                                message = error.message ?: "Update check failed",
                                retryInfo = null,
                            )
                        mutableDialogVisible.value = false
                    },
                )
            }
    }

    fun openUpdateDialog() {
        when (mutableUiState.value) {
            is AppUpdateUiState.UpdateAvailable,
            is AppUpdateUiState.Downloading,
            is AppUpdateUiState.PermissionRequired,
            is AppUpdateUiState.ReadyToInstall,
            -> mutableDialogVisible.value = true
            else -> checkForUpdatesManual()
        }
    }

    fun startDownload(info: AppUpdateInfo) {
        checkJob?.cancel()
        downloadJob?.cancel()
        mutableDialogVisible.value = true
        mutableUiState.value =
            AppUpdateUiState.Downloading(
                info = info,
                downloadedBytes = 0L,
                totalBytes = info.asset.sizeBytes.takeIf { it > 0L } ?: -1L,
            )
        downloadJob =
            scope.launch {
                val updatesDir = updatesDirectory()
                val destinationFile = File(updatesDir, UPDATE_APK_FILENAME)
                val downloadResult =
                    client.downloadApk(
                        asset = info.asset,
                        destinationFile = destinationFile,
                        onProgress = { downloadedBytes, totalBytes ->
                            mutableUiState.value =
                                AppUpdateUiState.Downloading(
                                    info = info,
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes,
                                )
                        },
                    )
                downloadResult.fold(
                    onSuccess = { apkFile ->
                        if (canRequestPackageInstalls()) {
                            installLaunchNonce++
                            mutableUiState.value =
                                AppUpdateUiState.ReadyToInstall(
                                    info = info,
                                    apkFile = apkFile,
                                    launchNonce = installLaunchNonce,
                                )
                        } else {
                            mutableUiState.value =
                                AppUpdateUiState.PermissionRequired(
                                    info = info,
                                    apkFile = apkFile,
                                )
                        }
                    },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        mutableUiState.value =
                            AppUpdateUiState.Error(
                                message = error.message ?: "Download failed",
                                retryInfo = info,
                            )
                    },
                )
            }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        val current = mutableUiState.value
        val rollbackInfo =
            when (current) {
                is AppUpdateUiState.Downloading -> current.info
                is AppUpdateUiState.PermissionRequired -> current.info
                is AppUpdateUiState.ReadyToInstall -> current.info
                is AppUpdateUiState.Error -> current.retryInfo
                else -> null
            }
        cleanStaleUpdatesAsync()
        mutableDialogVisible.value = false
        mutableUiState.value =
            if (rollbackInfo != null) {
                AppUpdateUiState.UpdateAvailable(rollbackInfo)
            } else {
                AppUpdateUiState.Idle
            }
    }

    fun dismissDialog() {
        if (mutableUiState.value is AppUpdateUiState.Downloading) {
            cancelDownload()
        } else {
            mutableDialogVisible.value = false
        }
    }

    fun onPermissionSettingsReturned() {
        val current = mutableUiState.value as? AppUpdateUiState.PermissionRequired ?: return
        if (canRequestPackageInstalls() && current.apkFile.isFile) {
            installLaunchNonce++
            mutableUiState.value =
                AppUpdateUiState.ReadyToInstall(
                    info = current.info,
                    apkFile = current.apkFile,
                    launchNonce = installLaunchNonce,
                )
            mutableDialogVisible.value = true
        }
    }

    fun retriggerInstall() {
        val current = mutableUiState.value as? AppUpdateUiState.ReadyToInstall ?: return
        if (!current.apkFile.isFile) {
            startDownload(current.info)
            return
        }
        if (canRequestPackageInstalls()) {
            installLaunchNonce++
            mutableUiState.value = current.copy(launchNonce = installLaunchNonce)
        } else {
            mutableUiState.value = AppUpdateUiState.PermissionRequired(current.info, current.apkFile)
        }
    }

    fun canRequestPackageInstalls(): Boolean =
        runCatching { appContext.packageManager.canRequestPackageInstalls() }.getOrDefault(false)

    fun buildInstallIntent(apkFile: File): Intent {
        val contentUri =
            FileProvider.getUriForFile(
                appContext,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                apkFile,
            )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, MIME_TYPE_APK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildUnknownSourcesIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${appContext.packageName}"),
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private fun updatesDirectory(): File = File(appContext.cacheDir, UPDATES_SUBDIR)

    private fun cleanStaleUpdatesAsync() {
        scope.launch(ioDispatcher) {
            runCatching {
                val dir = updatesDirectory()
                if (dir.isDirectory) {
                    dir.listFiles()?.forEach { file ->
                        file.delete()
                    }
                }
            }
        }
    }

    companion object {
        private const val UPDATES_SUBDIR = "updates"
        private const val UPDATE_APK_FILENAME = "alunara-update.apk"
        private const val MIME_TYPE_APK = "application/vnd.android.package-archive"
    }
}
