package com.alunara.core.music

import com.alunara.core.domain.contract.MusicEngine
import com.alunara.core.domain.model.AccountCredentials
import com.alunara.core.domain.model.AccountProfile
import com.alunara.core.domain.model.AlbumDetail
import com.alunara.core.domain.model.AlbumRef
import com.alunara.core.domain.model.ArtistDetail
import com.alunara.core.domain.model.ArtistRef
import com.alunara.core.domain.model.AudioStreamSource
import com.alunara.core.domain.model.HomeSection
import com.alunara.core.domain.model.PlaylistDetail
import com.alunara.core.domain.model.PlaylistSummary
import com.alunara.core.domain.model.QueueFilterChip
import com.alunara.core.domain.model.RadioQueueResult
import com.alunara.core.domain.model.SearchResults
import com.alunara.core.domain.model.Track
import com.alunara.core.domain.policy.AudioFormatCandidate
import com.alunara.core.domain.policy.AudioFormatSelector
import com.alunara.core.domain.policy.AudioQualityPreset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.rukamori.archivetune.innertube.MusicBackend
import moe.rukamori.archivetune.innertube.NetworkGatekeeperControl
import moe.rukamori.archivetune.innertube.NewPipeUtils
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse
import moe.rukamori.archivetune.innertube.pages.NextResult
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thin isolation adapter around ArchiveTune Core ([MusicBackend]).
 * Unlocks [NetworkGatekeeperControl] lazily on first request and normalizes InnerTube models
 * into Alunara domain models without leaking upstream types.
 */
class ArchiveTuneMusicAdapter internal constructor(
    private val backend: MusicBackend,
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() },
    private val fallbackStreamResolver: suspend (PlayerResponse.StreamingData.Format, String, YouTubeClient) -> Result<String> =
        { fmt, vid, client -> NewPipeUtils.getStreamUrl(fmt, vid, client, backend.currentPlaybackAuthState()) },
    private val signatureTimestampProvider: suspend (String) -> Int? = { vid ->
        NewPipeUtils.getSignatureTimestamp(vid).getOrNull()
    },
    private val allowConnectionsAction: () -> Unit,
) : MusicEngine {
    constructor() : this(
        backend = YouTubeMusicBackend,
        allowConnectionsAction = { NetworkGatekeeperControl.allowConnections() },
    )

    private val gatekeeperUnlocked = AtomicBoolean(false)
    private val visitorDataMutex = Mutex()
    private val streamCacheLock = Any()
    private val streamSourceCache =
        object : LinkedHashMap<String, AudioStreamSource>(8, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AudioStreamSource>?): Boolean =
                size > MAX_STREAM_CACHE_ENTRIES
        }

    @Volatile
    private var lastSuccessfulClient: YouTubeClient? = null

    fun isGatekeeperUnlocked(): Boolean = gatekeeperUnlocked.get()

    private fun ensureGatekeeperUnlocked() {
        if (gatekeeperUnlocked.compareAndSet(false, true)) {
            allowConnectionsAction()
        }
    }

    override suspend fun searchGuest(query: String): Result<SearchResults> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            return Result.success(SearchResults())
        }
        ensureGatekeeperUnlocked()
        backend.useLoginForBrowse = backend.hasLoginCookie()

        return backend.searchSummary(normalizedQuery).map { page ->
            val songs = mutableListOf<Track>()
            val albums = mutableListOf<AlbumRef>()
            val artists = mutableListOf<ArtistRef>()
            val playlists = mutableListOf<PlaylistSummary>()

            page.summaries
                .asSequence()
                .flatMap { it.items.asSequence() }
                .forEach { item ->
                    when (item) {
                        is SongItem -> songs += item.toDomainTrack()
                        is AlbumItem -> albums += item.toDomainAlbum()
                        is ArtistItem -> artists += item.toDomainArtist()
                        is PlaylistItem -> playlists += item.toDomainPlaylist()
                        else -> Unit
                    }
                }

            SearchResults(
                songs = songs.distinctBy { it.id },
                albums = albums.distinctBy { it.id },
                artists = artists.distinctBy { it.id ?: it.name },
                playlists = playlists.distinctBy { it.id },
            )
        }
    }

    override suspend fun fetchGuestHome(): Result<List<HomeSection>> =
        coroutineScope {
            ensureGatekeeperUnlocked()
            backend.useLoginForBrowse = backend.hasLoginCookie()

            val homeDeferred = async { backend.home() }
            val chartsDeferred = async { safeOptionalSuspend { backend.getChartsPage() } }

            homeDeferred.await().map { firstPage ->
                val sections = mutableListOf<HomeSection>()
                val seenTitles = mutableSetOf<String>()

                fun appendSection(title: String, items: List<YTItem>) {
                    val normalized = items.toHomeSection(title) ?: return
                    if (seenTitles.add(normalized.title)) {
                        sections += normalized
                    }
                }

                firstPage.sections.forEach { appendSection(it.title, it.items) }

                val continuation = firstPage.continuation
                if (continuation != null && sections.size < 6) {
                    safeOptionalSuspend { backend.home(continuation = continuation) }
                        ?.sections
                        ?.forEach { appendSection(it.title, it.items) }
                }

                chartsDeferred.await()
                    ?.sections
                    ?.forEach { appendSection(it.title, it.items) }

                sections
            }
        }

    private fun List<YTItem>.toHomeSection(title: String): HomeSection? {
        val songs = filterIsInstance<SongItem>().map { it.toDomainTrack() }.distinctBy { it.id }
        val albums = filterIsInstance<AlbumItem>().map { it.toDomainAlbum() }.distinctBy { it.id }
        val playlists = filterIsInstance<PlaylistItem>().map { it.toDomainPlaylist() }.distinctBy { it.id }
        return if (songs.isEmpty() && albums.isEmpty() && playlists.isEmpty()) {
            null
        } else {
            HomeSection(
                title = title,
                songs = songs,
                albums = albums,
                playlists = playlists,
            )
        }
    }

    private suspend inline fun <T> safeOptionalSuspend(crossinline block: suspend () -> Result<T>): T? =
        try {
            block().getOrNull()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Throwable) {
            null
        }

    override suspend fun fetchAlbumDetail(albumId: String): Result<AlbumDetail> {
        val normalizedId = albumId.trim()
        if (normalizedId.isEmpty()) {
            return Result.failure(IllegalArgumentException("Album id must not be blank"))
        }
        ensureGatekeeperUnlocked()
        backend.useLoginForBrowse = backend.hasLoginCookie()

        return backend.album(normalizedId, withSongs = true).map { page ->
            AlbumDetail(
                album = page.album.toDomainAlbum(),
                tracks = page.songs.map { it.toDomainTrack() }.distinctBy { it.id },
            )
        }
    }

    override suspend fun fetchPlaylistDetail(playlistId: String): Result<PlaylistDetail> {
        val normalizedId = playlistId.trim().removePrefix("VL")
        if (normalizedId.isEmpty()) {
            return Result.failure(IllegalArgumentException("Playlist id must not be blank"))
        }
        ensureGatekeeperUnlocked()
        backend.useLoginForBrowse = backend.hasLoginCookie()

        return backend.playlist(normalizedId).map { page ->
            PlaylistDetail(
                playlist = page.playlist.toDomainPlaylist(),
                description = page.playlist.description,
                tracks = page.songs.map { it.toDomainTrack() }.distinctBy { it.id },
            )
        }
    }

    override suspend fun fetchArtistDetail(artistId: String): Result<ArtistDetail> {
        val normalizedId = artistId.trim()
        if (normalizedId.isEmpty()) {
            return Result.failure(IllegalArgumentException("Artist id must not be blank"))
        }
        ensureGatekeeperUnlocked()
        backend.useLoginForBrowse = backend.hasLoginCookie()

        return backend.artist(normalizedId).map { page ->
            val topSongs = mutableListOf<Track>()
            val albums = mutableListOf<AlbumRef>()
            val playlists = mutableListOf<PlaylistSummary>()

            page.sections
                .asSequence()
                .flatMap { it.items.asSequence() }
                .forEach { item ->
                    when (item) {
                        is SongItem -> topSongs += item.toDomainTrack()
                        is AlbumItem -> albums += item.toDomainAlbum()
                        is PlaylistItem -> playlists += item.toDomainPlaylist()
                        else -> Unit
                    }
                }

            ArtistDetail(
                artist = page.artist.toDomainArtist(),
                description = page.description,
                topSongs = topSongs.distinctBy { it.id },
                albums = albums.distinctBy { it.id },
                playlists = playlists.distinctBy { it.id },
            )
        }
    }

    override suspend fun fetchRadioTracks(
        seedTrackId: String,
        playlistId: String?,
    ): Result<List<Track>> = fetchRadioQueue(seedTrackId = seedTrackId, playlistId = playlistId).map { it.tracks }

    override suspend fun fetchRadioQueue(
        seedTrackId: String,
        playlistId: String?,
        params: String?,
    ): Result<RadioQueueResult> {
        val normalizedId = seedTrackId.trim()
        if (normalizedId.isEmpty()) {
            return Result.failure(IllegalArgumentException("Seed track id must not be blank"))
        }
        ensureGatekeeperUnlocked()
        backend.useLoginForBrowse = backend.hasLoginCookie()

        val resolvedPlaylistId = playlistId?.trim()?.takeIf { it.isNotEmpty() } ?: "RDAMVM$normalizedId"
        val primaryEndpoint = WatchEndpoint(videoId = normalizedId, playlistId = resolvedPlaylistId, params = params)
        val primaryResult = backend.next(primaryEndpoint)
        val primaryMapped = primaryResult.getOrNull()?.toRadioQueueResult(resolvedPlaylistId)
        if (primaryMapped != null && (params != null || primaryMapped.tracks.any { it.id != normalizedId })) {
            return Result.success(primaryMapped)
        }
        if (params != null) {
            return primaryResult.map { it.toRadioQueueResult(resolvedPlaylistId) }
        }

        val fallbackResult = backend.next(WatchEndpoint(videoId = normalizedId))
        return fallbackResult.map { it.toRadioQueueResult(resolvedPlaylistId) }
    }

    private fun NextResult.toRadioQueueResult(fallbackPlaylistId: String): RadioQueueResult =
        RadioQueueResult(
            tracks =
                items
                    .map { it.toDomainTrack() }
                    .distinctBy { it.id }
                    .take(25),
            chips =
                queueChips.map { chip ->
                    QueueFilterChip(
                        label = chip.title,
                        playlistId = chip.endpoint.playlistId ?: fallbackPlaylistId,
                        params = chip.endpoint.params,
                        selected = chip.selected,
                    )
                },
            playlistId = endpoint.playlistId ?: fallbackPlaylistId,
        )

    override suspend fun verifyAccountSession(credentials: AccountCredentials): Result<AccountProfile> {
        ensureGatekeeperUnlocked()
        val candidateAuth =
            PlaybackAuthState(
                cookie = credentials.cookie,
                visitorData = credentials.visitorData ?: backend.visitorData,
                dataSyncId = credentials.dataSyncId,
                poToken = credentials.poToken,
            ).normalized()
        if (!candidateAuth.hasLoginCookie) {
            return Result.failure(IllegalArgumentException("Missing required YouTube login cookie (SAPISID)"))
        }

        backend.authState = candidateAuth
        backend.useLoginForBrowse = true
        return backend.accountInfo().fold(
            onSuccess = { info ->
                val stableId =
                    info.channelHandle?.takeIf { it.isNotBlank() }
                        ?: info.email?.takeIf { it.isNotBlank() }
                        ?: candidateAuth.fingerprint.take(16)
                Result.success(
                    AccountProfile(
                        accountId = stableId,
                        name = info.name,
                        email = info.email,
                        channelHandle = info.channelHandle,
                        avatarUrl = info.thumbnailUrl,
                    ),
                )
            },
            onFailure = { error ->
                clearAccountSession()
                Result.failure(error)
            },
        )
    }

    override fun clearAccountSession() {
        val currentVisitor = backend.authState.visitorData ?: backend.visitorData
        backend.authState = PlaybackAuthState(visitorData = currentVisitor)
        backend.useLoginForBrowse = false
    }

    override suspend fun fetchAccountPlaylists(): Result<List<PlaylistSummary>> {
        if (!backend.hasLoginCookie()) {
            return Result.failure(IllegalStateException("Account login required"))
        }
        ensureGatekeeperUnlocked()
        backend.useLoginForBrowse = true
        return backend.library("FEmusic_liked_playlists", tabIndex = 0).map { page ->
            page.items
                .filterIsInstance<PlaylistItem>()
                .filter { it.id.removePrefix("VL") !in SYSTEM_PLAYLIST_IDS }
                .map { it.toDomainPlaylist() }
                .distinctBy { it.id }
        }
    }

    override suspend fun fetchLikedMusicTracks(): Result<List<Track>> {
        if (!backend.hasLoginCookie()) {
            return Result.failure(IllegalStateException("Account login required"))
        }
        ensureGatekeeperUnlocked()
        backend.useLoginForBrowse = true
        return backend.playlist("LM").map { page ->
            page.songs.map { it.toDomainTrack() }.distinctBy { it.id }
        }
    }

    override suspend fun fetchAccountHistoryTracks(): Result<List<Track>> {
        if (!backend.hasLoginCookie()) {
            return Result.failure(IllegalStateException("Account login required"))
        }
        ensureGatekeeperUnlocked()
        backend.useLoginForBrowse = true
        return backend.musicHistory().map { page ->
            page.sections
                .orEmpty()
                .flatMap { it.songs }
                .map { it.toDomainTrack() }
                .distinctBy { it.id }
        }
    }

    override suspend fun setRemoteTrackLike(
        trackId: String,
        liked: Boolean,
    ): Result<Unit> {
        if (!backend.hasLoginCookie()) {
            return Result.failure(IllegalStateException("Account login required"))
        }
        ensureGatekeeperUnlocked()
        return backend.likeVideo(trackId.trim(), liked)
    }

    private companion object {
        const val MAX_STREAM_CACHE_ENTRIES = 4
        const val STREAM_CACHE_SAFETY_WINDOW_MS = 30_000L
        val SYSTEM_PLAYLIST_IDS = setOf("LM", "SE")
    }

    override suspend fun resolveAudioStream(trackId: String): Result<AudioStreamSource> =
        resolveAudioStream(trackId = trackId, quality = AudioQualityPreset.AUTO)

    override suspend fun resolveAudioStream(
        trackId: String,
        quality: AudioQualityPreset,
    ): Result<AudioStreamSource> {
        val normalizedId = trackId.trim()
        if (normalizedId.isEmpty()) {
            return Result.failure(IllegalArgumentException("Track id must not be blank"))
        }

        val cacheKey = "$normalizedId:${quality.name}"
        synchronized(streamCacheLock) {
            val cached = streamSourceCache[cacheKey]
            if (cached != null && cached.expiresAtEpochMs > nowEpochMs() + STREAM_CACHE_SAFETY_WINDOW_MS) {
                return Result.success(cached)
            }
            streamSourceCache.remove(cacheKey)
        }

        ensureGatekeeperUnlocked()
        val useLoginContext = backend.hasPlaybackLoginContext()
        val playbackAuth = if (useLoginContext) backend.currentPlaybackAuthState() else ensureGuestAuthState()
        val defaultClients =
            listOf(
                YouTubeClient.VISIONOS,
                YouTubeClient.IOS,
                YouTubeClient.TVHTML5_SIMPLY,
                YouTubeClient.ANDROID_VR_1_65_10,
            )
        val preferred = lastSuccessfulClient
        val guestClients =
            if (preferred != null && preferred in defaultClients) {
                listOf(preferred) + defaultClients.filter { it != preferred }
            } else {
                defaultClients
            }

        var lastFailureReason: String? = null

        for (client in guestClients) {
            val resolved =
                tryClientForStream(
                    trackId = normalizedId,
                    client = client,
                    signatureTimestamp = null,
                    authState = playbackAuth,
                    setLogin = useLoginContext,
                    requireDirectUrl = true,
                    quality = quality,
                )
            if (resolved.isSuccess) {
                lastSuccessfulClient = client
                resolved.getOrNull()?.let { src ->
                    synchronized(streamCacheLock) {
                        streamSourceCache[cacheKey] = src
                    }
                }
                return resolved
            }
            lastFailureReason = resolved.exceptionOrNull()?.message
        }

        val sigTs =
            try {
                signatureTimestampProvider(normalizedId)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Throwable) {
                null
            }

        val webRemixClient = clientForWebFallback()
        val webRemixResult =
            tryClientForStream(
                trackId = normalizedId,
                client = webRemixClient,
                signatureTimestamp = sigTs,
                authState = playbackAuth,
                setLogin = useLoginContext,
                requireDirectUrl = false,
                quality = quality,
            )
        if (webRemixResult.isSuccess) {
            lastSuccessfulClient = webRemixClient
            webRemixResult.getOrNull()?.let { src ->
                synchronized(streamCacheLock) {
                    streamSourceCache[cacheKey] = src
                }
            }
            return webRemixResult
        }
        lastFailureReason = webRemixResult.exceptionOrNull()?.message ?: lastFailureReason

        return Result.failure(
            IllegalStateException(lastFailureReason ?: "No playable audio stream available for track"),
        )
    }

    private fun clientForWebFallback(): YouTubeClient = YouTubeClient.WEB_REMIX

    private suspend fun ensureGuestAuthState(): PlaybackAuthState {
        backend.visitorData?.takeIf { it.isNotBlank() }?.let { existing ->
            return PlaybackAuthState(visitorData = existing)
        }
        return visitorDataMutex.withLock {
            backend.visitorData?.takeIf { it.isNotBlank() }?.let { cached ->
                return@withLock PlaybackAuthState(visitorData = cached)
            }
            val fetched =
                try {
                    backend.visitorData().getOrNull()?.takeIf { it.isNotBlank() }
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Throwable) {
                    null
                }
            if (fetched != null) {
                backend.visitorData = fetched
                PlaybackAuthState(visitorData = fetched)
            } else {
                PlaybackAuthState.EMPTY
            }
        }
    }

    private suspend fun tryClientForStream(
        trackId: String,
        client: YouTubeClient,
        signatureTimestamp: Int?,
        authState: PlaybackAuthState,
        setLogin: Boolean,
        requireDirectUrl: Boolean,
        quality: AudioQualityPreset,
    ): Result<AudioStreamSource> {
        val response =
            backend
                .player(
                    videoId = trackId,
                    playlistId = null,
                    client = client,
                    signatureTimestamp = signatureTimestamp,
                    poToken = null,
                    setLogin = setLogin,
                    authState = authState,
                ).getOrElse { return Result.failure(it) }

        if (!response.playabilityStatus.status.equals("OK", ignoreCase = true)) {
            val status = response.playabilityStatus.status
            val reason = response.playabilityStatus.reason.orEmpty()
            return Result.failure(
                IllegalStateException("${client.clientName} playability=$status $reason".trim()),
            )
        }

        val streamingData =
            response.streamingData
                ?: return Result.failure(IllegalStateException("${client.clientName} returned no streaming data"))

        val formats =
            streamingData.adaptiveFormats.filter { fmt ->
                fmt.isAudio && (!requireDirectUrl || !fmt.url.isNullOrBlank())
            }
        val candidates =
            formats.map { fmt ->
                AudioFormatCandidate(
                    itag = fmt.itag,
                    mimeType = fmt.mimeType,
                    bitrateBps = fmt.averageBitrate ?: fmt.bitrate,
                    isAudioOnly = fmt.isAudio,
                    hasDirectUrl = !fmt.url.isNullOrBlank(),
                )
            }
        val chosenCandidate =
            AudioFormatSelector.selectBest(candidates, quality = quality)
                ?: return Result.failure(IllegalStateException("${client.clientName} had no audio-only formats"))

        val chosenFormat =
            formats.firstOrNull { it.itag == chosenCandidate.itag }
                ?: return Result.failure(IllegalStateException("${client.clientName} selected format missing"))

        val directUrl = chosenFormat.url?.takeIf { it.isNotBlank() }
        val resolvedUrl =
            if (directUrl != null && !needsThrottleDeobfuscation(directUrl)) {
                directUrl
            } else {
                fallbackStreamResolver(chosenFormat, trackId, client).getOrElse {
                    return Result.failure(it)
                }
            }

        val ttlSeconds = ((streamingData.expiresInSeconds ?: 300) - 60).coerceAtLeast(60)
        return Result.success(
            AudioStreamSource(
                trackId = trackId,
                streamUrl = resolvedUrl,
                userAgent = client.userAgent,
                mimeType = chosenFormat.mimeType,
                bitrateBps = chosenFormat.averageBitrate ?: chosenFormat.bitrate,
                expiresAtEpochMs = nowEpochMs() + (ttlSeconds * 1000L),
                clientProfile = client.clientName,
            ),
        )
    }

    private fun needsThrottleDeobfuscation(url: String): Boolean =
        url.contains("?n=") || url.contains("&n=")

    private fun SongItem.toDomainTrack(): Track =
        Track(
            id = id,
            title = title,
            artists = artists.map { ArtistRef(id = it.id, name = it.name) },
            album = album?.let { AlbumRef(id = it.id, title = it.name) },
            durationSeconds = duration,
            thumbnailUrl = thumbnail,
            isExplicit = explicit,
        )

    private fun AlbumItem.toDomainAlbum(): AlbumRef =
        AlbumRef(
            id = browseId,
            playlistId = playlistId,
            title = title,
            artists = artists.orEmpty().map { ArtistRef(id = it.id, name = it.name) },
            year = year,
            thumbnailUrl = thumbnail,
        )

    private fun ArtistItem.toDomainArtist(): ArtistRef =
        ArtistRef(
            id = id,
            name = title,
            thumbnailUrl = thumbnail,
        )

    private fun PlaylistItem.toDomainPlaylist(): PlaylistSummary =
        PlaylistSummary(
            id = id,
            title = title,
            author = author?.name,
            trackCountText = songCountText,
            thumbnailUrl = thumbnail,
        )
}
