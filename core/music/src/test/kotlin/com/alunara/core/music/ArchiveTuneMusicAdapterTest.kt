package com.alunara.core.music

import com.alunara.core.domain.model.AccountCredentials
import com.alunara.core.domain.policy.AudioQualityPreset
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import moe.rukamori.archivetune.innertube.MusicBackend
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.innertube.SearchFilter
import moe.rukamori.archivetune.innertube.models.AccountChannel
import moe.rukamori.archivetune.innertube.models.AccountInfo
import moe.rukamori.archivetune.innertube.models.Album
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.Artist
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.innertube.models.MediaInfo
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.ResponseContext
import moe.rukamori.archivetune.innertube.models.SearchSuggestions
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.YouTubeLocale
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse
import moe.rukamori.archivetune.innertube.pages.AlbumPage
import moe.rukamori.archivetune.innertube.pages.ArtistItemsContinuationPage
import moe.rukamori.archivetune.innertube.pages.ArtistItemsPage
import moe.rukamori.archivetune.innertube.pages.ArtistPage
import moe.rukamori.archivetune.innertube.pages.ArtistSection
import moe.rukamori.archivetune.innertube.pages.ArtistSectionLayout
import moe.rukamori.archivetune.innertube.pages.BrowseResult
import moe.rukamori.archivetune.innertube.pages.ChartsPage
import moe.rukamori.archivetune.innertube.pages.ExplorePage
import moe.rukamori.archivetune.innertube.pages.HistoryPage
import moe.rukamori.archivetune.innertube.pages.HomePage
import moe.rukamori.archivetune.innertube.pages.LibraryContinuationPage
import moe.rukamori.archivetune.innertube.pages.LibraryPage
import moe.rukamori.archivetune.innertube.pages.MoodAndGenres
import moe.rukamori.archivetune.innertube.pages.NextResult
import moe.rukamori.archivetune.innertube.pages.PlaylistContinuationPage
import moe.rukamori.archivetune.innertube.pages.PlaylistPage
import moe.rukamori.archivetune.innertube.pages.QueueChip
import moe.rukamori.archivetune.innertube.pages.RelatedPage
import moe.rukamori.archivetune.innertube.pages.SearchResult
import moe.rukamori.archivetune.innertube.pages.SearchSummary
import moe.rukamori.archivetune.innertube.pages.SearchSummaryPage
import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ArchiveTuneMusicAdapterTest {
    private open class FakeMusicBackendBase : MusicBackend {
        override val authStateFlow: StateFlow<PlaybackAuthState> = MutableStateFlow(PlaybackAuthState.EMPTY)
        override val historySyncEvent: SharedFlow<Unit> = MutableSharedFlow()
        override var authState: PlaybackAuthState = PlaybackAuthState.EMPTY
        override var locale: YouTubeLocale = YouTubeLocale("US", "en")
        override var visitorData: String?
            get() = authState.visitorData
            set(value) {
                authState = authState.copy(visitorData = value)
            }
        override var dataSyncId: String? = null
        override var cookie: String? = null
        override var poToken: String? = null
        override var webClientPoTokenEnabled: Boolean = false
        override var poTokenGvs: String? = null
        override var poTokenPlayer: String? = null
        override var proxy: Proxy? = null
        override var proxyUsername: String? = null
        override var proxyPassword: String? = null
        override var dns: Dns = Dns.SYSTEM
        override var streamBypassProxy: Boolean = false
        override var useLoginForBrowse: Boolean = true
        override val streamProxy: Proxy? = null
        override val streamOkHttpProxy: Proxy = Proxy.NO_PROXY
        override fun notifyHistorySynced() = Unit
        override fun currentPlaybackAuthState(): PlaybackAuthState = authState
        override fun hasLoginCookie(): Boolean = authState.hasLoginCookie
        override fun hasPlaybackLoginContext(): Boolean = authState.hasPlaybackLoginContext
        override fun createDnsOverHttps(url: String): Dns = Dns.SYSTEM
        override suspend fun searchSuggestions(query: String): Result<SearchSuggestions> = error("Unused")
        override suspend fun searchSummary(query: String): Result<SearchSummaryPage> = error("Unused")
        override suspend fun search(query: String, filter: SearchFilter, useAccountContext: Boolean): Result<SearchResult> = error("Unused")
        override suspend fun searchContinuation(continuation: String, useAccountContext: Boolean): Result<SearchResult> = error("Unused")
        override suspend fun album(browseId: String, withSongs: Boolean): Result<AlbumPage> = error("Unused")
        override suspend fun albumSongs(playlistId: String, album: AlbumItem?): Result<List<SongItem>> = error("Unused")
        override suspend fun artist(browseId: String): Result<ArtistPage> = error("Unused")
        override suspend fun artistItems(endpoint: BrowseEndpoint): Result<ArtistItemsPage> = error("Unused")
        override suspend fun artistItemsContinuation(continuation: String): Result<ArtistItemsContinuationPage> = error("Unused")
        override suspend fun playlist(playlistId: String): Result<PlaylistPage> = error("Unused")
        override suspend fun playlistContinuation(continuation: String, playlistId: String?): Result<PlaylistContinuationPage> = error("Unused")
        override suspend fun home(continuation: String?, params: String?): Result<HomePage> = error("Unused")
        override suspend fun explore(): Result<ExplorePage> = error("Unused")
        override suspend fun newReleaseAlbums(): Result<List<AlbumItem>> = error("Unused")
        override suspend fun moodAndGenres(): Result<List<MoodAndGenres>> = error("Unused")
        override suspend fun browse(browseId: String, params: String?): Result<BrowseResult> = error("Unused")
        override suspend fun getChartsPage(continuation: String?): Result<ChartsPage> = error("Unused")
        override suspend fun library(browseId: String, tabIndex: Int): Result<LibraryPage> = error("Unused")
        override suspend fun libraryContinuation(continuation: String): Result<LibraryContinuationPage> = error("Unused")
        override suspend fun libraryRecentActivity(): Result<LibraryPage> = error("Unused")
        override suspend fun musicHistory(): Result<HistoryPage> = error("Unused")
        override suspend fun player(videoId: String, playlistId: String?, client: YouTubeClient, signatureTimestamp: Int?, poToken: String?, setLogin: Boolean, authState: PlaybackAuthState): Result<PlayerResponse> = error("Unused")
        override suspend fun next(endpoint: WatchEndpoint, continuation: String?, followAutomixPreview: Boolean): Result<NextResult> = error("Unused")
        override suspend fun lyrics(endpoint: BrowseEndpoint): Result<String?> = error("Unused")
        override suspend fun related(endpoint: BrowseEndpoint): Result<RelatedPage> = error("Unused")
        override suspend fun queue(videoIds: List<String>?, playlistId: String?): Result<List<SongItem>> = error("Unused")
        override suspend fun registerPlayback(playlistId: String?, playbackTracking: String, authState: PlaybackAuthState): Result<Unit> = error("Unused")
        override suspend fun transcript(videoId: String): Result<String> = error("Unused")
        override suspend fun visitorData(): Result<String> = Result.success("guest-visitor-token")
        override suspend fun accountInfo(): Result<AccountInfo> = error("Unused")
        override suspend fun accountChannels(): Result<List<AccountChannel>> = error("Unused")
        override suspend fun getChannelId(browseId: String): String = error("Unused")
        override suspend fun likeVideo(videoId: String, like: Boolean): Result<Unit> = error("Unused")
        override suspend fun likePlaylist(playlistId: String, like: Boolean): Result<Unit> = error("Unused")
        override suspend fun subscribeChannel(channelId: String, subscribe: Boolean): Result<Unit> = error("Unused")
        override suspend fun addToPlaylist(playlistId: String, videoId: String): Result<String> = error("Unused")
        override suspend fun addSongsToPlaylist(playlistId: String, videoIds: List<String>, batchSize: Int, onProgress: (Int, Int) -> Unit): Result<List<String?>> = error("Unused")
        override suspend fun addPlaylistToPlaylist(playlistId: String, addPlaylistId: String): Result<Unit> = error("Unused")
        override suspend fun playlistEntrySetVideoIds(playlistId: String, videoId: String): Result<List<String>> = error("Unused")
        override suspend fun removeFromPlaylist(playlistId: String, videoId: String, setVideoId: String): Result<Unit> = error("Unused")
        override suspend fun moveSongPlaylist(playlistId: String, setVideoId: String, successorSetVideoId: String?): Result<Unit> = error("Unused")
        override suspend fun createPlaylist(title: String, videoIds: List<String>): Result<String> = error("Unused")
        override suspend fun renamePlaylist(playlistId: String, name: String): Result<Unit> = error("Unused")
        override suspend fun deletePlaylist(playlistId: String): Result<Unit> = error("Unused")
        override suspend fun getMediaInfo(videoId: String): Result<MediaInfo> = error("Unused")
    }

    private fun testFormat(
        itag: Int,
        mimeType: String,
        bitrate: Int,
        width: Int? = null,
        url: String? = null,
        cipher: String? = null,
    ) = PlayerResponse.StreamingData.Format(
        itag = itag,
        url = url,
        mimeType = mimeType,
        bitrate = bitrate,
        width = width,
        height = width,
        contentLength = null,
        quality = "medium",
        fps = null,
        qualityLabel = null,
        averageBitrate = bitrate,
        audioQuality = "AUDIO_QUALITY_MEDIUM",
        approxDurationMs = "180000",
        audioSampleRate = 48000,
        audioChannels = 2,
        loudnessDb = null,
        lastModified = null,
        signatureCipher = cipher,
        cipher = null,
    )

    private fun testPlayerResponse(
        status: String,
        formats: List<PlayerResponse.StreamingData.Format> = emptyList(),
        expiresInSeconds: Int = 360,
    ) = PlayerResponse(
        responseContext = ResponseContext(visitorData = null, serviceTrackingParams = null),
        playabilityStatus = PlayerResponse.PlayabilityStatus(status = status, reason = if (status == "OK") null else "Blocked"),
        playerConfig = null,
        streamingData =
            if (status == "OK") {
                PlayerResponse.StreamingData(formats = emptyList(), adaptiveFormats = formats, expiresInSeconds = expiresInSeconds)
            } else {
                null
            },
        videoDetails = null,
        playbackTracking = null,
    )

    @Test
    fun searchGuest_unlocksGatekeeperOnceAndNormalizesItems() =
        runTest {
            val unlockCalls = AtomicInteger(0)
            val backend =
                object : FakeMusicBackendBase() {
                    override suspend fun searchSummary(query: String): Result<SearchSummaryPage> =
                        Result.success(
                            SearchSummaryPage(
                                summaries =
                                    listOf(
                                        SearchSummary(
                                            title = "Top results",
                                            items =
                                                listOf(
                                                    SongItem(
                                                        id = "vid1",
                                                        title = "Forest Echo",
                                                        artists = listOf(Artist("Artist A", "art1")),
                                                        album = Album("Album X", "alb1"),
                                                        duration = 195,
                                                        thumbnail = "https://img/1.jpg",
                                                    ),
                                                    AlbumItem(
                                                        browseId = "alb1",
                                                        playlistId = "pl1",
                                                        title = "Album X",
                                                        artists = listOf(Artist("Artist A", "art1")),
                                                        year = 2024,
                                                        thumbnail = "https://img/a.jpg",
                                                    ),
                                                    ArtistItem(
                                                        id = "art1",
                                                        title = "Artist A",
                                                        thumbnail = "https://img/u.jpg",
                                                        shuffleEndpoint = null,
                                                        radioEndpoint = null,
                                                    ),
                                                    PlaylistItem(
                                                        id = "VLPL1",
                                                        title = "Forest Radio Mix",
                                                        author = Artist("Alunara", null),
                                                        songCountText = "25 songs",
                                                        thumbnail = "https://img/p.jpg",
                                                        playEndpoint = null,
                                                        shuffleEndpoint = null,
                                                        radioEndpoint = null,
                                                    ),
                                                ),
                                        ),
                                    ),
                            ),
                        )
                }

            val adapter = ArchiveTuneMusicAdapter(backend) { unlockCalls.incrementAndGet() }
            assertFalse(adapter.isGatekeeperUnlocked())

            val r1 = adapter.searchGuest("forest")
            val r2 = adapter.searchGuest("forest")

            assertTrue(r1.isSuccess)
            assertTrue(r2.isSuccess)
            assertTrue(adapter.isGatekeeperUnlocked())
            assertEquals(1, unlockCalls.get())
            assertFalse(backend.useLoginForBrowse)

            val results = r1.getOrThrow()
            assertEquals(1, results.songs.size)
            assertEquals("Forest Echo", results.songs.first().title)
            assertEquals(195, results.songs.first().durationSeconds)
            assertEquals(1, results.albums.size)
            assertEquals("alb1", results.albums.first().id)
            assertEquals(1, results.artists.size)
            assertEquals("Artist A", results.artists.first().name)
            assertEquals(1, results.playlists.size)
            assertEquals("Forest Radio Mix", results.playlists.first().title)
        }

    @Test
    fun searchGuest_blankQuerySkipsBackendAndReturnsEmpty() =
        runTest {
            val unlockCalls = AtomicInteger(0)
            val backend = FakeMusicBackendBase()
            val adapter = ArchiveTuneMusicAdapter(backend) { unlockCalls.incrementAndGet() }

            val result = adapter.searchGuest("   ")
            assertTrue(result.isSuccess)
            assertEquals(0, unlockCalls.get())
            assertTrue(result.getOrThrow().songs.isEmpty())
        }

    @Test
    fun searchGuest_propagatesBackendFailure() =
        runTest {
            val backend =
                object : FakeMusicBackendBase() {
                    override suspend fun searchSummary(query: String): Result<SearchSummaryPage> =
                        Result.failure(IOException("Timeout"))
                }
            val adapter = ArchiveTuneMusicAdapter(backend) {}

            val result = adapter.searchGuest("test")
            assertTrue(result.isFailure)
        }

    @Test
    fun fetchGuestHome_normalizesSectionsAndOmitsEmptyOnes() =
        runTest {
            val backend =
                object : FakeMusicBackendBase() {
                    override suspend fun home(continuation: String?, params: String?): Result<HomePage> =
                        Result.success(
                            HomePage(
                                chips = null,
                                sections =
                                    listOf(
                                        HomePage.Section(
                                            title = "Quick picks",
                                            label = null,
                                            thumbnail = null,
                                            endpoint = null,
                                            items =
                                                listOf(
                                                    SongItem(
                                                        id = "s1",
                                                        title = "Pine Breeze",
                                                        artists = listOf(Artist("Artist 1", null)),
                                                        thumbnail = "https://img/s1.jpg",
                                                    ),
                                                ),
                                        ),
                                        HomePage.Section(
                                            title = "Empty Section",
                                            label = null,
                                            thumbnail = null,
                                            endpoint = null,
                                            items = emptyList(),
                                        ),
                                    ),
                            ),
                        )
                }

            val adapter = ArchiveTuneMusicAdapter(backend) {}
            val sections = adapter.fetchGuestHome().getOrThrow()
            assertEquals(1, sections.size)
            assertEquals("Quick picks", sections.first().title)
            assertEquals("Pine Breeze", sections.first().songs.first().title)
        }

    @Test
    fun resolveAudioStream_prefersDirectAudioOnlyAndFallsBackCleanly() =
        runTest {
            val fallbackInvoked = AtomicBoolean(false)
            var issuedUrls = 0
            val backend =
                object : FakeMusicBackendBase() {
                    override suspend fun player(
                        videoId: String,
                        playlistId: String?,
                        client: YouTubeClient,
                        signatureTimestamp: Int?,
                        poToken: String?,
                        setLogin: Boolean,
                        authState: PlaybackAuthState,
                    ): Result<PlayerResponse> {
                        assertFalse(setLogin)
                        return when (client.clientName) {
                            "VISIONOS" -> Result.success(testPlayerResponse("LOGIN_REQUIRED"))
                            else ->
                                Result.success(
                                    testPlayerResponse(
                                        status = "OK",
                                        formats =
                                            listOf(
                                                testFormat(18, "video/mp4", 500_000, width = 640, url = "https://cdn/video.mp4"),
                                                testFormat(140, "audio/mp4; codecs=\"mp4a.40.2\"", 128_000, url = "https://cdn/aac.m4a"),
                                                testFormat(251, "audio/webm; codecs=\"opus\"", 160_000, url = "https://cdn/opus-${++issuedUrls}.webm"),
                                            ),
                                    ),
                                )
                        }
                    }
                }

            val adapter =
                ArchiveTuneMusicAdapter(
                    backend = backend,
                    nowEpochMs = { 1_000L },
                    fallbackStreamResolver = { _, _, _ ->
                        fallbackInvoked.set(true)
                        error("Should not invoke cipher resolver when direct URL has no throttling")
                    },
                    signatureTimestampProvider = { null },
                    allowConnectionsAction = {},
                )

            val source = adapter.resolveAudioStream("track123").getOrThrow()
            assertEquals("https://cdn/opus-1.webm", source.streamUrl)
            assertEquals("IOS", source.clientProfile)
            assertEquals(301_000L, source.expiresAtEpochMs)
            assertFalse(fallbackInvoked.get())

            assertEquals(source, adapter.resolveAudioStream("track123").getOrThrow())
            adapter.invalidateAudioStream(source, AudioQualityPreset.AUTO)
            val refreshed = adapter.resolveAudioStream("track123").getOrThrow()
            assertEquals("https://cdn/opus-2.webm", refreshed.streamUrl)
            adapter.invalidateAudioStream(source, AudioQualityPreset.AUTO)
            assertEquals(refreshed, adapter.resolveAudioStream("track123").getOrThrow())
        }

    @Test
    fun resolveAudioStream_returnsFailureWhenAllClientsFail() =
        runTest {
            val backend =
                object : FakeMusicBackendBase() {
                    override suspend fun player(
                        videoId: String,
                        playlistId: String?,
                        client: YouTubeClient,
                        signatureTimestamp: Int?,
                        poToken: String?,
                        setLogin: Boolean,
                        authState: PlaybackAuthState,
                    ): Result<PlayerResponse> = Result.success(testPlayerResponse("UNPLAYABLE"))
                }

            val adapter =
                ArchiveTuneMusicAdapter(
                    backend = backend,
                    signatureTimestampProvider = { null },
                    allowConnectionsAction = {},
                )

            val result = adapter.resolveAudioStream("badTrack")
            assertTrue(result.isFailure)
        }

    @Test
    fun resolveAudioStream_fetchesAnonymousVisitorDataOnceAndAttachesToPlayerAuthState() =
        runTest {
            val visitorDataCalls = AtomicInteger(0)
            val seenAuthStates = mutableListOf<PlaybackAuthState>()
            val backend =
                object : FakeMusicBackendBase() {
                    override suspend fun visitorData(): Result<String> {
                        visitorDataCalls.incrementAndGet()
                        return Result.success("anon-visitor-123")
                    }

                    override suspend fun player(
                        videoId: String,
                        playlistId: String?,
                        client: YouTubeClient,
                        signatureTimestamp: Int?,
                        poToken: String?,
                        setLogin: Boolean,
                        authState: PlaybackAuthState,
                    ): Result<PlayerResponse> {
                        seenAuthStates += authState
                        return Result.success(
                            testPlayerResponse(
                                status = "OK",
                                formats = listOf(testFormat(251, "audio/webm; codecs=\"opus\"", 160_000, url = "https://cdn/$videoId.webm")),
                            ),
                        )
                    }
                }

            val adapter =
                ArchiveTuneMusicAdapter(
                    backend = backend,
                    signatureTimestampProvider = { null },
                    allowConnectionsAction = {},
                )

            val first = adapter.resolveAudioStream("trackOne").getOrThrow()
            val second = adapter.resolveAudioStream("trackTwo").getOrThrow()

            assertEquals("https://cdn/trackOne.webm", first.streamUrl)
            assertEquals("https://cdn/trackTwo.webm", second.streamUrl)
            assertEquals(1, visitorDataCalls.get())
            assertEquals("anon-visitor-123", backend.visitorData)
            assertEquals(2, seenAuthStates.size)
            assertTrue(seenAuthStates.all { it.visitorData == "anon-visitor-123" && it.cookie == null })
        }

    @Test
    fun resolveAudioStream_fallsBackToEmptyAuthWhenVisitorDataFails() =
        runTest {
            val seenAuthStates = mutableListOf<PlaybackAuthState>()
            val backend =
                object : FakeMusicBackendBase() {
                    override suspend fun visitorData(): Result<String> = Result.failure(IOException("Visitor lookup failed"))

                    override suspend fun player(
                        videoId: String,
                        playlistId: String?,
                        client: YouTubeClient,
                        signatureTimestamp: Int?,
                        poToken: String?,
                        setLogin: Boolean,
                        authState: PlaybackAuthState,
                    ): Result<PlayerResponse> {
                        seenAuthStates += authState
                        return Result.success(
                            testPlayerResponse(
                                status = "OK",
                                formats = listOf(testFormat(251, "audio/webm; codecs=\"opus\"", 160_000, url = "https://cdn/fallback.webm")),
                            ),
                        )
                    }
                }

            val adapter =
                ArchiveTuneMusicAdapter(
                    backend = backend,
                    signatureTimestampProvider = { null },
                    allowConnectionsAction = {},
                )

            val source = adapter.resolveAudioStream("trackFallback").getOrThrow()
            assertEquals("https://cdn/fallback.webm", source.streamUrl)
            assertEquals(1, seenAuthStates.size)
            assertEquals(PlaybackAuthState.EMPTY, seenAuthStates.single())
        }

    @Test
    fun fetchAlbumPlaylistAndArtistDetail_normalizeInnerTubePages() =
        runTest {
            val backend =
                object : FakeMusicBackendBase() {
                    override suspend fun album(browseId: String, withSongs: Boolean): Result<AlbumPage> =
                        Result.success(
                            AlbumPage(
                                album = AlbumItem(browseId = browseId, playlistId = "OLAK1", title = "Green Album", artists = listOf(Artist("Tulus", "artT")), year = 2022, thumbnail = "https://img/alb.jpg"),
                                songs = listOf(SongItem(id = "trk1", title = "Track One", artists = listOf(Artist("Tulus", "artT")), duration = 210, thumbnail = "https://img/t1.jpg")),
                                otherVersions = emptyList(),
                            ),
                        )

                    override suspend fun playlist(playlistId: String): Result<PlaylistPage> =
                        Result.success(
                            PlaylistPage(
                                playlist = PlaylistItem(id = playlistId, title = "Chill Mix", author = Artist("Alunara", null), songCountText = "1 song", description = "Calm TV mix", thumbnail = "https://img/pl.jpg", playEndpoint = null, shuffleEndpoint = null, radioEndpoint = null),
                                songs = listOf(SongItem(id = "trk2", title = "Track Two", artists = listOf(Artist("Raim", "artR")), duration = 200, thumbnail = "https://img/t2.jpg")),
                                songsContinuation = null,
                                continuation = null,
                            ),
                        )

                    override suspend fun artist(browseId: String): Result<ArtistPage> =
                        Result.success(
                            ArtistPage(
                                artist = ArtistItem(id = browseId, title = "Tulus", thumbnail = "https://img/art.jpg", shuffleEndpoint = null, radioEndpoint = null),
                                sections =
                                    listOf(
                                        ArtistSection(
                                            title = "Songs",
                                            items = listOf(SongItem(id = "trk1", title = "Track One", artists = listOf(Artist("Tulus", browseId)), duration = 210, thumbnail = "https://img/t1.jpg")),
                                            moreEndpoint = null,
                                            layout = ArtistSectionLayout.LIST,
                                        ),
                                        ArtistSection(
                                            title = "Albums",
                                            items = listOf(AlbumItem(browseId = "MPRE1", playlistId = "OLAK1", title = "Green Album", artists = listOf(Artist("Tulus", browseId)), thumbnail = "https://img/alb.jpg")),
                                            moreEndpoint = null,
                                            layout = ArtistSectionLayout.GRID,
                                        ),
                                    ),
                                description = "Indonesian singer",
                            ),
                        )
                }

            val adapter = ArchiveTuneMusicAdapter(backend) {}

            val albumDetail = adapter.fetchAlbumDetail("MPRE1").getOrThrow()
            assertEquals("Green Album", albumDetail.album.title)
            assertEquals("Track One", albumDetail.tracks.single().title)

            val playlistDetail = adapter.fetchPlaylistDetail("VLPL99").getOrThrow()
            assertEquals("PL99", playlistDetail.playlist.id)
            assertEquals("Calm TV mix", playlistDetail.description)
            assertEquals("Track Two", playlistDetail.tracks.single().title)

            val artistDetail = adapter.fetchArtistDetail("artT").getOrThrow()
            assertEquals("Tulus", artistDetail.artist.name)
            assertEquals(1, artistDetail.topSongs.size)
            assertEquals(1, artistDetail.albums.size)
        }

    @Test
    fun fetchRadioTracks_queriesAutomixAndFallsBackToWatchEndpoint() =
        runTest {
            val seenEndpoints = mutableListOf<WatchEndpoint>()
            val backend =
                object : FakeMusicBackendBase() {
                    override suspend fun next(endpoint: WatchEndpoint, continuation: String?, followAutomixPreview: Boolean): Result<NextResult> {
                        seenEndpoints += endpoint
                        return if (endpoint.playlistId != null) {
                            Result.failure(IOException("Automix unavailable"))
                        } else {
                            Result.success(
                                NextResult(
                                    title = "Radio",
                                    items =
                                        listOf(
                                            SongItem(id = "seed1", title = "Seed", artists = listOf(Artist("A", null)), thumbnail = "https://img/s.jpg"),
                                            SongItem(id = "rec1", title = "Next Song", artists = listOf(Artist("B", null)), thumbnail = "https://img/r.jpg"),
                                        ),
                                    continuation = null,
                                    endpoint = endpoint,
                                ),
                            )
                        }
                    }
                }

            val adapter = ArchiveTuneMusicAdapter(backend) {}
            val radio = adapter.fetchRadioTracks("seed1").getOrThrow()
            assertEquals(2, seenEndpoints.size)
            assertEquals("RDAMVMseed1", seenEndpoints.first().playlistId)
            assertEquals(null, seenEndpoints[1].playlistId)
            assertEquals(listOf("seed1", "rec1"), radio.map { it.id })
        }

    @Test
    fun verifyAccountSession_rejectsMissingCookieActivatesValidAndResetsOnClearOrFailure() =
        runTest {
            val backend =
                object : FakeMusicBackendBase() {
                    var failAccountInfo = false
                    override suspend fun accountInfo(): Result<AccountInfo> =
                        if (failAccountInfo) {
                            Result.failure(IllegalStateException("401 Unauthorized"))
                        } else {
                            Result.success(AccountInfo(name = "Alunara User", email = "user@example.com", channelHandle = "@alunara", thumbnailUrl = "https://img/u.jpg"))
                        }
                }

            val adapter = ArchiveTuneMusicAdapter(backend) {}

            val badResult = adapter.verifyAccountSession(AccountCredentials(cookie = "PREF=123"))
            assertTrue(badResult.isFailure)
            assertFalse(backend.hasLoginCookie())

            val okResult = adapter.verifyAccountSession(AccountCredentials(cookie = "SAPISID=abc123; SID=xyz", visitorData = "vis-1"))
            assertTrue(okResult.isSuccess)
            val profile = okResult.getOrThrow()
            assertEquals("@alunara", profile.accountId)
            assertEquals("Alunara User", profile.name)
            assertTrue(backend.hasLoginCookie())
            assertTrue(backend.useLoginForBrowse)

            backend.failAccountInfo = true
            val expiredResult = adapter.verifyAccountSession(AccountCredentials(cookie = "SAPISID=abc123", visitorData = "vis-1"))
            assertTrue(expiredResult.isFailure)
            assertFalse(backend.hasLoginCookie())
            assertFalse(backend.useLoginForBrowse)
            assertEquals("vis-1", backend.visitorData)

            adapter.clearAccountSession()
            assertFalse(backend.hasLoginCookie())
        }

    @Test
    fun fetchGuestHome_includesPlaylistMixesContinuationAndCharts() =
        runTest {
            val backend =
                object : FakeMusicBackendBase() {
                    override suspend fun home(continuation: String?, params: String?): Result<HomePage> =
                        if (continuation == null) {
                            Result.success(
                                HomePage(
                                    chips = null,
                                    sections =
                                        listOf(
                                            HomePage.Section(
                                                title = "Mixed for you",
                                                label = null,
                                                thumbnail = null,
                                                endpoint = null,
                                                items =
                                                    listOf(
                                                        PlaylistItem(
                                                            id = "VLRDMix1",
                                                            title = "Daily Mix 1",
                                                            author = Artist("YouTube Music", null),
                                                            songCountText = "50 songs",
                                                            thumbnail = "https://img/mix1.jpg",
                                                            playEndpoint = null,
                                                            shuffleEndpoint = null,
                                                            radioEndpoint = null,
                                                        ),
                                                    ),
                                            ),
                                        ),
                                    continuation = "cont-page-2",
                                ),
                            )
                        } else {
                            Result.success(
                                HomePage(
                                    chips = null,
                                    sections =
                                        listOf(
                                            HomePage.Section(
                                                title = "Forgotten favorites",
                                                label = null,
                                                thumbnail = null,
                                                endpoint = null,
                                                items =
                                                    listOf(
                                                        SongItem(
                                                            id = "fav1",
                                                            title = "Old Gold",
                                                            artists = listOf(Artist("Budi", null)),
                                                            thumbnail = "https://img/f1.jpg",
                                                        ),
                                                    ),
                                            ),
                                        ),
                                ),
                            )
                        }

                    override suspend fun getChartsPage(continuation: String?): Result<ChartsPage> =
                        Result.success(
                            ChartsPage(
                                sections =
                                    listOf(
                                        ChartsPage.ChartSection(
                                            title = "Top songs",
                                            items =
                                                listOf(
                                                    SongItem(
                                                        id = "top1",
                                                        title = "Chart Hit",
                                                        artists = listOf(Artist("Nadin", null)),
                                                        thumbnail = "https://img/top1.jpg",
                                                    ),
                                                ),
                                            chartType = ChartsPage.ChartType.TOP,
                                        ),
                                    ),
                                continuation = null,
                            ),
                        )
                }

            val adapter = ArchiveTuneMusicAdapter(backend) {}
            val sections = adapter.fetchGuestHome().getOrThrow()

            assertEquals(listOf("Mixed for you", "Forgotten favorites", "Top songs"), sections.map { it.title })
            assertEquals("Daily Mix 1", sections[0].playlists.single().title)
            assertEquals("Old Gold", sections[1].songs.single().title)
            assertEquals("Chart Hit", sections[2].songs.single().title)
        }

    @Test
    fun fetchRadioQueue_mapsQueueChipsAndRespectsChipFilter() =
        runTest {
            val seenEndpoints = mutableListOf<WatchEndpoint>()
            val backend =
                object : FakeMusicBackendBase() {
                    override suspend fun next(endpoint: WatchEndpoint, continuation: String?, followAutomixPreview: Boolean): Result<NextResult> {
                        seenEndpoints += endpoint
                        return Result.success(
                            NextResult(
                                title = "Radio",
                                items =
                                    listOf(
                                        SongItem(id = "seed1", title = "Seed", artists = listOf(Artist("A", null)), thumbnail = "https://img/s.jpg"),
                                        SongItem(id = "q2", title = "Discover Pick", artists = listOf(Artist("C", null)), thumbnail = "https://img/q2.jpg"),
                                    ),
                                continuation = null,
                                endpoint = endpoint,
                                queueChips =
                                    listOf(
                                        QueueChip(
                                            id = "chip-discover",
                                            title = "Discover",
                                            selected = endpoint.params == "p-discover",
                                            endpoint = WatchEndpoint(videoId = "seed1", playlistId = "RDAMVMseed1", params = "p-discover"),
                                        ),
                                    ),
                            ),
                        )
                    }
                }

            val adapter = ArchiveTuneMusicAdapter(backend) {}
            val initial = adapter.fetchRadioQueue("seed1").getOrThrow()
            assertEquals(listOf("seed1", "q2"), initial.tracks.map { it.id })
            assertEquals("Discover", initial.chips.single().label)
            assertEquals("p-discover", initial.chips.single().params)

            val filtered = adapter.fetchRadioQueue("seed1", playlistId = initial.chips.single().playlistId, params = "p-discover").getOrThrow()
            assertTrue(filtered.chips.single().selected)
            assertEquals("p-discover", seenEndpoints.last().params)
        }

    @Test
    fun fetchLikedMusicAndHistoryAndPlaylists_filterSystemAndRequireLogin() =
        runTest {
            val backend =
                object : FakeMusicBackendBase() {
                    override suspend fun library(browseId: String, tabIndex: Int): Result<LibraryPage> =
                        Result.success(
                            LibraryPage(
                                items =
                                    listOf(
                                        PlaylistItem(id = "VLLM", title = "Your Likes", author = null, songCountText = "10", thumbnail = "", playEndpoint = null, shuffleEndpoint = null, radioEndpoint = null),
                                        PlaylistItem(id = "VLSE", title = "Episodes", author = null, songCountText = "2", thumbnail = "", playEndpoint = null, shuffleEndpoint = null, radioEndpoint = null),
                                        PlaylistItem(id = "VLPLuser1", title = "Chill Coding", author = Artist("Me", null), songCountText = "12 songs", thumbnail = "https://img/pl.jpg", playEndpoint = null, shuffleEndpoint = null, radioEndpoint = null),
                                    ),
                                continuation = null,
                            ),
                        )

                    override suspend fun playlist(playlistId: String): Result<PlaylistPage> =
                        Result.success(
                            PlaylistPage(
                                playlist = PlaylistItem(id = playlistId, title = "Liked Music", author = null, songCountText = "1", thumbnail = "", playEndpoint = null, shuffleEndpoint = null, radioEndpoint = null),
                                songs = listOf(SongItem(id = "lm1", title = "Liked Track", artists = listOf(Artist("A", null)), thumbnail = "https://img/lm.jpg")),
                                songsContinuation = null,
                                continuation = null,
                            ),
                        )

                    override suspend fun musicHistory(): Result<HistoryPage> =
                        Result.success(
                            HistoryPage(
                                sections =
                                    listOf(
                                        HistoryPage.HistorySection(
                                            title = "Today",
                                            songs = listOf(SongItem(id = "h1", title = "History Track", artists = listOf(Artist("B", null)), thumbnail = "https://img/h.jpg")),
                                        ),
                                    ),
                            ),
                        )
                }

            val adapter = ArchiveTuneMusicAdapter(backend) {}
            assertTrue(adapter.fetchLikedMusicTracks().isFailure)
            assertTrue(adapter.fetchAccountHistoryTracks().isFailure)

            backend.authState = PlaybackAuthState(cookie = "SAPISID=abc123; SID=xyz")
            val playlists = adapter.fetchAccountPlaylists().getOrThrow()
            assertEquals(listOf("VLPLuser1"), playlists.map { it.id })
            assertEquals(listOf("lm1"), adapter.fetchLikedMusicTracks().getOrThrow().map { it.id })
            assertEquals(listOf("h1"), adapter.fetchAccountHistoryTracks().getOrThrow().map { it.id })
        }

    @Test
    fun setContentLanguage_updatesHlWhilePreservingRegionGl() {
        val backend =
            object : FakeMusicBackendBase() {
                override var locale: YouTubeLocale = YouTubeLocale(gl = "ID", hl = "en")
            }
        val adapter = ArchiveTuneMusicAdapter(backend) {}

        adapter.setContentLanguage("id")
        assertEquals("ID", backend.locale.gl)
        assertEquals("id", backend.locale.hl)

        adapter.setContentLanguage("in")
        assertEquals("ID", backend.locale.gl)
        assertEquals("id", backend.locale.hl)

        adapter.setContentLanguage("en")
        assertEquals("ID", backend.locale.gl)
        assertEquals("en", backend.locale.hl)

        adapter.setContentLanguage("ja")
        assertEquals("ID", backend.locale.gl)
        assertEquals("ja", backend.locale.hl)

        adapter.setContentLanguage("   ")
        assertEquals("ja", backend.locale.hl)
    }
}
