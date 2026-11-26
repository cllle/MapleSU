// SPDX-License-Identifier: GPL-3.0-or-later
package dev.maplesu.manager.ksu

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 首次 userland bootstrap（W12b；03 §4.4，主文档 §4.5）+ T1「部署内容寻址」。
 *
 * 主机可证明的部分：命令构造（`--stage-for-switch`）、失败阻断、安装后
 * 文件/摘要校验，以及 T1 的三项硬判定（摘要 = APK 内 `libksud_ksu.so`、
 * `ksud debug info` 版本、`busybox true` 退出码）。SELinux 标签与 AVC 属设备项，
 * 本类只断言 `labelVerified == null`（未验证，切换服务必须按未闭环处理）。
 *
 * T1 的四态语义（`DEPLOYED`/`ALREADY_PRESENT`/`FAILED`/`SKIPPED_NOT_KSU`）在
 * [ensureDeployedShortCircuitsOnlyWhenEverythingMatches] 等用例里逐条钉死：
 * 「文件在」不再等于「已部署」。
 */
class KsuBootstrapTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val daemonFile: File by lazy {
        KsuImageFixtures.write(File(tmp.root, "applib"), "libksud_ksu.so", "ksud".toByteArray())
    }

    private val lib: File by lazy {
        KsuImageFixtures.write(File(tmp.root, "apklib"), "libadbroot.so", "ADBROOT-PAYLOAD".toByteArray())
    }

    private fun daemon(): File = daemonFile

    private fun libadbroot(): File = lib

    /** 部署成功后 `/data/adb/ksud` 的内容（内容寻址的比对对象）。 */
    private val ksudBytes = "ksud-binary".toByteArray()

    /** 真实设备形态的 `ksud debug info` 解析结果（fixture 文本见 [versionInfoParsesRealKsudDebugInfo]）。 */
    private val realVersion =
        KsuVersionInfo(version = 32650, uapiVersion = 7, isLkm = true, flags = 0x21L)

    /** APK 内 `libksud_ksu.so` 的期望摘要（测试用固定字节）。 */
    private fun expectedSha(): String = KsuInstallProducer.sha256(
        KsuImageFixtures.write(File(tmp.root, "expected"), "ksud", ksudBytes),
    )

    private class FakeInstallRunner(
        private val onRun: (String) -> Unit = {},
    ) : KsuRootCommandRunner {
        val commands = mutableListOf<String>()
        var exitCode: Int = 0

        override fun run(commandLine: String, timeoutMs: Long): KsuShellResult {
            commands += commandLine
            if (exitCode != 0) return KsuShellResult(exitCode, emptyList(), listOf("install failed"))
            onRun(commandLine)
            return KsuShellResult(0, listOf("installed"), emptyList())
        }
    }

    /**
     * T1 用例的 fake root shell：只在 `ksud install` 时落盘。
     *
     * 版本/busybox 由注入的探针回答，不经过 runner —— 于是 [installs] 能精确断言
     * 「短路时不得重装」与「不符时必须重装」。
     */
    private class DeployRunner(
        private val onInstall: () -> Unit = {},
    ) : KsuRootCommandRunner {
        val commands = mutableListOf<String>()
        var exitCode: Int = 0

        override fun run(commandLine: String, timeoutMs: Long): KsuShellResult {
            commands += commandLine
            if (exitCode != 0) return KsuShellResult(exitCode, emptyList(), listOf("install failed"))
            onInstall()
            return KsuShellResult(0, listOf("installed"), emptyList())
        }

        val installs: Int get() = commands.count { it.contains(" install ") }
    }

    private fun deployFiles(dataRoot: File, ksud: ByteArray = ksudBytes) {
        KsuImageFixtures.write(dataRoot, "ksud", ksud)
        KsuImageFixtures.write(dataRoot, "ksu/bin/ksud", ksud)
        KsuImageFixtures.write(dataRoot, "ksu/lib/libadbroot.so", libadbroot().readBytes())
    }

    private fun bootstrap(runner: KsuRootCommandRunner, dataRoot: File): KsuBootstrap =
        KsuBootstrap(daemon().absolutePath, runner, dataRoot) { 42L }

    /** T1 全参数版本：期望摘要 + 版本探针 + busybox 探针 + 回执落点。 */
    private fun contentBootstrap(
        runner: KsuRootCommandRunner,
        dataRoot: File,
        expected: String? = expectedSha(),
        version: (String) -> KsuVersionInfo? = { realVersion },
        busybox: (String) -> Boolean = { true },
        receiptFile: File? = null,
    ): KsuBootstrap = KsuBootstrap(
        daemonPath = daemon().absolutePath,
        runner = runner,
        dataAdbRoot = dataRoot,
        expectedKsudSha256 = expected,
        versionProbe = version,
        busyboxProbe = busybox,
        receiptFile = receiptFile,
        kernelVersion = { "6.6.30-android15" },
        clock = { 42L },
    )

    // ── 既有：命令构造与失败阻断 ──────────────────────────────────────────

    @Test
    fun planBuildsStagedInstallCommand() {
        val lib = libadbroot()
        val dataRoot = tmp.newFolder("data-adb")
        val plan = bootstrap(FakeInstallRunner(), dataRoot).plan(lib, stageForSwitch = true)
        assertEquals(
            listOf(
                daemon().absolutePath,
                "install",
                "--libadbroot",
                lib.absolutePath,
                "--data-path",
                dataRoot.absolutePath,
                "--stage-for-switch",
            ),
            plan.tokens,
        )
        assertTrue(plan.commandLine.contains("--stage-for-switch"))
        assertEquals(KsuInstallProducer.sha256(lib), plan.libadbrootSha256)
        assertEquals(42L, plan.createdAt)
    }

    @Test
    fun planRejectsMissingOrForeignLibadbroot() {
        val dataRoot = tmp.newFolder("data-adb")
        val bootstrap = bootstrap(FakeInstallRunner(), dataRoot)
        val wrongName = KsuImageFixtures.write(tmp.newFolder("wrong"), "adbroot.bin", byteArrayOf(1))
        val err = runCatching { bootstrap.plan(wrongName) }.exceptionOrNull()
        assertTrue("wrong libadbroot name must be rejected, got $err", err is IllegalArgumentException)
        val missing = runCatching { bootstrap.plan(File(tmp.root, "missing/libadbroot.so")) }
        assertTrue(missing.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { bootstrap.plan(null) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun runFailsWithSwitchBootstrapFailedWhenKsudFails() {
        val runner = FakeInstallRunner().apply { exitCode = 1 }
        val result = bootstrap(runner, tmp.newFolder("data-adb")).run(libadbroot())
        assertTrue("expected Failed, got $result", result is KsuBootstrap.Result.Failed)
        assertEquals(KsuBootstrap.FAILURE_CODE, (result as KsuBootstrap.Result.Failed).code)
    }

    @Test
    fun runForwardsFailureOutputIncludingEmptyLinesAndActualExitCodeOnce() {
        val receipt = KsuShellResult(7, listOf("deploy progress", "", "failure detail on stdout"), listOf("deploy error", ""))
        val commands = mutableListOf<Pair<String, Long>>()
        val runner = KsuRootCommandRunner { command, timeout ->
            commands += command to timeout
            receipt
        }
        val stdout = mutableListOf<String>()
        val stderr = mutableListOf<String>()
        val bootstrap = bootstrap(runner, tmp.newFolder("data-output-fail"))
        val plan = bootstrap.plan(libadbroot())

        val result = bootstrap.run(libadbroot(), onStdout = { stdout += it }, onStderr = { stderr += it })

        assertTrue(result is KsuBootstrap.Result.Failed)
        val failed = result as KsuBootstrap.Result.Failed
        assertEquals(KsuBootstrap.FAILURE_CODE, failed.code)
        assertEquals(
            "command=${plan.commandLine}\nksud install code=7\n" +
                "stdout:\n${receipt.outText}\nstderr:\n${receipt.errText}",
            failed.detail,
        )
        assertEquals(listOf("- ${plan.commandLine}") + receipt.out + "- ksud install exitCode=${receipt.code}", stdout)
        assertEquals(receipt.err, stderr)
        assertEquals(listOf(plan.commandLine to KsuBootstrap.INSTALL_TIMEOUT_MS), commands)
    }

    @Test
    fun runForwardsSuccessOutputIncludingEmptyLinesWithoutChangingVerification() {
        val dataRoot = tmp.newFolder("data-output-ok")
        val receipt = KsuShellResult(0, listOf("deployed successfully", ""), listOf("warning", ""))
        val commands = mutableListOf<Pair<String, Long>>()
        val runner = KsuRootCommandRunner { command, timeout ->
            commands += command to timeout
            deployFiles(dataRoot)
            receipt
        }
        val stdout = mutableListOf<String>()
        val stderr = mutableListOf<String>()
        val bootstrap = bootstrap(runner, dataRoot)
        val plan = bootstrap.plan(libadbroot())

        val result = bootstrap.run(libadbroot(), onStdout = { stdout += it }, onStderr = { stderr += it })

        assertTrue(result is KsuBootstrap.Result.Completed)
        assertTrue((result as KsuBootstrap.Result.Completed).verification!!.complete)
        assertEquals(listOf("- ${plan.commandLine}") + receipt.out + "- ksud install exitCode=${receipt.code}", stdout)
        assertEquals(receipt.err, stderr)
        assertEquals(listOf(plan.commandLine to KsuBootstrap.INSTALL_TIMEOUT_MS), commands)
    }

    @Test
    fun runFailsWhenPostInstallFilesAreMissing() {
        // ksud 返回 0 但没有创建任何文件：不得把失败伪装成成功
        val runner = FakeInstallRunner()
        val result = bootstrap(runner, tmp.newFolder("data-adb")).run(libadbroot())
        assertTrue("expected Failed, got $result", result is KsuBootstrap.Result.Failed)
        val failed = result as KsuBootstrap.Result.Failed
        assertEquals("SWITCH_BOOTSTRAP_FAILED", failed.code)
        assertTrue(failed.detail.contains("verification failed"))
    }

    @Test
    fun runCompletesWhenFilesAndDigestMatch() {
        val lib = libadbroot()
        val dataRoot = tmp.newFolder("data-adb")
        val runner = FakeInstallRunner { commandLine ->
            assertTrue(commandLine.contains(lib.absolutePath))
            KsuImageFixtures.write(dataRoot, "ksud", "ksud-binary".toByteArray())
            KsuImageFixtures.write(dataRoot, "ksu/bin/ksud", "ksud-binary".toByteArray())
            KsuImageFixtures.write(dataRoot, "ksu/lib/libadbroot.so", lib.readBytes())
        }
        val result = bootstrap(runner, dataRoot).run(lib)
        assertTrue("expected Completed, got $result", result is KsuBootstrap.Result.Completed)
        val completed = result as KsuBootstrap.Result.Completed
        assertEquals(lib.readBytes().size.toLong(), completed.libadbrootInstalled.length())
        assertEquals(KsuInstallProducer.sha256(lib), completed.libadbrootSha256)
        // 标签是设备事实：主机只能返回"未验证"，不能假装已生效
        assertNull(completed.labelVerified)
        // T1：同一轮校验事实随结果返回（避免 ensureDeployed 再算一遍 6.6 MB）
        assertEquals(KsuInstallProducer.sha256(File(dataRoot, "ksud")), completed.verification?.ksudSha256)
    }

    @Test
    fun verifyDetectsDigestMismatch() {
        val lib = libadbroot()
        val dataRoot = tmp.newFolder("data-adb")
        KsuImageFixtures.write(dataRoot, "ksud", "ksud".toByteArray())
        KsuImageFixtures.write(dataRoot, "ksu/bin/ksud", "ksud".toByteArray())
        KsuImageFixtures.write(dataRoot, "ksu/lib/libadbroot.so", "tampered".toByteArray())
        val bootstrap = bootstrap(FakeInstallRunner(), dataRoot)
        val verification = bootstrap.verify(KsuInstallProducer.sha256(lib))
        assertTrue(verification.ksudPresent)
        assertTrue(verification.workDirKsudPresent)
        assertEquals(false, verification.libadbrootDigestMatches)
        assertTrue(!verification.complete)
    }

    @Test
    fun nonStagedPlanOmitsStagingFlag() {
        val plan = bootstrap(FakeInstallRunner(), tmp.newFolder("data-adb"))
            .plan(libadbroot(), stageForSwitch = false)
        assertTrue("--stage-for-switch" !in plan.tokens)
    }

    // ── T1：verify() 的三项判定 ─────────────────────────────────────────

    @Test
    fun verifyReportsContentDigestVersionAndBusybox() {
        val dataRoot = tmp.newFolder("data-verify")
        deployFiles(dataRoot)
        val verification = contentBootstrap(DeployRunner(), dataRoot).verify()
        val version = verification.ksudVersion!!
        assertTrue(verification.complete)
        assertEquals(expectedSha(), verification.ksudSha256)
        assertEquals(32650, version.version)
        assertEquals(7, version.uapiVersion)
        assertTrue(version.isLkm)
        assertEquals(0x21L, version.flags)
        assertTrue(verification.busyboxOk)
        assertTrue(verification.digestMatches(expectedSha()))
        assertTrue(verification.versionMatches)
        assertTrue(verification.deployable(expectedSha()))
        // 任何一项不成立 → 不得短路
        assertFalse(verification.deployable("f".repeat(64)))
        assertFalse(verification.deployable(null))
    }

    @Test
    fun unassembledProbesAreFailClosed() {
        val dataRoot = tmp.newFolder("data-cold")
        deployFiles(dataRoot)
        // 默认版本探针 = null、busybox 探针 = false：文件齐也不得判"可部署"（C7）。
        val verification = bootstrap(FakeInstallRunner(), dataRoot).verify()
        assertTrue(verification.complete)
        assertNull(verification.ksudVersion)
        assertFalse(verification.busyboxOk)
        assertEquals(false, verification.versionMatches)
        assertFalse(verification.deployable(expectedSha()))
    }

    @Test
    fun versionInfoParsesRealKsudDebugInfo() {
        val real = """
            KernelSU version: 32650
            version: 32650
            uapi_version: 7
            userspace_version: 32650
            userspace_uapi_version: 7
            flags: 0x00000021
            features: 0x0000001f
            lkm: true
            bundled: false
            late_load: false
            runtime_mode: lkm
        """.trimIndent()
        val info = KsuVersionInfo.parse(real)!!
        assertEquals(32650, info.version)
        assertEquals(7, info.uapiVersion)
        assertTrue(info.isLkm)
        assertEquals(0x21L, info.flags)
        assertTrue(info.matchesBuild)
        // 解析失败一律 null（不抛、不猜）
        assertNull(KsuVersionInfo.parse(""))
        assertNull(KsuVersionInfo.parse("totally unrelated output"))
        assertNull(KsuVersionInfo.parse("userspace_version: 32650\n"))
        assertNull(KsuVersionInfo.parse("userspace_uapi_version: 7\n"))
        assertNull(KsuVersionInfo.parse("userspace_version: not-a-number\nuserspace_uapi_version: 7\n"))
        // 版本门禁：UAPI 代际不一致 / 内核未接线（version=0）都算"版本不对"
        assertFalse(KsuVersionInfo.parse("userspace_version: 32650\nuserspace_uapi_version: 6\n")!!.matchesBuild)
        assertFalse(KsuVersionInfo.parse("userspace_version: 0\nuserspace_uapi_version: 7\n")!!.matchesBuild)
    }

    @Test
    fun productionProbesUseDebugInfoAndBusyboxTrue() {
        val commands = mutableListOf<String>()
        val exec = KsuRootShellExecutor { line ->
            commands += line
            if (line.contains("debug info")) {
                KsuShellResult(
                    0,
                    listOf("version: 32650", "uapi_version: 7", "userspace_version: 32650", "userspace_uapi_version: 7", "lkm: true"),
                    emptyList(),
                )
            } else {
                KsuShellResult(0, emptyList(), emptyList())
            }
        }
        val probe = KsuBootstrap.rootShellVersionProbe(exec)("/data/adb/ksud")!!
        assertEquals(32650, probe.version)
        assertEquals(7, probe.uapiVersion)
        assertTrue(KsuBootstrap.rootShellBusyboxProbe(exec)("/data/adb/ksu/bin/busybox"))
        assertTrue(commands.any { it == "/data/adb/ksud debug info" })
        assertTrue(commands.any { it == "/data/adb/ksu/bin/busybox true" })
        // 探针跑不起来 → null/false（不抛，也不当成通过）
        val dead = KsuRootShellExecutor { KsuShellResult(-1, emptyList(), listOf("no root")) }
        assertNull(KsuBootstrap.rootShellVersionProbe(dead)("/data/adb/ksud"))
        assertFalse(KsuBootstrap.rootShellBusyboxProbe(dead)("/data/adb/ksu/bin/busybox"))
    }

    @Test
    fun bundledKsudDigestIsCachedAndFailsClosed() {
        // APK 内 libksud_ksu.so 的摘要：同一份只算一次（进程级缓存），缺失 → null。
        val first = KsuBootstrap.bundledKsudSha256(daemon())
        assertEquals(KsuInstallProducer.sha256(daemon()), first)
        assertEquals(first, KsuBootstrap.bundledKsudSha256(daemon()))
        assertNull(KsuBootstrap.bundledKsudSha256(File(tmp.root, "missing/libksud_ksu.so")))
    }

    // ── T1：ensureDeployed() 的四态语义 ─────────────────────────────────

    @Test
    fun ensureDeployedShortCircuitsOnlyWhenEverythingMatches() {
        val dataRoot = tmp.newFolder("data-short")
        deployFiles(dataRoot)
        val runner = DeployRunner()
        val result = contentBootstrap(runner, dataRoot).ensureDeployed(libadbroot())
        assertEquals(KsuBootstrapResult.Status.ALREADY_PRESENT, result.status)
        assertTrue(result.ok)
        assertEquals("全对时必须短路，不得重跑 ksud install", 0, runner.installs)
        assertTrue(result.detail.contains("content verified"))
        assertTrue(result.detail.contains("sha256="))
    }

    @Test
    fun ensureDeployedReinstallsWhenDeployedDigestDiffers() {
        val dataRoot = tmp.newFolder("data-stale")
        deployFiles(dataRoot, ksud = "stale-ksud".toByteArray())
        val runner = DeployRunner { deployFiles(dataRoot) }
        val result = contentBootstrap(runner, dataRoot).ensureDeployed(libadbroot())
        assertEquals(KsuBootstrapResult.Status.DEPLOYED, result.status)
        assertEquals("摘要不符必须重装 binaries", 1, runner.installs)
    }

    @Test
    fun ensureDeployedReinstallsWhenVersionMismatch() {
        val dataRoot = tmp.newFolder("data-ver")
        deployFiles(dataRoot)
        // 旧 ksud 报 UAPI=6（T0 的 F1 形态）：文件齐也不算部署好。
        var version = KsuVersionInfo(version = 30000, uapiVersion = 6, isLkm = true, flags = 0L)
        val runner = DeployRunner { version = realVersion; deployFiles(dataRoot) }
        val result = contentBootstrap(runner, dataRoot, version = { version })
            .ensureDeployed(libadbroot())
        assertEquals(KsuBootstrapResult.Status.DEPLOYED, result.status)
        assertEquals("版本不符必须重装 binaries", 1, runner.installs)
    }

    @Test
    fun ensureDeployedFailsWithVersionMismatchWhenReinstallCannotFixIt() {
        val dataRoot = tmp.newFolder("data-ver-fail")
        deployFiles(dataRoot)
        val stale = KsuVersionInfo(version = 30000, uapiVersion = 6, isLkm = true, flags = 0L)
        val result = contentBootstrap(DeployRunner { deployFiles(dataRoot) }, dataRoot, version = { stale })
            .ensureDeployed(libadbroot())
        assertEquals(KsuBootstrapResult.Status.FAILED, result.status)
        assertEquals(KsuBootstrap.CODE_VERSION_MISMATCH, result.failureCode)
        assertFalse(result.ok)
    }

    @Test
    fun ensureDeployedFailsWithProbeFailedWhenVersionProbeUnavailable() {
        val dataRoot = tmp.newFolder("data-probe")
        val result = contentBootstrap(DeployRunner { deployFiles(dataRoot) }, dataRoot, version = { null })
            .ensureDeployed(libadbroot())
        assertEquals(KsuBootstrapResult.Status.FAILED, result.status)
        assertEquals(KsuBootstrap.CODE_PROBE_FAILED, result.failureCode)
        assertTrue(result.detail.contains("debug info"))
    }

    @Test
    fun ensureDeployedReinstallsWhenBusyboxUnusableThenSucceeds() {
        val dataRoot = tmp.newFolder("data-bb")
        deployFiles(dataRoot)
        var busyboxOk = false
        val runner = DeployRunner { busyboxOk = true; deployFiles(dataRoot) }
        val result = contentBootstrap(runner, dataRoot, busybox = { busyboxOk })
            .ensureDeployed(libadbroot())
        assertEquals(KsuBootstrapResult.Status.DEPLOYED, result.status)
        assertEquals("busybox 不可用必须重刷 binaries（A11）", 1, runner.installs)
    }

    @Test
    fun ensureDeployedFailsWithBusyboxUnusableWhenItStaysBroken() {
        val dataRoot = tmp.newFolder("data-bb-fail")
        deployFiles(dataRoot)
        val result = contentBootstrap(DeployRunner { deployFiles(dataRoot) }, dataRoot, busybox = { false })
            .ensureDeployed(libadbroot())
        assertEquals(KsuBootstrapResult.Status.FAILED, result.status)
        assertEquals(KsuBootstrap.CODE_BUSYBOX_UNUSABLE, result.failureCode)
    }

    @Test
    fun ensureDeployedFailsWithInstallFailedWhenKsudExitsNonZero() {
        val runner = DeployRunner().apply { exitCode = 1 }
        val result = contentBootstrap(runner, tmp.newFolder("data-rc")).ensureDeployed(libadbroot())
        assertEquals(KsuBootstrapResult.Status.FAILED, result.status)
        assertEquals(KsuBootstrap.CODE_INSTALL_FAILED, result.failureCode)
        assertTrue(result.detail.contains("install failed"))
    }

    @Test
    fun ensureDeployedRefusesNullExpectedDigest() {
        val runner = DeployRunner()
        val result = contentBootstrap(runner, tmp.newFolder("data-null"), expected = null)
            .ensureDeployed(libadbroot())
        assertEquals(KsuBootstrapResult.Status.FAILED, result.status)
        assertEquals(KsuBootstrap.CODE_DIGEST_MISMATCH, result.failureCode)
        assertTrue("没有权威摘要时不得安装（C7）", runner.commands.isEmpty())
    }

    @Test
    fun ensureDeployedWritesDeployReceipt() {
        val dataRoot = tmp.newFolder("data-receipt")
        val receipt = File(tmp.root, "files/ksu_deploy.json")
        val result = contentBootstrap(DeployRunner { deployFiles(dataRoot) }, dataRoot, receiptFile = receipt)
            .ensureDeployed(libadbroot())
        assertEquals(KsuBootstrapResult.Status.DEPLOYED, result.status)
        assertTrue(result.detail.contains("receipt=written"))
        val read = KsuDeployReceipt.read(receipt)!!
        assertEquals(expectedSha(), read.sha256)
        assertEquals(32650, read.version)
        assertEquals(7, read.uapiVersion)
        assertTrue(read.busyboxOk)
        assertEquals(42L, read.deployedAt)
        assertEquals("6.6.30-android15", read.kernelVersionAtDeploy)
        assertFalse("tmp 文件不得残留", File(receipt.parentFile, receipt.name + ".tmp").exists())
    }

    @Test
    fun staleReceiptIsNotAcceptedAsProofOfDeployment() {
        val dataRoot = tmp.newFolder("data-stale-receipt")
        deployFiles(dataRoot, ksud = "stale-ksud".toByteArray())
        val receipt = File(tmp.root, "files/ksu_deploy.json")
        // 回执声称"摘要对"（上次部署成功），但磁盘字节已不对：必须重装，不得信回执。
        assertTrue(
            KsuDeployReceipt.write(
                receipt,
                KsuDeployReceipt(expectedSha(), 32650, 7, true, 7L, "6.1.0-android14"),
            ),
        )
        val runner = DeployRunner { deployFiles(dataRoot) }
        val result = contentBootstrap(runner, dataRoot, receiptFile = receipt)
            .ensureDeployed(libadbroot())
        assertEquals(KsuBootstrapResult.Status.DEPLOYED, result.status)
        assertEquals(1, runner.installs)
        assertEquals("部署回执必须被本轮事实覆盖", 42L, KsuDeployReceipt.read(receipt)!!.deployedAt)
    }

    // ── T1：回执读写（原子写 / 坏档 / 覆盖）─────────────────────────────

    @Test
    fun deployReceiptRoundTripsAndRejectsUnreadableFiles() {
        val receipt = KsuDeployReceipt(
            sha256 = "a".repeat(64),
            version = 32650,
            uapiVersion = 7,
            busyboxOk = true,
            deployedAt = 1_700_000_000_000L,
            kernelVersionAtDeploy = "6.6.30-android15",
        )
        val file = File(tmp.root, "files/ksu_deploy.json")
        assertTrue(KsuDeployReceipt.write(file, receipt))
        assertEquals(receipt, KsuDeployReceipt.read(file))
        assertNull(KsuDeployReceipt.read(File(tmp.root, "files/missing.json")))
        val garbage = KsuImageFixtures.write(
            tmp.newFolder("garbage"), KsuDeployReceipt.FILE_NAME, "not json".toByteArray(),
        )
        assertNull(KsuDeployReceipt.read(garbage))
        val badSha = KsuImageFixtures.write(
            tmp.newFolder("bad-sha"), KsuDeployReceipt.FILE_NAME,
            """{"sha256":"nope","version":32650,"uapiVersion":7}""".toByteArray(),
        )
        assertNull(KsuDeployReceipt.read(badSha))
        // 覆盖写：第二次写必须替换旧档（rename 前删旧档，Android 的 renameTo 不覆盖）
        val second = receipt.copy(deployedAt = 2L, busyboxOk = false)
        assertTrue(KsuDeployReceipt.write(file, second))
        assertEquals(second, KsuDeployReceipt.read(file))
        assertFalse(File(file.parentFile, file.name + ".tmp").exists())
    }
}
