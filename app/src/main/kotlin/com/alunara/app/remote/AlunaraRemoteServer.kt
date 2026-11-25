package com.alunara.app.remote

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import com.alunara.app.playback.PlaybackSessionManager
import com.alunara.app.playback.PlaybackSessionUiState
import com.alunara.app.ui.i18n.AppLanguage
import com.alunara.core.domain.contract.MusicEngine
import com.alunara.core.domain.lyrics.LyricsSyncResolver
import com.alunara.core.domain.model.ArtistRef
import com.alunara.core.domain.model.AuthSessionState
import com.alunara.core.domain.model.GenreCategory
import com.alunara.core.domain.model.GenreSection
import com.alunara.core.domain.model.LyricsDocument
import com.alunara.core.domain.model.PlaylistSummary
import com.alunara.core.domain.model.SearchResults
import com.alunara.core.domain.model.Track
import com.alunara.core.domain.policy.AudioQualityPreset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

interface TvVolumeController {
    fun currentVolume(): Int

    fun maxVolume(): Int

    fun adjustVolume(delta: Int)

    fun setVolume(level: Int)
}

class AndroidTvVolumeController(
    context: Context,
) : TvVolumeController {
    private val audioManager: AudioManager? =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    override fun currentVolume(): Int =
        runCatching { audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0 }.getOrDefault(0)

    override fun maxVolume(): Int =
        runCatching { audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 15 }.getOrDefault(15)

    override fun adjustVolume(delta: Int) {
        val am = audioManager ?: return
        val dir =
            when {
                delta > 0 -> AudioManager.ADJUST_RAISE
                delta < 0 -> AudioManager.ADJUST_LOWER
                else -> AudioManager.ADJUST_SAME
            }
        runCatching { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI) }
    }

    override fun setVolume(level: Int) {
        val am = audioManager ?: return
        val clamped = level.coerceIn(0, maxVolume())
        runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, clamped, AudioManager.FLAG_SHOW_UI) }
    }
}

data class RemoteServerStatus(
    val enabled: Boolean = true,
    val running: Boolean = false,
    val lanIp: String? = null,
    val port: Int = AlunaraRemoteServer.DEFAULT_REMOTE_PORT,
    val pairingCode: String? = null,
    val qrPairUrl: String? = null,
    val directLocalPairUrl: String? = null,
    val directRemoteUrl: String? = null,
    val portalDisplayUrl: String = AlunaraRemoteServer.REMOTE_PORTAL_DISPLAY_URL,
    val lockTriggered: Boolean = false,
)

internal data class DecodedRemoteTarget(
    val ip: String,
    val port: Int,
    val pin: Int,
)

private data class ParsedRemoteRequest(
    val method: String,
    val path: String,
    val query: String,
    val headers: Map<String, String>,
    val body: String,
)

/**
 * Local-only embedded HTTP remote control server running on preferred LAN port 4581.
 * Binds strictly to private RFC-1918 LAN IPv4 addresses, requires constant-time session token
 * + `X-Alunara-Remote` header on mutations (blocking CSRF), and never leaks credentials.
 */
class AlunaraRemoteServer(
    private val scope: CoroutineScope,
    private val prefsStore: RemotePreferencesStore,
    private val sessionManager: PlaybackSessionManager,
    private val musicEngine: MusicEngine,
    private val volumeController: TvVolumeController,
    initialLanguage: AppLanguage = AppLanguage.INDONESIAN,
) {
    @Volatile
    var language: AppLanguage = initialLanguage

    @Volatile
    var onDispatchDpadKey: ((Int) -> Unit)? = null

    @Volatile
    var onNavigateDestination: ((String) -> Unit)? = null

    private val mutableStatus = MutableStateFlow(RemoteServerStatus(enabled = prefsStore.isRemoteEnabled()))
    val status: StateFlow<RemoteServerStatus> = mutableStatus.asStateFlow()

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var acceptJob: Job? = null

    @Volatile
    private var activePairPin: Int = prefsStore.getOrCreatePairPin()

    @Volatile
    private var sessionToken: String = prefsStore.getOrCreateSessionToken()

    private val failedPairAttempts = AtomicInteger(0)

    private val knownTracksById = ConcurrentHashMap<String, Track>()

    @Volatile
    private var cachedGenres: List<GenreSection> = emptyList()

    fun syncWithPrefs() {
        if (prefsStore.isRemoteEnabled()) {
            start()
        } else {
            stop()
            mutableStatus.value = RemoteServerStatus(enabled = false, running = false)
        }
    }

    fun setEnabled(enabled: Boolean) {
        prefsStore.setRemoteEnabled(enabled)
        syncWithPrefs()
    }

    fun regenerateCode() {
        val (freshPin, freshToken) = prefsStore.regenerateCredentials()
        activePairPin = freshPin
        sessionToken = freshToken
        failedPairAttempts.set(0)
        if (prefsStore.isRemoteEnabled()) {
            val socket = serverSocket
            if (socket != null && !socket.isClosed) {
                val lanIp = socket.inetAddress?.hostAddress ?: detectLanIpv4Address()
                publishRunningStatus(lanIp, socket.localPort)
            } else {
                start()
            }
        }
    }

    fun start(): Boolean {
        if (!prefsStore.isRemoteEnabled()) {
            mutableStatus.value = RemoteServerStatus(enabled = false, running = false)
            return false
        }
        val existing = serverSocket
        if (existing != null && !existing.isClosed) {
            return true
        }
        val lanIp = detectLanIpv4Address()
        if (lanIp == null) {
            mutableStatus.value = RemoteServerStatus(enabled = true, running = false, lanIp = null)
            return false
        }
        val bindAddr = runCatching { InetAddress.getByName(lanIp) }.getOrNull() ?: return false
        val socket =
            runCatching {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(bindAddr, DEFAULT_REMOTE_PORT))
                }
            }.getOrNull()
                ?: runCatching {
                    ServerSocket().apply {
                        reuseAddress = true
                        bind(InetSocketAddress(bindAddr, 0))
                    }
                }.getOrNull()
        if (socket == null) {
            mutableStatus.value = RemoteServerStatus(enabled = true, running = false, lanIp = lanIp)
            return false
        }
        serverSocket = socket
        publishRunningStatus(lanIp, socket.localPort)

        acceptJob =
            scope.launch(Dispatchers.IO) {
                try {
                    while (isActive && !socket.isClosed) {
                        val client =
                            try {
                                socket.accept()
                            } catch (_: Throwable) {
                                break
                            }
                        launch(Dispatchers.IO) {
                            handleClient(client)
                        }
                    }
                } catch (cancel: CancellationException) {
                    throw cancel
                }
            }
        return true
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        mutableStatus.value =
            mutableStatus.value.copy(
                enabled = prefsStore.isRemoteEnabled(),
                running = false,
            )
    }

    private fun publishRunningStatus(
        lanIp: String?,
        resolvedPort: Int,
    ) {
        val rawPin = activePairPin
        val encoded = lanIp?.let { encodePairingCode(it, resolvedPort, rawPin) }
        val normalizedPin =
            if (encoded != null && encoded.replace("-", "").length == 12) {
                rawPin and 0x3FF
            } else {
                rawPin
            }
        activePairPin = normalizedPin
        val pinHex = normalizedPin.toString(16).padStart(3, '0')
        val localPairUrl = lanIp?.let { "http://$it:$resolvedPort/p?k=$pinHex" }
        val qrPairUrl = buildPortalQrUrl(encoded) ?: localPairUrl
        val directUrl = lanIp?.let { "http://$it:$resolvedPort/r?t=$sessionToken" }
        mutableStatus.value =
            RemoteServerStatus(
                enabled = true,
                running = true,
                lanIp = lanIp,
                port = resolvedPort,
                pairingCode = encoded,
                qrPairUrl = qrPairUrl,
                directLocalPairUrl = localPairUrl,
                directRemoteUrl = directUrl,
                lockTriggered = failedPairAttempts.get() >= MAX_FAILED_PAIR_ATTEMPTS,
            )
    }

    internal fun isAuthorizedToken(candidate: String?): Boolean {
        if (candidate.isNullOrEmpty()) return false
        val expectedBytes = sessionToken.toByteArray(StandardCharsets.UTF_8)
        val actualBytes = candidate.toByteArray(StandardCharsets.UTF_8)
        return MessageDigest.isEqual(expectedBytes, actualBytes)
    }

    internal fun verifyPairPinAttempt(rawPin: String): Boolean {
        if (failedPairAttempts.get() >= MAX_FAILED_PAIR_ATTEMPTS) {
            updateLockStatus()
            return false
        }
        val cleaned = rawPin.trim()
        val submitted = cleaned.toIntOrNull(16) ?: cleaned.toIntOrNull()
        if (submitted != null && submitted == activePairPin) {
            failedPairAttempts.set(0)
            updateLockStatus()
            return true
        }
        failedPairAttempts.incrementAndGet()
        updateLockStatus()
        return false
    }

    private fun updateLockStatus() {
        val locked = failedPairAttempts.get() >= MAX_FAILED_PAIR_ATTEMPTS
        if (mutableStatus.value.lockTriggered != locked) {
            mutableStatus.value = mutableStatus.value.copy(lockTriggered = locked)
        }
    }

    private suspend fun handleClient(socket: Socket) {
        socket.use { s ->
            val remoteIp = s.inetAddress?.hostAddress
            if (!isPrivateIpv4(remoteIp)) {
                return
            }
            s.soTimeout = SOCKET_TIMEOUT_MS
            val input = BufferedInputStream(s.getInputStream())
            val output = s.getOutputStream()

            val request =
                readHttpRequest(input) ?: run {
                    writeResponse(output, 400, "text/plain; charset=utf-8", "Invalid request")
                    return
                }

            val queryParams = parseQueryParams(request.query)
            if (request.method == "GET" && request.path == "/p") {
                val rawPin = queryParams["k"].orEmpty()
                if (verifyPairPinAttempt(rawPin)) {
                    writeResponse(
                        output = output,
                        status = 302,
                        contentType = "text/plain; charset=utf-8",
                        body = "Redirecting...",
                        extraHeaders = mapOf("Location" to "/r?t=$sessionToken"),
                    )
                } else {
                    val locked = failedPairAttempts.get() >= MAX_FAILED_PAIR_ATTEMPTS
                    val msg =
                        if (locked) {
                            "Pairing locked due to failed attempts. Regenerate code on TV Settings."
                        } else {
                            "Invalid TV Remote PIN."
                        }
                    writeResponse(output, 403, "text/plain; charset=utf-8", msg)
                }
                return
            }

            if (!isAuthorizedToken(queryParams["t"])) {
                writeResponse(output, 403, "text/plain; charset=utf-8", "Invalid or expired remote token")
                return
            }

            when {
                request.method == "GET" && (request.path == "/r" || request.path == "/remote" || request.path == "/") -> {
                    writeResponse(
                        output = output,
                        status = 200,
                        contentType = "text/html; charset=utf-8",
                        body = buildRemoteWebAppHtml(token = sessionToken, language = language),
                    )
                }

                request.method == "GET" && request.path == "/api/state" -> {
                    writeResponse(
                        output = output,
                        status = 200,
                        contentType = "application/json; charset=utf-8",
                        body =
                            buildStateJson(
                                uiState = sessionManager.uiState.value,
                                positionMs = sessionManager.positionMsFlow.value,
                                currentVolume = volumeController.currentVolume(),
                                maxVolume = volumeController.maxVolume(),
                            ),
                    )
                }

                request.method == "GET" && request.path == "/api/lyrics" -> {
                    withContext(Dispatchers.Main) {
                        sessionManager.requestLyricsForRemote()
                    }
                    writeResponse(
                        output = output,
                        status = 200,
                        contentType = "application/json; charset=utf-8",
                        body =
                            buildLyricsJson(
                                uiState = sessionManager.uiState.value,
                                positionMs = sessionManager.positionMsFlow.value,
                            ),
                    )
                }

                request.method == "GET" && request.path == "/api/genres" -> {
                    val genres =
                        if (cachedGenres.isNotEmpty()) {
                            cachedGenres
                        } else {
                            musicEngine.fetchMoodAndGenres().getOrDefault(emptyList()).also {
                                if (it.isNotEmpty()) cachedGenres = it
                            }
                        }
                    writeResponse(
                        output = output,
                        status = 200,
                        contentType = "application/json; charset=utf-8",
                        body = buildGenresJson(genres),
                    )
                }

                request.method == "GET" && request.path == "/api/genre" -> {
                    val browseId = queryParams["browseId"]?.trim().orEmpty()
                    val params = queryParams["params"]?.takeIf { it.isNotBlank() }
                    val title = queryParams["title"]?.trim().orEmpty()
                    if (browseId.isEmpty() || title.isEmpty()) {
                        writeResponse(output, 400, "application/json; charset=utf-8", """{"ok":false,"error":"Missing genre"}""")
                        return
                    }
                    val category = GenreCategory(id = "$browseId:$title", title = title, browseId = browseId, params = params)
                    val res = musicEngine.fetchGenreContent(category)
                    val results = res.getOrNull() ?: SearchResults()
                    cacheTracks(results.songs)
                    writeResponse(
                        output = output,
                        status = if (res.isSuccess) 200 else 500,
                        contentType = "application/json; charset=utf-8",
                        body = buildSearchResultsJson(results, error = res.exceptionOrNull()?.message),
                    )
                }

                request.method == "GET" && request.path == "/api/search" -> {
                    val q = queryParams["q"]?.trim().orEmpty()
                    if (q.isEmpty()) {
                        writeResponse(output, 200, "application/json; charset=utf-8", buildSearchResultsJson(SearchResults()))
                        return
                    }
                    val res = musicEngine.searchGuest(q)
                    val results = res.getOrNull() ?: SearchResults()
                    cacheTracks(results.songs)
                    writeResponse(
                        output = output,
                        status = if (res.isSuccess) 200 else 500,
                        contentType = "application/json; charset=utf-8",
                        body = buildSearchResultsJson(results, error = res.exceptionOrNull()?.message),
                    )
                }

                request.method == "GET" && request.path == "/api/playlist" -> {
                    val playlistId = queryParams["id"]?.trim().orEmpty()
                    if (playlistId.isEmpty()) {
                        writeResponse(output, 400, "application/json; charset=utf-8", """{"ok":false}""")
                        return
                    }
                    val detail = musicEngine.fetchPlaylistDetail(playlistId).getOrNull()
                    if (detail == null) {
                        writeResponse(output, 404, "application/json; charset=utf-8", """{"ok":false}""")
                        return
                    }
                    cacheTracks(detail.tracks)
                    writeResponse(
                        output = output,
                        status = 200,
                        contentType = "application/json; charset=utf-8",
                        body = buildSearchResultsJson(SearchResults(songs = detail.tracks)),
                    )
                }

                request.method == "GET" && request.path == "/api/artist" -> {
                    val artistId = queryParams["id"]?.trim().orEmpty()
                    if (artistId.isEmpty()) {
                        writeResponse(output, 400, "application/json; charset=utf-8", """{"ok":false}""")
                        return
                    }
                    val detail = musicEngine.fetchArtistDetail(artistId).getOrNull()
                    if (detail == null) {
                        writeResponse(output, 404, "application/json; charset=utf-8", """{"ok":false}""")
                        return
                    }
                    cacheTracks(detail.topSongs)
                    writeResponse(
                        output = output,
                        status = 200,
                        contentType = "application/json; charset=utf-8",
                        body =
                            buildSearchResultsJson(
                                SearchResults(
                                    songs = detail.topSongs,
                                    albums = detail.albums,
                                    playlists = detail.playlists,
                                ),
                            ),
                    )
                }

                request.method == "POST" && request.path == "/api/cmd" -> {
                    if (request.headers["x-alunara-remote"] != "1") {
                        writeResponse(output, 403, "application/json; charset=utf-8", """{"ok":false,"error":"Missing CSRF header"}""")
                        return
                    }
                    val form = parseQueryParams(request.body)
                    val ok = handleCommand(form)
                    val stateJson =
                        buildStateJson(
                            uiState = sessionManager.uiState.value,
                            positionMs = sessionManager.positionMsFlow.value,
                            currentVolume = volumeController.currentVolume(),
                            maxVolume = volumeController.maxVolume(),
                        )
                    writeResponse(
                        output = output,
                        status = if (ok) 200 else 400,
                        contentType = "application/json; charset=utf-8",
                        body = """{"ok":$ok,"state":$stateJson}""",
                    )
                }

                else -> {
                    writeResponse(output, 404, "text/plain; charset=utf-8", "Not found")
                }
            }
        }
    }

    private suspend fun handleCommand(form: Map<String, String>): Boolean {
        val action = form["action"]?.trim().orEmpty()
        if (action.isEmpty()) return false

        when (action) {
            "play_pause" -> withContext(Dispatchers.Main) { sessionManager.togglePlayPause() }
            "next" -> withContext(Dispatchers.Main) { sessionManager.skipNext() }
            "prev" -> withContext(Dispatchers.Main) { sessionManager.skipPrevious() }
            "seek_to" -> {
                val ms = form["ms"]?.toLongOrNull() ?: return false
                withContext(Dispatchers.Main) { sessionManager.seekTo(ms.coerceAtLeast(0L)) }
            }
            "seek_by" -> {
                val delta = form["deltaMs"]?.toLongOrNull() ?: return false
                withContext(Dispatchers.Main) { sessionManager.seekBy(delta) }
            }
            "toggle_like" -> {
                val targetId = form["trackId"]?.trim()?.takeIf { it.isNotEmpty() }
                withContext(Dispatchers.Main) {
                    val track = targetId?.let { knownTracksById[it] } ?: sessionManager.uiState.value.currentTrack
                    if (track != null) {
                        sessionManager.toggleFavorite(track)
                    }
                }
            }
            "start_radio" -> {
                val seed =
                    form["trackId"]?.trim()?.let { knownTracksById[it] }
                        ?: sessionManager.uiState.value.currentTrack
                        ?: return false
                withContext(Dispatchers.Main) { sessionManager.startRadio(seed) }
            }
            "play_queue_at" -> {
                val idx = form["index"]?.toIntOrNull() ?: return false
                withContext(Dispatchers.Main) { sessionManager.playTrackAt(idx) }
            }
            "remove_queue_at" -> {
                val idx = form["index"]?.toIntOrNull() ?: return false
                withContext(Dispatchers.Main) { sessionManager.removeFromQueue(idx) }
            }
            "move_queue" -> {
                val from = form["from"]?.toIntOrNull() ?: return false
                val to = form["to"]?.toIntOrNull() ?: return false
                withContext(Dispatchers.Main) { sessionManager.moveQueueItem(from, to) }
            }
            "clear_queue" -> withContext(Dispatchers.Main) { sessionManager.clearQueue() }
            "refresh_radio" -> withContext(Dispatchers.Main) { sessionManager.refreshCurrentTrackRadio() }
            "select_chip" -> {
                val label = form["label"]?.trim().orEmpty()
                withContext(Dispatchers.Main) {
                    val chip =
                        if (label.isEmpty()) {
                            null
                        } else {
                            sessionManager.uiState.value.queueChips.firstOrNull { it.label == label }
                        }
                    sessionManager.selectQueueChip(chip)
                }
            }
            "play_track" -> {
                val track = resolveTrackFromForm(form) ?: return false
                withContext(Dispatchers.Main) { sessionManager.playTracks(listOf(track), 0) }
            }
            "enqueue_track" -> {
                val track = resolveTrackFromForm(form) ?: return false
                withContext(Dispatchers.Main) { sessionManager.enqueue(track) }
            }
            "play_playlist" -> {
                val playlistId = form["playlistId"]?.trim()?.takeIf { it.isNotEmpty() } ?: return false
                val detail = musicEngine.fetchPlaylistDetail(playlistId).getOrNull() ?: return false
                if (detail.tracks.isEmpty()) return false
                cacheTracks(detail.tracks)
                withContext(Dispatchers.Main) { sessionManager.playTracks(detail.tracks, 0) }
            }
            "add_to_playlist" -> {
                val playlistId = form["playlistId"]?.trim()?.takeIf { it.isNotEmpty() } ?: return false
                val track =
                    resolveTrackFromForm(form)
                        ?: sessionManager.uiState.value.currentTrack
                        ?: return false
                withContext(Dispatchers.Main) {
                    val summary =
                        sessionManager.uiState.value.accountPlaylists.firstOrNull { it.id == playlistId }
                            ?: PlaylistSummary(id = playlistId, title = form["playlistTitle"].orEmpty().ifBlank { "Playlist" })
                    sessionManager.addTrackToAccountPlaylist(summary, track)
                }
            }
            "create_playlist" -> {
                val title = form["title"]?.trim()?.takeIf { it.isNotEmpty() } ?: return false
                val requestedTrackId = form["trackId"]?.trim()?.takeIf { it.isNotEmpty() }
                val initialTrack =
                    requestedTrackId?.let { knownTracksById[it] }
                        ?: sessionManager.uiState.value.currentTrack
                withContext(Dispatchers.Main) {
                    sessionManager.createAccountPlaylist(title = title, initialTrack = initialTrack)
                }
            }
            "set_quality" -> {
                val presetName = form["preset"]?.trim()?.uppercase(Locale.ROOT).orEmpty()
                val preset = AudioQualityPreset.entries.firstOrNull { it.name == presetName } ?: return false
                withContext(Dispatchers.Main) { sessionManager.setAudioQuality(preset) }
            }
            "toggle_autoplay" -> {
                withContext(Dispatchers.Main) {
                    sessionManager.setAutoplay(!sessionManager.uiState.value.queueState.autoplayEnabled)
                }
            }
            "toggle_romaji" -> withContext(Dispatchers.Main) { sessionManager.toggleRomanizedLyrics() }
            "lyrics_offset" -> {
                val delta = form["deltaMs"]?.toLongOrNull() ?: return false
                withContext(Dispatchers.Main) { sessionManager.adjustLyricsSyncOffset(delta) }
            }
            "lyrics_reset" -> withContext(Dispatchers.Main) { sessionManager.resetLyricsSyncOffset() }
            "cycle_lyrics_provider" -> withContext(Dispatchers.Main) { sessionManager.cycleLyricsProvider() }
            "volume_delta" -> {
                val delta = form["delta"]?.toIntOrNull() ?: return false
                volumeController.adjustVolume(delta)
            }
            "volume_set" -> {
                val level = form["level"]?.toIntOrNull() ?: return false
                volumeController.setVolume(level)
            }
            "dpad" -> {
                val keyCode =
                    when (form["key"]?.trim()?.uppercase(Locale.ROOT)) {
                        "UP" -> KeyEvent.KEYCODE_DPAD_UP
                        "DOWN" -> KeyEvent.KEYCODE_DPAD_DOWN
                        "LEFT" -> KeyEvent.KEYCODE_DPAD_LEFT
                        "RIGHT" -> KeyEvent.KEYCODE_DPAD_RIGHT
                        "OK", "CENTER" -> KeyEvent.KEYCODE_DPAD_CENTER
                        "BACK" -> KeyEvent.KEYCODE_BACK
                        else -> return false
                    }
                withContext(Dispatchers.Main) { onDispatchDpadKey?.invoke(keyCode) }
            }
            "nav" -> {
                val dest = form["dest"]?.trim()?.uppercase(Locale.ROOT).orEmpty()
                if (dest !in setOf("HOME", "SEARCH", "LIBRARY", "SETTINGS", "NOW_PLAYING")) return false
                withContext(Dispatchers.Main) { onNavigateDestination?.invoke(dest) }
            }
            else -> return false
        }
        return true
    }

    private fun cacheTracks(tracks: List<Track>) {
        if (knownTracksById.size > 250) {
            knownTracksById.clear()
        }
        tracks.forEach { trk ->
            if (trk.id.isNotBlank()) {
                knownTracksById[trk.id] = trk
            }
        }
    }

    private fun resolveTrackFromForm(form: Map<String, String>): Track? {
        val id = form["id"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: form["trackId"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null
        knownTracksById[id]?.let { return it }
        val title = form["title"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val artistName = form["artist"]?.trim().orEmpty()
        val thumb = form["thumb"]?.trim()?.takeIf { it.isNotEmpty() }
        val duration = form["duration"]?.toIntOrNull()
        return Track(
            id = id,
            title = title,
            artists = if (artistName.isNotEmpty()) listOf(ArtistRef(id = null, name = artistName)) else emptyList(),
            thumbnailUrl = thumb,
            durationSeconds = duration,
        ).also { knownTracksById[id] = it }
    }

    internal fun buildStateJson(
        uiState: PlaybackSessionUiState,
        positionMs: Long,
        currentVolume: Int,
        maxVolume: Int,
    ): String {
        val track = uiState.currentTrack
        val authed = uiState.authSession as? AuthSessionState.Authenticated
        val activeLyricIdx = computeRemoteActiveLyricIndex(uiState.lyrics, positionMs, uiState.lyricsSyncOffsetMs)
        val hasLyrics = uiState.lyrics is LyricsDocument.Synced || uiState.lyrics is LyricsDocument.Plain
        val activeProviderName =
            uiState.selectedLyricsProvider?.name
                ?: when (val doc = uiState.lyrics) {
                    is LyricsDocument.Synced -> doc.provider.name
                    is LyricsDocument.Plain -> doc.provider.name
                    is LyricsDocument.Unavailable -> ""
                }
        return buildString {
            append('{')
            append("\"status\":").append(escapeJsonString(uiState.status.name)).append(',')
            append("\"positionMs\":").append(positionMs.coerceAtLeast(0L)).append(',')
            append("\"durationMs\":").append(uiState.durationMs.coerceAtLeast(0L)).append(',')
            append("\"audioQuality\":").append(escapeJsonString(uiState.audioQuality.name)).append(',')
            append("\"autoplay\":").append(uiState.queueState.autoplayEnabled).append(',')
            append("\"codec\":").append(escapeJsonString(uiState.activeStreamCodecLabel.orEmpty())).append(',')
            append("\"volume\":{\"current\":").append(currentVolume).append(",\"max\":").append(maxVolume).append("},")
            append("\"lyrics\":{")
            append("\"hasRomaji\":").append(uiState.hasRomanizedLyrics).append(',')
            append("\"showRomaji\":").append(uiState.showRomanizedLyrics).append(',')
            append("\"offsetMs\":").append(uiState.lyricsSyncOffsetMs).append(',')
            append("\"provider\":").append(escapeJsonString(activeProviderName)).append(',')
            append("\"activeIndex\":").append(activeLyricIdx).append(',')
            append("\"hasLyrics\":").append(hasLyrics).append(',')
            append("\"loading\":").append(uiState.lyricsLoading)
            append("},")
            append("\"auth\":{")
            append("\"authenticated\":").append(authed != null).append(',')
            append("\"accountName\":").append(escapeJsonString(authed?.profile?.name.orEmpty()))
            append("},")
            append("\"track\":")
            if (track == null) {
                append("null")
            } else {
                append('{')
                append("\"id\":").append(escapeJsonString(track.id)).append(',')
                append("\"title\":").append(escapeJsonString(track.title)).append(',')
                append("\"artist\":").append(escapeJsonString(track.primaryArtistName)).append(',')
                append("\"album\":").append(escapeJsonString(track.album?.title.orEmpty())).append(',')
                append("\"thumbnailUrl\":").append(escapeJsonString(track.thumbnailUrl.orEmpty())).append(',')
                append("\"isFavorite\":").append(track.id in uiState.favoriteIds)
                append('}')
            }
            append(',')
            append("\"queue\":{\"currentIndex\":").append(uiState.queueState.currentIndex).append(",\"items\":[")
            uiState.queueState.items.take(MAX_QUEUE_JSON_ITEMS).forEachIndexed { idx, item ->
                if (idx > 0) append(',')
                append('{')
                append("\"index\":").append(idx).append(',')
                append("\"id\":").append(escapeJsonString(item.track.id)).append(',')
                append("\"title\":").append(escapeJsonString(item.track.title)).append(',')
                append("\"artist\":").append(escapeJsonString(item.track.primaryArtistName)).append(',')
                append("\"durationSec\":").append(item.track.durationSeconds ?: 0).append(',')
                append("\"thumbnailUrl\":").append(escapeJsonString(item.track.thumbnailUrl.orEmpty()))
                append('}')
            }
            append("]},")
            append("\"chips\":{\"activeLabel\":").append(escapeJsonString(uiState.activeQueueChipLabel.orEmpty())).append(",\"items\":[")
            uiState.queueChips.forEachIndexed { idx, chip ->
                if (idx > 0) append(',')
                append('{')
                append("\"label\":").append(escapeJsonString(chip.label)).append(',')
                append("\"selected\":").append(chip.label == uiState.activeQueueChipLabel)
                append('}')
            }
            append("]},")
            append("\"playlists\":[")
            uiState.accountPlaylists.forEachIndexed { idx, pl ->
                if (idx > 0) append(',')
                append('{')
                append("\"id\":").append(escapeJsonString(pl.id)).append(',')
                append("\"title\":").append(escapeJsonString(pl.title)).append(',')
                append("\"trackCount\":").append(escapeJsonString(pl.trackCountText.orEmpty()))
                append('}')
            }
            append("]}")
        }
    }

    internal fun buildLyricsJson(
        uiState: PlaybackSessionUiState,
        positionMs: Long,
    ): String {
        val trackId = uiState.currentTrack?.id.orEmpty()
        val doc = uiState.lyrics
        val activeIdx = computeRemoteActiveLyricIndex(doc, positionMs, uiState.lyricsSyncOffsetMs)
        val showRomaji = uiState.showRomanizedLyrics
        val mode =
            when {
                trackId.isEmpty() -> "unavailable"
                uiState.lyricsLoading -> "loading"
                doc is LyricsDocument.Synced -> "synced"
                doc is LyricsDocument.Plain -> "plain"
                else -> "unavailable"
            }
        val providerName =
            when (doc) {
                is LyricsDocument.Synced -> doc.provider.name
                is LyricsDocument.Plain -> doc.provider.name
                is LyricsDocument.Unavailable -> uiState.selectedLyricsProvider?.name.orEmpty()
            }

        val cappedLines = ArrayList<Pair<String, String>>(MAX_REMOTE_LYRIC_LINES)
        var totalLines = 0
        var truncated = false
        when (doc) {
            is LyricsDocument.Synced -> {
                totalLines = doc.lines.size
                if (totalLines > MAX_REMOTE_LYRIC_LINES) truncated = true
                for (i in 0 until minOf(totalLines, MAX_REMOTE_LYRIC_LINES)) {
                    val line = doc.lines[i]
                    val mainText = clampLyricLine(line.text)
                    val romText = if (showRomaji) clampLyricLine(line.romanizedText.orEmpty()) else ""
                    cappedLines.add(mainText to romText)
                }
            }
            is LyricsDocument.Plain -> {
                val rawMain = doc.text.lines()
                val rawRom = if (showRomaji) doc.romanizedText?.lines().orEmpty() else emptyList()
                totalLines = rawMain.size
                if (totalLines > MAX_REMOTE_LYRIC_LINES) truncated = true
                for (i in 0 until minOf(totalLines, MAX_REMOTE_LYRIC_LINES)) {
                    val mainText = clampLyricLine(rawMain[i])
                    val romText = if (showRomaji) clampLyricLine(rawRom.getOrNull(i).orEmpty()) else ""
                    cappedLines.add(mainText to romText)
                }
            }
            is LyricsDocument.Unavailable -> Unit
        }

        val linesJson = StringBuilder()
        var emittedLines = 0
        for (idx in cappedLines.indices) {
            val (mainText, romText) = cappedLines[idx]
            val entry =
                buildString {
                    if (emittedLines > 0) append(',')
                    append("{\"i\":").append(idx)
                    append(",\"t\":").append(escapeJsonString(mainText))
                    if (romText.isNotEmpty()) {
                        append(",\"r\":").append(escapeJsonString(romText))
                    }
                    append('}')
                }
            if (linesJson.length + entry.length > MAX_REMOTE_LYRICS_JSON_BYTES - 512) {
                truncated = true
                break
            }
            linesJson.append(entry)
            emittedLines++
        }

        return buildString {
            append('{')
            append("\"trackId\":").append(escapeJsonString(trackId)).append(',')
            append("\"mode\":").append(escapeJsonString(mode)).append(',')
            append("\"provider\":").append(escapeJsonString(providerName)).append(',')
            append("\"showRomaji\":").append(showRomaji).append(',')
            append("\"hasRomaji\":").append(uiState.hasRomanizedLyrics).append(',')
            append("\"offsetMs\":").append(uiState.lyricsSyncOffsetMs).append(',')
            append("\"activeIndex\":").append(if (activeIdx in 0 until emittedLines) activeIdx else -1).append(',')
            append("\"totalLines\":").append(totalLines).append(',')
            append("\"maxLines\":").append(MAX_REMOTE_LYRIC_LINES).append(',')
            append("\"truncated\":").append(truncated).append(',')
            append("\"lines\":[").append(linesJson).append("]}")
        }
    }

    private fun computeRemoteActiveLyricIndex(
        lyrics: LyricsDocument,
        positionMs: Long,
        offsetMs: Long,
    ): Int {
        val synced = lyrics as? LyricsDocument.Synced ?: return -1
        if (synced.lines.isEmpty()) return -1
        return LyricsSyncResolver.findActiveLineIndex(
            lines = synced.lines,
            positionMs = (positionMs + offsetMs).coerceAtLeast(0L),
        )
    }

    private fun clampLyricLine(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.length <= MAX_REMOTE_LYRIC_LINE_CHARS) {
            trimmed
        } else {
            trimmed.substring(0, MAX_REMOTE_LYRIC_LINE_CHARS - 1) + "…"
        }
    }

    private fun buildGenresJson(sections: List<GenreSection>): String =
        buildString {
            append("{\"ok\":true,\"sections\":[")
            sections.forEachIndexed { sIdx, sec ->
                if (sIdx > 0) append(',')
                append("{\"title\":").append(escapeJsonString(sec.title)).append(",\"items\":[")
                sec.items.forEachIndexed { iIdx, item ->
                    if (iIdx > 0) append(',')
                    append('{')
                    append("\"id\":").append(escapeJsonString(item.id)).append(',')
                    append("\"title\":").append(escapeJsonString(item.title)).append(',')
                    append("\"browseId\":").append(escapeJsonString(item.browseId)).append(',')
                    append("\"params\":").append(escapeJsonString(item.params.orEmpty())).append(',')
                    append("\"color\":").append(escapeJsonString(String.format(Locale.ROOT, "#%06X", item.stripeColorArgb.toInt() and 0xFFFFFF)))
                    append('}')
                }
                append("]}")
            }
            append("]}")
        }

    private fun buildSearchResultsJson(
        results: SearchResults,
        error: String? = null,
    ): String =
        buildString {
            append('{')
            append("\"ok\":").append(error == null).append(',')
            append("\"error\":").append(escapeJsonString(error.orEmpty())).append(',')
            append("\"songs\":[")
            results.songs.take(30).forEachIndexed { idx, trk ->
                if (idx > 0) append(',')
                append('{')
                append("\"id\":").append(escapeJsonString(trk.id)).append(',')
                append("\"title\":").append(escapeJsonString(trk.title)).append(',')
                append("\"artist\":").append(escapeJsonString(trk.primaryArtistName)).append(',')
                append("\"duration\":").append(trk.durationSeconds ?: 0).append(',')
                append("\"thumb\":").append(escapeJsonString(trk.thumbnailUrl.orEmpty()))
                append('}')
            }
            append("],\"artists\":[")
            results.artists.take(16).forEachIndexed { idx, art ->
                if (idx > 0) append(',')
                append('{')
                append("\"id\":").append(escapeJsonString(art.id.orEmpty())).append(',')
                append("\"name\":").append(escapeJsonString(art.name)).append(',')
                append("\"thumb\":").append(escapeJsonString(art.thumbnailUrl.orEmpty()))
                append('}')
            }
            append("],\"playlists\":[")
            results.playlists.take(16).forEachIndexed { idx, pl ->
                if (idx > 0) append(',')
                append('{')
                append("\"id\":").append(escapeJsonString(pl.id)).append(',')
                append("\"title\":").append(escapeJsonString(pl.title)).append(',')
                append("\"author\":").append(escapeJsonString(pl.author ?: pl.trackCountText.orEmpty())).append(',')
                append("\"thumb\":").append(escapeJsonString(pl.thumbnailUrl.orEmpty()))
                append('}')
            }
            append("],\"albums\":[")
            results.albums.take(16).forEachIndexed { idx, alb ->
                if (idx > 0) append(',')
                append('{')
                append("\"id\":").append(escapeJsonString(alb.id)).append(',')
                append("\"title\":").append(escapeJsonString(alb.title)).append(',')
                append("\"artist\":").append(escapeJsonString(alb.artists.joinToString(", ") { it.name })).append(',')
                append("\"thumb\":").append(escapeJsonString(alb.thumbnailUrl.orEmpty()))
                append('}')
            }
            append("]}")
        }

    private fun readHttpRequest(input: BufferedInputStream): ParsedRemoteRequest? {
        val headerBytes = ByteArrayOutputStream()
        var state = 0
        while (headerBytes.size() < MAX_HEADER_BYTES) {
            val b = input.read()
            if (b == -1) break
            headerBytes.write(b)
            state =
                when {
                    state == 0 && b == '\r'.code -> 1
                    state == 1 && b == '\n'.code -> 2
                    state == 2 && b == '\r'.code -> 3
                    state == 3 && b == '\n'.code -> 4
                    b == '\r'.code -> 1
                    else -> 0
                }
            if (state == 4) break
        }
        val headerText = headerBytes.toString(StandardCharsets.UTF_8.name())
        val lines = headerText.split("\r\n")
        val requestLine = lines.firstOrNull()?.split(" ") ?: return null
        if (requestLine.size < 2) return null
        val method = requestLine[0].uppercase(Locale.ROOT)
        val rawTarget = requestLine[1]
        val qIdx = rawTarget.indexOf('?')
        val path = if (qIdx >= 0) rawTarget.substring(0, qIdx) else rawTarget
        val query = if (qIdx >= 0) rawTarget.substring(qIdx + 1) else ""

        val headers = HashMap<String, String>()
        for (line in lines.drop(1)) {
            val colon = line.indexOf(':')
            if (colon > 0) {
                val key = line.substring(0, colon).trim().lowercase(Locale.ROOT)
                val value = line.substring(colon + 1).trim()
                headers[key] = value
            }
        }
        val contentLength = headers["content-length"]?.toIntOrNull()?.coerceIn(0, MAX_BODY_BYTES) ?: 0
        val body =
            if (contentLength > 0) {
                val buf = ByteArray(contentLength)
                var readTotal = 0
                while (readTotal < contentLength) {
                    val n = input.read(buf, readTotal, contentLength - readTotal)
                    if (n <= 0) break
                    readTotal += n
                }
                String(buf, 0, readTotal, StandardCharsets.UTF_8)
            } else {
                ""
            }
        return ParsedRemoteRequest(
            method = method,
            path = path,
            query = query,
            headers = headers,
            body = body,
        )
    }

    private fun parseQueryParams(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return raw
            .split('&')
            .mapNotNull { pair ->
                val eq = pair.indexOf('=')
                if (eq <= 0) {
                    null
                } else {
                    urlDecode(pair.substring(0, eq)) to urlDecode(pair.substring(eq + 1))
                }
            }.toMap()
    }

    private fun urlDecode(value: String): String =
        runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrDefault(value)

    private fun writeResponse(
        output: OutputStream,
        status: Int,
        contentType: String,
        body: String,
        extraHeaders: Map<String, String> = emptyMap(),
    ) {
        val statusText =
            when (status) {
                200 -> "OK"
                302 -> "Found"
                400 -> "Bad Request"
                403 -> "Forbidden"
                404 -> "Not Found"
                else -> "Error"
            }
        val bodyBytes = body.toByteArray(StandardCharsets.UTF_8)
        val headers =
            buildString {
                append("HTTP/1.1 ").append(status).append(' ').append(statusText).append("\r\n")
                append("Content-Type: ").append(contentType).append("\r\n")
                append("Content-Length: ").append(bodyBytes.size).append("\r\n")
                append("Cache-Control: no-store, no-cache\r\n")
                append("X-Content-Type-Options: nosniff\r\n")
                extraHeaders.forEach { (k, v) ->
                    append(k).append(": ").append(v).append("\r\n")
                }
                append("Connection: close\r\n\r\n")
            }.toByteArray(StandardCharsets.US_ASCII)
        output.write(headers)
        output.write(bodyBytes)
        output.flush()
    }

    companion object {
        const val DEFAULT_REMOTE_PORT = 4581
        const val REMOTE_PORTAL_DISPLAY_URL = "kh1zz.github.io/Alunara/remote"
        const val MAX_FAILED_PAIR_ATTEMPTS = 8
        const val MAX_REMOTE_LYRIC_LINES = 80
        const val MAX_REMOTE_LYRIC_LINE_CHARS = 160
        const val MAX_REMOTE_LYRICS_JSON_BYTES = 16_384
        private const val SOCKET_TIMEOUT_MS = 4_000
        private const val MAX_HEADER_BYTES = 8_192
        private const val MAX_BODY_BYTES = 16_384
        private const val MAX_QUEUE_JSON_ITEMS = 60
        private const val PAIR_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

        internal fun buildPortalQrUrl(pairingCode: String?): String? =
            pairingCode?.takeIf { it.isNotBlank() }?.let { "https://$REMOTE_PORTAL_DISPLAY_URL/?c=$it" }

        internal fun encodePairingCode(
            ip: String,
            port: Int,
            pin: Int,
        ): String? {
            if (port !in 1..65535) return null
            val parts = ip.trim().split('.')
            if (parts.size != 4) return null
            val octets = parts.map { it.toIntOrNull() ?: return null }
            if (octets.any { it !in 0..255 }) return null
            val o0 = octets[0]
            val o1 = octets[1]
            val o2 = octets[2]
            val o3 = octets[3]

            return when {
                port == DEFAULT_REMOTE_PORT && o0 == 192 && o1 == 168 -> {
                    val packed =
                        ((o2.toLong() and 0xFFL) shl 20) or
                            ((o3.toLong() and 0xFFL) shl 12) or
                            (pin.toLong() and 0xFFFL)
                    val chars = encodeBase32(packed, 6)
                    "${chars.substring(0, 4)}-${chars.substring(4)}"
                }
                port == DEFAULT_REMOTE_PORT && ((o0 == 10 && o1 in 0..15) || (o0 == 172 && o1 in 16..31)) -> {
                    val kind = if (o0 == 10) 1L else 0L
                    val sub = if (o0 == 10) o1.toLong() else (o1 - 16).toLong()
                    val packed =
                        (1L shl 33) or
                            (kind shl 32) or
                            ((sub and 0xFL) shl 28) or
                            ((o2.toLong() and 0xFFL) shl 20) or
                            ((o3.toLong() and 0xFFL) shl 12) or
                            (pin.toLong() and 0xFFFL)
                    val chars = encodeBase32(packed, 7)
                    "${chars.substring(0, 4)}-${chars.substring(4)}"
                }
                else -> {
                    val ip32 =
                        ((o0.toLong() and 0xFFL) shl 24) or
                            ((o1.toLong() and 0xFFL) shl 16) or
                            ((o2.toLong() and 0xFFL) shl 8) or
                            (o3.toLong() and 0xFFL)
                    val packed =
                        (2L shl 58) or
                            (ip32 shl 26) or
                            ((port.toLong() and 0xFFFFL) shl 10) or
                            (pin.toLong() and 0x3FFL)
                    val chars = encodeBase32(packed, 12)
                    "${chars.substring(0, 4)}-${chars.substring(4, 8)}-${chars.substring(8)}"
                }
            }
        }

        internal fun decodePairingCode(code: String): DecodedRemoteTarget? {
            val clean = code.uppercase(Locale.ROOT).filter { it.isLetterOrDigit() }
            if (clean.length != 6 && clean.length != 7 && clean.length != 12) return null
            var packed = 0L
            for (ch in clean) {
                val idx = PAIR_ALPHABET.indexOf(ch)
                if (idx < 0) return null
                packed = (packed shl 5) or idx.toLong()
            }
            return when (clean.length) {
                6 -> {
                    val mode = (packed ushr 28) and 0x3L
                    if (mode != 0L) return null
                    val o2 = ((packed ushr 20) and 0xFFL).toInt()
                    val o3 = ((packed ushr 12) and 0xFFL).toInt()
                    val pin = (packed and 0xFFFL).toInt()
                    DecodedRemoteTarget("192.168.$o2.$o3", DEFAULT_REMOTE_PORT, pin)
                }
                7 -> {
                    val mode = (packed ushr 33) and 0x3L
                    if (mode != 1L) return null
                    val kind = (packed ushr 32) and 1L
                    val sub = ((packed ushr 28) and 0xFL).toInt()
                    val o0 = if (kind == 1L) 10 else 172
                    val o1 = if (kind == 1L) sub else (16 + sub)
                    val o2 = ((packed ushr 20) and 0xFFL).toInt()
                    val o3 = ((packed ushr 12) and 0xFFL).toInt()
                    val pin = (packed and 0xFFFL).toInt()
                    DecodedRemoteTarget("$o0.$o1.$o2.$o3", DEFAULT_REMOTE_PORT, pin)
                }
                else -> {
                    val mode = (packed ushr 58) and 0x3L
                    if (mode != 2L) return null
                    val ip32 = (packed ushr 26) and 0xFFFFFFFFL
                    val port = ((packed ushr 10) and 0xFFFFL).toInt()
                    val pin = (packed and 0x3FFL).toInt()
                    if (port <= 0) return null
                    val a0 = ((ip32 ushr 24) and 0xFFL).toInt()
                    val a1 = ((ip32 ushr 16) and 0xFFL).toInt()
                    val a2 = ((ip32 ushr 8) and 0xFFL).toInt()
                    val a3 = (ip32 and 0xFFL).toInt()
                    DecodedRemoteTarget("$a0.$a1.$a2.$a3", port, pin)
                }
            }
        }

        private fun encodeBase32(
            packed: Long,
            length: Int,
        ): String =
            buildString(length) {
                for (i in 0 until length) {
                    val shift = (length - 1 - i) * 5
                    val idx = ((packed ushr shift) and 0x1FL).toInt()
                    append(PAIR_ALPHABET[idx])
                }
            }

        internal fun isPrivateIpv4(rawHost: String?): Boolean {
            val cleaned =
                rawHost
                    ?.trim()
                    ?.substringBefore('%')
                    ?.removePrefix("::ffff:")
                    ?.takeIf { it.isNotEmpty() }
                    ?: return false
            val parts = cleaned.split('.')
            if (parts.size != 4) return false
            val octets = parts.map { it.toIntOrNull() ?: return false }
            if (octets.any { it !in 0..255 }) return false
            val o0 = octets[0]
            val o1 = octets[1]
            return (o0 == 127 && octets[1] == 0 && octets[2] == 0 && octets[3] == 1) ||
                o0 == 10 ||
                (o0 == 172 && o1 in 16..31) ||
                (o0 == 192 && o1 == 168)
        }

        fun detectLanIpv4Address(): String? =
            runCatching {
                val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                val candidates =
                    interfaces
                        .filter { !it.isLoopback && it.isUp }
                        .flatMap { it.inetAddresses.toList() }
                        .filterIsInstance<Inet4Address>()
                        .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress && isPrivateIpv4(it.hostAddress) }
                candidates.firstOrNull { it.isSiteLocalAddress }?.hostAddress
            }.getOrNull()

        internal fun escapeJsonString(raw: String): String =
            buildString {
                append('"')
                for (ch in raw) {
                    when (ch) {
                        '"' -> append("\\\"")
                        '\\' -> append("\\\\")
                        '<' -> append("\\u003C")
                        '>' -> append("\\u003E")
                        '\n' -> append("\\n")
                        '\r' -> append("\\r")
                        '\t' -> append("\\t")
                        else -> append(ch)
                    }
                }
                append('"')
            }

        internal fun buildRemoteWebAppHtml(
            token: String,
            language: AppLanguage = AppLanguage.INDONESIAN,
        ): String {
            fun t(
                id: String,
                ja: String,
                en: String,
            ): String =
                when (language) {
                    AppLanguage.INDONESIAN -> id
                    AppLanguage.JAPANESE -> ja
                    else -> en
                }

            val langCode = t("id", "ja", "en")
            val labelsJson =
                buildString {
                    append('{')
                    append("\"navNow\":").append(escapeJsonString(t("Putar", "再生中", "Now"))).append(',')
                    append("\"navSearch\":").append(escapeJsonString(t("Cari", "検索", "Search"))).append(',')
                    append("\"navQueue\":").append(escapeJsonString(t("Antrean", "キュー", "Queue"))).append(',')
                    append("\"navDpad\":").append(escapeJsonString(t("Remote", "操作", "Remote"))).append(',')
                    append("\"subPlayer\":").append(escapeJsonString(t("Putar", "プレーヤー", "Player"))).append(',')
                    append("\"subLyrics\":").append(escapeJsonString(t("Lirik", "歌詞", "Lyrics"))).append(',')
                    append("\"nothingPlaying\":").append(escapeJsonString(t("Belum ada lagu diputar di TV", "テレビで再生中の曲はありません", "Nothing playing on TV yet"))).append(',')
                    append("\"pickHint\":").append(escapeJsonString(t("Pilih lagu dari tab Cari & Genre di bawah.", "下の「検索」タブから曲を選んで再生できます。", "Pick a song from Search & Genres below."))).append(',')
                    append("\"likeBtn\":").append(escapeJsonString(t("Suka", "いいね", "Like"))).append(',')
                    append("\"likedBtn\":").append(escapeJsonString(t("Disukai", "いいね済", "Liked"))).append(',')
                    append("\"playlistBtn\":").append(escapeJsonString(t("Playlist", "リスト", "Playlist"))).append(',')
                    append("\"radioBtn\":").append(escapeJsonString(t("Radio", "ラジオ", "Radio"))).append(',')
                    append("\"autoBtn\":").append(escapeJsonString(t("Auto", "自動再生", "Auto"))).append(',')
                    append("\"searchPh\":").append(escapeJsonString(t("Cari lagu, artis, atau playlist…", "曲・アーティスト・プレイリストを検索…", "Search songs, artists, or playlists…"))).append(',')
                    append("\"searchBtn\":").append(escapeJsonString(t("Cari", "検索", "Search"))).append(',')
                    append("\"genresTitle\":").append(escapeJsonString(t("Genre & Suasana", "ジャンル＆ムード", "Genres & Moods"))).append(',')
                    append("\"allGenres\":").append(escapeJsonString(t("Semua Genre", "ジャンル一覧", "All Genres"))).append(',')
                    append("\"volLabel\":").append(escapeJsonString(t("Volume TV", "テレビ音量", "TV Volume"))).append(',')
                    append("\"qualLabel\":").append(escapeJsonString(t("Kualitas Audio", "音質", "Audio Quality"))).append(',')
                    append("\"romajiBtn\":").append(escapeJsonString(t("Romaji", "ローマ字", "Romaji"))).append(',')
                    append("\"sourcePrefix\":").append(escapeJsonString(t("Sumber", "取得元", "Source"))).append(',')
                    append("\"lyricsLoading\":").append(escapeJsonString(t("Memuat lirik dari TV…", "テレビから歌詞を読み込み中…", "Loading lyrics from TV…"))).append(',')
                    append("\"lyricsEmpty\":").append(escapeJsonString(t("Lirik tidak tersedia untuk lagu ini.", "この曲の歌詞はありません。", "Lyrics unavailable for this track."))).append(',')
                    append("\"lyricsCapNotice\":").append(escapeJsonString(t("Dibatasi maks 80 baris untuk menjaga performa TV 1 GB RAM.", "1 GB RAM テレビの性能維持のため最大 80 行に制限しています。", "Capped at 80 lines to protect 1 GB RAM TV performance."))).append(',')
                    append("\"lyricsPlainHint\":").append(escapeJsonString(t("Lirik statis (tanpa timestamp sinkron).", "タイムスタンプなしの歌詞です。", "Static lyrics (no synced timestamps)."))).append(',')
                    append("\"clearQueue\":").append(escapeJsonString(t("Kosongkan", "クリア", "Clear"))).append(',')
                    append("\"dpadTitle\":").append(escapeJsonString(t("Navigasi D-Pad TV", "テレビ D-pad 操作", "TV D-Pad Navigation"))).append(',')
                    append("\"backBtn\":").append(escapeJsonString(t("Kembali", "戻る", "Back"))).append(',')
                    append("\"quickJump\":").append(escapeJsonString(t("Lompat ke Layar TV", "テレビ画面へ移動", "Jump to TV Screen"))).append(',')
                    append("\"homeLbl\":").append(escapeJsonString(t("Beranda", "ホーム", "Home"))).append(',')
                    append("\"libraryLbl\":").append(escapeJsonString(t("Koleksi", "ライブラリ", "Library"))).append(',')
                    append("\"settingsLbl\":").append(escapeJsonString(t("Pengaturan", "設定", "Settings"))).append(',')
                    append("\"nowPlayingLbl\":").append(escapeJsonString(t("Now Playing", "再生画面", "Now Playing"))).append(',')
                    append("\"playlistModalTitle\":").append(escapeJsonString(t("Tambah ke Playlist YouTube", "プレイリストに追加", "Add to YouTube Playlist"))).append(',')
                    append("\"newPlPh\":").append(escapeJsonString(t("Nama playlist baru…", "新しいプレイリスト名…", "New playlist name…"))).append(',')
                    append("\"createPl\":").append(escapeJsonString(t("Buat & Tambah", "作成して追加", "Create & Add"))).append(',')
                    append("\"signInHint\":").append(escapeJsonString(t("Masuk akun YouTube Music di Pengaturan TV untuk menyinkronkan playlist.", "YouTube プレイリストと同期するにはテレビの設定でログインしてください。", "Sign in with YouTube Music in TV Settings to sync playlists.")))
                    append('}')
                }

            return REMOTE_WEB_APP_TEMPLATE
                .replace("__HTML_LANG__", langCode)
                .replace("__TOKEN_JSON__", escapeJsonString(token))
                .replace("__LABELS_JSON__", labelsJson)
                .replace("__DEMO_MODE__", "false")
        }

        private val REMOTE_WEB_APP_TEMPLATE =
            """<!DOCTYPE html>
<html lang="__HTML_LANG__">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no,viewport-fit=cover" />
  <title>Alunara Remote</title>
  <style>
    :root {
      color-scheme: dark;
      --bg: #0E1511;
      --surface: #15201A;
      --surface-elev: #1D2C23;
      --surface-active: #25392D;
      --border: #273A2F;
      --border-strong: #3B5947;
      --accent: #85BDA0;
      --accent-strong: #C8F0DA;
      --accent-ink: #09120D;
      --text: #E6F0E9;
      --muted: #8FA899;
      --dim: #687F72;
      --danger: #F4B8BC;
    }
    *, *::before, *::after { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
    html, body {
      margin: 0;
      padding: 0;
      min-height: 100dvh;
      font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Text', 'Segoe UI', Roboto, sans-serif;
      font-size: 14px;
      line-height: 1.4;
      background: var(--bg);
      color: var(--text);
      -webkit-font-smoothing: antialiased;
    }
    body { padding-bottom: calc(68px + env(safe-area-inset-bottom, 0px)); }
    header {
      position: sticky;
      top: 0;
      z-index: 20;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 10px;
      padding: calc(12px + env(safe-area-inset-top, 0px)) 16px 12px;
      background: var(--bg);
      border-bottom: 1px solid var(--border);
    }
    .brandRow { display: flex; align-items: center; gap: 8px; min-width: 0; }
    .dot { width: 8px; height: 8px; border-radius: 50%; background: var(--accent); flex-shrink: 0; }
    .dot.off { background: var(--danger); }
    .brandTitle { font-size: 14px; font-weight: 700; letter-spacing: -0.01em; color: var(--text); }
    .codecPill {
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 0.04em;
      color: var(--accent);
      background: var(--surface);
      border: 1px solid var(--border);
      padding: 4px 9px;
      border-radius: 999px;
      max-width: 170px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      font-variant-numeric: tabular-nums;
    }
    main { max-width: 480px; margin: 0 auto; padding: 14px 16px 16px; }
    .panel { display: none; }
    .panel.active { display: block; }
    .subSwitch {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 4px;
      padding: 4px;
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: 12px;
      margin-bottom: 12px;
    }
    .subSwitchBtn {
      height: 38px;
      border: 0;
      border-radius: 8px;
      background: transparent;
      color: var(--muted);
      font-size: 13px;
      font-weight: 600;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 6px;
    }
    .subSwitchBtn.active { background: var(--surface-active); color: var(--accent-strong); }
    .card {
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: 16px;
      padding: 14px;
      margin-bottom: 12px;
    }
    .secRow { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin: 0 0 10px; }
    .secLabel {
      font-size: 11px;
      font-weight: 700;
      color: var(--muted);
      text-transform: uppercase;
      letter-spacing: 0.08em;
      margin: 0;
    }
    .secMeta { font-size: 11.5px; font-weight: 600; color: var(--accent); font-variant-numeric: tabular-nums; }
    .npHero { display: flex; gap: 14px; align-items: center; margin-bottom: 14px; }
    .artWrap {
      width: 82px;
      height: 82px;
      border-radius: 14px;
      background: var(--surface-elev);
      border: 1px solid var(--border);
      overflow: hidden;
      flex-shrink: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      color: var(--accent);
    }
    .artWrap img { width: 100%; height: 100%; object-fit: cover; display: block; }
    .npMeta { flex: 1; min-width: 0; }
    .npStatus {
      font-size: 10.5px;
      font-weight: 700;
      letter-spacing: 0.07em;
      text-transform: uppercase;
      color: var(--accent);
      margin-bottom: 3px;
    }
    .npTitle {
      font-size: 17px;
      font-weight: 700;
      letter-spacing: -0.02em;
      color: var(--text);
      margin: 0 0 3px;
      line-height: 1.25;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }
    .npArtist {
      font-size: 13px;
      color: var(--muted);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .seekWrap { margin-bottom: 12px; }
    input[type="range"] {
      -webkit-appearance: none;
      appearance: none;
      width: 100%;
      height: 28px;
      background: transparent;
      margin: 0;
      cursor: pointer;
    }
    input[type="range"]::-webkit-slider-runnable-track {
      height: 5px;
      border-radius: 999px;
      background: var(--surface-active);
    }
    input[type="range"]::-webkit-slider-thumb {
      -webkit-appearance: none;
      appearance: none;
      width: 18px;
      height: 18px;
      border-radius: 50%;
      background: var(--accent-strong);
      border: 2px solid var(--bg);
      margin-top: -6.5px;
    }
    input[type="range"]::-moz-range-track {
      height: 5px;
      border-radius: 999px;
      background: var(--surface-active);
    }
    input[type="range"]::-moz-range-thumb {
      width: 16px;
      height: 16px;
      border-radius: 50%;
      background: var(--accent-strong);
      border: 2px solid var(--bg);
    }
    .timeRow {
      display: flex;
      justify-content: space-between;
      font-size: 11.5px;
      font-weight: 600;
      color: var(--muted);
      margin-top: -2px;
      font-variant-numeric: tabular-nums;
    }
    .transport {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 20px;
      margin: 12px 0 16px;
    }
    .btnCircle {
      width: 52px;
      height: 52px;
      border-radius: 50%;
      border: 1px solid var(--border-strong);
      background: var(--surface-elev);
      color: var(--text);
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      justify-content: center;
    }
    .btnCircle:active { background: var(--surface-active); }
    .btnPlay {
      width: 66px;
      height: 66px;
      border: 0;
      background: var(--accent-strong);
      color: var(--accent-ink);
    }
    .btnPlay:active { background: var(--accent); }
    .actionRow { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; }
    .actionCell {
      min-height: 52px;
      padding: 8px 4px;
      border-radius: 12px;
      border: 1px solid var(--border);
      background: var(--surface-elev);
      color: var(--muted);
      font-size: 11px;
      font-weight: 600;
      cursor: pointer;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 4px;
    }
    .actionCell:active { background: var(--surface-active); }
    .actionCell.active { background: #1F3B2B; border-color: var(--accent); color: var(--accent-strong); }
    .volRow { display: flex; align-items: center; gap: 10px; }
    .stepBtn {
      width: 40px;
      height: 40px;
      border-radius: 10px;
      border: 1px solid var(--border-strong);
      background: var(--surface-elev);
      color: var(--text);
      display: inline-flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      flex-shrink: 0;
    }
    .stepBtn:active { background: var(--surface-active); }
    .seg4 { display: grid; grid-template-columns: repeat(4, 1fr); gap: 6px; }
    .chipBtn {
      height: 40px;
      padding: 0 8px;
      border-radius: 10px;
      border: 1px solid var(--border);
      background: var(--surface-elev);
      color: var(--muted);
      font-size: 12.5px;
      font-weight: 600;
      cursor: pointer;
      text-align: center;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      font-variant-numeric: tabular-nums;
    }
    .chipBtn:active { background: var(--surface-active); }
    .chipBtn.active { background: #1F3B2B; border-color: var(--accent); color: var(--accent-strong); }
    .lyricsTools { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 10px; }
    .toolChip {
      height: 36px;
      padding: 0 11px;
      border-radius: 10px;
      border: 1px solid var(--border);
      background: var(--surface);
      color: var(--text);
      font-size: 12px;
      font-weight: 600;
      cursor: pointer;
      font-variant-numeric: tabular-nums;
    }
    .toolChip.active { background: #1F3B2B; border-color: var(--accent); color: var(--accent-strong); }
    .toolChip:disabled { opacity: 0.45; }
    .lyricsTrackBar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 10px;
      padding: 10px 12px;
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: 12px;
      margin-bottom: 10px;
    }
    .lyricsTrackMeta { min-width: 0; flex: 1; }
    .lyricsTrackTitle { font-size: 13px; font-weight: 700; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .lyricsTrackSub { font-size: 11.5px; color: var(--muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .lyricsSheet {
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: 16px;
      padding: 10px;
      max-height: 54dvh;
      overflow-y: auto;
      scroll-behavior: auto;
    }
    .lyricLine {
      padding: 9px 10px 9px 12px;
      border-left: 3px solid transparent;
      border-radius: 8px;
      margin-bottom: 4px;
      color: var(--dim);
      font-size: 15px;
      font-weight: 500;
      line-height: 1.42;
      word-break: break-word;
    }
    .lyricLine.past { color: #7E9689; }
    .lyricLine.active {
      background: #1A2D22;
      border-left-color: var(--accent-strong);
      color: var(--text);
      font-size: 16.5px;
      font-weight: 700;
    }
    .lyricRom { display: block; margin-top: 2px; font-size: 12px; font-weight: 500; color: var(--muted); }
    .lyricLine.active .lyricRom { color: var(--accent); }
    .capNotice {
      margin-top: 8px;
      padding: 9px 12px;
      border-radius: 10px;
      background: var(--surface);
      border: 1px dashed var(--border-strong);
      font-size: 11.5px;
      color: var(--muted);
      text-align: center;
    }
    .searchBar { display: flex; gap: 8px; margin-bottom: 12px; }
    .searchInput {
      flex: 1;
      min-width: 0;
      height: 46px;
      padding: 0 12px;
      border-radius: 12px;
      border: 1.5px solid var(--border-strong);
      background: var(--surface);
      color: var(--text);
      font-size: 15px;
    }
    .searchInput:focus { outline: none; border-color: var(--accent-strong); }
    .btnPrimary {
      height: 46px;
      padding: 0 16px;
      border: 0;
      border-radius: 12px;
      background: var(--accent-strong);
      color: var(--accent-ink);
      font-weight: 700;
      font-size: 14px;
      cursor: pointer;
    }
    .btnGhost {
      min-height: 40px;
      padding: 0 12px;
      border-radius: 10px;
      border: 1px solid var(--border);
      background: var(--surface-elev);
      color: var(--text);
      font-size: 12.5px;
      font-weight: 600;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 6px;
    }
    .btnGhost:active { background: var(--surface-active); }
    .genreGrid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; margin-bottom: 14px; }
    .genreTile {
      min-height: 50px;
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 10px 12px;
      border-radius: 12px;
      border: 1px solid var(--border);
      background: var(--surface);
      color: var(--text);
      font-size: 13px;
      font-weight: 600;
      text-align: left;
      cursor: pointer;
    }
    .genreTile:active { background: var(--surface-elev); }
    .stripe { width: 4px; height: 22px; border-radius: 4px; flex-shrink: 0; }
    .listRow {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 9px 10px;
      border-radius: 12px;
      background: var(--surface);
      border: 1px solid var(--border);
      margin-bottom: 8px;
    }
    .listRow.playing { border-color: var(--accent); background: #1B3024; }
    .rowThumb {
      width: 44px;
      height: 44px;
      border-radius: 9px;
      background: var(--surface-elev);
      border: 1px solid var(--border);
      object-fit: cover;
      flex-shrink: 0;
    }
    .rowMeta { flex: 1; min-width: 0; cursor: pointer; }
    .rowTitle {
      font-size: 13.5px;
      font-weight: 600;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .listRow.playing .rowTitle { color: var(--accent-strong); }
    .rowSub {
      font-size: 12px;
      color: var(--muted);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .rowBtns { display: flex; gap: 6px; flex-shrink: 0; }
    .miniBtn {
      min-width: 36px;
      height: 36px;
      padding: 0 9px;
      border-radius: 9px;
      border: 1px solid var(--border);
      background: var(--surface-elev);
      color: var(--text);
      font-size: 12px;
      font-weight: 600;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      justify-content: center;
    }
    .miniBtn.hi { background: var(--accent-strong); color: var(--accent-ink); border-color: var(--accent-strong); }
    .miniBtn:active { background: var(--surface-active); }
    .chipsScroll { display: flex; gap: 6px; overflow-x: auto; padding-bottom: 10px; margin-bottom: 4px; }
    .dpadGrid {
      display: grid;
      grid-template-columns: repeat(3, 74px);
      grid-template-rows: repeat(3, 64px);
      gap: 10px;
      justify-content: center;
      margin: 8px auto 14px;
    }
    .dpadKey {
      border-radius: 16px;
      border: 1px solid var(--border-strong);
      background: var(--surface-elev);
      color: var(--text);
      font-size: 15px;
      font-weight: 700;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      justify-content: center;
    }
    .dpadKey:active { background: var(--surface-active); }
    .dpadOk {
      background: var(--accent-strong);
      color: var(--accent-ink);
      border: 0;
      border-radius: 50%;
      letter-spacing: 0.04em;
    }
    .destGrid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; }
    nav.bottomNav {
      position: fixed;
      left: 0;
      right: 0;
      bottom: 0;
      z-index: 30;
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      background: #111A15;
      border-top: 1px solid var(--border);
      padding-bottom: env(safe-area-inset-bottom, 0px);
    }
    .navBtn {
      position: relative;
      height: 58px;
      border: 0;
      background: transparent;
      color: var(--dim);
      padding: 0 4px;
      font-size: 11px;
      font-weight: 600;
      cursor: pointer;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 4px;
    }
    .navBtn.active { color: var(--accent-strong); }
    .navBtn.active::before {
      content: "";
      position: absolute;
      top: 0;
      left: 22%;
      right: 22%;
      height: 2.5px;
      background: var(--accent-strong);
      border-radius: 0 0 3px 3px;
    }
    .toast {
      position: fixed;
      top: calc(56px + env(safe-area-inset-top, 0px));
      left: 16px;
      right: 16px;
      max-width: 340px;
      margin: 0 auto;
      text-align: center;
      background: #173324;
      border: 1px solid var(--accent);
      color: var(--accent-strong);
      padding: 8px 14px;
      border-radius: 10px;
      font-size: 12.5px;
      font-weight: 600;
      z-index: 50;
      display: none;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .modalBack {
      display: none;
      position: fixed;
      inset: 0;
      z-index: 60;
      background: rgba(0, 0, 0, 0.76);
      align-items: flex-end;
      justify-content: center;
      padding: 14px;
    }
    .modalBack.show { display: flex; }
    .modalSheet {
      width: 100%;
      max-width: 480px;
      max-height: 78dvh;
      overflow-y: auto;
      background: var(--surface);
      border: 1.5px solid var(--accent);
      border-radius: 18px;
      padding: 16px;
    }
    svg.ic {
      width: 20px;
      height: 20px;
      fill: none;
      stroke: currentColor;
      stroke-width: 1.85;
      stroke-linecap: round;
      stroke-linejoin: round;
      flex-shrink: 0;
    }
    svg.ic.sm { width: 16px; height: 16px; }
    svg.ic.lg { width: 26px; height: 26px; }
  </style>
</head>
<body>
  <header>
    <div class="brandRow">
      <span id="connDot" class="dot"></span>
      <span class="brandTitle">Alunara Remote</span>
    </div>
    <span id="codecBadge" class="codecPill">AUTO</span>
  </header>

  <div id="toast" class="toast" role="status" aria-live="polite"></div>

  <main>
    <!-- TAB 1: NOW PLAYING (Opsi A: Player | Lyrics sub-switch) -->
    <section id="tab-now" class="panel active">
      <div class="subSwitch" role="tablist">
        <button id="subPlayerBtn" class="subSwitchBtn active" type="button">
          <svg class="ic sm" viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><polygon points="10 8 16 12 10 16 10 8" fill="currentColor" stroke="none"/></svg>
          <span id="subPlayerTxt">Putar</span>
        </button>
        <button id="subLyricsBtn" class="subSwitchBtn" type="button">
          <svg class="ic sm" viewBox="0 0 24 24"><path d="M4 6h16M4 12h11M4 18h7"/><circle cx="18" cy="16" r="2"/><path d="M20 16V10l2-1"/></svg>
          <span id="subLyricsTxt">Lirik</span>
        </button>
      </div>

      <div id="nowPlayerSub">
        <div class="card">
          <div class="npHero">
            <div id="npArt" class="artWrap">
              <svg class="ic lg" viewBox="0 0 24 24"><path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/></svg>
            </div>
            <div class="npMeta">
              <div id="npStatus" class="npStatus">IDLE</div>
              <div id="npTitle" class="npTitle">-</div>
              <div id="npArtist" class="npArtist"></div>
            </div>
          </div>
          <div class="seekWrap">
            <input id="seekRange" type="range" min="0" max="100" value="0" />
            <div class="timeRow">
              <span id="posTxt">0:00</span>
              <span id="durTxt">0:00</span>
            </div>
          </div>
          <div class="transport">
            <button class="btnCircle" type="button" data-cmd="prev" aria-label="Previous">
              <svg class="ic" viewBox="0 0 24 24"><polygon points="19 20 9 12 19 4 19 20" fill="currentColor" stroke="none"/><line x1="5" y1="5" x2="5" y2="19"/></svg>
            </button>
            <button id="playPauseBtn" class="btnCircle btnPlay" type="button" data-cmd="play_pause" aria-label="Play or Pause">
              <svg id="playPauseSvg" class="ic lg" viewBox="0 0 24 24"><polygon points="7 4 20 12 7 20 7 4" fill="currentColor" stroke="none"/></svg>
            </button>
            <button class="btnCircle" type="button" data-cmd="next" aria-label="Next">
              <svg class="ic" viewBox="0 0 24 24"><polygon points="5 4 15 12 5 20 5 4" fill="currentColor" stroke="none"/><line x1="19" y1="5" x2="19" y2="19"/></svg>
            </button>
          </div>
          <div class="actionRow">
            <button id="likeBtn" class="actionCell" type="button" data-cmd="toggle_like">
              <svg class="ic sm" viewBox="0 0 24 24"><path d="M12 20.5s-7.5-4.6-9.5-9.1C1 7.9 3.2 4.5 6.8 4.5c2 0 3.6 1 5.2 2.7 1.6-1.7 3.2-2.7 5.2-2.7 3.6 0 5.8 3.4 4.3 6.9-2 4.5-9.5 9.1-9.5 9.1z"/></svg>
              <span id="likeBtnTxt">Suka</span>
            </button>
            <button id="openPlBtn" class="actionCell" type="button">
              <svg class="ic sm" viewBox="0 0 24 24"><path d="M3 6h12M3 12h12M3 18h8M18 14v8M14 18h8"/></svg>
              <span id="plBtnTxt">Playlist</span>
            </button>
            <button class="actionCell" type="button" data-cmd="start_radio">
              <svg class="ic sm" viewBox="0 0 24 24"><circle cx="12" cy="12" r="2" fill="currentColor" stroke="none"/><path d="M8.5 15.5a5 5 0 0 1 0-7M15.5 8.5a5 5 0 0 1 0 7M5.5 18.5a9.5 9.5 0 0 1 0-13M18.5 5.5a9.5 9.5 0 0 1 0 13"/></svg>
              <span id="radioBtnTxt">Radio</span>
            </button>
            <button id="autoBtn" class="actionCell" type="button" data-cmd="toggle_autoplay">
              <svg class="ic sm" viewBox="0 0 24 24"><path d="M17 2l4 4-4 4"/><path d="M3 11V9a4 4 0 0 1 4-4h14"/><path d="M7 22l-4-4 4-4"/><path d="M21 13v2a4 4 0 0 1-4 4H3"/></svg>
              <span id="autoBtnTxt">Auto</span>
            </button>
          </div>
        </div>

        <div class="card">
          <div class="secRow">
            <p id="volLabelEl" class="secLabel">Volume TV</p>
            <span id="volVal" class="secMeta">8/15</span>
          </div>
          <div class="volRow">
            <button id="volDownBtn" class="stepBtn" type="button" aria-label="Volume Down">
              <svg class="ic sm" viewBox="0 0 24 24"><line x1="5" y1="12" x2="19" y2="12"/></svg>
            </button>
            <input id="volRange" type="range" min="0" max="15" value="8" />
            <button id="volUpBtn" class="stepBtn" type="button" aria-label="Volume Up">
              <svg class="ic sm" viewBox="0 0 24 24"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
            </button>
          </div>
        </div>

        <div class="card" style="margin-bottom:0;">
          <div class="secRow">
            <p id="qualLabelEl" class="secLabel">Kualitas Audio</p>
            <span class="secMeta">Audio-only</span>
          </div>
          <div class="seg4" id="qualRow">
            <button class="chipBtn" type="button" data-quality="AUTO">Auto</button>
            <button class="chipBtn" type="button" data-quality="LOW">Low</button>
            <button class="chipBtn" type="button" data-quality="MEDIUM">Med</button>
            <button class="chipBtn" type="button" data-quality="HIGH">High</button>
          </div>
        </div>
      </div>

      <!-- Capped Lyrics Sub-View (lazy fetched 1x/track via /api/lyrics, max 80 lines, 0 animations) -->
      <div id="nowLyricsSub" style="display:none;">
        <div class="lyricsTrackBar">
          <div class="lyricsTrackMeta">
            <div id="lyrTrackTitle" class="lyricsTrackTitle">-</div>
            <div id="lyrTrackSub" class="lyricsTrackSub"></div>
          </div>
          <button id="lyrPlayBtn" class="miniBtn hi" type="button" data-cmd="play_pause" aria-label="Play or Pause">
            <svg id="lyrPlaySvg" class="ic sm" viewBox="0 0 24 24"><polygon points="7 4 20 12 7 20 7 4" fill="currentColor" stroke="none"/></svg>
          </button>
        </div>
        <div class="lyricsTools">
          <button id="romajiBtn" class="toolChip" type="button" data-cmd="toggle_romaji">Romaji</button>
          <button class="toolChip" type="button" data-lyrics-delta="-100">-0.1s</button>
          <button id="offsetResetBtn" class="toolChip" type="button" data-cmd="lyrics_reset">0ms</button>
          <button class="toolChip" type="button" data-lyrics-delta="100">+0.1s</button>
          <button id="providerCycleBtn" class="toolChip" type="button" data-cmd="cycle_lyrics_provider">Source</button>
        </div>
        <div id="lyricsSheet" class="lyricsSheet"></div>
        <div id="lyricsCapNotice" class="capNotice" style="display:none;"></div>
      </div>
    </section>

    <!-- TAB 2: SEARCH & GENRES -->
    <section id="tab-search" class="panel">
      <form id="searchForm" class="searchBar">
        <input id="searchInput" class="searchInput" type="search" autocomplete="off" />
        <button id="clearSearchBtn" type="button" class="btnGhost" style="display:none;" aria-label="Clear">
          <svg class="ic sm" viewBox="0 0 24 24"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
        </button>
        <button id="searchSubmitBtn" type="submit" class="btnPrimary">Cari</button>
      </form>
      <div id="genreHeaderBar" style="display:none; align-items:center; gap:10px; margin-bottom:10px;">
        <button id="backGenresBtn" type="button" class="btnGhost"></button>
        <strong id="activeGenreName" style="color:var(--accent-strong); font-size:14px;"></strong>
      </div>
      <div id="searchBody"></div>
    </section>

    <!-- TAB 3: QUEUE -->
    <section id="tab-queue" class="panel">
      <div id="chipsRow" class="chipsScroll"></div>
      <div class="secRow" style="margin-bottom:10px;">
        <span id="queueCountTxt" class="secLabel">Queue (0)</span>
        <div style="display:flex; gap:6px;">
          <button class="btnGhost" style="min-height:34px; font-size:12px;" type="button" data-cmd="refresh_radio">Radio</button>
          <button id="clearQueueBtn" class="btnGhost" style="min-height:34px; font-size:12px;" type="button" data-cmd="clear_queue">Clear</button>
        </div>
      </div>
      <div id="queueList"></div>
    </section>

    <!-- TAB 4: D-PAD REMOTE -->
    <section id="tab-dpad" class="panel">
      <div class="card">
        <div class="secRow">
          <p id="dpadTitleEl" class="secLabel">TV D-Pad</p>
          <span class="secMeta">LAN</span>
        </div>
        <div class="dpadGrid">
          <div></div>
          <button class="dpadKey" type="button" data-dpad="UP" aria-label="Up">
            <svg class="ic" viewBox="0 0 24 24"><polyline points="18 15 12 9 6 15"/></svg>
          </button>
          <div></div>
          <button class="dpadKey" type="button" data-dpad="LEFT" aria-label="Left">
            <svg class="ic" viewBox="0 0 24 24"><polyline points="15 18 9 12 15 6"/></svg>
          </button>
          <button class="dpadKey dpadOk" type="button" data-dpad="OK">OK</button>
          <button class="dpadKey" type="button" data-dpad="RIGHT" aria-label="Right">
            <svg class="ic" viewBox="0 0 24 24"><polyline points="9 18 15 12 9 6"/></svg>
          </button>
          <div></div>
          <button class="dpadKey" type="button" data-dpad="DOWN" aria-label="Down">
            <svg class="ic" viewBox="0 0 24 24"><polyline points="6 9 12 15 18 9"/></svg>
          </button>
          <div></div>
        </div>
        <div class="destGrid">
          <button class="btnGhost" style="min-height:44px;" type="button" data-dpad="BACK">
            <svg class="ic sm" viewBox="0 0 24 24"><polyline points="15 18 9 12 15 6"/><line x1="9" y1="12" x2="20" y2="12"/></svg>
            <span id="dpadBackTxt">Kembali</span>
          </button>
          <button class="btnGhost" style="min-height:44px;" type="button" data-nav="NOW_PLAYING">
            <svg class="ic sm" viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><polygon points="10 8 16 12 10 16 10 8" fill="currentColor" stroke="none"/></svg>
            <span id="navNowPlayingTxt">Now Playing</span>
          </button>
        </div>
      </div>
      <div class="card" style="margin-bottom:0;">
        <div class="secRow">
          <p id="quickJumpEl" class="secLabel">Jump to TV Screen</p>
        </div>
        <div class="destGrid">
          <button class="btnGhost" style="min-height:44px;" type="button" data-nav="HOME">
            <svg class="ic sm" viewBox="0 0 24 24"><path d="M3 10.5L12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"/></svg>
            <span id="jumpHomeTxt">Home</span>
          </button>
          <button class="btnGhost" style="min-height:44px;" type="button" data-nav="SEARCH">
            <svg class="ic sm" viewBox="0 0 24 24"><circle cx="11" cy="11" r="7"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
            <span id="jumpSearchTxt">Search</span>
          </button>
          <button class="btnGhost" style="min-height:44px;" type="button" data-nav="LIBRARY">
            <svg class="ic sm" viewBox="0 0 24 24"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
            <span id="jumpLibraryTxt">Library</span>
          </button>
          <button class="btnGhost" style="min-height:44px;" type="button" data-nav="SETTINGS">
            <svg class="ic sm" viewBox="0 0 24 24"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>
            <span id="jumpSettingsTxt">Settings</span>
          </button>
        </div>
      </div>
    </section>
  </main>

  <nav class="bottomNav">
    <button class="navBtn active" type="button" data-tab="tab-now">
      <svg class="ic" viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><polygon points="10 8 16 12 10 16 10 8" fill="currentColor" stroke="none"/></svg>
      <span id="navNowTxt">Putar</span>
    </button>
    <button class="navBtn" type="button" data-tab="tab-search">
      <svg class="ic" viewBox="0 0 24 24"><circle cx="11" cy="11" r="7"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
      <span id="navSearchTxt">Cari</span>
    </button>
    <button class="navBtn" type="button" data-tab="tab-queue">
      <svg class="ic" viewBox="0 0 24 24"><line x1="4" y1="6" x2="20" y2="6"/><line x1="4" y1="12" x2="16" y2="12"/><line x1="4" y1="18" x2="12" y2="18"/><polygon points="17 14 21 17 17 20 17 14" fill="currentColor" stroke="none"/></svg>
      <span id="navQueueTxt">Antrean</span>
    </button>
    <button class="navBtn" type="button" data-tab="tab-dpad">
      <svg class="ic" viewBox="0 0 24 24"><rect x="4" y="4" width="16" height="16" rx="4"/><line x1="12" y1="8" x2="12" y2="16"/><line x1="8" y1="12" x2="16" y2="12"/></svg>
      <span id="navDpadTxt">Remote</span>
    </button>
  </nav>

  <div id="playlistModal" class="modalBack">
    <div class="modalSheet">
      <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:12px;">
        <strong id="plModalTitle" style="color:var(--accent-strong);">Add to Playlist</strong>
        <button id="closePlBtn" class="miniBtn" type="button" aria-label="Close">
          <svg class="ic sm" viewBox="0 0 24 24"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
        </button>
      </div>
      <div id="plModalBody"></div>
    </div>
  </div>

  <script>
    (function () {
      var TOKEN = __TOKEN_JSON__;
      var L = __LABELS_JSON__;
      var DEMO_MODE = __DEMO_MODE__;
      var lastState = null;
      var genresCache = null;
      var seekDragging = false;
      var selectedGenre = null;
      var pollTimer = null;
      var pendingPlaylistTrack = null;
      var lyricsViewOpen = false;
      var lyricsPayload = null;
      var lastArtUrl = null;
      var lastQueueSig = '';
      var lastChipsSig = '';
      var lastLyricsKey = '';
      var lastActiveLyricIdx = -2;
      var lastLyricsFetchMs = 0;

      var SVG_PLAY = '<polygon points="7 4 20 12 7 20 7 4" fill="currentColor" stroke="none"/>';
      var SVG_PAUSE = '<rect x="6" y="4" width="4" height="16" rx="1" fill="currentColor" stroke="none"/><rect x="14" y="4" width="4" height="16" rx="1" fill="currentColor" stroke="none"/>';
      var SVG_NOTE = '<svg class="ic lg" viewBox="0 0 24 24"><path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/></svg>';
      var SVG_PLAY_SM = '<svg class="ic sm" viewBox="0 0 24 24"><polygon points="7 4 20 12 7 20 7 4" fill="currentColor" stroke="none"/></svg>';
      var SVG_PLUS_SM = '<svg class="ic sm" viewBox="0 0 24 24"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>';
      var SVG_PL_SM = '<svg class="ic sm" viewBox="0 0 24 24"><path d="M3 6h12M3 12h12M3 18h8M18 14v8M14 18h8"/></svg>';
      var SVG_UP_SM = '<svg class="ic sm" viewBox="0 0 24 24"><polyline points="18 15 12 9 6 15"/></svg>';
      var SVG_DN_SM = '<svg class="ic sm" viewBox="0 0 24 24"><polyline points="6 9 12 15 18 9"/></svg>';
      var SVG_X_SM = '<svg class="ic sm" viewBox="0 0 24 24"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>';

      document.getElementById('navNowTxt').textContent = L.navNow;
      document.getElementById('navSearchTxt').textContent = L.navSearch;
      document.getElementById('navQueueTxt').textContent = L.navQueue;
      document.getElementById('navDpadTxt').textContent = L.navDpad;
      document.getElementById('subPlayerTxt').textContent = L.subPlayer;
      document.getElementById('subLyricsTxt').textContent = L.subLyrics;
      document.getElementById('plBtnTxt').textContent = L.playlistBtn;
      document.getElementById('radioBtnTxt').textContent = L.radioBtn;
      document.getElementById('autoBtnTxt').textContent = L.autoBtn;
      document.getElementById('searchInput').placeholder = L.searchPh;
      document.getElementById('searchSubmitBtn').textContent = L.searchBtn;
      document.getElementById('backGenresBtn').textContent = L.allGenres;
      document.getElementById('volLabelEl').textContent = L.volLabel;
      document.getElementById('qualLabelEl').textContent = L.qualLabel;
      document.getElementById('romajiBtn').textContent = L.romajiBtn;
      document.getElementById('clearQueueBtn').textContent = L.clearQueue;
      document.getElementById('dpadTitleEl').textContent = L.dpadTitle;
      document.getElementById('dpadBackTxt').textContent = L.backBtn;
      document.getElementById('navNowPlayingTxt').textContent = L.nowPlayingLbl;
      document.getElementById('quickJumpEl').textContent = L.quickJump;
      document.getElementById('jumpHomeTxt').textContent = L.homeLbl;
      document.getElementById('jumpSearchTxt').textContent = L.navSearch;
      document.getElementById('jumpLibraryTxt').textContent = L.libraryLbl;
      document.getElementById('jumpSettingsTxt').textContent = L.settingsLbl;
      document.getElementById('plModalTitle').textContent = L.playlistModalTitle;

      function fmtMs(ms) {
        if (!ms || ms <= 0) return '0:00';
        var s = Math.floor(ms / 1000);
        var m = Math.floor(s / 60);
        var r = s % 60;
        return m + ':' + (r < 10 ? '0' : '') + r;
      }

      function showToast(msg) {
        var el = document.getElementById('toast');
        el.textContent = msg;
        el.style.display = 'block';
        clearTimeout(el._t);
        el._t = setTimeout(function () { el.style.display = 'none'; }, 1800);
      }

      function esc(s) {
        return String(s || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
      }

      function apiGet(path) {
        if (DEMO_MODE && window.__DEMO_HANDLER__) return window.__DEMO_HANDLER__('GET', path, null);
        var sep = path.indexOf('?') >= 0 ? '&' : '?';
        return fetch(path + sep + 't=' + encodeURIComponent(TOKEN), { cache: 'no-store' }).then(function (r) { return r.json(); });
      }

      function apiCmd(params) {
        if (DEMO_MODE && window.__DEMO_HANDLER__) {
          return window.__DEMO_HANDLER__('POST', '/api/cmd', params).then(function (d) {
            if (d && d.state) renderState(d.state);
            return d;
          });
        }
        var body = Object.keys(params).map(function (k) {
          return encodeURIComponent(k) + '=' + encodeURIComponent(params[k] == null ? '' : params[k]);
        }).join('&');
        return fetch('/api/cmd?t=' + encodeURIComponent(TOKEN), {
          method: 'POST',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'X-Alunara-Remote': '1' },
          body: body
        }).then(function (r) { return r.json(); }).then(function (d) {
          if (d && d.state) renderState(d.state);
          return d;
        }).catch(function () {
          document.getElementById('connDot').className = 'dot off';
        });
      }

      function highlightActiveLyricLine(idx) {
        if (!lyricsPayload || lyricsPayload.mode !== 'synced') return;
        if (idx === lastActiveLyricIdx) return;
        lastActiveLyricIdx = idx;
        var nodes = document.querySelectorAll('#lyricsSheet .lyricLine');
        for (var i = 0; i < nodes.length; i++) {
          var el = nodes[i];
          var isAct = i === idx;
          var isPast = idx >= 0 && i < idx;
          el.classList.toggle('active', isAct);
          el.classList.toggle('past', isPast);
          if (isAct && lyricsViewOpen) {
            try { el.scrollIntoView({ behavior: 'auto', block: 'center' }); } catch (_) {}
          }
        }
      }

      function renderLyricsPayload(data) {
        lyricsPayload = data;
        var sheet = document.getElementById('lyricsSheet');
        var notice = document.getElementById('lyricsCapNotice');
        sheet.innerHTML = '';
        lastActiveLyricIdx = -2;
        if (!data || data.mode === 'loading') {
          sheet.innerHTML = '<div style="padding:18px 8px; color:var(--muted); text-align:center;">' + esc(L.lyricsLoading) + '</div>';
          notice.style.display = 'none';
          return;
        }
        if (data.mode === 'unavailable' || !data.lines || !data.lines.length) {
          sheet.innerHTML = '<div style="padding:18px 8px; color:var(--muted); text-align:center;">' + esc(L.lyricsEmpty) + '</div>';
          notice.style.display = 'none';
          return;
        }
        data.lines.forEach(function (ln) {
          var row = document.createElement('div');
          row.className = 'lyricLine';
          row.textContent = ln.t || ' ';
          if (ln.r) {
            var rom = document.createElement('span');
            rom.className = 'lyricRom';
            rom.textContent = ln.r;
            row.appendChild(rom);
          }
          sheet.appendChild(row);
        });
        if (data.mode === 'synced') {
          highlightActiveLyricLine(typeof data.activeIndex === 'number' ? data.activeIndex : -1);
        }
        if (data.truncated) {
          notice.textContent = L.lyricsCapNotice;
          notice.style.display = 'block';
        } else if (data.mode === 'plain') {
          notice.textContent = L.lyricsPlainHint;
          notice.style.display = 'block';
        } else {
          notice.style.display = 'none';
        }
      }

      function fetchLyricsIfNeeded(force) {
        if (!lyricsViewOpen) return;
        var s = lastState;
        var trId = (s && s.track) ? s.track.id : '';
        var lyr = (s && s.lyrics) ? s.lyrics : {};
        var key = trId + '|' + (lyr.provider || '') + '|' + (lyr.showRomaji ? '1' : '0') + '|' + (lyr.hasLyrics ? '1' : '0') + '|' + (lyr.loading ? 'L' : 'R');
        var now = Date.now();
        var shouldRetryLoading = lyricsPayload && lyricsPayload.mode === 'loading' && (now - lastLyricsFetchMs > 2000);
        if (!force && key === lastLyricsKey && !shouldRetryLoading) return;
        lastLyricsKey = key;
        lastLyricsFetchMs = now;
        apiGet('/api/lyrics').then(renderLyricsPayload).catch(function () {});
      }

      function renderState(s) {
        if (!s) return;
        lastState = s;
        document.getElementById('connDot').className = 'dot';
        document.getElementById('codecBadge').textContent = s.codec || s.audioQuality || 'AUTO';
        var tr = s.track;
        var artEl = document.getElementById('npArt');
        if (tr) {
          document.getElementById('npTitle').textContent = tr.title || '-';
          document.getElementById('npArtist').textContent = tr.artist || '';
          document.getElementById('npStatus').textContent = s.status || '';
          document.getElementById('lyrTrackTitle').textContent = tr.title || '-';
          document.getElementById('lyrTrackSub').textContent = (tr.artist || '') + (s.lyrics && s.lyrics.provider ? ' · ' + s.lyrics.provider : '');
          var nextArt = tr.thumbnailUrl || '';
          if (nextArt !== lastArtUrl) {
            lastArtUrl = nextArt;
            if (nextArt) {
              artEl.innerHTML = '<img src="' + esc(nextArt) + '" alt="" />';
            } else {
              artEl.innerHTML = SVG_NOTE;
            }
          }
          var likeBtn = document.getElementById('likeBtn');
          document.getElementById('likeBtnTxt').textContent = tr.isFavorite ? L.likedBtn : L.likeBtn;
          likeBtn.classList.toggle('active', !!tr.isFavorite);
        } else {
          document.getElementById('npTitle').textContent = L.nothingPlaying;
          document.getElementById('npArtist').textContent = L.pickHint;
          document.getElementById('npStatus').textContent = 'IDLE';
          document.getElementById('lyrTrackTitle').textContent = L.nothingPlaying;
          document.getElementById('lyrTrackSub').textContent = '';
          if (lastArtUrl !== '') {
            lastArtUrl = '';
            artEl.innerHTML = SVG_NOTE;
          }
          document.getElementById('likeBtn').classList.remove('active');
          document.getElementById('likeBtnTxt').textContent = L.likeBtn;
        }

        var playSvg = s.status === 'PLAYING' ? SVG_PAUSE : SVG_PLAY;
        document.getElementById('playPauseSvg').innerHTML = playSvg;
        document.getElementById('lyrPlaySvg').innerHTML = playSvg;

        if (!seekDragging) {
          var seek = document.getElementById('seekRange');
          var dur = Math.max(1, s.durationMs || 0);
          seek.max = dur;
          seek.value = Math.min(dur, s.positionMs || 0);
          document.getElementById('posTxt').textContent = fmtMs(s.positionMs);
          document.getElementById('durTxt').textContent = fmtMs(s.durationMs);
        }

        document.getElementById('autoBtn').classList.toggle('active', !!s.autoplay);
        Array.from(document.querySelectorAll('#qualRow button')).forEach(function (b) {
          b.classList.toggle('active', b.getAttribute('data-quality') === s.audioQuality);
        });

        if (s.volume) {
          var vr = document.getElementById('volRange');
          vr.max = Math.max(1, s.volume.max || 15);
          vr.value = s.volume.current || 0;
          document.getElementById('volVal').textContent = (s.volume.current || 0) + '/' + (s.volume.max || 15);
        }

        if (s.lyrics) {
          var romBtn = document.getElementById('romajiBtn');
          romBtn.disabled = !s.lyrics.hasRomaji;
          romBtn.classList.toggle('active', !!s.lyrics.showRomaji);
          var off = s.lyrics.offsetMs || 0;
          document.getElementById('offsetResetBtn').textContent = (off > 0 ? '+' : '') + off + 'ms';
          document.getElementById('providerCycleBtn').textContent = L.sourcePrefix + (s.lyrics.provider ? ': ' + s.lyrics.provider : '');
          if (lyricsViewOpen) {
            fetchLyricsIfNeeded(false);
            if (typeof s.lyrics.activeIndex === 'number') {
              highlightActiveLyricLine(s.lyrics.activeIndex);
            }
          }
        }

        var chipItems = (s.chips && s.chips.items) ? s.chips.items : [];
        var chipsSig = chipItems.map(function (c) { return c.label + ':' + (c.selected ? '1' : '0'); }).join('|');
        if (chipsSig !== lastChipsSig) {
          lastChipsSig = chipsSig;
          var chipsRow = document.getElementById('chipsRow');
          chipsRow.innerHTML = '';
          chipItems.forEach(function (c) {
            var b = document.createElement('button');
            b.type = 'button';
            b.className = 'toolChip' + (c.selected ? ' active' : '');
            b.style.flex = '0 0 auto';
            b.textContent = c.label;
            b.onclick = function () { apiCmd({ action: 'select_chip', label: c.selected ? '' : c.label }); };
            chipsRow.appendChild(b);
          });
        }

        var qItems = (s.queue && s.queue.items) ? s.queue.items : [];
        var curIdx = s.queue ? s.queue.currentIndex : -1;
        document.getElementById('queueCountTxt').textContent = L.navQueue + ' (' + qItems.length + ')';
        var queueSig = curIdx + '#' + qItems.map(function (it) { return it.index + ':' + it.id; }).join(',');
        if (queueSig !== lastQueueSig) {
          lastQueueSig = queueSig;
          var qList = document.getElementById('queueList');
          qList.innerHTML = '';
          qItems.forEach(function (it) {
            var row = document.createElement('div');
            row.className = 'listRow' + (it.index === curIdx ? ' playing' : '');
            var thumbHtml = it.thumbnailUrl ? '<img class="rowThumb" src="' + esc(it.thumbnailUrl) + '" alt="" />' : '<div class="rowThumb"></div>';
            row.innerHTML = thumbHtml +
              '<div class="rowMeta"><div class="rowTitle">' + esc(it.title) + '</div><div class="rowSub">' + esc(it.artist) + '</div></div>' +
              '<div class="rowBtns">' +
              (it.index > 0 ? '<button type="button" class="miniBtn" data-mv="-1" aria-label="Move up">' + SVG_UP_SM + '</button>' : '') +
              (it.index < qItems.length - 1 ? '<button type="button" class="miniBtn" data-mv="1" aria-label="Move down">' + SVG_DN_SM + '</button>' : '') +
              '<button type="button" class="miniBtn" data-rm="1" aria-label="Remove">' + SVG_X_SM + '</button></div>';
            row.querySelector('.rowMeta').onclick = function () { apiCmd({ action: 'play_queue_at', index: it.index }); };
            var mvUp = row.querySelector('[data-mv="-1"]');
            if (mvUp) mvUp.onclick = function () { apiCmd({ action: 'move_queue', from: it.index, to: it.index - 1 }); };
            var mvDn = row.querySelector('[data-mv="1"]');
            if (mvDn) mvDn.onclick = function () { apiCmd({ action: 'move_queue', from: it.index, to: it.index + 1 }); };
            row.querySelector('[data-rm]').onclick = function () { apiCmd({ action: 'remove_queue_at', index: it.index }); };
            qList.appendChild(row);
          });
        }
      }

      function renderSongsAndCollections(data) {
        var box = document.getElementById('searchBody');
        box.innerHTML = '';
        if (!data) return;
        if (data.songs && data.songs.length) {
          var h = document.createElement('p');
          h.className = 'secLabel';
          h.style.marginBottom = '8px';
          h.textContent = 'Songs (' + data.songs.length + ')';
          box.appendChild(h);
          data.songs.forEach(function (song) {
            var row = document.createElement('div');
            row.className = 'listRow';
            var th = song.thumb ? '<img class="rowThumb" src="' + esc(song.thumb) + '" alt="" />' : '<div class="rowThumb"></div>';
            row.innerHTML = th +
              '<div class="rowMeta"><div class="rowTitle">' + esc(song.title) + '</div><div class="rowSub">' + esc(song.artist) + '</div></div>' +
              '<div class="rowBtns">' +
              '<button type="button" class="miniBtn hi" aria-label="Play">' + SVG_PLAY_SM + '</button>' +
              '<button type="button" class="miniBtn" aria-label="Queue">' + SVG_PLUS_SM + '</button>' +
              '<button type="button" class="miniBtn" aria-label="Playlist">' + SVG_PL_SM + '</button></div>';
            var btns = row.querySelectorAll('button');
            btns[0].onclick = function () {
              apiCmd({ action: 'play_track', id: song.id, title: song.title, artist: song.artist, thumb: song.thumb, duration: song.duration });
              showToast(song.title);
            };
            btns[1].onclick = function () {
              apiCmd({ action: 'enqueue_track', id: song.id, title: song.title, artist: song.artist, thumb: song.thumb, duration: song.duration });
              showToast('+ ' + song.title);
            };
            btns[2].onclick = function () { openPlaylistModal(song); };
            box.appendChild(row);
          });
        }
        if (data.artists && data.artists.length) {
          var ha = document.createElement('p');
          ha.className = 'secLabel';
          ha.style.margin = '14px 0 8px';
          ha.textContent = 'Artists (' + data.artists.length + ')';
          box.appendChild(ha);
          data.artists.forEach(function (art) {
            var row = document.createElement('div');
            row.className = 'listRow';
            var th = art.thumb ? '<img class="rowThumb" style="border-radius:50%" src="' + esc(art.thumb) + '" alt="" />' : '<div class="rowThumb" style="border-radius:50%"></div>';
            row.innerHTML = th + '<div class="rowMeta"><div class="rowTitle">' + esc(art.name) + '</div></div>';
            if (art.id) {
              row.onclick = function () {
                document.getElementById('genreHeaderBar').style.display = 'flex';
                document.getElementById('activeGenreName').textContent = art.name;
                apiGet('/api/artist?id=' + encodeURIComponent(art.id)).then(renderSongsAndCollections);
              };
            }
            box.appendChild(row);
          });
        }
        if (data.playlists && data.playlists.length) {
          var hp = document.createElement('p');
          hp.className = 'secLabel';
          hp.style.margin = '14px 0 8px';
          hp.textContent = 'Playlists (' + data.playlists.length + ')';
          box.appendChild(hp);
          data.playlists.forEach(function (pl) {
            var row = document.createElement('div');
            row.className = 'listRow';
            var th = pl.thumb ? '<img class="rowThumb" src="' + esc(pl.thumb) + '" alt="" />' : '<div class="rowThumb"></div>';
            row.innerHTML = th +
              '<div class="rowMeta"><div class="rowTitle">' + esc(pl.title) + '</div><div class="rowSub">' + esc(pl.author) + '</div></div>' +
              '<div class="rowBtns"><button type="button" class="miniBtn hi" aria-label="Play">' + SVG_PLAY_SM + '</button></div>';
            row.querySelector('.rowMeta').onclick = function () {
              document.getElementById('genreHeaderBar').style.display = 'flex';
              document.getElementById('activeGenreName').textContent = pl.title;
              apiGet('/api/playlist?id=' + encodeURIComponent(pl.id)).then(renderSongsAndCollections);
            };
            row.querySelector('button').onclick = function () {
              apiCmd({ action: 'play_playlist', playlistId: pl.id });
              showToast(pl.title);
            };
            box.appendChild(row);
          });
        }
      }

      function renderGenres() {
        document.getElementById('genreHeaderBar').style.display = 'none';
        var box = document.getElementById('searchBody');
        box.innerHTML = '';
        if (!genresCache || !genresCache.length) {
          box.innerHTML = '<div class="card" style="color:var(--muted);">Loading…</div>';
          apiGet('/api/genres').then(function (d) {
            genresCache = (d && d.sections) ? d.sections : [];
            if (!document.getElementById('searchInput').value.trim() && !selectedGenre) {
              renderGenres();
            }
          });
          return;
        }
        genresCache.forEach(function (sec) {
          var h = document.createElement('p');
          h.className = 'secLabel';
          h.style.margin = '4px 0 8px';
          h.textContent = sec.title;
          box.appendChild(h);
          var grid = document.createElement('div');
          grid.className = 'genreGrid';
          sec.items.forEach(function (g) {
            var b = document.createElement('button');
            b.type = 'button';
            b.className = 'genreTile';
            b.innerHTML = '<span class="stripe" style="background:' + esc(g.color) + '"></span><span>' + esc(g.title) + '</span>';
            b.onclick = function () {
              selectedGenre = g;
              document.getElementById('genreHeaderBar').style.display = 'flex';
              document.getElementById('activeGenreName').textContent = g.title;
              box.innerHTML = '<div class="card" style="color:var(--muted);">Loading ' + esc(g.title) + '…</div>';
              apiGet('/api/genre?browseId=' + encodeURIComponent(g.browseId) + '&params=' + encodeURIComponent(g.params || '') + '&title=' + encodeURIComponent(g.title))
                .then(renderSongsAndCollections);
            };
            grid.appendChild(b);
          });
          box.appendChild(grid);
        });
      }

      function openPlaylistModal(songObj) {
        pendingPlaylistTrack = songObj || null;
        var modal = document.getElementById('playlistModal');
        var body = document.getElementById('plModalBody');
        body.innerHTML = '';
        if (!lastState || !lastState.auth || !lastState.auth.authenticated) {
          body.innerHTML = '<p style="color:var(--muted); font-size:13px;">' + esc(L.signInHint) + '</p>';
          modal.classList.add('show');
          return;
        }
        var form = document.createElement('form');
        form.className = 'searchBar';
        form.innerHTML = '<input class="searchInput" type="text" placeholder="' + esc(L.newPlPh) + '" required /><button class="btnPrimary" type="submit">' + esc(L.createPl) + '</button>';
        form.onsubmit = function (ev) {
          ev.preventDefault();
          var title = form.querySelector('input').value.trim();
          if (!title) return;
          apiCmd({
            action: 'create_playlist',
            title: title,
            trackId: pendingPlaylistTrack ? pendingPlaylistTrack.id : ''
          });
          modal.classList.remove('show');
          showToast('+ ' + title);
        };
        body.appendChild(form);
        (lastState.playlists || []).forEach(function (pl) {
          var row = document.createElement('div');
          row.className = 'listRow';
          row.innerHTML = '<div class="rowMeta"><div class="rowTitle">' + esc(pl.title) + '</div><div class="rowSub">' + esc(pl.trackCount) + '</div></div><button type="button" class="miniBtn hi">' + SVG_PLUS_SM + '</button>';
          row.onclick = function () {
            apiCmd({
              action: 'add_to_playlist',
              playlistId: pl.id,
              playlistTitle: pl.title,
              id: pendingPlaylistTrack ? pendingPlaylistTrack.id : (lastState.track ? lastState.track.id : ''),
              title: pendingPlaylistTrack ? pendingPlaylistTrack.title : (lastState.track ? lastState.track.title : ''),
              artist: pendingPlaylistTrack ? pendingPlaylistTrack.artist : (lastState.track ? lastState.track.artist : '')
            });
            modal.classList.remove('show');
            showToast('+ ' + pl.title);
          };
          body.appendChild(row);
        });
        modal.classList.add('show');
      }

      function setNowSubView(showLyrics) {
        lyricsViewOpen = !!showLyrics;
        document.getElementById('subPlayerBtn').classList.toggle('active', !lyricsViewOpen);
        document.getElementById('subLyricsBtn').classList.toggle('active', lyricsViewOpen);
        document.getElementById('nowPlayerSub').style.display = lyricsViewOpen ? 'none' : 'block';
        document.getElementById('nowLyricsSub').style.display = lyricsViewOpen ? 'block' : 'none';
        if (lyricsViewOpen) {
          fetchLyricsIfNeeded(true);
        }
      }

      document.getElementById('subPlayerBtn').onclick = function () { setNowSubView(false); };
      document.getElementById('subLyricsBtn').onclick = function () { setNowSubView(true); };

      document.getElementById('openPlBtn').onclick = function () { openPlaylistModal(null); };
      document.getElementById('closePlBtn').onclick = function () { document.getElementById('playlistModal').classList.remove('show'); };
      document.getElementById('playlistModal').onclick = function (ev) {
        if (ev.target === this) this.classList.remove('show');
      };

      Array.from(document.querySelectorAll('.navBtn')).forEach(function (btn) {
        btn.onclick = function () {
          var targetId = btn.getAttribute('data-tab');
          Array.from(document.querySelectorAll('.navBtn')).forEach(function (b) { b.classList.toggle('active', b === btn); });
          Array.from(document.querySelectorAll('.panel')).forEach(function (p) { p.classList.toggle('active', p.id === targetId); });
          if (targetId === 'tab-search' && !document.getElementById('searchInput').value.trim() && !selectedGenre) {
            renderGenres();
          }
          if (targetId === 'tab-now' && lyricsViewOpen) {
            fetchLyricsIfNeeded(false);
          }
        };
      });

      Array.from(document.querySelectorAll('[data-cmd]')).forEach(function (btn) {
        btn.onclick = function () {
          var act = btn.getAttribute('data-cmd');
          apiCmd({ action: act }).then(function () {
            if (act === 'toggle_romaji' || act === 'cycle_lyrics_provider') {
              lastLyricsKey = '';
              setTimeout(function () { fetchLyricsIfNeeded(true); }, 250);
            }
          });
        };
      });

      Array.from(document.querySelectorAll('[data-quality]')).forEach(function (btn) {
        btn.onclick = function () { apiCmd({ action: 'set_quality', preset: btn.getAttribute('data-quality') }); };
      });

      Array.from(document.querySelectorAll('[data-lyrics-delta]')).forEach(function (btn) {
        btn.onclick = function () { apiCmd({ action: 'lyrics_offset', deltaMs: btn.getAttribute('data-lyrics-delta') }); };
      });

      Array.from(document.querySelectorAll('[data-dpad]')).forEach(function (btn) {
        btn.onclick = function () {
          var k = btn.getAttribute('data-dpad');
          apiCmd({ action: 'dpad', key: k });
          showToast('TV ' + k);
        };
      });

      Array.from(document.querySelectorAll('[data-nav]')).forEach(function (btn) {
        btn.onclick = function () {
          var d = btn.getAttribute('data-nav');
          apiCmd({ action: 'nav', dest: d });
          showToast('TV → ' + d);
        };
      });

      var seekRange = document.getElementById('seekRange');
      seekRange.oninput = function () {
        seekDragging = true;
        document.getElementById('posTxt').textContent = fmtMs(Number(seekRange.value));
      };
      seekRange.onchange = function () {
        seekDragging = false;
        apiCmd({ action: 'seek_to', ms: seekRange.value });
      };

      var volRange = document.getElementById('volRange');
      volRange.onchange = function () { apiCmd({ action: 'volume_set', level: volRange.value }); };
      document.getElementById('volDownBtn').onclick = function () { apiCmd({ action: 'volume_delta', delta: -1 }); };
      document.getElementById('volUpBtn').onclick = function () { apiCmd({ action: 'volume_delta', delta: 1 }); };

      var searchInput = document.getElementById('searchInput');
      var clearSearchBtn = document.getElementById('clearSearchBtn');
      searchInput.oninput = function () {
        var hasText = searchInput.value.trim().length > 0;
        clearSearchBtn.style.display = hasText ? 'inline-flex' : 'none';
        if (!hasText) {
          selectedGenre = null;
          renderGenres();
        }
      };
      clearSearchBtn.onclick = function () {
        searchInput.value = '';
        clearSearchBtn.style.display = 'none';
        selectedGenre = null;
        renderGenres();
      };
      document.getElementById('backGenresBtn').onclick = function () {
        selectedGenre = null;
        renderGenres();
      };
      document.getElementById('searchForm').onsubmit = function (ev) {
        ev.preventDefault();
        var q = searchInput.value.trim();
        if (!q) { renderGenres(); return; }
        selectedGenre = null;
        document.getElementById('genreHeaderBar').style.display = 'none';
        document.getElementById('searchBody').innerHTML = '<div class="card" style="color:var(--muted);">Searching…</div>';
        apiGet('/api/search?q=' + encodeURIComponent(q)).then(renderSongsAndCollections);
      };

      function pollOnce() {
        apiGet('/api/state').then(renderState).catch(function () {
          document.getElementById('connDot').className = 'dot off';
        });
      }

      function startPolling() {
        stopPolling();
        pollOnce();
        pollTimer = setInterval(pollOnce, 1000);
      }

      function stopPolling() {
        if (pollTimer) { clearInterval(pollTimer); pollTimer = null; }
      }

      document.addEventListener('visibilitychange', function () {
        if (document.visibilityState === 'visible') startPolling();
        else stopPolling();
      });

      startPolling();
    })();
  </script>
</body>
</html>"""
    }
}
