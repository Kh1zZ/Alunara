package com.alunara.core.domain.contract

import com.alunara.core.domain.model.AccountCredentials
import com.alunara.core.domain.model.AccountProfile
import com.alunara.core.domain.model.AccountScope
import com.alunara.core.domain.model.AlbumDetail
import com.alunara.core.domain.model.ArtistDetail
import com.alunara.core.domain.model.AudioStreamSource
import com.alunara.core.domain.model.HomeSection
import com.alunara.core.domain.model.LyricsDocument
import com.alunara.core.domain.model.LyricsProviderId
import com.alunara.core.domain.model.PlaylistDetail
import com.alunara.core.domain.model.PlaylistSummary
import com.alunara.core.domain.model.QueueState
import com.alunara.core.domain.model.RadioQueueResult
import com.alunara.core.domain.model.SearchResults
import com.alunara.core.domain.model.Track
import com.alunara.core.domain.policy.AudioQualityPreset

interface MusicEngine {
    suspend fun searchGuest(query: String): Result<SearchResults>

    suspend fun fetchGuestHome(): Result<List<HomeSection>>

    suspend fun resolveAudioStream(trackId: String): Result<AudioStreamSource>

    suspend fun resolveAudioStream(
        trackId: String,
        quality: AudioQualityPreset,
    ): Result<AudioStreamSource> = resolveAudioStream(trackId)

    fun invalidateAudioStream(source: AudioStreamSource, quality: AudioQualityPreset) {}

    suspend fun fetchAlbumDetail(albumId: String): Result<AlbumDetail> =
        Result.failure(UnsupportedOperationException("Album detail not implemented"))

    suspend fun fetchPlaylistDetail(playlistId: String): Result<PlaylistDetail> =
        Result.failure(UnsupportedOperationException("Playlist detail not implemented"))

    suspend fun fetchArtistDetail(artistId: String): Result<ArtistDetail> =
        Result.failure(UnsupportedOperationException("Artist detail not implemented"))

    suspend fun fetchRadioTracks(
        seedTrackId: String,
        playlistId: String? = null,
    ): Result<List<Track>> = Result.success(emptyList())

    suspend fun fetchRadioQueue(
        seedTrackId: String,
        playlistId: String? = null,
        params: String? = null,
    ): Result<RadioQueueResult> =
        fetchRadioTracks(seedTrackId, playlistId).map { tracks ->
            RadioQueueResult(tracks = tracks, playlistId = playlistId)
        }

    suspend fun verifyAccountSession(credentials: AccountCredentials): Result<AccountProfile> =
        Result.failure(UnsupportedOperationException("Account session not supported"))

    fun clearAccountSession() {}

    suspend fun fetchAccountPlaylists(): Result<List<PlaylistSummary>> = Result.success(emptyList())

    suspend fun fetchLikedMusicTracks(): Result<List<Track>> = Result.success(emptyList())

    suspend fun fetchAccountHistoryTracks(): Result<List<Track>> = Result.success(emptyList())

    suspend fun setRemoteTrackLike(
        trackId: String,
        liked: Boolean,
    ): Result<Unit> = Result.success(Unit)

    suspend fun createAccountPlaylist(
        title: String,
        initialTrackIds: List<String> = emptyList(),
    ): Result<String> = Result.failure(UnsupportedOperationException("Create playlist not supported"))

    suspend fun addTrackToAccountPlaylist(
        playlistId: String,
        trackId: String,
    ): Result<Unit> = Result.success(Unit)

    suspend fun removeTrackFromAccountPlaylist(
        playlistId: String,
        trackId: String,
        setVideoId: String? = null,
    ): Result<Unit> = Result.success(Unit)

    suspend fun setRemotePlaylistSaved(
        playlistId: String,
        saved: Boolean,
    ): Result<Unit> = Result.success(Unit)

    suspend fun setRemoteArtistSubscription(
        artistId: String,
        channelId: String? = null,
        subscribed: Boolean,
    ): Result<Unit> = Result.success(Unit)

    suspend fun reportPlaybackToAccount(
        trackId: String,
        playbackTrackingUrl: String?,
        playlistId: String? = null,
    ): Result<Unit> = Result.success(Unit)

    fun setContentLanguage(languageTag: String) = Unit
}

interface LyricsEngine {
    suspend fun fetchLyrics(
        track: Track,
        preferredProvider: LyricsProviderId? = null,
    ): LyricsDocument
}

interface QueueStore {
    suspend fun loadQueue(): QueueState

    suspend fun saveQueue(state: QueueState)
}

interface CatalogStore {
    suspend fun getFavorites(scope: AccountScope): List<Track>

    suspend fun isFavorite(
        trackId: String,
        scope: AccountScope,
    ): Boolean

    suspend fun toggleFavorite(
        track: Track,
        scope: AccountScope,
    ): Boolean

    suspend fun recordHistory(
        track: Track,
        scope: AccountScope,
    )

    suspend fun getRecentHistory(
        scope: AccountScope,
        limit: Int = 50,
    ): List<Track>

    suspend fun clearHistory(scope: AccountScope)

    suspend fun clearAccountData(scope: AccountScope.Account)
}

interface AccountSessionStore {
    suspend fun loadSession(): Pair<AccountProfile, AccountCredentials>?

    suspend fun saveSession(
        profile: AccountProfile,
        credentials: AccountCredentials,
    )

    suspend fun clearSession()
}

interface PlaybackLifecycleController {
    fun onAppBackgroundedOrStopped()
}
