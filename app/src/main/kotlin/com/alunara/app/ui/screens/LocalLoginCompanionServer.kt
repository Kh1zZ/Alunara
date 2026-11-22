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
    private val onInput: suspend (CompanionInputPayload) -> Unit,
) {
    val sessionToken: String = generateSessionToken()

    @Volatile
    var currentStep: String = STEP_EMAIL

    @Volatile
    var currentHost: String = "accounts.google.com"

    @Volatile
    var lastStatusHint: String = "Langkah 1: Masukkan Email Google Anda di kartu pertama."

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

    internal fun buildStatusJson(): String =
        buildString {
            append('{')
            append("\"step\":").append(escapeJsonString(currentStep)).append(',')
            append("\"host\":").append(escapeJsonString(currentHost)).append(',')
            append("\"hint\":").append(escapeJsonString(lastStatusHint))
            append('}')
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
                    writeResponse(output, 200, "text/html; charset=utf-8", buildMobileHtml(sessionToken, statusJson = buildStatusJson()))
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
                        if (isAjax) {
                            writeResponse(output, 400, "application/json; charset=utf-8", """{"ok":false,"message":"Aksi tidak dikenali"}""")
                        } else {
                            writeResponse(output, 400, "text/html; charset=utf-8", buildMobileHtml(sessionToken, "Aksi tidak dikenali.", isError = true, statusJson = buildStatusJson()))
                        }
                        return
                    }
                    if (action == CompanionAction.SUBMIT_COOKIE && !hasValidSapisidCookie(value)) {
                        val err = "Cookie tidak valid: pastikan menyertakan SAPISID atau __Secure-3PAPISID dari music.youtube.com."
                        if (isAjax) {
                            writeResponse(output, 400, "application/json; charset=utf-8", """{"ok":false,"message":${escapeJsonString(err)}}""")
                        } else {
                            writeResponse(output, 400, "text/html; charset=utf-8", buildMobileHtml(sessionToken, err, isError = true, statusJson = buildStatusJson()))
                        }
                        return
                    }
                    withContext(Dispatchers.Main) {
                        onInput(CompanionInputPayload(action = action, value = value))
                    }
                    val feedback =
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
                    if (isAjax) {
                        writeResponse(output, 200, "application/json; charset=utf-8", """{"ok":true,"message":${escapeJsonString(feedback)}}""")
                    } else {
                        writeResponse(output, 200, "text/html; charset=utf-8", buildMobileHtml(sessionToken, feedback, statusJson = buildStatusJson()))
                    }
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
        const val STEP_EMAIL = "email"
        const val STEP_PASSWORD = "password"
        const val STEP_VERIFY = "verify"
        const val STEP_DONE = "done"

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

        internal fun buildMobileHtml(
            token: String,
            statusMessage: String? = null,
            isError: Boolean = false,
            statusJson: String = """{"step":"email","host":"accounts.google.com","hint":"Langkah 1: Masukkan Email Google Anda"}""",
        ): String {
            val bannerHtml =
                if (!statusMessage.isNullOrBlank()) {
                    val cls = if (isError) "banner err" else "banner ok"
                    """<div id="liveBanner" class="$cls">${escapeHtml(statusMessage)}</div>"""
                } else {
                    """<div id="liveBanner" class="banner" style="display:none;"></div>"""
                }
            return """
                <!DOCTYPE html>
                <html lang="id">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
                  <title>Alunara TV — Wizard Masuk Akun</title>
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
                    .card { background:#1D2B22; border:1px solid #26382D; border-radius:16px; padding:16px; margin-bottom:12px; transition:border-color 0.2s; }
                    .card.current { border-color:#B5E6CC; box-shadow:0 0 0 1px rgba(181,230,204,0.35); }
                    .cardHead { display:flex; align-items:center; justify-content:space-between; margin-bottom:8px; }
                    .badge { font-size:11px; font-weight:700; padding:3px 8px; border-radius:999px; background:#26382D; color:#85BDA0; }
                    .card.current .badge { background:#C8F0DA; color:#0B120E; }
                    h2 { margin:0; font-size:16px; color:#E3EEE5; }
                    .sub { margin:0 0 12px; font-size:12.5px; color:#B2C4B7; line-height:1.45; }
                    label { display:block; font-size:12.5px; font-weight:600; margin-bottom:6px; color:#B5E6CC; }
                    input, textarea { width:100%; box-sizing:border-box; padding:12px; border-radius:10px; border:1px solid #355442; background:#101813; color:#FFFFFF; font-size:15px; margin-bottom:10px; font-family:inherit; }
                    textarea { min-height:76px; font-size:12.5px; }
                    button { width:100%; padding:12px 14px; border:0; border-radius:10px; background:#C8F0DA; color:#0B120E; font-weight:700; font-size:14.5px; cursor:pointer; }
                    button.secondary { background:#26382D; color:#E3EEE5; border:1px solid #3D5B49; }
                    .remoteGrid { display:grid; grid-template-columns:1fr 1fr; gap:8px; margin-top:8px; }
                    details summary { cursor:pointer; font-size:13px; font-weight:600; color:#85BDA0; }
                  </style>
                </head>
                <body>
                <div class="wrap">
                  <div class="hero">
                    <h1>Alunara TV · Wizard Login</h1>
                    <p>Ketik email, sandi, dan verifikasi langsung dari keyboard HP Anda — teks otomatis masuk ke layar TV melalui Wi-Fi lokal (tidak disimpan atau dikirim ke pihak lain).</p>
                    <div class="why">
                      <strong>Kenapa tidak memakai kode TV seperti SmartTube?</strong><br>
                      SmartTube hanya memakai API video YouTube biasa. YouTube Music memakai sesi web Google (<code>music.youtube.com</code>), sehingga login tercepat yang valid adalah mengisi WebView TV lewat HP ini.
                    </div>
                    <div class="steps">
                      <div id="pill-email" class="step">1. Email</div>
                      <div id="pill-password" class="step">2. Sandi</div>
                      <div id="pill-verify" class="step">3. 2FA</div>
                      <div id="pill-done" class="step">4. Masuk</div>
                    </div>
                    <div id="tvStepHint" class="hintLine">Menghubungkan status ke layar TV…</div>
                  </div>

                  $bannerHtml

                  <div id="card-email" class="card">
                    <div class="cardHead">
                      <h2>Langkah 1 · Email Google</h2>
                      <span class="badge">Email / No. HP</span>
                    </div>
                    <p class="sub">Isi alamat Gmail akun YouTube Music Anda, lalu tekan tombol untuk mengisi &amp; lanjut otomatis di TV.</p>
                    <form method="post" action="/submit?t=$token">
                      <input type="hidden" name="action" value="email">
                      <label for="emailInput">Email / Nomor HP</label>
                      <input id="emailInput" name="value" type="email" autocomplete="username" placeholder="nama@gmail.com" required>
                      <button type="submit">Kirim Email &amp; Lanjut</button>
                    </form>
                  </div>

                  <div id="card-password" class="card">
                    <div class="cardHead">
                      <h2>Langkah 2 · Sandi Akun Google</h2>
                      <span class="badge">Sandi</span>
                    </div>
                    <p class="sub">Setelah layar TV berpindah ke halaman sandi, isi kata sandi Anda di sini (bisa pakai Password Manager HP).</p>
                    <form method="post" action="/submit?t=$token">
                      <input type="hidden" name="action" value="password">
                      <label for="passwordInput">Kata Sandi</label>
                      <input id="passwordInput" name="value" type="password" autocomplete="current-password" placeholder="Masukkan kata sandi Google" required>
                      <button type="submit">Kirim Sandi &amp; Masuk</button>
                    </form>
                  </div>

                  <div id="card-verify" class="card">
                    <div class="cardHead">
                      <h2>Langkah 3 · Verifikasi 2-Langkah (2FA)</h2>
                      <span class="badge">Prompt / OTP</span>
                    </div>
                    <p class="sub">Jika muncul notifikasi <strong>Google Prompt ("Ya, itu saya")</strong> di HP Anda, cukup ketuk konfirmasi di HP. Bila Google meminta kode angka/SMS di TV, ketik kodenya di bawah ini:</p>
                    <form method="post" action="/submit?t=$token">
                      <input type="hidden" name="action" value="text">
                      <label for="otpInput">Kode OTP / Teks di Kolom Aktif TV</label>
                      <input id="otpInput" name="value" type="text" autocomplete="one-time-code" placeholder="Contoh: 123456">
                      <button class="secondary" type="submit">Kirim Kode ke TV</button>
                    </form>
                  </div>

                  <div class="card">
                    <div class="cardHead">
                      <h2>Remote Kontrol Halaman TV</h2>
                      <span class="badge">Opsional</span>
                    </div>
                    <p class="sub">Geser sorotan tombol/link atau klik tombol Berikutnya di WebView TV tanpa mengambil remote fisik.</p>
                    <div class="remoteGrid">
                      <button type="button" class="secondary" onclick="sendRemote('prev_focus')">▲ Fokus Atas</button>
                      <button type="button" class="secondary" onclick="sendRemote('next_focus')">▼ Fokus Bawah</button>
                      <button type="button" onclick="sendRemote('next')">⏎ Klik Aktif/Lanjut</button>
                      <button type="button" class="secondary" onclick="sendRemote('reload')">↻ Muat Ulang TV</button>
                    </div>
                  </div>

                  <div class="card">
                    <details>
                      <summary>Mode Lanjutan · Paste Cookie music.youtube.com Langsung</summary>
                      <p class="sub" style="margin-top:10px;">Jika Anda sudah login di browser HP/PC, tempel cookie dari <code>music.youtube.com</code> (harus memuat <code>SAPISID</code> / <code>__Secure-3PAPISID</code>) untuk masuk instan dalam 1 klik:</p>
                      <form method="post" action="/submit?t=$token">
                        <input type="hidden" name="action" value="cookie">
                        <label for="cookieInput">Cookie Header</label>
                        <textarea id="cookieInput" name="value" placeholder="Tempel string cookie music.youtube.com di sini..." required></textarea>
                        <button class="secondary" type="submit">Masuk dengan Cookie</button>
                      </form>
                    </details>
                  </div>
                </div>

                <script>
                  var token = ${escapeJsonString(token)};
                  var stepOrder = ['email', 'password', 'verify', 'done'];
                  function applyTvState(st) {
                    if (!st || !st.step) return;
                    var hintEl = document.getElementById('tvStepHint');
                    if (hintEl) hintEl.textContent = (st.hint || '') + (st.host ? ' · ' + st.host : '');
                    var activeIdx = stepOrder.indexOf(st.step);
                    if (activeIdx < 0) activeIdx = 0;
                    stepOrder.forEach(function(name, i) {
                      var pill = document.getElementById('pill-' + name);
                      if (pill) {
                        pill.classList.remove('active', 'done');
                        if (i < activeIdx) pill.classList.add('done');
                        else if (i === activeIdx) pill.classList.add('active');
                      }
                      var card = document.getElementById('card-' + name);
                      if (card) {
                        card.classList.toggle('current', name === st.step);
                      }
                    });
                  }
                  function showBanner(msg, ok) {
                    var b = document.getElementById('liveBanner');
                    if (!b) return;
                    b.textContent = msg;
                    b.className = 'banner ' + (ok ? 'ok' : 'err');
                    b.style.display = 'block';
                  }
                  function sendRemote(actionName) {
                    fetch('/submit?t=' + encodeURIComponent(token), {
                      method: 'POST',
                      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                      body: 'action=' + encodeURIComponent(actionName) + '&ajax=1'
                    }).then(function(r) { return r.json(); })
                      .then(function(res) { showBanner(res.message || 'Terkirim ke TV', !!res.ok); pollStatus(); })
                      .catch(function() { showBanner('Gagal menghubungi layar TV di Wi-Fi lokal.', false); });
                  }
                  function pollStatus() {
                    fetch('/status?t=' + encodeURIComponent(token), { cache: 'no-store' })
                      .then(function(r) { return r.ok ? r.json() : null; })
                      .then(function(st) { if (st) applyTvState(st); })
                      .catch(function() {});
                  }
                  applyTvState($statusJson);
                  setInterval(pollStatus, 1500);
                </script>
                </body>
                </html>
                """.trimIndent()
        }
    }
}
