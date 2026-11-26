// SPDX-License-Identifier: GPL-3.0-or-later
package dev.maplesu.manager.ksu

import dev.maplesu.manager.workmode.ActivationSource
import dev.maplesu.manager.workmode.ModeRuntime
import dev.maplesu.manager.workmode.WorkMode
import dev.maplesu.manager.workmode.backend.KsuBackendRegistry
import dev.maplesu.manager.workmode.detect.DetectionGroup
import dev.maplesu.manager.workmode.detect.DetectionResult
import dev.maplesu.manager.workmode.detect.DetectionRules
import dev.maplesu.manager.workmode.detect.ModeState
import dev.maplesu.manager.workmode.detect.RecoveryReadiness
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T3 的**接线断言**（开发文档 §9.4 第 3、4 条；registry 级，不碰设备）。
 *
 * 3. LITE 下 `runtimeStatus() == NotApplicable` 且**零**探针调用；
 * 4. `installProduction()` 未被调用（未接线）时必须是 `Unknown`，**不得**是 `Ready`。
 *
 * 另外证明"接线成功 + 回执注入"时 registry 只是**转发**判据结果，
 * 不做任何"看起来正常"的默认值。
 */
class KsuRuntimeStatusTest {

    @After
    fun tearDown() {
        KsuBackendRegistry.resetForTest()
        ModeRuntime.resetForTest()
    }

    /** 最小 [KsuBackendRegistry.WiringPort]：只让 `wiredSet != null`，不起进程、不碰 Context。 */
    private class FakePort : KsuBackendRegistry.WiringPort {
        override fun wire(): KsuBackendRegistry.Wired = KsuBackendRegistry.Wired(
            rootExecutor = null,
            moduleOps = null,
            command = null,
            settingsFeature = null,
            superuser = null,
            rootShellExecutor = null,
            backendSet = null,
        )

        override fun deploy(): KsuBootstrapResult = KsuBootstrapResult.skipped()
    }

    private fun receipt(probe: KsuBootReceiptProbe) = KsuBootReceipt(
        probe = probe,
        kernelVersion = { KsuBootReceiptFixture.KERNEL_VERSION },
        kernelUapi = { KsuBootReceiptFixture.KERNEL_UAPI },
        bootEpochMs = { KsuBootReceiptFixture.EPOCH },
        clock = { KsuBootReceiptFixture.NOW },
    )

    private fun activateLite() {
        ModeRuntime.installBootIdProviderForTest { "boot-1" }
        ModeRuntime.recordDetection(
            DetectionResult(
                state = ModeState.MODE_A_ACTIVE,
                group = DetectionGroup.A_ACTIVE,
                rule = DetectionRules.Rule.R5_A_HANDSHAKE,
                bootId = "boot-1",
                observedAtMillis = 0L,
                trace = emptyList(),
                recovery = RecoveryReadiness.NONE,
                rootChannel = null,
            ),
        )
        assertTrue(ModeRuntime.activate(WorkMode.LITE, "boot-1", ActivationSource.BOOT_DETECTION))
    }

    // ── §9.4 断言 3：LITE 零探针 ────────────────────────────────────────

    @Test
    fun liteIsNotApplicableAndNeverBuildsAProbe() {
        // 先接线（证明 NotApplicable 来自**模式门**，不是因为"没接线"）。
        KsuBackendRegistry.install(WorkMode.KSU, FakePort())
        activateLite()
        var factoryCalls = 0
        KsuBackendRegistry.installRuntimeReceiptForTest {
            factoryCalls++
            error("LITE must not build a receipt probe")
        }

        assertEquals(KsuRuntimeState.NotApplicable, KsuBackendRegistry.runtimeStatus())
        assertEquals("LITE 下必须零探针调用", 0, factoryCalls)
    }

    // ── §9.4 断言 4：未接线不得 Ready ───────────────────────────────────

    @Test
    fun unwiredIsUnknownNeverReady() {
        // 没有任何 install();模式也是未判定 → 既不是 LITE 也不是 KSU。
        assertEquals(KsuRuntimeState.Unknown, KsuBackendRegistry.runtimeStatus())
    }

    // ── 接线后只转发判据结果 ────────────────────────────────────────────

    @Test
    fun wiredForwardsReceiptState() {
        KsuBackendRegistry.install(WorkMode.KSU, FakePort())
        KsuBackendRegistry.installRuntimeReceiptForTest { receipt(KsuBootReceiptFakeProbe()) }
        assertEquals(KsuRuntimeState.Ready, KsuBackendRegistry.runtimeStatus())
    }

    @Test
    fun wiredForwardsStagesNotRun() {
        KsuBackendRegistry.install(WorkMode.KSU, FakePort())
        val probe = KsuBootReceiptFakeProbe().apply {
            mtimes[KsuBootReceipt.LOG_CAT] = KsuBootReceiptFixture.EPOCH - 200_000L
        }
        KsuBackendRegistry.installRuntimeReceiptForTest { receipt(probe) }
        val state = KsuBackendRegistry.runtimeStatus()
        assertEquals(
            KsuRuntimeState.StagesNotRun(listOf(KsuBootReceipt.CODE_BOOT_LOG_NOT_REFRESHED)),
            state,
        )
    }

    @Test
    fun receiptFailureIsUnknownNotReady() {
        // 探针/接线异常绝不能被当成 Ready（禁止静默成功）。
        KsuBackendRegistry.install(WorkMode.KSU, FakePort())
        KsuBackendRegistry.installRuntimeReceiptForTest { error("probe blew up") }
        assertEquals(KsuRuntimeState.Unknown, KsuBackendRegistry.runtimeStatus())
    }

    @Test
    fun refreshBypassesCache() {
        KsuBackendRegistry.install(WorkMode.KSU, FakePort())
        var calls = 0
        KsuBackendRegistry.installRuntimeReceiptForTest {
            calls++
            receipt(KsuBootReceiptFakeProbe().apply { busybox = false })
        }
        val first = KsuBackendRegistry.runtimeStatus()
        assertEquals(
            KsuBootReceipt.CODE_BUSYBOX_UNUSABLE,
            (first as KsuRuntimeState.Broken).code,
        )
        // 缓存命中：再读一次不再构造回执。
        KsuBackendRegistry.runtimeStatus()
        assertEquals(1, calls)
        KsuBackendRegistry.refreshRuntimeStatus()
        assertEquals(2, calls)
    }
}
