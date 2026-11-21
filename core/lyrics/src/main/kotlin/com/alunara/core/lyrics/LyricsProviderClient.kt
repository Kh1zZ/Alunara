package com.alunara.core.lyrics

import com.alunara.core.domain.model.LyricsProviderId
import com.alunara.core.domain.model.Track
import moe.rukamori.archivetune.kugou.KuGou
import moe.rukamori.archivetune.lrclib.LrcLib
import moe.rukamori.archivetune.simpmusic.SimpMusicLyrics
import kotlin.math.abs

interface LyricsProviderClient {
    val id: LyricsProviderId

    suspend fun fetchRawLyrics(track: Track): Result<String>
}

object LrcLibProviderClient : LyricsProviderClient {
    override val id: LyricsProviderId = LyricsProviderId.LRCLIB

    override suspend fun fetchRawLyrics(track: Track): Result<String> =
        LrcLib.getLyrics(
            title = track.title,
            artist = track.primaryArtistName,
            duration = track.durationSeconds ?: -1,
            album = track.album?.title,
        )
}

object SimpMusicProviderClient : LyricsProviderClient {
    override val id: LyricsProviderId = LyricsProviderId.SIMPMUSIC

    override suspend fun fetchRawLyrics(track: Track): Result<String> =
        runCatching {
            val tracks = SimpMusicLyrics.getLyricsByVideoId(track.id)
            if (tracks.isEmpty()) {
                throw IllegalStateException("Lyrics unavailable")
            }
            val duration = track.durationSeconds ?: 0
            val bestMatch =
                if (duration > 0 && tracks.size > 1) {
                    tracks.minByOrNull { abs((it.duration ?: 0) - duration) }
                } else {
                    tracks.firstOrNull()
                }
            bestMatch?.richSyncLyrics?.takeIf { it.isNotBlank() }
                ?: bestMatch?.syncedLyrics?.takeIf { it.isNotBlank() }
                ?: bestMatch?.plainLyrics?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Lyrics unavailable")
        }
}

object KuGouProviderClient : LyricsProviderClient {
    override val id: LyricsProviderId = LyricsProviderId.KUGOU

    override suspend fun fetchRawLyrics(track: Track): Result<String> =
        KuGou.getLyrics(
            title = track.title,
            artist = track.primaryArtistName,
            duration = track.durationSeconds ?: -1,
        )
}
