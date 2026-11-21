package com.alunara.app

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.room.Room
import com.alunara.app.playback.PlaybackSessionManager
import com.alunara.app.playback.SinglePlayerLifecycleController
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

    val musicEngine: MusicEngine by lazy {
        ArchiveTuneMusicAdapter()
    }

    val lyricsEngine: LyricsEngine by lazy {
        CachedLyricsRepository(
            delegate = ArchiveTuneLyricsAdapter(),
            cacheDao = database.lyricsCacheDao(),
        )
    }

    val playbackLifecycleController: SinglePlayerLifecycleController by lazy {
        SinglePlayerLifecycleController(appContext, okHttpClient)
    }

    val playbackSessionManager: PlaybackSessionManager by lazy {
        PlaybackSessionManager(
            musicEngine = musicEngine,
            lyricsEngine = lyricsEngine,
            queueStore = queueStore,
            playerController = playbackLifecycleController,
            catalogStore = catalogStore,
            accountSessionStore = accountSessionStore,
        )
    }

    val playbackLifecycleObserver: DefaultLifecycleObserver by lazy {
        object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                playbackSessionManager.onAppBackgroundedOrStopped()
            }
        }
    }
}
