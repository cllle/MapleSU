package com.maplesu.core.ui.screen.flash

import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Adb
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.RemoveModerator
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import com.maplesu.core.R
import com.maplesu.core.ksuApp
import com.maplesu.core.ui.util.FlashResult
import com.maplesu.core.ui.util.LkmSelection
import com.maplesu.core.ui.util.downloadBoot
import com.maplesu.core.ui.util.flashModule
import com.maplesu.core.ui.util.installBoot
import com.maplesu.core.ui.util.restoreBoot
import com.maplesu.core.ui.util.uninstallPermanently
import dev.maplesu.manager.ModuleArchiveInspector
import dev.maplesu.manager.installer.InstallTargetMode
import dev.maplesu.manager.modules.ModuleOperationService
import dev.maplesu.manager.runtime.ultra.UltraOutcome
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

enum class FlashingStatus {
    FLASHING,
    WAITING_INPUT,
    PENDING,
    SUCCESS,
    FAILED
}

internal fun UltraOutcome.moduleFlashingStatus(): FlashingStatus = when (this) {
    UltraOutcome.NEEDS_RECONCILIATION -> FlashingStatus.PENDING
    UltraOutcome.COMPLETED, UltraOutcome.PENDING_REBOOT -> FlashingStatus.SUCCESS
    UltraOutcome.FAILED, UltraOutcome.CONDITION_UNMET -> FlashingStatus.FAILED
}

enum class UninstallType(val icon: ImageVector, val title: Int, val message: Int) {
    TEMPORARY(
        Icons.Rounded.RemoveModerator,
        R.string.settings_uninstall_temporary,
        R.string.settings_uninstall_temporary_message
    ),
    PERMANENT(
        Icons.Rounded.DeleteForever,
        R.string.settings_uninstall_permanent,
        R.string.settings_uninstall_permanent_message
    ),
    RESTORE_STOCK_IMAGE(
        Icons.Rounded.RestartAlt,
        R.string.settings_restore_stock_image,
        R.string.settings_restore_stock_image_message
    ),
    NONE(Icons.Rounded.Adb, 0, 0)
}

@Parcelize
sealed class FlashIt : Parcelable {
    @Parcelize
    data class FlashBoot(
        val boot: Uri? = null,
        val lkm: LkmSelection,
        val ota: Boolean,
        val partition: String? = null,
        val allowShell: Boolean = false,
        val enableAdb: Boolean = false,
        val backup: Boolean = false,
        val blockModules: String = "",
        /**
         * 本次修补的**目标工作模式**（在安装页点「下一步」那一刻快照）。
         *
         * 事务一旦开始就只看这个字段，页面之后改选择不会影响它（中途改选择 = 下一次）。
         * 默认 Ultra：老调用点/老路由没有该字段时仍是 Ultra 语义。
         */
        val targetMode: InstallTargetMode = InstallTargetMode.DEFAULT,
        /**
         * 步骤 ③ 选定的目标 id（`k22` / release 目录 asset id）。默认 `null` = 目录默认档。
         *
         * 四步流程里页面上只有一个目标可选，这个字段仍然在**事务开始前**快照，
         * 执行时不再重算（47 报告 §3.2）。
         */
        val variantId: String? = null,
        /**
         * 步骤 ③ 的目标由**本机现场生成**（目录里没有它的补丁集）。
         *
         * 在点「下一步」那一刻快照，结果页据此多一句"此镜像由本机生成，尚未在设备上启动过"。
         * 它只是一个必须说的事实，不参与"能不能写"（那由事务自己的校验决定）。
         */
        val generatedOnDevice: Boolean = false,
        /** Explicit acceptance for this mode/partition/source, never inferred from installed apps. */
        val existingRootConflictAccepted: Boolean = false,
        val restoreOriginalImage: Boolean = false,
        val originalManagerPackage: String? = null,
        val lenovoSigning: dev.maplesu.manager.installer.LenovoSigningOptions = dev.maplesu.manager.installer.LenovoSigningOptions(),
        val vivoInputs: dev.maplesu.manager.installer.VivoCompatibilityInputs = dev.maplesu.manager.installer.VivoCompatibilityInputs(),
        val forceRepatch: Boolean = false,
        /** Authenticated current slot/partition facts for an automatically paired init_boot. */
        val pairedBootSnapshot: android.os.Bundle? = null,
        /** Explicit companion selection is export-only and bound to both previewed inputs. */
        val pairedBootUri: Uri? = null,
        val pairedSourceSha256: String? = null,
        val pairedBootSha256: String? = null,
    ) : FlashIt()

    @Parcelize
    data class DownloadBoot(
        val url: String,
        val partition: String,
        val lkm: LkmSelection,
        val allowShell: Boolean = false,
        val enableAdb: Boolean = false,
        val backup: Boolean = false,
        val blockModules: String = "",
        /** 见 [FlashBoot.targetMode]。 */
        val targetMode: InstallTargetMode = InstallTargetMode.DEFAULT,
        val variantId: String? = null,
        val existingRootConflictAccepted: Boolean = false,
        val restoreOriginalImage: Boolean = false,
        val originalManagerPackage: String? = null,
    ) : FlashIt()

    /**
     * @param action 这批安装来自哪个入口（开发方案 §4.1）。只影响兼容门的回执文案与证据归因，
     *   不参与命中判定。默认普通安装；**更新 / 市场快捷动作在各自的 Route 构造点显式传入**
     *   （否则回执会写成「安装」）。
     */
    @Parcelize
    data class FlashModules(
        val uris: List<Uri>,
        val action: dev.maplesu.manager.modules.compat.UltraModuleAction =
            dev.maplesu.manager.modules.compat.UltraModuleAction.INSTALL,
    ) : FlashIt()

    @Parcelize
    data object FlashRestore : FlashIt()

    @Parcelize
    data object FlashUninstall : FlashIt()
}

suspend fun flashModulesSequentially(
    uris: List<Uri>,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit,
    confirmModuleType: suspend (com.maplesu.core.ui.util.ModuleConfirmation) -> dev.maplesu.manager.modules.ModuleType?,
    /** 见 [FlashIt.FlashModules.action]。缺省按普通安装。 */
    action: dev.maplesu.manager.modules.compat.UltraModuleAction =
        dev.maplesu.manager.modules.compat.UltraModuleAction.INSTALL,
): FlashResult {
    if (uris.isEmpty()) {
        val message = ksuApp.getString(R.string.module_file_required)
        onStderr(message)
        return FlashResult(1, message, false)
    }
    var needsReboot = false
    var moduleOutcome: UltraOutcome? = null
    for (uri in uris) {
        val result = flashModule(uri, onStdout, onStderr, confirmModuleType, action)
        if (result.moduleOutcome == UltraOutcome.NEEDS_RECONCILIATION) {
            return result.copy(showReboot = false)
        }
        needsReboot = needsReboot || result.showReboot
        if (result.code != 0) return result.copy(showReboot = needsReboot)
        if (moduleOutcome == null || result.moduleOutcome == UltraOutcome.PENDING_REBOOT) {
            moduleOutcome = result.moduleOutcome
        }
    }
    return FlashResult(0, "", needsReboot, moduleOutcome = moduleOutcome)
}

suspend fun flashIt(
    flashIt: FlashIt,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit,
    confirmModuleType: suspend (com.maplesu.core.ui.util.ModuleConfirmation) -> dev.maplesu.manager.modules.ModuleType?,
    installationTaskId: String? = null,
    /** S5：结构化阶段回调（修补中 / 写入中 / 待重启）；页面不解析日志文本。 */
    onStage: (com.maplesu.core.ui.util.InstallStage) -> Unit = {},
): FlashResult {
    return when (flashIt) {
        is FlashIt.FlashBoot -> installBoot(
            flashIt.boot,
            flashIt.lkm,
            flashIt.ota,
            flashIt.partition,
            flashIt.allowShell,
            flashIt.enableAdb,
            flashIt.backup,
            flashIt.blockModules,
            onStdout,
            onStderr,
            installationTaskId,
            targetMode = flashIt.targetMode,
            variantId = flashIt.variantId,
            onStage = onStage,
            existingRootConflictAccepted = flashIt.existingRootConflictAccepted,
            restoreOriginalImage = flashIt.restoreOriginalImage,
            originalManagerPackage = flashIt.originalManagerPackage,
            lenovoSigning = flashIt.lenovoSigning,
            vivoInputs = flashIt.vivoInputs,
            forceRepatch = flashIt.forceRepatch,
            pairedBootSnapshot = flashIt.pairedBootSnapshot,
            pairedBootUri = flashIt.pairedBootUri,
            pairedSourceSha256 = flashIt.pairedSourceSha256,
            pairedBootSha256 = flashIt.pairedBootSha256,
        )

        is FlashIt.DownloadBoot -> downloadBoot(
            flashIt.url,
            flashIt.partition,
            flashIt.lkm,
            flashIt.allowShell,
            flashIt.enableAdb,
            flashIt.backup,
            flashIt.blockModules,
            onStdout,
            onStderr,
            targetMode = flashIt.targetMode,
            installationTaskId = installationTaskId,
            variantId = flashIt.variantId,
            onStage = onStage,
            existingRootConflictAccepted = flashIt.existingRootConflictAccepted,
            restoreOriginalImage = flashIt.restoreOriginalImage,
            originalManagerPackage = flashIt.originalManagerPackage,
        )

        is FlashIt.FlashModules -> {
            try {
                ModuleOperationService.run(ksuApp) {
                    flashModulesSequentially(
                        flashIt.uris, onStdout, onStderr, confirmModuleType, flashIt.action,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                android.util.Log.w("MapleSUModules", "module operation failed", error)
                val message = ksuApp.getString(R.string.module_operation_failed)
                onStderr(message)
                FlashResult(1, message, false)
            }
        }

        FlashIt.FlashRestore -> restoreBoot(onStdout, onStderr, installationTaskId)
        FlashIt.FlashUninstall -> uninstallPermanently(onStdout, onStderr, installationTaskId)
    }
}

@Composable
fun FlashEffect(
    flashIt: FlashIt,
    text: String,
    logContent: StringBuilder,
    onTextUpdate: (String) -> Unit,
    onShowRebootChange: (Boolean) -> Unit,
    onFlashingStatusChange: (FlashingStatus) -> Unit,
    enabled: Boolean = true,
    resumeModuleConfirmation: Boolean = false,
    confirmModuleType: suspend (com.maplesu.core.ui.util.ModuleConfirmation) -> dev.maplesu.manager.modules.ModuleType?,
) {
    var moduleOperationStarted by rememberSaveable(flashIt) { mutableStateOf(false) }
    var moduleOperationCompleted by rememberSaveable(flashIt) { mutableStateOf(false) }
    var moduleOutcomeName by rememberSaveable(flashIt) { mutableStateOf<String?>(null) }
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        if (flashIt is FlashIt.FlashModules && moduleOperationStarted) {
            val restoredOutcome = UltraOutcome.entries.firstOrNull { it.name == moduleOutcomeName }
            val restoredStatus = restoredOutcome?.moduleFlashingStatus()
            if (restoredStatus != null) {
                if (restoredStatus == FlashingStatus.PENDING) onShowRebootChange(false)
                onFlashingStatusChange(restoredStatus)
            } else if (!moduleOperationCompleted) {
                val message = ksuApp.getString(R.string.module_operation_interrupted)
                onTextUpdate(if (text.isBlank()) message else "$text\n$message")
                logContent.append(message).append('\n')
                moduleOutcomeName = UltraOutcome.NEEDS_RECONCILIATION.name
                onShowRebootChange(false)
                onFlashingStatusChange(FlashingStatus.PENDING)
            }
            return@LaunchedEffect
        }
        if (text.isNotEmpty() && !resumeModuleConfirmation) {
            return@LaunchedEffect
        }
        if (flashIt is FlashIt.FlashModules) moduleOperationStarted = true
        var currentText = text
        val mainHandler = Handler(Looper.getMainLooper())
        withContext(Dispatchers.IO) {
            flashIt(flashIt, onStdout = {
                val tempText = "$it\n"
                if (tempText.startsWith("[H[J")) { // clear command
                    currentText = tempText.substring(6)
                } else {
                    currentText += tempText
                }
                mainHandler.post {
                    onTextUpdate(currentText)
                }
                logContent.append(it).append("\n")
            }, onStderr = {
                logContent.append(it).append("\n")
            }, confirmModuleType = confirmModuleType).apply {
                val moduleStatus = if (flashIt is FlashIt.FlashModules) moduleOutcome?.moduleFlashingStatus() else null
                val resultStatus = moduleStatus ?: if (code == 0) FlashingStatus.SUCCESS else FlashingStatus.FAILED
                val pending = resultStatus == FlashingStatus.PENDING
                if (pending) {
                    if (err.isNotBlank()) currentText += "$err\n"
                    mainHandler.post { onTextUpdate(currentText) }
                } else if (resultStatus == FlashingStatus.FAILED) {
                    currentText += "Error code: $code.\n $err Please save and check the log.\n"
                    mainHandler.post {
                        onTextUpdate(currentText)
                    }
                }
                if (showReboot && !pending) {
                    currentText += "\n\n\n"
                    mainHandler.post {
                        onTextUpdate(currentText)
                        onShowRebootChange(true)
                    }
                }
                mainHandler.post {
                    if (flashIt is FlashIt.FlashModules) {
                        moduleOperationCompleted = true
                        moduleOutcomeName = (moduleOutcome ?: if (code == 0) UltraOutcome.COMPLETED else UltraOutcome.FAILED).name
                    }
                    if (pending) onShowRebootChange(false)
                    onFlashingStatusChange(resultStatus)
                }
            }
        }
    }
}

@Composable
fun saveLog(
    logContent: StringBuilder,
    scope: CoroutineScope,
    failMessage: String,
    showMessage: (String) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val savedMessage = stringResource(R.string.log_saved)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri, "wt")?.use {
                            it.write(logContent.toString().toByteArray(Charsets.UTF_8))
                        } ?: error("DOCUMENT_WRITE_FAILED")
                    }.isSuccess
                }
                showMessage(if (saved) savedMessage else failMessage)
            }
        }
    }
    return {
        val date = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(Date())
        launcher.launch("MapleSU_install_log_$date.log")
    }
}

private const val JAILBREAK_WARNING_COUNTDOWN = 10

@Composable
fun JailbreakFlashWarningDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var countdown by remember { mutableIntStateOf(JAILBREAK_WARNING_COUNTDOWN) }

    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000.milliseconds)
            countdown--
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(android.R.string.dialog_alert_title)) },
        text = {
            Text(
                stringResource(R.string.jailbreak_flash_warning),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = countdown == 0
            ) {
                Text(
                    if (countdown > 0)
                        stringResource(R.string.jailbreak_flash_warning_countdown, countdown)
                    else
                        stringResource(R.string.install_next)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
