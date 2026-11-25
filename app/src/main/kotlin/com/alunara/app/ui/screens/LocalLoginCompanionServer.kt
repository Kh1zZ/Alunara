package com.alunara.app.ui.screens

import com.alunara.app.ui.i18n.AppLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

internal data class DecodedPairingTarget(
    val ip: String,
    val port: Int,
    val pin: Int,
)

enum class CompanionAction {
    FILL_EMAIL,
    FILL_PASSWORD,
    FILL_TEXT,
    CLICK_NEXT,
    FOCUS_PREV,
    FOCUS_NEXT,
    RELOAD_PAGE,
    SUBMIT_COOKIE,
}

data class CompanionInputPayload(
    val action: CompanionAction,
    val value: String = "",
)

/**
 * Ephemeral LAN-only HTTP server used during Google TV WebView sign-in so users can scan a QR code
 * with their phone, follow live-synced 1-2-3 guidance, use remote controls, or paste a web session
 * cookie directly into the active TV WebView in memory (zero disk persistence, zero logging).
 */
class LocalLoginCompanionServer(
    private val scope: CoroutineScope,
    private val language: AppLanguage = AppLanguage.INDONESIAN,
    private val onInput: suspend (CompanionInputPayload) -> Unit,
) {
    val sessionToken: String = generateSessionToken()
    private val basePairPin: Int = generatePairPin()
    private val failedPairAttempts = AtomicInteger(0)

    @Volatile
    private var activePairPin: Int = basePairPin

    @Volatile
    var pairingCode: String? = null
        private set

    @Volatile
    var localLoginUrl: String? = null
        private set

    @Volatile
    var qrPortalUrl: String? = null
        private set

    @Volatile
    var isCompletedOnTv: Boolean = false
        private set

    @Volatile
    var isVerifyingOnTv: Boolean = false
        private set

    @Volatile
    var completedAccountName: String = ""
        private set

    @Volatile
    var lastErrorMessage: String = ""

    @Volatile
    var lastClientSeenAtMs: Long = 0L
        private set

    val isPhoneConnected: Boolean
        get() = lastClientSeenAtMs > 0L && (System.currentTimeMillis() - lastClientSeenAtMs) <= PHONE_CONNECTED_WINDOW_MS

    @Volatile
    var currentStep: String = STEP_EMAIL

    @Volatile
    var currentHost: String = "accounts.google.com"

    @Volatile
    var lastStatusHint: String =
        pickLocalized(
            language = language,
            id = "Langkah 1: Masukkan Email Google Anda di kartu pertama.",
            ja = "ステップ 1：最初のカードに Google メールアドレスを入力してください。",
            en = "Step 1: Enter your Google Email in the first card.",
        )

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var acceptJob: Job? = null

    fun markVerifying() {
        isVerifyingOnTv = true
        lastErrorMessage = ""
        currentStep = STEP_VERIFYING
        lastStatusHint =
            pickLocalized(
                language = language,
                id = "Memverifikasi akun YouTube Music di TV…",
                ja = "テレビで YouTube Music アカウントを確認しています…",
                en = "Verifying your YouTube Music account on the TV…",
            )
    }

    fun markVerificationFailed(reason: String) {
        isVerifyingOnTv = false
        isCompletedOnTv = false
        lastErrorMessage =
            reason.ifBlank {
                pickLocalized(
                    language = language,
                    id = "Verifikasi akun YouTube Music gagal. Silakan coba lagi.",
                    ja = "YouTube Music アカウントの確認に失敗しました。もう一度お試しください。",
                    en = "YouTube Music account verification failed. Please try again.",
                )
            }
        currentStep = STEP_EMAIL
        lastStatusHint = lastErrorMessage
    }

    fun markCompleted(accountName: String = "") {
        isVerifyingOnTv = false
        isCompletedOnTv = true
        lastErrorMessage = ""
        currentStep = STEP_DONE
        if (accountName.isNotBlank()) {
            completedAccountName = accountName
        }
        lastStatusHint =
            pickLocalized(
                language = language,
                id = "Login selesai! Akun YouTube Music sudah aktif di TV.",
                ja = "ログイン完了！テレビで YouTube Music アカウントが有効になりました。",
                en = "Sign-in complete! Your YouTube Music account is active on the TV.",
            )
    }

    fun start(): String? {
        if (serverSocket != null) return localLoginUrl ?: buildLoginUrl(serverSocket!!.localPort)
        val lanIp = detectLanIpv4Address() ?: return null
        val socket =
            runCatching {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(DEFAULT_PAIRING_PORT))
                }
            }.getOrNull()
                ?: runCatching { ServerSocket(0) }.getOrNull()
                ?: return null
        serverSocket = socket
        val resolvedPort = socket.localPort
        val encoded = encodePairingCode(lanIp, resolvedPort, basePairPin)
        activePairPin =
            if (encoded != null && encoded.replace("-", "").length == 12) {
                basePairPin and 0x3FF
            } else {
                basePairPin
            }
        pairingCode = encoded
        val url = "http://$lanIp:$resolvedPort/l?t=$sessionToken"
        localLoginUrl = url
        qrPortalUrl = buildPortalQrUrl(encoded) ?: url

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
        return url
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private fun buildLoginUrl(port: Int): String? {
        val ip = detectLanIpv4Address() ?: return null
        return "http://$ip:$port/l?t=$sessionToken"
    }

    internal fun buildStatusJson(): String {
        val resolvedStep =
            when {
                isCompletedOnTv -> STEP_DONE
                isVerifyingOnTv -> STEP_VERIFYING
                else -> currentStep
            }
        return buildString {
            append('{')
            append("\"step\":").append(escapeJsonString(resolvedStep)).append(',')
            append("\"host\":").append(escapeJsonString(currentHost)).append(',')
            append("\"hint\":").append(escapeJsonString(lastStatusHint)).append(',')
            append("\"error\":").append(escapeJsonString(lastErrorMessage)).append(',')
            append("\"verifying\":").append(isVerifyingOnTv).append(',')
            append("\"completed\":").append(isCompletedOnTv).append(',')
            append("\"accountName\":").append(escapeJsonString(completedAccountName))
            append('}')
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

            val request = readHttpRequest(input) ?: run {
                val msg =
                    pickLocalized(
                        language = language,
                        id = "Permintaan tidak valid",
                        ja = "無効なリクエストです",
                        en = "Invalid request",
                    )
                writeResponse(output, 400, "text/plain; charset=utf-8", msg)
                return
            }

            val queryParams = parseQueryParams(request.query)
            if (request.method == "GET" && request.path == "/p") {
                if (failedPairAttempts.get() >= MAX_FAILED_PAIR_ATTEMPTS) {
                    writeResponse(output, 403, "text/plain; charset=utf-8", "Too many attempts")
                    return
                }
                val rawPin = queryParams["k"]?.trim().orEmpty()
                val submittedPin = rawPin.toIntOrNull(16) ?: rawPin.toIntOrNull()
                if (submittedPin == activePairPin) {
                    lastClientSeenAtMs = System.currentTimeMillis()
                    writeResponse(
                        output = output,
                        status = 302,
                        contentType = "text/plain; charset=utf-8",
                        body = "Redirecting...",
                        extraHeaders = mapOf("Location" to "/l?t=$sessionToken"),
                    )
                } else {
                    failedPairAttempts.incrementAndGet()
                    val msg =
                        pickLocalized(
                            language = language,
                            id = "Kode TV tidak cocok atau sudah kedaluwarsa",
                            ja = "TV コードが一致しないか、有効期限が切れています",
                            en = "TV code does not match or has expired",
                        )
                    writeResponse(output, 403, "text/plain; charset=utf-8", msg)
                }
                return
            }

            if (queryParams["t"] != sessionToken) {
                val msg =
                    pickLocalized(
                        language = language,
                        id = "Sesi QR tidak cocok atau sudah kedaluwarsa",
                        ja = "QR セッションが一致しないか、有効期限が切れています",
                        en = "QR session does not match or has expired",
                    )
                writeResponse(output, 403, "text/plain; charset=utf-8", msg)
                return
            }

            lastClientSeenAtMs = System.currentTimeMillis()

            when {
                request.method == "GET" && (request.path == "/l" || request.path == "/login") -> {
                    writeResponse(
                        output,
                        200,
                        "text/html; charset=utf-8",
                        buildMobileHtml(
                            token = sessionToken,
                            statusJson = buildStatusJson(),
                            language = language,
                        ),
                    )
                }
                request.method == "GET" && request.path == "/status" -> {
                    writeResponse(output, 200, "application/json; charset=utf-8", buildStatusJson())
                }
                request.method == "POST" && request.path == "/submit" -> {
                    val form = parseQueryParams(request.body)
                    val action =
                        when (form["action"]) {
                            "email" -> CompanionAction.FILL_EMAIL
                            "password" -> CompanionAction.FILL_PASSWORD
                            "text" -> CompanionAction.FILL_TEXT
                            "next" -> CompanionAction.CLICK_NEXT
                            "prev_focus" -> CompanionAction.FOCUS_PREV
                            "next_focus" -> CompanionAction.FOCUS_NEXT
                            "reload" -> CompanionAction.RELOAD_PAGE
                            "cookie" -> CompanionAction.SUBMIT_COOKIE
                            else -> null
                        }
                    val value = form["value"].orEmpty()
                    val isAjax = form["ajax"] == "1"
                    if (action == null) {
                        val unknownMsg =
                            pickLocalized(
                                language = language,
                                id = "Aksi tidak dikenali",
                                ja = "不明な操作です",
                                en = "Unknown action",
                            )
                        if (isAjax) {
                            writeResponse(output, 400, "application/json; charset=utf-8", """{"ok":false,"message":${escapeJsonString(unknownMsg)}}""")
                        } else {
                            writeResponse(
                                output,
                                400,
                                "text/html; charset=utf-8",
                                buildMobileHtml(
                                    token = sessionToken,
                                    statusMessage = "$unknownMsg.",
                                    isError = true,
                                    statusJson = buildStatusJson(),
                                    language = language,
                                ),
                            )
                        }
                        return
                    }
                    if (action == CompanionAction.SUBMIT_COOKIE && !hasValidSapisidCookie(value)) {
                        val err =
                            pickLocalized(
                                language = language,
                                id = "Cookie tidak valid: pastikan menyertakan SAPISID atau __Secure-3PAPISID dari music.youtube.com.",
                                ja = "無効な Cookie です：music.youtube.com の SAPISID または __Secure-3PAPISID が含まれていることを確認してください。",
                                en = "Invalid cookie: make sure it includes SAPISID or __Secure-3PAPISID from music.youtube.com.",
                            )
                        if (isAjax) {
                            writeResponse(output, 400, "application/json; charset=utf-8", """{"ok":false,"message":${escapeJsonString(err)}}""")
                        } else {
                            writeResponse(
                                output,
                                400,
                                "text/html; charset=utf-8",
                                buildMobileHtml(
                                    token = sessionToken,
                                    statusMessage = err,
                                    isError = true,
                                    statusJson = buildStatusJson(),
                                    language = language,
                                ),
                            )
                        }
                        return
                    }
                    withContext(Dispatchers.Main) {
                        onInput(CompanionInputPayload(action = action, value = value))
                    }
                    val feedback =
                        when (language) {
                            AppLanguage.INDONESIAN ->
                                when (action) {
                                    CompanionAction.FILL_EMAIL -> "Email dikirim ke layar TV — menunggu halaman Sandi."
                                    CompanionAction.FILL_PASSWORD -> "Sandi dikirim ke layar TV — cek verifikasi jika diminta Google."
                                    CompanionAction.FILL_TEXT -> "Teks/kode OTP dikirim ke kolom aktif TV."
                                    CompanionAction.CLICK_NEXT -> "Tombol Lanjut/Masuk ditekan di layar TV."
                                    CompanionAction.FOCUS_PREV -> "Fokus digeser ke elemen sebelumnya di TV."
                                    CompanionAction.FOCUS_NEXT -> "Fokus digeser ke elemen berikutnya di TV."
                                    CompanionAction.RELOAD_PAGE -> "Halaman login TV sedang dimuat ulang."
                                    CompanionAction.SUBMIT_COOKIE -> "Cookie sesi dikirim ke TV — memverifikasi akun YouTube Music."
                                }
                            AppLanguage.JAPANESE ->
                                when (action) {
                                    CompanionAction.FILL_EMAIL -> "メールアドレスをテレビへ送信しました — パスワード画面を待機中です。"
                                    CompanionAction.FILL_PASSWORD -> "パスワードをテレビへ送信しました — 認証を求められた場合は確認してください。"
                                    CompanionAction.FILL_TEXT -> "OTP コード／テキストをテレビの入力欄へ送信しました。"
                                    CompanionAction.CLICK_NEXT -> "テレビ画面の「次へ／ログイン」ボタンを押しました。"
                                    CompanionAction.FOCUS_PREV -> "テレビのフォーカスを前の項目へ移動しました。"
                                    CompanionAction.FOCUS_NEXT -> "テレビのフォーカスを次の項目へ移動しました。"
                                    CompanionAction.RELOAD_PAGE -> "テレビのログイン画面を再読み込みしています。"
                                    CompanionAction.SUBMIT_COOKIE -> "セッション Cookie をテレビへ送信しました — YouTube Music アカウントを確認中です。"
                                }
                            else ->
                                when (action) {
                                    CompanionAction.FILL_EMAIL -> "Email sent to TV — waiting for Password page."
                                    CompanionAction.FILL_PASSWORD -> "Password sent to TV — check 2FA verification if prompted."
                                    CompanionAction.FILL_TEXT -> "Text/OTP code sent to active TV input."
                                    CompanionAction.CLICK_NEXT -> "Next/Sign-in button clicked on TV."
                                    CompanionAction.FOCUS_PREV -> "Focus moved to previous element on TV."
                                    CompanionAction.FOCUS_NEXT -> "Focus moved to next element on TV."
                                    CompanionAction.RELOAD_PAGE -> "Reloading TV sign-in page."
                                    CompanionAction.SUBMIT_COOKIE -> "Session cookie sent to TV — verifying YouTube Music account."
                                }
                        }
                    if (isAjax) {
                        writeResponse(
                            output,
                            200,
                            "application/json; charset=utf-8",
                            """{"ok":true,"step":${escapeJsonString(currentStep)},"hint":${escapeJsonString(lastStatusHint)},"message":${escapeJsonString(feedback)}}""",
                        )
                    } else {
                        writeResponse(
                            output,
                            200,
                            "text/html; charset=utf-8",
                            buildMobileHtml(
                                token = sessionToken,
                                statusMessage = feedback,
                                statusJson = buildStatusJson(),
                                language = language,
                            ),
                        )
                    }
                }
                else -> {
                    val notFound =
                        pickLocalized(
                            language = language,
                            id = "Tidak ditemukan",
                            ja = "見つかりません",
                            en = "Not found",
                        )
                    writeResponse(output, 404, "text/plain; charset=utf-8", notFound)
                }
            }
        }
    }

    private data class ParsedHttpRequest(
        val method: String,
        val path: String,
        val query: String,
        val body: String,
    )

    private fun readHttpRequest(input: BufferedInputStream): ParsedHttpRequest? {
        val headerBytes = ByteArrayOutputStream()
        var state = 0
        while (headerBytes.size() < MAX_HEADER_BYTES) {
            val b = input.read()
            if (b == -1) return null
            headerBytes.write(b)
            state =
                when {
                    (state == 0 || state == 2) && b == '\r'.code -> state + 1
                    (state == 1 || state == 3) && b == '\n'.code -> state + 1
                    else -> 0
                }
            if (state == 4) break
        }
        val headerText = headerBytes.toString(StandardCharsets.UTF_8.name())
        val lines = headerText.split("\r\n")
        val requestLine = lines.firstOrNull()?.split(" ") ?: return null
        if (requestLine.size < 2) return null
        val method = requestLine[0].uppercase()
        val rawTarget = requestLine[1]
        val qIdx = rawTarget.indexOf('?')
        val path = if (qIdx >= 0) rawTarget.substring(0, qIdx) else rawTarget
        val query = if (qIdx >= 0) rawTarget.substring(qIdx + 1) else ""

        var contentLength = 0
        for (line in lines.drop(1)) {
            if (line.startsWith("Content-Length:", ignoreCase = true)) {
                contentLength = line.substringAfter(':').trim().toIntOrNull()?.coerceIn(0, MAX_BODY_BYTES) ?: 0
            }
        }
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
        return ParsedHttpRequest(method = method, path = path, query = query, body = body)
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
                    val key = urlDecode(pair.substring(0, eq))
                    val value = urlDecode(pair.substring(eq + 1))
                    key to value
                }
            }.toMap()
    }

    private fun urlDecode(value: String): String =
        runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrDefault(value)

    private fun writeResponse(
        output: java.io.OutputStream,
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
        const val STEP_EMAIL = "email"
        const val STEP_PASSWORD = "password"
        const val STEP_VERIFY = "verify"
        const val STEP_VERIFYING = "verifying"
        const val STEP_DONE = "done"
        const val DEFAULT_PAIRING_PORT = 4580
        const val PAIRING_PORTAL_DISPLAY_URL = "kh1zz.github.io/Alunara/login"

        private const val PHONE_CONNECTED_WINDOW_MS = 6_000L
        private const val SOCKET_TIMEOUT_MS = 5000
        private const val MAX_HEADER_BYTES = 8192
        private const val MAX_BODY_BYTES = 16384
        private const val MAX_FAILED_PAIR_ATTEMPTS = 8
        private const val TOKEN_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val PAIR_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

        internal fun buildPortalQrUrl(pairingCode: String?): String? =
            pairingCode?.takeIf { it.isNotBlank() }?.let { "https://$PAIRING_PORTAL_DISPLAY_URL/?c=$it" }

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

        fun generateSessionToken(): String {
            val rng = SecureRandom()
            return buildString(6) {
                repeat(6) {
                    append(TOKEN_CHARS[rng.nextInt(TOKEN_CHARS.length)])
                }
            }
        }

        private fun generatePairPin(): Int = SecureRandom().nextInt(4096)

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
                port == DEFAULT_PAIRING_PORT && o0 == 192 && o1 == 168 -> {
                    val packed =
                        ((o2.toLong() and 0xFFL) shl 20) or
                            ((o3.toLong() and 0xFFL) shl 12) or
                            (pin.toLong() and 0xFFFL)
                    val chars = encodeBase32(packed, 6)
                    "${chars.substring(0, 4)}-${chars.substring(4)}"
                }
                port == DEFAULT_PAIRING_PORT && ((o0 == 10 && o1 in 0..15) || (o0 == 172 && o1 in 16..31)) -> {
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

        internal fun decodePairingCode(code: String): DecodedPairingTarget? {
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
                    DecodedPairingTarget("192.168.$o2.$o3", DEFAULT_PAIRING_PORT, pin)
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
                    DecodedPairingTarget("$o0.$o1.$o2.$o3", DEFAULT_PAIRING_PORT, pin)
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
                    DecodedPairingTarget("$a0.$a1.$a2.$a3", port, pin)
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

        fun hasValidSapisidCookie(cookie: String): Boolean =
            cookie.contains("SAPISID=") ||
                cookie.contains("__Secure-3PAPISID=") ||
                cookie.contains("__Secure-1PAPISID=")

        fun detectLanIpv4Address(): String? =
            runCatching {
                val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                val candidates =
                    interfaces
                        .filter { !it.isLoopback && it.isUp }
                        .flatMap { it.inetAddresses.toList() }
                        .filterIsInstance<Inet4Address>()
                        .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                candidates.firstOrNull { it.isSiteLocalAddress }?.hostAddress
                    ?: candidates.firstOrNull()?.hostAddress
            }.getOrNull()

        private fun escapeHtml(raw: String): String =
            raw
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")

        private fun escapeJsonString(raw: String): String =
            buildString {
                append('"')
                for (ch in raw) {
                    when (ch) {
                        '"' -> append("\\\"")
                        '\\' -> append("\\\\")
                        '\n' -> append("\\n")
                        '\r' -> append("\\r")
                        '\t' -> append("\\t")
                        else -> append(ch)
                    }
                }
                append('"')
            }

        private fun pickLocalized(
            language: AppLanguage,
            id: String,
            ja: String,
            en: String,
        ): String =
            when (language) {
                AppLanguage.INDONESIAN -> id
                AppLanguage.JAPANESE -> ja
                else -> en
            }

        internal fun buildMobileHtml(
            token: String,
            statusMessage: String? = null,
            isError: Boolean = false,
            statusJson: String? = null,
            language: AppLanguage = AppLanguage.INDONESIAN,
        ): String {
            fun t(
                id: String,
                ja: String,
                en: String,
            ): String = pickLocalized(language = language, id = id, ja = ja, en = en)

            val resolvedStatusJson =
                statusJson
                    ?: when (language) {
                        AppLanguage.INDONESIAN ->
                            """{"step":"email","host":"accounts.google.com","hint":"Langkah 1: Masukkan Email Google Anda","completed":false,"accountName":""}"""
                        AppLanguage.JAPANESE ->
                            """{"step":"email","host":"accounts.google.com","hint":"ステップ 1：Google メールアドレスを入力してください","completed":false,"accountName":""}"""
                        else ->
                            """{"step":"email","host":"accounts.google.com","hint":"Step 1: Enter your Google Email","completed":false,"accountName":""}"""
                    }
            val bannerHtml =
                if (!statusMessage.isNullOrBlank()) {
                    val cls = if (isError) "banner err" else "banner ok"
                    """<div id="liveBanner" class="$cls">${escapeHtml(statusMessage)}</div>"""
                } else {
                    """<div id="liveBanner" class="banner" style="display:none;"></div>"""
                }

            val htmlLang = t(id = "id", ja = "ja", en = "en")
            val pageTitle =
                t(
                    id = "Alunara TV — Wizard Masuk Akun",
                    ja = "Alunara TV — アカウントログインウィザード",
                    en = "Alunara TV — Account Sign-In Wizard",
                )
            val heroTitle =
                t(
                    id = "Alunara TV · Wizard Login",
                    ja = "Alunara TV · ログインウィザード",
                    en = "Alunara TV · Login Wizard",
                )
            val heroDesc =
                t(
                    id = "Ketik email, sandi, dan verifikasi langsung dari keyboard HP Anda — teks otomatis masuk ke layar TV melalui Wi-Fi lokal (tidak disimpan atau dikirim ke pihak lain).",
                    ja = "メールアドレス・パスワード・2段階認証コードをスマホのキーボードから直接入力できます — ローカル Wi-Fi 経由でテレビ画面へ即時送信されます（保存や外部送信は一切行われません）。",
                    en = "Type your email, password, and 2FA code directly from your phone keyboard — inputs are sent over local Wi-Fi straight to your TV (never stored or shared).",
                )
            val whyTitle =
                t(
                    id = "Kenapa tidak memakai kode TV seperti SmartTube?",
                    ja = "SmartTube のような TV コードを使わないのはなぜですか？",
                    en = "Why doesn't this use a TV code like SmartTube?",
                )
            val whyDesc =
                t(
                    id = "SmartTube hanya memakai API video YouTube biasa. YouTube Music memakai sesi web Google (<code>music.youtube.com</code>), sehingga login tercepat yang valid adalah mengisi WebView TV lewat HP ini.",
                    ja = "SmartTube は通常の YouTube 動画 API のみを使用します。YouTube Music は Google の Web セッション（<code>music.youtube.com</code>）が必要なため、スマホからテレビの WebView に入力する方法が最も速く確実です。",
                    en = "SmartTube only uses standard YouTube video APIs. YouTube Music requires a Google web session (<code>music.youtube.com</code>), so filling the TV WebView from your phone is the fastest valid sign-in method.",
                )
            val pillEmail = t(id = "1. Email", ja = "1. メール", en = "1. Email")
            val pillPassword = t(id = "2. Sandi", ja = "2. パスワード", en = "2. Password")
            val pillVerify = t(id = "3. 2FA", ja = "3. 2段階認証", en = "3. 2FA")
            val pillDone = t(id = "4. Masuk", ja = "4. 完了", en = "4. Done")
            val connectingHint =
                t(
                    id = "Menghubungkan status ke layar TV…",
                    ja = "テレビの状態に接続しています…",
                    en = "Connecting to TV status…",
                )

            val step1Title = t(id = "Langkah 1 · Email Google", ja = "ステップ 1 · Google メール", en = "Step 1 · Google Email")
            val step1Badge = t(id = "Langkah 1", ja = "ステップ 1", en = "Step 1")
            val step1Desc =
                t(
                    id = "Isi alamat Gmail akun YouTube Music Anda, lalu tekan tombol untuk mengisi &amp; lanjut otomatis di TV.",
                    ja = "YouTube Music に使う Google メールアドレスを入力し、ボタンを押してテレビ側を自動入力・続行してください。",
                    en = "Enter the Google email for your YouTube Music account, then tap the button to fill &amp; continue on your TV.",
                )
            val emailLabel = t(id = "Email / Nomor HP", ja = "メールアドレス / 電話番号", en = "Email / Phone")
            val emailPlaceholder = t(id = "nama@gmail.com", ja = "name@gmail.com", en = "name@gmail.com")
            val emailSubmit = t(id = "Kirim Email &amp; Lanjut", ja = "メールを送信して次へ", en = "Send Email &amp; Continue")

            val step2Title = t(id = "Langkah 2 · Sandi Akun Google", ja = "ステップ 2 · Google パスワード", en = "Step 2 · Google Password")
            val step2Badge = t(id = "Langkah 2", ja = "ステップ 2", en = "Step 2")
            val step2Desc =
                t(
                    id = "Setelah layar TV berpindah ke halaman sandi, isi kata sandi Anda di sini (bisa pakai Password Manager HP).",
                    ja = "テレビ画面がパスワード入力に切り替わったら、こちらに入力してください（スマホのパスワードマネージャーも使えます）。",
                    en = "Once the TV advances to the password page, enter your password here (phone Password Managers work too).",
                )
            val passwordLabel = t(id = "Kata Sandi", ja = "パスワード", en = "Password")
            val passwordPlaceholder = t(id = "Masukkan kata sandi Google", ja = "Google パスワードを入力", en = "Enter your Google password")
            val passwordSubmit = t(id = "Kirim Sandi &amp; Masuk", ja = "パスワードを送信してログイン", en = "Send Password &amp; Sign In")

            val step3Title = t(id = "Langkah 3 · Verifikasi 2-Langkah (2FA)", ja = "ステップ 3 · 2段階認証 (2FA)", en = "Step 3 · 2-Step Verification (2FA)")
            val step3Badge = t(id = "Langkah 3", ja = "ステップ 3", en = "Step 3")
            val step3Desc =
                t(
                    id = """Jika muncul notifikasi <strong>Google Prompt ("Ya, itu saya")</strong> di HP Anda, cukup ketuk konfirmasi di HP. Bila Google meminta kode angka/SMS di TV, ketik kodenya di bawah ini:""",
                    ja = """スマホに <strong>Google からの通知（「はい、私です」）</strong>が届いた場合はそのままスマホで承認してください。テレビ側で数字・SMS コードを求められた場合は以下に入力してください：""",
                    en = """If a <strong>Google Prompt ("Yes, it's me")</strong> pops up on your phone, simply approve it there. If Google asks for a numeric/SMS code on the TV, type it below:""",
                )
            val otpLabel = t(id = "Kode OTP / Teks di Kolom Aktif TV", ja = "OTP コード / テレビ側の入力文字", en = "OTP Code / Text for Active TV Field")
            val otpPlaceholder = t(id = "Contoh: 123456", ja = "例：123456", en = "Example: 123456")
            val otpSubmit = t(id = "Kirim Kode ke TV", ja = "テレビにコードを送信", en = "Send Code to TV")

            val remoteTitle = t(id = "Remote Kontrol Halaman TV", ja = "テレビ画面のリモート操作", en = "TV Page Remote Controls")
            val remoteBadge = t(id = "Opsional", ja = "任意", en = "Optional")
            val remoteDesc =
                t(
                    id = "Geser sorotan tombol/tautan atau klik tombol Berikutnya di WebView TV tanpa mengambil remote fisik.",
                    ja = "テレビのリモコンを手に取らなくても、WebView 内のフォーカス移動や「次へ」のクリックができます。",
                    en = "Move highlight focus or click Next inside the TV WebView without picking up the physical remote.",
                )
            val remoteFocusUp = t(id = "▲ Fokus Atas", ja = "▲ 上へ移動", en = "▲ Focus Up")
            val remoteFocusDown = t(id = "▼ Fokus Bawah", ja = "▼ 下へ移動", en = "▼ Focus Down")
            val remoteClickNext = t(id = "⏎ Klik Aktif/Lanjut", ja = "⏎ 選択項目 / 次へ", en = "⏎ Click Active/Next")
            val remoteReload = t(id = "↻ Muat Ulang TV", ja = "↻ テレビ再読み込み", en = "↻ Reload TV")

            val cookieSummary =
                t(
                    id = "Mode Lanjutan · Tempel Cookie music.youtube.com Langsung",
                    ja = "詳細モード · music.youtube.com の Cookie を直接貼り付け",
                    en = "Advanced Mode · Paste music.youtube.com Cookie Directly",
                )
            val cookieDesc =
                t(
                    id = "Jika Anda sudah login di browser HP/PC, tempel cookie dari <code>music.youtube.com</code> (harus memuat <code>SAPISID</code> / <code>__Secure-3PAPISID</code>) untuk masuk instan dalam 1 klik:",
                    ja = "スマホや PC のブラウザですでにログイン済みの場合は、<code>music.youtube.com</code> の Cookie（<code>SAPISID</code> または <code>__Secure-3PAPISID</code> を含むもの）を貼り付けてワンクリックでログインできます：",
                    en = "If you are already signed in on a phone/PC browser, paste the cookie from <code>music.youtube.com</code> (must contain <code>SAPISID</code> / <code>__Secure-3PAPISID</code>) for instant 1-click sign-in:",
                )
            val cookiePlaceholder =
                t(
                    id = "Tempel string cookie music.youtube.com di sini...",
                    ja = "music.youtube.com の Cookie 文字列をここに貼り付け…",
                    en = "Paste music.youtube.com cookie string here...",
                )
            val cookieSubmit = t(id = "Masuk dengan Cookie", ja = "Cookie でログイン", en = "Sign In with Cookie")

            val doneModalTitle = t(id = "Login Berhasil ✓", ja = "ログイン完了 ✓", en = "Sign-In Complete ✓")
            val doneModalDesc =
                t(
                    id = "Akun YouTube Music Anda sudah terhubung di Alunara TV. Anda sekarang dapat menutup halaman ini di HP.",
                    ja = "YouTube Music アカウントが Alunara TV に連携されました。スマホのこのページは閉じて構いません。",
                    en = "Your YouTube Music account is now active on Alunara TV. You may close this page on your phone.",
                )
            val doneModalClose = t(id = "Tutup Tampilan", ja = "閉じる", en = "Dismiss")
            val jsDoneAccountPrefix = escapeJsonString(t(id = "Akun terhubung: ", ja = "接続済みアカウント：", en = "Connected account: "))

            val jsBadgeDone = escapeJsonString(t(id = "Selesai ✓", ja = "完了 ✓", en = "Done ✓"))
            val jsBadgeActive = escapeJsonString(t(id = "Aktif di TV", ja = "テレビで表示中", en = "Active on TV"))
            val jsStepPrefix = escapeJsonString(t(id = "Langkah ", ja = "ステップ ", en = "Step "))
            val jsSentFallback = escapeJsonString(t(id = "Terkirim ke TV", ja = "テレビへ送信しました", en = "Sent to TV"))
            val jsNetworkError =
                escapeJsonString(
                    t(
                        id = "Gagal menghubungi layar TV di Wi-Fi lokal.",
                        ja = "ローカル Wi-Fi 上のテレビに接続できませんでした。",
                        en = "Could not reach the TV on local Wi-Fi.",
                    ),
                )

            return """
                <!DOCTYPE html>
                <html lang="$htmlLang">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width,initial-scale=1">
                  <title>$pageTitle</title>
                  <style>
                    :root { color-scheme: dark; }
                    body { margin:0; padding:18px 14px 36px; font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif; background:#101813; color:#E3EEE5; }
                    .wrap { max-width:460px; margin:0 auto; }
                    .hero { background:#1D2B22; border:1px solid #355442; border-radius:16px; padding:16px; margin-bottom:14px; }
                    .hero h1 { margin:0 0 6px; font-size:20px; color:#C8F0DA; }
                    .hero p { margin:0; font-size:13px; color:#B2C4B7; line-height:1.5; }
                    .why { margin-top:10px; padding:10px 12px; border-radius:10px; background:#142119; border:1px solid #26382D; font-size:12px; color:#9AB5A5; line-height:1.45; }
                    .steps { display:grid; grid-template-columns:repeat(4,1fr); gap:6px; margin:12px 0 6px; }
                    .step { padding:8px 6px; border-radius:10px; text-align:center; background:#142119; border:1px solid #26382D; font-size:11px; font-weight:600; color:#7E9486; }
                    .step.active { background:#2E493A; border-color:#B5E6CC; color:#E3EEE5; }
                    .step.done { background:#1A3526; border-color:#85BDA0; color:#85BDA0; }
                    .hintLine { font-size:12.5px; color:#85BDA0; font-weight:600; margin-top:6px; }
                    .banner { padding:12px 14px; border-radius:12px; border:1px solid #355442; background:#163826; color:#E3EEE5; font-size:13.5px; font-weight:600; margin-bottom:14px; }
                    .banner.err { background:#3E2224; border-color:#F4B8BC; color:#F4B8BC; }
                    .card { background:#1D2B22; border:1px solid #26382D; border-radius:16px; padding:16px; margin-bottom:12px; transition:border-color 0.2s, opacity 0.2s; }
                    @media (prefers-reduced-motion: reduce) { .card { transition:none; } }
                    .card.current { border-color:#B5E6CC; box-shadow:0 0 0 1px rgba(181,230,204,0.35); opacity:1; }
                    .card.completed { opacity:0.68; border-color:#2C4637; }
                    .cardHead { display:flex; align-items:center; justify-content:space-between; margin-bottom:8px; gap:8px; }
                    .badge { font-size:11px; font-weight:700; padding:3px 8px; border-radius:999px; background:#26382D; color:#85BDA0; white-space:nowrap; }
                    .card.current .badge { background:#C8F0DA; color:#0B120E; }
                    .card.completed .badge { background:#1A3526; color:#85BDA0; }
                    h2 { margin:0; font-size:16px; color:#E3EEE5; }
                    .sub { margin:0 0 12px; font-size:12.5px; color:#B2C4B7; line-height:1.45; }
                    label { display:block; font-size:12.5px; font-weight:600; margin-bottom:6px; color:#B5E6CC; }
                    input, textarea { width:100%; box-sizing:border-box; padding:12px; border-radius:10px; border:1px solid #355442; background:#101813; color:#FFFFFF; font-size:15px; margin-bottom:10px; font-family:inherit; }
                    textarea { min-height:76px; font-size:12.5px; }
                    button { width:100%; padding:12px 14px; border:0; border-radius:10px; background:#C8F0DA; color:#0B120E; font-weight:700; font-size:14.5px; cursor:pointer; }
                    button.secondary { background:#26382D; color:#E3EEE5; border:1px solid #3D5B49; }
                    .remoteGrid { display:grid; grid-template-columns:1fr 1fr; gap:8px; margin-top:8px; }
                    details summary { cursor:pointer; font-size:13px; font-weight:600; color:#85BDA0; }
                    .modalOverlay { position:fixed; inset:0; background:rgba(10,16,12,0.86); display:none; align-items:center; justify-content:center; padding:18px; z-index:999; }
                    .modalOverlay.open { display:flex; }
                    .modalCard { width:100%; max-width:400px; background:#1D2B22; border:2px solid #B5E6CC; border-radius:18px; padding:24px 20px; text-align:center; box-shadow:0 24px 56px rgba(0,0,0,0.55); }
                    .modalIcon { width:56px; height:56px; margin:0 auto 12px; border-radius:999px; background:#163826; border:2px solid #85BDA0; color:#C8F0DA; font-size:28px; font-weight:700; display:flex; align-items:center; justify-content:center; }
                    .modalAccount { display:none; margin:0 0 12px; padding:8px 12px; border-radius:10px; background:#142119; border:1px solid #2C4637; color:#B5E6CC; font-size:13px; font-weight:600; }
                    .modalCard h2 { font-size:20px; color:#C8F0DA; margin:0 0 8px; }
                    .modalCard p { font-size:13.5px; color:#B2C4B7; line-height:1.5; margin:0 0 16px; }
                  </style>
                </head>
                <body>
                <div class="wrap">
                  <div class="hero">
                    <h1>$heroTitle</h1>
                    <p>$heroDesc</p>
                    <div class="why">
                      <strong>$whyTitle</strong><br>
                      $whyDesc
                    </div>
                    <div class="steps">
                      <div id="pill-email" class="step">$pillEmail</div>
                      <div id="pill-password" class="step">$pillPassword</div>
                      <div id="pill-verify" class="step">$pillVerify</div>
                      <div id="pill-done" class="step">$pillDone</div>
                    </div>
                    <div id="tvStepHint" class="hintLine" role="status" aria-live="polite">$connectingHint</div>
                  </div>

                  $bannerHtml

                  <div id="card-email" class="card">
                    <div class="cardHead">
                      <h2>$step1Title</h2>
                      <span id="badge-email" class="badge">$step1Badge</span>
                    </div>
                    <p class="sub">$step1Desc</p>
                    <form class="wizardForm" method="post" action="/submit?t=$token">
                      <input type="hidden" name="action" value="email">
                      <label for="emailInput">$emailLabel</label>
                      <input id="emailInput" name="value" type="email" autocomplete="username" placeholder="$emailPlaceholder" required>
                      <button type="submit">$emailSubmit</button>
                    </form>
                  </div>

                  <div id="card-password" class="card">
                    <div class="cardHead">
                      <h2>$step2Title</h2>
                      <span id="badge-password" class="badge">$step2Badge</span>
                    </div>
                    <p class="sub">$step2Desc</p>
                    <form class="wizardForm" method="post" action="/submit?t=$token">
                      <input type="hidden" name="action" value="password">
                      <label for="passwordInput">$passwordLabel</label>
                      <input id="passwordInput" name="value" type="password" autocomplete="current-password" placeholder="$passwordPlaceholder" required>
                      <button type="submit">$passwordSubmit</button>
                    </form>
                  </div>

                  <div id="card-verify" class="card">
                    <div class="cardHead">
                      <h2>$step3Title</h2>
                      <span id="badge-verify" class="badge">$step3Badge</span>
                    </div>
                    <p class="sub">$step3Desc</p>
                    <form class="wizardForm" method="post" action="/submit?t=$token">
                      <input type="hidden" name="action" value="text">
                      <label for="otpInput">$otpLabel</label>
                      <input id="otpInput" name="value" type="text" autocomplete="one-time-code" placeholder="$otpPlaceholder">
                      <button class="secondary" type="submit">$otpSubmit</button>
                    </form>
                  </div>

                  <div class="card">
                    <div class="cardHead">
                      <h2>$remoteTitle</h2>
                      <span class="badge">$remoteBadge</span>
                    </div>
                    <p class="sub">$remoteDesc</p>
                    <div class="remoteGrid">
                      <button type="button" class="secondary" onclick="sendRemote('prev_focus')">$remoteFocusUp</button>
                      <button type="button" class="secondary" onclick="sendRemote('next_focus')">$remoteFocusDown</button>
                      <button type="button" onclick="sendRemote('next')">$remoteClickNext</button>
                      <button type="button" class="secondary" onclick="sendRemote('reload')">$remoteReload</button>
                    </div>
                  </div>

                  <div class="card">
                    <details>
                      <summary>$cookieSummary</summary>
                      <p class="sub" style="margin-top:10px;">$cookieDesc</p>
                      <form class="wizardForm" method="post" action="/submit?t=$token">
                        <input type="hidden" name="action" value="cookie">
                        <label for="cookieInput">Cookie Header</label>
                        <textarea id="cookieInput" name="value" placeholder="$cookiePlaceholder" required></textarea>
                        <button class="secondary" type="submit">$cookieSubmit</button>
                      </form>
                    </details>
                  </div>
                  <div id="doneModal" class="modalOverlay" role="dialog" aria-modal="true" aria-labelledby="doneModalTitle">
                    <div class="modalCard">
                      <div class="modalIcon">✓</div>
                      <h2 id="doneModalTitle">$doneModalTitle</h2>
                      <div id="doneModalAccount" class="modalAccount"></div>
                      <p>$doneModalDesc</p>
                      <button type="button" onclick="dismissDoneModal()">$doneModalClose</button>
                    </div>
                  </div>
                </div>

                <script>
                  var token = ${escapeJsonString(token)};
                  var stepOrder = ['email', 'password', 'verify', 'done'];
                  var lastSyncedStep = null;
                  var lastErrorShown = '';
                  var doneModalShown = false;
                  var pollTimer = null;
                  var lblDone = $jsBadgeDone;
                  var lblActive = $jsBadgeActive;
                  var lblStepPrefix = $jsStepPrefix;
                  var lblSent = $jsSentFallback;
                  var lblNetErr = $jsNetworkError;
                  var lblDoneAccPrefix = $jsDoneAccountPrefix;
                  function showDoneModal(accountName) {
                    var modal = document.getElementById('doneModal');
                    var accEl = document.getElementById('doneModalAccount');
                    if (accEl) {
                      if (accountName) {
                        accEl.textContent = lblDoneAccPrefix + accountName;
                        accEl.style.display = 'block';
                      } else {
                        accEl.style.display = 'none';
                      }
                    }
                    if (modal) modal.classList.add('open');
                  }
                  function dismissDoneModal() {
                    var modal = document.getElementById('doneModal');
                    if (modal) modal.classList.remove('open');
                  }
                  function stopPolling() {
                    if (pollTimer !== null) {
                      clearInterval(pollTimer);
                      pollTimer = null;
                    }
                  }
                  function startPolling() {
                    if (doneModalShown || pollTimer !== null) return;
                    pollTimer = setInterval(pollStatus, 750);
                  }
                  function applyTvState(st) {
                    if (!st || !st.step) return;
                    var hintEl = document.getElementById('tvStepHint');
                    var hint = (st.hint || '') + (st.host ? ' · ' + st.host : '');
                    if (hintEl && hintEl.textContent !== hint) hintEl.textContent = hint;
                    var visualStep = (st.step === 'verifying') ? 'done' : st.step;
                    var activeIdx = stepOrder.indexOf(visualStep);
                    if (activeIdx < 0) activeIdx = 0;
                    stepOrder.forEach(function(name, i) {
                      var pill = document.getElementById('pill-' + name);
                      if (pill) {
                        pill.classList.remove('active', 'done');
                        if (i < activeIdx) pill.classList.add('done');
                        else if (i === activeIdx) pill.classList.add('active');
                        if (i === activeIdx) pill.setAttribute('aria-current', 'step');
                        else pill.removeAttribute('aria-current');
                      }
                      var card = document.getElementById('card-' + name);
                      if (card) {
                        card.classList.toggle('current', name === st.step);
                        card.classList.toggle('completed', i < activeIdx);
                      }
                      var badge = document.getElementById('badge-' + name);
                      if (badge) {
                        if (i < activeIdx) badge.textContent = lblDone;
                        else if (i === activeIdx) badge.textContent = lblActive;
                        else badge.textContent = lblStepPrefix + (i + 1);
                      }
                    });
                    if (st.error) {
                      if (st.error !== lastErrorShown) {
                        lastErrorShown = st.error;
                        showBanner(st.error, false);
                      }
                    } else if (lastErrorShown) {
                      lastErrorShown = '';
                    }
                    if (st.verifying === true || st.step === 'verifying') {
                      if (st.hint) showBanner(st.hint, true);
                    }
                    if (st.completed === true) {
                      showDoneModal(st.accountName || '');
                      doneModalShown = true;
                      stopPolling();
                    }
                    if (lastSyncedStep !== st.step) {
                      var isTransition = lastSyncedStep !== null;
                      lastSyncedStep = st.step;
                      var activeCard = document.getElementById('card-' + st.step);
                      if (activeCard && isTransition) {
                        try { activeCard.scrollIntoView({ behavior: 'auto', block: 'center' }); } catch (_) {}
                        var inputId = st.step === 'password' ? 'passwordInput' : (st.step === 'verify' ? 'otpInput' : 'emailInput');
                        var targetInput = document.getElementById(inputId);
                        if (targetInput && (st.step === 'password' || (st.step === 'verify' && (st.hint || '').toLowerCase().indexOf('otp') >= 0))) {
                          setTimeout(function() { try { targetInput.focus(); } catch (_) {} }, 220);
                        }
                      }
                    }
                  }
                  function showBanner(msg, ok) {
                    var b = document.getElementById('liveBanner');
                    if (!b) return;
                    b.textContent = msg;
                    b.className = 'banner ' + (ok ? 'ok' : 'err');
                    b.style.display = 'block';
                  }
                  function burstPoll() {
                    setTimeout(pollStatus, 250);
                    setTimeout(pollStatus, 650);
                    setTimeout(pollStatus, 1200);
                    setTimeout(pollStatus, 1900);
                  }
                  function sendRemote(actionName) {
                    fetch('/submit?t=' + encodeURIComponent(token), {
                      method: 'POST',
                      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                      body: 'action=' + encodeURIComponent(actionName) + '&ajax=1'
                    }).then(function(r) { return r.json(); })
                      .then(function(res) { showBanner(res.message || lblSent, !!res.ok); pollStatus(); burstPoll(); })
                      .catch(function() { showBanner(lblNetErr, false); });
                  }
                  Array.from(document.querySelectorAll('form.wizardForm')).forEach(function(form) {
                    form.addEventListener('submit', function(ev) {
                      ev.preventDefault();
                      var actionEl = form.querySelector('input[name="action"]');
                      var valueEl = form.querySelector('input[name="value"], textarea[name="value"]');
                      var btn = form.querySelector('button[type="submit"]');
                      var actionVal = actionEl ? actionEl.value : '';
                      var inputVal = valueEl ? valueEl.value : '';
                      if (btn) btn.disabled = true;
                      lastErrorShown = '';
                      fetch('/submit?t=' + encodeURIComponent(token), {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                        body: 'action=' + encodeURIComponent(actionVal) + '&value=' + encodeURIComponent(inputVal) + '&ajax=1'
                      }).then(function(r) { return r.json(); })
                        .then(function(res) {
                          if (btn) btn.disabled = false;
                          showBanner(res.message || lblSent, !!res.ok);
                          pollStatus();
                          burstPoll();
                        })
                        .catch(function() {
                          if (btn) btn.disabled = false;
                          showBanner(lblNetErr, false);
                        });
                    });
                  });
                  function pollStatus() {
                    if (doneModalShown) return;
                    fetch('/status?t=' + encodeURIComponent(token), { cache: 'no-store' })
                      .then(function(r) { return r.ok ? r.json() : null; })
                      .then(function(st) { if (st) applyTvState(st); })
                      .catch(function() {});
                  }
                  document.addEventListener('visibilitychange', function() {
                    if (document.visibilityState === 'visible') {
                      pollStatus();
                      startPolling();
                    } else {
                      stopPolling();
                    }
                  });
                  applyTvState($resolvedStatusJson);
                  startPolling();
                </script>
                </body>
                </html>
                """.trimIndent()
        }
    }
}
