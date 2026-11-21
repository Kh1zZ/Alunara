package com.alunara.app.ui.screens

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
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom

enum class CompanionAction {
    FILL_EMAIL,
    FILL_PASSWORD,
    FILL_TEXT,
    CLICK_NEXT,
}

data class CompanionInputPayload(
    val action: CompanionAction,
    val value: String = "",
)

/**
 * Ephemeral LAN-only HTTP server used during Google TV WebView sign-in so users can scan a QR code
 * with their phone, type credentials using their mobile keyboard/password manager, and push them
 * directly into the active TV WebView DOM in memory (zero disk persistence, zero logging).
 */
class LocalLoginCompanionServer(
    private val scope: CoroutineScope,
    private val onInput: suspend (CompanionInputPayload) -> Unit,
) {
    val sessionToken: String = generateSessionToken()

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var acceptJob: Job? = null

    fun start(): String? {
        if (serverSocket != null) return buildLoginUrl(serverSocket!!.localPort)
        val lanIp = detectLanIpv4Address() ?: return null
        val socket =
            runCatching { ServerSocket(0) }
                .getOrNull()
                ?: return null
        serverSocket = socket
        val url = "http://$lanIp:${socket.localPort}/l?t=$sessionToken"

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

    private suspend fun handleClient(socket: Socket) {
        socket.use { s ->
            s.soTimeout = SOCKET_TIMEOUT_MS
            val input = BufferedInputStream(s.getInputStream())
            val output = s.getOutputStream()

            val request = readHttpRequest(input) ?: run {
                writeResponse(output, 400, "text/plain; charset=utf-8", "Permintaan tidak valid")
                return
            }

            val queryParams = parseQueryParams(request.query)
            if (queryParams["t"] != sessionToken) {
                writeResponse(output, 403, "text/plain; charset=utf-8", "Sesi QR tidak cocok atau sudah kedaluwarsa")
                return
            }

            when {
                request.method == "GET" && (request.path == "/l" || request.path == "/login") -> {
                    writeResponse(output, 200, "text/html; charset=utf-8", buildMobileHtml(sessionToken))
                }
                request.method == "POST" && request.path == "/submit" -> {
                    val form = parseQueryParams(request.body)
                    val action =
                        when (form["action"]) {
                            "email" -> CompanionAction.FILL_EMAIL
                            "password" -> CompanionAction.FILL_PASSWORD
                            "text" -> CompanionAction.FILL_TEXT
                            "next" -> CompanionAction.CLICK_NEXT
                            else -> null
                        }
                    val value = form["value"].orEmpty()
                    if (action == null) {
                        writeResponse(output, 400, "text/html; charset=utf-8", buildMobileHtml(sessionToken, "Aksi tidak dikenali.", isError = true))
                        return
                    }
                    withContext(Dispatchers.Main) {
                        onInput(CompanionInputPayload(action = action, value = value))
                    }
                    val feedback =
                        when (action) {
                            CompanionAction.FILL_EMAIL -> "Email dikirim ke layar TV."
                            CompanionAction.FILL_PASSWORD -> "Sandi dikirim ke layar TV."
                            CompanionAction.FILL_TEXT -> "Teks dikirim ke kolom aktif TV."
                            CompanionAction.CLICK_NEXT -> "Tombol Berikutnya ditekan di TV."
                        }
                    writeResponse(output, 200, "text/html; charset=utf-8", buildMobileHtml(sessionToken, feedback))
                }
                else -> {
                    writeResponse(output, 404, "text/plain; charset=utf-8", "Tidak ditemukan")
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
    ) {
        val statusText =
            when (status) {
                200 -> "OK"
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
                append("Connection: close\r\n\r\n")
            }.toByteArray(StandardCharsets.US_ASCII)
        output.write(headers)
        output.write(bodyBytes)
        output.flush()
    }

    companion object {
        private const val SOCKET_TIMEOUT_MS = 5000
        private const val MAX_HEADER_BYTES = 8192
        private const val MAX_BODY_BYTES = 16384
        private const val TOKEN_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        fun generateSessionToken(): String {
            val rng = SecureRandom()
            return buildString(6) {
                repeat(6) {
                    append(TOKEN_CHARS[rng.nextInt(TOKEN_CHARS.length)])
                }
            }
        }

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

        private fun buildMobileHtml(
            token: String,
            statusMessage: String? = null,
            isError: Boolean = false,
        ): String {
            val bannerHtml =
                if (!statusMessage.isNullOrBlank()) {
                    val bg = if (isError) "#401A1A" else "#163826"
                    val border = if (isError) "#E57373" else "#50C885"
                    """<div class="banner" style="background:$bg;border-color:$border;">$statusMessage</div>"""
                } else {
                    ""
                }
            return """
                <!DOCTYPE html>
                <html lang="id">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
                  <title>Alunara TV — Input Login HP</title>
                  <style>
                    body { margin:0; padding:20px; font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif; background:#0B120E; color:#E8F3EC; }
                    .card { max-width:440px; margin:0 auto; background:#142119; border:1px solid #2A4333; border-radius:16px; padding:18px; margin-bottom:14px; }
                    h1 { margin:0 0 6px; font-size:20px; color:#C8F0DA; }
                    p { margin:0 0 12px; font-size:13px; color:#9AB5A5; line-height:1.45; }
                    label { display:block; font-size:13px; font-weight:600; margin-bottom:6px; color:#BFE4CE; }
                    input { width:100%; box-sizing:border-box; padding:12px; border-radius:10px; border:1px solid #355442; background:#0D1712; color:#FFFFFF; font-size:15px; margin-bottom:10px; }
                    button { width:100%; padding:12px 14px; border:0; border-radius:10px; background:#C8F0DA; color:#0B120E; font-weight:700; font-size:15px; cursor:pointer; }
                    button.secondary { background:#23382C; color:#E8F3EC; border:1px solid #385845; }
                    .banner { max-width:440px; margin:0 auto 14px; padding:12px 14px; border-radius:12px; border:1px solid; font-size:14px; font-weight:600; }
                  </style>
                </head>
                <body>
                  $bannerHtml
                  <div class="card">
                    <h1>Alunara TV Companion</h1>
                    <p>Isi email, sandi, atau kode verifikasi di HP, lalu tekan tombol di bawah untuk mengirim langsung ke layar WebView TV.</p>
                    <form method="post" action="/submit?t=$token">
                      <input type="hidden" name="action" value="email">
                      <label for="email">1. Email / No. HP Akun Google</label>
                      <input id="email" name="value" type="email" autocomplete="username" placeholder="nama@gmail.com" required>
                      <button type="submit">Kirim Email &amp; Berikutnya</button>
                    </form>
                  </div>
                  <div class="card">
                    <form method="post" action="/submit?t=$token">
                      <input type="hidden" name="action" value="password">
                      <label for="password">2. Sandi Akun Google</label>
                      <input id="password" name="value" type="password" autocomplete="current-password" placeholder="Masukkan sandi" required>
                      <button type="submit">Kirim Sandi &amp; Masuk</button>
                    </form>
                  </div>
                  <div class="card">
                    <form method="post" action="/submit?t=$token">
                      <input type="hidden" name="action" value="text">
                      <label for="code">3. Kode 2FA / Isi Kolom Aktif TV</label>
                      <input id="code" name="value" type="text" autocomplete="one-time-code" placeholder="Opsional jika diminta Google">
                      <button class="secondary" type="submit">Isi ke Kolom Aktif TV</button>
                    </form>
                    <form method="post" action="/submit?t=$token" style="margin-top:10px;">
                      <input type="hidden" name="action" value="next">
                      <button class="secondary" type="submit">Klik Berikutnya di TV</button>
                    </form>
                  </div>
                </body>
                </html>
                """.trimIndent()
        }
    }
}
