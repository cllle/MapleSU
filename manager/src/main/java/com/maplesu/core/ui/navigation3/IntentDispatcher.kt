package com.maplesu.core.ui.navigation3

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.net.toUri
import androidx.core.content.IntentCompat
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.maplesu.core.Natives
import com.maplesu.core.R
import com.maplesu.core.data.repository.SettingsRepositoryImpl
import com.maplesu.core.ksuApp
import com.maplesu.core.ui.component.dialog.rememberConfirmDialog
import com.maplesu.core.ui.screen.flash.FlashIt
import com.maplesu.core.ui.util.DownloadService
import com.maplesu.core.ui.util.getFileName
import com.maplesu.core.ui.webui.WebUIActivity
import dev.maplesu.manager.modules.ModuleEnvironmentWatch
import dev.maplesu.manager.modules.ModuleWebUiLauncher
import dev.maplesu.manager.liteweb.LiteWebProxyBase
import dev.maplesu.manager.liteweb.LiteWebToken

private const val SCHEME_KSU = "ksu"
private const val HOST_ACTION = "action"
private const val HOST_WEBUI = "webui"
private const val PARAM_ID = "id"
private const val PARAM_TOKEN = "token"
private const val PARAM_WEB_TOKEN = "webToken"

/**
 * Resolved intent action to execute after validation.
 */
private sealed interface PendingAction {
    /** Install module(s) from URI — triggered by DownloadService notification or ZIP file open. */
    data class InstallModule(
        val uri: Uri,
        val displayName: String,
        val requiresConfirmation: Boolean,
    ) : PendingAction {
        companion object {
            val InstallModuleSaver = listSaver<InstallModule?, Any>(
                save = { module ->
                    if (module == null) {
                        emptyList()
                    } else {
                        listOf(
                            module.uri,
                            module.displayName,
                            module.requiresConfirmation
                        )
                    }
                },
                restore = { list ->
                    if (list.isEmpty()) {
                        null
                    } else {
                        InstallModule(
                            uri = list[0] as Uri,
                            displayName = list[1] as String,
                            requiresConfirmation = list[2] as Boolean
                        )
                    }
                }
            )
        }
    }

    /** Execute a module's action script — triggered by shortcut or deep link. */
    data class ExecuteAction(val moduleId: String) : PendingAction

    /** Open a module's WebUI — triggered by shortcut. */
    data class OpenWebUI(val moduleId: String, val webToken: String? = null) : PendingAction
}

private sealed interface KsuDeepLink {
    data class Action(val moduleId: String) : KsuDeepLink
    data class WebUi(val moduleId: String, val webToken: String? = null) : KsuDeepLink
}

private fun buildInternalWebUiUri(moduleId: String): Uri {
    return Uri.Builder()
        .scheme(SCHEME_KSU)
        .authority(HOST_WEBUI)
        .appendQueryParameter(PARAM_ID, moduleId)
        .build()
}

fun getDisplayName(uri: Uri): String {
    return uri.getFileName(ksuApp) ?: uri.lastPathSegment ?: "Unknown"
}

private val ZIP_MIME_TYPES = setOf("application/zip", "application/x-zip-compressed", "application/x-zip")

/**
 * Resolve an intent snapshot into a [PendingAction].
 * Returns null if the intent carries no recognized action.
 */
private fun resolveIntent(intent: Intent): PendingAction? {
    // DownloadService notification: install module
    if (intent.action == DownloadService.ACTION_INSTALL_MODULE) {
        val token = intent.getStringExtra(DownloadService.EXTRA_TOKEN)?.takeIf { it.isNotBlank() } ?: return null
        if (token != SettingsRepositoryImpl().intentToken) return null
        val uriString = intent.getStringExtra(DownloadService.EXTRA_MODULE_URI)
            ?: return null
        val uri = uriString.toUri()
        return PendingAction.InstallModule(
            uri = uri,
            displayName = getDisplayName(uri),
            requiresConfirmation = false,
        )
    }

    // File manager: open ZIP
    val viewUri = intent.data
    if (viewUri != null && viewUri.scheme == "content" && intent.type in ZIP_MIME_TYPES) {
        return PendingAction.InstallModule(
            uri = viewUri,
            displayName = getDisplayName(viewUri),
            requiresConfirmation = true,
        )
    }

    // Check deep links
    return when (val deepLink = parseDeepLink(intent.data)) {
        is KsuDeepLink.Action -> PendingAction.ExecuteAction(deepLink.moduleId)
        is KsuDeepLink.WebUi -> PendingAction.OpenWebUI(deepLink.moduleId, deepLink.webToken)
        null -> null
    }
}

private fun parseDeepLink(uri: Uri?): KsuDeepLink? {
    if (uri?.scheme != SCHEME_KSU) return null

    val moduleId = uri.getQueryParameter(PARAM_ID)?.takeIf { it.isNotBlank() } ?: return null
    val token = uri.getQueryParameter(PARAM_TOKEN)?.takeIf { it.isNotBlank() }
    if (token != null && token == SettingsRepositoryImpl().intentToken) {
        return when (uri.host) {
            HOST_ACTION -> KsuDeepLink.Action(moduleId)
            HOST_WEBUI -> KsuDeepLink.WebUi(moduleId)
            else -> null
        }
    }
    // A browser knows the existing webadmin token, never the app-private shortcut token.
    // Only WebUI can carry this proof; compare the protected current config after readiness.
    val webToken = uri.getQueryParameter(PARAM_WEB_TOKEN)?.takeIf(LiteWebToken::isValid) ?: return null
    return if (uri.host == HOST_WEBUI) KsuDeepLink.WebUi(moduleId, webToken) else null
}

@SuppressLint("StringFormatInvalid")
@Composable
fun IntentDispatcher(intentChannel: ReceiveChannel<Intent>) {
    val context = LocalContext.current
    val themeOperations = com.maplesu.core.ui.screen.settings.LocalThemeOperations.current
    val resources = LocalResources.current
    val navigator = LocalNavigator.current
    // 进程内一次性：深链触发的 WebUI 打开请求消费即清空；进程重启不会重放（见 PendingWebUiOpen）。
    val pendingWebUiOpen = remember { PendingWebUiOpen() }
    var pendingZipInstall by rememberSaveable(stateSaver = PendingAction.InstallModule.InstallModuleSaver) { mutableStateOf(null) }

    val installDialog = rememberConfirmDialog(
        onConfirm = {
            pendingZipInstall?.let { action ->
                navigator.push(Route.Flash(FlashIt.FlashModules(listOf(action.uri))))
            }
            pendingZipInstall = null
        },
        onDismiss = { pendingZipInstall = null }
    )

    // 【2026-09-16 已按用户要求删除】原本这里有一段「隐身救援」:
    //   隐身(isManager == false)时,任何 App 或浏览器打开 `ksu://` 链接都会弹一个
    //   「关闭隐身模式?」的确认框,确认后关掉隐身。
    //   删除理由:该入口对外暴露了"这台机器装了隐身软件"这件事,而且任何 App 都能拉起它。
    //   ⚠️ 删掉之后的后果:隐身退出的路只剩 (1) 拨号密令 (2) 有 root 时
    //      `su -c 'rm /data/adb/sevenk/stealth'` 再重启。
    //   同时清单里 ksu://action 的 BROWSABLE 分类也已移除 —— 浏览器/其它 App 打不开它了。
    //   (模块快捷方式不受影响:它们用的是显式 Intent(Intent(context, MainActivity::class.java)),
    //    不经过清单过滤器。)
    CollectIntentChannel(intentChannel) { intent ->
        val themeUris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> listOfNotNull(intent.data)
        }.filter { it.scheme in setOf("content", "file") }
        val themes = themeUris.filter { runCatching { getDisplayName(it).endsWith(".fpt", ignoreCase = true) }.getOrDefault(false) }
        if (themes.isNotEmpty()) {
            if (themeUris.size == 1) {
                if (Route.Wallpaper in navigator.backStack) navigator.popUntil { it == Route.Wallpaper }
                else navigator.push(Route.Wallpaper)
                themeOperations.prepare(themes.single())
            }
            else Toast.makeText(context, R.string.theme_single_file, Toast.LENGTH_LONG).show()
            return@CollectIntentChannel
        }
        val action = resolveIntent(intent) ?: return@CollectIntentChannel
        if (!awaitIntentManager(
                awaitEnvironment = {
                    val reason = ModuleEnvironmentWatch.facts().waitReason
                    reason == null || ModuleEnvironmentWatch.awaitReady(reason, 15_000L)
                },
                isManager = { runCatching { Natives.isManager }.getOrDefault(false) },
            )) return@CollectIntentChannel

        when (action) {
            is PendingAction.InstallModule -> {
                val isSafeMode = runCatching { Natives.isSafeMode }.getOrDefault(false)
                if (isSafeMode) {
                    Toast.makeText(
                        context,
                        resources.getString(R.string.safe_mode_module_disabled),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@CollectIntentChannel
                }
                if (action.requiresConfirmation) {
                    pendingZipInstall = action
                    installDialog.showConfirm(
                        title = resources.getString(R.string.module),
                        content = resources.getString(
                            R.string.module_install_prompt_with_name,
                            "\n${action.displayName}"
                        )
                    )
                } else {
                    navigator.push(Route.Flash(FlashIt.FlashModules(listOf(action.uri))))
                }
            }

            is PendingAction.ExecuteAction -> {
                navigator.push(Route.ExecuteModuleAction(action.moduleId, fromShortcut = true))
            }

            is PendingAction.OpenWebUI -> {
                if (action.webToken != null) {
                    val currentToken = withContext(Dispatchers.IO) {
                        LiteWebProxyBase.current(context)?.toUri()?.pathSegments?.getOrNull(1)
                    }
                    if (!LiteWebToken.matches(currentToken, action.webToken)) return@CollectIntentChannel
                }
                // Both deep-link credentials are validated before entering the one-shot gate.
                // The origin is an explicit deep link. The request is still offered to
                // the one-shot gate: unknown state never navigates and a consumed request
                // cannot be replayed by a recomposition.
                val request = WebUiOpenRequest(
                    moduleId = action.moduleId,
                    origin = WebUiOpenOrigin.DEEP_LINK,
                    tokenValid = true,
                )
                if (!pendingWebUiOpen.offer(request)) return@CollectIntentChannel
                val approved = pendingWebUiOpen.consume() ?: return@CollectIntentChannel
                if (!WebUiOpenPolicy.mayNavigate(approved)) return@CollectIntentChannel
                val moduleId = approved.moduleId.orEmpty()
                if (ModuleWebUiLauncher.isUltraModuleId(moduleId)) {
                    val failure = ModuleWebUiLauncher.openDetailed(context, moduleId, WebUiOpenOrigin.DEEP_LINK)
                    if (failure != null) {
                        Toast.makeText(
                            context,
                            resources.getString(R.string.module_webui_open_failed, failure.describe()),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                } else {
                    val webIntent = Intent(context, WebUIActivity::class.java)
                        .setData(buildInternalWebUiUri(moduleId))
                    context.startActivity(webIntent)
                }
            }
        }
    }
}

/**
 * Receive intents inside a [LaunchedEffect] tied to the channel identity.
 * Each emitted intent is processed exactly once; no activity.intent mutation needed.
 */
@Composable
private fun CollectIntentChannel(intentChannel: ReceiveChannel<Intent>, onIntent: suspend (Intent) -> Unit) {
    val currentOnIntent by rememberUpdatedState(onIntent)
    LaunchedEffect(intentChannel) {
        for (intent in intentChannel) {
            currentOnIntent(intent)
        }
    }
}
