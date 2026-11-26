package com.maplesu.core.ui.screen.install

import com.maplesu.core.ui.util.UltraKernelFacts
import com.maplesu.core.ui.util.UltraKernelGeneration
import com.maplesu.core.ui.util.UltraKernelVariantInfo
import dev.maplesu.manager.ultrakernel.UltraKernelGenerateCodes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** Target-renderer availability, image identity and existing install-state projection. */
class InstallBootFlowTest {
    private val release = "6.6.89-android15-8-g7e1f3c083cc6-abogki467167594-4k"

    /** 记录端口收到的 boot 参数，这样"探测真的把镜像交给渲染器"也能断言。 */
    private class FakeGeneration(
        private val answer: (File?, String) -> UltraKernelGenerationPort.GenerationPlan,
    ) : UltraKernelGenerationPort {
        var calls = 0
        var lastBoot: File? = null
        var lastRelease: String = ""

        override fun plan(boot: File?, release: String): UltraKernelGenerationPort.GenerationPlan {
            calls++
            lastBoot = boot
            lastRelease = release
            return answer(boot, release)
        }
    }

    private fun unavailable(code: String, detail: String = "") = FakeGeneration { _, _ ->
        UltraKernelGenerationPort.GenerationPlan.Unavailable(code, detail)
    }

    private fun available(
        sha: String = "b".repeat(64),
        patches: Int = 18,
        deferred: Boolean = false,
    ) = FakeGeneration { _, _ ->
        UltraKernelGenerationPort.GenerationPlan.Available(sha, patches, deferred)
    }

    /**
     * 生产装配是 `UltraKernelGeneration.port`（真的会读凭据 + 渲 36 MB 内核）。
     * 纯投影用例一律换成 fake；否则这些用例会在 host 上碰 `ksuApp`。
     */
    @Before
    fun useFakeGenerationByDefault() {
        InstallBootFlow.generation = unavailable(UltraKernelGenerateCodes.CREDENTIAL_REQUIRED)
    }

    @After
    fun restoreProductionWiring() {
        InstallBootFlow.generation = UltraKernelGeneration.port
    }

    private fun variant(
        id: String,
        default: Boolean = false,
        experimental: Boolean = false,
        patches: Int = 18,
    ) = UltraKernelVariantInfo(
        id = id,
        label = if (id == "k22") "MapleSU 内核（推荐）" else "兼容内核（回退）",
        summary = "",
        patches = patches,
        experimental = experimental,
        isDefault = default,
    )

    private fun readyFacts() = UltraKernelFacts(
        release = release,
        series = "6.6",
        variants = listOf(variant("k22", default = true), variant("k21", patches = 17)),
        defaultId = "k22",
    )

    private fun unknownFacts(release: String = "6.12.23-android16") = UltraKernelFacts(
        release = release,
        stableCode = "ULTRA_KERNEL_RELEASE_UNSUPPORTED",
        knownReleases = listOf(this.release),
    )

    @Test
    fun currentSourcePreviewNeverAsksTheGeneratorForPermissionToContinue() {
        val generation = unavailable("ULTRA_KERNEL_GENERATE_SITE_COVERAGE")
        InstallBootFlow.generation = generation
        val profile = probeCurrentBoot(release)
        assertEquals(release, profile.release)
        assertEquals("6.6", profile.series)
        assertEquals(0, generation.calls)
        assertNull(profile.target)
        assertEquals("", profile.stableCode)
    }

    @Test
    fun theOnlyTargetOfferedComesFromTheTargetRenderer() {
        InstallBootFlow.generation = available()
        val profile = InstallBootFlow.fromFacts("boot.img", release, readyFacts())
        assertEquals(InstallBootFlow.GENERATED_VARIANT_ID, profile.target?.id)
        assertTrue(profile.target?.generatedOnDevice ?: false)
        assertEquals(release, profile.release)
        assertEquals("6.6.x", profile.seriesLabel)
        assertTrue(profile.selectable)
        assertFalse(profile.releaseMismatch)
    }

    @Test
    fun aRejectedRecommendationDoesNotOverrideTheTargetRenderer() {
        InstallBootFlow.generation = available()
        val facts = UltraKernelFacts(
            release = release,
            variants = listOf(variant("k21")),
            rejected = listOf("k22:ULTRA_KERNEL_SPEC_DIGEST"),
            defaultId = "k22",
        )
        val profile = InstallBootFlow.fromFacts("boot.img", release, facts)
        assertEquals(InstallBootFlow.GENERATED_VARIANT_ID, profile.target?.id)
        assertTrue(profile.selectable)
        assertEquals("", profile.stableCode)
    }

    @Test
    fun unknownReleaseWithoutCredentialIsRefusedWithTheCredentialCode() {
        val profile = InstallBootFlow.fromFacts("boot.img", "", unknownFacts().copy(series = "6.12"))
        assertNull(profile.target)
        // 端口的码优先于 facts 的"暂不支持"：它告诉用户下一步该干什么。
        assertEquals(UltraKernelGenerateCodes.CREDENTIAL_REQUIRED, profile.stableCode)
        assertEquals(listOf(release), profile.knownReleases)
        assertFalse(profile.selectable)
        assertEquals("6.12.x", profile.seriesLabel)
    }

    @Test
    fun wiredGenerationOffersTheGeneratedTarget() {
        InstallBootFlow.generation = available(sha = "a".repeat(64), patches = 12)
        val profile = InstallBootFlow.fromFacts("boot.img", "", unknownFacts().copy(series = "6.12"))
        assertEquals(InstallBootFlow.GENERATED_VARIANT_ID, profile.target?.id)
        assertTrue(profile.target?.generatedOnDevice ?: false)
        assertEquals("6.12.23-android16", profile.release)
        assertEquals("6.12.x", profile.seriesLabel)
        assertTrue(profile.selectable)
    }

    @Test
    fun thePortIsAskedWithTheProbedImageAndTheReleaseItRead() {
        val generation = available()
        InstallBootFlow.generation = generation
        val image = File("boot-probe.img")
        InstallBootFlow.fromFacts("boot.img", "", unknownFacts(), image)
        assertSame(image, generation.lastBoot)
        assertEquals("6.12.23-android16", generation.lastRelease)
        assertEquals(1, generation.calls)

        // "当前分区"来源没有镜像：端口必须收到 null，而不是一个随便的 File。
        InstallBootFlow.fromFacts("current", release, unknownFacts(), null)
        assertNull(generation.lastBoot)
        assertEquals(2, generation.calls)
    }

    @Test
    fun aDeferredPlanIsStillASelectableTarget() {
        // 当前分区的 boot 只在安装时 dump，探测阶段只能确认"已接线 + 有凭据"。
        InstallBootFlow.generation = available(sha = "", patches = 0, deferred = true)
        val profile = InstallBootFlow.fromFacts("current", release, unknownFacts(release))
        assertEquals(InstallBootFlow.GENERATED_VARIANT_ID, profile.target?.id)
        assertTrue(profile.target?.generatedOnDevice ?: false)
        assertTrue(profile.selectable)
    }

    @Test
    fun aPartialSiteMatchIsAnExplicitRefusalCarryingTheSiteCode() {
        InstallBootFlow.generation = unavailable(
            UltraKernelGenerateCodes.SITE_COVERAGE,
            "3/18 differing=die+0x0,free_initmem+0x0",
        )
        val profile = InstallBootFlow.fromFacts("boot.img", "", unknownFacts("6.1.25-android14-11"))
        assertNull(profile.target)
        assertEquals(UltraKernelGenerateCodes.SITE_COVERAGE, profile.stableCode)
        assertTrue("stable code must keep the SITE_* shape", profile.stableCode.startsWith("ULTRA_KERNEL_GENERATE_SITE"))
        assertEquals("3/18 differing=die+0x0,free_initmem+0x0", profile.detail)
        assertFalse(profile.selectable)
    }

    @Test
    fun aPortThatFailsWithoutACodeStillFallsBackToTheCatalogCode() {
        InstallBootFlow.generation = unavailable("")
        val profile = InstallBootFlow.fromFacts("boot.img", "", unknownFacts())
        assertEquals("ULTRA_KERNEL_RELEASE_UNSUPPORTED", profile.stableCode)
    }

    @Test
    fun imageReleaseDifferentFromTheRunningKernelIsFlaggedNotBlocked() {
        InstallBootFlow.generation = available()
        val profile = InstallBootFlow.fromFacts("boot.img", "6.6.89-other", readyFacts())
        assertTrue(profile.releaseMismatch)
        // 仍然可选：能不能写盘是结果语义，不是"不许修补"。
        assertEquals(InstallBootFlow.GENERATED_VARIANT_ID, profile.target?.id)
        assertTrue(profile.selectable)
    }

    @Test
    fun seriesDisplayKeepsTheImageIdentityWhenTheCatalogUsesAnotherBuild() {
        InstallBootFlow.generation = available()
        val imageRelease = "6.6.100-android15-8-test"
        val facts = readyFacts().copy(release = imageRelease, assetRelease = release)
        val profile = InstallBootFlow.fromFacts("boot.img", release, facts)

        assertEquals("6.6.x", profile.seriesLabel)
        assertEquals(imageRelease, profile.release)
        assertEquals(release, profile.deviceRelease)
        assertTrue(profile.releaseMismatch)
        assertEquals(InstallBootFlow.GENERATED_VARIANT_ID, profile.target?.id)
        assertTrue(profile.selectable)
    }

    @Test
    fun unreadableReleaseIsAProfileWithoutATargetNotAnException() {
        val profile = InstallBootFlow.failed("boot.img", "", "ULTRA_KERNEL_BASE_NO_KERNEL", "detail")
        assertNull(profile.target)
        assertEquals("ULTRA_KERNEL_BASE_NO_KERNEL", profile.stableCode)
        assertFalse(profile.reading)
        assertFalse(profile.selectable)
    }

    @Test
    fun readingStateCarriesNoTarget() {
        val profile = InstallBootFlow.reading("boot.img", release)
        assertTrue(profile.reading)
        assertNull(profile.target)
        assertFalse(profile.selectable)
        assertEquals("", profile.seriesLabel)
    }
}
