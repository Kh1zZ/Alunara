package com.alunara.core.music

import com.alunara.core.domain.contract.MusicEngine
import com.alunara.core.domain.model.AlbumRef
import com.alunara.core.domain.model.ArtistRef
import com.alunara.core.domain.model.AudioStreamSource
import com.alunara.core.domain.model.HomeSection
import com.alunara.core.domain.model.SearchResults
import com.alunara.core.domain.model.Track
import com.alunara.core.domain.policy.AudioFormatCandidate
import com.alunara.core.domain.policy.AudioFormatSelector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.rukamori.archivetune.innertube.MusicBackend
import moe.rukamori.archivetune.innertube.NetworkGatekeeperControl
import moe.rukamori.archivetune.innertube.NewPipeUtils
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse
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
        backend.useLoginForBrowse = false

        return backend.searchSummary(normalizedQuery).map { page ->
            val songs = mutableListOf<Track>()
            val albums = mutableListOf<AlbumRef>()
            val artists = mutableListOf<ArtistRef>()

            page.summaries
                .asSequence()
                .flatMap { it.items.asSequence() }
                .forEach { item ->
                    when (item) {
                        is SongItem -> songs += item.toDomainTrack()
                        is AlbumItem -> albums += item.toDomainAlbum()
                        is ArtistItem -> artists += item.toDomainArtist()
                        else -> Unit
                    }
                }

            SearchResults(
                songs = songs.distinctBy { it.id },
                albums = albums.distinctBy { it.id },
                artists = artists.distinctBy { it.id ?: it.name },
            )
        }
    }

    override suspend fun fetchGuestHome(): Result<List<HomeSection>> {
        ensureGatekeeperUnlocked()
        backend.useLoginForBrowse = false

        return backend.home().map { page ->
            page.sections.mapNotNull { section ->
                val songs = section.items.filterIsInstance<SongItem>().map { it.toDomainTrack() }.distinctBy { it.id }
                val albums = section.items.filterIsInstance<AlbumItem>().map { it.toDomainAlbum() }.distinctBy { it.id }
                if (songs.isEmpty() && albums.isEmpty()) {
                    null
                } else {
                    HomeSection(
                        title = section.title,
                        songs = songs,
                        albums = albums,
                    )
                }
            }
        }
    }

    override suspend fun resolveAudioStream(trackId: String): Result<AudioStreamSource> {
        val normalizedId = trackId.trim()
        if (normalizedId.isEmpty()) {
            return Result.failure(IllegalArgumentException("Track id must not be blank"))
        }

        ensureGatekeeperUnlocked()
        val guestAuthState = ensureGuestAuthState()
        val guestClients =
            listOf(
                YouTubeClient.VISIONOS,
                YouTubeClient.IOS,
                YouTubeClient.TVHTML5_SIMPLY,
                YouTubeClient.ANDROID_VR_1_65_10,
            )

        var lastFailureReason: String? = null

        for (client in guestClients) {
            val resolved =
                tryClientForStream(
                    trackId = normalizedId,
                    client = client,
                    signatureTimestamp = null,
                    authState = guestAuthState,
                    requireDirectUrl = true,
                )
            if (resolved.isSuccess) return resolved
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

        val webRemixResult =
            tryClientForStream(
                trackId = normalizedId,
                client = clientForWebFallback(),
                signatureTimestamp = sigTs,
                authState = guestAuthState,
                requireDirectUrl = false,
            )
        if (webRemixResult.isSuccess) return webRemixResult
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
        requireDirectUrl: Boolean,
    ): Result<AudioStreamSource> {
        val response =
            backend
                .player(
                    videoId = trackId,
                    playlistId = null,
                    client = client,
                    signatureTimestamp = signatureTimestamp,
                    poToken = null,
                    setLogin = false,
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
            AudioFormatSelector.selectBest(candidates)
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
}
