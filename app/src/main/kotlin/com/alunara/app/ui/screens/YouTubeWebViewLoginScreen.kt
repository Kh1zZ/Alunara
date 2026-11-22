package com.alunara.app.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.view.KeyEvent as AndroidKeyEvent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.QrCode2
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
        webViewHolder.value?.evaluateJavascript(CHECK_STEP_ONCE_JS, null)
    }

    fun scheduleStepSyncChecks(webView: WebView) {
        webView.postDelayed(
            {
                if (!captured.get()) {
                    webView.evaluateJavascript(INSTALL_STEP_WATCHER_JS, null)
                    webView.evaluateJavascript(CHECK_STEP_ONCE_JS, null)
                }
            },
            500L,
        )
        webView.postDelayed(
            {
                if (!captured.get()) {
                    webView.evaluateJavascript(CHECK_STEP_ONCE_JS, null)
                }
            },
            1150L,
        )
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
                            scheduleStepSyncChecks(webView)
                            companionFeedback = "Email dikirim ke TV — menunggu halaman Sandi Google…"
                        }
                        CompanionAction.FILL_PASSWORD -> {
                            webView.evaluateJavascript(buildFillAndSubmitScript(payload.value, mode = "password"), null)
                            scheduleStepSyncChecks(webView)
                            companionFeedback = "Sandi dikirim ke TV — cek verifikasi jika diminta…"
                        }
                        CompanionAction.FILL_TEXT -> {
                            webView.evaluateJavascript(buildFillAndSubmitScript(payload.value, mode = "text"), null)
                            scheduleStepSyncChecks(webView)
                            companionFeedback = "Kode/teks dikirim dari HP ke TV"
                        }
                        CompanionAction.CLICK_NEXT -> {
                            webView.evaluateJavascript(CLICK_NEXT_OR_ACTIVE_JS, null)
                            scheduleStepSyncChecks(webView)
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

                            setOnKeyListener { _, keyCode, event ->
                                if (event.action != AndroidKeyEvent.ACTION_DOWN) return@setOnKeyListener false
                                when (keyCode) {
                                    AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                                        injectHighlightElement(1)
                                        true
                                    }
                                    AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                                        if (domFocusIndex <= 0) {
                                            cancelRequester.safeRequestFocus()
                                        } else {
                                            injectHighlightElement(-1)
                                        }
                                        true
                                    }
                                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                                        helperFocusInputRequester.safeRequestFocus()
                                        true
                                    }
                                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                                    AndroidKeyEvent.KEYCODE_ENTER,
                                    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                        injectClickHighlighted()
                                        true
                                    }
                                    AndroidKeyEvent.KEYCODE_BACK -> {
                                        onCancel()
                                        true
                                    }
                                    else -> false
                                }
                            }

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
                            fun captureMusicCookiesAndYtcfg(view: WebView) {
                                cookieManager.flush()
                                val cookie = cookieManager.getCookie(YOUTUBE_MUSIC_ORIGIN).orEmpty()
                                if (LocalLoginCompanionServer.hasValidSapisidCookie(cookie)) {
                                    latestCookie = cookie
                                    currentStep = LocalLoginCompanionServer.STEP_DONE
                                    companionServer.currentStep = LocalLoginCompanionServer.STEP_DONE
                                    companionServer.lastStatusHint = "Berhasil masuk YouTube Music — memverifikasi akun…"
                                    view.evaluateJavascript(buildExtractYtcfgJs(0), null)
                                }
                            }

                            fun syncUrlAndHost(url: String?) {
                                val parsedHost = url?.let { runCatching { Uri.parse(it).host }.getOrNull() }
                                if (!parsedHost.isNullOrBlank()) {
                                    currentHost = parsedHost
                                    companionServer.currentHost = parsedHost
                                }
                                if (!captured.get()) {
                                    inferStepFromUrl(url)?.let { (step, hint) ->
                                        currentStep = step
                                        companionServer.currentStep = step
                                        companionServer.lastStatusHint = hint
                                    }
                                }
                            }

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
                                        webViewHolder.value?.post { domFocusIndex = idx }
                                    },
                                    onStepDetected = { step, hint ->
                                        webViewHolder.value?.post {
                                            if (!captured.get()) {
                                                currentStep = step
                                                companionServer.currentStep = step
                                                if (hint.isNotBlank()) {
                                                    companionServer.lastStatusHint = hint
                                                }
                                            }
                                        }
                                    },
                                    onMusicHomeReached = {
                                        val view = webViewHolder.value ?: return@AlunaraAuthJsBridge
                                        view.post {
                                            if (!captured.get()) {
                                                captureMusicCookiesAndYtcfg(view)
                                            }
                                        }
                                    },
                                )
                            addJavascriptInterface(bridge, "AlunaraAuth")

                            webChromeClient = WebChromeClient()
                            webViewClient =
                                object : WebViewClient() {
                                    override fun onPageStarted(
                                        view: WebView?,
                                        url: String?,
                                        favicon: Bitmap?,
                                    ) {
                                        super.onPageStarted(view, url, favicon)
                                        syncUrlAndHost(url)
                                    }

                                    override fun doUpdateVisitedHistory(
                                        view: WebView?,
                                        url: String?,
                                        isReload: Boolean,
                                    ) {
                                        super.doUpdateVisitedHistory(view, url, isReload)
                                        syncUrlAndHost(url)
                                        if (view == null || captured.get()) return
                                        if (url != null && url.startsWith(YOUTUBE_MUSIC_ORIGIN)) {
                                            captureMusicCookiesAndYtcfg(view)
                                        } else {
                                            view.evaluateJavascript(INSTALL_STEP_WATCHER_JS, null)
                                            scheduleStepSyncChecks(view)
                                        }
                                    }

                                    override fun onPageFinished(
                                        view: WebView?,
                                        url: String?,
                                    ) {
                                        super.onPageFinished(view, url)
                                        syncUrlAndHost(url)
                                        if (view == null || captured.get()) return
                                        if (url != null && url.startsWith(YOUTUBE_MUSIC_ORIGIN)) {
                                            captureMusicCookiesAndYtcfg(view)
                                        } else {
                                            view.evaluateJavascript(buildHighlightDomScript(0), null)
                                            view.evaluateJavascript(INSTALL_STEP_WATCHER_JS, null)
                                            scheduleStepSyncChecks(view)
                                        }
                                    }
                                }

                            webViewHolder.value = this
                            loadUrl(YOUTUBE_MUSIC_LOGIN_URL)
                        }
                    },
                )
            }

            // Right Pane: Clear QR Wizard Tutorial + Quick Focusable Helpers (fits completely in 720p TV height)
            Column(
                modifier =
                    Modifier
                        .weight(0.38f)
                        .fillMaxHeight()
                        .background(colors.surface, RoundedCornerShape(AlunaraDimens.RadiusCard))
                        .border(BorderStroke(1.dp, colors.secondarySurface), RoundedCornerShape(AlunaraDimens.RadiusCard))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
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
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.qr_companion_title),
                        color = colors.primaryText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Text(
                    text = stringResource(R.string.qr_companion_subtitle),
                    color = colors.mutedText,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.fillMaxWidth().focusProperties { canFocus = false },
                )

                val activeUrl = companionUrl
                if (activeUrl != null) {
                    QrCodeCanvas(
                        content = activeUrl,
                        modifier = Modifier.size(124.dp).focusProperties { canFocus = false },
                    )
                    Text(
                        text = activeUrl,
                        color = colors.primaryAccent,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.focusProperties { canFocus = false },
                    )
                    Text(
                        text = stringResource(R.string.qr_companion_hint_steps),
                        color = colors.primaryText,
                        fontSize = 11.5.sp,
                        lineHeight = 15.5.sp,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(colors.secondarySurface, RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                .focusProperties { canFocus = false },
                    )
                } else {
                    Text(
                        text = stringResource(R.string.qr_companion_unavailable),
                        color = colors.mutedText,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.weight(1f).focusProperties { canFocus = false },
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LoginHelperChipButton(
                        text = stringResource(R.string.login_btn_focus_input),
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
                    LoginHelperChipButton(
                        text = stringResource(R.string.login_btn_click_next),
                        highlighted = true,
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
                    LoginHelperChipButton(
                        text = stringResource(R.string.login_btn_reload),
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

@Composable
private fun LoginHelperChipButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val colors = LocalAlunaraColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val bgColor =
        when {
            focused -> colors.focusedButtonBg
            highlighted -> colors.secondarySurface
            else -> colors.background
        }
    val fgColor =
        when {
            focused -> colors.focusedButtonContent
            highlighted -> colors.primaryAccent
            else -> colors.primaryText
        }
    val borderColor = if (focused) colors.focusRingStrong else colors.secondarySurface

    Box(
        modifier =
            modifier
                .heightIn(min = 38.dp)
                .background(bgColor, shape)
                .border(BorderStroke(colors.focusBorderWidth, borderColor), shape)
                .semantics { role = Role.Button }
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = fgColor,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun inferStepFromUrl(url: String?): Pair<String, String>? {
    if (url.isNullOrBlank()) return null
    val lower = url.lowercase()
    return when {
        lower.startsWith(YOUTUBE_MUSIC_ORIGIN) || lower.contains("://music.youtube.com") ->
            LocalLoginCompanionServer.STEP_DONE to "Langkah 4 · Berhasil masuk YouTube Music"
        lower.contains("/challenge/pwd") || lower.contains("/signin/v2/challenge/pwd") ->
            LocalLoginCompanionServer.STEP_PASSWORD to "Langkah 2 dari 3 · Masukkan Kata Sandi akun Google"
        lower.contains("/challenge/dp") || lower.contains("/challenge/ootp") ->
            LocalLoginCompanionServer.STEP_VERIFY to "Langkah 3 dari 3 · Ketuk 'Ya, itu saya' di notifikasi Google Prompt HP"
        lower.contains("/challenge/") ->
            LocalLoginCompanionServer.STEP_VERIFY to "Langkah 3 dari 3 · Verifikasi 2FA / Kode OTP di HP"
        lower.contains("/identifier") || lower.contains("accounts.google.com/servicelogin") ->
            LocalLoginCompanionServer.STEP_EMAIL to "Langkah 1 dari 3 · Masukkan Email / No. HP akun Google"
        else -> null
    }
}

private const val CHECK_STEP_ONCE_JS =
    """
    (function() {
        var isVisible = function(el) {
            if (!el || el.disabled) return false;
            var st = window.getComputedStyle(el);
            if (!st || st.display === 'none' || st.visibility === 'hidden' || st.opacity === '0') return false;
            if (el.closest('[aria-hidden="true"], [hidden]')) return false;
            var r = el.getBoundingClientRect();
            return r.width > 1 && r.height > 1;
        };
        var host = window.location.hostname || '';
        var href = (window.location.href || '').toLowerCase();
        var headingEl = document.querySelector('#headingText, h1');
        var heading = headingEl ? (headingEl.innerText || headingEl.textContent || '').trim().replace(/\s+/g, ' ') : '';
        var hasPassword = Array.from(document.querySelectorAll('input[type="password"], input[name="Passwd"], input[name="password"]')).some(isVisible);
        var hasEmail = Array.from(document.querySelectorAll('input[type="email"], input[name="identifier"], #identifierId')).some(isVisible);
        var hasOtp = Array.from(document.querySelectorAll('input[type="tel"], input[name="Pin"], input[id*="totp" i], input[autocomplete="one-time-code"], input[type="number"]')).some(isVisible);
        var step = 'email';
        var hint = 'Langkah 1 dari 3 · Masukkan Email / No. HP Google';
        if (host.indexOf('music.youtube.com') >= 0) {
            step = 'done';
            hint = 'Langkah 4 · Berhasil masuk YouTube Music — memverifikasi akun…';
            if (!window.__alunaraMusicNotified && window.AlunaraAuth && window.AlunaraAuth.onMusicHomeReached) {
                window.__alunaraMusicNotified = true;
                window.AlunaraAuth.onMusicHomeReached();
            }
        } else if (hasPassword) {
            step = 'password';
            hint = heading ? ('Langkah 2 dari 3 · ' + heading) : 'Langkah 2 dari 3 · Masukkan Kata Sandi akun Google';
        } else if (href.indexOf('/challenge/') >= 0 || hasOtp || document.querySelector('[data-challengetype]')) {
            step = 'verify';
            if (hasOtp) {
                hint = heading ? ('Langkah 3 dari 3 · ' + heading + ' (Isi kode OTP di HP)') : 'Langkah 3 dari 3 · Masukkan Kode Verifikasi OTP';
            } else {
                hint = heading ? ('Langkah 3 dari 3 · ' + heading + ' (Konfirmasi di HP)') : 'Langkah 3 dari 3 · Setujui Google Prompt di HP';
            }
        } else if (hasEmail) {
            step = 'email';
            hint = heading ? ('Langkah 1 dari 3 · ' + heading) : 'Langkah 1 dari 3 · Masukkan Email / No. HP Google';
        } else if (href.indexOf('/challenge/pwd') >= 0) {
            step = 'password';
            hint = 'Langkah 2 dari 3 · Memuat kolom Kata Sandi…';
        }
        var sig = step + '|' + hint;
        if (window.__alunaraLastReportedStep !== sig) {
            window.__alunaraLastReportedStep = sig;
            if (window.AlunaraAuth && window.AlunaraAuth.onLoginStep) {
                window.AlunaraAuth.onLoginStep(step, hint);
            }
        }
    })();
    """

private val INSTALL_STEP_WATCHER_JS =
    """
    (function() {
        window.__alunaraCheckStep = function() {
            $CHECK_STEP_ONCE_JS
        };
        window.__alunaraCheckStep();
        if (window.__alunaraStepWatcherInstalled) return;
        window.__alunaraStepWatcherInstalled = true;
        var debounceTimer = null;
        var schedule = function() {
            if (debounceTimer) clearTimeout(debounceTimer);
            debounceTimer = setTimeout(window.__alunaraCheckStep, 80);
        };
        if (window.MutationObserver && document.documentElement) {
            var obs = new MutationObserver(schedule);
            obs.observe(document.documentElement, {
                childList: true,
                subtree: true,
                attributes: true,
                attributeFilter: ['style', 'class', 'hidden', 'aria-hidden', 'disabled']
            });
        }
        setInterval(window.__alunaraCheckStep, 450);
    })();
    """.trimIndent()

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
                if (window.__alunaraCheckStep) {
                    setTimeout(window.__alunaraCheckStep, 350);
                    setTimeout(window.__alunaraCheckStep, 900);
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
        if (nextBtn) { nextBtn.click(); }
        else if (window.__alunaraActiveEl) { window.__alunaraActiveEl.click(); }
        if (window.__alunaraCheckStep) {
            setTimeout(window.__alunaraCheckStep, 350);
            setTimeout(window.__alunaraCheckStep, 900);
        }
    })();
    """

private class AlunaraAuthJsBridge(
    private val onContext: (visitorData: String?, dataSyncId: String?, attempt: Int) -> Unit,
    private val onFocusIndex: (Int) -> Unit,
    private val onStepDetected: (step: String, hint: String) -> Unit,
    private val onMusicHomeReached: () -> Unit,
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
    fun onMusicHomeReached() {
        onMusicHomeReached.invoke()
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
