package com.alunara.core.music

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import moe.rukamori.archivetune.innertube.MusicBackend
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.innertube.SearchFilter
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AccountChannel
import moe.rukamori.archivetune.innertube.models.AccountInfo
import moe.rukamori.archivetune.innertube.models.AlbumItem
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
import moe.rukamori.archivetune.innertube.pages.SearchSummaryPage
import okhttp3.Dns
import java.net.Proxy

/**
 * Delegates the ArchiveTune [MusicBackend] contract directly to the vendored [YouTube] singleton.
 */
object YouTubeMusicBackend : MusicBackend {
    override val authStateFlow: StateFlow<PlaybackAuthState>
        get() = YouTube.authStateFlow

    override val historySyncEvent: SharedFlow<Unit>
        get() = YouTube.historySyncEvent

    override var authState: PlaybackAuthState
        get() = YouTube.authState
        set(value) {
            YouTube.authState = value
        }

    override var locale: YouTubeLocale
        get() = YouTube.locale
        set(value) {
            YouTube.locale = value
        }

    override var visitorData: String?
        get() = YouTube.visitorData
        set(value) {
            YouTube.visitorData = value
        }

    override var dataSyncId: String?
        get() = YouTube.dataSyncId
        set(value) {
            YouTube.dataSyncId = value
        }

    override var cookie: String?
        get() = YouTube.cookie
        set(value) {
            YouTube.cookie = value
        }

    override var poToken: String?
        get() = YouTube.poToken
        set(value) {
            YouTube.poToken = value
        }

    override var webClientPoTokenEnabled: Boolean
        get() = YouTube.webClientPoTokenEnabled
        set(value) {
            YouTube.webClientPoTokenEnabled = value
        }

    override var poTokenGvs: String?
        get() = YouTube.poTokenGvs
        set(value) {
            YouTube.poTokenGvs = value
        }

    override var poTokenPlayer: String?
        get() = YouTube.poTokenPlayer
        set(value) {
            YouTube.poTokenPlayer = value
        }

    override var proxy: Proxy?
        get() = YouTube.proxy
        set(value) {
            YouTube.proxy = value
        }

    override var proxyUsername: String?
        get() = YouTube.proxyUsername
        set(value) {
            YouTube.proxyUsername = value
        }

    override var proxyPassword: String?
        get() = YouTube.proxyPassword
        set(value) {
            YouTube.proxyPassword = value
        }

    override var dns: Dns
        get() = YouTube.dns
        set(value) {
            YouTube.dns = value
        }

    override var streamBypassProxy: Boolean
        get() = YouTube.streamBypassProxy
        set(value) {
            YouTube.streamBypassProxy = value
        }

    override var useLoginForBrowse: Boolean
        get() = YouTube.useLoginForBrowse
        set(value) {
            YouTube.useLoginForBrowse = value
        }

    override val streamProxy: Proxy?
        get() = YouTube.streamProxy

    override val streamOkHttpProxy: Proxy
        get() = YouTube.streamOkHttpProxy

    override fun notifyHistorySynced() = YouTube.notifyHistorySynced()

    override fun currentPlaybackAuthState(): PlaybackAuthState = YouTube.currentPlaybackAuthState()

    override fun hasLoginCookie(): Boolean = YouTube.hasLoginCookie()

    override fun hasPlaybackLoginContext(): Boolean = YouTube.hasPlaybackLoginContext()

    override fun createDnsOverHttps(url: String): Dns = YouTube.createDnsOverHttps(url)

    override suspend fun searchSuggestions(query: String): Result<SearchSuggestions> = YouTube.searchSuggestions(query)

    override suspend fun searchSummary(query: String): Result<SearchSummaryPage> = YouTube.searchSummary(query)

    override suspend fun search(
        query: String,
        filter: SearchFilter,
        useAccountContext: Boolean,
    ): Result<SearchResult> = YouTube.search(query, YouTube.SearchFilter(filter.value), useAccountContext)

    override suspend fun searchContinuation(
        continuation: String,
        useAccountContext: Boolean,
    ): Result<SearchResult> = YouTube.searchContinuation(continuation, useAccountContext)

    override suspend fun album(
        browseId: String,
        withSongs: Boolean,
    ): Result<AlbumPage> = YouTube.album(browseId, withSongs)

    override suspend fun albumSongs(
        playlistId: String,
        album: AlbumItem?,
    ): Result<List<SongItem>> = YouTube.albumSongs(playlistId, album)

    override suspend fun artist(browseId: String): Result<ArtistPage> = YouTube.artist(browseId)

    override suspend fun artistItems(endpoint: BrowseEndpoint): Result<ArtistItemsPage> = YouTube.artistItems(endpoint)

    override suspend fun artistItemsContinuation(continuation: String): Result<ArtistItemsContinuationPage> =
        YouTube.artistItemsContinuation(continuation)

    override suspend fun playlist(playlistId: String): Result<PlaylistPage> = YouTube.playlist(playlistId)

    override suspend fun playlistContinuation(
        continuation: String,
        playlistId: String?,
    ): Result<PlaylistContinuationPage> = YouTube.playlistContinuation(continuation, playlistId)

    override suspend fun home(
        continuation: String?,
        params: String?,
    ): Result<HomePage> = YouTube.home(continuation, params)

    override suspend fun explore(): Result<ExplorePage> = YouTube.explore()

    override suspend fun newReleaseAlbums(): Result<List<AlbumItem>> = YouTube.newReleaseAlbums()

    override suspend fun moodAndGenres(): Result<List<MoodAndGenres>> = YouTube.moodAndGenres()

    override suspend fun browse(
        browseId: String,
        params: String?,
    ): Result<BrowseResult> = YouTube.browse(browseId, params)

    override suspend fun getChartsPage(continuation: String?): Result<ChartsPage> = YouTube.getChartsPage(continuation)

    override suspend fun library(
        browseId: String,
        tabIndex: Int,
    ): Result<LibraryPage> = YouTube.library(browseId, tabIndex)

    override suspend fun libraryContinuation(continuation: String): Result<LibraryContinuationPage> =
        YouTube.libraryContinuation(continuation)

    override suspend fun libraryRecentActivity(): Result<LibraryPage> = YouTube.libraryRecentActivity()

    override suspend fun musicHistory(): Result<HistoryPage> = YouTube.musicHistory()

    override suspend fun player(
        videoId: String,
        playlistId: String?,
        client: YouTubeClient,
        signatureTimestamp: Int?,
        poToken: String?,
        setLogin: Boolean,
        authState: PlaybackAuthState,
    ): Result<PlayerResponse> = YouTube.player(videoId, playlistId, client, signatureTimestamp, poToken, setLogin, authState)

    override suspend fun next(
        endpoint: WatchEndpoint,
        continuation: String?,
        followAutomixPreview: Boolean,
    ): Result<NextResult> = YouTube.next(endpoint, continuation, followAutomixPreview)

    override suspend fun lyrics(endpoint: BrowseEndpoint): Result<String?> = YouTube.lyrics(endpoint)

    override suspend fun related(endpoint: BrowseEndpoint): Result<RelatedPage> = YouTube.related(endpoint)

    override suspend fun queue(
        videoIds: List<String>?,
        playlistId: String?,
    ): Result<List<SongItem>> = YouTube.queue(videoIds, playlistId)

    override suspend fun registerPlayback(
        playlistId: String?,
        playbackTracking: String,
        authState: PlaybackAuthState,
    ): Result<Unit> = YouTube.registerPlayback(playlistId, playbackTracking, authState).map { }

    override suspend fun transcript(videoId: String): Result<String> = YouTube.transcript(videoId)

    override suspend fun visitorData(): Result<String> = YouTube.visitorData()

    override suspend fun accountInfo(): Result<AccountInfo> = YouTube.accountInfo()

    override suspend fun accountChannels(): Result<List<AccountChannel>> = YouTube.accountChannels()

    override suspend fun getChannelId(browseId: String): String = YouTube.getChannelId(browseId)

    override suspend fun likeVideo(
        videoId: String,
        like: Boolean,
    ): Result<Unit> = YouTube.likeVideo(videoId, like).map { }

    override suspend fun likePlaylist(
        playlistId: String,
        like: Boolean,
    ): Result<Unit> = YouTube.likePlaylist(playlistId, like).map { }

    override suspend fun subscribeChannel(
        channelId: String,
        subscribe: Boolean,
    ): Result<Unit> = YouTube.subscribeChannel(channelId, subscribe).map { }

    override suspend fun addToPlaylist(
        playlistId: String,
        videoId: String,
    ): Result<String> = YouTube.addToPlaylist(playlistId, videoId)

    override suspend fun addSongsToPlaylist(
        playlistId: String,
        videoIds: List<String>,
        batchSize: Int,
        onProgress: (completedSongs: Int, totalSongs: Int) -> Unit,
    ): Result<List<String?>> = YouTube.addSongsToPlaylist(playlistId, videoIds, batchSize, onProgress)

    override suspend fun addPlaylistToPlaylist(
        playlistId: String,
        addPlaylistId: String,
    ): Result<Unit> = YouTube.addPlaylistToPlaylist(playlistId, addPlaylistId).map { }

    override suspend fun playlistEntrySetVideoIds(
        playlistId: String,
        videoId: String,
    ): Result<List<String>> = YouTube.playlistEntrySetVideoIds(playlistId, videoId)

    override suspend fun removeFromPlaylist(
        playlistId: String,
        videoId: String,
        setVideoId: String,
    ): Result<Unit> = YouTube.removeFromPlaylist(playlistId, videoId, setVideoId).map { }

    override suspend fun moveSongPlaylist(
        playlistId: String,
        setVideoId: String,
        successorSetVideoId: String?,
    ): Result<Unit> = YouTube.moveSongPlaylist(playlistId, setVideoId, successorSetVideoId).map { }

    override suspend fun createPlaylist(
        title: String,
        videoIds: List<String>,
    ): Result<String> = YouTube.createPlaylist(title, videoIds)

    override suspend fun renamePlaylist(
        playlistId: String,
        name: String,
    ): Result<Unit> = YouTube.renamePlaylist(playlistId, name).map { }

    override suspend fun deletePlaylist(playlistId: String): Result<Unit> = YouTube.deletePlaylist(playlistId).map { }

    override suspend fun getMediaInfo(videoId: String): Result<MediaInfo> = YouTube.getMediaInfo(videoId)
}
