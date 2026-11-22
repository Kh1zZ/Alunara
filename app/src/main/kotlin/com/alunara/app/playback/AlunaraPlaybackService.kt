package com.alunara.app.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.alunara.app.AlunaraApplication
import com.alunara.app.MainActivity
import com.alunara.app.R
import com.alunara.core.domain.model.PlaybackStatus

/**
 * Background audio playback & MediaSession service for Android TV.
 * Publishes track metadata and playback state to Android TV's native Now Playing card
 * and keeps queued audio alive in the background while guarding 1 GB RAM budgets.
 */
@OptIn(UnstableApi::class)
class AlunaraPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val container = (application as AlunaraApplication).container
        val sessionManager = container.playbackSessionManager
        val rawPlayer = container.playbackLifecycleController.obtainPlayer(sessionManager.playerListener)

        val sessionPlayer =
            object : ForwardingPlayer(rawPlayer) {
                override fun play() {
                    val state = sessionManager.uiState.value
                    if (state.status != PlaybackStatus.PLAYING && state.currentTrack != null) {
                        sessionManager.togglePlayPause()
                    }
                }

                override fun pause() {
                    if (wrappedPlayer.isPlaying || wrappedPlayer.playWhenReady) {
                        wrappedPlayer.pause()
                    }
                }

                override fun setPlayWhenReady(playWhenReady: Boolean) {
                    if (playWhenReady) {
                        play()
                    } else {
                        pause()
                    }
                }

                override fun stop() {
                    pause()
                }

                override fun seekTo(positionMs: Long) {
                    sessionManager.seekTo(positionMs)
                }

                override fun seekTo(
                    mediaItemIndex: Int,
                    positionMs: Long,
                ) {
                    sessionManager.seekTo(positionMs)
                }

                override fun seekToNext() {
                    sessionManager.skipNext()
                }

                override fun seekToNextMediaItem() {
                    sessionManager.skipNext()
                }

                override fun seekToPrevious() {
                    sessionManager.skipPrevious()
                }

                override fun seekToPreviousMediaItem() {
                    sessionManager.skipPrevious()
                }

                override fun hasNextMediaItem(): Boolean {
                    val q = sessionManager.uiState.value.queueState
                    return q.currentIndex in 0 until q.items.lastIndex
                }

                override fun hasPreviousMediaItem(): Boolean = sessionManager.uiState.value.queueState.items.isNotEmpty()

                override fun getAvailableCommands(): Player.Commands =
                    super.getAvailableCommands()
                        .buildUpon()
                        .addAll(
                            Player.COMMAND_PLAY_PAUSE,
                            Player.COMMAND_STOP,
                            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                            Player.COMMAND_SEEK_TO_NEXT,
                            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                            Player.COMMAND_SEEK_TO_PREVIOUS,
                            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                            Player.COMMAND_GET_METADATA,
                        ).build()

                override fun isCommandAvailable(command: Int): Boolean = getAvailableCommands().contains(command)
            }

        val session =
            MediaSession
                .Builder(this, sessionPlayer)
                .setSessionActivity(openNowPlayingPendingIntent())
                .build()
        mediaSession = session
        addSession(session)
        ensureForegroundStarted()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        ensureForegroundStarted()
        val result = super.onStartCommand(intent, flags, startId)
        val session = mediaSession
        val container = (application as AlunaraApplication).container
        if (session != null &&
            container.playbackSessionManager.uiState.value.currentTrack != null &&
            container.playbackLifecycleController.hasActivePlayer
        ) {
            onUpdateNotification(session, true)
        } else {
            ensureForegroundStarted()
        }
        return result
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onUpdateNotification(
        session: MediaSession,
        startInForegroundRequired: Boolean,
    ) {
        val container = (application as AlunaraApplication).container
        val keepForegroundAlive =
            container.playbackSessionManager.uiState.value.currentTrack != null &&
                container.playbackLifecycleController.hasActivePlayer
        super.onUpdateNotification(session, startInForegroundRequired || keepForegroundAlive)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val container = (application as AlunaraApplication).container
        if (container.playbackSessionManager.uiState.value.currentTrack == null) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.let {
            removeSession(it)
            it.release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private fun ensureForegroundStarted() {
        runCatching {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.app_name),
                        NotificationManager.IMPORTANCE_LOW,
                    ),
                )
            }
            val track =
                (application as? AlunaraApplication)
                    ?.container
                    ?.playbackSessionManager
                    ?.uiState
                    ?.value
                    ?.currentTrack
            val notification =
                NotificationCompat
                    .Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(track?.title ?: getString(R.string.app_name))
                    .setContentText(track?.primaryArtistName.orEmpty())
                    .setContentIntent(openNowPlayingPendingIntent())
                    .setOngoing(true)
                    .setSilent(true)
                    .build()
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    private fun openNowPlayingPendingIntent(): PendingIntent {
        val intent =
            Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_NOW_PLAYING, true)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        return PendingIntent.getActivity(
            this,
            SESSION_ACTIVITY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val CHANNEL_ID = "default_channel_id"
        private const val FOREGROUND_NOTIFICATION_ID = 1001
        private const val SESSION_ACTIVITY_REQUEST_CODE = 1001
    }
}
