package com.alunara.app.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Text
import com.alunara.app.R
import com.alunara.app.ui.components.TvFocusableButton
import com.alunara.app.ui.theme.AlunaraPalette
import com.alunara.core.domain.model.AccountCredentials
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
    val webViewHolder = remember { mutableStateOf<WebView?>(null) }
    val captured = remember { AtomicBoolean(false) }

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
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
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
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.login_webview_hint),
                    color = AlunaraPalette.MutedText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
                                }
                            }
                        }

                    webViewHolder.value = this
                    requestFocus()
                    loadUrl(YOUTUBE_MUSIC_LOGIN_URL)
                }
            },
        )
    }
}

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
