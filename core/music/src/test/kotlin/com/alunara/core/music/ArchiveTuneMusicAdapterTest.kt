package com.alunara.core.music

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
        override var visitorData: String? = null
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
        override fun hasLoginCookie(): Boolean = false
        override fun hasPlaybackLoginContext(): Boolean = false
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
                                                testFormat(251, "audio/webm; codecs=\"opus\"", 160_000, url = "https://cdn/opus.webm"),
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
            assertEquals("https://cdn/opus.webm", source.streamUrl)
            assertEquals("IOS", source.clientProfile)
            assertEquals(301_000L, source.expiresAtEpochMs)
            assertFalse(fallbackInvoked.get())
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
}
