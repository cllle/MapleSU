package dev.maplesu.manager.workmode.service

import dev.maplesu.manager.installer.BaselineCaptureCodes
import dev.maplesu.manager.installer.BaselineProvisionOutcome
import dev.maplesu.manager.installer.InstallCandidateImage
import dev.maplesu.manager.installer.InstallCommitCodes
import dev.maplesu.manager.installer.InstallCommitCoordinator
import dev.maplesu.manager.installer.InstallCommitOutcome
import dev.maplesu.manager.installer.InstallConfigMover
import dev.maplesu.manager.installer.InstallMoveOutcome
import dev.maplesu.manager.installer.InstallMovePhase
import dev.maplesu.manager.installer.RootScriptInstallConfigMover
import dev.maplesu.manager.installer.RootScriptResult
import dev.maplesu.manager.runtime.RootExec
import dev.maplesu.manager.ultrakernel.UltraKernelCatalog
import dev.maplesu.manager.patching.CandidateMode
import dev.maplesu.manager.patching.CandidateOutcome
import dev.maplesu.manager.patching.CandidateProducer
import dev.maplesu.manager.patching.CandidateRequest
import dev.maplesu.manager.workmode.WorkMode
import dev.maplesu.manager.workmode.detect.BootIdSource
import dev.maplesu.manager.workmode.detect.Clock
import dev.maplesu.manager.workmode.detect.ProcBootIdSource
import dev.maplesu.manager.workmode.detect.WallClock
import dev.maplesu.manager.workmode.switch.BatteryFacts
import dev.maplesu.manager.workmode.switch.BaselineErrors
import dev.maplesu.manager.workmode.switch.BeginInputs
import dev.maplesu.manager.workmode.switch.CandidateInput
import dev.maplesu.manager.workmode.switch.CandidateResult
import dev.maplesu.manager.workmode.switch.HookResult
import dev.maplesu.manager.workmode.switch.ModeCommitSink
import dev.maplesu.manager.workmode.switch.ModeConsumerVerifier
import dev.maplesu.manager.workmode.switch.ModeSwitchCodes
import dev.maplesu.manager.workmode.switch.ModeSwitchHashes
import dev.maplesu.manager.workmode.switch.HiddenSwitchCodes
import dev.maplesu.manager.workmode.switch.UltraEnvSwitchCoordinator
import dev.maplesu.manager.workmode.switch.UltraSwitchCodes
import dev.maplesu.manager.workmode.switch.UltraSwitchResult
import dev.maplesu.manager.workmode.switch.HiddenSwitchCoordinator
import dev.maplesu.manager.workmode.switch.HiddenSwitchDecision
import dev.maplesu.manager.workmode.switch.HiddenSwitchResult
import dev.maplesu.manager.workmode.switch.ModeSwitchKmi
import dev.maplesu.manager.workmode.switch.ModeSwitchPreflight
import dev.maplesu.manager.workmode.switch.ModeSwitchRequest
import dev.maplesu.manager.workmode.switch.ModeSwitchSnapshots
import dev.maplesu.manager.workmode.switch.NoOpSwitchQuiesceHooks
import dev.maplesu.manager.workmode.switch.SuperuserReconciler
import dev.maplesu.manager.workmode.switch.SwitchCandidateProducer
import dev.maplesu.manager.workmode.switch.SwitchQuiesceHooks
import dev.maplesu.manager.workmode.storage.MoveEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex

/** `canSwitch` 的结构化拒绝原因（UI 直接映射文案）。 */
data class SwitchBlocker(val code: String, val detail: String)

sealed interface SwitchEligibility {
    /** `probeSwitchId` 保留旧调用形状，仅是本地标识，不代表服务端预检凭据。 */
    data class Allowed(
        val probeSwitchId: String,
        val target: WorkMode,
        /** H5：源模式隐藏 ON → UI 必须二次确认后才能 startSwitch(confirmHidden=true)。 */
        val requiresHiddenConfirmation: Boolean = false,
        /** 兼容既有 UI 的提示字段；入口不再依赖预检及错误降级。 */
        val repairNotes: List<SwitchBlocker> = emptyList(),
    ) : SwitchEligibility

    data class Blocked(
        val code: String,
        val detail: String,
        val reasons: List<SwitchBlocker>,
    ) : SwitchEligibility
}

sealed interface SwitchOutcome {
    data class ImagesRequired(val target: WorkMode, val restoreInitBoot: Boolean) : SwitchOutcome {
        // On devices without init_boot, Normal's loader is inside boot itself.
        val requiresBoot: Boolean get() = target == WorkMode.KSU || !restoreInitBoot
    }
    data class AwaitingReboot(val switchId: String, val status: ClientStatus?,
                             val candidate: InstallCandidateImage? = null) : SwitchOutcome
    data class Verified(val switchId: String, val status: ClientStatus?) : SwitchOutcome

    /**
     * 分区验证已过，但 S6.4 超级用户 reconcile 未成功（或目标 root 通道不可得）。
     * 不是成功：UI 必须展示 code（`VERIFY_SUPERUSER_FAILED`/`SUPERUSER_REVOKE_FAILED`/
     * `SUPERUSER_BACKEND_UNAVAILABLE`），不得当作 Verified（发布红线 8 / fail-closed）。
     * 与 Failed 区分：分区已 key 落地，无需人工恢复分区，允许重试 reconcile。
     */
    data class Incomplete(
        val switchId: String,
        val code: String,
        val detail: String,
        val status: ClientStatus?,
    ) : SwitchOutcome

    /**
     * 失败/未完成。S6.4 超级用户 reconcile 未成功时也走这里（不是成功）：
     * code ∈ {`VERIFY_SUPERUSER_FAILED`,`SUPERUSER_REVOKE_FAILED`,`SUPERUSER_BACKEND_UNAVAILABLE`}，
     * UI 必须展示 code，不得当作 Verified（发布红线 8 / fail-closed）。
     */
    data class Failed(
        val switchId: String?,
        val code: String,
        val detail: String,
        val status: ClientStatus?,
    ) : SwitchOutcome
    data class Blocked(val code: String, val detail: String, val reasons: List<SwitchBlocker>) : SwitchOutcome
}

/** One execution's AVB finalizer; companion files stay alive through commit and compensation. */
interface ModeSwitchSigningContext : AutoCloseable {
    val mode: String
    fun finish(candidate: InstallCandidateImage, source: File): InstallCandidateImage
}

/**
 * App 侧切换门面（UI 唯一入口；生产装配见 [ModeSwitchRuntime]）。
 *
 * 驱动顺序与 02 §5 的 AIDL 拆分一致：
 * `begin → snapshot → export BASE → producer 产候选 → submitCandidate →
 * commit → markRebootRequested → (重启后) verify → 轮询 getModeSwitch 收敛`。
 * 分区写入只发生在 root 服务内（红线 4）；本类不写任何块设备文件。
 */
class ModeSwitchFacade(
    private val client: ModeSwitchClient,
    private val environment: ModeSwitchAppEnvironment,
    private val producers: Map<WorkMode, SwitchCandidateProducer>,
    private val workRoot: File,
    private val snapshots: ModeSwitchSnapshots = UnavailableModeSwitchSnapshots,
    private val hooks: SwitchQuiesceHooks = NoOpSwitchQuiesceHooks,
    private val modeCommit: ModeCommitSink? = null,
    private val consumer: ModeConsumerVerifier = ModeConsumerVerifier { _, _ -> HookResult.OK },
    private val superuser: SuperuserReconciler = SuperuserReconciler { HookResult.OK },
    /** H5 隐藏状态守卫；null = 隐藏能力未接线（旧行为，不做隐藏阻断）。 */
    private val hidden: HiddenSwitchCoordinator? = null,
    /** P0 Ultra 环境互操作守卫；null = Ultra 能力未接线（旧行为，不做 Ultra 阻断）。 */
    private val ultra: UltraEnvSwitchCoordinator? = null,
    private val clock: Clock = WallClock,
    private val bootIds: BootIdSource = ProcBootIdSource,
    private val pollDelay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
    private val maxVerifyPolls: Int = 10,
    private val lifecycle: ModeSwitchEnvironmentLifecycle? = null,
    private val rootRunner: (String) -> RootScriptResult = { script ->
        val result = RootExec.execute(script)
        RootScriptResult(result.ok, result.code, result.output)
    },
    private val dataPaths: (WorkMode) -> List<MoveEntry> = { emptyList() },
    private val moverFactory: ((File, WorkMode, WorkMode) -> InstallConfigMover)? = null,
    private val acquireLease: (String) -> HookResult = { HookResult.OK },
    private val releaseLease: (String) -> HookResult = { HookResult.OK },
    private val ultraCredentialFingerprint: ((String) -> String?)? = null,
    private val onInstallReceipt: (String, String, String?) -> Unit = { _, _, _ -> },
    private val prepareCompatibility: ((WorkMode, InstallCandidateImage, Map<String, File>, String) -> InstallCandidateImage)? = null,
    private val normalBootUserland: () -> InstallMoveOutcome = { InstallMoveOutcome.OK },
    private val defaultSigning: ((ModeSwitchRequest) -> ModeSwitchSigningContext?)? = null,
    private val prepareCandidateInput: suspend (CandidateInput, ClientStatus) -> CandidateInput = { input, _ -> input },
) {
    private val switching = Mutex()
    private var activeRequest: ModeSwitchRequest? = null
    private var sourceQuiesced = false
    private var sourceWithdrawn = false
    private var activeMover: InstallConfigMover? = null
    private var activeProgress: ModeSwitchProgress? = null
    private val attemptedMoves = linkedSetOf<InstallMovePhase>()
    private var configRecoveryFailed = false
    private var commitAttempted = false
    private var partitionRecoveryFailed = false
    private var environmentRecoveryFailed = false
    private var operationUncertain = false
    private var leasedSwitchId: String? = null
    private var progressComplete = false
    private var ultraBoot: ModeSwitchProgress.UltraBoot? = null

    private fun checkpoint(waiting: Boolean = false) {
        activeRequest?.let { activeProgress?.write(it, attemptedMoves, sourceWithdrawn, waiting, sourceQuiesced,
            ultraBoot.takeIf { waiting }) }
    }

    private fun prepareUltraBoot(request: ModeSwitchRequest, image: File, sha256: String,
                                 directory: File): HookResult {
        if (request.fromMode != WorkMode.KSU || request.toMode != WorkMode.LITE) return HookResult.OK
        val fingerprintProvider = ultraCredentialFingerprint ?: return HookResult.OK
        val fingerprint = runCatching { fingerprintProvider(request.switchId) }.getOrNull()
        val sourceBoot = environment.bootId()
        if (!ModeSwitchHashes.isHash(fingerprint.orEmpty()) || sourceBoot.isBlank() ||
            sourceBoot.length > 64 || sourceBoot.any(Char::isWhitespace) ||
            !ModeSwitchHashes.isHash(sha256) || Files.isSymbolicLink(image.toPath()) ||
            image.canonicalFile.parentFile != directory.canonicalFile) {
            return HookResult.failed("ULTRA_KERNEL_INSTALL_IDENTITY_MISSING")
        }
        ultraBoot = ModeSwitchProgress.UltraBoot(sourceBoot, requireNotNull(fingerprint), sha256, image.name)
        return HookResult.OK
    }

    private fun clearOperation() {
        activeRequest = null
        sourceQuiesced = false
        sourceWithdrawn = false
        activeMover = null
        activeProgress = null
        attemptedMoves.clear()
        configRecoveryFailed = false
        commitAttempted = false
        partitionRecoveryFailed = false
        environmentRecoveryFailed = false
        operationUncertain = false
        leasedSwitchId = null
        progressComplete = false
        ultraBoot = null
    }

    private suspend fun <T> withSwitchOperation(
        busy: T,
        releaseFailure: (T, HookResult) -> T,
        recoverOnException: Boolean = false,
        operation: suspend () -> T,
    ): T {
        if (!switching.tryLock()) return busy
        var completed = false
        try {
            val outcome = operation()
            completed = true
            val released = withContext(NonCancellable + Dispatchers.IO) { releaseAfterRecovery() }
            return if (released.ok) outcome else releaseFailure(outcome, released)
        } finally {
            try {
                if (!completed) withContext(NonCancellable + Dispatchers.IO) {
                    if (recoverOnException && !operationUncertain) {
                        activeRequest?.let { request ->
                            if (commitAttempted) rollbackPartitions(request.switchId)
                            if (!partitionRecoveryFailed && !operationUncertain) rollbackConfig(
                                activeMover, request.switchId, request.fromMode, request.toMode,
                                attemptedMoves.toSet(),
                            )
                        }
                        restoreSource()
                    }
                    releaseAfterRecovery()
                }
            } finally {
                clearOperation()
                switching.unlock()
            }
        }
    }

    private fun releaseSwitchFailure(outcome: SwitchOutcome, failure: HookResult): SwitchOutcome {
        val id = leasedSwitchId ?: activeRequest?.switchId
        val status = id?.let { runCatching { client.status(it) }.getOrNull() } ?: observeStatus()
        return if (outcome is SwitchOutcome.Verified || outcome is SwitchOutcome.Incomplete) {
            SwitchOutcome.Incomplete(id ?: status?.switchId.orEmpty(), failure.code, failure.detail, status)
        } else SwitchOutcome.Failed(id ?: status?.switchId, failure.code, failure.detail, status)
    }

    private fun releaseClientFailure(outcome: ClientResult?, failure: HookResult): ClientResult =
        ClientResult(false, failure.code, failure.detail, outcome?.status ?: observeStatus())

    val supported: Boolean get() = environment.activeMode() != null
    val operationActive: Boolean get() = switching.isLocked

    /** 当前激活模式（UI 显示/目标选择用；未判定 null）。 */
    fun currentMode(): WorkMode? = environment.activeMode()

    /** Page entry is independent of producer/root readiness; execution resolves its inputs. */
    fun uiSwitchSupported(target: WorkMode): Boolean = environment.activeMode() != target

    fun observeStatus(): ClientStatus? = runCatching { client.status("")?.let(::projectRollback) }.getOrNull()

    /** Reopen only unfinished app-side completion for the current target mode. */
    fun hasPendingFinalization(status: ClientStatus): Boolean {
        if (status.state != "VERIFIED") return false
        val mode = environment.activeMode() ?: return false
        if (WorkMode.fromWire(status.toMode) != mode) return false
        val record = progressFor(status.switchId).read() ?: return false
        return record.waiting && record.request.switchId == status.switchId &&
            record.request.fromMode == WorkMode.fromWire(status.fromMode) &&
            record.request.toMode == mode
    }

    suspend fun statusAsync(): ClientStatus? = withContext(Dispatchers.IO) {
        runCatching { client.status("")?.let(::projectRollback) }.getOrNull()
    }

    suspend fun statusFor(switchId: String): ClientStatus? = withContext(Dispatchers.IO) {
        require(switchId.isNotBlank()) { InstallCommitCodes.SWITCH_ID_INVALID }
        val prepared = lifecycle?.prepare() ?: HookResult.OK
        check(prepared.ok) { prepared.code.ifBlank { "SWITCH_STATUS_READ_FAILED" } }
        val status = client.statusExact(switchId) ?: return@withContext null
        check(status.switchId == switchId) { "SWITCH_STATUS_TASK_MISMATCH" }
        projectRollback(status)
    }

    private fun progressFor(switchId: String) =
        ModeSwitchProgress(File(File(workRoot, switchId), "switch-progress.properties"))

    private fun projectRollback(status: ClientStatus): ClientStatus {
        if (status.state != "ROLLED_BACK") return status
        val receipt = progressFor(status.switchId).rollbackBoot() ?: return status
        if (!receipt.ready) return status.copy(state = "RECOVERY_REQUIRED", rebootRequired = false,
            canRollback = true, canResumeReboot = false, error = "SWITCH_PROGRESS_RECOVERY_REQUIRED")
        val currentBoot = environment.bootId()
        check(currentBoot.isNotBlank()) { "SWITCH_BOOT_ID_UNAVAILABLE" }
        val observed = receipt.bootId != currentBoot
        return status.copy(rebootRequired = !observed, bootObserved = observed,
            canRollback = false, canResumeReboot = !observed)
    }

    private fun verifyRollbackImages(status: ClientStatus, receipt: ModeSwitchProgress.RollbackBoot): Boolean {
        if (!receipt.ready || environment.bootId() != receipt.bootId ||
            environment.deviceFacts()?.slot != receipt.slot) return false
        val rows = status.partitions
        val allowed = if ((client as? LenovoSignedSwitchClient)?.isSignedTask(status.switchId) == true)
            setOf("boot", "init_boot", "vendor_boot", "recovery", "vbmeta_system", "vbmeta") else setOf("boot", "init_boot", "vendor_boot")
        if (rows.isEmpty() || rows.none { it.name == "boot" } || rows.map { it.name }.distinct().size != rows.size ||
            rows.any { it.name !in allowed || it.size <= 0 ||
                !ModeSwitchHashes.isHash(it.preSha256) }) return false
        fun q(value: String) = "'" + value.replace("'", "'\\''") + "'"
        val script = buildString {
            append("set -e\n[ \"\$(/system/bin/cat /proc/sys/kernel/random/boot_id)\" = ")
                .append(q(receipt.bootId)).append(" ] || exit 71\n")
            rows.forEach { row ->
                val device = q("/dev/block/by-name/${row.name}${receipt.slot}")
                append("[ -b $device ] || exit 72\n")
                append("[ \"\$(/system/bin/blockdev --getsize64 $device)\" = '${row.size}' ] || exit 73\n")
                append("h=\$(/system/bin/sha256sum $device) || exit 74\n")
                append("[ \"\${h%% *}\" = ").append(q(row.preSha256)).append(" ] || exit 74\n")
            }
            append("echo MODE_SWITCH_ROLLBACK_IMAGES_OK\n")
        }
        val result = rootRunner(script)
        if (result.output.contains(ModeSwitchRootProtocol.UNCERTAIN) ||
            result.error.contains(ModeSwitchRootProtocol.UNCERTAIN)) operationUncertain = true
        return !operationUncertain && result.ok && result.output.lineSequence().any { it == "MODE_SWITCH_ROLLBACK_IMAGES_OK" }
    }

    suspend fun reboot(switchId: String): Boolean = withSwitchOperation(
        busy = false, releaseFailure = { _, _ -> false },
    ) {
        withContext(Dispatchers.IO) {
            val prepared = lifecycle?.prepare() ?: HookResult.OK
            if (!prepared.ok) return@withContext false
            val lease = holdLease(switchId)
            if (!lease.ok) return@withContext false
            val status = client.statusExact(switchId)
            if (status?.switchId != switchId) return@withContext false
            onInstallReceipt(switchId, status.state,
                status.carrierCandidate()?.targetSha256)
            if (status.state == "ROLLED_BACK") {
                val receipt = progressFor(switchId).rollbackBoot() ?: return@withContext false
                if (!verifyRollbackImages(status, receipt)) return@withContext false
            } else {
                if (!status.rebootRequired || !client.markRebootRequested(switchId)) return@withContext false
                // All data must be durable before SDK removal and the helper's reboot request;
                // the same receipt lets a new Manager process finish verification after reboot.
                recoverDataStages(status)?.let { return@withContext false }
                val record = progressFor(switchId).read()
                val request = record?.request ?: ModeSwitchRequest(switchId,
                    WorkMode.fromWire(status.fromMode) ?: return@withContext false,
                    WorkMode.fromWire(status.toMode) ?: return@withContext false, "")
                val removed = (lifecycle?.complete(request) ?: HookResult.OK).trackUncertain()
                if (!removed.ok) return@withContext false
                if (removed.code == ModeSwitchRootProtocol.REBOOT_REQUESTED) return@withContext true
            }
            val result = rootRunner("/system/bin/svc power reboot")
            if (result.output.contains(ModeSwitchRootProtocol.UNCERTAIN) ||
                result.error.contains(ModeSwitchRootProtocol.UNCERTAIN)) operationUncertain = true
            result.ok
        }
    }

    // ------------------------------------------------------------------ canSwitch

    /** Navigation does not connect to the installer or acquire image/diagnostic facts. */
    suspend fun canSwitch(target: WorkMode): SwitchEligibility {
        val active = environment.activeMode()
        if (active == target) return SwitchEligibility.Blocked(
            ModeSwitchCodes.SAME_MODE, "already ${target.name}",
            listOf(SwitchBlocker(ModeSwitchCodes.SAME_MODE, "already ${target.name}")),
        )
        val hiddenDecision = if (active == null) null else runCatching {
            hidden?.evaluate(active, target)
        }.getOrNull()
        return SwitchEligibility.Allowed(
            UUID.randomUUID().toString(), target,
            (hiddenDecision as? HiddenSwitchDecision.Allowed)?.requiresConfirmation == true,
        )
    }

    private class LocalGate(
        val activeMode: WorkMode?,
        val requiresHiddenConfirmation: Boolean,
        val blocked: SwitchEligibility.Blocked?,
    )

    /** Actual mode and user confirmation are resolved after the execution channel is prepared. */
    private fun localEligibility(target: WorkMode): LocalGate {
        val active = environment.activeMode()
        val modeError = when (active) {
            null -> SwitchBlocker(ModeSwitchCodes.STATE_UNDETERMINED, "active mode unresolved")
            target -> SwitchBlocker(ModeSwitchCodes.SAME_MODE, "already ${target.name}")
            else -> null
        }
        if (modeError != null) return LocalGate(active, false, SwitchEligibility.Blocked(
            modeError.code, modeError.detail, listOf(modeError),
        ))
        val decision = hidden?.evaluate(requireNotNull(active), target)
        val blocker = when (decision) {
            is HiddenSwitchDecision.MustConfigureRecovery -> SwitchBlocker(decision.blocker.code, decision.blocker.detail)
            is HiddenSwitchDecision.MustUnhideFirst -> SwitchBlocker(decision.blocker.code, decision.blocker.detail)
            is HiddenSwitchDecision.Block -> SwitchBlocker(decision.blocker.code, decision.blocker.detail)
            else -> null
        }
        return LocalGate(active, (decision as? HiddenSwitchDecision.Allowed)?.requiresConfirmation == true,
            blocker?.let { SwitchEligibility.Blocked(it.code, it.detail, listOf(it)) })
    }

    // ------------------------------------------------------------------ startSwitch

    suspend fun startSwitch(
        target: WorkMode,
        /** H5：源模式隐藏 ON 时 UI 二次确认后传 true；否则返回 `HIDDEN_SWITCH_CONFIRMATION_REQUIRED`。 */
        confirmHidden: Boolean = false,
        onProgress: (String) -> Unit = {},
        requestedPartition: String? = null,
        requestedSwitchId: String? = null,
        images: ModeSwitchImages? = null,
        signingMode: String = "",
        finalizeCandidate: ((InstallCandidateImage, File) -> InstallCandidateImage)? = null,
        providedCandidate: InstallCandidateImage? = null,
        useDefaultSigning: Boolean = false,
        initBootArtifact: dev.maplesu.image.UltraInitBootProducer.Artifact? = null,
    ): SwitchOutcome {
        var signing: ModeSwitchSigningContext? = null
        return try {
            withSwitchOperation(
                busy = SwitchOutcome.Blocked(ModeSwitchCodes.PENDING, "switch already running", emptyList()),
                releaseFailure = ::releaseSwitchFailure,
                recoverOnException = true,
            ) {
                var outcome = performSwitch(target, confirmHidden, onProgress, requestedPartition, requestedSwitchId, images,
                    signingMode, finalizeCandidate, providedCandidate, useDefaultSigning, initBootArtifact) { signing = it }
                if (outcome !is SwitchOutcome.AwaitingReboot) {
                    val recovered = withContext(NonCancellable + Dispatchers.IO) { restoreSource() }
                    if (!recovered.ok) outcome = SwitchOutcome.Failed(
                        activeRequest?.switchId, recovered.code, recovered.detail, observeStatus(),
                    )
                }
                outcome
            }
        } finally {
            signing?.close()
        }
    }

    private fun restoreSource(): HookResult {
        val request = activeRequest ?: return HookResult.OK
        if (operationUncertain) return HookResult.failed(ModeSwitchRootProtocol.UNCERTAIN)
        if (partitionRecoveryFailed) return HookResult.failed(InstallCommitCodes.PARTITION_ROLLBACK_FAILED)
        if (configRecoveryFailed) return HookResult.failed(InstallCommitCodes.MOVE_ROLLBACK_FAILED)
        environmentRecoveryFailed = true
        if (sourceWithdrawn) {
            val restored = lifecycle?.restoreAfterFailure(request) ?: HookResult.OK
            if (!restored.ok) return restored
            sourceWithdrawn = false
            checkpoint()
        }
        if (sourceQuiesced) {
            val result = hooks.restoreAfterFailure(request.fromMode)
            if (!result.ok) return result
            sourceQuiesced = false
            checkpoint()
        }
        environmentRecoveryFailed = false
        activeProgress?.completeRollback()
        progressComplete = true
        return HookResult.OK
    }

    private fun holdLease(switchId: String, newTask: Boolean = false): HookResult {
        if (leasedSwitchId == switchId) return HookResult.OK
        val wrapper = client as? LenovoSignedSwitchClient
        val current = wrapper?.ordinary ?: client
        if (!newTask && leasedSwitchId == null && wrapper?.isSignedTask(switchId) != true &&
            current is ServiceResumeModeSwitchClient && current.isServiceTask(switchId)) return HookResult.OK
        val result = acquireLease(switchId)
        if (result.ok) leasedSwitchId = switchId
        if (result.code == ModeSwitchRootProtocol.UNCERTAIN) operationUncertain = true
        return result
    }

    private suspend fun releaseAfterRecovery(): HookResult {
        // A command with an unknown completion may still be mutating. All completed calls,
        // including failed recovery and a request awaiting reboot, release the physical lock.
        // Their progress journals remain available to an explicit recovery operation.
        if (operationUncertain) return HookResult.OK
        val switchId = leasedSwitchId ?: return HookResult.OK.also {
            if (progressComplete) activeProgress?.finish()
        }
        return runCatching { releaseLease(switchId) }.getOrElse {
            HookResult.failed("SWITCH_LEASE_RELEASE_FAILED", it.javaClass.simpleName)
        }.also {
            if (it.code == ModeSwitchRootProtocol.UNCERTAIN) operationUncertain = true
            if (it.ok) {
                leasedSwitchId = null
                if (progressComplete && !partitionRecoveryFailed && !configRecoveryFailed && !environmentRecoveryFailed) {
                    activeProgress?.finish()
                }
            }
        }
    }

    private fun withdrawSource(request: ModeSwitchRequest): HookResult {
        if (sourceWithdrawn) return HookResult.OK
        val lease = holdLease(request.switchId)
        if (!lease.ok) return lease
        sourceQuiesced = true
        checkpoint()
        val quiet = hooks.quiesce(request.fromMode).trackUncertain()
        if (!quiet.ok) return quiet
        if (request.fromMode == WorkMode.LITE) {
            val implants = hooks.withdrawLiteImplants().trackUncertain()
            if (!implants.ok) return implants
        }
        sourceWithdrawn = true
        checkpoint()
        return (lifecycle?.withdraw(request) ?: HookResult.OK).also {
            if (it.code == ModeSwitchRootProtocol.UNCERTAIN) operationUncertain = true
        }
    }

    private suspend fun performSwitch(
        target: WorkMode,
        confirmHidden: Boolean,
        onProgress: (String) -> Unit,
        requestedPartition: String?,
        requestedSwitchId: String?,
        images: ModeSwitchImages?,
        signingMode: String,
        finalizeCandidate: ((InstallCandidateImage, File) -> InstallCandidateImage)?,
        providedCandidate: InstallCandidateImage?,
        useDefaultSigning: Boolean,
        initBootArtifact: dev.maplesu.image.UltraInitBootProducer.Artifact?,
        onSigningPrepared: (ModeSwitchSigningContext) -> Unit,
    ): SwitchOutcome {
        val selectedPartition = requestedPartition ?: when {
            target == WorkMode.KSU && images?.boot != null && images.initBoot == null -> "boot"
            target == WorkMode.LITE && initBootArtifact != null && environment.hasInitBoot() -> "init_boot"
            else -> null
        }
        require(signingMode in setOf("", "CHAINED", "NON_CHAINED", "AOSP"))
        require(signingMode.isEmpty() || finalizeCandidate != null && when (requestedPartition) {
            "boot" -> signingMode in setOf("CHAINED", "AOSP")
            "init_boot" -> signingMode == "NON_CHAINED"
            else -> false
        })
        require(!useDefaultSigning || signingMode.isEmpty() && finalizeCandidate == null)
        if (requestedSwitchId != null && runCatching {
                UUID.fromString(requestedSwitchId).toString() == requestedSwitchId
            }.getOrDefault(false).not()) return SwitchOutcome.Failed(
            requestedSwitchId, InstallCommitCodes.SWITCH_ID_INVALID, "", null,
        )
        var partition = ModeSwitchPreflight.resolveTarget(target, selectedPartition)
            ?: return SwitchOutcome.Failed(null, ModeSwitchCodes.PLAN_INVALID, "target partition", null)
        val channel = withContext(Dispatchers.IO) { lifecycle?.prepare() ?: HookResult.OK }
        if (!channel.ok) return SwitchOutcome.Failed(null, channel.code, channel.detail, null)
        val local = localEligibility(target)
        local.blocked?.let { blocked ->
            return SwitchOutcome.Blocked(blocked.code, blocked.detail, blocked.reasons)
        }
        // H5 二次确认同属"用户侧资格"：不确认就不该做任何系统状态变更。
        if (local.requiresHiddenConfirmation && !confirmHidden) {
            return SwitchOutcome.Blocked(
                HiddenSwitchCodes.HIDDEN_SWITCH_CONFIRMATION_REQUIRED,
                "hidden is ON; user confirmation required",
                listOf(SwitchBlocker(HiddenSwitchCodes.HIDDEN_SWITCH_CONFIRMATION_REQUIRED, "hidden ON")),
            )
        }
        partition = resolvePartition(target, selectedPartition)
            ?: return SwitchOutcome.Failed(null, ModeSwitchCodes.PLAN_INVALID, "target partition", null)
        require(providedCandidate == null || providedCandidate.partition == partition)
        // Preserve the requested execution-time SELinux restoration; page refresh never performs it.
        val restorer = withContext(Dispatchers.IO) {
            runCatching { environment.selinuxRestorer() }.getOrNull()
        }
        if (restorer != null) {
            val repaired = withContext(Dispatchers.IO) {
                runCatching { restorer.ensureEnforcing() }.getOrElse { error ->
                    SelinuxRestoreResult(
                        status = SelinuxRestoreResult.UNKNOWN,
                        attempted = true,
                        detail = error.javaClass.simpleName,
                    )
                }
            }
            // M-2：`SelinuxRestoreResult.UNWIRED`（空 status）是"未接线"哨兵，不是"修好了"。
            // 规则：本机已 Enforcing → 不需要修，直接继续；本机非 Enforcing → 显式失败
            // （不能静默继续，也不能因为"接上了 UNWIRED"而让每次切换都硬失败）。
            if (repaired.status.isEmpty()) {
                val selinux = environment.deviceFacts()?.selinux.orEmpty()
                if (selinux != SelinuxRestoreResult.ENFORCING) {
                    return SwitchOutcome.Failed(
                        null,
                        ModeSwitchCodes.SELINUX_NOT_ENFORCING,
                        "selinux=${selinux.ifEmpty { "unknown" }}; restorer unwired",
                        null,
                    )
                }
            } else if (!repaired.enforcing) {
                // 失败才报既有错误码（不新增码）；此时一个字节都还没写。
                // detail 是 UI 直接展示的字符串（`WorkModeMiuix.kt:163` 用 failure.detail），
                // 所以带上真实状态 + 失败原因，便于用户排障。
                val status = repaired.status.ifEmpty { "unknown" }
                val detail = if (repaired.detail.isEmpty()) {
                    "selinux=$status"
                } else {
                    "selinux=$status; ${repaired.detail}"
                }
                return SwitchOutcome.Failed(
                    null,
                    ModeSwitchCodes.SELINUX_NOT_ENFORCING,
                    detail,
                    null,
                )
            }
            // 恢复成功后立刻用同一根通道做活性探针（setenforce 可能打断 Ultra 根通道）：
            // 失败 → SWITCH_ROOT_UNAVAILABLE，同样在任何写之前返回。
            if (repaired.attempted && !repaired.rootAlive) {
                return SwitchOutcome.Failed(
                    null,
                    ModeSwitchCodes.ROOT_UNAVAILABLE,
                    repaired.detail.ifEmpty { "root channel lost after setenforce" },
                    null,
                )
            }
        }
        // Only explicitly selected images are imported. The backend captures PRE and
        // reconstructs the installed carrier without requiring a complete stock baseline.
        val provisioner = withContext(Dispatchers.IO) {
            runCatching {
                if (images == null) null else environment.selectedImages(target, images)
            }.getOrNull()
        }
        if (images != null && provisioner == null) return SwitchOutcome.Failed(
            null, BaselineErrors.BASELINE_MISSING, "selected image importer unavailable", null,
        )
        var baselineId = BASELINE_LATEST
        if (provisioner != null) {
            val provisioned = withContext(Dispatchers.IO) {
                runCatching { provisioner.ensureBaseline() }.getOrElse { error ->
                    BaselineProvisionOutcome.Failed(
                        BaselineCaptureCodes.CAPTURE_WRITE_FAILED,
                        error.javaClass.simpleName,
                    )
                }
            }
            when (provisioned) {
                is BaselineProvisionOutcome.Failed -> return SwitchOutcome.Failed(
                    null, provisioned.code, provisioned.detail, null)
                is BaselineProvisionOutcome.Ready -> baselineId = provisioned.id
            }
        }
        val from = requireNotNull(local.activeMode)
        val switchId = requestedSwitchId ?: UUID.randomUUID().toString()
        // An explicit image import supplies its id; an empty id uses captured carrier inputs.
        val request = ModeSwitchRequest(switchId, from, target, baselineId, targetPartition = partition)
        activeRequest = request
        val directory = File(workRoot, switchId).apply {
            if (!isDirectory && !mkdirs() && !isDirectory) {
                return SwitchOutcome.Failed(switchId, ModeSwitchCodes.CONTROL_DIR_INVALID, "work dir", null)
            }
        }
        activeProgress = ModeSwitchProgress(File(directory, "switch-progress.properties"))
        checkpoint()
        // H5：切换事务开始前必须关掉源模式隐藏（并持久化源隐藏供切回恢复）。
        hidden?.let { guard ->
            val prepared = guard.beforeSwitch(from, target, confirmed = true)
            if (!prepared.ok) {
                return SwitchOutcome.Failed(switchId, prepared.code, prepared.detail, null)
            }
        }
        // Actual channel preparation and begin determine root access; a cached Ultra state is not a ticket.
        val lease = withContext(Dispatchers.IO) { holdLease(switchId, newTask = true) }
        if (!lease.ok) return SwitchOutcome.Failed(switchId, lease.code, lease.detail, null)
        onProgress("S1")
        val snapshot = runCatching { snapshots.preSwitchSnapshot(request) }.getOrElse {
            HookResult.failed(ModeSwitchCodes.SNAPSHOT_FAILED, it.message.orEmpty())
                .let { hook -> dev.maplesu.manager.workmode.switch.GatewayOutcome(false, "", hook.code, hook.detail) }
        }
        if (!snapshot.ok) return failAndAbort(switchId, snapshot.code, snapshot.detail)
        val inputs = environment.beginInputs()
        if (inputs.error.isNotEmpty() || inputs.superuserJson == null || inputs.settingsMirrorJson == null) {
            // No begin request was sent. A task ID without a root journal must not become
            // a pending/unknown transaction in the UI; the operation finally clears its progress.
            return SwitchOutcome.Blocked(
                ModeSwitchCodes.PREPARE_EXPORT_FAILED,
                inputs.error.ifEmpty { "switch mirrors unavailable" },
                emptyList(),
            )
        }
        // Resolve defaults only now: the real carrier and execution channel are established,
        // and the previous native receipt has not been replaced by this transaction's begin.
        val signing = if (useDefaultSigning) defaultSigning?.invoke(request)?.also(onSigningPrepared) else null
        val effectiveSigningMode = signing?.mode ?: signingMode
        require(signing == null || effectiveSigningMode.isNotEmpty()) { "LENOVO_AVB_PARTITION_INVALID" }
        require(effectiveSigningMode.isEmpty() || when (partition) {
            "boot" -> effectiveSigningMode in setOf("CHAINED", "AOSP")
            "init_boot" -> effectiveSigningMode == "NON_CHAINED"
            else -> false
        }) { "LENOVO_AVB_PARTITION_INVALID" }
        val begin = client.begin(
            switchId, planFields(from, target, baselineId, partition) +
                (if (effectiveSigningMode.isEmpty()) emptyMap() else mapOf("lenovoSigning" to effectiveSigningMode)),
            inputs.superuserJson, inputs.settingsMirrorJson,
        )
        if (!begin.ok) return failAndAbort(switchId, begin.code, begin.detail)

        // The first actual call selects the root backend; no preflight ticket is needed.
        val movesByBackend = runCatching { transactionMovesConfig() }.getOrNull()
            ?: return failAndAbort(switchId, InstallCommitCodes.MOVE_STAGE_UNWIRED,
                "cannot determine whether the backend moves config/modules inside commit")
        val configMovesByCaller = !movesByBackend
        val configMover = if (configMovesByCaller) buildConfigMover(directory, from, target, onProgress) else null
        if (configMovesByCaller && configMover == null) {
            return failAndAbort(switchId, InstallCommitCodes.MOVE_STAGE_UNWIRED,
                "backend does not move config/modules inside commit and no root mover is wired")
        }

        onProgress("S2")
        val snapshotCall = client.snapshot(switchId)
        if (!snapshotCall.ok) {
            val failure = failAndAbort(switchId, snapshotCall.code, snapshotCall.detail)
            if (images == null && snapshotCall.code == "SOURCE_RECOVERY_UNAVAILABLE" &&
                failure is SwitchOutcome.Failed && failure.code == snapshotCall.code &&
                failure.status?.state == "CANCELLED") {
                return SwitchOutcome.ImagesRequired(target,
                    restoreInitBoot = failure.status.partitions.any { it.name == "init_boot" } && from == WorkMode.KSU)
            }
            return failure
        }
        val status = client.status(switchId)
            ?: return failAndAbort(switchId, ModeSwitchCodes.SNAPSHOT_FAILED, "no status after snapshot")
        val row = status.partitions.firstOrNull { it.name == partition }
            ?: return failAndAbort(
                switchId, ModeSwitchCodes.LAYOUT_UNSUPPORTED, "partition $partition missing",
            )

        onProgress("S3")
        val baseImage = File(directory, "base-$partition.img")
        if (!client.exportImage(switchId, "BASE", partition, baseImage)) {
            return failAndAbortWithMoves(
                switchId, ModeSwitchCodes.PRE_IMAGE_INVALID, "base $partition export failed",
                configMover, from, target,
            )
        }
        if (baseImage.length() != row.size) {
            return failAndAbortWithMoves(
                switchId, ModeSwitchCodes.PRE_IMAGE_INVALID, "base $partition length",
                configMover, from, target,
            )
        }
        val baseSha = ModeSwitchHashes.sha256(baseImage)
        val targetBootSha = if (target == WorkMode.LITE && partition == "init_boot" &&
            initBootArtifact?.isKmiComponent == false) currentBootSha256() else null
        if (target == WorkMode.LITE && partition == "init_boot" && initBootArtifact != null) {
            dev.maplesu.manager.installer.UltraInitBootInstallTarget.requireCompatible(initBootArtifact,
                environment.deviceFacts()?.kernelRelease.orEmpty(), targetBootSha)
        } else if (providedCandidate != null && target == WorkMode.LITE && partition == "init_boot") {
            error("ULTRA_KERNEL_INIT_BOOT_ARTIFACT_REQUIRED")
        }
        val candidate = if (providedCandidate != null) CandidateResult(providedCandidate.image,
            providedCandidate.sha256, providedCandidate.size) else {
            val producer = producers[target] ?: return failAndAbortWithMoves(
                switchId, ModeSwitchCodes.PRODUCE_FAILED, "no producer for ${target.name}", configMover, from, target)
            val input = CandidateInput(
                switchId = switchId,
                toMode = target,
                partition = partition,
                baselineImage = baseImage,
                baselineSha256 = baseSha,
                workDir = directory,
                kmi = ModeSwitchKmi.parse(environment.deviceFacts()?.kernelRelease).orEmpty(),
                kernelRelease = environment.deviceFacts()?.kernelRelease.orEmpty(),
                initBootArtifact = initBootArtifact,
                targetBootSha256 = targetBootSha,
            )
            try { producer.produce(prepareCandidateInput(input, status)) } catch (error: Exception) {
                val code = error.message.orEmpty().substringBefore(':')
                    .takeIf { it.startsWith("SOURCE_RECOVERY_") || it.startsWith("ULTRA_") || it.startsWith("INSTALLER_") }
                    ?: ModeSwitchCodes.PRODUCE_FAILED
                CandidateResult(null, "", 0, code, error.message.orEmpty())
            }
        }
        if (!candidate.ok) {
            return failAndAbortWithMoves(
                switchId, candidate.code.ifEmpty { ModeSwitchCodes.PRODUCE_FAILED }, candidate.detail,
                configMover, from, target,
            )
        }
        val produced = candidate.image!!
        if (!ModeSwitchHashes.sha256(produced).equals(candidate.sha256, ignoreCase = true)) {
            return failAndAbortWithMoves(switchId, ModeSwitchCodes.PRODUCE_CANDIDATE_CHANGED, partition,
                configMover, from, target)
        }
        val compatible = try {
            val initial = InstallCandidateImage(produced, candidate.sha256, produced.length(), partition,
                providedCandidate?.compatibilityImages.orEmpty())
            if (prepareCompatibility == null) initial else {
                val sources = linkedMapOf(partition to baseImage)
                val patched = prepareCompatibility.invoke(target, initial, sources, switchId)
                val retained = File(directory, "compatibility-$partition.img")
                patched.image.copyTo(retained, overwrite = true)
                check(ModeSwitchHashes.sha256(retained) == patched.sha256) { "INSTALLER_IMAGE_CHANGED" }
                patched.copy(image = retained)
            }
        } catch (error: Exception) {
            return failAndAbortWithMoves(switchId, "INSTALLER_VIVO_PRODUCE_FAILED", error.message.orEmpty(),
                configMover, from, target)
        }
        val finalized = if (signing == null && finalizeCandidate == null) compatible else try {
            val signed = signing?.finish(compatible, baseImage) ?: requireNotNull(finalizeCandidate)(compatible, baseImage)
            check(signed.partition == partition) { "LENOVO_AVB_PARTITION_INVALID" }
            val output = File(directory, "signed-$partition.img")
            signed.image.copyTo(output, overwrite = true)
            check(output.length() == signed.size && ModeSwitchHashes.sha256(output) == signed.sha256) {
                "LENOVO_AVB_CANDIDATE_CHANGED"
            }
            signed.copy(image = output)
        } catch (error: Exception) {
            return failAndAbortWithMoves(switchId,
                error.message?.takeIf { it.matches(Regex("LENOVO_AVB_[A-Z0-9_]+")) }
                    ?: "LENOVO_AVB_FINALIZATION_FAILED", error.message.orEmpty(), configMover, from, target)
        }
        val image = finalized.image
        if (image.length() != row.size) {
            return failAndAbortWithMoves(
                switchId, ModeSwitchCodes.PRODUCE_OUTPUT_SIZE_INVALID, partition,
                configMover, from, target,
            )
        }
        val sha = ModeSwitchHashes.sha256(image)
        if (!sha.equals(finalized.sha256, ignoreCase = true)) {
            return failAndAbortWithMoves(
                switchId, ModeSwitchCodes.PRODUCE_CANDIDATE_CHANGED, partition,
                configMover, from, target,
            )
        }
        val bootIdentity = prepareUltraBoot(request, image, sha, directory)
        if (!bootIdentity.ok) {
            return failAndAbortWithMoves(switchId, bootIdentity.code, bootIdentity.detail,
                configMover, from, target)
        }
        for ((name, companion) in finalized.compatibilityImages) {
            if (!ModeSwitchHashes.sha256(companion.image).equals(companion.sha256, true)) {
                return failAndAbortWithMoves(switchId, ModeSwitchCodes.PRODUCE_CANDIDATE_CHANGED, name, configMover, from, target)
            }
            val attached = client.submitCandidate(switchId, name, companion.image, companion.sha256)
            if (!attached.ok) return failAndAbortWithMoves(switchId, attached.code, attached.detail, configMover, from, target)
        }
        val submit = client.submitCandidate(switchId, partition, image, sha)
        if (!submit.ok) {
            return failAndAbortWithMoves(
                switchId, submit.code, submit.detail, configMover, from, target,
            )
        }

        // All images are prepared and checked before the source environment is touched.
        val cleanup = hooks.restoreProducerSideEffects()
        if (!cleanup.ok) return failAndAbort(switchId, cleanup.code, cleanup.detail)
        val withdrawn = withContext(Dispatchers.IO) { withdrawSource(request) }
        if (withdrawn.code == ModeSwitchRootProtocol.UNCERTAIN) {
            operationUncertain = true
            return SwitchOutcome.Failed(switchId, withdrawn.code, withdrawn.detail, status)
        }
        if (!withdrawn.ok) return failAndAbort(switchId, withdrawn.code, withdrawn.detail)
        if (configMover != null) {
            onProgress("4b")
            attemptedMoves += InstallMovePhase.OUT
            checkpoint()
            val moveOut = runCatching { configMover.moveOut(switchId, from, target) }.getOrElse { error ->
                InstallMoveOutcome.failed(InstallCommitCodes.MOVE_OUT_FAILED, error.javaClass.simpleName)
            }.staged(InstallCommitCodes.MOVE_OUT_FAILED)
            if (!moveOut.ok) return failAndAbortWithMoves(
                switchId, moveOut.code, moveOut.detail, configMover, from, target,
            )
        }

        if (targetBootSha != null && currentBootSha256() != targetBootSha) {
            return failAndAbortWithMoves(switchId, "ULTRA_KERNEL_INIT_BOOT_TARGET_CHANGED", "target boot changed",
                configMover, from, target)
        }
        if (target == WorkMode.LITE && partition == "init_boot" && initBootArtifact?.isKmiComponent == true) {
            dev.maplesu.manager.installer.UltraInitBootInstallTarget.requireCompatible(initBootArtifact,
                environment.deviceFacts()?.kernelRelease.orEmpty())
        }
        onProgress("S4")
        // M1（切换链同款）：`SelectingModeSwitchClient` 允许 SERVICE→ULTRA 翻转，若 begin 前判成
        // SERVICE 而此刻已是 ULTRA，则 4b 没人做、Ultra 的 commit 也不搬 ⇒ "切换成功但配置还是旧的"。
        // 必须在**第一个写分区之前**零写盘显式失败。
        val movesNow = runCatching { transactionMovesConfig() }.getOrNull()
        if (movesNow == null || movesNow != movesByBackend) {
            return failAndAbortWithMoves(
                switchId, InstallCommitCodes.MOVE_STAGE_UNWIRED,
                "backend move capability changed/unknown before commit " +
                    "(before=$movesByBackend, now=$movesNow); no partition was written",
                configMover, from, target,
            )
        }
        commitAttempted = true
        val commit = client.commit(switchId)
        if (commit.code == ModeSwitchRootProtocol.UNCERTAIN) {
            operationUncertain = true
            return SwitchOutcome.Failed(switchId, commit.code, commit.detail, commit.status)
        }
        if (!commit.ok) {
            val committedStatus = commit.status ?: client.status(switchId)
            // 分区可能已被尝试写入：客户端内部已做逆序回滚；这里再撤配置/模块（4e 反向）。
            val rollback = if (committedStatus != null && committedStatus.state == "RECOVERY_REQUIRED") {
                null
            } else {
                rollbackPartitions(switchId)
            }
            if (operationUncertain) return SwitchOutcome.Failed(
                switchId, ModeSwitchRootProtocol.UNCERTAIN, rollback?.detail.orEmpty(), rollback?.status,
            )
            partitionRecoveryFailed = rollback?.ok != true && rollback?.status?.state != "ROLLED_BACK" && committedStatus?.state !in
                setOf("ROLLED_BACK", "FAILED_PRE_WRITE")
            if (partitionRecoveryFailed) return SwitchOutcome.Failed(
                switchId, InstallCommitCodes.PARTITION_ROLLBACK_FAILED, commit.detail,
                rollback?.status ?: committedStatus,
            )
            val config = rollbackConfig(
                configMover, switchId, from, target, setOf(InstallMovePhase.OUT),
            )
            val status2 = rollback?.status ?: client.status(switchId)
            val code = if (config.ok) {
                commit.code.ifEmpty { ModeSwitchCodes.WRITE_FAILED }
            } else {
                config.code.ifEmpty { InstallCommitCodes.MOVE_ROLLBACK_FAILED }
            }
            return SwitchOutcome.Failed(switchId, code, commit.detail, status2)
        }
        // P0-1 第 5 项：配置/模块入仓（4e）+ 预物化（4f）。失败 ⇒ 先回滚分区、再回滚配置。
        if (configMover != null) {
            onProgress("4e")
            attemptedMoves += InstallMovePhase.IN
            checkpoint()
            val moveIn = runCatching { configMover.moveIn(switchId, from, target) }.getOrElse { error ->
                InstallMoveOutcome.failed(InstallCommitCodes.MOVE_IN_FAILED, error.javaClass.simpleName)
            }.staged(InstallCommitCodes.MOVE_IN_FAILED)
            if (!moveIn.ok) {
                if (operationUncertain) return SwitchOutcome.Failed(switchId, moveIn.code, moveIn.detail, client.status(switchId))
                val rolledBack = rollbackPartitions(switchId)
                if (operationUncertain) return SwitchOutcome.Failed(switchId, ModeSwitchRootProtocol.UNCERTAIN, rolledBack.detail, rolledBack.status)
                if (partitionRecoveryFailed) return SwitchOutcome.Failed(
                    switchId, InstallCommitCodes.PARTITION_ROLLBACK_FAILED, moveIn.detail, client.status(switchId),
                )
                val config = rollbackConfig(
                    configMover, switchId, from, target,
                    setOf(InstallMovePhase.OUT, InstallMovePhase.IN),
                )
                return SwitchOutcome.Failed(
                    switchId,
                    moveIn.code,
                    joinDetails(moveIn.detail, config.detail),
                    client.status(switchId),
                )
            }
        }
        run {
            if (configMover != null) onProgress("4f")
            val materialized = runCatching {
                val moved = configMover?.materialize(switchId, from, target) ?: InstallMoveOutcome.OK
                if (moved.ok && target == WorkMode.KSU) normalBootUserland() else moved
            }
                .getOrElse { error ->
                    InstallMoveOutcome.failed(
                        InstallCommitCodes.MATERIALIZE_FAILED, error.javaClass.simpleName,
                    )
                }.staged(InstallCommitCodes.MATERIALIZE_FAILED)
            if (!materialized.ok) {
                if (operationUncertain) return SwitchOutcome.Failed(switchId, materialized.code, materialized.detail, client.status(switchId))
                val rolledBack = rollbackPartitions(switchId)
                if (operationUncertain) return SwitchOutcome.Failed(switchId, ModeSwitchRootProtocol.UNCERTAIN, rolledBack.detail, rolledBack.status)
                if (partitionRecoveryFailed) return SwitchOutcome.Failed(
                    switchId, InstallCommitCodes.PARTITION_ROLLBACK_FAILED, materialized.detail, client.status(switchId),
                )
                val config = rollbackConfig(
                    configMover, switchId, from, target,
                    setOf(InstallMovePhase.OUT, InstallMovePhase.IN),
                )
                return SwitchOutcome.Failed(
                    switchId,
                    materialized.code,
                    joinDetails(materialized.detail, config.detail),
                    client.status(switchId),
                )
            }
        }
        checkpoint(waiting = true)
        onInstallReceipt(switchId, "REBOOT_REQUIRED", sha)
        runCatching { client.markRebootRequested(switchId) }
        onProgress("REBOOT_REQUIRED")
        return SwitchOutcome.AwaitingReboot(switchId, client.status(switchId) ?: commit.status,
            InstallCandidateImage(image, sha, image.length(), partition, finalized.compatibilityImages))
    }

    // ------------------------------------------------------------------ 安装提交（P0-1 第 4 项）

    /**
     * 把安装页**已经产出并校验过**的候选写进共用切换事务，并等它真正落盘（P0-1 第 4 项）。
     *
     * 与 [startSwitch] 的关系：同一条事务、同一套失败语义；区别只有"候选从哪来"——
     * `startSwitch` 自己用 `producers` 产候选，而本方法**接收**调用方（`KsuInstallProducer`）
     * 已经用用户所选镜像产出的候选，**不重新产出、不改变字节与摘要**；Normal→Ultra 会按字节暂存到事务目录。
     *
     * 顺序（每一步失败即返回；只有最后一步能算成功）：
     * `S0b preflight（只读）` → `S1 begin` → `S2 snapshot` → `4b 配置出仓` →
     * `S3 submitCandidate` → `S4 commit（逐分区回读 + 失败逆序回滚）` → `回读确认` →
     * `4e 入仓` → `4f 物化` → `4g 重启标记`。
     *
     * snapshot retains PRE and reconstructs only the installed source carrier. A complete
     * stock baseline is reserved for explicit restoration and is not an installation prerequisite.
     *
     * M4：与 [startSwitch] 对齐，事务开始前必须调 `hidden.beforeSwitch`（持久化源模式隐藏 +
     * 按策略关掉并回读）。安装路径是显式用户动作，故按"已确认"处理（同
     * `startSwitch(confirmHidden = true)`）。失败即 `Rejected`，仍然零写盘。
     *
     * @param candidate 安装页选中的生产者产物；Ultra 提交使用经摘要核对的同内容事务副本
     * @param requestedSwitchId 续跑用的事务 id（null = 新建 canonical UUID）
     */
    suspend fun installCommit(
        candidate: InstallCandidateImage,
        requestedSwitchId: String? = null,
        onProgress: (String) -> Unit = {},
        target: WorkMode = WorkMode.KSU,
        initBootArtifact: dev.maplesu.image.UltraInitBootProducer.Artifact? = null,
    ): InstallCommitOutcome = withSwitchOperation(
        busy = InstallCommitOutcome.Rejected(ModeSwitchCodes.PENDING),
        releaseFailure = { _, failure -> InstallCommitOutcome.Rejected(
            failure.code, failure.detail, activeRequest?.switchId.orEmpty(), requiresRecovery = true,
        ) },
        recoverOnException = true,
    ) {
            var outcome = performInstallCommit(candidate, requestedSwitchId, onProgress, target, initBootArtifact)
            if (outcome is InstallCommitOutcome.Rejected && outcome.code == ModeSwitchRootProtocol.UNCERTAIN) {
                operationUncertain = true
            }
            if (outcome is InstallCommitOutcome.AwaitingReboot) {
                checkpoint(waiting = true)
                onInstallReceipt(outcome.switchId, "REBOOT_REQUIRED", candidate.sha256)
            } else {
                if (outcome is InstallCommitOutcome.Rejected && outcome.requiresRecovery) {
                    return@withSwitchOperation outcome
                }
                val restored = withContext(NonCancellable + Dispatchers.IO) { restoreSource() }
                if (!restored.ok) outcome = InstallCommitOutcome.Rejected(
                    restored.code, restored.detail, activeRequest?.switchId.orEmpty(), requiresRecovery = true,
                )
            }
            outcome
    }

    private suspend fun performInstallCommit(
        candidate: InstallCandidateImage,
        requestedSwitchId: String?,
        onProgress: (String) -> Unit,
        target: WorkMode,
        initBootArtifact: dev.maplesu.image.UltraInitBootProducer.Artifact?,
    ): InstallCommitOutcome {
        val channel = withContext(Dispatchers.IO) { lifecycle?.prepare() ?: HookResult.OK }
        if (!channel.ok) return InstallCommitOutcome.Rejected(channel.code, channel.detail)
        val active = environment.activeMode()
            ?: return InstallCommitOutcome.Rejected(
                ModeSwitchCodes.STATE_UNDETERMINED, "active mode unresolved",
            )
        if (active == target) return InstallCommitOutcome.Rejected(
            InstallCommitCodes.SOURCE_MODE_INVALID, "source and target modes are identical",
        )
        val baselineId = BASELINE_LATEST
        val inputs = environment.beginInputs()
        val superuserJson = inputs.superuserJson
        val settingsMirrorJson = inputs.settingsMirrorJson
        if (inputs.error.isNotEmpty() || superuserJson == null || settingsMirrorJson == null) {
            return InstallCommitOutcome.Rejected(
                ModeSwitchCodes.PREPARE_EXPORT_FAILED,
                inputs.error.ifEmpty { "switch mirrors unavailable" },
            )
        }
        // M4：H5 前置（与 `startSwitch` 同一条：持久化源隐藏 → 按策略关闭并回读）。
        // 在**任何客户端调用之前**，失败仍然是零写盘。
        hidden?.let { guard ->
            val prepared = runCatching { guard.beforeSwitch(active, target, confirmed = true) }
                .getOrElse { error ->
                    HiddenSwitchResult.failed(
                        HiddenSwitchCodes.HIDDEN_UNHIDE_FAILED, error.javaClass.simpleName,
                    )
                }
            if (!prepared.ok) {
                return InstallCommitOutcome.Rejected(prepared.code, prepared.detail)
            }
        }
        val switchId = requestedSwitchId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val allowed = ModeSwitchPreflight.allowedTargets(target)
        if (candidate.partition !in allowed) return InstallCommitOutcome.Rejected(
            InstallCommitCodes.PARTITION_INVALID,
            "candidate targets '${candidate.partition}', but ${target.name} allows ${allowed.joinToString("/")}",
            switchId,
        )
        val directory = File(workRoot, switchId).apply { if (!isDirectory) mkdirs() }
        activeRequest = ModeSwitchRequest(switchId, active, target, baselineId,
            targetPartition = candidate.partition)
        activeProgress = ModeSwitchProgress(File(directory, "switch-progress.properties"))
        checkpoint()
        val committedCandidate = if (active == WorkMode.KSU && target == WorkMode.LITE) {
            // Downloaded candidates live outside switch-work; retain the exact submitted bytes
            // in this transaction so its post-commit receipt has the same provenance as startSwitch.
            val staged = runCatching {
                check(candidate.size in 1..UltraKernelCatalog.MAX_IMAGE_BYTES && candidate.image.isFile &&
                    candidate.image.length() == candidate.size)
                val image = File(directory, "ultra-candidate.img")
                if (candidate.image.canonicalFile != image.canonicalFile) {
                    candidate.image.inputStream().use { input ->
                        check(image.createNewFile())
                        FileOutputStream(image).use { output -> input.copyTo(output); output.fd.sync() }
                    }
                }
                check(image.length() == candidate.size &&
                    ModeSwitchHashes.sha256(image).equals(candidate.sha256, ignoreCase = true))
                candidate.copy(image = image)
            }.getOrElse { return InstallCommitOutcome.Rejected(
                InstallCommitCodes.CANDIDATE_CHANGED, it.javaClass.simpleName, switchId,
            ) }
            val identity = prepareUltraBoot(requireNotNull(activeRequest), staged.image, staged.sha256, directory)
            if (!identity.ok) return InstallCommitOutcome.Rejected(identity.code, identity.detail, switchId)
            staged
        } else candidate
        val lease = withContext(Dispatchers.IO) { holdLease(switchId, newTask = true) }
        if (!lease.ok) return InstallCommitOutcome.Rejected(lease.code, lease.detail, switchId)
        if (target == WorkMode.LITE && candidate.partition == "init_boot") {
            val artifact = requireNotNull(initBootArtifact) { "ULTRA_KERNEL_INIT_BOOT_ARTIFACT_REQUIRED" }
            dev.maplesu.manager.installer.UltraInitBootInstallTarget.requireCompatible(artifact,
                environment.deviceFacts()?.kernelRelease.orEmpty(),
                if (artifact.isKmiComponent) null else currentBootSha256())
        }
        val coordinator = InstallCommitCoordinator(
            client = client,
            // 延迟构造：只有在确认"后端不搬配置"之后才需要 root 脚本执行器。
            moverProvider = { buildConfigMover(directory, active, target, onProgress) },
            backendMovesConfig = { transactionMovesConfig() },
            newSwitchId = { switchId },
            log = onProgress,
            afterMaterialize = { mode -> if (mode == WorkMode.KSU) normalBootUserland() else InstallMoveOutcome.OK },
        )
        return coordinator.commit(
            candidate = committedCandidate,
            from = active,
            to = target,
            baselineId = baselineId,
            superuserJson = superuserJson,
            settingsMirrorJson = settingsMirrorJson,
            requestedSwitchId = switchId,
            onProgress = { stage ->
                if (stage == "S4") commitAttempted = true
                onProgress(stage)
            },
        )
    }

    /**
     * 当前事务后端是否**自己在 commit 内**完成 4b/4e/4f。
     *
     * 事实：服务端 AIDL 委托在 `commit` 里做 moveOut→分区→moveIn→materialize
     * （`ModeSwitchDeviceDelegate.kt:469-535`）；Ultra 根通道**完全不搬**
     * （`UltraRootModeSwitchClient` 不 import storage/MoveSet）。
     *
     * 判据在当前任务 begin 成功后求值：SelectingModeSwitchClient 此时已实际选择通道，
     * 签名路径按当前任务 owns 判定，不继承旧任务的 signedActive 状态。
     */
    private fun transactionMovesConfig(): Boolean {
        val wrapper = client as? LenovoSignedSwitchClient
        if (wrapper != null && activeRequest?.switchId?.let(wrapper::isSignedTask) == true) return false
        val current = wrapper?.ordinary ?: client
        if (current is ServiceResumeModeSwitchClient) return current.isServiceTask(
            checkNotNull(activeRequest).switchId)
        if (current is UltraRootModeSwitchClient) return false
        if (current is SelectingModeSwitchClient &&
            current.selectedKind() == ModeSwitchClientKind.ULTRA_ROOT
        ) {
            return false
        }
        return true
    }

    // ------------------------------------------------------------------ 重启后收敛

    /** 启动/手动进入时调用：有 pending 才 verify；结束返回 Verified/Failed/Nothing。 */
    suspend fun reopenAfterReboot(): SwitchOutcome = withSwitchOperation(
        busy = SwitchOutcome.Blocked(ModeSwitchCodes.PENDING, "switch running", emptyList()),
        releaseFailure = ::releaseSwitchFailure,
    ) {
        val status = withContext(Dispatchers.IO) { client.statusExact("")?.let(::projectRollback) }
            ?: return@withSwitchOperation SwitchOutcome.Failed(
            null, ModeSwitchCodes.PLAN_INVALID, "no switch record", null,
        )
        recoverDataStages(status)?.let { return@withSwitchOperation it }
        if (status.state == "VERIFIED") {
            if ((client as? LenovoSignedSwitchClient)?.isSignedTask(status.switchId) != true) {
                return@withSwitchOperation finishVerified(status.switchId, status)
            }
            if (hasPendingFinalization(status)) {
                val verified = client.verify(status.switchId)
                return@withSwitchOperation if (verified.ok) {
                    finishVerified(status.switchId, verified.status ?: status)
                } else {
                    SwitchOutcome.Incomplete(status.switchId, verified.code, verified.detail,
                        verified.status ?: status)
                }
            }
        }
        if (!status.pending) {
            return@withSwitchOperation SwitchOutcome.Failed(status.switchId, status.error, status.errorDetail, status)
        }
        converge(status.switchId)
    }

    /** Resume the external data stages before a root VERIFIED state can become UI success. */
    private suspend fun recoverDataStages(status: ClientStatus): SwitchOutcome? {
        val directory = File(workRoot, status.switchId)
        val progress = ModeSwitchProgress(File(directory, "switch-progress.properties"))
        val record = runCatching { progress.read() }.getOrElse {
            return SwitchOutcome.Failed(status.switchId, "SWITCH_PROGRESS_RECOVERY_REQUIRED", "", status)
        } ?: return null
        activeRequest = record.request
        activeProgress = progress
        sourceWithdrawn = record.withdrawn
        sourceQuiesced = record.quiesced
        attemptedMoves += record.phases
        val from = record.request.fromMode
        val to = record.request.toMode
        val channel = withContext(Dispatchers.IO) { lifecycle?.prepare() ?: HookResult.OK }
        if (!channel.ok) return SwitchOutcome.Failed(status.switchId, channel.code, channel.detail, status)
        val lease = withContext(Dispatchers.IO) { holdLease(status.switchId) }
        if (!lease.ok) return SwitchOutcome.Failed(status.switchId, lease.code, lease.detail, status)
        if (record.waiting && status.state in setOf("REBOOT_REQUIRED", "VERIFIED", "VERIFYING")) return null
        val mover = buildConfigMover(directory, from, to) {}
            ?: return SwitchOutcome.Failed(status.switchId, InstallCommitCodes.MOVE_STAGE_UNWIRED, "", status)
        if (status.state in setOf("REBOOT_REQUIRED", "VERIFIED", "VERIFYING")) {
            // A process death after commit must finish inbound data before verifying the new mode.
            val materialized = withContext(Dispatchers.IO) {
                val inbound = mover.moveIn(status.switchId, from, to)
                val moved = if (inbound.ok) mover.materialize(status.switchId, from, to) else inbound
                if (moved.ok && to == WorkMode.KSU) normalBootUserland() else moved
            }
            if (!materialized.ok) return SwitchOutcome.Failed(
                status.switchId, materialized.code, materialized.detail, status,
            )
            checkpoint(waiting = true)
            return null
        }
        if (status.canRollback || status.state in setOf("COMMIT", "ROLLING_BACK", "RECOVERY_REQUIRED")) {
            val partitions = rollbackPartitions(status.switchId)
            if (partitionRecoveryFailed) return SwitchOutcome.Failed(
                status.switchId, if (operationUncertain) ModeSwitchRootProtocol.UNCERTAIN else InstallCommitCodes.PARTITION_ROLLBACK_FAILED, partitions.detail,
                partitions.status ?: status,
            )
        }
        val config = rollbackConfig(mover, status.switchId, from, to, record.phases)
        if (!config.ok) return SwitchOutcome.Failed(status.switchId, config.code, config.detail, status)
        val restored = withContext(Dispatchers.IO) { restoreSource() }
        if (!restored.ok) return SwitchOutcome.Failed(status.switchId, restored.code, restored.detail, status)
        if (status.state in setOf("BEGIN", "SNAPSHOT", "PRODUCE")) client.abort(status.switchId)
        return SwitchOutcome.Failed(status.switchId, "SWITCH_INTERRUPTED_RESTORED", "", observeStatus())
    }

    suspend fun retryVerify(switchId: String): SwitchOutcome = withSwitchOperation(
        busy = SwitchOutcome.Blocked(ModeSwitchCodes.PENDING, "switch running", emptyList()),
        releaseFailure = ::releaseSwitchFailure,
    ) {
            val status = client.status(switchId)
                ?: return@withSwitchOperation SwitchOutcome.Failed(switchId, ModeSwitchCodes.PLAN_INVALID, "no switch record", null)
            recoverDataStages(status)?.let { return@withSwitchOperation it }
            converge(switchId)
    }

    private suspend fun converge(switchId: String): SwitchOutcome {
        val first = client.verify(switchId)
        if (first.ok) return finishVerified(switchId, first.status ?: client.status(switchId))
        var last: ClientStatus? = first.status ?: client.status(switchId)
        repeat(maxVerifyPolls.coerceAtLeast(1)) {
            val status = client.status(switchId) ?: return SwitchOutcome.Failed(
                switchId, first.code, first.detail, last,
            )
            last = status
            when (status.state) {
                "VERIFIED" -> return finishVerified(switchId, status)
                "RECOVERY_REQUIRED", "ROLLED_BACK" ->
                    return SwitchOutcome.Failed(switchId,
                        status.error.ifEmpty { first.code }, status.errorDetail.ifEmpty { first.detail }, status)
                else -> pollDelay(POLL_INTERVAL_MS)
            }
            val retry = client.verify(switchId)
            if (retry.ok) return finishVerified(switchId, retry.status ?: status)
        }
        return SwitchOutcome.Failed(
            switchId, first.code.ifEmpty { ModeSwitchCodes.VERIFY_BOOT_NOT_OBSERVED },
            first.detail.ifEmpty { "verification did not converge" }, last,
        )
    }

    private suspend fun finishVerified(switchId: String, status: ClientStatus?): SwitchOutcome {
        status?.let { done ->
            val mode = WorkMode.fromWire(done.toMode)
            if (mode != null) {
                val consumed = consumer.verify(mode, bootIds.read())
                if (!consumed.ok) return SwitchOutcome.Incomplete(
                    switchId, consumed.code, consumed.detail, status,
                )
                val committed = runCatching {
                    modeCommit?.commit(
                        mode,
                        bootIds.read(),
                        done.partitions.firstOrNull { it.name == "boot" }?.targetSha256.orEmpty(),
                        done.partitions.firstOrNull { it.name == "init_boot" }?.targetSha256.orEmpty(),
                    )
                }
                if (committed.isFailure) return SwitchOutcome.Incomplete(
                    switchId, "SWITCH_MODE_PUBLICATION_FAILED", "", status,
                )
                // H5：目标模式默认 OFF；切回时按该模式持久化恢复原隐藏（恢复通道需仍有效）。
                hidden?.let { guard ->
                    val hiddenResult = runCatching { guard.afterVerified(mode) }.getOrElse { error ->
                        HiddenSwitchResult.failed("HIDDEN_AFTER_VERIFY_FAILED", error.javaClass.simpleName)
                    }
                    if (!hiddenResult.ok) {
                        return SwitchOutcome.Incomplete(switchId, hiddenResult.code, hiddenResult.detail, status)
                    }
                }
                // P0：方向感知的 Ultra 环境校验（S-2）。A→B 期望 Ultra 已停、B→A 期望 Ultra 可用；
                // 失败 → `Incomplete`（分区已写完，不是 Failed），需要人工恢复时 detail 带
                // `RECOVERY_REQUIRED:` 前缀。必须切到 IO：新鲜探针
                //（`UltraEnvironmentProbe.refresh`）有主线程断言（`UltraEnvironmentProbe.kt:105`）。
                ultra?.let { guard ->
                    val ultraResult = withContext(Dispatchers.IO) {
                        runCatching { guard.afterVerified(mode) }.getOrElse { error ->
                            UltraSwitchResult.failed(UltraSwitchCodes.ULTRA_RESTORE_FAILED,
                                error.javaClass.simpleName, recoveryRequired = true)
                        }
                    }
                    if (!ultraResult.ok) {
                        val detail = if (ultraResult.recoveryRequired) {
                            "RECOVERY_REQUIRED:${ultraResult.detail}"
                        } else {
                            ultraResult.detail
                        }
                        return SwitchOutcome.Incomplete(switchId, ultraResult.code, detail, status)
                    }
                }
                // S6.4 真实 reconcile：失败必须显式返回错误码，绝不把 VERIFIED 静默扩大。
                val sync = runCatching { superuser.reconcileAfterBoot(mode) }.getOrElse { error ->
                    HookResult.failed(ModeSwitchCodes.VERIFY_SUPERUSER_FAILED, error.javaClass.simpleName)
                }
                if (!sync.ok) {
                    return SwitchOutcome.Incomplete(
                        switchId,
                        sync.code.ifEmpty { ModeSwitchCodes.VERIFY_SUPERUSER_FAILED },
                        sync.detail,
                        status,
                    )
                }
            }
        }
        activeProgress = ModeSwitchProgress(File(File(workRoot, switchId), "switch-progress.properties"))
        progressComplete = true
        return SwitchOutcome.Verified(switchId, status)
    }

    // ------------------------------------------------------------------ 其它 UI 动作

    suspend fun abort(switchId: String? = null): ClientResult? = withSwitchOperation<ClientResult?>(
        busy = ClientResult(false, ModeSwitchCodes.PENDING), releaseFailure = ::releaseClientFailure,
    ) {
        val channel = withContext(Dispatchers.IO) { lifecycle?.prepare() ?: HookResult.OK }
        if (!channel.ok) return@withSwitchOperation ClientResult(false, channel.code, channel.detail)
        val status = withContext(Dispatchers.IO) {
            if (switchId == null) observeStatus() else client.statusExact(switchId)?.takeIf {
                it.switchId == switchId
            }
        }
        if (status == null) return@withSwitchOperation null
        if (!status.pending) return@withSwitchOperation null
        // abort is only valid before partition commit; preserve the root journal on recovery failure.
        if (status.state !in setOf("BEGIN", "SNAPSHOT", "PRODUCE")) {
            return@withSwitchOperation ClientResult(false, ModeSwitchCodes.PENDING, status = status)
        }
        val lease = withContext(Dispatchers.IO) { holdLease(status.switchId) }
        if (!lease.ok) return@withSwitchOperation ClientResult(false, lease.code, lease.detail, status)
        restoreManualData(status)?.let { return@withSwitchOperation it }
        client.abort(status.switchId).also {
            if (it.code == ModeSwitchRootProtocol.UNCERTAIN) operationUncertain = true
            if (it.ok) {
                onInstallReceipt(status.switchId, "CANCELLED", null)
                progressComplete = true
            }
        }
    }

    suspend fun rollback(switchId: String? = null): ClientResult? = withSwitchOperation<ClientResult?>(
        busy = ClientResult(false, ModeSwitchCodes.PENDING), releaseFailure = ::releaseClientFailure,
    ) {
        val channel = withContext(Dispatchers.IO) { lifecycle?.prepare() ?: HookResult.OK }
        if (!channel.ok) return@withSwitchOperation ClientResult(false, channel.code, channel.detail)
        val status = withContext(Dispatchers.IO) {
            if (switchId == null) client.status("") else client.statusExact(switchId)?.takeIf {
                it.switchId == switchId
            }
        }
        if (status == null || status.switchId.isEmpty()) return@withSwitchOperation null
        val lease = withContext(Dispatchers.IO) { holdLease(status.switchId) }
        if (!lease.ok) return@withSwitchOperation ClientResult(false, lease.code, lease.detail, status)
        val progress = progressFor(status.switchId)
        if (status.state != "ROLLED_BACK" && status.bootObserved) {
            val slot = environment.deviceFacts()?.slot
                ?: return@withSwitchOperation ClientResult(false, ModeSwitchCodes.ROOT_UNAVAILABLE, status = status)
            progress.recordRollbackBoot(environment.bootId(), slot)
        }
        val result = if (status.state == "ROLLED_BACK") ClientResult(true, status = status)
            else rollbackPartitions(status.switchId)
        if (partitionRecoveryFailed) return@withSwitchOperation result
        restoreManualData(status)?.let { return@withSwitchOperation it }
        if (result.status?.state == "ROLLED_BACK") progress.completeRollback()
        result.copy(status = result.status?.let(::projectRollback))
    }

    private suspend fun restoreManualData(status: ClientStatus): ClientResult? {
        val directory = File(workRoot, status.switchId)
        val progress = ModeSwitchProgress(File(directory, "switch-progress.properties"))
        val record = runCatching { progress.read() }.getOrElse {
            return ClientResult(false, "SWITCH_PROGRESS_RECOVERY_REQUIRED", status = status)
        } ?: return null
        activeRequest = record.request
        activeProgress = progress
        sourceWithdrawn = record.withdrawn
        sourceQuiesced = record.quiesced
        attemptedMoves += record.phases
        val channel = withContext(Dispatchers.IO) { lifecycle?.prepare() ?: HookResult.OK }
        if (!channel.ok) return ClientResult(false, channel.code, channel.detail, status)
        val lease = withContext(Dispatchers.IO) { holdLease(status.switchId) }
        if (!lease.ok) return ClientResult(false, lease.code, lease.detail, status)
        val mover = buildConfigMover(directory, record.request.fromMode, record.request.toMode) {}
            ?: return ClientResult(false, InstallCommitCodes.MOVE_STAGE_UNWIRED, status = status)
        val data = rollbackConfig(mover, status.switchId, record.request.fromMode, record.request.toMode, record.phases)
        if (!data.ok) return ClientResult(false, data.code, data.detail, status)
        val restored = withContext(Dispatchers.IO) { restoreSource() }
        return if (restored.ok) null else ClientResult(false, restored.code, restored.detail, status)
    }

    suspend fun exportLog(destination: File): File? =
        observeStatus()?.let { status -> client.exportLog(status.switchId, destination) }

    private suspend fun failAndAbort(switchId: String, code: String, detail: String): SwitchOutcome {
        if (code == ModeSwitchRootProtocol.UNCERTAIN || operationUncertain) {
            operationUncertain = true
            return SwitchOutcome.Failed(switchId, ModeSwitchRootProtocol.UNCERTAIN, detail, observeStatus())
        }
        val status = runCatching { client.status(switchId) }.getOrNull()
        var abortedStatus: ClientStatus? = null
        if (status != null && status.state in setOf("BEGIN", "SNAPSHOT", "PRODUCE")) {
            val aborted = runCatching { client.abort(switchId) }.getOrNull()
            if (aborted?.code == ModeSwitchRootProtocol.UNCERTAIN) {
                operationUncertain = true
                return SwitchOutcome.Failed(switchId, aborted.code, aborted.detail, aborted.status)
            }
            if (aborted?.ok == true) abortedStatus = aborted.status?.takeIf { it.switchId == switchId }
        }
        return SwitchOutcome.Failed(
            switchId, code.ifEmpty { ModeSwitchCodes.WRITE_FAILED }, detail,
            abortedStatus ?: runCatching { client.status(switchId) }.getOrNull(),
        )
    }

    /**
     * 4b 已执行之后的失败收尾：**先撤配置/模块（4e→4b 逆序），再 abort 事务**。
     *
     * 为什么顺序是"配置先、分区后"：这一支只在 `commit` 之前失败时被调用，此时分区**一个字节
     * 都没写**（第一个写分区在 `client.commit` 内），所以只需要把已出仓的数据放回去。
     * `commit` 失败之后的收尾在调用点自己按契约做（先分区回滚、后配置回滚）。
     */
    private suspend fun failAndAbortWithMoves(
        switchId: String,
        code: String,
        detail: String,
        mover: InstallConfigMover?,
        from: WorkMode,
        to: WorkMode,
        /** 已经真正执行过的搬移阶段（S3）：4b 失败/未做 4e 时只有 [InstallMovePhase.OUT]。 */
        completed: Set<InstallMovePhase> = attemptedMoves.toSet(),
    ): SwitchOutcome {
        if (code == ModeSwitchRootProtocol.UNCERTAIN || operationUncertain) {
            operationUncertain = true
            return SwitchOutcome.Failed(switchId, ModeSwitchRootProtocol.UNCERTAIN, detail, observeStatus())
        }
        val config = rollbackConfig(mover, switchId, from, to, completed)
        val status = runCatching { client.status(switchId) }.getOrNull()
        if (config.ok && status != null && status.state in setOf("BEGIN", "SNAPSHOT", "PRODUCE")) {
            val aborted = runCatching { client.abort(switchId) }.getOrNull()
            if (aborted?.code == ModeSwitchRootProtocol.UNCERTAIN) {
                operationUncertain = true
                return SwitchOutcome.Failed(switchId, aborted.code, aborted.detail, aborted.status)
            }
        }
        val finalCode = if (config.ok) {
            code.ifEmpty { ModeSwitchCodes.WRITE_FAILED }
        } else {
            config.code.ifEmpty { InstallCommitCodes.MOVE_ROLLBACK_FAILED }
        }
        return SwitchOutcome.Failed(
            switchId, finalCode, joinDetails(detail, config.detail),
            runCatching { client.status(switchId) }.getOrNull(),
        )
    }

    /** 配置/模块回滚（mover 为 null = 该后端自己搬、或压根没搬）。 */
    private fun rollbackConfig(
        mover: InstallConfigMover?,
        switchId: String,
        from: WorkMode,
        to: WorkMode,
        completed: Set<InstallMovePhase>,
    ): InstallMoveOutcome {
        if (operationUncertain) return InstallMoveOutcome.failed(ModeSwitchRootProtocol.UNCERTAIN)
        val implementation = mover ?: return InstallMoveOutcome.OK
        if (completed.isEmpty()) return InstallMoveOutcome.OK
        val result = runCatching { implementation.rollback(switchId, from, to, completed) }.getOrElse { error ->
            InstallMoveOutcome.failed(
                InstallCommitCodes.MOVE_ROLLBACK_FAILED, error.javaClass.simpleName,
            )
        }.staged(InstallCommitCodes.MOVE_ROLLBACK_FAILED)
        if (activeRequest?.switchId == switchId) {
            configRecoveryFailed = !result.ok
            if (result.ok) {
                attemptedMoves.clear()
                checkpoint()
            }
        }
        return result
    }

    private fun joinDetails(first: String, second: String): String =
        listOf(first, second).filter { it.isNotEmpty() }.joinToString("; ")

    private fun HookResult.trackUncertain(): HookResult = also {
        if (code == ModeSwitchRootProtocol.UNCERTAIN) operationUncertain = true
    }

    private suspend fun rollbackPartitions(switchId: String): ClientResult {
        if (operationUncertain) return ClientResult(false, ModeSwitchRootProtocol.UNCERTAIN)
        runCatching { progressFor(switchId).invalidateUltraBoot() }.getOrElse {
            return ClientResult(false, "SWITCH_PROGRESS_RECOVERY_REQUIRED", it.javaClass.simpleName)
        }
        ultraBoot = null
        onInstallReceipt(switchId, "RESTORING", null)
        val result = runCatching { client.rollback(switchId) }.getOrElse {
            ClientResult(false, InstallCommitCodes.PARTITION_ROLLBACK_FAILED, it.javaClass.simpleName)
        }
        if (result.code == ModeSwitchRootProtocol.UNCERTAIN) operationUncertain = true
        partitionRecoveryFailed = !result.ok && result.status?.state !in setOf("ROLLED_BACK", "FAILED_PRE_WRITE")
        return result
    }

    /**
     * 把搬移实现的 [InstallMoveOutcome] 归一化成**阶段码**（`SWITCH_MOVE_*`）。
     *
     * 为什么必须归一化：实现（[RootScriptInstallConfigMover]）报的是 `MoveSet.Errors.MOVE_*`
     * 细码（它知道具体哪一步/哪个 entry 失败），而 UI 只按稳定阶段码映射文案。若不归一化，
     * `startSwitch` 与 `installCommit` 两条链会把**同一个失败**报成两个不同的码。
     * 细码不丢：进 `detail`，与实现自己的 detail 合并。
     */
    private fun InstallMoveOutcome.staged(stageCode: String): InstallMoveOutcome =
        if (ok) this else if (code == ModeSwitchRootProtocol.UNCERTAIN) {
            operationUncertain = true
            this
        } else InstallMoveOutcome(false, stageCode, joinDetails(code, detail))

    /**
     * 4b/4e/4f 的 root 脚本实现（Ultra 根通道唯一可用的搬移方式：App 进程不能 rename `/data/adb`）。
     * 构造失败 → null；调用方据此 fail-closed（`SWITCH_MOVE_STAGE_UNWIRED`）。
     */
    private fun buildConfigMover(
        directory: File,
        from: WorkMode,
        to: WorkMode,
        log: (String) -> Unit,
    ): InstallConfigMover? = runCatching {
        val mover = moverFactory?.invoke(directory, from, to) ?: RootScriptInstallConfigMover(
            runner = rootRunner,
            manifestFile = File(directory, "move-manifest.json"),
            dynamicFrom = dataPaths(from),
            dynamicTo = dataPaths(to),
            log = log,
        )
        val tracked = object : InstallConfigMover by mover {
            override fun moveOut(switchId: String, from: WorkMode, to: WorkMode): InstallMoveOutcome {
                val request = activeRequest
                if (request != null) {
                    val withdrawn = withdrawSource(request)
                    if (!withdrawn.ok) {
                        if (withdrawn.code == ModeSwitchRootProtocol.UNCERTAIN) operationUncertain = true
                        return InstallMoveOutcome.failed(withdrawn.code, withdrawn.detail)
                    }
                    attemptedMoves += InstallMovePhase.OUT
                    checkpoint()
                }
                return mover.moveOut(switchId, from, to).also {
                    if (it.code == ModeSwitchRootProtocol.UNCERTAIN) operationUncertain = true
                }
            }

            override fun moveIn(switchId: String, from: WorkMode, to: WorkMode): InstallMoveOutcome {
                attemptedMoves += InstallMovePhase.IN
                checkpoint()
                return mover.moveIn(switchId, from, to).also {
                    if (it.code == ModeSwitchRootProtocol.UNCERTAIN) operationUncertain = true
                }
            }

            override fun rollback(switchId: String, from: WorkMode, to: WorkMode,
                                  completed: Set<InstallMovePhase>): InstallMoveOutcome {
                val result = mover.rollback(switchId, from, to, completed)
                configRecoveryFailed = !result.ok
                if (result.ok) {
                    attemptedMoves.clear()
                    checkpoint()
                }
                return result
            }
        }
        activeMover = tracked
        tracked
    }.getOrNull()

    /**
     * @param baselineId Explicit imported-image id; empty uses current carrier snapshots.
     */
    private fun planFields(
        from: WorkMode,
        to: WorkMode,
        baselineId: String = BASELINE_LATEST,
        requestedPartition: String? = null,
    ): Map<String, String> = mapOf(
        "fromMode" to from.name,
        "toMode" to to.name,
        "baselineId" to baselineId,
        "targetPartition" to (resolvePartition(to, requestedPartition) ?: requestedPartition.orEmpty()),
        "vivoCompatibility" to if (prepareCompatibility != null) "1" else "0",
    )

    private fun currentBootSha256(): String {
        val slot = environment.deviceFacts()?.slot ?: error("ULTRA_KERNEL_INIT_BOOT_TARGET_SLOT_UNKNOWN")
        check(slot in ModeSwitchPreflight.VALID_SLOTS) { ModeSwitchCodes.SLOT_INVALID }
        val result = rootRunner("/system/bin/sha256sum /dev/block/by-name/boot$slot")
        val sha = result.output.trim().substringBefore(' ')
        check(result.ok && ModeSwitchHashes.isHash(sha)) { "ULTRA_KERNEL_INIT_BOOT_TARGET_READ_FAILED" }
        return sha
    }

    private fun resolvePartition(to: WorkMode, requested: String?): String? {
        if (!requested.isNullOrBlank()) return ModeSwitchPreflight.resolveTarget(to, requested)
        val available = if (environment.hasInitBoot()) setOf("boot", "init_boot") else setOf("boot")
        return ModeSwitchPreflight.resolveTarget(to, null, available,
            ModeSwitchKmi.parse(environment.deviceFacts()?.kernelRelease))
    }

    companion object {
        /** Empty uses captured carrier inputs; the name remains for legacy callers. */
        const val BASELINE_LATEST = ""
        const val POLL_INTERVAL_MS = 1_000L

        fun targetPartition(to: WorkMode): String = if (to == WorkMode.LITE) "boot" else "init_boot"
    }
}

// ---------------------------------------------------------------------------
// producer 适配器（W09 Lite / W12 KSU）
// ---------------------------------------------------------------------------

/** W09 `LiteCandidateProducer` → 切换事务的 [SwitchCandidateProducer] 接缝。 */
class LiteSwitchCandidateProducer(
    private val producer: CandidateProducer,
) : SwitchCandidateProducer {
    override fun produce(input: CandidateInput): CandidateResult {
        val outcome = runCatching {
            producer.produce(
                CandidateRequest(
                    baseline = input.baselineImage,
                    workDirectory = File(input.workDir, "lite-produce"),
                    sourceMode = CandidateMode.KSU,
                    targetMode = CandidateMode.LITE,
                    baselineSha256 = input.baselineSha256,
                    expectedSize = input.baselineImage.length(),
                ),
            )
        }.getOrElse { error ->
            return CandidateResult(null, "", 0, "PRODUCE_FAILED", error.message.orEmpty())
        }
        return when (outcome) {
            is CandidateOutcome.Produced -> CandidateResult(
                image = outcome.artifact.image,
                sha256 = outcome.artifact.sha256,
                size = outcome.artifact.size,
                structureEvidence = "lite:${outcome.artifact.headerVersion}/${outcome.artifact.compression}",
            )
            is CandidateOutcome.Rejected -> CandidateResult(null, "", 0, outcome.code, outcome.detail)
        }
    }
}

/** KSU producer 桥（Runtime 用 r4 `KsuInstallProducer` 装配；未装配 → 明确错误）。 */
fun interface KsuSwitchProducerBridge {
    fun produce(input: CandidateInput): CandidateResult

    companion object {
        val UNAVAILABLE = KsuSwitchProducerBridge {
            CandidateResult(
                null, "", 0, "PRODUCE_KSU_UNAVAILABLE",
                "KSU producer 未装配（r4 KsuInstallProducer 需要 libksud_ksu.so + root shell）",
            )
        }
    }
}

class KsuSwitchCandidateProducer(
    private val bridge: KsuSwitchProducerBridge = KsuSwitchProducerBridge.UNAVAILABLE,
) : SwitchCandidateProducer {
    override fun produce(input: CandidateInput): CandidateResult = bridge.produce(input)
}

/** 供 UI 的静态装配信息（r5-ui 契约）。 */
data class ModeSwitchUiSupport(
    val supported: Boolean,
    val target: WorkMode?,
    val currentMode: WorkMode?,
    val blockers: List<SwitchBlocker>,
    /** H5：源模式隐藏 ON → UI 必须弹出二次确认，之后以 `startSwitch(confirmHidden = true)` 调用。 */
    val requiresHiddenConfirmation: Boolean = false,
    /**
     * S-1：**可自动修复**的 note（非阻断；例如"将自动把 SELinux 恢复为 Enforcing"）。
     * `supported` 仍为 true（CTA 可点），本字段只用于提示"点一下会自动修"。
     */
    val repairNotes: List<SwitchBlocker> = emptyList(),
)

/** 便捷：把 eligibility 转成 UI 状态。 */
suspend fun ModeSwitchFacade.uiSupport(target: WorkMode): ModeSwitchUiSupport {
    val current = currentMode()
    return when (val eligibility = canSwitch(target)) {
        is SwitchEligibility.Allowed -> ModeSwitchUiSupport(
            supported = true, target = target, currentMode = current, blockers = emptyList(),
            requiresHiddenConfirmation = eligibility.requiresHiddenConfirmation,
            repairNotes = eligibility.repairNotes,
        )
        is SwitchEligibility.Blocked -> ModeSwitchUiSupport(
            supported = false, target = target, currentMode = current, blockers = eligibility.reasons,
        )
    }
}
