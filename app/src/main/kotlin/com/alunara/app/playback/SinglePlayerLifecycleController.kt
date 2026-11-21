package com.alunara.app.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.alunara.core.domain.contract.PlaybackLifecycleController

/**
 * Enforces PRD §6.1 & §6.3:
 * - At most one active audio-only ExoPlayer instance per process (video tracks disabled).
 * - Immediate stop and release when the app leaves foreground, preserving queue snapshot.
 */
class SinglePlayerLifecycleController(
    context: Context,
    private val onPersistPositionMs: (Long) -> Unit = {},
) : DefaultLifecycleObserver,
    PlaybackLifecycleController {
    private val appContext = context.applicationContext
    private var player: ExoPlayer? = null

    val hasActivePlayer: Boolean
        get() = player != null

    @OptIn(UnstableApi::class)
    @Synchronized
    fun obtainPlayer(): ExoPlayer {
        player?.let { return it }
        val trackSelector =
            DefaultTrackSelector(appContext).apply {
                setParameters(
                    buildUponParameters()
                        .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true),
                )
            }
        return ExoPlayer
            .Builder(appContext)
            .setTrackSelector(trackSelector)
            .build()
            .also { player = it }
    }

    override fun onStop(owner: LifecycleOwner) {
        onAppBackgroundedOrStopped()
    }

    @Synchronized
    override fun onAppBackgroundedOrStopped() {
        val current = player ?: return
        val lastPositionMs = current.currentPosition.coerceAtLeast(0L)
        onPersistPositionMs(lastPositionMs)
        current.stop()
        current.release()
        player = null
    }
}
