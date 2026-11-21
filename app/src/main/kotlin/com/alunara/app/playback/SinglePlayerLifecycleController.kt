package com.alunara.app.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.alunara.core.domain.contract.PlaybackLifecycleController
import com.alunara.core.domain.model.AudioStreamSource
import okhttp3.Call
import okhttp3.OkHttpClient

/**
 * Enforces PRD §6.1 & §6.3:
 * - At most one active audio-only ExoPlayer instance per process (video tracks disabled).
 * - Conservative buffering tuned for 1 GB RAM / 192 MB heap Android TV targets.
 * - No audio disk cache (FG-03).
 * - Immediate stop and release when the app leaves foreground.
 */
class SinglePlayerLifecycleController(
    context: Context,
    private val okHttpClient: OkHttpClient,
    private val onPersistPositionMs: (Long) -> Unit = {},
) : DefaultLifecycleObserver,
    PlaybackLifecycleController {
    private val appContext = context.applicationContext
    private var player: ExoPlayer? = null
    private var attachedListener: Player.Listener? = null

    val hasActivePlayer: Boolean
        get() = player != null

    @OptIn(UnstableApi::class)
    @Synchronized
    fun obtainPlayer(listener: Player.Listener? = null): ExoPlayer {
        player?.let { existing ->
            if (listener != null && attachedListener !== listener) {
                attachedListener?.let(existing::removeListener)
                existing.addListener(listener)
                attachedListener = listener
            }
            return existing
        }

        val trackSelector =
            DefaultTrackSelector(appContext).apply {
                setParameters(
                    buildUponParameters()
                        .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true),
                )
            }
        val loadControl =
            DefaultLoadControl
                .Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 10_000,
                    /* maxBufferMs = */ 25_000,
                    /* bufferForPlaybackMs = */ 1_500,
                    /* bufferForPlaybackAfterRebufferMs = */ 3_000,
                ).build()

        return ExoPlayer
            .Builder(appContext)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build()
            .also { created ->
                if (listener != null) {
                    created.addListener(listener)
                    attachedListener = listener
                }
                player = created
            }
    }

    @OptIn(UnstableApi::class)
    @Synchronized
    fun prepareAudioStream(
        source: AudioStreamSource,
        startPositionMs: Long,
        playWhenReady: Boolean,
        listener: Player.Listener,
    ) {
        val activePlayer = obtainPlayer(listener)
        val dataSourceFactory =
            OkHttpDataSource
                .Factory(okHttpClient as Call.Factory)
                .setUserAgent(source.userAgent)
        val mediaSource =
            ProgressiveMediaSource
                .Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(source.streamUrl))
        activePlayer.setMediaSource(mediaSource, startPositionMs.coerceAtLeast(0L))
        activePlayer.playWhenReady = playWhenReady
        activePlayer.prepare()
    }

    @Synchronized
    fun play() {
        player?.play()
    }

    @Synchronized
    fun pause() {
        player?.pause()
    }

    @Synchronized
    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs.coerceAtLeast(0L))
    }

    @Synchronized
    fun currentPositionMs(): Long = player?.currentPosition?.coerceAtLeast(0L) ?: 0L

    @Synchronized
    fun durationMs(): Long = player?.duration?.takeIf { it > 0L && it != C.TIME_UNSET } ?: 0L

    @Synchronized
    fun isPlaying(): Boolean = player?.isPlaying == true

    override fun onStop(owner: LifecycleOwner) {
        onAppBackgroundedOrStopped()
    }

    @Synchronized
    override fun onAppBackgroundedOrStopped() {
        val current = player ?: return
        val lastPositionMs = current.currentPosition.coerceAtLeast(0L)
        onPersistPositionMs(lastPositionMs)
        attachedListener?.let(current::removeListener)
        attachedListener = null
        current.stop()
        current.release()
        player = null
    }
}
