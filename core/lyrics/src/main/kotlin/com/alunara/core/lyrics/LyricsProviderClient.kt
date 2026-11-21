package com.alunara.core.lyrics

import com.alunara.core.domain.model.LyricsProviderId
import com.alunara.core.domain.model.Track
import moe.rukamori.archivetune.kugou.KuGou
import moe.rukamori.archivetune.lrclib.LrcLib
import moe.rukamori.archivetune.simpmusic.SimpMusicLyrics

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
        SimpMusicLyrics.getLyrics(
            videoId = track.id,
            duration = track.durationSeconds ?: 0,
        )
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
