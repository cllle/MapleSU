package com.maplesu.core.ui.webui

import android.annotation.SuppressLint
import android.animation.ValueAnimator
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.maplesu.core.R
import com.maplesu.core.ui.LocalUiMode
import com.maplesu.core.ui.MainActivity
import com.maplesu.core.ui.UiMode
import com.maplesu.core.ui.navigation3.LocalNavigator
import com.maplesu.core.ui.navigation3.Route
import com.maplesu.core.ui.navigation3.rememberNavigator
import org.json.JSONObject
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.maplesu.manager.modules.webui.ModuleWebUiPlan
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.ScriptHandler
import com.maplesu.core.ui.util.AppearancePreferences
import dev.maplesu.manager.t3.T3Runtime
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * 显示「环境模块」自己的 WebUI 页面。
 *
 * 与 [WebUIActivity] 的区别是**页面来源**：那个 Activity 用 `WebViewAssetLoader` 直接读
 * 模块的 `webroot` 目录（静态文件模型），而环境里的模块由 env init 按需拉起一个只监听
 * `127.0.0.1` 的 HTTP 服务，SDK 的 open 调用负责启动它并回传端口。所以这里不做资源拦截，
 * 只把 SDK 给出的 URL 原样加载——页面自己的 POST 接口就在同一 origin 上。
 *
 * 页面加载失败时保留主题一致的重试页；渲染进程退出时提示返回重新打开。
 * 失败状态保留技术原因供排查，展示文案只说明下一步操作。
 */
class ModuleWebUIActivity : ComponentActivity() {
    private val t3 by lazy { T3Runtime.get(applicationContext) }
    private val t3Owner = Any()
    private var t3DisableRedirected = false
    private var pageState = WebUIState()

    companion object {
        const val EXTRA_URL = "dev.maplesu.manager.extra.MODULE_WEB_UI_URL"
        const val EXTRA_MODULE_ID = "dev.maplesu.manager.extra.MODULE_WEB_UI_ID"
        const val EXTRA_TITLE = "dev.maplesu.manager.extra.MODULE_WEB_UI_TITLE"
        const val EXTRA_MODULE_DIR = "dev.maplesu.manager.extra.MODULE_WEB_UI_DIR"
        const val EXTRA_MODULE_INFO = "dev.maplesu.manager.extra.MODULE_WEB_UI_INFO"
        internal const val EXTRA_SESSION_REVISION = "dev.maplesu.manager.extra.MODULE_WEB_UI_REVISION"
        internal const val EXTRA_OWNER_UID = "dev.maplesu.manager.extra.MODULE_WEB_UI_OWNER_UID"
        internal const val EXTRA_OWNER_EPOCH = "dev.maplesu.manager.extra.MODULE_WEB_UI_OWNER_EPOCH"
        internal const val EXTRA_OWNER_HOST = "dev.maplesu.manager.extra.MODULE_WEB_UI_OWNER_HOST"

        /** 连上但没回 HTTP 时重试两次；服务已经在监听就不该再等。 */
        private const val MAX_RETRIES = 2
        private const val EXTERNAL_NAVIGATION_BLOCKED = "WEBUI_EXTERNAL_NAVIGATION_BLOCKED"

        /** 只接受回环 http，避免这个 Activity 被当成任意 URL 的容器。 */
        fun isLoopbackUrl(url: String?): Boolean {
            return ModuleWebOrigin.from(url) != null
        }
    }

    private var webView: WebView? = null
    private var backCallback: OnBackPressedCallback? = null
    private var documentAppearance: ScriptHandler? = null
    private var currentThemeScript = ""
    private var automaticRetries = 0
    private var pendingPageRetry: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        t3.attach(t3Owner)
        lifecycleScope.launch {
            t3.secondsRemaining.collect { seconds ->
                if (seconds == 0) {
                    finishAffinity()
                    finishAndRemoveTask()
                } else if (seconds != null && !t3DisableRedirected) {
                    t3DisableRedirected = true
                    startActivity(Intent(this@ModuleWebUIActivity, MainActivity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    ))
                }
            }
        }
        if (isFinishing) return
        bindRequest(useSeed = savedInstanceState == null)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        webView?.let(::releaseWebView) ?: pageState.dispose(this)
        backCallback?.remove()
        backCallback = null
        automaticRetries = 0
        setIntent(intent)
        pageState = WebUIState()
        bindRequest(useSeed = true)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun bindRequest(useSeed: Boolean) {
        val pageState = this.pageState
        val url = intent.getStringExtra(EXTRA_URL)?.takeIf { isLoopbackUrl(it) }
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val moduleId = intent.getStringExtra(EXTRA_MODULE_ID).orEmpty()
        if (url == null) {
            // 没有 URL 说明调用方解析失败：直接结束，但把原因留在日志里（不是静默 finish）。
            android.util.Log.w("MapleSUWebUi", "ModuleWebUIActivity without a loopback URL (module=$moduleId)")
            finish()
            return
        }

        val revision = if (intent.hasExtra(EXTRA_SESSION_REVISION))
            intent.getLongExtra(EXTRA_SESSION_REVISION, 0L)
        else dev.maplesu.manager.modules.webui.ModuleWebUiSessions.revision(moduleId)
        val ownerScope = if (intent.hasExtra(EXTRA_OWNER_UID) || intent.hasExtra(EXTRA_OWNER_EPOCH) ||
            intent.hasExtra(EXTRA_OWNER_HOST)) dev.maplesu.manager.modules.webui.ModuleWebUiSessions.OwnerScope(
                intent.getIntExtra(EXTRA_OWNER_UID, -1), intent.getLongExtra(EXTRA_OWNER_EPOCH, 0L),
                intent.getStringExtra(EXTRA_OWNER_HOST).orEmpty()) else null
        if (!pageState.bindModuleSession(this, moduleId, revision, ownerScope)) {
            finish()
            return
        }
        val origin = requireNotNull(ModuleWebOrigin.from(url))
        val moduleDir = dev.maplesu.manager.modules.webui.ModuleWebUiPlan.moduleDirectoryOrNull(
            intent.getStringExtra(EXTRA_MODULE_DIR))
        if (dev.maplesu.manager.modules.webui.ModuleWebUiPlan.isUltraModuleId(moduleId) && moduleDir == null) {
            android.util.Log.w("MapleSUWebUi", "Native module request without a verified module directory")
            finish()
            return
        }
        if (moduleDir != null) {
            pageState.modDir = moduleDir
            pageState.moduleId = moduleId
            pageState.moduleName = title
            pageState.moduleInfoJson = if (useSeed) intent.getStringExtra(EXTRA_MODULE_INFO) else null
        }

        backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = navigateBack()
        }.also { onBackPressedDispatcher.addCallback(this, it) }

        val settingsRepo = com.maplesu.core.data.repository.SettingsRepositoryImpl()
        val initialSettings = com.maplesu.core.ui.theme.ThemeController.getAppSettings(settingsRepo)
        val initialMode = UiMode.fromValue(settingsRepo.uiMode)
        setContent {
        key(pageState) {
            if (pageState.isDisposed) {
                LaunchedEffect(Unit) { finish() }
            } else {
            var state by remember { mutableStateOf<LoadState>(LoadState.Loading) }

            var appSettings by remember { mutableStateOf(initialSettings) }
            var uiMode by remember { mutableStateOf(initialMode) }
            DisposableEffect(Unit) {
                val prefs = AppearancePreferences.prefs(this@ModuleWebUIActivity)
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key in AppearancePreferences.keys) {
                        appSettings = com.maplesu.core.ui.theme.ThemeController.getAppSettings(settingsRepo)
                        uiMode = UiMode.fromValue(settingsRepo.uiMode)
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }
            val navigator = rememberNavigator(Route.Module)
            androidx.compose.runtime.CompositionLocalProvider(LocalUiMode provides uiMode, LocalNavigator provides navigator) {
            com.maplesu.core.ui.theme.KernelSUTheme(appSettings = appSettings, uiMode = uiMode) {
            val fileLauncher = rememberFileLauncher(pageState)
            when (LocalUiMode.current) {
                UiMode.Miuix -> HandleWebUIEventMiuix(pageState, fileLauncher)
                UiMode.Material -> HandleWebUIEventMaterial(pageState, fileLauncher)
            }
            LaunchedEffect(pageState.uiEvent) {
                if (pageState.uiEvent is WebUIEvent.Close) finish()
            }
            val palette = moduleWebUiPalette()
            val themeScript = remember(palette) { palette.documentScript() }
            SideEffect {
                currentThemeScript = themeScript
                if (pageState.isModuleSessionCurrent) webView?.let { applyDocumentAppearance(it, url, themeScript) }
            }
            val density = LocalDensity.current
            val layoutDirection = LocalLayoutDirection.current
            val deviceInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
            LaunchedEffect(density, layoutDirection, deviceInsets, pageState.isInsetsEnabled) {
                snapshotFlow {
                    if (pageState.isInsetsEnabled) Insets(
                        // The toolbar already keeps the document below the top system inset.
                        top = 0,
                        bottom = (deviceInsets.getBottom(density) / density.density).toInt(),
                        left = (deviceInsets.getLeft(density, layoutDirection) / density.density).toInt(),
                        right = (deviceInsets.getRight(density, layoutDirection) / density.density).toInt(),
                    ) else Insets(0, 0, 0, 0)
                }.collect { insets ->
                    if (pageState.currentInsets != insets) {
                        pageState.currentInsets = insets
                        webView?.let { applyPageInsets(it, origin) }
                    }
                }
            }

            ModuleWebUiFrame(
                title = title.ifBlank { moduleId }.ifBlank { "WebUI" },
                onBack = ::navigateBack,
                edgeToEdge = pageState.isInsetsEnabled,
            ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.background)
            ) {
                // WebView 常驻：失败后按「重试」只是 loadUrl 同一个实例，不重建 renderer。
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        (if (moduleDir == null) WebView(context)
                        else createHttpModuleWebView(context, url, pageState)).apply {
                            setBackgroundColor(Color.TRANSPARENT)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            pageState.webView = this
                            webChromeClient = moduleWebChromeClient(pageState)
                            setDownloadListener { target, _, _, _, _ ->
                                if (Uri.parse(target).host != BlobDownloadHandler.HOST) pageState.downloadBridge?.openExternal(target)
                            }
                            webViewClient = object : WebViewClient() {
                                private var mainFrameFailed = false

                                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                                    if (pageState.acceptsPage(view)) pageState.downloadBridge?.blobs?.shouldInterceptRequest(request) else null

                                private fun reportBlockedNavigation(view: WebView) {
                                    mainFrameFailed = true
                                    cancelAutomaticRetry(view)
                                    state = LoadState.Failed(
                                        EXTERNAL_NAVIGATION_BLOCKED,
                                        getString(R.string.module_webui_external_blocked),
                                        url,
                                    )
                                }

                                private fun acceptsCurrentPage(view: WebView?): Boolean =
                                    view != null && webView === view && pageState.acceptsPage(view)

                                private fun acceptsMainFrameError(view: WebView, request: WebResourceRequest): Boolean =
                                    request.isForMainFrame && acceptsCurrentPage(view) &&
                                        origin.contains(request.url.toString()) &&
                                        (state as? LoadState.Failed)?.code != EXTERNAL_NAVIGATION_BLOCKED

                                override fun onPageStarted(view: WebView?, startedUrl: String?, favicon: Bitmap?) {
                                    if (view == null || !acceptsCurrentPage(view)) return
                                    if (!origin.contains(startedUrl)) {
                                        reportBlockedNavigation(view)
                                        view.stopLoading()
                                    } else {
                                        cancelAutomaticRetry(view)
                                        mainFrameFailed = false
                                    }
                                }

                                override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                                    if (mainFrameFailed || view == null || !acceptsCurrentPage(view) ||
                                        !origin.contains(finishedUrl) ||
                                        !origin.contains(view.url)) return
                                    cancelAutomaticRetry(view)
                                    automaticRetries = 0
                                    applyDocumentAppearance(view, url, currentThemeScript)
                                    applyPageInsets(view, origin)
                                    if (pageState.downloadBridge != null) {
                                        view.evaluateJavascript(loadWebUIDownloadScript(this@ModuleWebUIActivity), null)
                                    }
                                    if (state is LoadState.Loading) state = LoadState.Ready
                                }

                                override fun onReceivedError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    error: WebResourceError,
                                ) {
                                    if (!acceptsMainFrameError(view, request)) return
                                    mainFrameFailed = true
                                    cancelAutomaticRetry(view)
                                    if (automaticRetries < MAX_RETRIES) {
                                        automaticRetries++
                                        val retry = object : Runnable {
                                            override fun run() {
                                                if (pendingPageRetry !== this) return
                                                pendingPageRetry = null
                                                if (acceptsCurrentPage(view) &&
                                                    !isFinishing && !isDestroyed) view.loadUrl(url)
                                            }
                                        }
                                        pendingPageRetry = retry
                                        view.postDelayed(retry, 400)
                                        return
                                    }
                                    val detail = "${error.errorCode} ${error.description}"
                                    state = LoadState.Failed(
                                        code = if (
                                            error.errorCode == WebViewClient.ERROR_HOST_LOOKUP ||
                                            error.errorCode == WebViewClient.ERROR_CONNECT
                                        ) {
                                            "PORT_NOT_LISTENING"
                                        } else {
                                            "WEBVIEW_ERROR"
                                        },
                                        detail = detail,
                                        url = url,
                                    )
                                }

                                override fun onReceivedHttpError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    errorResponse: WebResourceResponse,
                                ) {
                                    if (!acceptsMainFrameError(view, request)) return
                                    val failure = ModuleWebUiPlan.failureFromHttpStatus(
                                        errorResponse.statusCode,
                                        request.url?.toString().orEmpty(),
                                    ) ?: return
                                    mainFrameFailed = true
                                    cancelAutomaticRetry(view)
                                    state = LoadState.Failed(failure.code, failure.detail, url)
                                }

                                override fun onRenderProcessGone(
                                    view: WebView,
                                    detail: RenderProcessGoneDetail,
                                ): Boolean {
                                    if (webView !== view) return true
                                    val reportFailure = acceptsCurrentPage(view)
                                    (view.parent as? ViewGroup)?.removeView(view)
                                    releaseWebView(view)
                                    if (reportFailure) state = LoadState.Failed(
                                        code = "WEBVIEW_RENDERER_GONE",
                                        detail = "renderer died (crash=${detail.didCrash()}), reopen the WebUI",
                                        url = url,
                                    )
                                    return true
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: WebResourceRequest,
                                ): Boolean {
                                    if (!acceptsCurrentPage(view)) return true
                                    if (!request.isForMainFrame) return false
                                    if (origin.contains(request.url.toString())) return false
                                    if (state is LoadState.Loading) {
                                        reportBlockedNavigation(view)
                                        return true
                                    }
                                    // 外链**不再**交给系统浏览器：这个 Activity 只承载模块本机页面，
                                    // 弹「打开方式」选择器正是用户投诉的那个问题。拦下并说明原因。
                                    android.widget.Toast.makeText(
                                        view.context,
                                        view.context.getString(
                                            R.string.module_webui_external_blocked
                                        ),
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                    return true
                                }
                            }
                            installDocumentAppearance(this, url, themeScript)
                            webView = this
                            loadUrl(url)
                        }
                    },
                    onRelease = { view ->
                        releaseWebView(view)
                    },
                )

                when (val current = state) {
                    is LoadState.Loading -> Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (LocalUiMode.current == UiMode.Miuix) {
                            InfiniteProgressIndicator()
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.35f))
                        }
                        ModuleWebUiText(stringResource(R.string.webui_loading), palette.summary)
                    }

                    is LoadState.Failed -> ErrorPanel(
                        title = title.ifBlank { moduleId },
                        failure = current,
                        onRetry = {
                            webView?.takeIf { pageState.acceptsPage(it) }?.let { view ->
                                automaticRetries = 0
                                state = LoadState.Loading
                                cancelAutomaticRetry(view)
                                view.stopLoading()
                                view.loadUrl(url)
                            }
                        },
                        onClose = { finish() },
                    )

                    is LoadState.Ready -> Unit
                }
            }
            }
            }
            }
            }
        }
        }
    }

    sealed interface LoadState {
        data object Loading : LoadState
        data object Ready : LoadState
        data class Failed(val code: String, val detail: String, val url: String) : LoadState
    }

    private fun navigateBack() {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else finish()
    }

    private fun cancelAutomaticRetry(view: WebView) {
        pendingPageRetry?.let { view.removeCallbacks(it) }
        pendingPageRetry = null
    }

    private fun releaseWebView(view: WebView) {
        if (webView !== view) return
        cancelAutomaticRetry(view)
        if (!pageState.isDisposed) documentAppearance?.remove()
        documentAppearance = null
        webView = null
        if (!pageState.isDisposed) view.stopLoading()
        pageState.dispose(this)
    }

    private fun installDocumentAppearance(view: WebView, entryUrl: String, script: String) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        val entry = Uri.parse(entryUrl)
        val origin = "${entry.scheme}://${entry.host}" + if (entry.port == -1) "" else ":${entry.port}"
        documentAppearance?.remove()
        documentAppearance = WebViewCompat.addDocumentStartJavaScript(view, script, setOf(origin))
    }

    private fun applyDocumentAppearance(view: WebView, entryUrl: String, script: String) {
        // Presentation data only, and only on the exact module origin that was opened.
        // No privileged bridge, URL token or root state is exposed to the document.
        val entry = Uri.parse(entryUrl)
        val current = view.url?.let(Uri::parse) ?: return
        if (current.scheme == entry.scheme && current.host == entry.host && current.port == entry.port) {
            installDocumentAppearance(view, entryUrl, script)
            view.evaluateJavascript(script, null)
        }
    }

    private fun applyPageInsets(view: WebView, origin: ModuleWebOrigin) {
        if (!pageState.acceptsPage(view) || !origin.contains(view.url)) return
        val css = JSONObject.quote(pageState.currentInsets.css)
        view.evaluateJavascript("""(function(){var r=document.documentElement;if(!r)return;
            var s=document.getElementById('maplesu-window-insets');
            if(!s){s=document.createElement('style');s.id='maplesu-window-insets';r.appendChild(s);}
            s.textContent=$css;})()""".trimIndent(), null)
    }

    @Composable
    private fun ErrorPanel(
        title: String,
        failure: LoadState.Failed,
        onRetry: () -> Unit,
        onClose: () -> Unit,
    ) {
        val palette = moduleWebUiPalette()
        val rendererGone = failure.code == "WEBVIEW_RENDERER_GONE"
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.background)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ModuleWebUiText(title.ifBlank { "WebUI" }, palette.text, title = true)
            ModuleWebUiText(getString(R.string.module_webui_error_title), palette.text)
            ModuleWebUiText(
                getString(when (failure.code) {
                    "WEBVIEW_RENDERER_GONE" -> R.string.webui_reopen_description
                    EXTERNAL_NAVIGATION_BLOCKED -> R.string.module_webui_external_blocked
                    else -> R.string.webui_retry_description
                }),
                palette.summary,
            )
            if (LocalUiMode.current == UiMode.Miuix) {
                if (!rendererGone) MiuixTextButton(
                    text = getString(R.string.module_webui_retry),
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                    colors = MiuixButtonDefaults.textButtonColorsPrimary(),
                )
                MiuixTextButton(
                    text = getString(R.string.module_webui_close),
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                if (!rendererGone) Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Text(getString(R.string.module_webui_retry))
                }
                OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                    Text(getString(R.string.module_webui_close))
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        t3.foreground(t3Owner)
    }

    override fun onStop() {
        t3.background(t3Owner)
        super.onStop()
    }

    override fun onDestroy() {
        webView?.let(::releaseWebView) ?: pageState.dispose(this)
        backCallback?.isEnabled = false
        backCallback = null
        t3.destroy(t3Owner, isFinishing)
        super.onDestroy()
    }
}

/** Theme-specific chrome shared by HTTP module documents. */
@Composable
private fun ModuleWebUiFrame(
    title: String,
    onBack: () -> Unit,
    edgeToEdge: Boolean,
    content: @Composable () -> Unit,
) {
    val palette = moduleWebUiPalette()
    Column(
        modifier = Modifier.fillMaxSize().background(palette.background)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (LocalUiMode.current == UiMode.Miuix) {
                MiuixIconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    MiuixIcon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.webui_back),
                        tint = palette.text,
                    )
                }
            } else {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.webui_back),
                        tint = palette.text,
                    )
                }
            }
            ModuleWebUiText(title, palette.text, title = true, modifier = Modifier.weight(1f).padding(end = 12.dp))
        }
        val contentInsets = if (edgeToEdge) WindowInsets.ime else
            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        Box(modifier = Modifier.fillMaxWidth().weight(1f).windowInsetsPadding(contentInsets)) { content() }
    }
}

@Composable
private fun ModuleWebUiText(
    text: String,
    color: ComposeColor,
    modifier: Modifier = Modifier,
    title: Boolean = false,
) {
    if (LocalUiMode.current == UiMode.Miuix) {
        MiuixText(
            text = text, color = color, modifier = modifier,
            fontSize = if (title) 20.sp else 15.sp,
            fontWeight = if (title) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = if (title) 1 else Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis,
        )
    } else {
        Text(
            text = text, color = color, modifier = modifier,
            style = if (title) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
            maxLines = if (title) 1 else Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class ModuleWebUiPalette(
    val mode: String,
    val dark: Boolean,
    val background: ComposeColor,
    val surface: ComposeColor,
    val text: ComposeColor,
    val summary: ComposeColor,
    val primary: ComposeColor,
    val onPrimary: ComposeColor,
    val outline: ComposeColor,
    val error: ComposeColor,
) {
    fun documentScript(): String {
        fun ComposeColor.css() = "#%06x".format(toArgb() and 0xFFFFFF)
        val values = JSONObject().apply {
            put("style", mode)
            put("motion", ValueAnimator.areAnimatorsEnabled())
            put("scheme", if (dark) "dark" else "light")
            put("colors", JSONObject().apply {
                put("--bg", background.css()); put("--card", surface.css())
                put("--text", text.css()); put("--text-2", summary.css()); put("--muted", summary.css())
                put("--accent", primary.css()); put("--accent-fg", onPrimary.css())
                put("--line", outline.css()); put("--error", error.css())
            })
        }
        return """(function(){var a=$values;window.MapleSUAppearance=a;
            function apply(){var r=document.documentElement;if(!r)return;
            r.dataset.style=a.style;r.dataset.scheme=a.scheme;
            Object.keys(a.colors).forEach(function(k){r.style.setProperty(k,a.colors[k]);});
            window.dispatchEvent(new Event('maplesuappearancechange'));}
            apply();if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',apply,{once:true});})()""".trimIndent()
    }
}

@Composable
private fun moduleWebUiPalette(): ModuleWebUiPalette {
    val dark = com.maplesu.core.ui.theme.isInDarkTheme()
    return when (LocalUiMode.current) {
        UiMode.Miuix -> MiuixTheme.colorScheme.let {
            ModuleWebUiPalette("miuix", dark, it.background, it.surface, it.onBackground,
                it.onSurfaceVariantSummary, it.primary, it.onPrimary, it.dividerLine, it.error)
        }
        UiMode.Material -> MaterialTheme.colorScheme.let {
            ModuleWebUiPalette("material", dark, it.background, it.surface, it.onBackground,
                it.onSurfaceVariant, it.primary, it.onPrimary, it.outlineVariant, it.error)
        }
    }
}
