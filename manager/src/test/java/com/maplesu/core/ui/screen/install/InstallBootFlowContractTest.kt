package com.maplesu.core.ui.screen.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 四步流程的源码契约 + 三个**已确认缺陷**的回归钉（47 报告 §1.5 G1/G2、`_work/53-w3-ui-flow.md`）。
 *
 * 这不是风格测试：每一条都对应一个真实发生过的行为
 * （来源动作混淆 / 未登记 release 静默降级 Lite / 没有确认页），
 * 以及本波的用户口径：
 *
 * > 用户只看得到「选择 Ultra 模式 → 选择 boot → 选择内核版本 → 修补成功」；
 * > 不要支持等级 / 实验档 / 证据 / 记录 / k21-k22 变体；成果只要一份完整 Ultra boot。
 */
class InstallBootFlowContractTest {
    private fun source(path: String): String {
        val file = File(path)
        assumeTrue(file.path, file.isFile)
        return file.readText()
    }

    private fun moduleRoot(): File {
        val root = File("src/main/java/com/maplesu/core/ui/screen")
        assumeTrue(root.path, root.isDirectory)
        return root
    }

    private fun ksuCli() = source("src/main/java/com/maplesu/core/ui/util/KsuCli.kt")
    private fun ultraInstall() = source("src/main/java/com/maplesu/core/ui/util/UltraKernelInstall.kt")
    private fun installScreen() = source("src/main/java/com/maplesu/core/ui/screen/install/InstallScreen.kt")
    private fun installSteps() = source("src/main/java/com/maplesu/core/ui/screen/install/InstallSteps.kt")
    private fun installFlow() = source("src/main/java/com/maplesu/core/ui/screen/install/InstallBootFlow.kt")
    private fun installMaterial() = source("src/main/java/com/maplesu/core/ui/screen/install/InstallMaterial.kt")
    private fun installMiuix() = source("src/main/java/com/maplesu/core/ui/screen/install/InstallMiuix.kt")
    private fun flashScreen() = source("src/main/java/com/maplesu/core/ui/screen/flash/FlashScreen.kt")
    private fun flashState() = source("src/main/java/com/maplesu/core/ui/screen/flash/FlashUiState.kt")
    private fun flashBanner() = source("src/main/java/com/maplesu/core/ui/screen/flash/FlashStatusBanner.kt")
    private fun flashMaterial() = source("src/main/java/com/maplesu/core/ui/screen/flash/FlashMaterial.kt")
    private fun flashMiuix() = source("src/main/java/com/maplesu/core/ui/screen/flash/FlashMiuix.kt")
    private fun bootExport() = source("src/main/java/com/maplesu/core/ui/util/UltraKernelInstallExport.kt")

    // Selected files publish a candidate; direct installation keeps the verified write transaction.

    @Test
    fun chosenImageIsExportedBeforeAnyInstallOrSwitchTransaction() {
        val body = ksuCli().substringAfter("private fun installStockTarget(")
            .substringBefore("private fun copyInstallerImage(")
        val export = body.substringBefore("val active = ModeRuntime.active")
            .substringAfter("if (bootUri != null) {")
        assertTrue(export.contains("if (selectedImageOnly)"))
        assertTrue(export.contains("exportSelectedImage("))
        assertTrue(export.contains("FlashOutcome.SAVED_ONLY"))
        assertTrue(export.contains("outputPath = saved"))
        assertFalse(export.contains("InstallerServiceClient."))
        assertFalse(export.contains("StockInstallFlow.install("))
        assertFalse(export.contains("installCommit("))
        assertFalse(export.contains("written()"))
    }

    @Test
    fun directInstallationKeepsRollbackAndVerifiedWriting() {
        val body = ksuCli().substringAfter("private fun installStockTarget(")
            .substringBefore("private fun copyInstallerImage(")
        val direct = body.substringAfter("val active = ModeRuntime.active")
        assertTrue(direct.contains("facade.startSwitch("))
        assertTrue(direct.contains("StockInstallFlow.install("))
        assertTrue(direct.contains("restoreOriginal, originalManager, conflictAccepted"))
        assertTrue(direct.contains("InstallStage.WRITING"))
        assertTrue(direct.contains("return written()"))
        assertTrue(direct.contains("FlashOutcome.RESTORED"))
        assertTrue(direct.contains("FlashOutcome.RECOVERY_REQUIRED"))
    }

    @Test
    fun theStepThreeKmiTravelsIntoThePatchEntryPoint() {
        assertTrue(installScreen().contains("initBootKmi?.let { LkmSelection.KmiString(it) } ?: LkmSelection.KmiNone"))
        assertTrue(installScreen().contains("lkm = selectedLkm"))
        assertFalse(installScreen().contains("variantId = variantSnapshot"))
        val flashIt = source("src/main/java/com/maplesu/core/ui/screen/flash/FlashUtils.kt")
        assertTrue(flashIt.contains("val variantId: String? = null"))
        assertTrue(flashIt.contains("variantId = flashIt.variantId"))
    }

    // ── 缺陷 2：未登记 release 绝不静默降级 Lite ────────────────────────────────

    @Test
    fun theUltraInstallPathCannotFallBackToTheLitePatcher() {
        val source = ksuCli()
        assertFalse(
            "LiteBootPatcher must be unreachable from the Ultra install path",
            source.contains("LiteBootPatcher"),
        )
        assertFalse(source.contains("install_patched_boot_root_mismatch"))
        // 「暂不支持」必须是显式失败 + 稳定码；54 起它还必须是"缺凭据 / 生成器拒绝"这两种
        // **可执行**的原因，而不是一句笼统的 unsupported。
        assertTrue(ultraInstall().contains("UltraImageBuild.Refused(78"))
        assertTrue(ultraInstall().contains("UltraKernelCredentialRequiredException"))
        assertTrue(ultraInstall().contains("UltraKernelGenerateException"))
    }

    @Test
    fun theUltraBuildNeverPublishesAndNeverReturnsNull() {
        val source = ultraInstall()
        // 建候选与"存 Download"解耦：发布只在 KsuCli 的 SAVED_ONLY 分支与「保存 boot」动作里发生。
        assertFalse(source.contains("BootCandidatePublisher"))
        // 旧缺陷写法：selectForRelease(...) ?: return null
        assertFalse(source.contains("?: return null"))
        assertFalse(source.contains("FlashResult? {"))
        assertTrue(source.contains("sealed class UltraImageBuild"))
        assertTrue(source.contains("class Refused("))
    }

    // ── 54：W1b 生成器 → UI 端口 ─────────────────────────────────────────────

    @Test
    fun theGenerationPortHasAProductionImplementation() {
        val flow = installFlow()
        assertTrue(flow.contains("var generation: UltraKernelGenerationPort = UltraKernelGeneration.port"))
        assertFalse(flow.contains("object NotWired"))
        assertFalse(flow.contains("UltraKernelGenerationPort.NotWired"))
        assertFalse(flow.contains("ULTRA_KERNEL_GENERATION_NOT_WIRED"))
        assertTrue(flow.contains("fun plan(boot: File?, release: String)"))
        val install = ultraInstall()
        assertTrue(install.contains("class UltraKernelSourcedGeneration("))
        assertTrue(install.contains("object UltraKernelGeneration"))
        // 生产装配真的会渲染（不是返回常量）。
        assertTrue(install.contains("generatePlan(boot, credential)"))
    }

    @Test
    fun bothSourcesPrepareCandidateIdentityIndependentlyOfRuntimePairing() {
        val install = ultraInstall()
        val wiring = source("src/main/java/dev/maplesu/manager/runtime/ultra/UltraInstallCredentials.kt")
        val policy = source("src/main/java/dev/maplesu/manager/runtime/ultra/UltraInstallCredentialPolicy.kt")
        assertTrue(install.contains("UltraInstallCredentials.existing(ksuApp)"))
        assertTrue(wiring.contains("UltraRootKeyStore.read(app)"))
        assertTrue(wiring.contains("imageSeed(app, create = false)"))
        assertTrue(wiring.contains("runCatching { store.load() }.getOrNull()"))
        assertTrue(wiring.contains("imageSeed(app, create = true)"))
        assertFalse(wiring.contains("store::getOrCreate"))
        assertFalse(policy.contains("if (!imageOnly && !knownNonUltra()) return null"))
        assertFalse(install.contains("canProvision ="))
        assertTrue(install.contains("UltraInstallCredentials.forCandidate(ksuApp, imageOnly)"))
        assertTrue(policy.contains("existing()?.let { return it }"))
        assertFalse(install.contains("getOrCreate("))
        assertFalse(install.contains("need=ultra-root-key-or-device-credential"))
    }

    @Test
    fun bothInstallEntriesUseTheTargetPreparationAndTheirOwnSource() {
        val install = ultraInstall()
        assertTrue(install.contains("flow.prepareTarget(source, UltraInstallCredentials.forCandidate(ksuApp, imageOnly), variantId,"))
        assertTrue(install.contains("artifact, recoveredDeployment, onStdout)"))
        assertTrue(install.contains("flow.prepareTarget(backup, UltraInstallCredentials.forCandidate(ksuApp), variantId, onStdout)"))
        assertFalse(install.contains("flow.prepare(base, credential, onStdout)"))
        assertTrue(install.contains("repository.withCurrentImage(sourceSha256)"))
        assertTrue(install.contains("val backup = flow.backupCurrentBoot()"))
    }

    @Test
    fun theGeneratorRefusesAPartialSiteMatchBeforeAPlanExists() {
        val generator = source("src/main/java/dev/maplesu/manager/ultrakernel/UltraKernelSpecGenerator.kt")
        assertTrue(generator.contains("requireFullSiteCoverage(evidence)"))
        // 调用点在 "build the plan" 之前：半成品 plan 连对象都不该出现。
        assertTrue(
            generator.indexOf("requireFullSiteCoverage(evidence)") <
                generator.indexOf("UltraKernelPlan.fromRecords("),
        )
        val codes = source("src/main/java/dev/maplesu/manager/ultrakernel/UltraKernelGenerateException.kt")
        assertTrue(codes.contains("\"ULTRA_KERNEL_GENERATE_SITE_COVERAGE\""))
    }

    @Test
    fun thePreviewDoesNotClaimThatItGeneratedTheCandidate() {
        assertFalse(installScreen().contains("generatedOnDevice = generatedOnDevice"))
        val flashIt = source("src/main/java/com/maplesu/core/ui/screen/flash/FlashUtils.kt")
        assertTrue(flashIt.contains("val generatedOnDevice: Boolean = false"))
        assertTrue(flashScreen().contains("generatedOnDevice = (flashIt as? FlashIt.FlashBoot)?.generatedOnDevice == true"))
        assertTrue(flashState().contains("val generatedOnDevice: Boolean = false"))
        assertTrue(flashBanner().contains("R.string.install_boot_generated_notice"))
    }

    // ── 安装列表：目标模式、安装来源、按需出现的分区与检测信息 ──────────────────

    @Test
    fun bothSkinsKeepTheNativeSelectionListAndOneNextButton() {
        listOf("Material" to installMaterial(), "Miuix" to installMiuix()).forEach { (skin, text) ->
            val mode = text.indexOf("InstallTargetSelectionSection(")
            val source = text.indexOf("SelectInstallMethod(")
            val partition = text.indexOf("InstallPartitionSection(")
            val information = text.indexOf("InstallTargetStepSection(")
            val next = text.indexOf("R.string.install_next")
            assertTrue(
                "$skin：安装来源、目标模式、分区、检测信息和下一步必须可达且有序",
                source > 0 && source < mode && mode < partition && partition < information && information < next,
            )
            assertFalse("$skin：Ultra 也要展示固定的目标镜像", text.contains("if (!uiState.partitionFixed)"))
            assertTrue("$skin：选定来源后才展示检测信息", text.contains("if (uiState.hasInstallSource)"))
            assertTrue("$skin：下一步使用真实可安装状态", text.contains("enabled = uiState.canInstall"))
            assertTrue("$skin：来源选择在事务中锁定", text.contains("enabled = state.targetSelectable"))
            assertEquals("$skin：只有一个下一步", 1, Regex("R.string.install_next").findAll(text).count())
            assertFalse("$skin：原生列表不再重复编号标题", text.contains("InstallStepTitle("))
        }
    }

    @Test
    fun automaticTargetInformationIsReadOnlyAndHidesProbeDiagnostics() {
        val steps = installSteps()
        assertTrue(steps.contains("SegmentedDropdownItem("))
        assertTrue(steps.contains("OverlayDropdownPreference("))
        assertTrue(steps.contains("enabled = state.targetSelectable"))
        val fixedPartition = steps.substringAfter("if (state.partitionFixed) {")
            .substringBefore("val items =")
        assertTrue(fixedPartition.contains("InstallTargetInfo("))
        assertTrue(fixedPartition.contains("R.string.install_partition_fixed, state.partition.wire"))
        assertTrue(fixedPartition.contains("return"))
        assertFalse(fixedPartition.contains("Dropdown"))
        val info = steps.substringAfter("private fun InstallTargetInfo(").substringBefore("private fun KernelWarning(")
        assertTrue(info.contains("ListItem("))
        assertTrue(info.contains("BasicComponent("))
        assertFalse(info.contains("onClick"))
        assertFalse(info.contains("onCheckedChange"))
        assertFalse(steps.contains("SegmentedRadioItem("))
        assertFalse(steps.contains("CheckboxPreference("))
        assertFalse(steps.contains("decision.detail"))
        assertFalse(steps.contains("decision.code"))
        assertFalse(steps.contains("R.string.install_boot_error_code"))
        assertFalse(steps.contains("knownReleases"))
    }

    @Test
    fun theInstallPageOffersBothTargetModesAndAPartitionChoice() {
        val screen = installScreen()
        // 步骤 ① 真的可以选择 Normal（默认仍是 Ultra：`InstallTargetMode.DEFAULT`）。
        assertTrue("默认目标仍是 Ultra", screen.contains("initialTarget: InstallTargetMode = InstallTargetMode.DEFAULT"))
        assertTrue("迁移入口可指定本次安装目标", screen.contains("mutableStateOf(initialTarget)"))
        assertTrue("必须能选到 Normal", screen.contains("InstallTargetMode.NORMAL"))
        // 分区选择贯通到修补请求（不是只画一个没有消费者的选择器）。
        assertTrue(screen.contains("installMethod is InstallMethod.SelectFile && imagePartition != null"))
        assertTrue(screen.contains("val requestedPartition = selectedPartition.wire"))
        assertTrue(screen.contains("partition = requestedPartition"))
        assertTrue(screen.contains("targetMode = mode"))
        // 选择与"当前已激活模式"无关，也不持久化（每次打开都是默认 Ultra）。
        assertFalse("安装页不得持久化目标模式", screen.contains("targetModeStore"))
        // 旧的选择器组件名不得复活（它在一次重构里被删除过）。
        assertFalse(screen.contains("InstallTargetModeRow"))
        listOf(installMaterial(), installMiuix()).forEach { skin ->
            assertFalse(skin.contains("InstallTargetModeRow"))
        }
        assertFalse(File(moduleRoot(), "install/InstallTargetModeRow.kt").exists())
        assertFalse(File(moduleRoot(), "install/InstallTargetModeUi.kt").exists())
    }

    @Test
    fun thePartitionPolicyIsTheOnlyPlaceThatDecidesFixedOrSelectable() {
        // Ultra 使用实际选项；Normal 使用既有分区策略。值模型行为由 availability 测试覆盖。
        val state = source("src/main/java/com/maplesu/core/ui/screen/install/InstallUiState.kt")
        assertTrue(
            state.contains("val partitionFixed: Boolean get() = if (targetMode == InstallTargetMode.ULTRA)"),
        )
        assertTrue(state.contains("partitionOptions.size <= 1"))
        assertTrue(state.contains("else InstallPartitionPolicy.isFixed(targetMode)"))
        val steps = installSteps()
        assertTrue("步骤 ① 的分区区块必须按 state.partitionFixed 决定能否操作", steps.contains("state.partitionFixed"))
        assertTrue(steps.contains("onSelectPartition"))
        assertTrue("页面把点击交给纯函数", installScreen().contains("InstallPartitionSelection.select("))
    }

    @Test
    fun theInstallPageOnlyOffersTheFileAndTheCurrentBoot() {
        val screen = installScreen()
        assertTrue(screen.contains("add(InstallMethod.SelectFile(summary = selectFileTip))"))
        assertTrue(screen.contains("add(InstallMethod.DirectInstall)"))
        assertFalse("读取当前分区应在点击后请求权限", screen.contains("if (installerTarget != null) add(InstallMethod.DirectInstall)"))
        assertFalse("下载 boot 入口不属于四步", screen.contains("InstallMethod.DownloadFile("))
        assertFalse("迁移入口不属于四步（仍在首页）", screen.contains("InstallMethod.MigrateToSelf"))
        assertFalse(screen.contains("DownloadDialog("))
        assertEquals(
            "安装页只能构造一次安装请求（= 一次确认）",
            1,
            Regex(Regex.escape("navigator.push(Route.Flash(request))")).findAll(screen).count(),
        )
    }

    @Test
    fun theKernelStepListsOnlyPatchableTargets() {
        val flow = installFlow()
        // Availability belongs to the target renderer, independently of historical defaults.
        assertTrue(flow.contains("generation.plan(boot, facts.release)"))
        assertFalse(flow.contains("facts.variants.firstOrNull { it.isDefault }"))
        assertFalse(flow.contains("DEFAULT_VARIANT_UNAVAILABLE"))
        val steps = installSteps()
        assertTrue(steps.contains("R.string.install_step_kernel_unavailable"))
        assertFalse("内部错误码留在诊断结果中", steps.contains("R.string.install_boot_error_code"))
        // 步骤 ③ 的可见内容里不再有等级徽标 / 变体 / 条数。
        assertFalse(steps.contains("install_boot_support_supported"))
        assertFalse(steps.contains("install_boot_support_generated"))
        assertFalse(steps.contains("install_boot_variant_"))
        assertFalse(steps.contains("install_boot_confirm_"))
    }

    @Test
    fun theIntermediaryDeveloperPanelsAreGone() {
        val root = moduleRoot()
        // 支持等级 / 变体 / 事务回读卡片整块删除（不是不显示）。
        assertFalse(File(root, "install/InstallBootFlowSection.kt").exists())
        assertFalse(File(root, "install/InstallTransactionCard.kt").exists())
        val flow = installFlow()
        assertFalse(flow.contains("BootSupportLevel"))
        assertFalse(flow.contains("BootVariantChoice"))
        assertFalse(flow.contains("showVariantPicker"))
    }

    // ── 步骤 ④：文件修补显示路径；直接安装显示日志与保存 / 重启 ───────────────────

    @Test
    fun onlyTheSelectedFileResultHidesThePatchLog() {
        assertTrue(flashScreen().contains("val patchOnly = flashIt is FlashIt.FlashBoot && flashIt.boot != null"))
        assertTrue(flashScreen().contains("val showLog = !patchOnly"))
        assertTrue(flashState().contains("val showLog: Boolean = true"))
        // 文件修补不显示日志；直接安装保留日志和保存日志入口。
        assertTrue(flashMaterial().contains("if (state.showLog)"))
        assertTrue(flashMiuix().contains("showSave = state.showLog"))
        // 结果页不再有事务状态串 / 回读 / 回滚 / 补丁条数。
        assertFalse(flashBanner().contains("install_boot_task_"))
        assertFalse(flashState().contains("transactionId"))
        assertFalse(flashScreen().contains("patchEdits"))
        assertFalse(flashScreen().contains("records.size"))
    }

    @Test
    fun theDirectInstallSuccessPageOffersItsOwnBootAndReboot() {
        assertTrue(flashState().contains("val onSaveBoot: () -> Unit"))
        assertTrue(flashScreen().contains("UltraKernelBootExport.save("))
        listOf(flashMaterial(), flashMiuix()).forEach { skin ->
            val actions = skin.substringAfter("private fun DirectInstallActions(")
                .substringBefore("private fun FlashResultActions(")
            assertTrue(skin.contains("DirectInstallActions(state, actions, Modifier.align(Alignment.BottomCenter))"))
            assertTrue(actions.contains("R.string.install_boot_action_save"))
            assertTrue(actions.contains("actions.onSaveBoot"))
            assertTrue(actions.contains("if (state.canSaveBoot)"))
            assertTrue(actions.contains("FloatingActionButton("))
            assertTrue(actions.contains("if (!state.actionInProgress)"))
            assertTrue(actions.contains("if (state.showRebootAction) actions.onReboot() else actions.onClose()"))
        }
        // 「保存 boot」导出的必须是**通过回读校验**的那一份：
        // 文件摘要和尺寸要与本次提交候选相等，导出本身再回读一次。
        val export = bootExport()
        assertTrue(export.contains("candidate.sha256"))
        assertTrue(export.contains("candidate.size"))
        assertFalse(export.contains("sortedByDescending"))
        assertTrue(export.contains("UltraKernelSpec.sha256(image)"))
        assertTrue(export.contains("BootCandidatePublisher.publish(context, image, sha256)"))
    }

    // ── one-tap：仍然只有一次点击，没有"继续"对话框 ─────────────────────────────

    @Test
    fun theInstallFlowStillAsksExactlyOnce() {
        assertFalse(flashScreen().contains("confirmContinue"))
        val vm = source("src/main/java/dev/maplesu/manager/installer/InstallerFlashViewModel.kt")
        val attach = vm.substringAfter("fun attach(").substringBefore("fun continueModeSwitch(")
        assertTrue(attach.contains("if (attachedTask == taskId) return"))
        assertTrue(attach.contains("attachedTask = taskId"))
        assertEquals(1, Regex("val result = execute").findAll(attach).count())
        assertTrue(attach.contains("resume && transactional) observe(context.applicationContext, taskId)"))
        assertFalse(vm.contains("InstallerServiceClient.continueInstallation("))
        assertTrue(vm.contains("InstallerServiceClient.cancelInstallation(context, taskId)"))
    }

    // ── 文案：两个 locale 都在，且不再出现开发者话术 ─────────────────────────────

    @Test
    fun bothLocalesDefineTheFourStepStrings() {
        val keys = listOf(
            "install_step_ultra",
            "install_step_ultra_summary",
            "install_step_boot",
            "install_step_kernel",
            // 步骤 ① 的目标模式 + 目标分区（Normal 可选 init_boot / boot）
            "install_step_target_mode",
            "install_step_partition",
            "install_partition_fixed",
            "install_target_normal_ready",
            "install_target_information",
            "install_kernel_pairing_required",
            "install_kernel_image_unreadable",
            "install_kernel_unavailable_hint",
            "install_kernel_boot_image_hint",
            // 直装：从当前设备分区读源镜像
            "install_partition_extract_start",
            "install_partition_extract_done",
            "install_partition_extract_failed",
            "install_target_blocker_partition_fixed",
            "install_target_blocker_partition_unsupported",
            "install_step_kernel_waiting",
            "install_step_kernel_selected",
            "install_step_kernel_unavailable",
            "install_step4_patching",
            "install_step4_success",
            "install_step4_failed",
            "install_boot_action_save",
            "install_boot_action_flash",
            "install_boot_save_done",
            "install_boot_save_failed",
            "install_boot_generated_notice",
            "install_boot_outcome_written",
            "install_boot_outcome_saved_only",
            "install_boot_error_code",
        )
        listOf("values", "values-zh-rCN").forEach { locale ->
            val file = File("src/main/res/$locale/install_boot_flow_strings.xml")
            assumeTrue(file.path, file.isFile)
            val text = file.readText()
            keys.forEach { key ->
                assertTrue("$locale is missing $key", text.contains("<string name=\"$key\">"))
            }
        }
    }

    @Test
    fun theFourStepCopyHasNoDeveloperVocabulary() {
        listOf("values", "values-zh-rCN").forEach { locale ->
            val file = File("src/main/res/$locale/install_boot_flow_strings.xml")
            assumeTrue(file.path, file.isFile)
            // 注释可以解释"删了什么"，用户看不到；只检查真正会渲染出来的 <string>。
            val text = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
                .replace(file.readText(), "")
            listOf(
                "install_boot_variant_",
                "install_boot_support_supported",
                "install_boot_support_generated",
                "install_boot_support_unsupported",
                "install_boot_confirm_summary",
                "install_boot_confirm_generated_note",
                "install_boot_task_",
                "install_boot_outcome_generated",
            ).forEach { retired ->
                assertFalse("$locale still declares $retired", text.contains(retired))
            }
            assertFalse("$locale still says 实验档", text.contains("实验档"))
            assertFalse("$locale still says 补丁变体", text.contains("补丁变体"))
            assertFalse("$locale still says 兼容内核", text.contains("兼容内核"))
        }
    }

    @Test
    fun bothPackagedAndGeneratedExportsRetainTheirCandidateIdentity() {
        val body = ultraInstall().substringAfter("internal fun ultraImageBuildCandidate(")
            .substringBefore("private fun ultraGeneratedPreparation(")
        assertTrue(body.contains("UltraInstallCredentials.retainCandidate("))
        assertFalse("Packaged boot exports need their embedded identity too",
            body.contains("if (preparation.generated) UltraInstallCredentials.retainCandidate("))
    }
}
