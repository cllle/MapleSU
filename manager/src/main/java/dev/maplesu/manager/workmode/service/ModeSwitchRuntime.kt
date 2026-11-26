package dev.maplesu.manager.workmode.service

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import dev.maplesu.manager.ksu.KsuCandidateCommitPort
import dev.maplesu.manager.ksu.KsuCommitResult
import dev.maplesu.manager.ksu.KsuInstallProducer
import dev.maplesu.manager.ksu.KsuProduceResult
import dev.maplesu.manager.ksu.KsuRootCommandRunner
import dev.maplesu.manager.ksu.KsuShellResult
import dev.maplesu.manager.ksu.LibsuRootCommandRunner
import dev.maplesu.manager.ksu.ensureNormalBootUserland
import dev.maplesu.manager.ultrakernel.UltraKernelFlow
import dev.maplesu.manager.ultrakernel.UltraKernelContextAssets
import dev.maplesu.manager.modules.UltraRootKeyStore
import dev.maplesu.manager.installer.RootScriptResult
import dev.maplesu.manager.runtime.RootExecResult
import dev.maplesu.manager.workmode.storage.AndroidModeSwitchDataPaths
import dev.maplesu.manager.workmode.switch.CandidateResult
import dev.maplesu.manager.workmode.switch.ModeSwitchHashes
import dev.maplesu.manager.runtime.ultra.UltraInstallCredentials
import dev.maplesu.manager.runtime.RootExec
import dev.maplesu.manager.workmode.WorkMode
import dev.maplesu.manager.workmode.control.ControlPaths
import dev.maplesu.manager.workmode.control.schema.AppSnapshotFacts
import dev.maplesu.manager.workmode.control.schema.BootFacts
import dev.maplesu.manager.workmode.control.schema.ManagerFacts
import dev.maplesu.manager.workmode.control.schema.ModeEntry
import dev.maplesu.manager.workmode.control.schema.ModesFile
import dev.maplesu.manager.workmode.control.ControlRead
import dev.maplesu.manager.workmode.snapshot.FileSnapshotService
import dev.maplesu.manager.workmode.snapshot.SnapshotService
import dev.maplesu.manager.workmode.switch.ModeCommitSink
import dev.maplesu.manager.workmode.switch.ModeSwitchSnapshots
import dev.maplesu.manager.workmode.switch.NoOpSwitchQuiesceHooks
import dev.maplesu.manager.workmode.switch.SwitchCandidateProducer
import dev.maplesu.manager.workmode.switch.SwitchQuiesceHooks
import dev.maplesu.manager.workmode.switch.HiddenSwitchCoordinator
import dev.maplesu.manager.workmode.switch.UltraEnvSwitchCoordinator
import dev.maplesu.manager.workmode.switch.HookResult
import dev.maplesu.manager.workmode.switch.SuperuserReconciler
import java.io.File

/**
 * 生产装配单例（W08 App 侧）。
 *
 * 初始化时机：Application/UI 首次进入工作模式页时调用 [initialize]（未判定态也可安全初始化：
 * [ModeSwitchFacade.canSwitch] 会返回 `SWITCH_STATE_UNDETERMINED`，不执行任何切换准备）。
 *
 * 依赖原则：能真实构造的全部真实构造（installer 客户端、环境、W09/W12 producer），
 * 不能构造的（W19 hooks、KSU 原生依赖缺失、AppSnapshotFacts 读取失败）按 fail-closed 暴露，
 * **不伪造成功**。
 */
object ModeSwitchRuntime {

    private const val TAG = "ModeSwitchRuntime"

    @Volatile
    private var facade: ModeSwitchFacade? = null

    /** 默认装配的执行方，仅供诊断，不表示 root 已就绪。 */
    @Volatile
    private var configuredClientKind: ModeSwitchClientKind? = null

    @Volatile
    private var lastError: String? = null

    fun initialize(
        context: Context,
        hooks: SwitchQuiesceHooks? = null,
        snapshots: ModeSwitchSnapshots? = null,
        mirrorProvider: ModeSwitchMirrorProvider? = null,
        ksuBridge: KsuSwitchProducerBridge? = null,
        modeCommit: ModeCommitSink? = null,
        environment: ModeSwitchAppEnvironment? = null,
        client: ModeSwitchClient? = null,
        workRoot: File? = null,
        superuserReconciler: SuperuserReconciler? = null,
        /** H5 隐藏状态守卫；null = 生产默认（KSU stealth + 按模式持久化）。 */
        hiddenCoordinator: HiddenSwitchCoordinator? = null,
        /** P0 Ultra 环境互操作守卫；null = 生产默认（只读探针 + 不可用动作显式失败）。 */
        ultraCoordinator: UltraEnvSwitchCoordinator? = null,
    ): ModeSwitchFacade {
        synchronized(this) {
            facade?.let { return it }
            val appContext = context.applicationContext
            val root = workRoot ?: File(appContext.filesDir, "workmode/switch-work")
            val rootSession = ModeSwitchRootSession(appContext)
            // Pin the helper before teardown; an uncertain result also blocks failure recovery.
            val pinnedRoot = ModeSwitchRootPin(rootSession::ensureReady, rootSession::execute,
                rootSession::uninstallUltra, rootSession::acquire, rootSession::release)
            val durableRoot: (String) -> RootExecResult = pinnedRoot::execute
            val authorizationRunner = dev.maplesu.manager.workmode.superuser.RootScriptRunner { script ->
                val result = durableRoot(script)
                dev.maplesu.manager.workmode.superuser.RootScriptResult(result.code, result.output)
            }
            val authorizationControl = RootShellControlPlane(appContext, runner = authorizationRunner)
            // Export and post-boot reconciliation use the same pinned root transport as the switch.
            val runtimeMirrors = mirrorProvider ?: runCatching {
                AndroidModeSwitchMirrorProvider.production(appContext, authorizationRunner)
            }.getOrElse { ModeSwitchMirrorProvider.UNAVAILABLE }
            val runtimeEnvironment = environment ?: AndroidSwitchEnvironment(appContext, runtimeMirrors)
            var frozenPaths: AndroidModeSwitchDataPaths? = null
            val pathsFor: (WorkMode) -> List<dev.maplesu.manager.workmode.storage.MoveEntry> = { mode ->
                checkNotNull(frozenPaths) { "SWITCH_DATA_PATHS_NOT_FROZEN" }.entries(mode)
            }
            val runtimeClient = client ?: buildClient(appContext, root, durableRoot)
            val ordinaryClient = if (client != null) runtimeClient else ServiceResumeModeSwitchClient(
                runtimeClient, ServiceClientModeSwitchClient(appContext),
            ) { hasServiceHistory(durableRoot) }
            val producers = buildMap<WorkMode, SwitchCandidateProducer> {
                buildLiteProducer(appContext)?.let { put(WorkMode.LITE, it) }
                put(WorkMode.KSU, KsuSwitchCandidateProducer(
                    ksuBridge ?: defaultKsuBridge(appContext, durableRoot),
                ))
            }
            val runtimeSnapshots = snapshots ?: defaultSnapshots(appContext)
            val durableCommit = modeCommit ?: runCatching {
                RootModeCommitSink(durableRoot,
                    initial = {
                        val app = requireNotNull(appSnapshotFacts(appContext))
                        val device = requireNotNull(runtimeEnvironment.deviceFacts())
                        val shared = authorizationControl.readSharedSuperuser() as? ControlRead.Ok
                        ModesFile(revision = 0, updatedAt = 0, bootId = runtimeEnvironment.bootId(),
                            manager = ManagerFacts(android.os.Process.myUid(), app.packageName,
                                app.apkSha256, app.signerSha256, app.versionCode),
                            current = ModesFile.CURRENT_NONE, sharedRevision = shared?.value?.revision ?: 0,
                            owners = emptyList(), modes = listOf(
                                ModeEntry("A", "maplesu-ultra", 0, "pending-attention", dataRoot = "/data/adb/maplesu"),
                                ModeEntry("B", "maplesu-ksu-uapi7", 0, "pending-attention", dataRoot = "/data/adb/ksu"),
                            ), boot = BootFacts(device.slot, "", "", ModesFile.CURRENT_NONE,
                                kernelRelease = device.kernelRelease, firmware = device.firmware))
                    })
            }.getOrNull()
            // T14：模式**真的**被换掉（commit 成功）⇒ 上次环境状态失效，下次必须重检；
            // commit 失败不置脏（状态没变，"上次状态"仍然有效）。
            val commitSink = durableCommit?.let { sink ->
                dev.maplesu.manager.workmode.switch.ModeCommitSink { mode, bootId, bootSha256, initBootSha256 ->
                    sink.commit(mode, bootId, bootSha256, initBootSha256)
                    runCatching {
                        dev.maplesu.manager.workmode.detect.probes.EnvironmentCheckProduction.markDirty(
                            appContext,
                            dev.maplesu.manager.workmode.detect.EnvironmentInvalidation.MODE_SWITCHED,
                        )
                    }
                }
            }
            val transactionClient = LenovoSignedSwitchClient(ordinaryClient,
                dev.maplesu.manager.installer.NativeSignedSwitchClient(appContext))
            val lineage = ModeSwitchCandidateInput(UltraKernelContextAssets(appContext),
                dev.maplesu.manager.compat.UltraStateStorage.flash(appContext.filesDir))
            val built = ModeSwitchFacade(
                client = transactionClient,
                prepareCandidateInput = { input, status -> lineage.prepare(input, status, transactionClient) },
                environment = runtimeEnvironment,
                producers = producers,
                workRoot = root,
                prepareCompatibility = if (dev.maplesu.manager.installer.VivoCompatibility.deviceSupported()) {
                    { mode, candidate, sources, id -> dev.maplesu.manager.installer.VivoCompatibility.prepare(
                        appContext, mode, candidate, sources, operationId = id) }
                } else null,
                snapshots = runtimeSnapshots,
                // W19：未显式注入时也要尝试安装真实植入记录器并构造可证明恢复的 hooks；
                // 失败 → NoOp（implantRestoreReady=false，S0-15 fail-closed）。
                hooks = hooks ?: buildW19Hooks(appContext) ?: NoOpSwitchQuiesceHooks,
                modeCommit = commitSink,
                // S6.4：真实 SuperuserSync.apply/verify；不可用/撤销失败一律 fail-closed。
                superuser = superuserReconciler
                    ?: runCatching { createProductionSuperuserReconciler(appContext, authorizationControl) }
                        .getOrElse { SuperuserReconciler { HookResult.failed("SUPERUSER_BACKEND_UNAVAILABLE", it.name) } },
                // H5：KSU 内核 stealth + 按模式持久化；构造失败 → null（跳过隐藏守卫，不伪造状态）。
                hidden = hiddenCoordinator ?: createProductionHiddenCoordinator(appContext),
                // P0：Ultra 环境互操作（只读探针；暂停/恢复未接线时动作显式失败 → fail-closed）。
                ultra = ultraCoordinator ?: createProductionUltraEnvCoordinator(appContext),
                rootRunner = { script ->
                    val result = durableRoot(script)
                    RootScriptResult(result.ok, result.code, result.output, result.detail)
                },
                dataPaths = pathsFor,
                acquireLease = pinnedRoot::acquire,
                releaseLease = pinnedRoot::release,
                ultraCredentialFingerprint = { id -> UltraInstallCredentials.fingerprint(appContext, id) },
                onInstallReceipt = { id, state, sha ->
                    dev.maplesu.manager.runtime.ultra.UltraPendingInstallCredentialStore(appContext).observe(id, state, sha)
                },
                lifecycle = RootModeSwitchEnvironmentLifecycle(
                    ready = {
                        pinnedRoot.ensureReady().also {
                            if (it.ok) frozenPaths = AndroidModeSwitchDataPaths(appContext)
                        }
                    },
                    runner = { script ->
                        val result = durableRoot(script)
                        RootScriptResult(result.ok, result.code, result.output, result.detail)
                    },
                    dynamicPaths = pathsFor,
                    uninstallUltra = {
                        val key = UltraRootKeyStore.read(appContext)
                        if (key.isNullOrBlank()) HookResult.failed("ULTRA_ENV_KEY_MISSING")
                        else pinnedRoot.uninstallUltra(key)
                    },
                ),
                normalBootUserland = {
                    val result = ensureNormalBootUserland(
                        File(appContext.applicationInfo.nativeLibraryDir),
                        KsuRootCommandRunner { command, _ ->
                            val executed = durableRoot(command)
                            KsuShellResult(if (executed.ok) executed.code else executed.code.takeUnless { it == 0 } ?: -1,
                                executed.output.lines(), listOf(executed.detail))
                        },
                        onStdout = { Log.i(TAG, "normal-boot: $it") },
                        onStderr = { Log.w(TAG, "normal-boot: $it") },
                    )
                    if (result.ok) dev.maplesu.manager.installer.InstallMoveOutcome.OK else
                        dev.maplesu.manager.installer.InstallMoveOutcome.failed(
                            result.failureCode ?: dev.maplesu.manager.ksu.KsuBootstrap.CODE_INSTALL_FAILED, result.detail)
                },
                defaultSigning = { request ->
                    dev.maplesu.manager.installer.LenovoModeSwitchSigning.open(appContext, request) {
                        Log.i(TAG, it)
                    }
                },
            )
            lastError = null
            configuredClientKind = if (client == null && runtimeClient is UltraRootModeSwitchClient)
                ModeSwitchClientKind.ULTRA_ROOT else null
            facade = built
            return built
        }
    }

    /**
     * W19 生产装配（幂等）：
     * 1) 安装 `LiteImplantRecorderProvider`（之后所有 su 投递/植入自动登记原件备份）；
     * 2) 构造 `LiteImplantQuiesceHooks` 供 S0-15/4a 使用。
     * 任一步失败返回 null（调用方保持 NoOp = fail-closed），绝不伪造"可恢复"。
     */
    fun buildW19Hooks(context: Context): SwitchQuiesceHooks? = runCatching {
        val shared = File(dev.maplesu.manager.workmode.control.ControlPaths.SHARED)
        dev.maplesu.manager.workmode.superuser.installLiteImplantRecorder(shared, context)
        dev.maplesu.manager.workmode.superuser.createLiteImplantQuiesceHooks(shared, context)
    }.getOrElse { error ->
        lastError = "W19 hooks unavailable: ${error.message}"
        null
    }

    fun facadeOrNull(): ModeSwitchFacade? = facade

    fun isInitialized(): Boolean = facade != null

    fun lastInitializationError(): String? = lastError

    /**
     * 默认装配的执行方（诊断/测试用；未初始化或显式注入 client 时返回 null）。
     * 此值只描述配置，不代表 root 已就绪或任务已被受理。
     */
    fun selectedClientKind(): ModeSwitchClientKind? = configuredClientKind

    /** Only the original SERVICE reader interprets these records; presence is not ownership. */
    private fun hasServiceHistory(runner: (String) -> RootExecResult): Boolean {
        val result = runner("""
            #maplesu:service-history
            if [ -e '${ControlPaths.switchFile()}' ] || [ -L '${ControlPaths.switchFile()}' ]; then
                printf 'present'
            else
                for path in '${ControlPaths.WORK}'/*/journal.jsonl; do
                    if [ -e "${'$'}path" ] || [ -L "${'$'}path" ]; then printf 'present'; exit 0; fi
                done
                printf 'absent'
            fi
        """.trimIndent())
        check(result.ok) { result.detail.ifBlank { "SWITCH_STATUS_READ_FAILED" } }
        return when (result.output.trim()) {
            "present" -> true
            "absent" -> false
            else -> error("SWITCH_STATUS_READ_FAILED")
        }
    }

    /**
     * 两个切换方向复用同一个客户端和持久任务目录，root 传输由既有会话负责。
     * 构造不探测 root；装配异常向 initialize 调用方传播，不在任务中途另选执行方。
     * 历史 SERVICE 的 control/switch.json 与 boot/work 记录不属于此客户端的 tx 格式。
     */
    private fun buildClient(
        context: Context,
        appWorkRoot: File,
        runner: (String) -> RootExecResult,
    ): UltraRootModeSwitchClient {
        val recovery = ModeSwitchSourceRecovery(
            assets = UltraKernelContextAssets(context),
            receiptsDirectory = dev.maplesu.manager.compat.UltraStateStorage.flash(context.filesDir),
            retainedOriginals = listOf(File(dev.maplesu.manager.compat.UltraStateStorage.flash(context.filesDir), "base-stock.img")),
            daemon = File(context.applicationInfo.nativeLibraryDir, "libksud_ksu.so").absolutePath,
            runner = KsuRootCommandRunner { command, _ ->
                val result = runner(command)
                KsuShellResult(result.code, result.output.lines(), listOf(result.detail))
            },
            logger = { line -> Log.i(TAG, line) },
            appUid = android.os.Process.myUid(),
        )
        return UltraRootModeSwitchClient(
            runner = runner,
            workRoot = File(ControlPaths.ROOT),
            logger = { line -> Log.i(TAG, line) },
            // 导出目标只允许落在 App 自己的切换工作目录（facade 传入的文件都在这里）。
            destinationRoots = listOf(appWorkRoot),
            baselineRoot = File(ControlPaths.ROOT, "boot/baseline"),
            sourceRecovery = recovery::recover,
        )
    }

    // ------------------------------------------------------------------ 生产 producer

    /** Ultra 候选生产者：首次 Normal 迁移准备持久身份，候选回读通过后保留相同身份。 */
    private fun buildLiteProducer(context: Context): SwitchCandidateProducer? = runCatching {
        SwitchCandidateProducer { input ->
            runCatching {
                val flow = UltraKernelFlow(context)
                val artifact = if (input.partition == "init_boot")
                    requireNotNull(input.initBootArtifact ?: UltraKernelFlow.initBootArtifact(context, input.kmi)) {
                        "ULTRA_INIT_KMI_COMPONENT_MISSING:${input.kmi}"
                    } else null
                if (artifact != null) dev.maplesu.manager.installer.UltraInitBootInstallTarget.requireCompatible(
                    artifact, input.kernelRelease, input.targetBootSha256)
                val prepared = flow.prepareTarget(input.baselineImage,
                    UltraInstallCredentials.forCandidate(context), null, artifact, input.deploymentNamespace) { Log.i(TAG, it) }
                val built = flow.candidateForInstall(input.baselineImage, prepared)
                check(built.partition == input.partition) { "ULTRA_KERNEL_CANDIDATE_PARTITION_MISMATCH" }
                val image = fullSizeSwitchCandidate(built.image, File(input.workDir, "ultra-candidate.img"),
                    input.baselineImage.length())
                val sha256 = ModeSwitchHashes.sha256(image)
                UltraInstallCredentials.retainCandidate(context, image, sha256, prepared.credential, input.switchId)
                CandidateResult(image, sha256, image.length(),
                    structureEvidence = "ultra-kernel:${built.kernelRelease}")
            }.getOrElse { error ->
                val code = error.message.orEmpty().substringBefore(':').trim()
                    .takeIf { it.matches(Regex("(?:ULTRA_KERNEL|ULTRA_INIT)_[A-Z0-9_]+")) } ?: "PRODUCE_FAILED"
                val detail = dev.maplesu.manager.runtime.ultra.UltraSdkLogging.redact(
                    error.message.orEmpty().ifBlank { error.javaClass.simpleName })
                Log.w(TAG, "Ultra candidate failed: $code $detail")
                CandidateResult(null, "", 0, code, detail)
            }
        }
    }.getOrNull()

    /** W12 KSU 生产者：libksud_ksu.so / root shell 任一不可用 → 明确错误码。 */
    private fun defaultKsuBridge(
        context: Context,
        execute: (String) -> RootExecResult,
    ): KsuSwitchProducerBridge =
        KsuSwitchProducerBridge { input ->
            runCatching {
                val daemon = File(context.applicationInfo.nativeLibraryDir, "libksud_ksu.so")
                if (!daemon.isFile) {
                    return@runCatching dev.maplesu.manager.workmode.switch.CandidateResult(
                        null, "", 0, "PRODUCE_KSU_UNAVAILABLE", "libksud_ksu.so missing",
                    )
                }
                val runner = KsuRootCommandRunner { command, _ ->
                    val result = execute(command)
                    KsuShellResult(result.code, result.output.lines(), listOf(result.detail))
                }
                // 多 KMI：先问 ksud 本次 APK 到底内嵌了哪些 `kernelsu.ko`
                // （`boot-info supported-kmis` = 资产名去掉 `_kernelsu.ko` 后缀）。
                // 拿不到就保持 null（目录未知）：producer 不猜，ksud 按 --kmi 查资产，缺了硬失败。
                val kmiCatalog: Set<String>? = runCatching {
                    val command = dev.maplesu.manager.ksu.KsuCommands.bootInfo(
                        daemon.absolutePath,
                        dev.maplesu.manager.ksu.KsuBootInfoSubcommand.SUPPORTED_KMIS,
                    )
                    val result = runner.run(
                        dev.maplesu.manager.ksu.KsuInstallProducer.shellJoin(command),
                        dev.maplesu.manager.ksu.KsuTimeouts.COMMAND_MS,
                    )
                    if (result.code == 0) {
                        result.out.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                    } else {
                        null
                    }
                }.getOrNull()
                val producer = KsuInstallProducer(
                    daemonPath = daemon.absolutePath,
                    runner = runner,
                    workRoot = dev.maplesu.manager.ksu.KsuCandidateWorkspaces.switch(input.workDir),
                    commit = KsuCandidateCommitPort { _, _ ->
                        KsuCommitResult.NoTransaction("PRODUCE_ONLY", "facade submits candidate")
                    },
                    switchSession = { null },
                    kmiCatalog = { kmiCatalog },
                    appUid = android.os.Process.myUid(),
                )
                when (val result = producer.produceCandidate(
                    source = input.baselineImage,
                    options = dev.maplesu.manager.ksu.KsuBootPatchOptions(
                        kmi = input.kmi.ifBlank { null },
                        partition = input.partition,
                    ),
                    onCommand = { receipt ->
                        dev.maplesu.manager.ksu.KsuLog.record("switch-boot-patch", dev.maplesu.manager.runtime.ultra.UltraSdkLogging.redact(
                            "argv=${receipt.argv} code=${receipt.result.code} stdout=${receipt.result.outText} stderr=${receipt.result.errText}"))
                    },
                )) {
                    is KsuProduceResult.Produced -> {
                        val image = fullSizeSwitchCandidate(result.candidate.candidate,
                            File(input.workDir, "normal-candidate.img"), input.baselineImage.length())
                        dev.maplesu.manager.workmode.switch.CandidateResult(
                            image = image, sha256 = ModeSwitchHashes.sha256(image), size = image.length(),
                            structureEvidence = "ksu:${result.candidate.form}",
                        )
                    }
                    is KsuProduceResult.Rejected -> dev.maplesu.manager.workmode.switch.CandidateResult(
                        null, "", 0, result.code, result.detail,
                    )
                }
            }.getOrElse { error ->
                dev.maplesu.manager.workmode.switch.CandidateResult(
                    null, "", 0, "PRODUCE_KSU_UNAVAILABLE", error.message.orEmpty(),
                )
            }
        }

    // ------------------------------------------------------------------ 快照

    /**
     * W02 快照的 App 侧装配：App 私有数据写到 App 私有 storeRoot（无需 root），
     * `AppSnapshotFacts` 读不到时 FileSnapshotService 返回 Skipped，切换按 fail-closed 阻断。
     */
    private fun defaultSnapshots(context: Context): ModeSwitchSnapshots {
        val appDir = context.filesDir
        val storeRoot = File(context.filesDir, "workmode/snapshots")
        val service: SnapshotService = FileSnapshotService(
            appDir = appDir,
            storeRoot = storeRoot,
            rootAvailable = { true },
            appFactsProvider = { appSnapshotFacts(context) },
        )
        return SnapshotServiceModeSwitchSnapshots(service)
    }

    private fun appSnapshotFacts(context: Context): AppSnapshotFacts? = runCatching {
        val packageName = context.packageName
        val info = context.packageManager.getPackageInfo(
            packageName, PackageManager.GET_SIGNING_CERTIFICATES,
        )
        val signers = info.signingInfo?.apkContentsSigners.orEmpty().map { signature ->
            dev.maplesu.manager.workmode.Hashes.sha256(signature.toByteArray())
        }
        val apk = File(context.applicationInfo.sourceDir)
        AppSnapshotFacts(
            packageName = packageName,
            versionCode = info.longVersionCode,
            apkSha256 = dev.maplesu.manager.workmode.Hashes.sha256(apk.readBytes()),
            signerSha256 = signers,
            settingsRevision = 0,
            appPrefsRevision = 0,
        )
    }.getOrNull()
}

/**
 * 生产 SELinux 恢复器装配（P0-1 第 1 项）。
 *
 * **为什么住在这里而不是 `SelinuxRestorer.kt`**：Normal 通道要构造
 * `dev.maplesu.manager.ksu.LibsuRootCommandRunner`，而 `src/main/java` 里只允许
 * `KsuSourceIsolationTest.onlyFacadesMayImportKsuBackend` 白名单内的文件 import
 * `dev.maplesu.manager.ksu.*`；本文件正是白名单列出的"切换事务运行时的合法消费者"。
 * `SelinuxRestorer.kt` 因此只保留与 KSU 无关的抽象/实现/超时看护。
 *
 * 通道语义（与搬迁前完全一致）：
 * - Ultra：`RootExec.execute(script)`（Application 启动期已 install router）；
 * - Normal：libsu root shell（`LibsuRootCommandRunner`）。`libksud_ksu.so` 路径装配与
 *   [defaultKsuBridge] 一致；该 so 缺失时 runner 仍会退化到系统 `su -mm`/`su`
 *   （三个候选，`KsuInstallGlue.kt:208-212`）；
 * - 回读/liveness 探针与 `[AndroidSelinuxRestorer]` 的既有实现不变（回读走同一根通道，
 *   活性探针 `id -u` 也走同一根通道）。
 *
 * 只构造对象，**不在这里执行任何根命令**（真正执行在 `ModeSwitchFacade.startSwitch` 的
 * IO 线程里，且受 [runWithTimeout] 的 15s 硬上限看护）。
 */
fun productionSelinuxRestorer(context: Context): SelinuxRestorer {
    val app = context.applicationContext
    val daemonPath = File(app.applicationInfo.nativeLibraryDir, "libksud_ksu.so").absolutePath
    return AndroidSelinuxRestorer(
        ultra = SelinuxRootChannel { script ->
            runWithTimeout(NORMAL_TIMEOUT_MS) {
                val result = RootExec.execute(script)
                SelinuxShellResult(result.ok, result.output, result.code, result.detail)
            }
        },
        normal = SelinuxRootChannel { script ->
            runWithTimeout(NORMAL_TIMEOUT_MS) {
                val result = LibsuRootCommandRunner(daemonPath).run(script, NORMAL_TIMEOUT_MS)
                SelinuxShellResult(result.code == 0, result.outText, result.code, result.errText)
            }
        },
        readLocal = { readSelinuxStatusLocal() },
    )
}
