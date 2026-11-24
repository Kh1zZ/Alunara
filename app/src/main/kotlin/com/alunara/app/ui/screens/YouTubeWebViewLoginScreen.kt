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
import androidx.compose.ui.platform.LocalView
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
import com.alunara.app.ui.i18n.AppLanguage
import com.alunara.app.ui.i18n.LocalAppLanguage
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
    companionServer: LocalLoginCompanionServer? = null,
    onRegisterCompanionInputHandler: ((suspend (CompanionInputPayload) -> Unit)?) -> Unit = {},
) {
    val colors = LocalAlunaraColors.current
    val appLanguage = LocalAppLanguage.current
    val hostActivityContext = LocalView.current.context
    val checkStepOnceJs = remember(appLanguage) { buildCheckStepOnceJs(appLanguage) }
    val installStepWatcherJs = remember(appLanguage) { buildInstallStepWatcherJs(appLanguage) }
    val companionScope = rememberCoroutineScope()
    var currentHost by remember { mutableStateOf("accounts.google.com") }
    var currentStep by remember {
        mutableStateOf(
            if (companionServer?.isCompletedOnTv == true) {
                LocalLoginCompanionServer.STEP_DONE
            } else {
                LocalLoginCompanionServer.STEP_EMAIL
            },
        )
    }
    var companionUrl by remember { mutableStateOf(companionServer?.localLoginUrl) }
    var pairingCode by remember { mutableStateOf(companionServer?.pairingCode) }
    var companionFeedbackResId by remember { mutableStateOf<Int?>(null) }
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
        webViewHolder.value?.evaluateJavascript(checkStepOnceJs, null)
    }

    fun scheduleStepSyncChecks(webView: WebView) {
        webView.postDelayed(
            {
                if (!captured.get()) {
                    webView.evaluateJavascript(installStepWatcherJs, null)
                    webView.evaluateJavascript(checkStepOnceJs, null)
                }
            },
            500L,
        )
        webView.postDelayed(
            {
                if (!captured.get()) {
                    webView.evaluateJavascript(checkStepOnceJs, null)
                }
            },
            1150L,
        )
    }

    fun applyManualCookie(
        rawCookie: String,
        targetServer: LocalLoginCompanionServer?,
    ) {
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
        targetServer?.currentStep = LocalLoginCompanionServer.STEP_DONE
        companionFeedbackResId = R.string.login_feedback_cookie_applied
        webViewHolder.value?.loadUrl(YOUTUBE_MUSIC_ORIGIN)
    }

    val ownedServer =
        remember(companionServer, companionScope, appLanguage) {
            companionServer
                ?: LocalLoginCompanionServer(
                    scope = companionScope,
                    language = appLanguage,
                    onInput = { payload ->
                        val webView = webViewHolder.value ?: return@LocalLoginCompanionServer
                        when (payload.action) {
                            CompanionAction.FILL_EMAIL -> {
                                webView.evaluateJavascript(buildFillAndSubmitScript(payload.value, mode = "email"), null)
                                scheduleStepSyncChecks(webView)
                                companionFeedbackResId = R.string.login_feedback_email_sent
                            }
                            CompanionAction.FILL_PASSWORD -> {
                                webView.evaluateJavascript(buildFillAndSubmitScript(payload.value, mode = "password"), null)
                                scheduleStepSyncChecks(webView)
                                companionFeedbackResId = R.string.login_feedback_password_sent
                            }
                            CompanionAction.FILL_TEXT -> {
                                webView.evaluateJavascript(buildFillAndSubmitScript(payload.value, mode = "text"), null)
                                scheduleStepSyncChecks(webView)
                                companionFeedbackResId = R.string.login_feedback_text_sent
                            }
                            CompanionAction.CLICK_NEXT -> {
                                webView.evaluateJavascript(CLICK_NEXT_OR_ACTIVE_JS, null)
                                scheduleStepSyncChecks(webView)
                                companionFeedbackResId = R.string.login_feedback_next_clicked
                            }
                            CompanionAction.FOCUS_PREV -> {
                                injectHighlightElement(-1)
                                companionFeedbackResId = R.string.login_feedback_focus_up
                            }
                            CompanionAction.FOCUS_NEXT -> {
                                injectHighlightElement(1)
                                companionFeedbackResId = R.string.login_feedback_focus_down
                            }
                            CompanionAction.RELOAD_PAGE -> {
                                webView.reload()
                                companionFeedbackResId = R.string.login_feedback_reloaded
                            }
                            CompanionAction.SUBMIT_COOKIE -> {
                                applyManualCookie(payload.value, null)
                            }
                        }
                    },
                )
        }

    val activeServer = ownedServer

    LaunchedEffect(activeServer) {
        onRegisterCompanionInputHandler { payload ->
            val webView = webViewHolder.value ?: return@onRegisterCompanionInputHandler
            when (payload.action) {
                CompanionAction.FILL_EMAIL -> {
                    webView.evaluateJavascript(buildFillAndSubmitScript(payload.value, mode = "email"), null)
                    scheduleStepSyncChecks(webView)
                    companionFeedbackResId = R.string.login_feedback_email_sent
                }
                CompanionAction.FILL_PASSWORD -> {
                    webView.evaluateJavascript(buildFillAndSubmitScript(payload.value, mode = "password"), null)
                    scheduleStepSyncChecks(webView)
                    companionFeedbackResId = R.string.login_feedback_password_sent
                }
                CompanionAction.FILL_TEXT -> {
                    webView.evaluateJavascript(buildFillAndSubmitScript(payload.value, mode = "text"), null)
                    scheduleStepSyncChecks(webView)
                    companionFeedbackResId = R.string.login_feedback_text_sent
                }
                CompanionAction.CLICK_NEXT -> {
                    webView.evaluateJavascript(CLICK_NEXT_OR_ACTIVE_JS, null)
                    scheduleStepSyncChecks(webView)
                    companionFeedbackResId = R.string.login_feedback_next_clicked
                }
                CompanionAction.FOCUS_PREV -> {
                    injectHighlightElement(-1)
                    companionFeedbackResId = R.string.login_feedback_focus_up
                }
                CompanionAction.FOCUS_NEXT -> {
                    injectHighlightElement(1)
                    companionFeedbackResId = R.string.login_feedback_focus_down
                }
                CompanionAction.RELOAD_PAGE -> {
                    webView.reload()
                    companionFeedbackResId = R.string.login_feedback_reloaded
                }
                CompanionAction.SUBMIT_COOKIE -> {
                    applyManualCookie(payload.value, activeServer)
                }
            }
        }
        companionUrl = activeServer.start()
        pairingCode = activeServer.pairingCode
        withFrameNanos { }
        cancelRequester.safeRequestFocus()
    }

    DisposableEffect(activeServer, companionServer) {
        onDispose {
            onRegisterCompanionInputHandler(null)
            if (companionServer == null) {
                activeServer.stop()
            }
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
                val feedbackText = companionFeedbackResId?.let { stringResource(it) }
                Text(
                    text = feedbackText ?: stringResource(R.string.login_webview_hint),
                    color = if (feedbackText != null) colors.primaryAccent else colors.mutedText,
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

        // 2-Column Body: Focused QR + Domain Pairing Wizard (Left 66%) + Compact Live WebView Preview (Right 34%)
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Left Hero Pane: Large QR Code + Option A Domain & TV Code Instructions
            Column(
                modifier =
                    Modifier
                        .weight(0.66f)
                        .fillMaxHeight()
                        .background(colors.surface, RoundedCornerShape(AlunaraDimens.RadiusCard))
                        .border(BorderStroke(1.dp, colors.secondarySurface), RoundedCornerShape(AlunaraDimens.RadiusCard))
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .focusProperties { canFocus = false },
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                val activeUrl = companionUrl
                val activeCode = pairingCode
                if (activeUrl != null) {
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        QrCodeCanvas(
                            content = activeUrl,
                            modifier = Modifier.size(204.dp),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.qr_companion_scan_cta),
                                color = colors.primaryText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 19.sp,
                            )
                            Text(
                                text = stringResource(R.string.qr_companion_domain_instruction, LocalLoginCompanionServer.PAIRING_PORTAL_DISPLAY_URL),
                                color = colors.mutedText,
                                fontSize = 12.5.sp,
                                lineHeight = 17.5.sp,
                            )
                            if (!activeCode.isNullOrBlank()) {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .background(colors.secondarySurface, RoundedCornerShape(12.dp))
                                            .border(BorderStroke(1.dp, colors.primaryAccent.copy(alpha = 0.55f)), RoundedCornerShape(12.dp))
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = stringResource(R.string.qr_companion_code_label),
                                        color = colors.mutedText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        text = activeCode,
                                        color = colors.focusOutline,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp,
                                    )
                                }
                            }
                            Text(
                                text = stringResource(R.string.qr_companion_hint_steps),
                                color = colors.mutedText,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .background(colors.background.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.qr_companion_local_fallback, activeUrl),
                        color = colors.dimmedLyrics,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.qr_companion_unavailable),
                        color = colors.mutedText,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Right Secondary Pane: Compact WebView Live Preview + Helper Remote Controls
            Column(
                modifier =
                    Modifier
                        .weight(0.34f)
                        .fillMaxHeight()
                        .background(colors.surface, RoundedCornerShape(AlunaraDimens.RadiusCard))
                        .border(BorderStroke(1.dp, colors.secondarySurface), RoundedCornerShape(AlunaraDimens.RadiusCard))
                        .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.login_preview_label, currentHost),
                    color = colors.mutedText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.focusProperties { canFocus = false },
                )

                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .border(BorderStroke(colors.focusBorderWidth, webBorderColor), RoundedCornerShape(10.dp))
                            .background(colors.background, RoundedCornerShape(10.dp))
                            .padding(2.dp)
                            .focusRequester(webViewRequester)
                            .focusProperties {
                                up = cancelRequester
                                down = helperFocusInputRequester
                                left = cancelRequester
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
                                    Key.DirectionLeft -> {
                                        cancelRequester.safeRequestFocus()
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
                        factory = {
                            WebView(hostActivityContext).apply {
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
                                        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                                            cancelRequester.safeRequestFocus()
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
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                setInitialScale(70)
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
                                        activeServer.currentStep = LocalLoginCompanionServer.STEP_DONE
                                        activeServer.lastStatusHint =
                                            when (appLanguage) {
                                                AppLanguage.INDONESIAN ->
                                                    "Berhasil masuk YouTube Music — memverifikasi akun…"
                                                AppLanguage.JAPANESE ->
                                                    "YouTube Music にサインインしました — アカウントを確認中…"
                                                else ->
                                                    "Signed in to YouTube Music — verifying account…"
                                            }
                                        view.evaluateJavascript(buildExtractYtcfgJs(0), null)
                                    }
                                }

                                fun syncUrlAndHost(url: String?) {
                                    val parsedHost = url?.let { runCatching { Uri.parse(it).host }.getOrNull() }
                                    if (!parsedHost.isNullOrBlank()) {
                                        currentHost = parsedHost
                                        activeServer.currentHost = parsedHost
                                    }
                                    if (!captured.get()) {
                                        inferStepFromUrl(url, language = appLanguage)?.let { (step, hint) ->
                                            currentStep = step
                                            activeServer.currentStep = step
                                            activeServer.lastStatusHint = hint
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
                                                    activeServer.currentStep = LocalLoginCompanionServer.STEP_DONE
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
                                                    activeServer.currentStep = step
                                                    if (hint.isNotBlank()) {
                                                        activeServer.lastStatusHint = hint
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
                                                view.evaluateJavascript(installStepWatcherJs, null)
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
                                                view.evaluateJavascript(installStepWatcherJs, null)
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
                                    up = webViewRequester
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
                                    up = webViewRequester
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
                                    up = webViewRequester
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

private fun webViewStepLabel(
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

private fun inferStepFromUrl(
    url: String?,
    language: AppLanguage,
): Pair<String, String>? {
    if (url.isNullOrBlank()) return null
    val lower = url.lowercase()
    return when {
        lower.startsWith(YOUTUBE_MUSIC_ORIGIN) || lower.contains("://music.youtube.com") ->
            LocalLoginCompanionServer.STEP_DONE to
                webViewStepLabel(
                    language = language,
                    id = "Langkah 4 · Berhasil masuk YouTube Music",
                    ja = "ステップ 4 · YouTube Music にサインインしました",
                    en = "Step 4 · Signed in to YouTube Music",
                )
        lower.contains("/challenge/pwd") || lower.contains("/signin/v2/challenge/pwd") ->
            LocalLoginCompanionServer.STEP_PASSWORD to
                webViewStepLabel(
                    language = language,
                    id = "Langkah 2 dari 3 · Masukkan Kata Sandi akun Google",
                    ja = "ステップ 2/3 · Google アカウントのパスワードを入力",
                    en = "Step 2 of 3 · Enter Google account password",
                )
        lower.contains("/challenge/dp") || lower.contains("/challenge/ootp") ->
            LocalLoginCompanionServer.STEP_VERIFY to
                webViewStepLabel(
                    language = language,
                    id = "Langkah 3 dari 3 · Ketuk 'Ya, itu saya' di notifikasi Google Prompt HP",
                    ja = "ステップ 3/3 · スマホの Google 通知で「はい、私です」をタップ",
                    en = "Step 3 of 3 · Tap 'Yes, it's me' on your phone's Google Prompt",
                )
        lower.contains("/challenge/") ->
            LocalLoginCompanionServer.STEP_VERIFY to
                webViewStepLabel(
                    language = language,
                    id = "Langkah 3 dari 3 · Verifikasi 2FA / Kode OTP di HP",
                    ja = "ステップ 3/3 · スマホで 2段階認証 / OTP コードを確認",
                    en = "Step 3 of 3 · Complete 2FA / OTP verification on phone",
                )
        lower.contains("/identifier") || lower.contains("accounts.google.com/servicelogin") ->
            LocalLoginCompanionServer.STEP_EMAIL to
                webViewStepLabel(
                    language = language,
                    id = "Langkah 1 dari 3 · Masukkan Email / No. HP akun Google",
                    ja = "ステップ 1/3 · Google メールアドレス / 電話番号を入力",
                    en = "Step 1 of 3 · Enter Google Email / Phone",
                )
        else -> null
    }
}

private fun buildCheckStepOnceJs(language: AppLanguage): String {
    val defaultEmail =
        JSONObject.quote(
            webViewStepLabel(
                language = language,
                id = "Langkah 1 dari 3 · Masukkan Email / No. HP Google",
                ja = "ステップ 1/3 · Google メールアドレス / 電話番号を入力",
                en = "Step 1 of 3 · Enter Google Email / Phone",
            ),
        )
    val doneHint =
        JSONObject.quote(
            webViewStepLabel(
                language = language,
                id = "Langkah 4 · Berhasil masuk YouTube Music — memverifikasi akun…",
                ja = "ステップ 4 · YouTube Music にサインイン完了 — アカウントを確認中…",
                en = "Step 4 · Signed in to YouTube Music — verifying account…",
            ),
        )
    val step1Prefix = JSONObject.quote(webViewStepLabel(language, "Langkah 1 dari 3 · ", "ステップ 1/3 · ", "Step 1 of 3 · "))
    val step2Prefix = JSONObject.quote(webViewStepLabel(language, "Langkah 2 dari 3 · ", "ステップ 2/3 · ", "Step 2 of 3 · "))
    val step2Default =
        JSONObject.quote(
            webViewStepLabel(
                language = language,
                id = "Langkah 2 dari 3 · Masukkan Kata Sandi akun Google",
                ja = "ステップ 2/3 · Google アカウントのパスワードを入力",
                en = "Step 2 of 3 · Enter Google account password",
            ),
        )
    val step2Loading =
        JSONObject.quote(
            webViewStepLabel(
                language = language,
                id = "Langkah 2 dari 3 · Memuat kolom Kata Sandi…",
                ja = "ステップ 2/3 · パスワード入力欄を読み込み中…",
                en = "Step 2 of 3 · Loading Password field…",
            ),
        )
    val step3Prefix = JSONObject.quote(webViewStepLabel(language, "Langkah 3 dari 3 · ", "ステップ 3/3 · ", "Step 3 of 3 · "))
    val step3OtpSuffix = JSONObject.quote(webViewStepLabel(language, " (Isi kode OTP di HP)", "（スマホで OTP コードを入力）", " (Enter OTP code on phone)"))
    val step3OtpDefault =
        JSONObject.quote(
            webViewStepLabel(
                language = language,
                id = "Langkah 3 dari 3 · Masukkan Kode Verifikasi OTP",
                ja = "ステップ 3/3 · OTP 認証コードを入力",
                en = "Step 3 of 3 · Enter OTP Verification Code",
            ),
        )
    val step3PromptSuffix = JSONObject.quote(webViewStepLabel(language, " (Konfirmasi di HP)", "（スマホで承認）", " (Approve on phone)"))
    val step3PromptDefault =
        JSONObject.quote(
            webViewStepLabel(
                language = language,
                id = "Langkah 3 dari 3 · Setujui Google Prompt di HP",
                ja = "ステップ 3/3 · スマホで Google 通知を承認",
                en = "Step 3 of 3 · Approve Google Prompt on phone",
            ),
        )
    return """
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
            var hint = $defaultEmail;
            if (host.indexOf('music.youtube.com') >= 0) {
                step = 'done';
                hint = $doneHint;
                if (!window.__alunaraMusicNotified && window.AlunaraAuth && window.AlunaraAuth.onMusicHomeReached) {
                    window.__alunaraMusicNotified = true;
                    window.AlunaraAuth.onMusicHomeReached();
                }
            } else if (hasPassword) {
                step = 'password';
                hint = heading ? ($step2Prefix + heading) : $step2Default;
            } else if (href.indexOf('/challenge/') >= 0 || hasOtp || document.querySelector('[data-challengetype]')) {
                step = 'verify';
                if (hasOtp) {
                    hint = heading ? ($step3Prefix + heading + $step3OtpSuffix) : $step3OtpDefault;
                } else {
                    hint = heading ? ($step3Prefix + heading + $step3PromptSuffix) : $step3PromptDefault;
                }
            } else if (hasEmail) {
                step = 'email';
                hint = heading ? ($step1Prefix + heading) : $defaultEmail;
            } else if (href.indexOf('/challenge/pwd') >= 0) {
                step = 'password';
                hint = $step2Loading;
            }
            var sig = step + '|' + hint;
            if (window.__alunaraLastReportedStep !== sig) {
                window.__alunaraLastReportedStep = sig;
                if (window.AlunaraAuth && window.AlunaraAuth.onLoginStep) {
                    window.AlunaraAuth.onLoginStep(step, hint);
                }
            }
        })();
        """.trimIndent()
}

private fun buildInstallStepWatcherJs(language: AppLanguage): String {
    val checkStepJs = buildCheckStepOnceJs(language)
    return """
        (function() {
            window.__alunaraCheckStep = function() {
                $checkStepJs
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
}

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
