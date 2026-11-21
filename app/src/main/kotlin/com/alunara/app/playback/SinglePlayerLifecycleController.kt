package com.alunara.app.playback

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.alunara.core.domain.contract.PlaybackLifecycleController
import com.alunara.core.domain.media.ArtworkTier
import com.alunara.core.domain.media.ArtworkUrlResolver
import com.alunara.core.domain.model.AudioStreamSource
import com.alunara.core.domain.model.Track
import okhttp3.Call
import okhttp3.OkHttpClient

interface AudioPlayerControl : PlaybackLifecycleController {
    val hasActivePlayer: Boolean

    fun prepareAudioStream(
        source: AudioStreamSource,
        startPositionMs: Long,
        playWhenReady: Boolean,
        listener: Player.Listener,
        track: Track? = null,
    )

    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    fun currentPositionMs(): Long

    fun durationMs(): Long

    fun isPlaying(): Boolean
}

/**
 * Enforces PRD §6.1 & §6.3:
 * - At most one active audio-only ExoPlayer instance per process (video tracks disabled).
 * - Conservative buffering tuned for 1 GB RAM / 192 MB heap Android TV targets.
 * - No audio disk cache (FG-03).
 * - AudioFocus + noisy-becoming handling for reliable background playback.
 */
class SinglePlayerLifecycleController(
    context: Context,
    private val okHttpClient: OkHttpClient,
    private val onPersistPositionMs: (Long) -> Unit = {},
) : DefaultLifecycleObserver,
    AudioPlayerControl {
    private val appContext = context.applicationContext
    private var player: ExoPlayer? = null
    private var attachedListener: Player.Listener? = null

    override val hasActivePlayer: Boolean
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
        val audioAttributes =
            AudioAttributes
                .Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()

        return ExoPlayer
            .Builder(appContext)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
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
    override fun prepareAudioStream(
        source: AudioStreamSource,
        startPositionMs: Long,
        playWhenReady: Boolean,
        listener: Player.Listener,
        track: Track?,
    ) {
        val activePlayer = obtainPlayer(listener)
        val dataSourceFactory =
            OkHttpDataSource
                .Factory(okHttpClient as Call.Factory)
                .setUserAgent(source.userAgent)
        val mediaItem = buildMediaItem(source.streamUrl, source.trackId, track)
        val mediaSource =
            ProgressiveMediaSource
                .Factory(dataSourceFactory)
                .createMediaSource(mediaItem)
        activePlayer.setMediaSource(mediaSource, startPositionMs.coerceAtLeast(0L))
        activePlayer.playWhenReady = playWhenReady
        activePlayer.prepare()
    }

    @Synchronized
    fun updateMediaMetadata(track: Track) {
        val activePlayer = player ?: return
        val currentItem = activePlayer.currentMediaItem ?: return
        val updated =
            currentItem
                .buildUpon()
                .setMediaId(track.id)
                .setMediaMetadata(buildMediaMetadata(track))
                .build()
        activePlayer.replaceMediaItem(0, updated)
    }

    @Synchronized
    override fun play() {
        player?.play()
    }

    @Synchronized
    override fun pause() {
        player?.pause()
    }

    @Synchronized
    override fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs.coerceAtLeast(0L))
    }

    @Synchronized
    override fun currentPositionMs(): Long = player?.currentPosition?.coerceAtLeast(0L) ?: 0L

    @Synchronized
    override fun durationMs(): Long = player?.duration?.takeIf { it > 0L && it != C.TIME_UNSET } ?: 0L

    @Synchronized
    override fun isPlaying(): Boolean = player?.isPlaying == true

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

    private fun buildMediaItem(
        streamUrl: String,
        trackId: String,
        track: Track?,
    ): MediaItem {
        val builder = MediaItem.Builder().setUri(streamUrl).setMediaId(trackId)
        if (track != null) {
            builder.setMediaMetadata(buildMediaMetadata(track))
        }
        return builder.build()
    }

    private fun buildMediaMetadata(track: Track): MediaMetadata {
        val artistLine = track.artists.joinToString(", ") { it.name }.ifBlank { null }
        val artworkUri =
            ArtworkUrlResolver
                .resolve(track.thumbnailUrl, ArtworkTier.CARD)
                ?.let { raw -> runCatching { Uri.parse(raw) }.getOrNull() }
        return MediaMetadata
            .Builder()
            .setTitle(track.title)
            .setArtist(artistLine)
            .setAlbumTitle(track.album?.title)
            .setArtworkUri(artworkUri)
            .build()
    }
}
