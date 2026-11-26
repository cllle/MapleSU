package dev.maplesu.manager.litehidden

import dev.maplesu.manager.recovery.LocalRecoveryFs
import dev.maplesu.manager.recovery.RecoveryBundle
import dev.maplesu.manager.recovery.RecoveryCommandResult
import dev.maplesu.manager.workmode.Hashes
import dev.maplesu.manager.workmode.WorkMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 生产隐藏后端的事务测试（JVM，fake root/PM/时间/探活）。
 *
 * 覆盖：宿主校验矩阵、激活各步失败与**逆序回滚**、解除寄生三步、模式隔离、
 * `extractNativeLibs=false` 拒绝、su 校验失败、daemon ping 失败、幂等与陈旧备份守卫。
 * 这些用例锁的是「绝不半隐藏」：失败返回必须带失败码，且回滚脚本真的跑过。
 */
class ParasiteBackendTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val host = "dev.maplesu.t2host"
    private val port = 18427
    private val libDir = "/data/app/~~abc123/dev.maplesu.t2host-xyz/lib/arm64"
    private val label = "u:object_r:apk_data_file:s0"
    private val token = "0123456789abcdef0123456789abcdef"
    private val targets = listOf("liba.so", "libb.so")

    private lateinit var apk: File
    private lateinit var loader: File
    private lateinit var bundleDir: File

    @Before
    fun setUp() {
        apk = temp.newFile("manager.apk").apply { writeBytes(ByteArray(2048) { (it % 251).toByte() }) }
        loader = temp.newFile(ParasitePaths.LOADER_NAME).apply { writeBytes(ByteArray(1024) { 9 }) }
        bundleDir = File(temp.root, "recovery").apply { mkdirs() }
    }

    // ── 夹具 ───────────────────────────────────────────────────────────────

    private fun facts(
        packageName: String = host,
        isSystem: Boolean = false,
        extractNativeLibs: Boolean = true,
        abis: List<String> = listOf(HiddenHostPolicy.ARM64),
        libs: List<String> = targets,
    ) = HostFacts(
        packageName = packageName,
        isSystem = isSystem,
        abis = abis,
        nativeLibraryDir = libDir,
        extractNativeLibs = extractNativeLibs,
        libFiles = libs.map { HostLibFile(it, 4096) },
        uid = 10489,
    )

    private class FakeHostFacts(var lookup: HostFactsLookup) : HostFactsSource {
        var calls = 0
        override fun load(packageName: String): HostFactsLookup {
            calls++
            return lookup
        }
    }

    private class FakeProbe(var result: Boolean = true) : HiddenDaemonProbe {
        var calls = 0
        var lastPort = 0
        var lastToken = ""
        override fun ping(port: Int, token: String): Boolean {
            calls++
            lastPort = port
            lastToken = token
            return result
        }
    }

    private class FakeState(var value: Boolean? = false) : HiddenStatePort {
        val writes = mutableListOf<Boolean>()
        var failWrite = false
        var failReadback = false
        override fun write(on: Boolean): Boolean {
            writes += on
            if (failWrite) return false
            value = on
            return true
        }

        override fun read(): Boolean? = if (failReadback) null else value
    }

    private class FakeSuAuth(
        var grantOutcome: SuAuthOutcome = SuAuthOutcome.Granted,
        var revokeOutcome: SuAuthOutcome = SuAuthOutcome.Revoked,
    ) : HiddenSuAuth {
        val granted = mutableSetOf<String>()
        val revokes = mutableListOf<String>()
        var unreadable = false
        override fun grant(packageName: String): SuAuthOutcome = when (grantOutcome) {
            SuAuthOutcome.Granted, SuAuthOutcome.AlreadyGranted -> {
                granted += packageName
                grantOutcome
            }
            is SuAuthOutcome.Failed -> {
                if ((grantOutcome as SuAuthOutcome.Failed).rollbackGrant) granted += packageName
                grantOutcome
            }
            else -> grantOutcome
        }

        override fun revoke(packageName: String): SuAuthOutcome = when (revokeOutcome) {
            SuAuthOutcome.Revoked, SuAuthOutcome.AlreadyGranted -> {
                revokes += packageName
                granted -= packageName
                revokeOutcome
            }
            else -> revokeOutcome
        }

        override fun isGranted(packageName: String): Boolean {
            check(!unreadable) { "readback_unavailable" }
            return packageName in granted
        }
    }

    private inner class FakeRoot : HiddenRootRunner {
        val scripts = mutableListOf<String>()
        var uid = "0"
        var restoreFails = false
        var verifyFails = false
        var startFails = false
        var stopFails = false
        /** 模拟"脚本被改回无条件 echo STOPPED 但退出码非 0"：上层必须仍判失败。 */
        var stopExitCode = 0
        var injectFails = false
        var backupFails = false
        var staleBackup = false
        var hasBackupDir = true
        /** 身份材料（`recovery/identity.json`）写入是否失败；false = 正常落盘。 */
        var identityWriteFails = false
        var managerPresent: Boolean? = true
        var restorePending = false
        var hostStartupProved = true
        var cleanupReceipt: String? = null
        var backupCleanupExitCode = 0
        var backupCleanupThrows = false
        var backupCleanupMarker = true
        var partialBackupCleanup = false
        var identityCleanupExitCode = 0
        var checkpointWriteFails = false
        var checkpointFinishFails = false
        var checkpointReadExitCode = 0
        var checkpointFinishLosesAcknowledgement = false
        var identityCleanupThrows = false
        var identityCleanupMarker = true
        var retirementExitCode = 0
        var retirementThrows = false
        var retirementMarker = true
        var retirementCommitted = false
        var retirementVerify = false
        var restoreRecords: String? = null

        /** 模拟设备上已存在的 webui.conf token（非 null 时 `cat webui.conf` 返回它）。 */
        var reuseToken: String? = null

        fun origSha(name: String): String = Hashes.sha256("orig:$name".toByteArray())
        fun patchedSha(name: String): String = Hashes.sha256("patched:$name".toByteArray())
        val loaderSha: String get() = Hashes.sha256("loader:$label".toByteArray())

        fun manifestText(): String = HostManifestCodec.encode(
            HostManifest(
                packageName = host,
                libraryDir = libDir,
                label = label,
                loaderName = ParasitePaths.LOADER_NAME,
                loaderSha256 = loaderSha,
                files = targets.map {
                    HostBackupFile(it, origSha(it), patchedSha(it), "755", 1000, 1000)
                },
            ),
        )

        override fun run(script: String): RecoveryCommandResult {
            scripts += script
            return when {
                script.contains("# PARASITE_BACKUP_RETIRE_VERIFY") ->
                    RecoveryCommandResult(if (retirementVerify) 0 else 1,
                        if (retirementVerify) "PARASITE_BACKUP_RETIRED" else "")
                script.contains("# PARASITE_BACKUP_RETIRE") -> {
                    check(!retirementThrows) { "backup_retirement_exception" }
                    if (retirementCommitted || retirementExitCode == 0 && retirementMarker) {
                        hasBackupDir = false
                        staleBackup = false
                    }
                    RecoveryCommandResult(retirementExitCode,
                        if (retirementMarker) "PARASITE_BACKUP_RETIRED" else "")
                }
                script.contains("# PARASITE_CLEANUP_READ") ->
                    RecoveryCommandResult(checkpointReadExitCode, cleanupReceipt ?: "PARASITE_CLEANUP_NONE")
                script.contains("# PARASITE_CLEANUP_BEGIN") -> if (checkpointWriteFails) {
                    RecoveryCommandResult(1, "PARASITE_FAIL code=CLEANUP_WRITE_FAILED")
                } else {
                    cleanupReceipt = "MAPLESU_CLEANUP_V1 mode=" +
                        (if (script.contains("mode=KSU")) "KSU" else "LITE") + " host=$host restored=" +
                        if (script.contains("restored=0")) "0" else "1"
                    RecoveryCommandResult(0, "PARASITE_CLEANUP_READY")
                }
                script.contains("# PARASITE_CLEANUP_FINISH") -> if (checkpointFinishFails) {
                    RecoveryCommandResult(1, "PARASITE_FAIL code=CLEANUP_DELETE_FAILED")
                } else {
                    cleanupReceipt = null
                    RecoveryCommandResult(if (checkpointFinishLosesAcknowledgement) 1 else 0, "PARASITE_CLEANUP_GONE")
                }
                script == ParasiteScripts.removeBackup(host) -> {
                    check(!backupCleanupThrows) { "backup_cleanup_exception" }
                    if (partialBackupCleanup) restoreFails = true
                    if (backupCleanupExitCode == 0 && backupCleanupMarker) {
                        hasBackupDir = false
                        staleBackup = false
                    }
                    RecoveryCommandResult(backupCleanupExitCode,
                        if (backupCleanupMarker) "PARASITE_BACKUP_GONE\nLITE_OK" else "")
                }
                script.contains("# MAPLESU_HOST_START_CHECK") -> if (hostStartupProved) {
                    RecoveryCommandResult(0, ParasiteMarkers.HOST_STARTED)
                } else RecoveryCommandResult(1, "PARASITE_FAIL code=HOST_START_UNVERIFIED")
                script.contains(dev.maplesu.manager.recovery.ManagerRestoreHandover.PATH) ->
                    RecoveryCommandResult(0, if (restorePending) "pending:01234567-89ab-cdef-0123-456789abcdef" else "")
                script.contains("MAPLESU_MANAGER_PRESENT") -> RecoveryCommandResult(
                    if (managerPresent == null) 1 else 0,
                    when (managerPresent) {
                        true -> "MAPLESU_MANAGER_PRESENT"
                        false -> "MAPLESU_MANAGER_ABSENT"
                        null -> ""
                    },
                )
                script.contains("MAPLESU_WEBUI_RESUME_OK") -> RecoveryCommandResult(0, "MAPLESU_WEBUI_RESUME_OK")
                script.trim() == ParasiteScripts.probeRoot() -> RecoveryCommandResult(0, uid)
                // 身份材料（D1）：写入自证 / 解除成功后清理自证。
                script.contains(ParasiteMarkers.IDENTITY_OK) -> if (identityWriteFails) {
                    RecoveryCommandResult(85, "PARASITE_FAIL code=IDENTITY_MODE_READBACK")
                } else {
                    RecoveryCommandResult(0, ParasiteMarkers.IDENTITY_OK)
                }
                script.contains(ParasiteMarkers.IDENTITY_GONE) -> {
                    check(!identityCleanupThrows) { "identity_cleanup_exception" }
                    RecoveryCommandResult(identityCleanupExitCode,
                        if (identityCleanupMarker) ParasiteMarkers.IDENTITY_GONE else "")
                }
                // 备份目录探针必须排在 host.manifest 之前：探针脚本里也含清单路径。
                script.contains("PARASITE_BACKUP_PROBE") ->
                    RecoveryCommandResult(0, if (hasBackupDir) "PARASITE_YES" else "PARASITE_NO")
                script.contains("PARASITE_STALE_BACKUP") ->
                    RecoveryCommandResult(0, if (staleBackup || cleanupReceipt != null) "PARASITE_STALE_BACKUP" else "PARASITE_CLEAN")

                script.contains("PARASITE_BACKUP_OK") -> if (backupFails) {
                    RecoveryCommandResult(24, "PARASITE_FAIL code=BACKUP_SHA detail=liba.so")
                } else {
                    val records = targets.joinToString("\n") {
                        "PARASITE_BACKUP name=$it sha=${origSha(it)} mode=755 uid=1000 gid=1000"
                    }
                    RecoveryCommandResult(0, "PARASITE_LABEL=$label\n$records\nPARASITE_BACKUP_OK")
                }

                script.contains("HostSoPatchMain") -> if (injectFails) {
                    RecoveryCommandResult(60, "PARASITE_FAIL code=PATCH_RENAME_FAILED detail=liba.so")
                } else {
                    val patched = targets.joinToString("\n") {
                        "PARASITE_PATCHED name=$it orig=${origSha(it)} patched=${patchedSha(it)} inserted=1"
                    }
                    RecoveryCommandResult(
                        0,
                        "$patched\nPARASITE_LOADER name=${ParasitePaths.LOADER_NAME} sha=$loaderSha\n" +
                            "PARASITE_OK files=${targets.size}",
                    )
                }

                // 复原脚本里也有清单路径（完整性闸门），必须先于 host.manifest 匹配。
                script.contains("PARASITE_RESTORE_OK") -> if (restoreFails) {
                    RecoveryCommandResult(46, "PARASITE_FAIL code=RESTORE_SHA detail=liba.so")
                } else {
                    RecoveryCommandResult(
                        0,
                        (restoreRecords ?: targets.joinToString("\n") {
                            "PARASITE_RESTORED name=$it sha=${origSha(it)}"
                        }) + "\nPARASITE_RESTORE_OK count=${targets.size}",
                    )
                }

                script.contains("host.manifest") -> RecoveryCommandResult(0, manifestText())
                script.contains("MAPLESU_EOF") -> if (startFails) {
                    RecoveryCommandResult(1, "PARASITE_FAIL code=START_SCRIPT")
                } else {
                    RecoveryCommandResult(0, "LITE_OK")
                }

                script.startsWith("/system/bin/sh ") -> if (startFails) {
                    RecoveryCommandResult(11, "PARASITE_FAIL code=DAEMON_EXITED")
                } else {
                    RecoveryCommandResult(0, "PARASITE_STARTED pid=4242")
                }

                script.contains("BAD=") -> if (verifyFails) {
                    RecoveryCommandResult(50, "PARASITE_FAIL code=VERIFY_SHA bad=1")
                } else {
                    RecoveryCommandResult(
                        0,
                        targets.joinToString("\n") { "PARASITE_VERIFY name=$it state=ok" } +
                            "\nPARASITE_VERIFY_OK",
                    )
                }

                script.contains("PARASITE_STOPPED") ->
                    RecoveryCommandResult(
                        if (stopFails) 1 else stopExitCode,
                        if (stopFails) "" else "PARASITE_STOPPED",
                    )

                script.contains("PARASITE_YES") ->
                    RecoveryCommandResult(0, if (hasBackupDir) "PARASITE_YES" else "PARASITE_NO")

                script.contains("libdir.txt") -> RecoveryCommandResult(0, libDir)
                script.contains("label.txt") -> RecoveryCommandResult(0, label)
                script.contains(ParasitePaths.WEBUI_CONFIG) ->
                    RecoveryCommandResult(0, reuseToken?.let { "webuiPort=18427\ntoken=$it\n" } ?: "")
                script.startsWith("rm -rf ") -> RecoveryCommandResult(0, "LITE_OK")
                else -> RecoveryCommandResult(0, "")
            }
        }
    }

    private fun bundle(): RecoveryBundle = RecoveryBundle(
        fs = LocalRecoveryFs(),
        directory = bundleDir.absolutePath,
        clock = { 1_700_000_000_000L },
        locationPolicy = { true },
    )

    private fun backend(
        mode: WorkMode? = WorkMode.LITE,
        ultraRoot: FakeRoot = FakeRoot(),
        ksuRoot: FakeRoot = FakeRoot(),
        hostFacts: HostFactsSource = FakeHostFacts(HostFactsLookup.Ok(facts())),
        probe: HiddenDaemonProbe = FakeProbe(),
        state: HiddenStatePort = FakeState(),
        suAuth: HiddenSuAuth = FakeSuAuth(),
        rootAvailable: Boolean = true,
        prepareBoot: () -> Boolean = { true },
        identity: RecoveryIdentity? = RecoveryIdentity.of("0123456789abcdef", "dev.maplesu.manager", 10489),
        logger: (String) -> Unit = {},
        managerRemoval: HiddenManagerRemoval = HiddenManagerRemoval { _, _ -> HiddenManagerRemovalResult(true) },
    ): ProductionHiddenModeBackend = ProductionHiddenModeBackend(
        modeProvider = { mode },
        deps = mapOf(
            WorkMode.LITE to HiddenModeDeps(
                mode = WorkMode.LITE,
                root = ultraRoot,
                suAuth = suAuth,
                state = state,
                rootAvailable = { rootAvailable },
                identity = { identity },
                prepareBoot = prepareBoot,
            ),
            WorkMode.KSU to HiddenModeDeps(
                mode = WorkMode.KSU,
                root = ksuRoot,
                suAuth = KsuPolicySuAuth,
                state = state,
                rootAvailable = { rootAvailable },
                identity = { identity },
            ),
        ),
        hostFacts = hostFacts,
        bundle = bundle(),
        probe = probe,
        managerApkPath = apk.absolutePath,
        managerVersionCode = 2,
        loaderSourcePath = loader.absolutePath,
        tokenFactory = { token },
        pause = {},
        logger = logger,
        managerRemoval = managerRemoval,
    )

    private fun activate(b: ProductionHiddenModeBackend) = runBlocking { b.activate(host, port) }

    private fun deactivate(b: ProductionHiddenModeBackend) = runBlocking { b.deactivate(host) }

    private fun withPendingActivation(inspect: () -> Unit) {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val worker = Executors.newSingleThreadExecutor()
        val pending = worker.submit<HiddenActivateResult> {
            activate(backend(prepareBoot = {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                false
            }))
        }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            inspect()
        } finally {
            release.countDown()
            try { assertFalse(pending.get(5, TimeUnit.SECONDS).success) }
            finally { worker.shutdownNow() }
        }
    }

    @Test
    fun concurrentActivationIsRejectedBeforeTouchingAnotherHostTransaction() {
        withPendingActivation {
            val root = FakeRoot()
            val result = activate(backend(ultraRoot = root))
            assertFalse(result.success)
            assertEquals("PARASITE_BUSY", result.code)
            assertTrue(root.scripts.isEmpty())
        }
        assertTrue(activate(backend()).success)
    }

    @Test
    fun deactivationCannotRestoreAHostWhileActivationStillOwnsIt() {
        withPendingActivation {
            val root = FakeRoot()
            val result = deactivate(backend(ultraRoot = root))
            assertFalse(result.success)
            assertEquals("PARASITE_BUSY", result.code)
            assertTrue(root.scripts.isEmpty())
        }
        assertTrue(deactivate(backend()).success)
    }

    @Test
    fun activationSurvivesPreSubmissionBusyAtWriteReadbackAndFinalVerification() {
        val replies = ArrayDeque(listOf(
            RecoveryCommandResult(-1, "ULTRA_ROOT_FAILED:MAPLESU_SDK_BUSY"),
            RecoveryCommandResult(0, "LITE_OK\n"),
            RecoveryCommandResult(-1, "ROOT_EXECUTION_BUSY"),
            RecoveryCommandResult(0, "1\n"),
            RecoveryCommandResult(-1, "ULTRA_ROOT_FAILED:MAPLESU_SDK_BUSY"),
            RecoveryCommandResult(0, "1\n"),
        ))
        val commands = mutableListOf<String>()
        val state = RootFileHiddenState(HiddenRootRunner {
            commands += it
            replies.removeFirst()
        }, pause = {})
        var removals = 0
        val root = FakeRoot()
        val result = activate(backend(ultraRoot = root, state = state, managerRemoval = HiddenManagerRemoval { _, _ ->
            removals++
            HiddenManagerRemovalResult(true)
        }))
        assertTrue("transient admission contention must not fail activation: $result", result.success)
        assertTrue(result.steps.all { it.ok })
        assertEquals(1, removals)
        assertEquals(2, commands.count { it == LiteHiddenPaths.stateScript(write = true, value = "1") })
        assertEquals(4, commands.count { it == LiteHiddenPaths.stateReadScript() })
        assertFalse(root.scripts.any { it.contains("PARASITE_RESTORE_OK") })
    }

    // ── 激活成功路径 ───────────────────────────────────────────────────────

    // 1
    @Test
    fun activateSucceedsThroughAllFiveSteps() {
        val root = FakeRoot()
        val state = FakeState()
        val suAuth = FakeSuAuth()
        val probe = FakeProbe()
        val b = backend(ultraRoot = root, state = state, suAuth = suAuth, probe = probe)
        val result = activate(b)

        assertTrue(result.steps.joinToString { "${it.step}:${it.ok}:${it.code}:${it.detail}" }, result.success)
        assertEquals(
            listOf("precheck", "host_so", "webui_start", "webui_ping", "hide_state"),
            result.steps.map { it.step },
        )
        assertEquals("http://127.0.0.1:18427/x7k9f/$token/", result.recoveryUrl)
        assertEquals(host, result.hostPackage)
        assertEquals(true, state.value)
        assertTrue(suAuth.granted.contains(host))
        assertEquals(port, probe.lastPort)
        assertEquals("token 只给 daemon，不给端口探活前的任何地方", token, probe.lastToken)
    }

    // 2
    @Test
    fun removalFailureRollsBackRatherThanReportingHiddenSuccess() {
        val root = FakeRoot()
        val state = FakeState()
        val suAuth = FakeSuAuth()
        var requests = 0
        var requestBoundary = 0
        val result = activate(backend(
            ultraRoot = root, state = state, suAuth = suAuth,
            managerRemoval = HiddenManagerRemoval { _, _ -> requests++; requestBoundary = root.scripts.size; HiddenManagerRemovalResult(false) },
        ))
        assertFalse(result.success)
        assertEquals("MANAGER_UNINSTALL_UNVERIFIED", result.code)
        assertEquals(1, requests)
        assertEquals(false, state.value)
        assertFalse(host in suAuth.granted)
        assertTrue(root.scripts.any { it.contains("PARASITE_RESTORE_OK") })
        assertTrue(result.recoveryUrl.isNotBlank())
        assertFalse("a timed-out PM request may still remove the app later",
            root.scripts.drop(requestBoundary).any { it.contains("PARASITE_STOPPED") })
    }

    @Test
    fun managerRemovalFailureKeepsItsOwnStepInsteadOfBlamingStateWrite() {
        val result = activate(backend(managerRemoval = HiddenManagerRemoval { _, _ -> HiddenManagerRemovalResult(false) }))
        assertTrue("state was written and verified before removal",
            result.steps.single { it.step == "hide_state" }.ok)
        assertEquals("manager_removal", result.steps.last().step)
        assertFalse(result.steps.last().ok)
    }

    @Test
    fun managerRemovalFailureHasAnActionableReasonInsteadOfUnknown() {
        val result = activate(backend(managerRemoval = HiddenManagerRemoval { _, _ -> HiddenManagerRemovalResult(false) }))
        assertEquals("lite_hidden_reason_manager_removal", result.failure?.messageKey)
    }

    @Test
    fun daemonReadinessFailureKeepsItsCodeAndDoesNotBlameStateWrite() {
        val result = activate(backend(managerRemoval = HiddenManagerRemoval { _, _ ->
            HiddenManagerRemovalResult(false, "MANAGER_FULL_RESTORE_UNAVAILABLE")
        }))
        assertFalse(result.success)
        assertEquals("MANAGER_FULL_RESTORE_UNAVAILABLE", result.code)
        assertEquals("lite_hidden_reason_recovery_not_ready", result.failure?.messageKey)
        assertTrue(result.steps.single { it.step == "hide_state" }.ok)
        assertEquals("manager_removal", result.steps.last().step)
        assertEquals(result.code, result.steps.last().code)
    }

    @Test
    fun everyKnownDaemonFailureHasASpecificMappedReason() {
        mapOf(
            "MANAGER_HIDE_NOT_VERIFIED" to HiddenFailure.VERIFY_FAILED,
            "MANAGER_RECOVERY_NOT_VERIFIED" to HiddenFailure.RECOVERY_NOT_READY,
            "MANAGER_FULL_RESTORE_UNAVAILABLE" to HiddenFailure.RECOVERY_NOT_READY,
            "MANAGER_UNINSTALL_FAILED" to HiddenFailure.MANAGER_REMOVAL_FAILED,
            "MANAGER_UNINSTALL_UNVERIFIED" to HiddenFailure.MANAGER_REMOVAL_FAILED,
            "MANAGER_UNINSTALL_UNAVAILABLE" to HiddenFailure.MANAGER_REMOVAL_FAILED,
        ).forEach { (code, failure) ->
            val result = activate(backend(managerRemoval = HiddenManagerRemoval { _, _ ->
                HiddenManagerRemovalResult(false, code)
            }))
            assertEquals(code, result.code)
            assertEquals(code, failure, result.failure)
            assertEquals(code, result.steps.last().code)
            assertEquals("manager_removal", result.steps.last().step)
        }
    }

    @Test
    fun anInterruptedRemovalStillReportsTheRemovalStepAndRetainsRecovery() {
        val state = FakeState()
        val result = activate(backend(state = state, managerRemoval = HiddenManagerRemoval { _, _ ->
            throw java.io.IOException("response lost")
        }))
        assertFalse(result.success)
        assertEquals("MANAGER_UNINSTALL_UNVERIFIED", result.code)
        assertEquals(HiddenFailure.MANAGER_REMOVAL_FAILED, result.failure)
        assertEquals("manager_removal", result.steps.last().step)
        assertTrue(result.steps.single { it.step == "hide_state" }.ok)
        assertEquals(false, state.value)
        assertTrue(result.recoveryUrl.isNotBlank())
    }

    @Test
    fun lostUninstallResponseMustPreserveRecoveryWhenManagerIsAbsent() {
        val root = FakeRoot().apply { managerPresent = false }
        val state = FakeState()
        var beforeRemoval = 0
        val result = activate(backend(
            ultraRoot = root, state = state,
            managerRemoval = HiddenManagerRemoval { _, _ -> beforeRemoval = root.scripts.size; HiddenManagerRemovalResult(false) },
        ))
        assertTrue("verified removal must reconcile the lost response: $result", result.success)
        assertEquals(true, state.value)
        assertFalse(root.scripts.drop(beforeRemoval).any { it.contains("PARASITE_STOPPED") })
    }

    @Test
    fun uncertainUninstallMustKeepWebAndHiddenStateForRetry() {
        val root = FakeRoot().apply { managerPresent = null }
        val state = FakeState()
        var beforeRemoval = 0
        val result = activate(backend(
            ultraRoot = root, state = state,
            managerRemoval = HiddenManagerRemoval { _, _ -> beforeRemoval = root.scripts.size; HiddenManagerRemovalResult(false) },
        ))
        assertFalse(result.success)
        assertEquals(HiddenFailure.MANAGER_REMOVAL_FAILED, result.failure)
        assertTrue(result.steps.single { it.step == "hide_state" }.ok)
        assertEquals("manager_removal", result.steps.last().step)
        assertFalse(result.steps.last().ok)
        assertEquals(true, state.value)
        assertEquals("http://127.0.0.1:18427/x7k9f/$token/", result.recoveryUrl)
        assertFalse(root.scripts.drop(beforeRemoval).any { it.contains("PARASITE_STOPPED") })
    }

    @Test
    fun anUnstoppableDaemonMustKeepItsOriginalClasspathArchive() {
        bundle().prepare("dev.maplesu.manager", 1, apk.absolutePath,
            webuiConfig = "webuiPort=$port\ntoken=$token\nworkMode=LITE\n".toByteArray())
        val originalManifest = File(bundleDir, "manifest.json").readText()
        val root = FakeRoot().apply { stopFails = true }
        val result = activate(backend(ultraRoot = root))
        assertFalse(result.success)
        assertEquals(originalManifest, File(bundleDir, "manifest.json").readText())
        assertTrue(result.steps.last().detail.contains("previous_daemon_stop_unverified"))
    }

    @Test
    fun pendingManagerHandoverBlocksAnotherActivationBeforeHostMutation() {
        val root = FakeRoot().apply { restorePending = true }
        val result = activate(backend(ultraRoot = root))
        assertFalse(result.success)
        assertTrue(result.steps.last().detail.contains("manager_restore_confirmation_required"))
        assertFalse(root.scripts.any { it.contains("HostSoPatchMain") })
    }

    @Test
    fun normalKeepsExistingInAppStealthRatherThanUninstallingItsKernelRestorer() {
        var removals = 0
        val result = activate(backend(
            mode = WorkMode.KSU,
            managerRemoval = HiddenManagerRemoval { _, _ -> removals++; HiddenManagerRemovalResult(true) },
        ))
        assertTrue(result.steps.toString(), result.success)
        assertEquals(0, removals)
        assertFalse(result.steps.last().detail.contains("manager=absent"))
    }

    @Test
    fun oldDaemonIsReplacedBeforeManagerRemovalAndKeepsRecoveryToken() {
        val existing = "c".repeat(32)
        val root = FakeRoot().apply { reuseToken = existing }
        val capabilities = mutableListOf<Boolean>()
        var removalToken = ""
        val removal = object : HiddenManagerRemoval {
            override fun supported(port: Int, token: String): Boolean {
                val ready = capabilities.isNotEmpty()
                capabilities += ready
                return ready
            }
            override fun uninstall(port: Int, token: String): HiddenManagerRemovalResult {
                removalToken = token
                return HiddenManagerRemovalResult(true)
            }
        }
        val result = activate(backend(ultraRoot = root, managerRemoval = removal))
        assertTrue(result.steps.toString(), result.success)
        assertEquals(listOf(false, true), capabilities)
        assertEquals(existing, removalToken)
        assertTrue(root.scripts.any { it.contains("PARASITE_STARTED") })
    }

    @Test
    fun unsupportedDaemonNeverReachesManagerRemovalAndRestoresHost() {
        val root = FakeRoot()
        val state = FakeState()
        var removals = 0
        val removal = object : HiddenManagerRemoval {
            override fun supported(port: Int, token: String) = false
            override fun uninstall(port: Int, token: String): HiddenManagerRemovalResult { removals++; return HiddenManagerRemovalResult(true) }
        }
        val result = activate(backend(ultraRoot = root, state = state, managerRemoval = removal))
        assertFalse(result.success)
        assertEquals(0, removals)
        assertEquals(false, state.value)
        assertTrue(root.scripts.any { it.contains("PARASITE_RESTORE_OK") })
    }

    // 2
    @Test
    fun activationReusesHealthyDaemonAndKeepsItsToken() {
        // 真机场景：用户先在「WebUI 自检」里启动过 daemon，再点开启隐藏。
        // 必须复用该 daemon + 沿用它的 token；否则第二个 daemon 端口冲突立即退出（DAEMON_EXITED）。
        val existing = "c".repeat(32)
        bundle().prepare("dev.maplesu.manager", 2, apk.absolutePath,
            webuiConfig = "webuiPort=$port\ntoken=$existing\nworkMode=LITE\n".toByteArray())
        val root = FakeRoot().apply { reuseToken = existing }
        val probe = FakeProbe(result = true)
        val state = FakeState()
        val b = backend(ultraRoot = root, probe = probe, state = state)
        val result = activate(b)
        assertTrue(result.steps.joinToString { "${it.step}:${it.ok}:${it.code}:${it.detail}" }, result.success)
        val webui = result.steps.first { it.step == "webui_start" }
        assertTrue("必须复用已运行的 daemon：${webui.detail}", webui.detail.contains("daemon=reused"))
        assertTrue("reused listener still needs its reboot entry", root.scripts.any { it.contains("MAPLESU_EOF") })
        assertTrue(
            "端口被占用时不能再拉起第二个 daemon",
            root.scripts.none { it == ParasiteScripts.runStartScript() || it == ParasiteScripts.daemonStop() },
        )
        assertEquals("http://127.0.0.1:18427/x7k9f/$existing/", result.recoveryUrl)
        assertEquals(existing, probe.lastToken)
        assertEquals(true, state.value)
    }

    @Test
    fun sameVersionDifferentApkMustUpgradeRecoveryInsteadOfReusingOldCode() {
        val existing = "c".repeat(32)
        val oldApk = temp.newFile("old-manager.apk").apply { writeText("old code with same version") }
        bundle().prepare("dev.maplesu.manager", 2, oldApk.path,
            webuiConfig = "webuiPort=$port\ntoken=$existing\nworkMode=LITE\n".toByteArray())
        val root = FakeRoot().apply { reuseToken = existing }
        val result = activate(backend(ultraRoot = root))
        assertTrue(result.success)
        assertFalse(result.steps.first { it.step == "webui_start" }.detail.contains("daemon=reused"))
        assertTrue(root.scripts.any { it.contains(ParasiteMarkers.STOPPED) })
        assertEquals(Hashes.sha256(apk), bundle().verify().manifest?.apkSha256)
    }

    // 3
    @Test
    fun activateRunsBackupInjectAndDaemonScripts() {
        val root = FakeRoot()
        val b = backend(ultraRoot = root)
        assertTrue(activate(b).success)
        assertTrue(root.scripts.any { it.contains("PARASITE_BACKUP_OK") })
        assertTrue("注入必须走既有 root 通道起 app_process", root.scripts.any { it.contains("HostSoPatchMain") })
        assertTrue(root.scripts.any { it.contains("CLASSPATH=") })
        assertTrue(root.scripts.any { it.contains("MAPLESU_EOF") })
        assertTrue(root.scripts.any { it.startsWith("/system/bin/sh ") })
        // 不许出现会误杀自己的 pkill -f（spike §8.2）。
        assertFalse(root.scripts.any { it.contains("pkill -f") })
    }

    // 3
    @Test
    fun activateWritesRecoveryBundleAndWebUiConfig() {
        val b = backend()
        assertTrue(activate(b).success)
        assertTrue(File(bundleDir, "manager.apk").isFile)
        val config = File(bundleDir, "webui.conf").readText()
        assertTrue(config.contains("webuiPort=$port"))
        assertTrue(config.contains("token=$token"))
        assertTrue(File(bundleDir, "manifest.json").isFile)
    }

    // 真机 D1：WebUI 一键解除寄生卡在 `revoke_su_auth`，因为 daemon 只有系统 androidId，
    // 解不开用**App 作用域** androidId 封的 rootkey ⇒ 激活时必须把身份材料落盘。
    @Test
    fun activatePersistsRecoveryIdentityForWebUiRevoke() {
        val root = FakeRoot()
        val material = requireNotNull(RecoveryIdentity.of("0123456789abcdef", "dev.maplesu.manager", 10_123))
        val result = activate(backend(ultraRoot = root, identity = material))

        assertTrue(result.steps.joinToString { "${it.step}:${it.ok}:${it.code}:${it.detail}" }, result.success)
        val hostSo = result.steps.first { it.step == "host_so" }
        assertTrue("激活必须落身份材料：${hostSo.detail}", hostSo.detail.contains("identity=ok"))
        val script = root.scripts.first { it.contains(ParasitePaths.IDENTITY_FILE) }
        assertTrue("必须写到 recovery/identity.json", script.contains(ParasitePaths.IDENTITY_FILE))
        assertTrue("必须是 0600 原子写", script.contains("chmod 600"))
        assertTrue(script.contains("0123456789abcdef"))
        assertFalse("身份材料绝不带 rootKey/token", script.contains("token="))
    }

    @Test
    fun activationWithoutIdentityMaterialSaysSoInsteadOfPretending() {
        // 没有接线（拿不到 Settings.Secure）→ 激活仍成功，但 detail 必须如实标 skipped。
        val result = activate(backend(identity = null))
        assertFalse(result.success)
        assertEquals("MANAGER_RECOVERY_IDENTITY_UNVERIFIED", result.code)
        val hostSo = result.steps.first { it.step == "host_so" }
        assertTrue(hostSo.detail.contains("identity=skipped:no_material"))
    }

    @Test
    fun identityWriteFailureIsReportedInTheStepDetail() {
        val root = FakeRoot().apply { identityWriteFails = true }
        val material = requireNotNull(RecoveryIdentity.of("0123456789abcdef", "dev.maplesu.manager", 10_123))
        val result = activate(backend(ultraRoot = root, identity = material))

        assertFalse("真实卸载前必须保有独立恢复身份", result.success)
        assertEquals("MANAGER_RECOVERY_IDENTITY_UNVERIFIED", result.code)
        val hostSo = result.steps.first { it.step == "host_so" }
        assertTrue("不许把写失败伪装成 ok：${hostSo.detail}", hostSo.detail.contains("identity=failed:write"))
    }

    // ── 宿主校验矩阵 ───────────────────────────────────────────────────────

    // 4
    @Test
    fun rejectsHostWithExtractNativeLibsFalse() {
        val root = FakeRoot()
        val b = backend(
            ultraRoot = root,
            hostFacts = FakeHostFacts(HostFactsLookup.Ok(facts(extractNativeLibs = false))),
        )
        val result = activate(b)
        assertFalse(result.success)
        assertEquals("HOST_UNSUPPORTED", result.code)
        assertEquals(HiddenFailure.HOST_REJECTED, result.failure)
        assertTrue(result.steps.single().detail.contains("extractNativeLibs=false"))
        assertEquals("拒绝时不得写任何 root 状态（只跑过 root 自检）", 1, root.scripts.size)
    }

    // 5
    @Test
    fun rejectsNonArm64Host() {
        val b = backend(
            hostFacts = FakeHostFacts(HostFactsLookup.Ok(facts(abis = listOf("armeabi-v7a")))),
        )
        val result = activate(b)
        assertFalse(result.success)
        assertEquals("HOST_UNSUPPORTED", result.code)
        assertTrue(result.steps.single().detail.contains("HOST_NO_ARM64"))
    }

    // 6
    @Test
    fun rejectsSelfPackage() {
        val b = backend(
            hostFacts = FakeHostFacts(HostFactsLookup.Ok(facts(packageName = "dev.maplesu.manager"))),
        )
        val result = activate(b)
        assertFalse(result.success)
        assertEquals("HOST_UNSUPPORTED", result.code)
        assertTrue(result.steps.single().detail.contains("HOST_SELF"))
    }

    // 7
    @Test
    fun rejectsSystemApp() {
        val b = backend(hostFacts = FakeHostFacts(HostFactsLookup.Ok(facts(isSystem = true))))
        val result = activate(b)
        assertFalse(result.success)
        assertEquals("HOST_UNSUPPORTED", result.code)
        assertTrue(result.steps.single().detail.contains("HOST_SYSTEM_APP"))
    }

    // 8
    @Test
    fun rejectsHostWithoutNativeLibraries() {
        val b = backend(hostFacts = FakeHostFacts(HostFactsLookup.Ok(facts(libs = emptyList()))))
        val result = activate(b)
        assertFalse(result.success)
        assertEquals("HOST_UNSUPPORTED", result.code)
        assertTrue(result.steps.single().detail.contains("HOST_NO_NATIVE_LIB"))
    }

    // 9
    @Test
    fun rejectsUninstalledHost() {
        val b = backend(hostFacts = FakeHostFacts(HostFactsLookup.Missing(host)))
        val result = activate(b)
        assertFalse(result.success)
        assertEquals("HOST_UNSUPPORTED", result.code)
        assertEquals("not_installed", result.steps.single().detail)
    }

    // 10
    @Test
    fun hostScanFailureIsReportedAsScanFailure() {
        val b = backend(hostFacts = FakeHostFacts(HostFactsLookup.Failed("SecurityException")))
        val result = activate(b)
        assertFalse(result.success)
        assertEquals("HOST_SCAN_FAILED", result.code)
        assertEquals(HiddenFailure.HOST_SCAN_FAILED, result.failure)
    }

    // ── root / 备份 / 注入 / su 失败与回滚 ────────────────────────────────

    // 11
    @Test
    fun activateFailsClosedWhenRootBackendUnavailable() {
        val root = FakeRoot()
        val b = backend(ultraRoot = root, rootAvailable = false)
        val result = activate(b)
        assertFalse(result.success)
        assertEquals(HiddenFailure.ROOT_UNAVAILABLE.wire, result.code)
        assertTrue("后端不可用时一条命令都不该执行", root.scripts.isEmpty())
    }

    // 12
    @Test
    fun activateFailsClosedWhenRootProbeIsNotUidZero() {
        val root = FakeRoot().apply { uid = "2000" }
        val b = backend(ultraRoot = root)
        val result = activate(b)
        assertFalse(result.success)
        assertEquals(HiddenFailure.ROOT_UNAVAILABLE.wire, result.code)
        assertEquals(1, root.scripts.size)
    }

    // 13
    @Test
    fun staleBackupBlocksActivationInsteadOfOverwritingRecoveryMaterial() {
        val root = FakeRoot().apply { staleBackup = true }
        val b = backend(ultraRoot = root)
        val result = activate(b)
        assertFalse(result.success)
        assertEquals("HOST_BACKUP_STALE", result.code)
        assertFalse("陈旧备份时绝不继续写宿主", root.scripts.any { it.contains("HostSoPatchMain") })
    }

    // 14
    @Test
    fun backupFailureStopsBeforeInjection() {
        val root = FakeRoot().apply { backupFails = true }
        val b = backend(ultraRoot = root)
        val result = activate(b)
        assertFalse(result.success)
        assertEquals("HOST_BACKUP_FAILED", result.code)
        assertEquals(
            "备份失败必须报「备份失败」，不能显示成「改写原生库失败」",
            HiddenFailure.HOST_BACKUP_FAILED,
            result.failure,
        )
        assertFalse(root.scripts.any { it.contains("HostSoPatchMain") })
        assertEquals(HiddenActivateStep.HOST_SO.wire, result.steps.last().step)
        assertFalse(result.steps.last().ok)
    }

    // 15
    @Test
    fun injectFailureRollsBackHostLibraryAndState() {
        val root = FakeRoot().apply { injectFails = true }
        val state = FakeState()
        val suAuth = FakeSuAuth()
        val b = backend(ultraRoot = root, state = state, suAuth = suAuth)
        val result = activate(b)
        assertFalse(result.success)
        assertEquals("HOST_SO_PATCH_FAILED", result.code)
        assertTrue("注入尝试后必须回滚宿主 so", root.scripts.any { it.contains("PARASITE_RESTORE_OK") })
        assertEquals(false, state.value)
        assertFalse(suAuth.granted.contains(host))
        assertTrue(result.steps.last().detail.contains("rollback=ok"))
    }

    // 16
    @Test
    fun suAuthFailureRollsBackInjectedLibrary() {
        val root = FakeRoot()
        val state = FakeState()
        val suAuth = FakeSuAuth(grantOutcome = SuAuthOutcome.Failed("add_su_auth"))
        val b = backend(ultraRoot = root, state = state, suAuth = suAuth)
        val result = activate(b)
        assertFalse(result.success)
        assertEquals("SU_AUTH_FAILED", result.code)
        assertEquals(HiddenFailure.ROOT_UNAVAILABLE, result.failure)
        assertTrue(root.scripts.any { it.contains("PARASITE_RESTORE_OK") })
        assertEquals("回滚后不得留下隐藏状态", false, state.value)
    }

    // 17
    @Test
    fun alreadyGrantedSuAuthIsIdempotent() {
        val suAuth = FakeSuAuth(grantOutcome = SuAuthOutcome.AlreadyGranted)
        val root = FakeRoot()
        val b = backend(ultraRoot = root, suAuth = suAuth)
        val result = activate(b)
        assertTrue(result.success)
        assertTrue(result.steps[1].detail.contains("su_auth=already_granted"))
        assertTrue(suAuth.granted.contains(host))
    }

    // ── daemon / 隐藏状态 / 终检 ──────────────────────────────────────────

    @Test
    fun healthyManagerStartedDaemonDoesNotPermitRemovalWithoutHostStartup() {
        val root = FakeRoot().apply { hostStartupProved = false }
        var removals = 0
        val result = activate(backend(ultraRoot = root, managerRemoval = HiddenManagerRemoval { _, _ ->
            removals++
            HiddenManagerRemovalResult(true)
        }))
        assertFalse(result.success)
        assertEquals(ParasiteCodes.HOST_START_UNVERIFIED, result.code)
        assertEquals(0, removals)
        assertTrue(root.scripts.any { it.contains("# MAPLESU_HOST_START_CHECK") })
        assertTrue(root.scripts.any { it.contains("PARASITE_RESTORE_OK") })
    }

    @Test
    fun verifiedHostStartupPrecedesManagerRemoval() {
        val root = FakeRoot()
        var removals = 0
        val result = activate(backend(ultraRoot = root, managerRemoval = HiddenManagerRemoval { _, _ ->
            assertTrue(root.scripts.any { it.contains("# MAPLESU_HOST_START_CHECK") })
            removals++
            HiddenManagerRemovalResult(true)
        }))
        assertTrue(result.success)
        assertEquals(1, removals)
    }

    @Test
    fun lostGrantReplyStillRollsBackTheNewAuthorization() {
        val suAuth = FakeSuAuth(grantOutcome = SuAuthOutcome.Failed("reply_lost", rollbackGrant = true))
        val result = activate(backend(suAuth = suAuth))
        assertFalse(result.success)
        assertFalse(host in suAuth.granted)
        assertEquals(listOf(host), suAuth.revokes)
    }

    @Test
    fun laterActivationFailuresPreserveExistingHostAuthorization() {
        for (failure in listOf("daemon", "state", "verify")) {
            val root = FakeRoot().apply {
                startFails = failure == "daemon"
                verifyFails = failure == "verify"
            }
            val state = FakeState().apply { failWrite = failure == "state" }
            val suAuth = FakeSuAuth(grantOutcome = SuAuthOutcome.AlreadyGranted).apply { granted += host }
            val result = activate(backend(ultraRoot = root, state = state, suAuth = suAuth))
            assertFalse(result.success)
            assertTrue("original grant must survive $failure", host in suAuth.granted)
            assertTrue("rollback must not revoke an existing grant", suAuth.revokes.isEmpty())
            assertTrue(root.scripts.any { it.contains("PARASITE_RESTORE_OK") })
        }
    }

    // 18
    @Test
    fun daemonStartFailureRollsBackEverything() {
        val root = FakeRoot().apply { startFails = true }
        val state = FakeState()
        val suAuth = FakeSuAuth()
        val b = backend(ultraRoot = root, state = state, suAuth = suAuth)
        val result = activate(b)
        assertFalse(result.success)
        assertEquals("LITEWEB_DAEMON_START_FAILED", result.code)
        assertTrue(root.scripts.any { it.contains("PARASITE_RESTORE_OK") })
        assertEquals(false, state.value)
        assertFalse(suAuth.granted.contains(host))
    }

    // 19
    @Test
    fun daemonPingFailureRollsBackAndReportsDaemonUnverified() {
        val root = FakeRoot()
        val probe = FakeProbe(result = false)
        val state = FakeState()
        val b = backend(ultraRoot = root, probe = probe, state = state)
        val result = activate(b)
        assertFalse(result.success)
        assertEquals("LITEWEB_DAEMON_PING_FAILED", result.code)
        assertEquals(HiddenFailure.WEBUI_NOT_STARTED, result.failure)
        assertEquals(3, probe.calls) // 有界重试
        assertTrue("ping 失败必须把 daemon 停掉", root.scripts.any { it.contains("PARASITE_STOPPED") })
        assertEquals(false, state.value)
    }

    // 20
    @Test
    fun hiddenStateWriteFailureRollsBack() {
        val state = FakeState().apply { failWrite = true }
        val root = FakeRoot()
        val b = backend(ultraRoot = root, state = state)
        val result = activate(b)
        assertFalse(result.success)
        assertEquals("LITE_HIDDEN_WRITE_FAILED", result.code)
        assertEquals(HiddenFailure.VERIFY_FAILED, result.failure)
        assertEquals("hide_state", result.steps.last().step)
        assertFalse(result.steps.last().ok)
        assertTrue(root.scripts.any { it.contains("PARASITE_RESTORE_OK") })
    }

    // 21
    @Test
    fun hiddenStateReadbackFailureIsAFailure() {
        val state = FakeState().apply { failReadback = true }
        val b = backend(state = state)
        val result = activate(b)
        assertFalse(result.success)
        assertEquals("ACTIVATION_ROLLBACK_INCOMPLETE", result.code)
        assertTrue("unknown state must retain the recovery URL", result.recoveryUrl.isNotBlank())
    }

    // 22
    @Test
    fun finalEmbeddedVerificationFailureRollsBack() {
        val root = FakeRoot().apply { verifyFails = true }
        val state = FakeState()
        val b = backend(ultraRoot = root, state = state)
        val result = activate(b)
        assertFalse(result.success)
        assertEquals("LITE_HIDDEN_VERIFY_FAILED", result.code)
        assertTrue("successful state write must retain its result",
            result.steps.single { it.step == "hide_state" }.ok)
        assertEquals("final_verify", result.steps.last().step)
        assertFalse(result.steps.last().ok)
        assertEquals(false, state.value)
        assertTrue(root.scripts.any { it.contains("PARASITE_RESTORE_OK") })
    }

    @Test
    fun finalPingFailureDoesNotBecomeAStateWriteFailure() {
        val state = FakeState()
        val result = activate(backend(state = state, probe = HiddenDaemonProbe { _, _ -> state.value != true }))
        assertFalse(result.success)
        assertEquals(ParasiteCodes.DAEMON_PING_FAILED, result.code)
        assertEquals("final_verify", result.steps.last().step)
        assertTrue(result.steps.single { it.step == "hide_state" }.ok)
        assertEquals(false, state.value)
    }

    @Test
    fun finalReadbackFailureKeepsTheEarlierConfirmedStateWriteResult() {
        val state = object : HiddenStatePort {
            var value = false
            var reads = 0
            override fun write(on: Boolean): Boolean { value = on; return true }
            override fun read(): Boolean? = if (++reads == 2) null else value
        }
        val result = activate(backend(state = state))
        assertFalse(result.success)
        assertEquals(ParasiteCodes.HIDDEN_STATE_READBACK_FAILED, result.code)
        assertEquals("final_verify", result.steps.last().step)
        assertTrue(result.steps.single { it.step == "hide_state" }.ok)
        assertEquals(false, state.value)
    }

    // 23
    @Test
    fun rollbackIncompleteIsReportedLoudly() {
        val root = FakeRoot().apply {
            verifyFails = true
            restoreFails = true
        }
        val b = backend(ultraRoot = root)
        val result = activate(b)
        assertFalse(result.success)
        assertEquals("ACTIVATION_ROLLBACK_INCOMPLETE", result.code)
        assertEquals(HiddenFailure.HOST_SO_PATCH_FAILED, result.failure)
        assertTrue(result.steps.last().detail.contains("incomplete("))
    }

    private fun assertRetirementFailure(result: HiddenActivateResult) {
        assertFalse(result.success)
        assertEquals(ParasiteCodes.ROLLBACK_INCOMPLETE, result.code)
        assertTrue(result.steps.last().detail.contains("rollback=incomplete"))
        assertFalse(result.steps.last().detail.contains("rollback=ok"))
    }

    @Test
    fun rollbackRetirementNonzeroExceptionAndMissingMarkerAreReported() {
        listOf("nonzero", "exception", "marker").forEach { fault ->
            val root = FakeRoot().apply {
                injectFails = true
                retirementExitCode = if (fault == "nonzero") 71 else 0
                retirementThrows = fault == "exception"
                retirementMarker = fault != "marker"
            }
            assertRetirementFailure(activate(backend(ultraRoot = root)))
            assertTrue(root.hasBackupDir)
            assertFalse(root.scripts.any { it == ParasiteScripts.removeBackup(host) })
        }
    }

    @Test
    fun rollbackRequiresEveryExpectedOriginalShaBeforeRetirement() {
        val sha = FakeRoot().origSha("liba.so")
        listOf("", "PARASITE_RESTORED name=liba.so sha=$sha",
            targets.joinToString("\n") { "PARASITE_RESTORED name=$it sha=${"f".repeat(64)}" },
            "PARASITE_RESTORE_SKIP name=liba.so sha=$sha\nPARASITE_RESTORE_SKIP name=liba.so sha=$sha").forEach { records ->
            val root = FakeRoot().apply { injectFails = true; restoreRecords = records }
            assertRetirementFailure(activate(backend(ultraRoot = root)))
            assertFalse(root.scripts.any { it.contains("# PARASITE_BACKUP_RETIRE") })
            assertTrue(root.hasBackupDir)
        }
    }

    @Test
    fun rollbackAcceptsCompleteShaVerifiedSkipRecordsAndAllowsNextActivation() {
        val root = FakeRoot().apply {
            injectFails = true
            restoreRecords = targets.joinToString("\n") { "PARASITE_RESTORE_SKIP name=$it sha=${origSha(it)}" }
        }
        val b = backend(ultraRoot = root)
        val result = activate(b)
        assertEquals(ParasiteCodes.HOST_SO_PATCH_FAILED, result.code)
        assertTrue(result.steps.last().detail.contains("rollback=ok"))
        assertFalse(root.hasBackupDir)
        assertTrue(root.scripts.any { it.contains("# PARASITE_BACKUP_RETIRE") })
        assertFalse(root.scripts.any { it == ParasiteScripts.removeBackup(host) })
        root.injectFails = false
        assertTrue(activate(b).success)
    }

    @Test
    fun rollbackLostMoveAcknowledgementIsResolvedOnlyByReadOnlyMatchingVerification() {
        val root = FakeRoot().apply {
            injectFails = true
            retirementExitCode = 71
            retirementCommitted = true
            retirementVerify = true
        }
        val result = activate(backend(ultraRoot = root))
        assertEquals(ParasiteCodes.HOST_SO_PATCH_FAILED, result.code)
        assertTrue(result.steps.last().detail.contains("rollback=ok"))
        assertTrue(root.scripts.any { it.contains("# PARASITE_BACKUP_RETIRE_VERIFY") })
        assertFalse(root.hasBackupDir)
    }

    @Test
    fun rollbackKeptRecoveryAndPreexistingGrantAreNotFullDeactivation() {
        val root = FakeRoot().apply { retirementExitCode = 71 }
        val auth = FakeSuAuth(grantOutcome = SuAuthOutcome.AlreadyGranted).apply { granted += host }
        val result = activate(backend(ultraRoot = root, suAuth = auth, managerRemoval = HiddenManagerRemoval { _, _ ->
            HiddenManagerRemovalResult(false)
        }))
        assertRetirementFailure(result)
        assertTrue(host in auth.granted)
        assertTrue(auth.revokes.isEmpty())
        assertTrue(result.recoveryUrl.isNotEmpty())
        val restoredAt = root.scripts.indexOfLast { it.contains(ParasiteMarkers.RESTORE_OK) }
        assertFalse(root.scripts.drop(restoredAt + 1).any { it == ParasiteScripts.daemonStop() })
        assertFalse(root.scripts.any { it.contains("# PARASITE_CLEANUP_BEGIN") || it.contains(ParasiteMarkers.IDENTITY_GONE) })
    }

    // ── 解除寄生 ───────────────────────────────────────────────────────────

    @Test
    fun activationRequiresIndependentBootBeforeHostMutation() {
        val root = FakeRoot()
        val result = activate(backend(ultraRoot = root, prepareBoot = { false }))
        assertFalse(result.success)
        assertEquals("boot_recovery_unavailable", result.steps.last().detail)
        assertFalse(root.scripts.any { it.contains("PARASITE_PATCHED") || it.contains("PARASITE_BACKUP_OK") })
    }

    @Test
    fun deactivateVerifiesActualRootBeforeTouchingHostOrAuthorization() {
        val root = FakeRoot().apply { uid = "10489" }
        val auth = FakeSuAuth().apply { granted += host }
        val result = deactivate(backend(ultraRoot = root, suAuth = auth))
        assertFalse(result.success)
        assertEquals(HiddenFailure.ROOT_UNAVAILABLE, result.failure)
        assertTrue(host in auth.granted)
        assertEquals(listOf(ParasiteScripts.probeRoot()), root.scripts)
    }

    // 24
    @Test
    fun deactivateRestoresSoRevokesSuAndStopsDaemon() {
        val root = FakeRoot()
        val state = FakeState(value = true)
        val suAuth = FakeSuAuth().apply { granted += host }
        val b = backend(ultraRoot = root, state = state, suAuth = suAuth)
        val result = deactivate(b)
        assertTrue(result.steps.joinToString { "${it.step}:${it.ok}:${it.code}" }, result.success)
        assertEquals(
            listOf("restore_host_so", "revoke_su_auth", "stop_daemon"),
            result.steps.map { it.step },
        )
        assertTrue(result.hostSoRestored)
        assertTrue(result.suAuthRevoked)
        assertTrue(result.daemonStopped)
        assertEquals(false, state.value)
        assertFalse(suAuth.granted.contains(host))
        assertTrue(root.scripts.any { it.contains("PARASITE_RESTORE_OK") })
        assertTrue(root.scripts.any { it.contains("PARASITE_STOPPED") })
    }

    @Test
    fun backupCleanupNonzeroIsAnAccurateRetryableFailure() {
        val root = FakeRoot().apply { backupCleanupExitCode = 1 }
        val auth = FakeSuAuth().apply { granted += host }
        val result = deactivate(backend(ultraRoot = root, suAuth = auth, state = FakeState(true)))
        assertFalse(result.success)
        assertEquals("HOST_BACKUP_CLEANUP_FAILED", result.code)
        assertEquals("cleanup_recovery", result.failedStep)
        assertEquals("cleanup_recovery", result.retryableStep)
        assertTrue(result.hostSoRestored)
        assertTrue(result.suAuthRevoked)
        assertTrue(result.daemonStopped)
        assertFalse(root.scripts.any { it.contains(ParasiteMarkers.IDENTITY_GONE) })
    }

    @Test
    fun backupCleanupExceptionAndMissingMarkerRemainFailures() {
        listOf(true, false).forEach { throws ->
            val root = FakeRoot().apply {
                backupCleanupThrows = throws
                backupCleanupMarker = false
            }
            val result = deactivate(backend(ultraRoot = root))
            assertFalse("throws=$throws", result.success)
            assertEquals("HOST_BACKUP_CLEANUP_FAILED", result.code)
            assertNotNull(root.cleanupReceipt)
        }
    }

    @Test
    fun partialBackupCleanupRetriesAfterBackendRecreationWithoutReplayingCompletedActions() {
        val root = FakeRoot().apply {
            backupCleanupExitCode = 1
            partialBackupCleanup = true
        }
        val auth = FakeSuAuth().apply { granted += host }
        val state = FakeState(true)
        val first = deactivate(backend(ultraRoot = root, suAuth = auth, state = state))
        assertFalse(first.success)
        assertNotNull(root.cleanupReceipt)
        val restores = root.scripts.count { it.contains(ParasiteMarkers.RESTORE_OK) }
        val stops = root.scripts.count { it.contains(ParasiteMarkers.STOPPED) }
        val revokes = auth.revokes.size
        root.backupCleanupExitCode = 0
        root.partialBackupCleanup = false
        val second = deactivate(backend(ultraRoot = root, suAuth = auth, state = state))
        assertTrue(second.steps.joinToString { it.detail }, second.success)
        assertTrue(second.hostSoRestored)
        assertTrue(second.suAuthRevoked)
        assertTrue(second.daemonStopped)
        assertNull(root.cleanupReceipt)
        assertEquals(restores, root.scripts.count { it.contains(ParasiteMarkers.RESTORE_OK) })
        assertEquals(stops, root.scripts.count { it.contains(ParasiteMarkers.STOPPED) })
        assertEquals(revokes, auth.revokes.size)
    }

    @Test
    fun identityCleanupFailureKeepsCheckpointUntilCleanupOnlyRetryCompletes() {
        val root = FakeRoot().apply { identityCleanupExitCode = 86 }
        val auth = FakeSuAuth().apply { granted += host }
        val state = FakeState(true)
        val first = deactivate(backend(ultraRoot = root, suAuth = auth, state = state))
        assertFalse(first.success)
        assertEquals("RECOVERY_IDENTITY_CLEANUP_FAILED", first.code)
        assertNotNull(root.cleanupReceipt)
        assertFalse(root.hasBackupDir)
        val count = root.scripts.count { it.contains(ParasiteMarkers.RESTORE_OK) }
        root.identityCleanupExitCode = 0
        assertTrue(deactivate(backend(ultraRoot = root, suAuth = auth, state = state)).success)
        assertEquals(count, root.scripts.count { it.contains(ParasiteMarkers.RESTORE_OK) })
        assertEquals(1, auth.revokes.size)
        assertNull(root.cleanupReceipt)
    }

    @Test
    fun invalidOrOtherModeCleanupReceiptStopsBeforeRestorationAndAuthorization() {
        listOf("corrupt", "MAPLESU_CLEANUP_V1 mode=KSU host=$host restored=1",
            "MAPLESU_CLEANUP_V1 mode=LITE host=com.other.host restored=1").forEach { receipt ->
            val root = FakeRoot().apply { cleanupReceipt = receipt }
            val auth = FakeSuAuth().apply { granted += host }
            val result = deactivate(backend(ultraRoot = root, suAuth = auth))
            assertFalse(receipt, result.success)
            assertEquals("RECOVERY_CLEANUP_CHECKPOINT_FAILED", result.code)
            assertTrue(auth.revokes.isEmpty())
            assertFalse(root.scripts.any { it.contains(ParasiteMarkers.RESTORE_OK) })
            assertEquals(receipt, root.cleanupReceipt)
        }
    }

    @Test
    fun checkpointFailuresPreserveRetryMaterialAndCompletedFlags() {
        listOf(true, false).forEach { writeFails ->
            val root = FakeRoot().apply {
                checkpointWriteFails = writeFails
                checkpointFinishFails = !writeFails
            }
            val result = deactivate(backend(ultraRoot = root))
            assertFalse(result.success)
            assertEquals("RECOVERY_CLEANUP_CHECKPOINT_FAILED", result.code)
            assertTrue(result.hostSoRestored && result.suAuthRevoked && result.daemonStopped)
            if (writeFails) assertFalse(root.scripts.any { it == ParasiteScripts.removeBackup(host) })
            else assertNotNull(root.cleanupReceipt)
        }
    }

    @Test
    fun unreadableCheckpointCannotReplayRestoreOrAuthorization() {
        val root = FakeRoot().apply { checkpointReadExitCode = 88 }
        val auth = FakeSuAuth().apply { granted += host }
        val result = deactivate(backend(ultraRoot = root, suAuth = auth))
        assertFalse(result.success)
        assertEquals("RECOVERY_CLEANUP_CHECKPOINT_FAILED", result.code)
        assertFalse(result.hostSoRestored || result.suAuthRevoked || result.daemonStopped)
        assertTrue(auth.revokes.isEmpty())
        assertFalse(root.scripts.any { it.contains(ParasiteMarkers.RESTORE_OK) })
    }

    @Test
    fun pendingCleanupBlocksAnotherHostsActivationBeforeAnyInjectionOrIdentityWrite() {
        val root = FakeRoot().apply {
            cleanupReceipt = "MAPLESU_CLEANUP_V1 mode=LITE host=$host restored=1"
        }
        val other = "com.other.host"
        var prepareCalls = 0
        val b = backend(ultraRoot = root, hostFacts = FakeHostFacts(HostFactsLookup.Ok(facts(packageName = other))),
            prepareBoot = { prepareCalls++; true })
        val result = runBlocking { b.activate(other, port) }
        assertEquals("pending cleanup owns shared recovery identity before boot preparation", 0, prepareCalls)
        assertFalse(result.success)
        assertEquals(ParasiteCodes.HOST_BACKUP_STALE, result.code)
        assertFalse(root.scripts.any { it.contains("HostSoPatchMain") || it.contains(ParasiteMarkers.IDENTITY_OK) })
        assertNotNull(root.cleanupReceipt)
    }

    @Test
    fun identityCleanupExceptionAndMissingMarkerRetainCheckpoint() {
        listOf(true, false).forEach { throws ->
            val root = FakeRoot().apply { identityCleanupThrows = throws; identityCleanupMarker = false }
            val result = deactivate(backend(ultraRoot = root))
            assertFalse(result.success)
            assertEquals("RECOVERY_IDENTITY_CLEANUP_FAILED", result.code)
            assertNotNull(root.cleanupReceipt)
        }
    }

    @Test
    fun lostFinalAcknowledgementRechecksAbsentBackupAndGrantWithoutReapplyingThem() {
        val root = FakeRoot().apply { checkpointFinishLosesAcknowledgement = true }
        val auth = FakeSuAuth().apply { granted += host }
        val state = FakeState(true)
        val first = deactivate(backend(ultraRoot = root, suAuth = auth, state = state))
        assertFalse(first.success)
        assertTrue(first.hostSoRestored && first.suAuthRevoked && first.daemonStopped)
        assertNull(root.cleanupReceipt)
        val restores = root.scripts.count { it.contains(ParasiteMarkers.RESTORE_OK) }
        root.checkpointFinishLosesAcknowledgement = false
        val second = deactivate(backend(ultraRoot = root, suAuth = auth, state = state))
        assertTrue(second.success)
        assertFalse(second.hostSoRestored)
        assertEquals("no_backup", second.steps[0].detail)
        assertEquals(restores, root.scripts.count { it.contains(ParasiteMarkers.RESTORE_OK) })
        assertEquals(1, auth.revokes.size)
    }

    @Test
    fun successfulDeactivationRemovesGuardMaterialBeforeFreshActivation() {
        val root = FakeRoot().apply { staleBackup = true }
        val state = FakeState(true)
        val auth = FakeSuAuth().apply { granted += host }
        val b = backend(ultraRoot = root, state = state, suAuth = auth)
        assertTrue(deactivate(b).success)
        assertFalse(root.hasBackupDir)
        assertFalse(root.staleBackup)
        assertNull(root.cleanupReceipt)
        assertTrue(activate(b).success)
    }

    @Test
    fun deactivateWithAlreadyAbsentHostGrantCompletesAfterLibraryRestoration() {
        val root = FakeRoot()
        val state = FakeState(value = true)
        val authorizations = dev.maplesu.manager.runtime.ultra.UltraAuthorizationState()
        var removeCalls = 0
        val port = object : dev.maplesu.manager.runtime.ultra.SuAuthPort {
            override fun snapshot() = listOf("dev.maplesu.manager")
            override fun add(packageName: String) = error("deactivation must not grant")
            override fun remove(packageName: String): dev.maplesu.manager.runtime.ultra.NativeReceipt {
                removeCalls++
                return dev.maplesu.manager.runtime.ultra.NativeReceipt.bridgeFailure(
                    "remove_su_auth", "ERR_MODULE_SU_LIST_NOT_FOUND", null,
                )
            }
        }
        val auth = object : HiddenSuAuth {
            override fun grant(packageName: String) = error("deactivation must not grant")
            override fun revoke(packageName: String): SuAuthOutcome {
                val attempt = authorizations.setGranted(port, packageName, false)
                return if (attempt.succeeded) SuAuthOutcome.Revoked else SuAuthOutcome.Failed(attempt.error.orEmpty())
            }
            override fun isGranted(packageName: String) = packageName in authorizations.snapshot.packages
        }
        val result = deactivate(backend(ultraRoot = root, state = state, suAuth = auth))
        assertTrue(result.steps.joinToString { "${it.step}:${it.ok}:${it.detail}" }, result.success)
        assertTrue(result.hostSoRestored)
        assertTrue(result.suAuthRevoked)
        assertTrue(result.daemonStopped)
        assertEquals(0, removeCalls)
        assertEquals(false, state.value)
        assertTrue(root.scripts.any { it.contains(ParasiteMarkers.STOPPED) })
    }

    @Test
    fun deactivateRemovesIdentityOnlyAfterSuccess() {
        val root = FakeRoot()
        val b = backend(ultraRoot = root, state = FakeState(value = true))
        assertTrue(deactivate(b).success)
        assertTrue(
            "解除成功后必须清掉身份材料（next 激活会重新写）",
            root.scripts.any { it.contains(ParasiteMarkers.IDENTITY_GONE) },
        )
    }

    @Test
    fun deactivateFailureKeepsIdentityForRetry() {
        val root = FakeRoot()
        val suAuth = FakeSuAuth(revokeOutcome = SuAuthOutcome.Failed("remove_su_auth")).apply { granted += host }
        val result = deactivate(backend(ultraRoot = root, state = FakeState(value = true), suAuth = suAuth))

        assertFalse(result.success)
        assertFalse(
            "撤销失败必须保留身份材料（重试还要用它解 rootkey）",
            root.scripts.any { it.contains(ParasiteMarkers.IDENTITY_GONE) },
        )
    }

    @Test
    fun unreadableRevocationReadbackRetainsRecoveryForRetry() {
        val root = FakeRoot()
        val suAuth = FakeSuAuth().apply { unreadable = true; granted += host }
        val result = deactivate(backend(ultraRoot = root, state = FakeState(value = true), suAuth = suAuth))
        assertFalse(result.success)
        assertFalse(result.suAuthRevoked)
        assertFalse(result.daemonStopped)
        assertFalse(root.scripts.any { it.contains(ParasiteMarkers.IDENTITY_GONE) })
    }

    // 25
    @Test
    fun deactivateWithoutBackupSucceedsButDoesNotClaimRestoration() {        val root = FakeRoot().apply { hasBackupDir = false }
        val state = FakeState(value = true)
        val b = backend(ultraRoot = root, state = state)
        val result = deactivate(b)
        assertTrue(result.success)
        assertFalse("没有备份就不能声称复原过宿主 so", result.hostSoRestored)
        assertEquals("no_backup", result.steps[0].detail)
        assertEquals(false, state.value)
    }

    // 26
    @Test
    fun deactivateRestoreFailureRetainsAuthorizationAndRecoveryForRetry() {
        val root = FakeRoot().apply { restoreFails = true }
        val state = FakeState(value = true)
        val suAuth = FakeSuAuth().apply { granted += host }
        val b = backend(ultraRoot = root, state = state, suAuth = suAuth)
        val result = deactivate(b)
        assertFalse(result.success)
        assertEquals("HOST_SO_RESTORE_FAILED", result.code)
        assertFalse(result.hostSoRestored)
        assertFalse(result.suAuthRevoked)
        assertFalse(result.daemonStopped)
        assertEquals(listOf(false), result.steps.map { it.ok })
        assertTrue(host in suAuth.granted)
        assertEquals(true, state.value)
        assertFalse(root.scripts.any { it.contains(ParasiteMarkers.STOPPED) })
    }

    // 27
    @Test
    fun deactivateSuRevokeFailureIsReported() {
        val root = FakeRoot()
        val state = FakeState(value = true)
        val suAuth = FakeSuAuth(revokeOutcome = SuAuthOutcome.Failed("remove_su_auth")).apply { granted += host }
        val b = backend(ultraRoot = root, state = state, suAuth = suAuth)
        val result = deactivate(b)
        assertFalse(result.success)
        assertEquals("SU_AUTH_REVOKE_FAILED", result.code)
        assertEquals(HiddenFailure.DEACTIVATE_FAILED, result.failure)
        assertFalse(result.suAuthRevoked)
        assertTrue(result.hostSoRestored)
        assertFalse(result.daemonStopped)
        assertEquals(true, state.value)
        assertFalse(root.scripts.any { it.contains(ParasiteMarkers.STOPPED) })
    }

    // 28
    @Test
    fun deactivateDaemonStopFailureIsReported() {
        val root = FakeRoot().apply { stopFails = true }
        val state = FakeState(value = true)
        val b = backend(ultraRoot = root, state = state)
        val result = deactivate(b)
        assertFalse(result.success)
        assertEquals("LITEWEB_DAEMON_STOP_FAILED", result.code)
        assertFalse(result.daemonStopped)
        assertEquals(true, state.value)
    }

    @Test
    fun deactivateCannotStopRecoveryBeforeHiddenOffIsVerified() {
        val root = FakeRoot()
        val state = FakeState(value = true).apply { failWrite = true }
        val result = deactivate(backend(ultraRoot = root, state = state))
        assertFalse(result.success)
        assertEquals(ParasiteCodes.HIDDEN_STATE_WRITE_FAILED, result.code)
        assertFalse(root.scripts.any { it.contains(ParasiteMarkers.STOPPED) })
    }

    // 28b
    @Test
    fun deactivateDaemonStopFailureIsExplicitAndRetryableWithoutRollingBackRestore() {
        // 真机缺陷 1：旧脚本无条件 echo STOPPED → 宿主库还原成功就整体报成功。
        // 现在停 daemon 失败 = 总体失败，但**已经完成的宿主还原保留**（不回滚，可只重试停 daemon）。
        val root = FakeRoot().apply { stopFails = true }
        val state = FakeState(value = true)
        val suAuth = FakeSuAuth().apply { granted += host }
        val b = backend(ultraRoot = root, state = state, suAuth = suAuth)
        val result = deactivate(b)
        assertFalse(result.success)
        assertEquals("LITEWEB_DAEMON_STOP_FAILED", result.code)
        assertEquals("stop_daemon", result.failedStep)
        assertEquals("stop_daemon", result.retryableStep)
        assertTrue("宿主库还原不能被回滚", result.hostSoRestored)
        assertTrue(result.suAuthRevoked)
        assertFalse(result.daemonStopped)
        assertTrue(root.scripts.any { it.contains("PARASITE_RESTORE_OK") })
        assertFalse(
            "停 daemon 失败不能反过来把宿主库写回去",
            root.scripts.count { it.contains("PARASITE_RESTORE_OK") } > 1,
        )
    }

    // 28c
    @Test
    fun deactivateDaemonStopRequiresZeroExitEvenWithStoppedMarker() {
        // 脚本自证失败会 exit 1；即使输出里残留 STOPPED 字样，也必须按失败处理。
        val root = FakeRoot().apply { stopExitCode = 1 }
        val state = FakeState(value = true)
        val b = backend(ultraRoot = root, state = state)
        val result = deactivate(b)
        assertFalse(result.success)
        assertEquals("LITEWEB_DAEMON_STOP_FAILED", result.code)
        assertEquals("stop_daemon", result.retryableStep)
    }

    // ── 模式隔离 / 契约 ───────────────────────────────────────────────────

    // 29
    @Test
    fun modeIsolationUsesOnlyTheActiveModesChannel() {
        val ultraRoot = FakeRoot()
        val ksuRoot = FakeRoot()
        val b = backend(mode = WorkMode.LITE, ultraRoot = ultraRoot, ksuRoot = ksuRoot)
        assertTrue(activate(b).success)
        assertTrue(ultraRoot.scripts.isNotEmpty())
        assertTrue("LITE 绝不能碰 KSU 通道", ksuRoot.scripts.isEmpty())
    }

    // 30
    @Test
    fun ksuModeUsesRouterChannelAndPolicyManagedSuAuth() {
        val ultraRoot = FakeRoot()
        val ksuRoot = FakeRoot()
        val b = backend(mode = WorkMode.KSU, ultraRoot = ultraRoot, ksuRoot = ksuRoot)
        val result = activate(b)
        assertTrue(result.success)
        assertTrue(ksuRoot.scripts.isNotEmpty())
        assertTrue("KSU 绝不能碰 Ultra 通道", ultraRoot.scripts.isEmpty())
        assertTrue(result.steps[1].detail.contains("su_auth=policy_managed"))
    }

    // 31
    @Test
    fun unresolvedModeFailsClosedWithoutTouchingEitherChannel() {
        val ultraRoot = FakeRoot()
        val ksuRoot = FakeRoot()
        val b = backend(mode = null, ultraRoot = ultraRoot, ksuRoot = ksuRoot)
        val result = activate(b)
        assertFalse(result.success)
        assertEquals(HiddenFailure.BACKEND_UNAVAILABLE.wire, result.code)
        assertTrue(ultraRoot.scripts.isEmpty())
        assertTrue(ksuRoot.scripts.isEmpty())
    }

    // 32
    @Test
    fun deactivateFailsClosedWhenModeUnresolved() {
        val b = backend(mode = null)
        val result = deactivate(b)
        assertFalse(result.success)
        assertEquals(HiddenFailure.BACKEND_UNAVAILABLE.wire, result.code)
    }

    // 33
    @Test
    fun legacyEnableHiddenWithoutHostIsHostNotSelected() {
        val b = backend()
        val result = runBlocking { b.enableHidden(port) }
        assertFalse(result.success)
        assertEquals(HiddenFailure.HOST_NOT_SELECTED, result.failure)
    }

    // 34
    @Test
    fun legacyEnableHiddenAfterActivationDelegatesToTransaction() {
        val b = backend()
        assertTrue(activate(b).success)
        val result = runBlocking { b.enableHidden(port) }
        assertTrue(result.success)
        assertTrue(result.recoveryUrl.startsWith("http://127.0.0.1:$port/x7k9f/"))
    }

    // 35
    @Test
    fun secondActivationIsBlockedByStaleBackupGuard() {
        val root = FakeRoot()
        val b = backend(ultraRoot = root)
        assertTrue(activate(b).success)
        root.staleBackup = true
        val second = activate(b)
        assertFalse(second.success)
        assertEquals("HOST_BACKUP_STALE", second.code)
    }

    // 36
    @Test
    fun tokenIsNeverLogged() {
        val messages = mutableListOf<String>()
        val b = backend(logger = { messages += it })
        assertTrue(activate(b).success)
        assertTrue(messages.none { it.contains(token) })
    }

    // ── 纯策略/编解码 ─────────────────────────────────────────────────────

    // 37
    @Test
    fun policyRejectsTooManyNativeLibraries() {
        val many = (1..(ParasitePolicy.MAX_TARGETS + 1)).map { "lib$it.so" }
        val validation = ParasitePolicy.validate(facts(libs = many))
        assertTrue(validation is HostValidation.Rejected)
        assertEquals("HOST_UNSUPPORTED", (validation as HostValidation.Rejected).code)
        assertTrue(validation.detail.startsWith("too_many_libs="))
    }

    // 38
    @Test
    fun policySortsAndFiltersTargets() {
        val validation = ParasitePolicy.validate(
            facts(libs = listOf("libz.so", "liba.so", "not-a-lib.txt", "liba.so")),
        )
        assertTrue(validation is HostValidation.Ok)
        assertEquals(listOf("liba.so", "libz.so"), (validation as HostValidation.Ok).targets)
    }

    // 39
    @Test
    fun manifestRoundTripsAndRejectsCorruption() {
        val manifest = HostManifest(
            packageName = host,
            libraryDir = libDir,
            label = label,
            loaderName = ParasitePaths.LOADER_NAME,
            loaderSha256 = "b".repeat(64),
            files = listOf(HostBackupFile("liba.so", "c".repeat(64), "d".repeat(64), "755", 1000, 1000)),
        )
        val decoded = HostManifestCodec.decode(HostManifestCodec.encode(manifest))
        assertEquals(manifest, decoded)
        assertNull(HostManifestCodec.decode("schema=2\n"))
        assertNull(HostManifestCodec.decode("schema=1\npackage=$host\n"))
        assertNull(HostManifestCodec.decode(HostManifestCodec.encode(manifest).replace("schema=1", "schema=9")))
    }

    // 40
    @Test
    fun scriptsUseDirectoryLabelAndPidFileInsteadOfPkill() {
        val backup = ParasiteScripts.backup(facts(), targets)
        assertTrue("label 必须取目录自身（spike §8.3）", backup.contains("ls -Zd"))
        assertTrue(backup.contains("PARASITE_BACKUP_OK"))
        val stop = ParasiteScripts.daemonStop()
        assertTrue(stop.contains("liteweb.pid"))
        assertFalse(stop.contains("pkill"))
        val inject = ParasiteScripts.inject(facts(), targets, apk.absolutePath, loader.absolutePath, label)
        assertTrue(inject.contains("/system/bin/app_process"))
        assertTrue(inject.contains("CLASSPATH="))
    }

    // 41
    @Test
    fun daemonStartScriptIsIdempotentThroughPidFile() {
        val script = ParasiteScripts.daemonStart()
        assertTrue(script.contains("liteweb.pid"))
        assertTrue(script.contains("PARASITE_ALIVE"))
        assertTrue(script.contains("kill -0"))
        assertFalse(script.contains("pkill"))
        val startScript = ParasiteScripts.writeStartScript()
        assertTrue(startScript.contains("parasite-start.sh"))
        assertTrue(startScript.contains("chmod 700"))
    }

    // 42
    @Test
    fun recoveryUrlShapeMatchesUiTokenExtraction() {
        val url = ParasitePaths.webUiUrl(18427, token)
        assertEquals("http://127.0.0.1:18427/x7k9f/$token/", url)
        assertNotNull(Regex("/x7k9f/([0-9a-fA-F]+)").find(url))
    }
}
