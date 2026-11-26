package com.maplesu.core.ui.screen.install

import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.maplesu.core.R
import com.maplesu.core.ui.LocalUiMode
import com.maplesu.core.ui.UiMode
import com.maplesu.core.ui.navigation3.LocalNavigator
import com.maplesu.core.ui.navigation3.Route
import com.maplesu.core.ui.screen.flash.FlashIt
import com.maplesu.core.ui.screen.home.HomeInstallSignal
import com.maplesu.core.ui.screen.home.readInstallSignal
import com.maplesu.core.ui.security.HiddenFactsSignal
import com.maplesu.core.ui.security.HiddenStartupConfirm
import com.maplesu.core.ui.util.LkmSelection
import com.maplesu.core.ui.util.packagedKmiCatalog
import dev.maplesu.manager.installer.InstallPartitionPolicy
import dev.maplesu.manager.installer.InstallPartition
import dev.maplesu.manager.installer.LenovoSigningOptions
import dev.maplesu.manager.installer.lenovoFingerprint
import dev.maplesu.manager.installer.InstallPartitionSelection
import dev.maplesu.manager.installer.InstallTargetDispatch
import dev.maplesu.manager.installer.InstallTargetMode
import dev.maplesu.manager.installer.InstallTargetModePolicy
import dev.maplesu.manager.installer.UltraDirectInstallPolicy
import dev.maplesu.manager.workmode.ui.WorkModeSwitchSession
import dev.maplesu.manager.workmode.ModeRuntime
import dev.maplesu.manager.workmode.detect.probes.EnvironmentCheckProduction
import dev.maplesu.manager.runtime.ultra.UltraEnvironmentProbe
import top.yukonga.miuix.kmp.basic.SnackbarHostState as MiuixSnackbarHostState

/**
 * 安装页 = 四步里的前三步，刷写页 = 第四步（修补成功/失败）。
 *
 * ```
 * ①  目标工作模式 + 分区  Ultra（默认）/ Normal；按实际载体选择 init_boot / boot
 * ②  选择 boot           选文件 / 当前 boot（当前 boot = 直接安装，需要 root 会话）
 * ③  目标内核             boot 显示自身内核；init_boot 可选 KMI，实际组件在修补时核实
 *     「下一步」          选文件 = 修补并导出；直接安装 = 修补并写入
 * ④  修补成功            FlashScreen 的成功态：导出路径或写入结果
 * ```
 *
 * 目标模式与分区的语义（与 KernelSU 一致）：
 *  - 选择**不持久化**：每次打开本页都从 Ultra 开始（默认值不会被"上次选过 Normal"改写）；
 *  - Normal + 直接安装：源镜像由 root 从**当前槽的目标分区**读出来再修补，**不要求先选文件**；
 *  - 目标分区在点击时快照；当前载体、原图和槽位由安装事务在 PRE 阶段核实。
 *
 * 与旧页面的差别（用户口径：「不需要什么记录乱七八糟的东西」）：
 *  - 原管理器迁移并入直接安装，高级选项控制是否自动还原匹配的原始镜像；
 *  - 没有等级徽标 / 补丁条数 / 备选档 / 确认摘要 / 事务回读卡片。
 *
 * 页面只收集选择；镜像格式、组件、实际 Root 与分区写入检查在对应执行阶段完成。
 */
@Composable
fun InstallScreen(
    initialTarget: InstallTargetMode = InstallTargetMode.DEFAULT,
    migrationEntry: Boolean = false,
    onBack: (() -> Unit)? = null,
    deviceFingerprint: String? = null,
) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    val miuixSnackbarHost = remember { MiuixSnackbarHostState() }
    val uiMode = LocalUiMode.current
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val moduleChoiceModel = viewModel<UltraModuleChoiceViewModel>()
    val moduleChoice by moduleChoiceModel.state.collectAsStateWithLifecycle()
    var installAfterModuleChoice by rememberSaveable { mutableStateOf(false) }

    var installMethod by rememberSaveable { mutableStateOf<InstallMethod?>(null) }
    var sourceRequest by rememberSaveable { mutableIntStateOf(0) }
    var pageResumed by remember { mutableStateOf(false) }
    var existingRootConflictAccepted by rememberSaveable { mutableStateOf(false) }
    var restoreOriginalImage by rememberSaveable { mutableStateOf(false) }
    var originalManagerPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var originalManagers by remember { mutableStateOf(emptyList<OriginalRootManager>()) }
    var signing by rememberSaveable { mutableStateOf(LenovoSigningOptions()) }
    val vivoDevice = remember { dev.maplesu.manager.installer.VivoCompatibility.deviceSupported() }
    var vivoInputs by rememberSaveable { mutableStateOf(dev.maplesu.manager.installer.VivoCompatibilityInputs()) }
    var vivoImageName by rememberSaveable { mutableStateOf<String?>(null) }
    var vivoBootWarning by rememberSaveable { mutableStateOf(false) }
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
    var signingTouched by rememberSaveable { mutableStateOf(false) }
    var signingDialog by rememberSaveable { mutableStateOf(false) }
    var aospDraft by rememberSaveable { mutableStateOf(false) }
    var signingImageName by rememberSaveable { mutableStateOf<String?>(null) }
    var managerInstalled by remember { mutableStateOf<Boolean?>(null) }
    val detectionRevision by ModeRuntime.detectionRevision.collectAsStateWithLifecycle()
    val hiddenRevision by HiddenFactsSignal.revision.collectAsStateWithLifecycle()
    // 只读已确认的隐藏事实；变化当帧收起入口，不等后台权限检查返回。
    val directInstallSuppressed = remember(context, detectionRevision, hiddenRevision) {
        val mapleModeKnown = ModeRuntime.active != null ||
            EnvironmentCheckProduction.store(context).snapshot()?.mode != null
        HiddenStartupConfirm.masking(context) ||
            HiddenStartupConfirm.isPending(context) ||
            (mapleModeKnown && HiddenStartupConfirm.current(context))
    }
    // 事务进行中：只用于挡连点。
    var installInFlight by rememberSaveable { mutableStateOf(false) }
    // Consume the shared home fact without starting or awaiting another probe.
    LaunchedEffect(deviceFingerprint, installInFlight) {
        if (!installInFlight && !signingTouched && lenovoFingerprint(deviceFingerprint)) {
            signingTouched = true
            advancedExpanded = true
            signing = signing.copy(enabled = true)
        }
    }

    // ── 步骤 ①：目标工作模式 + 目标分区 ────────────────────────────────────
    // 默认 Ultra（每次打开本页都是 Ultra：选择**不持久化**，避免"上次选过 Normal 就再也回不到
    // 默认值"）。Normal 按实际分区和 KMI 选择；Ultra 更新保持已观测载体。
    var targetMode by rememberSaveable { mutableStateOf(initialTarget) }
    val ultraAction = UltraInstallAction.AUTOMATIC
    var storedPartition by rememberSaveable { mutableStateOf(InstallPartitionPolicy.NORMAL_DEFAULT) }
    var partitionTouched by rememberSaveable { mutableStateOf(false) }
    var imagePartition by remember { mutableStateOf<InstallPartition?>(null) }
    // 事务进行中：选择器仍然可见但不可操作（`InstallTargetModePolicy` 是既有策略，纯函数）。
    val selectionsLocked = !InstallTargetModePolicy.selectable(
        installRunning = installInFlight,
        switchRunning = WorkModeSwitchSession.executing,
    ) || moduleChoice.show || moduleChoice.busy

    // 设备当前运行内核：步骤 ③ 的"当前 boot"来源直接用它的 release（不开 root）。
    var deviceKernelRelease by remember { mutableStateOf<String?>(null) }
    var kernelReleaseLoading by remember { mutableStateOf(true) }
    var initBootKmi by rememberSaveable(targetMode) { mutableStateOf<String?>(null) }
    var initBootKmis by remember(targetMode) { mutableStateOf(emptyList<String>()) }
    val partition = if (installMethod is InstallMethod.SelectFile && imagePartition != null) imagePartition!!
        else if (targetMode == InstallTargetMode.ULTRA) {
            if (partitionTouched) storedPartition else InstallPartition.BOOT
        }
        else if (partitionTouched) storedPartition
        else InstallPartitionPolicy.automaticNormal(null,
            dev.maplesu.manager.workmode.switch.ModeSwitchKmi.parse(deviceKernelRelease))
    LaunchedEffect(Unit) {
        deviceKernelRelease = withContext(Dispatchers.IO) { readDeviceKernelRelease() }
        kernelReleaseLoading = false
    }
    LaunchedEffect(targetMode) {
        initBootKmis = withContext(Dispatchers.IO) {
            when (targetMode) {
                InstallTargetMode.ULTRA -> runCatching {
                    dev.maplesu.manager.ultrakernel.UltraKernelFlow.availableInitBootKmis(context)
                }.getOrDefault(emptyList())
                InstallTargetMode.NORMAL -> packagedKmiCatalog(context).orEmpty()
            }
        }
    }
    LifecycleResumeEffect(Unit) {
        // 从刷写页回来 → 允许下一次安装。
        installInFlight = false
        onPauseOrDispose { }
    }
    LifecycleResumeEffect(context, detectionRevision, hiddenRevision) {
        pageResumed = true
        val job = scope.launch {
            managerInstalled = withContext(Dispatchers.IO) {
                when (readInstallSignal(ModeRuntime.active)) {
                    HomeInstallSignal.INSTALLED -> true
                    HomeInstallSignal.NOT_INSTALLED -> false
                    HomeInstallSignal.UNKNOWN -> null
                }
            }
            originalManagers = withContext(Dispatchers.IO) { originalRootManagers(context) }
            if (originalManagerPackage != null && originalManagers.none { it.packageName == originalManagerPackage }) {
                originalManagerPackage = null
                sourceRequest++
            }
        }
        onPauseOrDispose {
            pageResumed = false
            // 返回手势/暂时离开不改变来源选择；操作仍由 pageResumed 限制。
            job.cancel()
        }
    }
    val selectFileTip = stringResource(id = R.string.install_select_image_summary)

    // ── 步骤 ③：boot 来源的内核事实（只读探测，不写 files/images/imported.img）──────────
    // 识别完立刻删临时副本：`imported.img` 在确认之前必须保持"现场 boot 的备份"语义（47 报告 R3）。
    var bootFlow by remember { mutableStateOf<BootFlowProfile?>(null) }
    val bootSourceKey = when (val method = installMethod) {
        is InstallMethod.SelectFile -> method.uri?.toString().orEmpty()
        InstallMethod.DirectInstall -> "current"
        else -> ""
    }
    LaunchedEffect(bootSourceKey, sourceRequest) {
        imagePartition = null
        val uri = (installMethod as? InstallMethod.SelectFile)?.uri ?: return@LaunchedEffect
        imagePartition = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use(InstallPartitionPolicy::imagePartition)
            }.getOrNull()
        }
    }
    // 文件来源不随 uname 变化重跑（避免为了显示一行版本把几百 MB 镜像拷两遍）。
    val bootProbeKey = when (installMethod) {
        InstallMethod.DirectInstall -> deviceKernelRelease
        else -> null
    }
    LaunchedEffect(bootSourceKey, sourceRequest, bootProbeKey, kernelReleaseLoading, targetMode, detectionRevision, pageResumed) {
        if (!pageResumed) return@LaunchedEffect
        // Normal 的目标是"设备 KMI × 分区"，与 Ultra 内核版本无关：不做这次探测
        // （既不白拷几百 MB 镜像，也不会在 Normal 下闪出 Ultra 的"修不了"）。
        if (targetMode == InstallTargetMode.NORMAL) {
            bootFlow = null
            return@LaunchedEffect
        }
        // 等设备内核读完再探测：否则"当前 boot"来源会先闪一个"读不到内核版本"再变成真的。
        if (kernelReleaseLoading && installMethod == InstallMethod.DirectInstall) return@LaunchedEffect
        val method = installMethod
        val uri = (method as? InstallMethod.SelectFile)?.uri
        if (method !is InstallMethod.DirectInstall && uri == null) {
            bootFlow = null
            return@LaunchedEffect
        }
        val device = deviceKernelRelease.orEmpty()
        bootFlow = InstallBootFlow.reading(bootSourceKey, device)
        val probed = withContext(Dispatchers.IO) {
            try {
                if (uri != null) probeChosenBoot(context.applicationContext, uri, device)
                else probeCurrentBoot(device)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val code = error.message.orEmpty().substringBefore(':').takeIf {
                    it.startsWith("ULTRA_INIT_") || it.startsWith("ULTRA_KERNEL_") || it.startsWith("INSTALLER_")
                } ?: "ULTRA_KERNEL_BASE_UNAVAILABLE"
                InstallBootFlow.failed(bootSourceKey, device, code, error.message.orEmpty())
            }
        }
        bootFlow = probed
    }
    val installMethodOptions = remember(selectFileTip, directInstallSuppressed) {
        buildList {
            add(InstallMethod.SelectFile(summary = selectFileTip))
            if (!directInstallSuppressed) add(InstallMethod.DirectInstall)
        }
    }

    fun showMessage(message: String) {
        scope.launch {
            if (uiMode == UiMode.Material) {
                snackbarHost.showSnackbar(message)
            } else {
                miuixSnackbarHost.showSnackbar(message)
            }
        }
    }

    val onInstall = onInstall@{
        // UI 尚未重组时也拦住连点，以及其他入口刚发起的切换。
        if (!pageResumed || installInFlight || WorkModeSwitchSession.executing) return@onInstall
        if (moduleChoice.show || moduleChoice.busy) return@onInstall
        val method = installMethod ?: return@onInstall
        if (method == InstallMethod.DirectInstall && directInstallSuppressed) return@onInstall
        if (method == InstallMethod.DirectInstall && !existingRootConflictAccepted) return@onInstall
        if (targetMode == InstallTargetMode.ULTRA) {
            val route = ultraInstallRoute(ultraAction, partition.wire, null,
                null, method == InstallMethod.DirectInstall, null)
            if (route.blocker != null) {
                showMessage(route.blocker)
                return@onInstall
            }
        }
        if (method == InstallMethod.DirectInstall &&
            UltraDirectInstallPolicy.requiresModuleChoice(targetMode, ModeRuntime.active) &&
            moduleChoice.keepModules == null) {
            installAfterModuleChoice = true
            moduleChoiceModel.request()
            return@onInstall
        }
        if (installMethodOptions.none { it::class == method::class }) {
            showMessage(resources.getString(R.string.install_method_stale))
            return@onInstall
        }
        // 步骤 ① 的选择在事务开始这一刻快照（之后再改不影响本次事务）。
        val mode = targetMode
        val selectedPartition = partition
        val requestedPartition = selectedPartition.wire
        val resolvedAction = resolvedUltraInstallAction(ultraAction, requestedPartition, null,
            method == InstallMethod.DirectInstall)
        val signingSnapshot = signing
        if (signingSnapshot.enabled && method is InstallMethod.SelectFile &&
                !signingSnapshot.fileInputsReady(selectedPartition.wire)) {
            showMessage(resources.getString(R.string.install_lenovo_images_required))
            return@onInstall
        }
        // 预览仅供展示；执行链按本次源镜像或实际 PRE 选择并验证组件。
        val selectedLkm = if (selectedPartition == InstallPartition.INIT_BOOT &&
            (mode == InstallTargetMode.ULTRA || method is InstallMethod.SelectFile))
            initBootKmi?.let { LkmSelection.KmiString(it) } ?: LkmSelection.KmiNone else LkmSelection.KmiNone
        val request = when (method) {
            is InstallMethod.SelectFile -> {
                val image = method.uri ?: return@onInstall
                FlashIt.FlashBoot(
                    boot = image,
                    lkm = selectedLkm,
                    ota = false,
                    partition = requestedPartition,
                    targetMode = mode,
                    lenovoSigning = signingSnapshot,
                    vivoInputs = vivoInputs,
                    forceRepatch = mode == InstallTargetMode.ULTRA && resolvedAction.forceRepatch,
                )
            }

            InstallMethod.DirectInstall -> {
                FlashIt.FlashBoot(
                    lkm = selectedLkm,
                    ota = false,
                    partition = requestedPartition,
                    targetMode = mode,
                    existingRootConflictAccepted = existingRootConflictAccepted,
                    restoreOriginalImage = restoreOriginalImage ||
                        mode == InstallTargetMode.ULTRA && resolvedAction.restoresOtherCarrier,
                    originalManagerPackage = originalManagerPackage,
                    lenovoSigning = signingSnapshot,
                    vivoInputs = vivoInputs,
                    forceRepatch = mode == InstallTargetMode.ULTRA && resolvedAction.forceRepatch,
                )
            }

            else -> return@onInstall
        }
        installInFlight = true
        navigator.push(Route.Flash(request))
    }

    val selectImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { uri ->
                moduleChoiceModel.reset()
                installAfterModuleChoice = false
                existingRootConflictAccepted = false
                imagePartition = null
                bootFlow = null
                restoreOriginalImage = false
                installMethod = InstallMethod.SelectFile(uri, summary = selectFileTip)
                sourceRequest++
                if (vivoDevice) advancedExpanded = true
            }
        }
    }
    val vivoImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val name = vivoImageName; vivoImageName = null
        if (uri != null && name != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vivoInputs = if (name == "vendor_boot") vivoInputs.copy(vendorBoot = uri) else vivoInputs.copy(initBoot = uri)
        }
    }
    val signingImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val name = signingImageName
        signingImageName = null
        if (uri != null && name != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            signing = when (name) {
                "vbmeta" -> signing.copy(vbmeta = uri)
                "recovery" -> signing.copy(recovery = uri)
                "vbmeta_system" -> signing.copy(vbmetaSystem = uri)
                else -> signing
            }
        }
    }

    // File KMI resolution belongs to the producer; opening a file does not wait for uname.
    val normalDecision = if (targetMode == InstallTargetMode.NORMAL &&
        (installMethod is InstallMethod.SelectFile || !kernelReleaseLoading)) {
        InstallTargetDispatch.decide(
            installTargetRequestFor(
                method = installMethod,
                target = InstallTargetMode.NORMAL,
                kernelRelease = deviceKernelRelease,
                sdkInt = Build.VERSION.SDK_INT,
                partition = partition.wire,
            ),
        )
    } else {
        null
    }

    val state = InstallUiState(
        installMethod = installMethod,
        installMethodOptions = installMethodOptions,
        bootFlow = bootFlow,
        targetMode = targetMode,
        partition = partition,
        partitionOptions = if (targetMode == InstallTargetMode.ULTRA) {
            if (installMethod is InstallMethod.SelectFile && imagePartition != null) listOf(imagePartition!!)
            else InstallPartition.ALLOWED
        } else InstallPartitionPolicy.optionsFor(targetMode),
        targetSelectable = !selectionsLocked,
        normalDecision = normalDecision,
        migrationEntry = migrationEntry,
        managerInstalled = managerInstalled,
        directAccessReady = !directInstallSuppressed && pageResumed && existingRootConflictAccepted,
        sourceUsesManagedRoot = ModeRuntime.active != null || UltraEnvironmentProbe.usesUltraBackend(context),
        restoreOriginalImage = restoreOriginalImage,
        originalManagers = originalManagers,
        originalManagerPackage = originalManagerPackage,
        advancedExpanded = advancedExpanded,
        lenovoSigning = signing,
        vivoDevice = vivoDevice,
        vivoInputs = vivoInputs,
        initBootKmi = initBootKmi,
        initBootKmis = initBootKmis,
    )
    LaunchedEffect(moduleChoice.keepModules, moduleChoice.show, pageResumed, state.canInstall) {
        if (installAfterModuleChoice && pageResumed && state.canInstall && !moduleChoice.show && moduleChoice.keepModules != null) {
            installAfterModuleChoice = false
            onInstall()
        }
    }
    val actions = InstallScreenActions(
        onBack = dropUnlessResumed { if (!moduleChoice.busy) onBack?.invoke() ?: navigator.pop() },
        onSelectMethod = { method ->
            if (!selectionsLocked && pageResumed) {
                if (method == InstallMethod.DirectInstall) {
                    if (!directInstallSuppressed) {
                        if (installMethod != InstallMethod.DirectInstall) {
                            restoreOriginalImage = false
                        }
                        existingRootConflictAccepted = true
                        installMethod = InstallMethod.DirectInstall
                        sourceRequest++
                        if (UltraDirectInstallPolicy.requiresModuleChoice(targetMode, ModeRuntime.active) &&
                            moduleChoiceModel.state.value.keepModules == null) moduleChoiceModel.request()
                    }
                }
                else {
                    moduleChoiceModel.reset()
                    installAfterModuleChoice = false
                    existingRootConflictAccepted = false
                    restoreOriginalImage = false
                    installMethod = method
                    sourceRequest++
                }
            }
        },
        onSelectBootImage = {
            selectImageLauncher.launch(
                Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
            )
        },
        onSelectInitBootKmi = { kmi ->
            if (!selectionsLocked && pageResumed && (kmi == null || kmi in initBootKmis)) {
                initBootKmi = kmi
            }
        },
        onSelectTargetMode = { mode ->
            if (!selectionsLocked && pageResumed) {
                if (targetMode != mode) {
                    moduleChoiceModel.reset()
                    installAfterModuleChoice = false
                    targetMode = mode
                    partitionTouched = false
                    sourceRequest++
                }
                if (UltraDirectInstallPolicy.requiresModuleChoice(mode, ModeRuntime.active) &&
                    !directInstallSuppressed && installMethod == InstallMethod.DirectInstall) {
                    moduleChoiceModel.request()
                }
            }
        },
        onSelectPartition = { requested ->
            if (vivoDevice && targetMode == InstallTargetMode.NORMAL && requested == InstallPartition.BOOT &&
                requested != partition && !selectionsLocked) {
                vivoBootWarning = true
                return@InstallScreenActions
            }
            // Ultra 按实际选项和当前操作选择；Normal 保留既有分区策略。
            val next = if (targetMode == InstallTargetMode.ULTRA) {
                if (!selectionsLocked && requested in state.partitionOptions && !state.partitionFixed) requested else storedPartition
            } else InstallPartitionSelection.select(
                mode = targetMode,
                stored = storedPartition,
                requested = requested,
                locked = selectionsLocked,
            )
            if (next != storedPartition) {
                sourceRequest++
            }
            storedPartition = next
            if (!selectionsLocked && !state.partitionFixed) partitionTouched = true
        },
        onNext = {
            if (!installInFlight && !WorkModeSwitchSession.executing) {
                onInstall()
            }
        },
        onToggleAdvanced = { advancedExpanded = !advancedExpanded },
        onLenovoSigningChange = { enabled ->
            if (!selectionsLocked) {
                signingTouched = true
                if (enabled) { aospDraft = signing.aosp; signingDialog = true }
                else signing = signing.copy(enabled = false)
            }
        },
        onConfigureLenovoSigning = {
            if (!selectionsLocked) { signingTouched = true; aospDraft = signing.aosp; signingDialog = true }
        },
        onSelectVivoImage = { name ->
            if (!selectionsLocked) { vivoImageName = name; vivoImageLauncher.launch(arrayOf("*/*")) }
        },
        onSelectSigningImage = { name ->
            if (!selectionsLocked) { signingImageName = name; signingImageLauncher.launch(arrayOf("*/*")) }
        },
        onRestoreOriginalImageChange = { restore ->
            if (!selectionsLocked) {
                restoreOriginalImage = restore
            }
        },
        onOriginalManagerChange = { managerPackage ->
            if (!selectionsLocked) {
                originalManagerPackage = managerPackage
            }
        },
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> InstallScreenMiuix(state, actions, miuixSnackbarHost)
        UiMode.Material -> InstallScreenMaterial(state, actions, snackbarHost)
    }
    UltraModuleChoiceDialog(
        state = moduleChoice,
        onKeep = moduleChoiceModel::keep,
        onRemove = { moduleChoiceModel.remove(context) },
        onDismiss = {
            installAfterModuleChoice = false
            moduleChoiceModel.dismiss()
        },
    )
    LenovoSigningDialog(
        show = signingDialog, aosp = aospDraft,
        onAospChange = { aospDraft = it },
        onConfirm = { signing = signing.copy(enabled = true, aosp = aospDraft); signingDialog = false },
        onDismiss = { signingDialog = false },
    )
    VivoBootChoiceDialog(show = vivoBootWarning,
        onConfirm = {
            vivoBootWarning = false
            if (!selectionsLocked && targetMode == InstallTargetMode.NORMAL) {
                storedPartition = InstallPartitionSelection.select(targetMode, storedPartition, InstallPartition.BOOT)
                partitionTouched = true
                sourceRequest++
            }
        }, onDismiss = { vivoBootWarning = false })
}

/**
 * 设备内核 release（与 `uname -r` 等价）。
 *
 * 读不到 → null：步骤 ③ 的"当前 boot"来源会显示"读不到内核版本"，不猜。
 * 只读 `/proc`，不开 root、不 exec ksud（未安装 root 时本页也要能渲染）。
 */
private fun readDeviceKernelRelease(): String? {
    val fromProc = runCatching {
        java.io.File("/proc/sys/kernel/osrelease").readText().trim()
    }.getOrNull()?.takeIf { it.isNotEmpty() }
    if (fromProc != null) return fromProc
    return runCatching { System.getProperty("os.version")?.trim() }
        .getOrNull()
        ?.takeIf { it.isNotEmpty() }
}
