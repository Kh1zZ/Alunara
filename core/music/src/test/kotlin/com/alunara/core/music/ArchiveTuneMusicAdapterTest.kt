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
        override suspend fun visitorData(): Result<String> = error("Unused")
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
}
