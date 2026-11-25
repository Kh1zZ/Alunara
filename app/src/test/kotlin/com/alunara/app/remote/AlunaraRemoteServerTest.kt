package com.alunara.app.remote

import androidx.media3.common.Player
import com.alunara.app.playback.AudioPlayerControl
import com.alunara.app.playback.InMemoryPlaybackPreferences
import com.alunara.app.playback.PlaybackSessionManager
import com.alunara.app.playback.PlaybackSessionUiState
import com.alunara.app.ui.i18n.AppLanguage
import com.alunara.core.domain.contract.LyricsEngine
import com.alunara.core.domain.contract.MusicEngine
import com.alunara.core.domain.contract.QueueStore
import com.alunara.core.domain.model.ArtistRef
import com.alunara.core.domain.model.AudioStreamSource
import com.alunara.core.domain.model.HomeSection
import com.alunara.core.domain.model.LyricsDocument
import com.alunara.core.domain.model.LyricsProviderId
import com.alunara.core.domain.model.PlaybackStatus
import com.alunara.core.domain.model.PlaylistSummary
import com.alunara.core.domain.model.QueueItem
import com.alunara.core.domain.model.QueueState
import com.alunara.core.domain.model.SearchResults
import com.alunara.core.domain.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlunaraRemoteServerTest {
    private class StubVolumeController : TvVolumeController {
        var level = 7
        override fun currentVolume(): Int = level
        override fun maxVolume(): Int = 15
        override fun adjustVolume(delta: Int) { level = (level + delta).coerceIn(0, 15) }
        override fun setVolume(level: Int) { this.level = level.coerceIn(0, 15) }
    }

    private fun createServer(
        scope: CoroutineScope,
        prefs: InMemoryRemotePreferencesStore = InMemoryRemotePreferencesStore(initialPin = 345, initialToken = "tok-remote-test-1234"),
    ): AlunaraRemoteServer {
        val dummyMusic =
            object : MusicEngine {
                override suspend fun searchGuest(query: String): Result<SearchResults> = Result.success(SearchResults())
                override suspend fun fetchGuestHome(): Result<List<HomeSection>> = Result.success(emptyList())
                override suspend fun resolveAudioStream(trackId: String): Result<AudioStreamSource> =
                    Result.failure(UnsupportedOperationException("unused"))
            }
        val dummyLyrics =
            object : LyricsEngine {
                override suspend fun fetchLyrics(track: Track, preferredProvider: LyricsProviderId?): LyricsDocument =
                    LyricsDocument.Unavailable("none")
            }
        val dummyQueue =
            object : QueueStore {
                override suspend fun loadQueue(): QueueState = QueueState()
                override suspend fun saveQueue(state: QueueState) = Unit
            }
        val dummyPlayer =
            object : AudioPlayerControl {
                override var hasActivePlayer: Boolean = false
                override val activeTrackId: String? = null
                override fun prepareAudioStream(source: AudioStreamSource, startPositionMs: Long, playWhenReady: Boolean, listener: Player.Listener, track: Track?) = Unit
                override fun play() = Unit
                override fun pause() = Unit
                override fun stopCurrentTrack() = Unit
                override fun seekTo(positionMs: Long) = Unit
                override fun currentPositionMs(): Long = 0L
                override fun durationMs(): Long = 0L
                override fun isPlaying(): Boolean = false
                override fun onAppBackgroundedOrStopped() = Unit
            }
        val sessionManager =
            PlaybackSessionManager(
                musicEngine = dummyMusic,
                lyricsEngine = dummyLyrics,
                queueStore = dummyQueue,
                playerController = dummyPlayer,
                playbackPreferences = InMemoryPlaybackPreferences(),
                ioDispatcher = Dispatchers.Unconfined,
                mainDispatcher = Dispatchers.Unconfined,
            )
        return AlunaraRemoteServer(
            scope = scope,
            prefsStore = prefs,
            sessionManager = sessionManager,
            musicEngine = dummyMusic,
            volumeController = StubVolumeController(),
            initialLanguage = AppLanguage.INDONESIAN,
        )
    }

    @Test
    fun encodePairingCode_and_decodePairingCode_roundTripOnPort4581() {
        val homeCode = AlunaraRemoteServer.encodePairingCode("192.168.100.124", 4581, 2026)
        assertNotNull(homeCode)
        assertEquals(6, homeCode!!.replace("-", "").length)
        assertEquals(
            DecodedRemoteTarget("192.168.100.124", 4581, 2026),
            AlunaraRemoteServer.decodePairingCode(homeCode),
        )

        val corpCode = AlunaraRemoteServer.encodePairingCode("10.12.4.8", 4581, 91)
        assertNotNull(corpCode)
        assertEquals(7, corpCode!!.replace("-", "").length)
        assertEquals(
            DecodedRemoteTarget("10.12.4.8", 4581, 91),
            AlunaraRemoteServer.decodePairingCode(corpCode.lowercase()),
        )

        val vpnCode = AlunaraRemoteServer.encodePairingCode("172.18.2.9", 4581, 3001)
        assertNotNull(vpnCode)
        assertEquals(7, vpnCode!!.replace("-", "").length)
        assertEquals(
            DecodedRemoteTarget("172.18.2.9", 4581, 3001),
            AlunaraRemoteServer.decodePairingCode(vpnCode),
        )

        val customCode = AlunaraRemoteServer.encodePairingCode("192.168.100.124", 51234, 700)
        assertNotNull(customCode)
        assertEquals(12, customCode!!.replace("-", "").length)
        assertEquals(
            DecodedRemoteTarget("192.168.100.124", 51234, 700),
            AlunaraRemoteServer.decodePairingCode(customCode),
        )
    }

    @Test
    fun isPrivateIpv4_allowsOnlyLanAndLoopbackAddresses() {
        assertTrue(AlunaraRemoteServer.isPrivateIpv4("192.168.100.12"))
        assertTrue(AlunaraRemoteServer.isPrivateIpv4("10.0.0.5"))
        assertTrue(AlunaraRemoteServer.isPrivateIpv4("172.16.0.1"))
        assertTrue(AlunaraRemoteServer.isPrivateIpv4("172.31.255.254"))
        assertTrue(AlunaraRemoteServer.isPrivateIpv4("127.0.0.1"))
        assertTrue(AlunaraRemoteServer.isPrivateIpv4("::ffff:192.168.1.9"))

        assertFalse(AlunaraRemoteServer.isPrivateIpv4("8.8.8.8"))
        assertFalse(AlunaraRemoteServer.isPrivateIpv4("1.1.1.1"))
        assertFalse(AlunaraRemoteServer.isPrivateIpv4("172.15.0.1"))
        assertFalse(AlunaraRemoteServer.isPrivateIpv4("172.32.0.1"))
        assertFalse(AlunaraRemoteServer.isPrivateIpv4("0.0.0.0"))
        assertFalse(AlunaraRemoteServer.isPrivateIpv4(null))
    }

    @Test
    fun tokenValidation_and_pinRateLimitLockoutAndReset() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val prefs = InMemoryRemotePreferencesStore(initialPin = 0x1A2, initialToken = "secret-token-abc")
            val server = createServer(scope, prefs)

            assertTrue(server.isAuthorizedToken("secret-token-abc"))
            assertFalse(server.isAuthorizedToken("wrong-token"))
            assertFalse(server.isAuthorizedToken(null))

            // Wrong PIN attempts trigger lockout after MAX_FAILED_PAIR_ATTEMPTS
            repeat(AlunaraRemoteServer.MAX_FAILED_PAIR_ATTEMPTS) {
                assertFalse(server.verifyPairPinAttempt("000"))
            }
            assertTrue(server.status.value.lockTriggered)
            // Even correct PIN is rejected once locked
            assertFalse(server.verifyPairPinAttempt("1a2"))

            // Regenerating credentials rotates token, clears lockout, and unlocks pairing
            server.regenerateCode()
            assertFalse(server.status.value.lockTriggered)
            assertFalse(server.isAuthorizedToken("secret-token-abc"))
            val newPinHex = prefs.getOrCreatePairPin().toString(16)
            assertTrue(server.verifyPairPinAttempt(newPinHex))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun buildStateJson_and_remoteHtml_escapeStringsAndExposeAllFourTabs() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val server = createServer(scope)
            val track =
                Track(
                    id = "v-1",
                    title = """Song "<script>" Test""",
                    artists = listOf(ArtistRef(id = null, name = "Artist A")),
                )
            val uiState =
                PlaybackSessionUiState(
                    queueState =
                        QueueState(
                            items = listOf(QueueItem(queueItemId = "q1", track = track)),
                            currentIndex = 0,
                        ),
                    status = PlaybackStatus.PLAYING,
                    durationMs = 210_000L,
                    accountPlaylists = listOf(PlaylistSummary(id = "PL1", title = "Chill Mix")),
                )
            val json = server.buildStateJson(uiState, positionMs = 45_000L, currentVolume = 8, maxVolume = 15)
            assertFalse(json.contains("<script>"))
            assertTrue(json.contains("""\u003Cscript\u003E"""))
            assertTrue(json.contains(""""current":8,"max":15"""))
            assertTrue(json.contains(""""title":"Chill Mix""""))

            val html = AlunaraRemoteServer.buildRemoteWebAppHtml(token = "t-abc", language = AppLanguage.INDONESIAN)
            assertTrue(html.contains("""id="tab-now""""))
            assertTrue(html.contains("""id="tab-search""""))
            assertTrue(html.contains("""id="tab-queue""""))
            assertTrue(html.contains("""id="tab-dpad""""))
            assertTrue(html.contains("X-Alunara-Remote"))
            assertTrue(html.contains("visibilitychange"))
            assertTrue(html.contains("""id="subPlayerBtn""""))
            assertTrue(html.contains("""id="subLyricsBtn""""))
            assertTrue(html.contains("/api/lyrics"))
            assertFalse("Remote CSS must contain zero @keyframes", html.contains("@keyframes"))
            assertFalse("Remote CSS must contain zero transitions", html.contains("transition:"))
            assertFalse("Remote CSS must contain zero animations", html.contains("animation:"))
            assertFalse("Remote CSS must contain zero backdrop-filter", html.contains("backdrop-filter:"))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun buildPortalQrUrl_pointsToGithubPagesRemotePortal() {
        assertEquals(
            "https://kh1zz.github.io/Alunara/remote/?c=K7M9-P2",
            AlunaraRemoteServer.buildPortalQrUrl("K7M9-P2"),
        )
        assertEquals(null, AlunaraRemoteServer.buildPortalQrUrl("   "))
    }

    @Test
    fun buildLyricsJson_capsLinesAndLineLengthAndOmitsWordSync() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val server = createServer(scope)
            val track =
                Track(
                    id = "trk-lyr",
                    title = "Long Song",
                    artists = listOf(ArtistRef(id = null, name = "Singer")),
                )
            val longText = "A".repeat(220)
            val lines =
                (0 until 120).map { idx ->
                    com.alunara.core.domain.model.SyncedLyricLine(
                        startTimeMs = idx * 2_000L,
                        text = if (idx == 0) longText else "Line $idx",
                        words =
                            listOf(
                                com.alunara.core.domain.model.SyncedLyricWord(
                                    startTimeMs = idx * 2_000L,
                                    text = "word$idx",
                                ),
                            ),
                    )
                }
            val uiState =
                PlaybackSessionUiState(
                    queueState = QueueState(items = listOf(QueueItem("q1", track)), currentIndex = 0),
                    status = PlaybackStatus.PLAYING,
                    lyrics =
                        LyricsDocument.Synced(
                            provider = LyricsProviderId.LRCLIB,
                            lines = lines,
                        ),
                )
            val json = server.buildLyricsJson(uiState, positionMs = 4_500L)
            assertTrue(json.contains(""""mode":"synced""""))
            assertTrue(json.contains(""""totalLines":120"""))
            assertTrue(json.contains(""""maxLines":80"""))
            assertTrue(json.contains(""""truncated":true"""))
            assertTrue(json.contains(""""activeIndex":2"""))
            assertFalse(json.contains(""""i":80"""))
            assertFalse(json.contains("words"))
            assertFalse(json.contains("A".repeat(165)))
            assertTrue(json.toByteArray(Charsets.UTF_8).size <= AlunaraRemoteServer.MAX_REMOTE_LYRICS_JSON_BYTES)
        } finally {
            scope.cancel()
        }
    }
}

