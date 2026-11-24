package com.alunara.app

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import coil3.SingletonImageLoader
import com.alunara.app.playback.AlunaraPlaybackService
import com.alunara.app.playback.PlaybackPreferences
import com.alunara.app.playback.PlaybackSessionManager
import com.alunara.app.playback.SharedPreferencesPlaybackPreferencesStore
import com.alunara.app.playback.SinglePlayerLifecycleController
import com.alunara.app.remote.AlunaraRemoteServer
import com.alunara.app.remote.AndroidTvVolumeController
import com.alunara.app.remote.RemotePreferencesStore
import com.alunara.app.remote.SharedPreferencesRemotePreferencesStore
import com.alunara.app.update.AppUpdateManager
import com.alunara.core.data.cache.CacheBudgetDecision
import com.alunara.core.data.cache.CacheBudgetManager
import com.alunara.core.data.db.AlunaraDatabase
import com.alunara.core.data.store.CachedLyricsRepository
import com.alunara.core.data.store.KeystoreAccountSessionStore
import com.alunara.core.data.store.RoomCatalogStore
import com.alunara.core.data.store.RoomQueueStore
import com.alunara.core.domain.contract.AccountSessionStore
import com.alunara.core.domain.contract.CatalogStore
import com.alunara.core.domain.contract.LyricsEngine
import com.alunara.core.domain.contract.MusicEngine
import com.alunara.core.domain.contract.QueueStore
import com.alunara.core.lyrics.ArchiveTuneLyricsAdapter
import com.alunara.core.music.ArchiveTuneMusicAdapter
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Lightweight lazy dependency container avoiding eager network or heavy JS engine initialization
 * at cold start on 1 GB RAM Android TVs.
 */
class AppContainer(
    context: Context,
    private val cacheBudget: CacheBudgetDecision = CacheBudgetManager.computeBudget(DEFAULT_STORAGE_BYTES),
    private val isLowMemoryPressure: () -> Boolean = { false },
) {
    private val appContext = context.applicationContext

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val database: AlunaraDatabase by lazy {
        Room
            .databaseBuilder(
                appContext,
                AlunaraDatabase::class.java,
                "alunara.db",
            ).build()
    }

    val queueStore: QueueStore by lazy {
        RoomQueueStore(database.queueDao())
    }

    val catalogStore: CatalogStore by lazy {
        RoomCatalogStore(database.favoritesDao(), database.historyDao())
    }

    val accountSessionStore: AccountSessionStore by lazy {
        KeystoreAccountSessionStore(appContext)
    }

    val playbackPreferences: PlaybackPreferences by lazy {
        SharedPreferencesPlaybackPreferencesStore(appContext)
    }

    val remotePrefsStore: RemotePreferencesStore by lazy {
        SharedPreferencesRemotePreferencesStore(appContext)
    }

    val musicEngine: MusicEngine by lazy {
        ArchiveTuneMusicAdapter()
    }

    val lyricsEngine: LyricsEngine by lazy {
        val maxEntries =
            if (cacheBudget.dynamicCacheLimitBytes <= 32L * 1024L * 1024L) {
                100
            } else {
                300
            }
        CachedLyricsRepository(
            delegate = ArchiveTuneLyricsAdapter(),
            cacheDao = database.lyricsCacheDao(),
            maxCachedEntries = maxEntries,
        )
    }

    val playbackLifecycleController: SinglePlayerLifecycleController by lazy {
        SinglePlayerLifecycleController(appContext, okHttpClient)
    }

    val appUpdateManager: AppUpdateManager by lazy {
        AppUpdateManager(
            context = appContext,
            okHttpClient = okHttpClient,
            currentVersionName = BuildConfig.VERSION_NAME,
        )
    }

    val playbackSessionManager: PlaybackSessionManager by lazy {
        PlaybackSessionManager(
            musicEngine = musicEngine,
            lyricsEngine = lyricsEngine,
            queueStore = queueStore,
            playerController = playbackLifecycleController,
            catalogStore = catalogStore,
            accountSessionStore = accountSessionStore,
            playbackPreferences = playbackPreferences,
            onBackgroundServiceStateChanged = ::syncPlaybackServiceState,
            isLowMemoryPressure = isLowMemoryPressure,
        )
    }

    val remoteServer: AlunaraRemoteServer by lazy {
        AlunaraRemoteServer(
            scope = ProcessLifecycleOwner.get().lifecycleScope,
            prefsStore = remotePrefsStore,
            sessionManager = playbackSessionManager,
            musicEngine = musicEngine,
            volumeController = AndroidTvVolumeController(appContext),
        )
    }

    val playbackLifecycleObserver: DefaultLifecycleObserver by lazy {
        object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                remoteServer.syncWithPrefs()
            }

            override fun onStop(owner: LifecycleOwner) {
                playbackSessionManager.onAppBackgrounded()
                if (!isPlaybackServiceRunning) {
                    remoteServer.stop()
                }
                // Free decoded artwork RAM immediately when leaving UI for Android TV launcher
                runCatching { SingletonImageLoader.get(appContext).memoryCache?.clear() }
            }
        }
    }

    @Volatile
    private var isPlaybackServiceRunning: Boolean = false

    fun onPlaybackServiceDestroyed() {
        isPlaybackServiceRunning = false
    }

    private fun syncPlaybackServiceState(shouldRunService: Boolean) {
        val intent = Intent(appContext, AlunaraPlaybackService::class.java)
        runCatching {
            if (shouldRunService) {
                if (isPlaybackServiceRunning) return
                val isForeground =
                    ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                if (isForeground) {
                    appContext.startService(intent)
                } else {
                    ContextCompat.startForegroundService(appContext, intent)
                }
                isPlaybackServiceRunning = true
            } else {
                isPlaybackServiceRunning = false
                appContext.stopService(intent)
            }
        }
    }

    companion object {
        private const val DEFAULT_STORAGE_BYTES: Long = 512L * 1024L * 1024L
    }
}
