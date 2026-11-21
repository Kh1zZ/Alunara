package com.alunara.core.domain.model

data class ArtistRef(
    val id: String?,
    val name: String,
    val thumbnailUrl: String? = null,
)

data class AlbumRef(
    val id: String,
    val playlistId: String? = null,
    val title: String,
    val artists: List<ArtistRef> = emptyList(),
    val year: Int? = null,
    val thumbnailUrl: String? = null,
)

data class Track(
    val id: String,
    val title: String,
    val artists: List<ArtistRef> = emptyList(),
    val album: AlbumRef? = null,
    val durationSeconds: Int? = null,
    val thumbnailUrl: String? = null,
    val isExplicit: Boolean = false,
) {
    val primaryArtistName: String
        get() = artists.joinToString(", ") { it.name }.ifBlank { "" }
}

data class PlaylistSummary(
    val id: String,
    val title: String,
    val author: String? = null,
    val trackCountText: String? = null,
    val thumbnailUrl: String? = null,
)

data class SearchResults(
    val songs: List<Track> = emptyList(),
    val albums: List<AlbumRef> = emptyList(),
    val artists: List<ArtistRef> = emptyList(),
)

data class HomeSection(
    val title: String,
    val songs: List<Track> = emptyList(),
    val albums: List<AlbumRef> = emptyList(),
)

data class AudioStreamSource(
    val trackId: String,
    val streamUrl: String,
    val userAgent: String,
    val mimeType: String,
    val bitrateBps: Int,
    val expiresAtEpochMs: Long,
    val clientProfile: String,
)

enum class PlaybackStatus {
    IDLE,
    RESOLVING,
    BUFFERING,
    PLAYING,
    PAUSED,
    ENDED,
    ERROR,
}

sealed interface AccountScope {
    val storageKey: String

    data object Guest : AccountScope {
        override val storageKey: String = "guest"
    }

    data class Account(
        val accountId: String,
    ) : AccountScope {
        override val storageKey: String = "account:$accountId"
    }
}
