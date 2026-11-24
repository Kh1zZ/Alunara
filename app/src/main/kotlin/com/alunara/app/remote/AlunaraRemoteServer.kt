package com.alunara.app.remote

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import com.alunara.app.playback.PlaybackSessionManager
import com.alunara.app.playback.PlaybackSessionUiState
import com.alunara.app.ui.i18n.AppLanguage
import com.alunara.core.domain.contract.MusicEngine
import com.alunara.core.domain.model.ArtistRef
import com.alunara.core.domain.model.AuthSessionState
import com.alunara.core.domain.model.GenreCategory
import com.alunara.core.domain.model.GenreSection
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
        val qrPairUrl = lanIp?.let { "http://$it:$resolvedPort/p?k=$pinHex" }
        val directUrl = lanIp?.let { "http://$it:$resolvedPort/r?t=$sessionToken" }
        mutableStatus.value =
            RemoteServerStatus(
                enabled = true,
                running = true,
                lanIp = lanIp,
                port = resolvedPort,
                pairingCode = encoded,
                qrPairUrl = qrPairUrl,
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
            append("\"provider\":").append(escapeJsonString(uiState.selectedLyricsProvider?.name.orEmpty()))
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
        private const val SOCKET_TIMEOUT_MS = 4_000
        private const val MAX_HEADER_BYTES = 8_192
        private const val MAX_BODY_BYTES = 16_384
        private const val MAX_QUEUE_JSON_ITEMS = 60
        private const val PAIR_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

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
                    append("\"navNow\":").append(escapeJsonString(t("Putar", "再生中", "Now Playing"))).append(',')
                    append("\"navSearch\":").append(escapeJsonString(t("Cari", "検索", "Search"))).append(',')
                    append("\"navQueue\":").append(escapeJsonString(t("Antrean", "キュー", "Queue"))).append(',')
                    append("\"navDpad\":").append(escapeJsonString(t("Remote", "操作", "Remote"))).append(',')
                    append("\"nothingPlaying\":").append(escapeJsonString(t("Belum ada lagu diputar di TV", "テレビで再生中の曲はありません", "Nothing playing on TV yet"))).append(',')
                    append("\"pickHint\":").append(escapeJsonString(t("Pilih lagu dari tab Cari & Genre di bawah.", "下の「検索」タブから曲を選んで再生できます。", "Pick a song from Search & Genres below."))).append(',')
                    append("\"searchPh\":").append(escapeJsonString(t("Cari lagu, artis, atau playlist…", "曲・アーティスト・プレイリストを検索…", "Search songs, artists, or playlists…"))).append(',')
                    append("\"searchBtn\":").append(escapeJsonString(t("Cari", "検索", "Search"))).append(',')
                    append("\"genresTitle\":").append(escapeJsonString(t("Jelajahi Genre & Suasana", "ジャンル＆ムード", "Browse Genres & Moods"))).append(',')
                    append("\"allGenres\":").append(escapeJsonString(t("← Semua Genre", "← ジャンル一覧", "← All Genres"))).append(',')
                    append("\"playBtn\":").append(escapeJsonString(t("▶ Putar", "▶ 再生", "▶ Play"))).append(',')
                    append("\"queueAddBtn\":").append(escapeJsonString(t("+ Antre", "+ 追加", "+ Queue"))).append(',')
                    append("\"volLabel\":").append(escapeJsonString(t("Volume TV", "テレビ音量", "TV Volume"))).append(',')
                    append("\"qualLabel\":").append(escapeJsonString(t("Kualitas Audio", "音質", "Audio Quality"))).append(',')
                    append("\"lyricsLabel\":").append(escapeJsonString(t("Sinkronisasi Lirik & Romaji", "歌詞同期 & ローマ字", "Lyrics Sync & Romaji"))).append(',')
                    append("\"romajiBtn\":").append(escapeJsonString(t("Romaji", "ローマ字", "Romaji"))).append(',')
                    append("\"clearQueue\":").append(escapeJsonString(t("Kosongkan Antrean", "キューをクリア", "Clear Queue"))).append(',')
                    append("\"dpadTitle\":").append(escapeJsonString(t("Remote D-Pad TV", "テレビ D-pad 操作", "TV D-Pad Controller"))).append(',')
                    append("\"quickJump\":").append(escapeJsonString(t("Buka Layar TV Cepat", "テレビ画面へ移動", "Jump to TV Screen"))).append(',')
                    append("\"playlistModalTitle\":").append(escapeJsonString(t("Tambah ke Playlist YouTube", "プレイリストに追加", "Add to YouTube Playlist"))).append(',')
                    append("\"newPlPh\":").append(escapeJsonString(t("Nama playlist baru…", "新しいプレイリスト名…", "New playlist name…"))).append(',')
                    append("\"createPl\":").append(escapeJsonString(t("Buat & Tambah", "作成して追加", "Create & Add"))).append(',')
                    append("\"signInHint\":").append(escapeJsonString(t("Masuk akun YouTube Music di Pengaturan TV untuk menyinkronkan playlist.", "YouTube プレイリストと同期するにはテレビの設定でログインしてください。", "Sign in with YouTube Music in TV Settings to sync playlists.")))
                    append('}')
                }

            val html = REMOTE_WEB_APP_TEMPLATE
                .replace("__HTML_LANG__", langCode)
                .replace("__TOKEN_JSON__", escapeJsonString(token))
                .replace("__LABELS_JSON__", labelsJson)
                .replace("__DEMO_MODE__", "false")
            return html
        }

        private val REMOTE_WEB_APP_TEMPLATE =
            """<!DOCTYPE html>
<html lang="__HTML_LANG__">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no" />
  <title>Alunara Remote</title>
  <style>
    :root {
      color-scheme: dark;
      --bg: #101813;
      --surface: #1D2B22;
      --surface2: #26382D;
      --border: #355442;
      --accent: #85BDA0;
      --accentStrong: #C8F0DA;
      --text: #E3EEE5;
      --muted: #9DB7A7;
      --danger: #F4B8BC;
    }
    * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
    body {
      margin: 0;
      min-height: 100vh;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      background: var(--bg);
      color: var(--text);
      padding-bottom: 76px;
    }
    header {
      position: sticky;
      top: 0;
      z-index: 20;
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 12px 16px;
      background: rgba(16, 24, 19, 0.94);
      backdrop-filter: blur(10px);
      border-bottom: 1px solid var(--surface2);
    }
    .brandRow { display: flex; align-items: center; gap: 8px; }
    .dot {
      width: 9px;
      height: 9px;
      border-radius: 50%;
      background: var(--accent);
      box-shadow: 0 0 8px var(--accent);
    }
    .dot.off { background: var(--danger); box-shadow: none; }
    .brandTitle { font-size: 14px; font-weight: 700; letter-spacing: 0.04em; color: var(--accentStrong); }
    .codecPill {
      font-size: 11px;
      font-weight: 600;
      color: var(--muted);
      background: var(--surface);
      border: 1px solid var(--border);
      padding: 4px 10px;
      border-radius: 999px;
      max-width: 170px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    main { max-width: 540px; margin: 0 auto; padding: 14px; }
    .panel { display: none; }
    .panel.active { display: block; }
    .card {
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: 16px;
      padding: 16px;
      margin-bottom: 12px;
    }
    .npHero { display: flex; gap: 14px; align-items: center; margin-bottom: 14px; }
    .artWrap {
      width: 78px;
      height: 78px;
      border-radius: 12px;
      background: var(--surface2);
      overflow: hidden;
      flex-shrink: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 28px;
      color: var(--accent);
    }
    .artWrap img { width: 100%; height: 100%; object-fit: cover; display: block; }
    .npMeta { flex: 1; min-width: 0; }
    .npTitle {
      font-size: 17px;
      font-weight: 700;
      color: var(--text);
      margin: 0 0 4px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .npArtist {
      font-size: 13px;
      color: var(--accent);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      margin-bottom: 4px;
    }
    .npStatus { font-size: 11.5px; color: var(--muted); }
    .seekWrap { margin-bottom: 14px; }
    input[type="range"] {
      width: 100%;
      accent-color: var(--accentStrong);
      height: 6px;
      cursor: pointer;
    }
    .timeRow {
      display: flex;
      justify-content: space-between;
      font-size: 11.5px;
      color: var(--muted);
      margin-top: 4px;
      font-variant-numeric: tabular-nums;
    }
    .transport {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 18px;
      margin-bottom: 14px;
    }
    .btnCircle {
      width: 48px;
      height: 48px;
      border-radius: 50%;
      border: 1px solid var(--border);
      background: var(--surface2);
      color: var(--text);
      font-size: 18px;
      font-weight: 700;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      justify-content: center;
    }
    .btnPlay {
      width: 62px;
      height: 62px;
      border: 0;
      background: var(--accentStrong);
      color: #0B120E;
      font-size: 24px;
    }
    .actionRow {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 8px;
    }
    .chipBtn {
      padding: 9px 6px;
      border-radius: 11px;
      border: 1px solid var(--border);
      background: var(--surface2);
      color: var(--text);
      font-size: 12px;
      font-weight: 600;
      cursor: pointer;
      text-align: center;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .chipBtn.active {
      background: #2C513D;
      border-color: var(--accentStrong);
      color: var(--accentStrong);
    }
    .secLabel {
      font-size: 12px;
      font-weight: 700;
      color: var(--accent);
      text-transform: uppercase;
      letter-spacing: 0.05em;
      margin: 0 0 10px;
    }
    .volRow { display: flex; align-items: center; gap: 10px; }
    .volNum { font-size: 12.5px; font-weight: 700; min-width: 44px; text-align: right; color: var(--accentStrong); }
    .seg4 { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; }
    .searchBar { display: flex; gap: 8px; margin-bottom: 12px; }
    .searchInput {
      flex: 1;
      min-width: 0;
      padding: 12px;
      border-radius: 12px;
      border: 1.5px solid var(--border);
      background: var(--bg);
      color: #fff;
      font-size: 15px;
    }
    .searchInput:focus { outline: none; border-color: var(--accentStrong); }
    .btnPrimary {
      padding: 0 16px;
      border: 0;
      border-radius: 12px;
      background: var(--accentStrong);
      color: #0B120E;
      font-weight: 700;
      font-size: 14px;
      cursor: pointer;
    }
    .btnGhost {
      padding: 9px 12px;
      border-radius: 10px;
      border: 1px solid var(--border);
      background: var(--surface2);
      color: var(--text);
      font-size: 12.5px;
      font-weight: 600;
      cursor: pointer;
    }
    .genreGrid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; margin-bottom: 14px; }
    .genreTile {
      display: flex;
      align-items: center;
      gap: 9px;
      padding: 11px 10px;
      border-radius: 12px;
      border: 1px solid var(--border);
      background: var(--surface);
      color: var(--text);
      font-size: 13px;
      font-weight: 600;
      text-align: left;
      cursor: pointer;
    }
    .stripe { width: 4px; height: 22px; border-radius: 4px; flex-shrink: 0; }
    .listRow {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 10px;
      border-radius: 12px;
      background: var(--surface);
      border: 1px solid var(--surface2);
      margin-bottom: 8px;
    }
    .listRow.playing { border-color: var(--accentStrong); background: #22382B; }
    .rowThumb {
      width: 44px;
      height: 44px;
      border-radius: 8px;
      background: var(--surface2);
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
    .rowSub {
      font-size: 12px;
      color: var(--muted);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .rowBtns { display: flex; gap: 6px; flex-shrink: 0; }
    .miniBtn {
      padding: 6px 9px;
      border-radius: 8px;
      border: 1px solid var(--border);
      background: var(--surface2);
      color: var(--text);
      font-size: 11.5px;
      font-weight: 600;
      cursor: pointer;
    }
    .miniBtn.hi { background: var(--accentStrong); color: #0B120E; border: 0; }
    .chipsScroll { display: flex; gap: 8px; overflow-x: auto; padding-bottom: 8px; margin-bottom: 8px; }
    .dpadGrid {
      display: grid;
      grid-template-columns: repeat(3, 74px);
      gap: 10px;
      justify-content: center;
      margin: 14px auto;
    }
    .dpadKey {
      height: 62px;
      border-radius: 14px;
      border: 1.5px solid var(--border);
      background: var(--surface2);
      color: var(--text);
      font-size: 18px;
      font-weight: 700;
      cursor: pointer;
    }
    .dpadOk {
      background: var(--accentStrong);
      color: #0B120E;
      border: 0;
      border-radius: 50%;
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
      background: #142119;
      border-top: 1px solid var(--border);
      padding-bottom: env(safe-area-inset-bottom, 0);
    }
    .navBtn {
      border: 0;
      background: transparent;
      color: var(--muted);
      padding: 10px 4px 11px;
      font-size: 11.5px;
      font-weight: 600;
      cursor: pointer;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 2px;
    }
    .navBtn.active { color: var(--accentStrong); }
    .toast {
      position: fixed;
      top: 58px;
      left: 50%;
      transform: translateX(-50%);
      background: #163826;
      border: 1px solid var(--accentStrong);
      color: var(--text);
      padding: 8px 14px;
      border-radius: 999px;
      font-size: 12.5px;
      font-weight: 600;
      z-index: 50;
      display: none;
      max-width: 90%;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .modalBack {
      display: none;
      position: fixed;
      inset: 0;
      z-index: 60;
      background: rgba(0, 0, 0, 0.72);
      align-items: flex-end;
      justify-content: center;
      padding: 14px;
    }
    .modalBack.show { display: flex; }
    .modalSheet {
      width: 100%;
      max-width: 480px;
      max-height: 78vh;
      overflow-y: auto;
      background: var(--surface);
      border: 1.5px solid var(--accent);
      border-radius: 18px;
      padding: 18px;
    }
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

  <div id="toast" class="toast"></div>

  <main>
    <!-- TAB 1: NOW PLAYING -->
    <section id="tab-now" class="panel active">
      <div class="card">
        <div class="npHero">
          <div id="npArt" class="artWrap">♪</div>
          <div class="npMeta">
            <div id="npTitle" class="npTitle">-</div>
            <div id="npArtist" class="npArtist"></div>
            <div id="npStatus" class="npStatus">IDLE</div>
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
          <button class="btnCircle" data-cmd="prev" title="Previous">⏮</button>
          <button id="playPauseBtn" class="btnCircle btnPlay" data-cmd="play_pause" title="Play/Pause">▶</button>
          <button class="btnCircle" data-cmd="next" title="Next">⏭</button>
        </div>
        <div class="actionRow">
          <button id="likeBtn" class="chipBtn" data-cmd="toggle_like">♡ Like</button>
          <button id="openPlBtn" class="chipBtn">+ Playlist</button>
          <button class="chipBtn" data-cmd="start_radio">Radio</button>
          <button id="autoBtn" class="chipBtn" data-cmd="toggle_autoplay">Auto</button>
        </div>
      </div>

      <div class="card">
        <p id="volLabelEl" class="secLabel">TV Volume</p>
        <div class="volRow">
          <button id="volDownBtn" class="miniBtn">－</button>
          <input id="volRange" type="range" min="0" max="15" value="8" />
          <button id="volUpBtn" class="miniBtn">＋</button>
          <span id="volVal" class="volNum">8/15</span>
        </div>
      </div>

      <div class="card">
        <p id="qualLabelEl" class="secLabel">Audio Quality</p>
        <div class="seg4" id="qualRow">
          <button class="chipBtn" data-quality="AUTO">Auto</button>
          <button class="chipBtn" data-quality="LOW">Low</button>
          <button class="chipBtn" data-quality="MEDIUM">Med</button>
          <button class="chipBtn" data-quality="HIGH">High</button>
        </div>
      </div>

      <div class="card">
        <p id="lyricsLabelEl" class="secLabel">Lyrics Sync &amp; Romaji</p>
        <div class="actionRow">
          <button id="romajiBtn" class="chipBtn" data-cmd="toggle_romaji">Romaji</button>
          <button class="chipBtn" data-lyrics-delta="-100">-0.1s</button>
          <button id="offsetResetBtn" class="chipBtn" data-cmd="lyrics_reset">0ms</button>
          <button class="chipBtn" data-lyrics-delta="100">+0.1s</button>
        </div>
      </div>
    </section>

    <!-- TAB 2: SEARCH & GENRES -->
    <section id="tab-search" class="panel">
      <form id="searchForm" class="searchBar">
        <input id="searchInput" class="searchInput" type="search" autocomplete="off" />
        <button id="clearSearchBtn" type="button" class="btnGhost" style="display:none;">✕</button>
        <button id="searchSubmitBtn" type="submit" class="btnPrimary">Cari</button>
      </form>
      <div id="genreHeaderBar" style="display:none; align-items:center; gap:10px; margin-bottom:10px;">
        <button id="backGenresBtn" type="button" class="btnGhost"></button>
        <strong id="activeGenreName" style="color:var(--accentStrong);"></strong>
      </div>
      <div id="searchBody"></div>
    </section>

    <!-- TAB 3: QUEUE -->
    <section id="tab-queue" class="panel">
      <div id="chipsRow" class="chipsScroll"></div>
      <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:10px;">
        <span id="queueCountTxt" class="secLabel" style="margin:0;">Queue (0)</span>
        <div style="display:flex; gap:8px;">
          <button class="btnGhost" data-cmd="refresh_radio">↻ Radio</button>
          <button id="clearQueueBtn" class="btnGhost" data-cmd="clear_queue">Clear</button>
        </div>
      </div>
      <div id="queueList"></div>
    </section>

    <!-- TAB 4: D-PAD REMOTE -->
    <section id="tab-dpad" class="panel">
      <div class="card">
        <p id="dpadTitleEl" class="secLabel">TV D-Pad</p>
        <div class="dpadGrid">
          <div></div>
          <button class="dpadKey" data-dpad="UP">▲</button>
          <div></div>
          <button class="dpadKey" data-dpad="LEFT">◀</button>
          <button class="dpadKey dpadOk" data-dpad="OK">OK</button>
          <button class="dpadKey" data-dpad="RIGHT">▶</button>
          <button class="dpadKey" data-dpad="BACK" style="font-size:13px;">BACK</button>
          <button class="dpadKey" data-dpad="DOWN">▼</button>
          <div></div>
        </div>
      </div>
      <div class="card">
        <p id="quickJumpEl" class="secLabel">Jump to TV Screen</p>
        <div class="destGrid">
          <button class="btnGhost" data-nav="HOME">🏠 Beranda / Home</button>
          <button class="btnGhost" data-nav="SEARCH">🔍 Cari / Search</button>
          <button class="btnGhost" data-nav="LIBRARY">📚 Koleksi / Library</button>
          <button class="btnGhost" data-nav="SETTINGS">⚙ Pengaturan</button>
          <button class="btnGhost" data-nav="NOW_PLAYING" style="grid-column: span 2;">▶ Now Playing</button>
        </div>
      </div>
    </section>
  </main>

  <nav class="bottomNav">
    <button class="navBtn active" data-tab="tab-now"><span>♪</span><span id="navNowTxt">Putar</span></button>
    <button class="navBtn" data-tab="tab-search"><span>🔍</span><span id="navSearchTxt">Cari</span></button>
    <button class="navBtn" data-tab="tab-queue"><span>☰</span><span id="navQueueTxt">Antrean</span></button>
    <button class="navBtn" data-tab="tab-dpad"><span>🎮</span><span id="navDpadTxt">Remote</span></button>
  </nav>

  <div id="playlistModal" class="modalBack">
    <div class="modalSheet">
      <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:12px;">
        <strong id="plModalTitle" style="color:var(--accentStrong);">Add to Playlist</strong>
        <button id="closePlBtn" class="miniBtn">✕</button>
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

      document.getElementById('navNowTxt').textContent = L.navNow;
      document.getElementById('navSearchTxt').textContent = L.navSearch;
      document.getElementById('navQueueTxt').textContent = L.navQueue;
      document.getElementById('navDpadTxt').textContent = L.navDpad;
      document.getElementById('searchInput').placeholder = L.searchPh;
      document.getElementById('searchSubmitBtn').textContent = L.searchBtn;
      document.getElementById('backGenresBtn').textContent = L.allGenres;
      document.getElementById('volLabelEl').textContent = L.volLabel;
      document.getElementById('qualLabelEl').textContent = L.qualLabel;
      document.getElementById('lyricsLabelEl').textContent = L.lyricsLabel;
      document.getElementById('clearQueueBtn').textContent = L.clearQueue;
      document.getElementById('dpadTitleEl').textContent = L.dpadTitle;
      document.getElementById('quickJumpEl').textContent = L.quickJump;
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
        el._t = setTimeout(function () { el.style.display = 'none'; }, 2000);
      }

      function esc(s) {
        return String(s || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
      }

      function apiGet(path) {
        if (DEMO_MODE && window.__DEMO_HANDLER__) return window.__DEMO_HANDLER__('GET', path, null);
        var sep = path.indexOf('?') >= 0 ? '&' : '?';
        return fetch(path + sep + 't=' + encodeURIComponent(TOKEN)).then(function (r) { return r.json(); });
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
          if (tr.thumbnailUrl) {
            artEl.innerHTML = '<img src="' + esc(tr.thumbnailUrl) + '" alt="" />';
          } else {
            artEl.textContent = '♪';
          }
          var likeBtn = document.getElementById('likeBtn');
          likeBtn.textContent = tr.isFavorite ? '♥ Liked' : '♡ Like';
          likeBtn.classList.toggle('active', !!tr.isFavorite);
        } else {
          document.getElementById('npTitle').textContent = L.nothingPlaying;
          document.getElementById('npArtist').textContent = L.pickHint;
          document.getElementById('npStatus').textContent = 'IDLE';
          artEl.textContent = '♪';
          document.getElementById('likeBtn').classList.remove('active');
        }

        document.getElementById('playPauseBtn').textContent = s.status === 'PLAYING' ? '❚❚' : '▶';
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
          romBtn.style.opacity = s.lyrics.hasRomaji ? '1' : '0.45';
          romBtn.classList.toggle('active', !!s.lyrics.showRomaji);
          document.getElementById('offsetResetBtn').textContent = (s.lyrics.offsetMs || 0) + 'ms';
        }

        var chipsRow = document.getElementById('chipsRow');
        chipsRow.innerHTML = '';
        if (s.chips && s.chips.items) {
          s.chips.items.forEach(function (c) {
            var b = document.createElement('button');
            b.className = 'chipBtn' + (c.selected ? ' active' : '');
            b.style.flex = '0 0 auto';
            b.style.padding = '7px 12px';
            b.textContent = c.label;
            b.onclick = function () { apiCmd({ action: 'select_chip', label: c.selected ? '' : c.label }); };
            chipsRow.appendChild(b);
          });
        }

        var qItems = (s.queue && s.queue.items) ? s.queue.items : [];
        var curIdx = s.queue ? s.queue.currentIndex : -1;
        document.getElementById('queueCountTxt').textContent = L.navQueue + ' (' + qItems.length + ')';
        var qList = document.getElementById('queueList');
        qList.innerHTML = '';
        qItems.forEach(function (it) {
          var row = document.createElement('div');
          row.className = 'listRow' + (it.index === curIdx ? ' playing' : '');
          var thumbHtml = it.thumbnailUrl ? '<img class="rowThumb" src="' + esc(it.thumbnailUrl) + '" />' : '<div class="rowThumb"></div>';
          row.innerHTML = thumbHtml +
            '<div class="rowMeta"><div class="rowTitle">' + esc(it.title) + '</div><div class="rowSub">' + esc(it.artist) + '</div></div>' +
            '<div class="rowBtns">' +
            (it.index > 0 ? '<button class="miniBtn" data-mv="-1">↑</button>' : '') +
            (it.index < qItems.length - 1 ? '<button class="miniBtn" data-mv="1">↓</button>' : '') +
            '<button class="miniBtn" data-rm="1">✕</button></div>';
          row.querySelector('.rowMeta').onclick = function () { apiCmd({ action: 'play_queue_at', index: it.index }); };
          var mvUp = row.querySelector('[data-mv="-1"]');
          if (mvUp) mvUp.onclick = function () { apiCmd({ action: 'move_queue', from: it.index, to: it.index - 1 }); };
          var mvDn = row.querySelector('[data-mv="1"]');
          if (mvDn) mvDn.onclick = function () { apiCmd({ action: 'move_queue', from: it.index, to: it.index + 1 }); };
          row.querySelector('[data-rm]').onclick = function () { apiCmd({ action: 'remove_queue_at', index: it.index }); };
          qList.appendChild(row);
        });
      }

      function renderSongsAndCollections(data) {
        var box = document.getElementById('searchBody');
        box.innerHTML = '';
        if (!data) return;
        if (data.songs && data.songs.length) {
          var h = document.createElement('p');
          h.className = 'secLabel';
          h.textContent = 'Songs (' + data.songs.length + ')';
          box.appendChild(h);
          data.songs.forEach(function (song) {
            var row = document.createElement('div');
            row.className = 'listRow';
            var th = song.thumb ? '<img class="rowThumb" src="' + esc(song.thumb) + '" />' : '<div class="rowThumb"></div>';
            row.innerHTML = th +
              '<div class="rowMeta"><div class="rowTitle">' + esc(song.title) + '</div><div class="rowSub">' + esc(song.artist) + '</div></div>' +
              '<div class="rowBtns"><button class="miniBtn hi">' + esc(L.playBtn) + '</button><button class="miniBtn">' + esc(L.queueAddBtn) + '</button><button class="miniBtn">+</button></div>';
            var btns = row.querySelectorAll('button');
            btns[0].onclick = function () {
              apiCmd({ action: 'play_track', id: song.id, title: song.title, artist: song.artist, thumb: song.thumb, duration: song.duration });
              showToast('▶ ' + song.title);
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
          ha.style.marginTop = '14px';
          ha.textContent = 'Artists (' + data.artists.length + ')';
          box.appendChild(ha);
          data.artists.forEach(function (art) {
            var row = document.createElement('div');
            row.className = 'listRow';
            var th = art.thumb ? '<img class="rowThumb" style="border-radius:50%" src="' + esc(art.thumb) + '" />' : '<div class="rowThumb" style="border-radius:50%"></div>';
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
          hp.style.marginTop = '14px';
          hp.textContent = 'Playlists (' + data.playlists.length + ')';
          box.appendChild(hp);
          data.playlists.forEach(function (pl) {
            var row = document.createElement('div');
            row.className = 'listRow';
            var th = pl.thumb ? '<img class="rowThumb" src="' + esc(pl.thumb) + '" />' : '<div class="rowThumb"></div>';
            row.innerHTML = th +
              '<div class="rowMeta"><div class="rowTitle">' + esc(pl.title) + '</div><div class="rowSub">' + esc(pl.author) + '</div></div>' +
              '<div class="rowBtns"><button class="miniBtn hi">' + esc(L.playBtn) + '</button></div>';
            row.querySelector('.rowMeta').onclick = function () {
              document.getElementById('genreHeaderBar').style.display = 'flex';
              document.getElementById('activeGenreName').textContent = pl.title;
              apiGet('/api/playlist?id=' + encodeURIComponent(pl.id)).then(renderSongsAndCollections);
            };
            row.querySelector('button').onclick = function () {
              apiCmd({ action: 'play_playlist', playlistId: pl.id });
              showToast('▶ ' + pl.title);
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
          box.innerHTML = '<div class="card" style="color:var(--muted);">Loading genres…</div>';
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
          h.textContent = sec.title;
          box.appendChild(h);
          var grid = document.createElement('div');
          grid.className = 'genreGrid';
          sec.items.forEach(function (g) {
            var b = document.createElement('button');
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
          row.innerHTML = '<div class="rowMeta"><div class="rowTitle">' + esc(pl.title) + '</div><div class="rowSub">' + esc(pl.trackCount) + '</div></div><button class="miniBtn hi">+</button>';
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
        };
      });

      Array.from(document.querySelectorAll('[data-cmd]')).forEach(function (btn) {
        btn.onclick = function () { apiCmd({ action: btn.getAttribute('data-cmd') }); };
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
        clearSearchBtn.style.display = hasText ? 'inline-block' : 'none';
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
