package com.alunara.app

import androidx.media3.common.Player
import com.alunara.app.playback.AudioPlayerControl
import com.alunara.app.playback.PlaybackSessionManager
import com.alunara.core.domain.contract.AccountSessionStore
import com.alunara.core.domain.contract.CatalogStore
import com.alunara.core.domain.contract.LyricsEngine
import com.alunara.core.domain.contract.MusicEngine
import com.alunara.core.domain.contract.QueueStore
import com.alunara.core.domain.model.AccountCredentials
import com.alunara.core.domain.model.AccountProfile
import com.alunara.core.domain.model.AccountScope
import com.alunara.core.domain.model.AudioStreamSource
import com.alunara.core.domain.model.AuthSessionState
import com.alunara.core.domain.model.HomeSection
import com.alunara.core.domain.model.LyricsDocument
import com.alunara.core.domain.model.LyricsProviderId
import com.alunara.core.domain.model.PlaylistSummary
import com.alunara.core.domain.model.QueueFilterChip
import com.alunara.core.domain.model.QueueState
import com.alunara.core.domain.model.RadioQueueResult
import com.alunara.core.domain.model.SearchResults
import com.alunara.core.domain.model.Track
import com.alunara.core.domain.policy.FailureCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSessionManagerTest {
    private class FakePlayerControl : AudioPlayerControl {
        override var hasActivePlayer: Boolean = false
        var preparedTracks = mutableListOf<String>()
        var playingState: Boolean = false
        var listenerRef: Player.Listener? = null

        override fun prepareAudioStream(
            source: AudioStreamSource,
            startPositionMs: Long,
            playWhenReady: Boolean,
            listener: Player.Listener,
        ) {
            hasActivePlayer = true
            playingState = playWhenReady
            listenerRef = listener
            preparedTracks += source.trackId
        }

        override fun play() {
            playingState = true
        }

        override fun pause() {
            playingState = false
        }

        override fun seekTo(positionMs: Long) = Unit

        override fun currentPositionMs(): Long = 0L

        override fun durationMs(): Long = 180_000L

        override fun isPlaying(): Boolean = playingState

        override fun onAppBackgroundedOrStopped() {
            hasActivePlayer = false
            playingState = false
        }
    }

    private class FakeCatalogStore : CatalogStore {
        private val favoritesByScope = linkedMapOf<String, LinkedHashMap<String, Track>>()
        private val historyByScope = linkedMapOf<String, MutableList<Track>>()

        override suspend fun getFavorites(scope: AccountScope): List<Track> =
            favoritesByScope[scope.storageKey]?.values?.toList()?.reversed().orEmpty()

        override suspend fun isFavorite(
            trackId: String,
            scope: AccountScope,
        ): Boolean = favoritesByScope[scope.storageKey]?.containsKey(trackId) == true

        override suspend fun toggleFavorite(
            track: Track,
            scope: AccountScope,
        ): Boolean {
            val map = favoritesByScope.getOrPut(scope.storageKey) { linkedMapOf() }
            return if (map.remove(track.id) != null) {
                false
            } else {
                map[track.id] = track
                true
            }
        }

        override suspend fun recordHistory(
            track: Track,
            scope: AccountScope,
        ) {
            val list = historyByScope.getOrPut(scope.storageKey) { mutableListOf() }
            if (list.lastOrNull()?.id != track.id) {
                list += track
            }
        }

        override suspend fun getRecentHistory(
            scope: AccountScope,
            limit: Int,
        ): List<Track> =
            historyByScope[scope.storageKey]
                .orEmpty()
                .asReversed()
                .distinctBy { it.id }
                .take(limit)

        override suspend fun clearHistory(scope: AccountScope) {
            historyByScope.remove(scope.storageKey)
        }

        override suspend fun clearAccountData(scope: AccountScope.Account) {
            favoritesByScope.remove(scope.storageKey)
            historyByScope.remove(scope.storageKey)
        }
    }

    private class FakeAccountSessionStore : AccountSessionStore {
        var current: Pair<AccountProfile, AccountCredentials>? = null
        var clearedCount = 0

        override suspend fun loadSession(): Pair<AccountProfile, AccountCredentials>? = current

        override suspend fun saveSession(
            profile: AccountProfile,
            credentials: AccountCredentials,
        ) {
            current = profile to credentials
        }

        override suspend fun clearSession() {
            clearedCount++
            current = null
        }
    }

    private class FakeMusicEngine(
        private val radioResultsByTrack: Map<String, List<Track>> = emptyMap(),
        private val radioQueueByChipParams: Map<String?, RadioQueueResult> = emptyMap(),
        private val remoteLikesInitial: List<Track> = emptyList(),
        private val remoteHistoryInitial: List<Track> = emptyList(),
    ) : MusicEngine {
        var radioFetchCalls = mutableListOf<String>()
        var accountClearedCount = 0
        var remoteLikes = mutableListOf<Pair<String, Boolean>>()

        override suspend fun searchGuest(query: String): Result<SearchResults> = Result.success(SearchResults())

        override suspend fun fetchGuestHome(): Result<List<HomeSection>> = Result.success(emptyList())

        override suspend fun resolveAudioStream(trackId: String): Result<AudioStreamSource> =
            Result.success(
                AudioStreamSource(
                    trackId = trackId,
                    streamUrl = "https://cdn/$trackId.m4a",
                    userAgent = "TestAgent",
                    mimeType = "audio/mp4",
                    bitrateBps = 128_000,
                    expiresAtEpochMs = Long.MAX_VALUE,
                    clientProfile = "VISIONOS",
                ),
            )

        override suspend fun fetchRadioTracks(
            seedTrackId: String,
            playlistId: String?,
        ): Result<List<Track>> {
            radioFetchCalls += seedTrackId
            return Result.success(radioResultsByTrack[seedTrackId].orEmpty())
        }

        override suspend fun fetchRadioQueue(
            seedTrackId: String,
            playlistId: String?,
            params: String?,
        ): Result<RadioQueueResult> {
            val chipped = radioQueueByChipParams[params]
            if (chipped != null) {
                radioFetchCalls += seedTrackId
                return Result.success(chipped)
            }
            return super.fetchRadioQueue(seedTrackId, playlistId, params)
        }

        override suspend fun verifyAccountSession(credentials: AccountCredentials): Result<AccountProfile> =
            Result.success(AccountProfile(accountId = "acc-1", name = "Alunara User"))

        override fun clearAccountSession() {
            accountClearedCount++
        }

        override suspend fun fetchAccountPlaylists(): Result<List<PlaylistSummary>> =
            Result.success(listOf(PlaylistSummary(id = "PL1", title = "Account Favorites")))

        override suspend fun fetchLikedMusicTracks(): Result<List<Track>> =
            Result.success(remoteLikesInitial)

        override suspend fun fetchAccountHistoryTracks(): Result<List<Track>> =
            Result.success(remoteHistoryInitial)

        override suspend fun setRemoteTrackLike(
            trackId: String,
            liked: Boolean,
        ): Result<Unit> {
            remoteLikes += trackId to liked
            return Result.success(Unit)
        }
    }

    private val emptyLyricsEngine =
        object : LyricsEngine {
            override suspend fun fetchLyrics(
                track: Track,
                preferredProvider: LyricsProviderId?,
            ): LyricsDocument = LyricsDocument.Unavailable("none")
        }

    private val memoryQueueStore =
        object : QueueStore {
            var state = QueueState()

            override suspend fun loadQueue(): QueueState = state

            override suspend fun saveQueue(state: QueueState) {
                this.state = state
            }
        }

    @Test
    fun startRadioAndAutoplayTail_enqueuesDeduplicatedRecommendationsWithoutInfiniteLoop() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val seed = Track(id = "t1", title = "Seed")
            val rec1 = Track(id = "t2", title = "Recommended 1")
            val rec2 = Track(id = "t3", title = "Recommended 2")

            val music =
                FakeMusicEngine(
                    radioResultsByTrack =
                        mapOf(
                            "t1" to listOf(seed, rec1),
                            "t2" to listOf(rec1, rec2),
                            "t3" to emptyList(),
                        ),
                )
            val player = FakePlayerControl()
            val catalog = FakeCatalogStore()
            val manager =
                PlaybackSessionManager(
                    musicEngine = music,
                    lyricsEngine = emptyLyricsEngine,
                    queueStore = memoryQueueStore,
                    playerController = player,
                    catalogStore = catalog,
                    ioDispatcher = dispatcher,
                    mainDispatcher = dispatcher,
                )

            manager.startRadio(seed)
            advanceUntilIdle()

            assertEquals(listOf("t1", "t2"), manager.uiState.value.queueState.items.map { it.track.id })

            // Advance naturally from t1 -> t2 -> extend at t2 tail -> t3 -> no infinite loop when t3 returns empty
            manager.playerListener.onPlaybackStateChanged(Player.STATE_ENDED)
            advanceUntilIdle()
            assertEquals("t2", manager.uiState.value.currentTrack?.id)

            manager.playerListener.onPlaybackStateChanged(Player.STATE_ENDED)
            advanceUntilIdle()
            assertEquals("t3", manager.uiState.value.currentTrack?.id)

            manager.playerListener.onPlaybackStateChanged(Player.STATE_ENDED)
            advanceUntilIdle()
            val radioCallsAfterFirstTailEnd = music.radioFetchCalls.count { it == "t3" }
            assertEquals(1, radioCallsAfterFirstTailEnd)

            // Repeated end event on same tail track must not re-query radio
            manager.playerListener.onPlaybackStateChanged(Player.STATE_ENDED)
            advanceUntilIdle()
            assertEquals(1, music.radioFetchCalls.count { it == "t3" })
        }

    @Test
    fun authExpiryAndLogout_preserveGuestCatalogAndWipeAccountSession() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val guestTrack = Track(id = "g1", title = "Guest Song")
            val acctTrack = Track(id = "a1", title = "Account Song")

            val catalog = FakeCatalogStore()
            catalog.toggleFavorite(guestTrack, AccountScope.Guest)
            val sessionStore = FakeAccountSessionStore()
            val music = FakeMusicEngine()
            val player = FakePlayerControl()

            val manager =
                PlaybackSessionManager(
                    musicEngine = music,
                    lyricsEngine = emptyLyricsEngine,
                    queueStore = memoryQueueStore,
                    playerController = player,
                    catalogStore = catalog,
                    accountSessionStore = sessionStore,
                    ioDispatcher = dispatcher,
                    mainDispatcher = dispatcher,
                )

            val activated = manager.activateAccountSession(AccountCredentials(cookie = "SAPISID=xyz"))
            advanceUntilIdle()
            assertTrue(activated.isSuccess)
            assertTrue(manager.uiState.value.authSession is AuthSessionState.Authenticated)
            assertEquals(1, manager.uiState.value.accountPlaylists.size)

            manager.toggleFavorite(acctTrack)
            advanceUntilIdle()
            assertTrue(manager.uiState.value.favoriteIds.contains("a1"))
            assertEquals(listOf("a1" to true), music.remoteLikes)

            // Play a track, then simulate FailureCategory.AUTH_INVALID (HTTP 401 from Media3)
            manager.playTracks(listOf(acctTrack))
            advanceUntilIdle()

            manager.handlePlaybackFailure(FailureCategory.AUTH_INVALID, "401 Unauthorized")
            advanceUntilIdle()

            assertTrue(manager.uiState.value.authSession is AuthSessionState.Expired)
            assertTrue(sessionStore.clearedCount >= 1)
            assertTrue(music.accountClearedCount >= 1)
            assertTrue(manager.uiState.value.accountPlaylists.isEmpty())
            // Guest favorites must be intact and loaded instead of account favorites
            assertEquals(listOf("g1"), manager.uiState.value.favorites.map { it.id })

            // Explicit re-auth + logout clears account scope in Room while still preserving guest
            assertTrue(manager.activateAccountSession(AccountCredentials(cookie = "SAPISID=xyz")).isSuccess)
            advanceUntilIdle()
            assertEquals(listOf("a1"), catalog.getFavorites(AccountScope.Account("acc-1")).map { it.id })

            manager.logoutAccount()
            advanceUntilIdle()
            assertEquals(AuthSessionState.Guest, manager.uiState.value.authSession)
            assertTrue(catalog.getFavorites(AccountScope.Account("acc-1")).isEmpty())
            assertEquals(listOf("g1"), catalog.getFavorites(AccountScope.Guest).map { it.id })
            assertFalse(manager.uiState.value.favoriteIds.contains("a1"))
            assertTrue(manager.uiState.value.remoteFavorites.isEmpty())
            assertTrue(manager.uiState.value.remoteHistory.isEmpty())
        }

    @Test
    fun singleTrackPlayAutoExpandsRadioQueueAndSelectChipReplacesUpcomingWithoutInterruptingCurrent() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val seed = Track(id = "s1", title = "Seed Track")
            val fam1 = Track(id = "f1", title = "Familiar Pick")
            val disc1 = Track(id = "d1", title = "Discover Pick")
            val discoverChip = QueueFilterChip(label = "Discover", playlistId = "RDAMVMs1", params = "chip-discover")

            val music =
                FakeMusicEngine(
                    radioQueueByChipParams =
                        mapOf(
                            null to
                                RadioQueueResult(
                                    tracks = listOf(seed, fam1),
                                    chips = listOf(QueueFilterChip("Familiar", "RDAMVMs1", "chip-fam", selected = true), discoverChip),
                                ),
                            "chip-discover" to
                                RadioQueueResult(
                                    tracks = listOf(seed, disc1),
                                    chips = listOf(QueueFilterChip("Familiar", "RDAMVMs1", "chip-fam", selected = false), discoverChip.copy(selected = true)),
                                ),
                        ),
                )
            val player = FakePlayerControl()
            val manager =
                PlaybackSessionManager(
                    musicEngine = music,
                    lyricsEngine = emptyLyricsEngine,
                    queueStore = memoryQueueStore,
                    playerController = player,
                    ioDispatcher = dispatcher,
                    mainDispatcher = dispatcher,
                )

            manager.playTracks(listOf(seed))
            advanceUntilIdle()

            assertEquals(listOf("s1", "f1"), manager.uiState.value.queueState.items.map { it.track.id })
            assertEquals("Familiar", manager.uiState.value.activeQueueChipLabel)
            assertEquals(2, manager.uiState.value.queueChips.size)

            manager.selectQueueChip(discoverChip)
            advanceUntilIdle()

            assertEquals("s1", manager.uiState.value.currentTrack?.id)
            assertEquals(listOf("s1", "d1"), manager.uiState.value.queueState.items.map { it.track.id })
            assertEquals("Discover", manager.uiState.value.activeQueueChipLabel)
        }

    @Test
    fun accountSync_mergesRemoteLikesAndHistoryAndSupportsRemoteUnlike() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val remoteFav = Track(id = "rf1", title = "Liked on Mobile")
            val remoteHist = Track(id = "rh1", title = "Played on Web")
            val catalog = FakeCatalogStore()
            val music =
                FakeMusicEngine(
                    remoteLikesInitial = listOf(remoteFav),
                    remoteHistoryInitial = listOf(remoteHist),
                )
            val manager =
                PlaybackSessionManager(
                    musicEngine = music,
                    lyricsEngine = emptyLyricsEngine,
                    queueStore = memoryQueueStore,
                    playerController = FakePlayerControl(),
                    catalogStore = catalog,
                    ioDispatcher = dispatcher,
                    mainDispatcher = dispatcher,
                )

            assertTrue(manager.activateAccountSession(AccountCredentials(cookie = "SAPISID=xyz")).isSuccess)
            advanceUntilIdle()

            assertEquals(listOf("rf1"), manager.uiState.value.remoteFavorites.map { it.id })
            assertEquals(listOf("rh1"), manager.uiState.value.remoteHistory.map { it.id })
            assertTrue(manager.uiState.value.favoriteIds.contains("rf1"))

            manager.toggleFavorite(remoteFav)
            advanceUntilIdle()

            assertFalse(manager.uiState.value.favoriteIds.contains("rf1"))
            assertTrue(manager.uiState.value.remoteFavorites.isEmpty())
            assertEquals(listOf("rf1" to false), music.remoteLikes)
        }
}
