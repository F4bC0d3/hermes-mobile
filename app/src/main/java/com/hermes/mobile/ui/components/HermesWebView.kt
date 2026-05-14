package com.hermes.mobile.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.hermes.mobile.data.SessionStore

/** Imperative bridge so the surrounding Compose UI can drive the WebView. */
class WebViewControls {
    private var view: WebView? = null
    var busy: Boolean = false
        internal set

    internal fun bind(v: WebView) { view = v }
    internal fun unbind() { view = null }

    fun loadUrl(url: String) { view?.loadUrl(url) }
    fun reload() { view?.reload() }
    fun goBack() { view?.takeIf { it.canGoBack() }?.goBack() }
    fun canGoBack(): Boolean = view?.canGoBack() == true
    fun evalJs(script: String) { view?.evaluateJavascript(script, null) }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HermesWebView(
    sessionStore: SessionStore,
    controls: WebViewControls,
    darkTheme: Boolean,
    onTitleChanged: (String?) -> Unit,
) {
    val context = LocalContext.current
    var progress by remember { mutableFloatStateOf(0f) }
    var loading by remember { mutableFloatStateOf(0f) }

    var fileChooserCallback: ValueCallback<Array<Uri>>? = remember { null }
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = WebChromeClient.FileChooserParams
            .parseResult(result.resultCode, result.data)
        fileChooserCallback?.onReceiveValue(uris ?: emptyArray())
        fileChooserCallback = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopStart,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setBackgroundColor(Color.TRANSPARENT)

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mediaPlaybackRequiresUserGesture = false
                        allowFileAccess = false
                        allowContentAccess = false
                        setGeolocationEnabled(false)
                        textZoom = 100
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            safeBrowsingEnabled = true
                        }
                        userAgentString = "$userAgentString HermesMobile/1.0"
                    }

                    // Persist webui session cookie across launches.
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, false)
                    sessionStore.spaceUrl?.let { url ->
                        sessionStore.sessionCookie?.let { cookie ->
                            cookieManager.setCookie(url, "hermes_session=$cookie; Path=/; HttpOnly")
                            cookieManager.flush()
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean {
                            val url = request.url.toString()
                            val space = sessionStore.spaceUrl.orEmpty()
                            // Stay inside the Space; everything else opens externally.
                            if (space.isNotEmpty() && url.startsWith(space)) return false
                            return runCatching {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                                true
                            }.getOrDefault(false)
                        }

                        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                            controls.busy = true
                            super.onPageStarted(view, url, favicon)
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            controls.busy = false
                            // Inject mobile niceties: tighter padding, hide WebUI's own
                            // sidebar drawer (we use the native one), keep composer focused.
                            val css = """
                                (function(){
                                  var s = document.getElementById('hermes-mobile-css');
                                  if (s) return;
                                  s = document.createElement('style');
                                  s.id = 'hermes-mobile-css';
                                  s.textContent = `
                                    body { -webkit-tap-highlight-color: transparent; }
                                    .sidebar-toggle, .mobile-nav-toggle { display: none !important; }
                                    .composer { padding-bottom: env(safe-area-inset-bottom, 8px) !important; }
                                  `;
                                  document.head.appendChild(s);
                                })();
                            """.trimIndent()
                            view.evaluateJavascript(css, null)
                            super.onPageFinished(view, url)
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress / 100f
                            loading = if (newProgress in 1..99) 1f else 0f
                        }
                        override fun onReceivedTitle(view: WebView?, title: String?) {
                            onTitleChanged(title)
                        }
                        override fun onPermissionRequest(request: PermissionRequest) {
                            // Hand mic and camera straight through — voice input,
                            // possible video tools.
                            request.grant(request.resources)
                        }
                        override fun onShowFileChooser(
                            webView: WebView?,
                            filePathCallback: ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?,
                        ): Boolean {
                            fileChooserCallback?.onReceiveValue(null)
                            fileChooserCallback = filePathCallback
                            val intent = fileChooserParams?.createIntent() ?: return false
                            return runCatching {
                                filePickerLauncher.launch(intent)
                                true
                            }.getOrDefault(false)
                        }
                        override fun onCreateWindow(
                            view: WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: Message?,
                        ): Boolean {
                            // Block popups; user gestures redirect through shouldOverrideUrlLoading.
                            return false
                        }
                    }

                    // Initial load -> primary chat UI.
                    val space = sessionStore.spaceUrl.orEmpty()
                    if (space.isNotBlank()) loadUrl("$space/")
                }
            },
            update = { webView ->
                controls.bind(webView)
                // Theme sync: hint the WebUI to flip themes via its settings cookie.
                val themeCookie = if (darkTheme) "dark" else "light"
                CookieManager.getInstance().setCookie(
                    sessionStore.spaceUrl ?: "",
                    "hermes_theme=$themeCookie; Path=/; SameSite=Lax",
                )
            },
            onRelease = { webView ->
                controls.unbind()
                webView.stopLoading()
                webView.removeAllViews()
                webView.destroy()
            },
        )

        // Slim progress bar at the very top while pages are loading.
        if (progress in 0.01f..0.99f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface,
            )
        }
    }

    DisposableEffect(Unit) { onDispose { controls.unbind() } }
}
