package com.maplesu.core.ui.screen.settings

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RemoveModerator
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Card as MaterialCard
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text as MaterialText
import androidx.compose.material3.TextButton as MaterialTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maplesu.core.R
import com.maplesu.core.ui.component.dialog.rememberConfirmDialog
import com.maplesu.core.ui.component.material.SegmentedListItem
import com.maplesu.core.ui.component.statustag.StatusTag
import com.maplesu.core.ui.component.uninstalldialog.UninstallChoice
import com.maplesu.core.ui.component.uninstalldialog.UninstallChooser
import dev.maplesu.manager.modules.UltraRootKeyStore
import dev.maplesu.manager.runtime.HookRootExecutor
import dev.maplesu.manager.runtime.ultra.UltraAutoPair
import dev.maplesu.manager.runtime.ultra.UltraEnvironmentInstaller
import dev.maplesu.manager.runtime.ultra.UltraEnvironmentAutoSetup
import dev.maplesu.manager.runtime.ultra.UltraEnvironmentInstallReadiness
import dev.maplesu.manager.runtime.ultra.UltraEnvironmentUninstaller
import dev.maplesu.manager.runtime.ultra.UltraEnvironmentProbe
import dev.maplesu.manager.runtime.ultra.UltraEnvironmentUpgrade
import dev.maplesu.manager.runtime.ultra.UltraInstalledIdentityProbe
import dev.maplesu.manager.runtime.ultra.UltraRuntimeClient
import dev.maplesu.manager.runtime.ultra.UltraSdkCredentials
import dev.maplesu.manager.runtime.ultra.UltraSdkLogging
import dev.maplesu.manager.runtime.ultra.UltraSdkSession
import dev.maplesu.manager.runtime.ultra.UltraEnvironmentVersion
import dev.maplesu.manager.workmode.ModeRuntime
import dev.maplesu.manager.workmode.WorkMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

private const val TAG = "MapleSUUltraEnvUi"

/** Ultra environment settings. Temporary removal retains install credentials; permanent
 * removal restores backed-up images or persists disable. SDK calls run off the UI thread.
 */
@Stable
class UltraEnvironmentPanel internal constructor(
    private val context: Context,
    private val scope: CoroutineScope,
    initialSnapshot: UltraEnvironmentProbe.Snapshot,
) {
    private val seed = lastFacts?.takeIf { it.current() }

    var state by mutableStateOf(seed?.state ?: initialSnapshot.state)
        private set
    var kernelRootVerified by mutableStateOf(seed?.kernelRootVerified ?: initialSnapshot.kernelRootVerified)
        private set
    var version by mutableStateOf(seed?.version)
        private set
    var observedAtWallClockMs by mutableStateOf(seed?.observedAt ?: initialSnapshot.observedAtWallClockMs)
        private set
    var busy by mutableStateOf(false)
        private set
    var hasLocalKey by mutableStateOf(seed?.hasKey ?: false)
        private set
    var keyState by mutableStateOf(seed?.keyState ?: UltraAutoPair.KeyState.Present)
        private set
    var hookEnvironment by mutableStateOf(seed?.hookEnvironment ?: false)
        private set

    fun loadInitialState() {
        if (busy) return
        busy = true
        scope.launch {
            try {
                applyFacts(readFacts())
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "initial environment read failed", error)
            } finally {
                busy = false
            }
        }
    }

    /** 静默刷新：界面直接反映新状态，不弹 Toast。 */
    fun refresh() = runAction {
        applyFacts(readFacts(force = true))
        ""
    }

    fun repairEnvironment() = runAction {
        val result = withContext(Dispatchers.IO) { UltraEnvironmentUpgrade.repairEnvironment(context) }
        applyFacts(readFacts(force = true))
        when (result) {
            UltraEnvironmentUpgrade.Result.SUBMITTED -> context.getString(R.string.ui_d_env_repair_submitted)
            UltraEnvironmentUpgrade.Result.REFUSED -> context.getString(R.string.ui_d_env_repair_refused)
            UltraEnvironmentUpgrade.Result.FAILED -> context.getString(R.string.ui_d_env_repair_failed)
        }
    }

    /** Permanent removal marks the environment removed only after backed-up images are restored. */
    fun removeEnvironment() = uninstall(permanent = true)

    /** Temporary removal calls the Ultra SDK and retains the install credential. */
    fun suspendLocal() = uninstall(permanent = false)

    private fun uninstall(permanent: Boolean) = runAction {
        val result = withContext(Dispatchers.IO) { UltraEnvironmentUninstaller.uninstall(context, permanent) }
        if (!result.ok) {
            Log.w(TAG, UltraSdkLogging.redact(
                "uninstall failed sdkError=${result.sdkError} sdkErrorCode=${result.sdkErrorCode}"))
            context.getString(R.string.ui_d_env_remove_failed)
        } else {
            applyFacts(readFacts(force = true))
            context.getString(R.string.ui_d_env_remove_done)
        }
    }

    private fun captureObservation(): () -> Boolean {
        val activation = ModeRuntime.activation
        val boot = UltraEnvironmentAutoSetup.currentBootId()
        val owner = UltraRootKeyStore.captureOwnerMarker(context)
        val session = UltraSdkSession.captureOwnerMarker(context)
        return {
            ModeRuntime.activation === activation && boot != null &&
                UltraEnvironmentAutoSetup.currentBootId() == boot && owner() && session()
        }
    }
    /** 一次读全：减少重组次数，也避免版本行和状态胶囊错开刷新。 */
    private suspend fun readFacts(force: Boolean = false): Facts = withContext(Dispatchers.IO) {
        val hook = HookRootExecutor.production(context)
        if (ModeRuntime.active == WorkMode.LITE && UltraSdkCredentials.hookWithoutSdk(context) && hook.available()) {
            val current = captureObservation()
            val result = hook.run("/system/bin/id -u")
            return@withContext Facts(
                state = if (result.ok && result.output.trim() == "0") UltraEnvironmentProbe.State.Running
                    else UltraEnvironmentProbe.State.Unknown,
                version = null,
                observedAt = System.currentTimeMillis(),
                hasKey = false,
                keyState = UltraAutoPair.KeyState.Present,
                hookEnvironment = true,
                current = current,
            )
        }
        val key = UltraSdkCredentials.read(context)
        val boot = UltraEnvironmentAutoSetup.currentBootId()
        val nextState = runCatching { UltraEnvironmentProbe.refresh(context, force = force) }
            .getOrDefault(UltraEnvironmentProbe.State.Unknown)
        val current = captureObservation()
        val observation = UltraEnvironmentProbe.currentSnapshot(context)
        Facts(
            state = nextState,
            version = if (current()) readVersion(key, boot) else null,
            observedAt = observation.observedAtWallClockMs,
            hasKey = UltraRootKeyStore.read(context) != null,
            keyState = if (UltraSdkCredentials.read(context) != null) UltraAutoPair.KeyState.Present
                else UltraAutoPair.keyState(context),
            kernelRootVerified = observation.state == nextState && observation.kernelRootVerified,
            current = current,
        )
    }

    private fun readVersion(key: String?, boot: String?): String? = runCatching {
        val client = UltraSdkCredentials.client(context, key, boot)
        val raw = client
            .installedEnvironmentVersion()
            .takeIf { it.succeeded }
            ?.string("version")
        if (raw != null) return@runCatching UltraEnvironmentVersion.displayOf(raw)
        // Version fallback only: do not start a root hash read when the SDK answered.
        val buildId = runCatching { UltraInstalledIdentityProbe.read(context, client).buildId }.getOrNull()
        UltraEnvironmentVersion.displayOf(raw, buildId)
    }.getOrNull()

    private fun applyFacts(facts: Facts) {
        if (!facts.current()) return
        // 环境仍在运行但这次没读到版本：保留旧值，版本行不闪回「未知」。
        val previous = lastFacts?.takeIf { it.current() }
        val merged = if (!facts.hookEnvironment && previous?.hookEnvironment == false &&
            facts.version == null && facts.state == UltraEnvironmentProbe.State.Running) {
            facts.copy(version = previous.version)
        } else {
            facts
        }
        lastFacts = merged
        state = merged.state
        kernelRootVerified = merged.kernelRootVerified
        version = merged.version
        observedAtWallClockMs = merged.observedAt
        hasLocalKey = merged.hasKey
        keyState = merged.keyState
        hookEnvironment = merged.hookEnvironment
    }

    private fun runAction(block: suspend () -> String) {
        if (busy) return
        busy = true
        scope.launch {
            val text = try {
                block()
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "environment action failed", error)
                context.getString(R.string.ui_d_env_action_failed)
            } finally {
                busy = false
            }
            // 空字符串 = 静默动作（例如刷新）。结果只走 Toast，不往卡片里塞文字，卡片高度不变。
            if (text.isNotBlank()) {
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private data class Facts(
        val state: UltraEnvironmentProbe.State,
        val version: String?,
        val observedAt: Long,
        val hasKey: Boolean,
        val keyState: UltraAutoPair.KeyState,
        val hookEnvironment: Boolean = false,
        val kernelRootVerified: Boolean = false,
        val current: () -> Boolean,
    )

    private companion object {
        /** 本进程上一次的结果：面板重建时做首帧。 */
        @Volatile
        var lastFacts: Facts? = null
    }
}

/**
 * 工作环境面板（**仅 Ultra**）。
 *
 * [enabled] = `SettingsModePolicy.shows(SettingsSection.ULTRA_ULTRA_ENV)` 且隐藏未生效：
 * 其余情况返回 null 且**不做任何 Ultra SDK / 环境探测**
 * （`UltraEnvironmentProbe.currentSnapshot()`、`UltraAutoPair.ensure` 都不会被调用）。
 */
@Composable
fun rememberUltraEnvironmentPanel(enabled: Boolean): UltraEnvironmentPanel? {
    if (!enabled) return null
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val panel = remember {
        UltraEnvironmentPanel(
            context = context.applicationContext,
            scope = scope,
            initialSnapshot = UltraEnvironmentProbe.currentSnapshot(context.applicationContext),
        )
    }
    val observation by UltraEnvironmentProbe.observations.collectAsStateWithLifecycle(
        initialValue = UltraEnvironmentProbe.currentSnapshot(context.applicationContext))
    LaunchedEffect(panel, observation.state, observation.kernelRootVerified,
        observation.managerOwnerIdentity, observation.sdkChannelAvailable) { panel.loadInitialState() }
    return panel
}

private fun envStateLabelRes(panel: UltraEnvironmentPanel): Int = when {
    else -> when (panel.state) {
        UltraEnvironmentProbe.State.Running -> R.string.ui_d_env_state_running
        UltraEnvironmentProbe.State.NotInstalled -> R.string.ui_d_env_state_not_installed
        UltraEnvironmentProbe.State.Fault -> R.string.ui_d_env_state_fault
        UltraEnvironmentProbe.State.NoKey -> R.string.ui_d_env_state_no_key
        UltraEnvironmentProbe.State.Unknown -> R.string.ui_d_env_state_unknown
    }
}

/** 版本 + 检查时间，一行写完；只有数值在变，行高不变。 */
@Composable
private fun envSummary(panel: UltraEnvironmentPanel): String {
    val checkedAt = panel.observedAtWallClockMs.takeIf { it > 0 }?.let { at ->
        remember(at) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(at)) }
    }
    val version = panel.version
    return when {
        checkedAt == null -> stringResource(R.string.ui_d_env_checking)
        panel.hookEnvironment -> stringResource(R.string.ui_d_env_hook_summary, checkedAt)
        version != null -> stringResource(R.string.ui_d_env_summary, ultraEnvironmentLabel(version), checkedAt)
        else -> stringResource(R.string.ui_d_env_summary_no_version, checkedAt)
    }
}

@Composable
private fun envHint(panel: UltraEnvironmentPanel): String? = when {
    panel.keyState == UltraAutoPair.KeyState.Absent -> stringResource(R.string.ui_d_env_hint_no_key)
    panel.keyState == UltraAutoPair.KeyState.Malformed -> stringResource(R.string.ui_d_env_hint_bad_key)
    else -> null
}


@Composable
private fun rememberUltraUninstallChoices(panel: UltraEnvironmentPanel): List<UninstallChoice> {
    // SDK removal uses the retained install credential.
    val removeBlocked = when {
        panel.hookEnvironment -> stringResource(R.string.ui_d_env_hook_removal)
        panel.hasLocalKey -> null
        else -> stringResource(R.string.ui_d_uninstall_remove_blocked)
    }
    val remove = UninstallChoice(
        icon = Icons.Rounded.DeleteForever,
        title = stringResource(R.string.ui_d_uninstall_remove),
        message = stringResource(R.string.ui_d_uninstall_remove_message),
        disabledReason = removeBlocked,
        onRun = panel::removeEnvironment,
    )
    val secondary = UninstallChoice(
        icon = Icons.Rounded.RemoveModerator,
        title = stringResource(R.string.ui_d_uninstall_suspend),
        message = stringResource(R.string.ui_d_uninstall_suspend_message),
        disabledReason = removeBlocked,
        onRun = panel::suspendLocal,
    )
    return listOf(remove, secondary)
}

/** Miuix：「MapleSU_Ultra工作环境」卡。 */
@Composable
fun UltraEnvironmentSectionMiuix(panel: UltraEnvironmentPanel) {
    val confirmRepair = rememberFaultRepairAction(panel)
    MiuixCard(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth(),
    ) {
        val title = stringResource(R.string.ui_d_env_title)
        BasicComponent(
            title = title,
            summary = envSummary(panel),
            startAction = {
                MiuixIcon(
                    Icons.Rounded.Security,
                    contentDescription = title,
                    modifier = Modifier.padding(end = 6.dp),
                    tint = colorScheme.onBackground,
                )
            },
            endActions = {
                val (bg, fg) = envTagColorsMiuix(panel)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StatusTag(label = stringResource(envStateLabelRes(panel)), backgroundColor = bg, contentColor = fg)
                    MiuixIcon(
                        Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.ui_d_env_refresh),
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer { alpha = if (panel.busy) 0.38f else 1f },
                        tint = colorScheme.onSurfaceVariantSummary,
                    )
                }
            },
            onClick = panel::refresh,
        )
        envHint(panel)?.let { hint ->
            BasicComponent(
                title = stringResource(R.string.ui_d_env_attention),
                summary = hint,
                startAction = {
                    MiuixIcon(Icons.Rounded.Security, null, modifier = Modifier.padding(end = 6.dp))
                },
            )
        }
        if (panel.canShowRepair) {
            BasicComponent(
                title = stringResource(R.string.ui_d_env_repair_title),
                summary = stringResource(R.string.ui_d_env_repair_summary),
                endActions = {
                    MiuixTextButton(
                        text = stringResource(R.string.ui_d_env_repair_action),
                        onClick = confirmRepair,
                        enabled = !panel.busy,
                    )
                },
            )
        }
    }
}

@Composable
private fun envTagColorsMiuix(panel: UltraEnvironmentPanel): Pair<Color, Color> = when {
    panel.state == UltraEnvironmentProbe.State.Running -> colorScheme.primary to colorScheme.onPrimary
    panel.state == UltraEnvironmentProbe.State.Fault -> colorScheme.error to colorScheme.onError
    else -> colorScheme.secondaryContainer to colorScheme.onSecondaryContainer
}

/** Miuix：卸载行（设置页最后一组）。 */
@Composable
fun UltraUninstallRowMiuix(panel: UltraEnvironmentPanel) {
    var show by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(R.string.uninstall)
    ArrowPreference(
        title = title,
        startAction = {
            MiuixIcon(
                Icons.Rounded.Delete,
                contentDescription = title,
                modifier = Modifier.padding(end = 6.dp),
                tint = colorScheme.onBackground,
            )
        },
        enabled = !panel.busy,
        onClick = { show = true },
    )
    UninstallChooser(
        show = show,
        title = title,
        choices = rememberUltraUninstallChoices(panel),
        onDismissRequest = { show = false },
    )
}

/**
 * Material：环境信息作为一个分段项。分段项由调用方放进 `SegmentedColumn`，
 * 这样与同组的行共用圆角，不再是一张风格不同的卡片。
 */
@Composable
fun UltraEnvironmentRowMaterial(panel: UltraEnvironmentPanel) {
    val confirmRepair = rememberFaultRepairAction(panel)
    val title = stringResource(R.string.ui_d_env_title)
    val hint = envHint(panel)
    SegmentedListItem(
        onClick = panel::refresh,
        headlineContent = { MaterialText(title) },
        supportingContent = {
            Column {
                MaterialText(envSummary(panel))
                hint?.let { MaterialText(it) }
            }
        },
        leadingContent = { MaterialIcon(Icons.Rounded.Security, contentDescription = title) },
        trailingContent = {
            val (bg, fg) = envTagColorsMaterial(panel)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatusTag(label = stringResource(envStateLabelRes(panel)), backgroundColor = bg, contentColor = fg)
                MaterialIcon(
                    Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.ui_d_env_refresh),
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { alpha = if (panel.busy) 0.38f else 1f },
                )
            }
        },
    )
    if (panel.canShowRepair) {
        SegmentedListItem(
            onClick = confirmRepair,
            enabled = !panel.busy,
            headlineContent = { MaterialText(stringResource(R.string.ui_d_env_repair_title)) },
            supportingContent = { MaterialText(stringResource(R.string.ui_d_env_repair_summary)) },
            leadingContent = { MaterialIcon(Icons.Rounded.Security, contentDescription = null) },
            trailingContent = {
                MaterialTextButton(onClick = confirmRepair, enabled = !panel.busy) {
                    MaterialText(stringResource(R.string.ui_d_env_repair_action))
                }
            },
        )
    }
}

@Composable
private fun rememberFaultRepairAction(panel: UltraEnvironmentPanel): () -> Unit {
    val dialog = rememberConfirmDialog(onConfirm = { panel.repairEnvironment() })
    val title = stringResource(R.string.ui_d_env_repair_title)
    val content = stringResource(R.string.ui_d_env_repair_confirm)
    val confirm = stringResource(R.string.ui_d_env_repair_action)
    return { if (!panel.busy) dialog.showConfirm(title = title, content = content, confirm = confirm) }
}

private val UltraEnvironmentPanel.canShowRepair: Boolean
    get() = !hookEnvironment && (state == UltraEnvironmentProbe.State.Fault ||
        state == UltraEnvironmentProbe.State.Unknown ||
        state == UltraEnvironmentProbe.State.NotInstalled && kernelRootVerified)

@Composable
private fun envTagColorsMaterial(panel: UltraEnvironmentPanel): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when {
        panel.state == UltraEnvironmentProbe.State.Running -> scheme.primaryContainer to scheme.onPrimaryContainer
        panel.state == UltraEnvironmentProbe.State.Fault -> scheme.errorContainer to scheme.onErrorContainer
        else -> scheme.secondaryContainer to scheme.onSecondaryContainer
    }
}

/** Material：卸载行（分段项）。 */
@Composable
fun UltraUninstallRowMaterial(panel: UltraEnvironmentPanel) {
    var show by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(R.string.uninstall)
    SegmentedListItem(
        onClick = { show = true },
        enabled = !panel.busy,
        headlineContent = { MaterialText(title) },
        leadingContent = { MaterialIcon(Icons.Rounded.Delete, contentDescription = title) },
        trailingContent = { MaterialIcon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
    )
    UninstallChooser(
        show = show,
        title = title,
        choices = rememberUltraUninstallChoices(panel),
        onDismissRequest = { show = false },
    )
}

/** Explicit environment recovery delegates installation capability to the SDK operation. */
@Stable
class UltraEnvironmentInstallPanel internal constructor(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    var visible by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var readiness by mutableStateOf(UltraEnvironmentInstallReadiness.NotChecked)
        private set
    private var refreshJob: Job? = null

    private fun localIdentity(mode: WorkMode? = ModeRuntime.active): String? {
        if (mode == WorkMode.KSU || ModeRuntime.active == WorkMode.KSU ||
            HookRootExecutor.production(context).available()) return null
        return UltraRootKeyStore.read(context)
    }

    fun refresh(mode: WorkMode? = ModeRuntime.active) {
        if (busy) return
        refreshJob?.cancel()
        refreshJob = scope.launch {
            val eligible = withContext(Dispatchers.IO) { localIdentity(mode) != null }
            val observation = UltraEnvironmentProbe.currentSnapshot(context)
            visible = environmentInstallVisible(eligible, observation.state, observation.kernelChannelAbsent)
            readiness = UltraEnvironmentInstallReadiness.NotChecked
            message = null
        }
    }

    fun act(onInstallImage: () -> Unit) {
        if (busy) return
        if (readiness.needsImage) onInstallImage() else install()
    }

    fun install() {
        if (busy) return
        refreshJob?.cancel()
        val modeBeforeInstall = ModeRuntime.active
        val keyBeforeInstall = localIdentity() ?: return
        val stateBeforeInstall = UltraEnvironmentProbe.currentSnapshot(context).state
        busy = true
        scope.launch {
            try {
                val outcome = withContext(Dispatchers.IO) {
                    currentCoroutineContext().ensureActive()
                    if (ModeRuntime.active != modeBeforeInstall || localIdentity() != keyBeforeInstall) {
                        UltraEnvironmentInstaller.Outcome(false, "INSTALL_CONTEXT_CHANGED")
                    } else {
                        UltraRootKeyStore.allowEnvironmentInstall(context)
                        UltraEnvironmentInstaller.install(
                            context, UltraRuntimeClient { UltraRootKeyStore.read(context) },
                            UltraEnvironmentInstaller.Intent.InstallToBundledBuild,
                        )
                    }
                }
                message = if (outcome.ok && outcome.verified) {
                    visible = false
                    context.getString(R.string.ui_d_env_install_done)
                } else {
                    Log.w(TAG, UltraSdkLogging.redact(
                        "install failed sdkError=${outcome.sdkError} code=${outcome.sdkErrorCode}"))
                    readiness = when (outcome.sdkError) {
                        "ERR_MODULE_NO_BOOT" -> UltraEnvironmentInstallReadiness.NeedsImage
                        "ERR_MODULE_ROOT_KEY" -> UltraEnvironmentInstallReadiness.KeyMismatch
                        else -> UltraEnvironmentInstallReadiness.Unavailable
                    }
                    environmentInstallFailure(context, outcome.sdkError)
                }
                message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "install failed", error)
                message = context.getString(R.string.ui_d_env_install_failed)
            } finally {
                busy = false
                if (ModeRuntime.active != modeBeforeInstall ||
                    UltraEnvironmentProbe.currentSnapshot(context).state != stateBeforeInstall) refresh()
            }
        }
    }
}

internal fun environmentInstallVisible(
    eligible: Boolean,
    state: UltraEnvironmentProbe.State,
    kernelChannelAbsent: Boolean,
): Boolean = eligible && (state == UltraEnvironmentProbe.State.NotInstalled || kernelChannelAbsent)

internal fun environmentReadinessSummaryResId(readiness: UltraEnvironmentInstallReadiness): Int = when (readiness) {
    UltraEnvironmentInstallReadiness.NotChecked, UltraEnvironmentInstallReadiness.Ready -> R.string.ui_d_env_install_summary
    UltraEnvironmentInstallReadiness.NeedsImage -> R.string.ui_d_env_install_kernel_channel
    UltraEnvironmentInstallReadiness.KeyMismatch -> R.string.ui_d_env_install_key_mismatch
    UltraEnvironmentInstallReadiness.Unavailable -> R.string.ui_d_env_install_failed
}

internal fun environmentInstallFailureResId(sdkError: String): Int =
    when (sdkError) {
        "ERR_MODULE_NO_BOOT" -> R.string.ui_d_env_install_kernel_channel
        "ERR_MODULE_ROOT_KEY" -> R.string.ui_d_env_install_key_mismatch
        else -> R.string.ui_d_env_install_failed
    }

private fun environmentInstallFailure(context: Context, sdkError: String): String =
    context.getString(environmentInstallFailureResId(sdkError))

@Composable
fun rememberUltraEnvironmentInstallPanel(mode: WorkMode? = ModeRuntime.active): UltraEnvironmentInstallPanel {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appContext = context.applicationContext
    val panel = remember { UltraEnvironmentInstallPanel(appContext, scope) }
    val observation by UltraEnvironmentProbe.observations.collectAsStateWithLifecycle(
        initialValue = UltraEnvironmentProbe.currentSnapshot(appContext))
    LaunchedEffect(panel, mode, observation.state, observation.kernelChannelAbsent) { panel.refresh(mode) }
    return panel
}

@Composable
private fun installSummary(panel: UltraEnvironmentInstallPanel): String =
    panel.message ?: stringResource(if (panel.busy) R.string.ui_d_env_install_busy else environmentReadinessSummaryResId(panel.readiness))

@Composable
private fun installActionLabel(panel: UltraEnvironmentInstallPanel): String = stringResource(when {
    panel.readiness.needsImage -> R.string.ui_d_env_install_image_action
    else -> R.string.ui_d_env_install_action
})

@Composable
fun UltraEnvironmentInstallCardMiuix(panel: UltraEnvironmentInstallPanel, onInstallImage: () -> Unit) {
    if (!panel.visible) return
    val title = stringResource(R.string.ui_d_env_install_title)
    MiuixCard(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        BasicComponent(
            title = title,
            summary = installSummary(panel),
            startAction = {
                MiuixIcon(
                    Icons.Rounded.Security,
                    contentDescription = title,
                    modifier = Modifier.padding(end = 6.dp),
                    tint = colorScheme.onBackground,
                )
            },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            MiuixTextButton(
                text = installActionLabel(panel),
                onClick = { panel.act(onInstallImage) },
                enabled = !panel.busy,
            )
        }
    }
}

@Composable
fun UltraEnvironmentInstallCardMaterial(panel: UltraEnvironmentInstallPanel, onInstallImage: () -> Unit) {
    if (!panel.visible) return
    MaterialCard(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            MaterialText(stringResource(R.string.ui_d_env_install_title))
            MaterialText(
                installSummary(panel),
                modifier = Modifier.padding(top = 2.dp),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                MaterialTextButton(onClick = { panel.act(onInstallImage) }, enabled = !panel.busy) {
                    MaterialText(installActionLabel(panel))
                }
            }
        }
    }
}
