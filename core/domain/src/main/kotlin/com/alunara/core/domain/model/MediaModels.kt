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
    val setVideoId: String? = null,
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
    val playlists: List<PlaylistSummary> = emptyList(),
)

data class AlbumDetail(
    val album: AlbumRef,
    val tracks: List<Track>,
)

data class PlaylistDetail(
    val playlist: PlaylistSummary,
    val description: String? = null,
    val tracks: List<Track>,
)

data class ArtistDetail(
    val artist: ArtistRef,
    val description: String? = null,
    val topSongs: List<Track> = emptyList(),
    val albums: List<AlbumRef> = emptyList(),
    val playlists: List<PlaylistSummary> = emptyList(),
    val channelId: String? = null,
)

data class AccountCredentials(
    val cookie: String,
    val dataSyncId: String? = null,
    val visitorData: String? = null,
    val poToken: String? = null,
) {
    override fun toString(): String = "AccountCredentials(redacted)"
}

data class AccountProfile(
    val accountId: String,
    val name: String,
    val email: String? = null,
    val channelHandle: String? = null,
    val avatarUrl: String? = null,
)

sealed interface AuthSessionState {
    val scope: AccountScope

    data object Guest : AuthSessionState {
        override val scope: AccountScope = AccountScope.Guest
    }

    data class Authenticated(
        val profile: AccountProfile,
    ) : AuthSessionState {
        override val scope: AccountScope = AccountScope.Account(profile.accountId)
    }

    data class Expired(
        val previousAccountId: String?,
        val reason: String,
    ) : AuthSessionState {
        override val scope: AccountScope = AccountScope.Guest
    }
}

data class HomeSection(
    val title: String,
    val songs: List<Track> = emptyList(),
    val albums: List<AlbumRef> = emptyList(),
    val playlists: List<PlaylistSummary> = emptyList(),
)

data class GenreCategory(
    val id: String,
    val title: String,
    val browseId: String,
    val params: String? = null,
    val stripeColorArgb: Long = 0xFF85BDA0L,
)

data class GenreSection(
    val title: String,
    val items: List<GenreCategory> = emptyList(),
)

data class QueueFilterChip(
    val label: String,
    val playlistId: String,
    val params: String? = null,
    val selected: Boolean = false,
)

data class RadioQueueResult(
    val tracks: List<Track>,
    val chips: List<QueueFilterChip> = emptyList(),
    val playlistId: String? = null,
)

data class AudioStreamSource(
    val trackId: String,
    val streamUrl: String,
    val userAgent: String,
    val mimeType: String,
    val bitrateBps: Int,
    val expiresAtEpochMs: Long,
    val clientProfile: String,
    val playbackTrackingUrl: String? = null,
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
