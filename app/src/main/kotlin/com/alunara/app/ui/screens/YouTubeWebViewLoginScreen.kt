package com.alunara.app.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.alunara.app.R
import com.alunara.app.ui.components.TvFocusableButton
import com.alunara.app.ui.components.safeRequestFocus
import com.alunara.app.ui.theme.AlunaraDimens
import com.alunara.app.ui.theme.LocalAlunaraColors
import com.alunara.core.domain.model.AccountCredentials
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

private const val YOUTUBE_MUSIC_LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&uilel=3&passive=true&continue=https%3A%2F%2Fmusic.youtube.com%2F"

private const val YOUTUBE_MUSIC_ORIGIN = "https://music.youtube.com"
private const val MAX_YTCFG_RETRY_ATTEMPTS = 2
private const val YTCFG_RETRY_DELAY_MS = 650L

private fun buildExtractYtcfgJs(attempt: Int): String =
    """
    (function() {
        try {
            var cfg = (window.yt && window.yt.config_) || (window.ytcfg && window.ytcfg.data_) || {};
            var getter = (window.ytcfg && window.ytcfg.get) ? function(k) { return window.ytcfg.get(k); } : function() { return ''; };
            var vd = cfg.VISITOR_DATA || getter('VISITOR_DATA') || '';
            var ds = cfg.DATASYNC_ID || getter('DATASYNC_ID') || cfg.DELEGATED_SESSION_ID || getter('DELEGATED_SESSION_ID') || '';
            window.AlunaraAuth.onYtConfig(String(vd || ''), String(ds || ''), $attempt);
        } catch (e) {
            window.AlunaraAuth.onYtConfig('', '', $attempt);
        }
    })();
    """

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeWebViewLoginScreen(
    onCancel: () -> Unit,
    onCredentialsCaptured: (AccountCredentials) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAlunaraColors.current
    val companionScope = rememberCoroutineScope()
    var currentHost by remember { mutableStateOf("accounts.google.com") }
    var currentStep by remember { mutableStateOf(LocalLoginCompanionServer.STEP_EMAIL) }
    var companionUrl by remember { mutableStateOf<String?>(null) }
    var companionFeedback by remember { mutableStateOf<String?>(null) }
    var webViewFocused by remember { mutableStateOf(false) }
    var domFocusIndex by remember { mutableIntStateOf(0) }
    val cancelRequester = remember { FocusRequester() }
    val webViewRequester = remember { FocusRequester() }
    val helperFocusInputRequester = remember { FocusRequester() }
    val helperClickNextRequester = remember { FocusRequester() }
    val helperReloadRequester = remember { FocusRequester() }
    val webViewHolder = remember { mutableStateOf<WebView?>(null) }
    val captured = remember { AtomicBoolean(false) }

    fun injectHighlightElement(direction: Int) {
        webViewHolder.value?.evaluateJavascript(buildHighlightDomScript(direction), null)
    }

    fun injectClickHighlighted() {
        webViewHolder.value?.evaluateJavascript(CLICK_HIGHLIGHTED_JS, null)
    }

    fun injectDetectLoginStep() {
        webViewHolder.value?.evaluateJavascript(DETECT_LOGIN_STEP_JS, null)
    }

    fun applyManualCookie(rawCookie: String) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        rawCookie
            .split(';')
            .map { it.trim() }
            .filter { it.contains('=') }
            .forEach { pair ->
                cookieManager.setCookie(YOUTUBE_MUSIC_ORIGIN, "$pair; Domain=.youtube.com; Path=/; Secure")
                cookieManager.setCookie(YOUTUBE_MUSIC_ORIGIN, "$pair; Path=/; Secure")
            }
        cookieManager.flush()
        currentStep = LocalLoginCompanionServer.STEP_DONE
        companionFeedback = "Cookie sesi diterapkan — memuat YouTube Music…"
        webViewHolder.value?.loadUrl(YOUTUBE_MUSIC_ORIGIN)
    }

    val companionServer =
        remember(companionScope) {
            LocalLoginCompanionServer(
                scope = companionScope,
                onInput = { payload ->
                    val webView = webViewHolder.value ?: return@LocalLoginCompanionServer
                    when (payload.action) {
                        CompanionAction.FILL_EMAIL -> {
                            webView.evaluateJavascript(buildFillAndSubmitScript(payload.value, mode = "email"), null)
                            companionFeedback = "Email dikirim dari HP ke layar TV"
                        }
                        CompanionAction.FILL_PASSWORD -> {
                            webView.evaluateJavascript(buildFillAndSubmitScript(payload.value, mode = "password"), null)
                            companionFeedback = "Sandi dikirim dari HP ke layar TV"
                        }
                        CompanionAction.FILL_TEXT -> {
                            webView.evaluateJavascript(buildFillAndSubmitScript(payload.value, mode = "text"), null)
                            companionFeedback = "Kode/teks dikirim dari HP ke TV"
                        }
                        CompanionAction.CLICK_NEXT -> {
                            webView.evaluateJavascript(CLICK_NEXT_OR_ACTIVE_JS, null)
                            companionFeedback = "Tombol Berikutnya diklik dari HP"
                        }
                        CompanionAction.FOCUS_PREV -> {
                            injectHighlightElement(-1)
                            companionFeedback = "Fokus TV digeser ke atas"
                        }
                        CompanionAction.FOCUS_NEXT -> {
                            injectHighlightElement(1)
                            companionFeedback = "Fokus TV digeser ke bawah"
                        }
                        CompanionAction.RELOAD_PAGE -> {
                            webView.reload()
                            companionFeedback = "Halaman login TV dimuat ulang"
                        }
                        CompanionAction.SUBMIT_COOKIE -> {
                            applyManualCookie(payload.value)
                        }
                    }
                },
            )
        }

    LaunchedEffect(Unit) {
        companionUrl = companionServer.start()
        withFrameNanos { }
        webViewRequester.safeRequestFocus()
    }

    DisposableEffect(Unit) {
        onDispose {
            companionServer.stop()
            webViewHolder.value?.apply {
                stopLoading()
                clearHistory()
                removeAllViews()
                destroy()
            }
            webViewHolder.value = null
        }
    }

    val webBorderColor = if (webViewFocused) colors.focusRingStrong else colors.secondarySurface
    val stepBadgeText =
        when (currentStep) {
            LocalLoginCompanionServer.STEP_PASSWORD -> stringResource(R.string.login_step_password)
            LocalLoginCompanionServer.STEP_VERIFY -> stringResource(R.string.login_step_verify)
            LocalLoginCompanionServer.STEP_DONE -> stringResource(R.string.login_step_done)
            else -> stringResource(R.string.login_step_email)
        }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(AlunaraDimens.RadiusCard))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TvFocusableButton(
                text = stringResource(R.string.cancel),
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                onClick = onCancel,
                modifier =
                    Modifier
                        .focusRequester(cancelRequester)
                        .focusProperties {
                            down = webViewRequester
                            right = helperFocusInputRequester
                        },
            )
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .focusProperties { canFocus = false },
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "${stringResource(R.string.login_webview_title)} · $currentHost",
                    color = colors.primaryText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = companionFeedback ?: stringResource(R.string.login_webview_hint),
                    color = if (companionFeedback != null) colors.primaryAccent else colors.mutedText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = stepBadgeText,
                color = colors.primaryAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier =
                    Modifier
                        .background(colors.secondarySurface, RoundedCornerShape(999.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .focusProperties { canFocus = false },
            )
        }

        // 2-Column Body: Direct D-pad WebView (Left) + Smart QR Wizard & Helper Controls (Right)
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .weight(0.62f)
                        .fillMaxHeight()
                        .border(BorderStroke(colors.focusBorderWidth, webBorderColor), RoundedCornerShape(AlunaraDimens.RadiusCard))
                        .background(colors.surface, RoundedCornerShape(AlunaraDimens.RadiusCard))
                        .padding(4.dp)
                        .focusRequester(webViewRequester)
                        .focusProperties {
                            up = cancelRequester
                            right = helperFocusInputRequester
                        }
                        .onFocusChanged { webViewFocused = it.isFocused }
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type != KeyEventType.KeyDown) {
                                return@onPreviewKeyEvent false
                            }
                            when (keyEvent.key) {
                                Key.DirectionDown -> {
                                    injectHighlightElement(1)
                                    true
                                }
                                Key.DirectionUp -> {
                                    if (domFocusIndex <= 0) {
                                        cancelRequester.safeRequestFocus()
                                    } else {
                                        injectHighlightElement(-1)
                                    }
                                    true
                                }
                                Key.DirectionRight -> {
                                    helperFocusInputRequester.safeRequestFocus()
                                    true
                                }
                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                    injectClickHighlighted()
                                    true
                                }
                                Key.Back -> {
                                    onCancel()
                                    true
                                }
                                else -> false
                            }
                        }
                        .focusable(),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            layoutParams =
                                ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                            isFocusable = true
                            isFocusableInTouchMode = true

                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true)

                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.userAgentString =
                                settings.userAgentString
                                    .replace("; wv", "")
                                    .replace("Version/4.0 ", "")

                            var latestCookie = ""
                            val bridge =
                                AlunaraAuthJsBridge(
                                    onContext = { visitorData, dataSyncId, attempt ->
                                        val view = webViewHolder.value ?: return@AlunaraAuthJsBridge
                                        view.post {
                                            if (captured.get()) return@post
                                            val cookie = latestCookie
                                            if (!LocalLoginCompanionServer.hasValidSapisidCookie(cookie)) return@post
                                            val missingContext = visitorData.isNullOrBlank() || dataSyncId.isNullOrBlank()
                                            if (missingContext && attempt < MAX_YTCFG_RETRY_ATTEMPTS) {
                                                view.postDelayed(
                                                    {
                                                        if (!captured.get()) {
                                                            view.evaluateJavascript(buildExtractYtcfgJs(attempt + 1), null)
                                                        }
                                                    },
                                                    YTCFG_RETRY_DELAY_MS,
                                                )
                                                return@post
                                            }
                                            if (captured.compareAndSet(false, true)) {
                                                companionServer.currentStep = LocalLoginCompanionServer.STEP_DONE
                                                companionServer.stop()
                                                onCredentialsCaptured(
                                                    AccountCredentials(
                                                        cookie = cookie,
                                                        dataSyncId = dataSyncId,
                                                        visitorData = visitorData,
                                                    ),
                                                )
                                            }
                                        }
                                    },
                                    onFocusIndex = { idx ->
                                        domFocusIndex = idx
                                    },
                                    onStepDetected = { step, hint ->
                                        currentStep = step
                                        companionServer.currentStep = step
                                        companionServer.lastStatusHint = hint
                                    },
                                )
                            addJavascriptInterface(bridge, "AlunaraAuth")

                            webChromeClient = WebChromeClient()
                            webViewClient =
                                object : WebViewClient() {
                                    override fun onPageFinished(
                                        view: WebView?,
                                        url: String?,
                                    ) {
                                        super.onPageFinished(view, url)
                                        val parsedHost = url?.let { runCatching { Uri.parse(it).host }.getOrNull() }
                                        if (!parsedHost.isNullOrBlank()) {
                                            currentHost = parsedHost
                                            companionServer.currentHost = parsedHost
                                        }
                                        if (url != null && url.startsWith(YOUTUBE_MUSIC_ORIGIN) && !captured.get()) {
                                            cookieManager.flush()
                                            val cookie = cookieManager.getCookie(YOUTUBE_MUSIC_ORIGIN).orEmpty()
                                            if (LocalLoginCompanionServer.hasValidSapisidCookie(cookie)) {
                                                latestCookie = cookie
                                                currentStep = LocalLoginCompanionServer.STEP_DONE
                                                companionServer.currentStep = LocalLoginCompanionServer.STEP_DONE
                                                companionServer.lastStatusHint = "Berhasil masuk YouTube Music — memverifikasi akun…"
                                                view?.evaluateJavascript(buildExtractYtcfgJs(0), null)
                                            }
                                        } else if (!captured.get()) {
                                            view?.evaluateJavascript(buildHighlightDomScript(0), null)
                                            view?.evaluateJavascript(DETECT_LOGIN_STEP_JS, null)
                                        }
                                    }
                                }

                            webViewHolder.value = this
                            loadUrl(YOUTUBE_MUSIC_LOGIN_URL)
                        }
                    },
                )
            }

            // Right Pane: Clear QR Wizard Tutorial + Quick Focusable Helpers
            Column(
                modifier =
                    Modifier
                        .weight(0.38f)
                        .fillMaxHeight()
                        .background(colors.surface, RoundedCornerShape(AlunaraDimens.RadiusCard))
                        .border(BorderStroke(1.dp, colors.secondarySurface), RoundedCornerShape(AlunaraDimens.RadiusCard))
                        .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().focusProperties { canFocus = false },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QrCode2,
                        contentDescription = null,
                        tint = colors.primaryAccent,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = stringResource(R.string.qr_companion_title),
                        color = colors.primaryText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Text(
                    text = stringResource(R.string.qr_companion_subtitle),
                    color = colors.mutedText,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.fillMaxWidth().focusProperties { canFocus = false },
                )

                val activeUrl = companionUrl
                if (activeUrl != null) {
                    QrCodeCanvas(
                        content = activeUrl,
                        modifier = Modifier.size(182.dp).focusProperties { canFocus = false },
                    )
                    Text(
                        text = activeUrl,
                        color = colors.primaryAccent,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.focusProperties { canFocus = false },
                    )
                    Text(
                        text = stringResource(R.string.qr_companion_hint_steps),
                        color = colors.primaryText,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(colors.secondarySurface, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .focusProperties { canFocus = false },
                    )
                } else {
                    Text(
                        text = stringResource(R.string.qr_companion_unavailable),
                        color = colors.mutedText,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        modifier = Modifier.weight(1f).focusProperties { canFocus = false },
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TvFocusableButton(
                        text = stringResource(R.string.login_btn_focus_input),
                        icon = Icons.Rounded.TouchApp,
                        onClick = {
                            injectHighlightElement(0)
                            injectDetectLoginStep()
                            webViewRequester.safeRequestFocus()
                        },
                        modifier =
                            Modifier
                                .weight(1f)
                                .focusRequester(helperFocusInputRequester)
                                .focusProperties {
                                    left = webViewRequester
                                    right = helperClickNextRequester
                                    up = cancelRequester
                                },
                    )
                    TvFocusableButton(
                        text = stringResource(R.string.login_btn_click_next),
                        onClick = {
                            webViewHolder.value?.evaluateJavascript(CLICK_NEXT_OR_ACTIVE_JS, null)
                        },
                        modifier =
                            Modifier
                                .weight(1f)
                                .focusRequester(helperClickNextRequester)
                                .focusProperties {
                                    left = helperFocusInputRequester
                                    right = helperReloadRequester
                                    up = cancelRequester
                                },
                    )
                    TvFocusableButton(
                        text = stringResource(R.string.login_btn_reload),
                        icon = Icons.Rounded.Refresh,
                        onClick = { webViewHolder.value?.reload() },
                        modifier =
                            Modifier
                                .weight(1f)
                                .focusRequester(helperReloadRequester)
                                .focusProperties {
                                    left = helperClickNextRequester
                                    up = cancelRequester
                                },
                    )
                }
            }
        }
    }
}

private const val DETECT_LOGIN_STEP_JS =
    """
    (function() {
        var isVisible = function(el) {
            if (!el) return false;
            var r = el.getBoundingClientRect();
            return r.width > 0 && r.height > 0;
        };
        var step = 'email';
        var hint = 'Langkah 1: Masukkan Email Google Anda';
        if (window.location.hostname.indexOf('music.youtube.com') >= 0) {
            step = 'done';
            hint = 'Berhasil masuk YouTube Music';
        } else if (Array.from(document.querySelectorAll('input[type="password"]')).some(isVisible)) {
            step = 'password';
            hint = 'Langkah 2: Masukkan Kata Sandi akun Google Anda';
        } else if (Array.from(document.querySelectorAll('input[type="email"], input[name="identifier"]')).some(isVisible)) {
            step = 'email';
            hint = 'Langkah 1: Masukkan Email / No. HP akun Google';
        } else {
            step = 'verify';
            hint = 'Langkah 3: Setujui notifikasi Google Prompt di HP atau isi kode OTP';
        }
        if (window.AlunaraAuth && window.AlunaraAuth.onLoginStep) {
            window.AlunaraAuth.onLoginStep(step, hint);
        }
    })();
    """

private fun buildHighlightDomScript(direction: Int): String =
    """
    (function() {
        var nodes = Array.from(document.querySelectorAll('input, button, a[href], [role="button"], [role="link"], [role="checkbox"], [tabindex="0"]'))
            .filter(function(el) {
                if (el.disabled) return false;
                var style = window.getComputedStyle(el);
                if (!style || style.visibility === 'hidden' || style.display === 'none') return false;
                var r = el.getBoundingClientRect();
                return r.width > 0 && r.height > 0;
            });
        if (!nodes.length) return;
        if (window.__alunaraPrevEl) {
            window.__alunaraPrevEl.style.outline = '';
            window.__alunaraPrevEl.style.outlineOffset = '';
        }
        var idx = typeof window.__alunaraFocusIdx === 'number' ? window.__alunaraFocusIdx : 0;
        if ($direction === 0) {
            var inputIdx = nodes.findIndex(function(el) {
                return el.tagName === 'INPUT' && el.type !== 'hidden' && el.type !== 'checkbox';
            });
            idx = inputIdx >= 0 ? inputIdx : 0;
        } else {
            idx = (idx + ($direction) + nodes.length) % nodes.length;
        }
        window.__alunaraFocusIdx = idx;
        if (window.AlunaraAuth && window.AlunaraAuth.onDomFocusIndex) {
            window.AlunaraAuth.onDomFocusIndex(idx);
        }
        var target = nodes[idx];
        window.__alunaraPrevEl = target;
        window.__alunaraActiveEl = target;
        target.style.outline = '3px solid #C8F0DA';
        target.style.outlineOffset = '3px';
        target.scrollIntoView({ behavior: 'smooth', block: 'center' });
        if ($direction !== 0) {
            target.focus();
        }
    })();
    """.trimIndent()

private fun buildFillAndSubmitScript(
    rawValue: String,
    mode: String,
): String {
    val quotedValue = JSONObject.quote(rawValue)
    val selectorsArray =
        when (mode) {
            "email" -> "['input[type=\"email\"]', 'input[name=\"identifier\"]', 'input[type=\"text\"]']"
            "password" -> "['input[type=\"password\"]', 'input[name=\"Passwd\"]']"
            else -> "['input[type=\"tel\"]', 'input[type=\"text\"]', 'input[type=\"number\"]', 'input[type=\"email\"]', 'input[type=\"password\"]', 'input:not([type=\"hidden\"])']"
        }
    return """
        (function() {
            var val = $quotedValue;
            var isVisibleInput = function(el) {
                if (!el || el.tagName !== 'INPUT' || el.disabled) return false;
                if (el.type === 'hidden' || el.type === 'checkbox' || el.type === 'radio') return false;
                var r = el.getBoundingClientRect();
                return r.width > 0 && r.height > 0;
            };
            var target = null;
            if ("$mode" === "text" && isVisibleInput(window.__alunaraActiveEl)) {
                target = window.__alunaraActiveEl;
            }
            if (!target) {
                var selectors = $selectorsArray;
                for (var i = 0; i < selectors.length; i++) {
                    var found = Array.from(document.querySelectorAll(selectors[i])).find(isVisibleInput);
                    if (found) { target = found; break; }
                }
            }
            if (!target) return false;
            target.focus();
            var descriptor = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value');
            if (descriptor && descriptor.set) {
                descriptor.set.call(target, val);
            } else {
                target.value = val;
            }
            target.dispatchEvent(new Event('input', { bubbles: true }));
            target.dispatchEvent(new Event('change', { bubbles: true }));
            setTimeout(function() {
                var nextSelector = ("$mode" === "password")
                    ? '#passwordNext button, #passwordNext, button[jsname="LgbsSe"][type="submit"], button[type="submit"], [role="button"][jsname="LgbsSe"]'
                    : '#identifierNext button, #identifierNext, #passwordNext button, #passwordNext, button[jsname="LgbsSe"][type="submit"], button[type="submit"], [role="button"][jsname="LgbsSe"]';
                var nextBtn = document.querySelector(nextSelector);
                if (nextBtn) {
                    nextBtn.click();
                }
            }, 180);
            return true;
        })();
    """.trimIndent()
}

private const val CLICK_HIGHLIGHTED_JS =
    """
    (function() {
        var el = window.__alunaraActiveEl;
        if (!el) return;
        el.focus();
        el.click();
    })();
    """

private const val CLICK_NEXT_OR_ACTIVE_JS =
    """
    (function() {
        var nextBtn = document.querySelector('#identifierNext button, #identifierNext, #passwordNext button, #passwordNext, button[type="submit"]');
        if (nextBtn) { nextBtn.click(); return; }
        if (window.__alunaraActiveEl) { window.__alunaraActiveEl.click(); }
    })();
    """

private class AlunaraAuthJsBridge(
    private val onContext: (visitorData: String?, dataSyncId: String?, attempt: Int) -> Unit,
    private val onFocusIndex: (Int) -> Unit,
    private val onStepDetected: (step: String, hint: String) -> Unit,
) {
    @JavascriptInterface
    fun onYtConfig(
        visitorData: String?,
        dataSyncId: String?,
        attempt: Int,
    ) {
        onContext(
            visitorData?.takeIf { it.isNotBlank() && it != "null" },
            dataSyncId?.takeIf { it.isNotBlank() && it != "null" },
            attempt,
        )
    }

    @JavascriptInterface
    fun onDomFocusIndex(index: Int) {
        onFocusIndex(index)
    }

    @JavascriptInterface
    fun onLoginStep(
        step: String?,
        hint: String?,
    ) {
        val safeStep =
            when (step) {
                LocalLoginCompanionServer.STEP_PASSWORD,
                LocalLoginCompanionServer.STEP_VERIFY,
                LocalLoginCompanionServer.STEP_DONE,
                -> step
                else -> LocalLoginCompanionServer.STEP_EMAIL
            }
        onStepDetected(safeStep, hint.orEmpty())
    }
}
