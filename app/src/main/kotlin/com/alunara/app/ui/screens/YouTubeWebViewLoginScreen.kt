package com.alunara.app.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Text
import com.alunara.app.R
import com.alunara.app.ui.components.TvFocusableButton
import com.alunara.app.ui.theme.AlunaraDimens
import com.alunara.app.ui.theme.AlunaraPalette
import com.alunara.core.domain.model.AccountCredentials
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

private const val YOUTUBE_MUSIC_LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&uilel=3&passive=true&continue=https%3A%2F%2Fmusic.youtube.com%2F"

private const val YOUTUBE_MUSIC_ORIGIN = "https://music.youtube.com"

private const val EXTRACT_YTCFG_JS =
    """
    (function() {
        try {
            var cfg = (window.yt && window.yt.config_) || (window.ytcfg && window.ytcfg.data_) || {};
            var vd = cfg.VISITOR_DATA || (window.ytcfg && window.ytcfg.get && window.ytcfg.get('VISITOR_DATA')) || '';
            var ds = cfg.DATASYNC_ID || (window.ytcfg && window.ytcfg.get && window.ytcfg.get('DATASYNC_ID')) || '';
            window.AlunaraAuth.onYtConfig(String(vd || ''), String(ds || ''));
        } catch (e) {
            window.AlunaraAuth.onYtConfig('', '');
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
    var currentHost by remember { mutableStateOf("accounts.google.com") }
    var assistInputText by remember { mutableStateOf("") }
    var inputFocused by remember { mutableStateOf(false) }
    val assistInputRequester = remember { FocusRequester() }
    val webViewHolder = remember { mutableStateOf<WebView?>(null) }
    val captured = remember { AtomicBoolean(false) }

    fun injectHighlightElement(direction: Int) {
        webViewHolder.value?.evaluateJavascript(buildHighlightDomScript(direction), null)
    }

    fun injectFillAndNext() {
        val text = assistInputText
        if (text.isEmpty()) {
            injectHighlightElement(0)
            webViewHolder.value?.evaluateJavascript(CLICK_NEXT_OR_ACTIVE_JS, null)
            return
        }
        webViewHolder.value?.evaluateJavascript(buildFillAndSubmitScript(text), null)
        assistInputText = ""
    }

    fun injectClickHighlighted() {
        webViewHolder.value?.evaluateJavascript(CLICK_HIGHLIGHTED_JS, null)
    }

    LaunchedEffect(Unit) {
        assistInputRequester.requestFocus()
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewHolder.value?.apply {
                stopLoading()
                clearHistory()
                removeAllViews()
                destroy()
            }
            webViewHolder.value = null
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(AlunaraPalette.Background),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(AlunaraPalette.Surface)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TvFocusableButton(
                text = stringResource(R.string.cancel),
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                onClick = onCancel,
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
                    color = AlunaraPalette.PrimaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.login_webview_hint),
                    color = AlunaraPalette.MutedText,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TvFocusableButton(
                text = stringResource(R.string.webview_prev_element),
                onClick = { injectHighlightElement(-1) },
            )
            TvFocusableButton(
                text = stringResource(R.string.webview_next_element),
                onClick = { injectHighlightElement(1) },
            )
            TvFocusableButton(
                text = stringResource(R.string.webview_click_element),
                icon = Icons.Rounded.TouchApp,
                onClick = { injectClickHighlighted() },
            )
        }

        // TV Remote Input Assist Bar: native Gboard TV text input + Fill & Next injector for WebView forms
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(AlunaraPalette.Background)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val shape = RoundedCornerShape(AlunaraDimens.RadiusControl)
            val fieldBorder by animateColorAsState(
                targetValue = if (inputFocused) AlunaraPalette.FocusRingStrong else AlunaraPalette.SecondarySurface,
                animationSpec = tween(150),
                label = "webview_assist_border",
            )
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(AlunaraDimens.ControlHeight)
                        .background(AlunaraPalette.Surface, shape)
                        .border(BorderStroke(AlunaraDimens.FocusBorderWidth, fieldBorder), shape)
                        .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (assistInputText.isEmpty()) {
                    Text(
                        text = stringResource(R.string.webview_input_hint),
                        color = AlunaraPalette.DimmedLyrics,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = assistInputText,
                    onValueChange = { assistInputText = it },
                    singleLine = true,
                    textStyle =
                        TextStyle(
                            color = AlunaraPalette.PrimaryText,
                            fontSize = 15.sp,
                        ),
                    cursorBrush = SolidColor(AlunaraPalette.PrimaryAccent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { injectFillAndNext() }),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(assistInputRequester)
                            .onFocusChanged { inputFocused = it.isFocused },
                )
            }

            TvFocusableButton(
                text = stringResource(R.string.webview_fill_and_next),
                icon = Icons.AutoMirrored.Rounded.Send,
                highlighted = true,
                onClick = { injectFillAndNext() },
            )
        }

        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
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
                        AlunaraAuthJsBridge { visitorData, dataSyncId ->
                            val view = webViewHolder.value ?: return@AlunaraAuthJsBridge
                            view.post {
                                val cookie = latestCookie
                                if (hasSapisidCookie(cookie) && captured.compareAndSet(false, true)) {
                                    onCredentialsCaptured(
                                        AccountCredentials(
                                            cookie = cookie,
                                            dataSyncId = dataSyncId,
                                            visitorData = visitorData,
                                        ),
                                    )
                                }
                            }
                        }
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
                                }
                                if (url != null && url.startsWith(YOUTUBE_MUSIC_ORIGIN) && !captured.get()) {
                                    cookieManager.flush()
                                    val cookie = cookieManager.getCookie(YOUTUBE_MUSIC_ORIGIN).orEmpty()
                                    if (hasSapisidCookie(cookie)) {
                                        latestCookie = cookie
                                        view?.evaluateJavascript(EXTRACT_YTCFG_JS, null)
                                    }
                                } else if (!captured.get()) {
                                    view?.evaluateJavascript(buildHighlightDomScript(0), null)
                                }
                            }
                        }

                    webViewHolder.value = this
                    loadUrl(YOUTUBE_MUSIC_LOGIN_URL)
                }
            },
        )
    }
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
        var target = nodes[idx];
        window.__alunaraPrevEl = target;
        window.__alunaraActiveEl = target;
        target.style.outline = '3px solid #FFD54F';
        target.style.outlineOffset = '2px';
        target.scrollIntoView({ behavior: 'smooth', block: 'center' });
        if ($direction !== 0) {
            target.focus();
        }
    })();
    """.trimIndent()

private fun buildFillAndSubmitScript(rawValue: String): String {
    val quotedValue = JSONObject.quote(rawValue)
    return """
        (function() {
            var val = $quotedValue;
            var active = window.__alunaraActiveEl;
            var isVisibleInput = function(el) {
                if (!el || el.tagName !== 'INPUT' || el.disabled) return false;
                if (el.type === 'hidden' || el.type === 'checkbox' || el.type === 'radio') return false;
                var r = el.getBoundingClientRect();
                return r.width > 0 && r.height > 0;
            };
            var target = isVisibleInput(active) ? active : null;
            if (!target) {
                var selectors = [
                    'input[type="email"]',
                    'input[name="identifier"]',
                    'input[type="password"]',
                    'input[name="Passwd"]',
                    'input[type="tel"]',
                    'input[type="text"]',
                    'input:not([type="hidden"])'
                ];
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
                var nextBtn = document.querySelector('#identifierNext button, #identifierNext, #passwordNext button, #passwordNext, button[jsname="LgbsSe"][type="submit"], button[type="submit"], [role="button"][jsname="LgbsSe"]');
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
    private val onContext: (visitorData: String?, dataSyncId: String?) -> Unit,
) {
    @JavascriptInterface
    fun onYtConfig(
        visitorData: String?,
        dataSyncId: String?,
    ) {
        onContext(
            visitorData?.takeIf { it.isNotBlank() && it != "null" },
            dataSyncId?.takeIf { it.isNotBlank() && it != "null" },
        )
    }
}

private fun hasSapisidCookie(cookie: String): Boolean =
    cookie.contains("SAPISID=") ||
        cookie.contains("__Secure-3PAPISID=") ||
        cookie.contains("__Secure-1PAPISID=")
