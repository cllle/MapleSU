// SPDX-License-Identifier: GPL-3.0-or-later
package dev.maplesu.manager.workmode.backend

import dev.maplesu.manager.ksu.KsuBootstrapResult
import dev.maplesu.manager.ksu.KsuCommandBackend
import dev.maplesu.manager.ksu.KsuModuleOps
import dev.maplesu.manager.ksu.KsuRootShell
import dev.maplesu.manager.ksu.KsuRootShellExecutor
import dev.maplesu.manager.ksu.KsuSettingsFeature
import dev.maplesu.manager.ksu.KsuShellResult
import dev.maplesu.manager.ksu.KsuShellRunner
import dev.maplesu.manager.ksu.KsuPathProbe
import dev.maplesu.manager.ksu.KsuSuperuserAdapter
import com.maplesu.core.data.repository.ModuleRepositoryImpl
import dev.maplesu.manager.modules.ModuleListParser
import dev.maplesu.manager.modules.ModuleBackendPorts
import dev.maplesu.manager.runtime.RootBackend
import dev.maplesu.manager.runtime.RootExec
import dev.maplesu.manager.runtime.RootExecResult
import dev.maplesu.manager.runtime.RootExecutor
import dev.maplesu.manager.workmode.service.SwitchOutcome
import dev.maplesu.manager.workmode.ui.WorkModeSwitchSession
import dev.maplesu.manager.workmode.WorkMode
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Normal（KSU）管理侧接线（2026-11-01 收尾）。
 *
 * 覆盖审计 §D 的第 2/3 条：
 * - `ModuleBackendPorts.ksuFactory` 被真正注入（模块仓库的 KSU 分支不再是
 *   `Unavailable`）；
 * - `RootExec` 在 KSU 下拿到 KSU executor（`RootShellControlPlane`/`ModuleTree` 等
 *   既有调用点零改动可用）；
 * - 设置 feature 面被注入，且 `set` 之后**必须** `save()`（否则重启即丢）；
 * - 非 KSU 模式显式 Skipped，绝不"顺手接线"；
 * - 失败显式（错误码 + lastError），不伪造可用。
 */
class KsuBackendRegistryTest {

    @After
    fun tearDown() {
        KsuBackendRegistry.resetForTest()
        WorkModeSwitchSession.finish(SwitchOutcome.ImagesRequired(WorkMode.KSU, false))
    }

    private fun installCommand(command: List<String>): List<String> {
        assertEquals(6, command.size)
        assertEquals("/system/bin/env", command[0])
        assertTrue(command[1].matches(Regex("KSUD_MODULE_LIVE_EVENT=[0-9a-f]{32}")))
        return command.drop(2)
    }

    // ── fakes ───────────────────────────────────────────────────────────

    private class FakeRootExecutor(
        private val available: Boolean = true,
    ) : RootExecutor {
        override val backend: RootBackend = RootBackend.KSU
        var calls = 0
        var lastScript: String? = null

        override fun available(): Boolean = available

        override fun run(script: String): RootExecResult {
            calls++
            lastScript = script
            return RootExecResult(RootBackend.KSU, 0, "ksu:$script")
        }
    }

    private class FakeSettingsFeature(
        private val setResult: Boolean = true,
    ) : KsuSettingsFeature {
        val calls = mutableListOf<String>()
        var status = "supported"
        var value: Long? = 1L
        var persist: Long? = 1L

        override fun status(feature: String): String {
            calls += "status:$feature"
            return status
        }

        override fun value(feature: String): Long? {
            calls += "value:$feature"
            return value
        }

        override fun persistValue(feature: String): Long? {
            calls += "persist:$feature"
            return persist
        }

        override fun set(feature: String, enabled: Boolean): Boolean {
            calls += "set:$feature=$enabled"
            return setResult
        }

        override fun save(): Boolean {
            calls += "save"
            return true
        }
    }

    private class FakeSuperuser(
        private val allowSu: Boolean? = true,
        private val available: Boolean = true,
    ) : KsuSuperuserAdapter {
        val writes = mutableListOf<String>()

        override fun available(): Boolean = available

        override fun count(): Int = 0

        override fun getProfile(
            packageName: String,
            uid: Int,
        ): com.maplesu.core.Natives.Profile? = allowSu?.let {
            // `context` 显式传入：默认值会走 NativesFacade.kernelDomainForProfile()，
            // 主机单测里不需要为这条默认值承担任何后端加载风险。
            com.maplesu.core.Natives.Profile(
                name = packageName,
                currentUid = uid,
                allowSu = it,
                context = "u:r:ksu:s0",
            )
        }

        override fun setAllow(packageName: String, uid: Int, allow: Boolean): Boolean {
            writes += "$packageName:$uid=$allow"
            return true
        }

        override fun allowListFile(): File = File("/data/adb/ksu/.allowlist")
    }

    private class FakePort(
        private val wired: KsuBackendRegistry.Wired,
        private val deploy: KsuBootstrapResult = deployed(),
        private val throwOnWire: Throwable? = null,
    ) : KsuBackendRegistry.WiringPort {
        var deployCalls = 0

        override fun wire(): KsuBackendRegistry.Wired {
            throwOnWire?.let { throw it }
            return wired
        }

        override fun deploy(): KsuBootstrapResult {
            deployCalls++
            return deploy
        }
    }

    private fun moduleOps(
        commands: MutableList<String>,
        daemonExists: Boolean = true,
        result: KsuShellResult = KsuShellResult(
            0, listOf("""[{"id":"m1","name":"M1","enabled":"true"}]"""), emptyList(),
        ),
        argv: MutableList<List<String>>? = null,
    ): KsuModuleOps {
        val backend = KsuCommandBackend(
            daemonPathProvider = { "/data/adb/ksud" },
            runner = { command, _ ->
                commands += command.joinToString(" ")
                argv?.add(command)
                result
            },
            probe = object : dev.maplesu.manager.ksu.KsuPathProbe {
                override fun exists(path: String): Boolean = daemonExists
                override fun canExecute(path: String): Boolean = daemonExists
            },
        )
        return KsuModuleOps(backend)
    }

    private fun wired(
        commands: MutableList<String> = mutableListOf(),
        settings: KsuSettingsFeature? = FakeSettingsFeature(),
        superuser: KsuSuperuserAdapter? = FakeSuperuser(),
        root: RootExecutor? = FakeRootExecutor(),
        moduleOps: KsuModuleOps? = moduleOps(commands),
        rootShell: KsuRootShellExecutor? = KsuRootShellExecutor { line ->
            commands += line
            KsuShellResult(0, listOf("ok"), emptyList())
        },
    ) = KsuBackendRegistry.Wired(
        rootExecutor = root,
        moduleOps = moduleOps,
        command = null,
        settingsFeature = settings,
        superuser = superuser,
        rootShellExecutor = rootShell,
        backendSet = null,
    )

    // ── 接线：KSU 分支真的被注入 ────────────────────────────────────────

    @Test
    fun ksuInstallInjectsModuleFactoryRootExecutorAndSettings() {
        val settings = FakeSettingsFeature()
        val commands = mutableListOf<String>()
        val result = KsuBackendRegistry.install(
            WorkMode.KSU,
            FakePort(wired(commands = commands, settings = settings)),
        )
        assertTrue(result is KsuBackendRegistry.InstallResult.Wired)
        assertTrue(KsuBackendRegistry.isWired())
        assertEquals(WorkMode.KSU, KsuBackendRegistry.wiredMode())

        // 1) 模块仓库的 KSU 分支现在有端口（不再是 "ksu backend not wired"）
        val factory = ModuleBackendPorts.ksuFactory
        assertNotNull("ModuleBackendPorts.ksuFactory must be injected", factory)
        val port = factory?.invoke()
        assertNotNull(port)
        assertTrue(port!!.isAvailable())

        // 2) RootExec 在 KSU 下拿到 KSU executor（既有调用点无需改动）
        val ksuExecutor = RootExec.ksuExecutor()
        assertNotNull("RootExec must carry the KSU executor", ksuExecutor)
        assertEquals(RootBackend.KSU, ksuExecutor?.backend)

        // 3) 设置 feature 面注入
        assertNotNull(KsuBackendRegistry.featurePort())
        assertEquals("supported", KsuBackendRegistry.featurePort()!!.status("sulog"))
    }

    @Test
    fun ksuModuleListRoutesThroughInjectedPort() {
        val commands = mutableListOf<String>()
        KsuBackendRegistry.install(
            WorkMode.KSU,
            FakePort(wired(commands = commands)),
        )
        val json = KsuBackendRegistry.moduleListJson()
        assertNotNull(json)
        assertTrue(json!!.contains("\"id\":\"m1\""))
        assertEquals(listOf("/data/adb/ksud module list"), commands)
        for (receipt in listOf(
            KsuShellResult(7, listOf("partial"), listOf("list failed")),
            KsuShellResult(0, listOf("{}"), emptyList()),
        )) {
            KsuBackendRegistry.install(WorkMode.KSU,
                FakePort(wired(moduleOps = moduleOps(mutableListOf(), result = receipt))))
            val direct = org.junit.Assert.assertThrows(dev.maplesu.manager.ksu.KsuModuleListException::class.java) {
                KsuBackendRegistry.moduleListJson()
            }
            org.junit.Assert.assertSame(receipt, direct.receipt)
            val adapter = requireNotNull(requireNotNull(ModuleBackendPorts.ksuFactory).invoke())
            val adapted = org.junit.Assert.assertThrows(dev.maplesu.manager.ksu.KsuModuleListException::class.java) {
                adapter.listJson()
            }
            org.junit.Assert.assertSame(receipt, adapted.receipt)
        }
        KsuBackendRegistry.install(WorkMode.KSU,
            FakePort(wired(moduleOps = moduleOps(mutableListOf(),
                result = KsuShellResult(0, listOf("[]"), emptyList())))))
        assertEquals("[]", KsuBackendRegistry.moduleListJson())
        assertEquals("[]", requireNotNull(requireNotNull(ModuleBackendPorts.ksuFactory).invoke()).listJson())
    }

    @Test
    fun normalInstallInvalidatesAListStartedBeforeItsSuccessOrFailure() = runBlocking {
        for (exitCode in listOf(0, 7)) {
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val version = AtomicInteger(1)
            val reads = AtomicInteger()
            val backend = KsuCommandBackend(
                daemonPathProvider = { "/data/adb/ksud" },
                runner = KsuShellRunner { command, _ ->
                    val argv = if (command.firstOrNull() == "/system/bin/env") installCommand(command) else command
                    when (argv.drop(1).take(2)) {
                        listOf("module", "list") -> {
                            val observed = version.get()
                            if (reads.incrementAndGet() == 1) {
                                entered.countDown()
                                check(release.await(3, TimeUnit.SECONDS))
                            }
                            KsuShellResult(0, listOf("""[{"id":"fixture","versionCode":"$observed"}]"""), emptyList())
                        }
                        listOf("module", "install") -> {
                            version.set(2)
                            KsuShellResult(exitCode, emptyList(), emptyList())
                        }
                        else -> error("unexpected command: $command")
                    }
                },
                probe = object : KsuPathProbe { override fun exists(path: String) = true },
            )
            KsuBackendRegistry.install(WorkMode.KSU,
                FakePort(wired(moduleOps = KsuModuleOps(backend))))
            val projected = mutableListOf<String>()
            val repository = ModuleRepositoryImpl(mode = { WorkMode.KSU },
                listParser = ModuleListParser { json -> projected += json; emptyList() })
            val pending = async { repository.getModules() }
            val zip = File.createTempFile("normal-race-", ".zip")
            try {
                withContext(Dispatchers.IO) { assertTrue(entered.await(2, TimeUnit.SECONDS)) }
                assertEquals(exitCode, KsuBackendRegistry.installModuleResult(zip)?.code)
                release.countDown()
                assertTrue(pending.await().isSuccess)
                assertTrue("exitCode=$exitCode: ${projected.last()}",
                    projected.last().contains("\"versionCode\":2"))
                assertEquals(2, reads.get())
            } finally {
                release.countDown()
                zip.delete()
            }
        }
    }

    @Test
    fun ksuModuleInstallAndActionUseKsud() {
        val commands = mutableListOf<String>()
        val zip = File.createTempFile("module-", ".zip").apply { writeText("zip") }
        try {
            KsuBackendRegistry.install(WorkMode.KSU, FakePort(wired(commands = commands)))
            assertEquals(true, KsuBackendRegistry.installModule(zip))
            assertTrue(commands.any { it.contains("module install") && it.contains(zip.absolutePath) })

            commands.clear()
            val stdout = mutableListOf<String>()
            val stderr = mutableListOf<String>()
            assertEquals(
                true,
                KsuBackendRegistry.runModuleAction("m1", { msg: String -> stdout.add(msg) }, { msg: String ->
                    stderr.add(msg)
                }),
            )
            // 开发文档 §10.3 D3：上游形态是 `module action <id>`，`-g` 只属于 `debug su`。
            assertTrue(commands.any { it == "/data/adb/ksud module action m1" })
            assertFalse("module action 不得携带 -g", commands.any { it.contains(" -g") })
            // 开发文档 §10.3 D4：真实 stdout 透传，不再伪造 MODULE_ACTION_STARTED。
            assertTrue("真实 stdout 必须透传", stdout.any { it.contains("\"id\":\"m1\"") })
            assertFalse(stdout.any { it.startsWith("MODULE_ACTION_STARTED:") })
            assertTrue(stderr.isEmpty())
        } finally {
            zip.delete()
        }
    }

    @Test
    fun moduleInstallRetainsArgvAndActualReceipt() {
        val argv = mutableListOf<List<String>>()
        val zip = File.createTempFile("module with spaces-", ".zip")
        val receipt = KsuShellResult(7, listOf("installing", "", "last line"), listOf("failure", ""))
        try {
            KsuBackendRegistry.install(
                WorkMode.KSU,
                FakePort(wired(moduleOps = moduleOps(mutableListOf(), result = receipt, argv = argv))),
            )
            val result = KsuBackendRegistry.installModuleResult(zip)
            assertNotNull(result)
            assertEquals(7, result!!.code)
            assertEquals(receipt.out, result.out)
            assertEquals(receipt.err, result.err)
            assertEquals(listOf("/data/adb/ksud", "module", "install", zip.absolutePath), installCommand(argv.single()))

            argv.clear()
            assertEquals(false, KsuBackendRegistry.installModule(zip))
            assertEquals("Boolean compatibility executes the command once", 1, argv.size)
        } finally {
            zip.delete()
        }
    }

    @Test
    fun detailedModuleInstallExplainsMissingDaemon() {
        val zip = File.createTempFile("module-", ".zip")
        try {
            KsuBackendRegistry.install(
                WorkMode.KSU,
                FakePort(wired(moduleOps = moduleOps(mutableListOf(), daemonExists = false))),
            )
            val result = KsuBackendRegistry.installModuleResult(zip)
            assertEquals(-1, result?.code)
            assertEquals(listOf("KSUD_UNAVAILABLE:/data/adb/ksud"), result?.err)
            assertNull("Boolean compatibility retains the unavailable state", KsuBackendRegistry.installModule(zip))
        } finally {
            zip.delete()
        }
    }

    @Test
    fun detailedModuleInstallIsNullWhenUnwired() {
        KsuBackendRegistry.resetForTest()
        assertNull(KsuBackendRegistry.installModuleResult(File("module.zip")))
    }

    @Test
    fun settingsPortSavesAfterEverySet() {
        val settings = FakeSettingsFeature()
        KsuBackendRegistry.install(WorkMode.KSU, FakePort(wired(settings = settings)))
        val port = KsuBackendRegistry.featurePort()!!
        assertTrue(port.set("sulog", true))
        // 只 set 不 save 会在重启后丢（审计 §7）→ 必须成对。
        assertEquals(listOf("set:sulog=true", "save"), settings.calls)
    }

    @Test
    fun settingsPortDoesNotSaveWhenSetFails() {
        val settings = FakeSettingsFeature(setResult = false)
        KsuBackendRegistry.install(WorkMode.KSU, FakePort(wired(settings = settings)))
        assertFalse(KsuBackendRegistry.featurePort()!!.set("sulog", true))
        assertEquals(listOf("set:sulog=true"), settings.calls)
    }

    @Test
    fun superuserAdapterIsExposedForAppProfile() {
        val superuser = FakeSuperuser(allowSu = true)
        KsuBackendRegistry.install(WorkMode.KSU, FakePort(wired(superuser = superuser)))
        assertEquals(true, KsuBackendRegistry.appProfileAllowSu("com.example", 10123))
        assertTrue(KsuBackendRegistry.setAppProfileAllowSu("com.example", 10123, false))
        assertEquals(listOf("com.example:10123=false"), superuser.writes)
    }

    // ── 非 KSU：显式跳过 ──────────────────────────────────────────────

    @Test
    fun liteAndUnresolvedModesAreExplicitlySkipped() {
        val port = FakePort(wired())
        assertEquals(
            KsuBackendRegistry.InstallResult.Skipped,
            KsuBackendRegistry.install(WorkMode.LITE, port),
        )
        assertEquals(
            KsuBackendRegistry.InstallResult.Skipped,
            KsuBackendRegistry.install(null, port),
        )
        assertFalse(KsuBackendRegistry.isWired())
        assertNull(ModuleBackendPorts.ksuFactory)
        assertNull(RootExec.ksuExecutor())
        assertNull(KsuBackendRegistry.featurePort())
    }

    // ── 失败：显式，不伪造 ────────────────────────────────────────────

    @Test
    fun missingPortFailsWithCode() {
        val result = KsuBackendRegistry.install(WorkMode.KSU, null)
        assertTrue(result is KsuBackendRegistry.InstallResult.Failed)
        assertEquals(
            KsuBackendRegistry.CODE_WIRING_UNAVAILABLE,
            (result as KsuBackendRegistry.InstallResult.Failed).code,
        )
        assertFalse(KsuBackendRegistry.isWired())
        assertNotNull(KsuBackendRegistry.lastError())
    }

    @Test
    fun wiringExceptionFailsWithCodeAndLeavesNothingWired() {
        val result = KsuBackendRegistry.install(
            WorkMode.KSU,
            FakePort(wired(), throwOnWire = IllegalStateException("native missing")),
        )
        assertTrue(result is KsuBackendRegistry.InstallResult.Failed)
        assertEquals(
            KsuBackendRegistry.CODE_WIRING_FAILED,
            (result as KsuBackendRegistry.InstallResult.Failed).code,
        )
        assertFalse(KsuBackendRegistry.isWired())
        assertNull(ModuleBackendPorts.ksuFactory)
        assertNull(RootExec.ksuExecutor())
        assertTrue(KsuBackendRegistry.lastError()!!.contains("native missing"))
    }

    @Test
    fun unavailableModuleBackendIsNullNotFakeList() {
        KsuBackendRegistry.install(
            WorkMode.KSU,
            FakePort(wired(moduleOps = moduleOps(mutableListOf(), daemonExists = false))),
        )
        // ksud 不存在（未部署）：列表读不到就是读不到，不能返回空数组冒充"没有模块"。
        assertNull(KsuBackendRegistry.moduleListJson())
        assertNull(KsuBackendRegistry.installModule(File("x.zip")))
    }

    @Test
    fun rootShellSeamIsInstalledSoKsudRunsAsRoot() {
        val lines = mutableListOf<String>()
        KsuBackendRegistry.install(
            WorkMode.KSU,
            FakePort(
                wired(
                    rootShell = KsuRootShellExecutor { line ->
                        lines += line
                        KsuShellResult(0, listOf("ok"), emptyList())
                    },
                ),
            ),
        )
        assertTrue(KsuRootShell.isInstalled())
        val runner = KsuRootShell.runner()
        runner.run(listOf("/data/adb/ksud", "module", "list"), 1_000L)
        assertEquals(listOf("/data/adb/ksud module list"), lines)
    }

    @Test
    fun snapshotReportsExplicitFacts() {
        KsuBackendRegistry.install(WorkMode.KSU, FakePort(wired()))
        val snapshot = KsuBackendRegistry.snapshot()
        assertEquals("true", snapshot["wired"])
        assertEquals("KSU", snapshot["mode"])
        assertEquals("KSU", snapshot["rootExecutor"])
        assertEquals("true", snapshot["rootShell"])
        // 部署还没跑（install 不做部署；scheduleDeployment 才做）
        assertEquals("PENDING", snapshot["bootstrap"])
    }

    // ── 部署状态：显式 ────────────────────────────────────────────────

    @Test
    fun backgroundDeploymentDoesNotTreatHistoricalRecoveryAsAnActiveSwitch() {
        WorkModeSwitchSession.finish(SwitchOutcome.AwaitingReboot("old-switch", null))
        assertTrue(WorkModeSwitchSession.pending)
        assertEquals("echo DEPLOYED", KsuBackendRegistry.backgroundDeployCommand("echo DEPLOYED"))
    }

    @Test
    fun backgroundDeploymentWaitsOnlyWhileASwitchIsExecuting() {
        WorkModeSwitchSession.begin()
        val error = org.junit.Assert.assertThrows(IllegalStateException::class.java) {
            KsuBackendRegistry.backgroundDeployCommand("echo DEPLOYED")
        }
        assertEquals("KSU_BOOTSTRAP_SWITCH_BUSY", error.message)
        WorkModeSwitchSession.finish(SwitchOutcome.AwaitingReboot("new-switch", null))
        assertEquals("echo DEPLOYED", KsuBackendRegistry.backgroundDeployCommand("echo DEPLOYED"))
    }

    @Test
    fun deploymentResultIsPublishedOnThePortResult() {
        val port = FakePort(wired(), deploy = deployed())
        KsuBackendRegistry.install(WorkMode.KSU, port)
        // install() 只做接线；scheduleDeployment 负责执行并写状态。
        assertNull(KsuBackendRegistry.bootstrapResult())
        KsuBackendRegistry.scheduleDeployment(WorkMode.KSU)
        awaitDeploy()
        assertEquals(
            KsuBootstrapResult.Status.DEPLOYED,
            KsuBackendRegistry.bootstrapResult()?.status,
        )
        assertEquals(1, port.deployCalls)
    }

    @Test
    fun deploymentFailureKeepsExplicitFailureCode() {
        KsuBackendRegistry.install(
            WorkMode.KSU,
            FakePort(
                wired(),
                deploy = KsuBootstrapResult.failed("SWITCH_BOOTSTRAP_FAILED", "install exit=1"),
            ),
        )
        KsuBackendRegistry.scheduleDeployment(WorkMode.KSU)
        awaitDeploy()
        val result = KsuBackendRegistry.bootstrapResult()!!
        assertEquals(KsuBootstrapResult.Status.FAILED, result.status)
        assertFalse(result.ok)
        assertEquals("SWITCH_BOOTSTRAP_FAILED", result.failureCode)
        assertTrue(KsuBackendRegistry.lastError()!!.contains("install exit=1"))
    }

    @Test
    fun deploymentWithoutWiringIsExplicitFailure() {
        KsuBackendRegistry.scheduleDeployment(WorkMode.KSU)
        awaitDeploy()
        val result = KsuBackendRegistry.bootstrapResult()!!
        assertEquals(KsuBootstrapResult.Status.FAILED, result.status)
        assertEquals(KsuBackendRegistry.CODE_WIRING_UNAVAILABLE, result.failureCode)
    }

    @Test
    fun deploymentIsSkippedOutsideKsu() {
        KsuBackendRegistry.scheduleDeployment(WorkMode.LITE)
        assertEquals(
            KsuBootstrapResult.Status.SKIPPED_NOT_KSU,
            KsuBackendRegistry.bootstrapResult()?.status,
        )
    }

    private fun awaitDeploy() {
        repeat(200) {
            if (KsuBackendRegistry.bootstrapResult() != null) return
            Thread.sleep(10)
        }
    }
}

/** 一次"部署成功"的 KSU bootstrap 结果（顶层：嵌套 fake 的默认参数也能用）。 */
private fun deployed(): KsuBootstrapResult = KsuBootstrapResult(
    status = KsuBootstrapResult.Status.DEPLOYED,
    ksudPath = "/data/adb/ksud",
    workDirKsudPath = "/data/adb/ksu/bin/ksud",
    libadbrootPath = "/data/adb/ksu/lib/libadbroot.so",
    detail = "labelVerified=null",
)
