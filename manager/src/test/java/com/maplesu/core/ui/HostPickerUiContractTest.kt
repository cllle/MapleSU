package com.maplesu.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 「选择寄生应用」/「解除寄生」的 UI 契约（源码/资源断言，JVM）：
 * 页面接线、二次确认内容、未选禁用、失败原因与分步结果、文案红线。
 */
class HostPickerUiContractTest {

    private fun read(path: String): String? = File(path).takeIf { it.isFile }?.readText()

    private val screenPath = "src/main/java/dev/maplesu/manager/workmode/ui/LiteHiddenScreen.kt"
    private val vmPath = "src/main/java/dev/maplesu/manager/workmode/ui/LiteHiddenViewModel.kt"
    private val pagePath = "src/main/java/com/maplesu/core/ui/screen/host/HostPickerScreen.kt"
    private val statePath = "src/main/java/com/maplesu/core/ui/screen/host/HostPickerUiState.kt"
    private val policyPath = "src/main/java/dev/maplesu/manager/litehidden/HiddenHostPolicy.kt"
    private val backendPath = "src/main/java/dev/maplesu/manager/litehidden/HiddenModeBackend.kt"
    private val zhPath = "src/main/res/values-zh-rCN/maplesu_litehidden.xml"

    // 1
    @Test
    fun hiddenPageOpensHostPickerThenConfirm() {
        val screen = read(screenPath) ?: run { assumeTrue(false); return }
        val vm = read(vmPath) ?: run { assumeTrue(false); return }
        assertTrue("隐藏页未挂宿主选择页", screen.contains("HostPickerPage("))
        assertTrue("未构造宿主页动作", screen.contains("HostPickerActions("))
        assertTrue("未接二次确认", screen.contains("HiddenConfirmDialog("))
        assertTrue("主按钮应打开选择页", vm.contains("HostPickerPolicy.open("))
        assertTrue("确认后才激活", vm.contains("HostPickerPolicy.startRunning("))
        assertTrue(vm.contains("LiteHiddenRuntimeProxy.activate("))
    }

    // 2
    @Test
    fun confirmDialogShowsHostPortAndCopyLink() {
        val screen = read(screenPath) ?: run { assumeTrue(false); return }
        assertTrue(screen.contains("lite_hidden_confirm_title"))
        assertTrue(screen.contains("lite_hidden_confirm_body"))
        assertTrue("确认弹窗必须显示宿主", screen.contains("host_picker_confirm_host"))
        assertTrue(screen.contains("ui.hostPicker.selectedDisplayName"))
        assertTrue("确认弹窗必须显示端口", screen.contains("host_picker_confirm_port"))
        assertTrue(screen.contains("ui.hostPicker.portText"))
        assertTrue("确认弹窗必须有复制恢复链接", screen.contains("lite_hidden_copy_link"))
        assertTrue(screen.contains("onCopyRecoveryLink"))
        assertTrue(screen.contains("lite_hidden_confirm_enable"))
        assertTrue("确认按钮按状态禁用", screen.contains("confirmEnabled = ui.canActivate"))
    }

    // 3
    @Test
    fun hostPageListsIconNamePackageAndDisablesNextWithoutSelection() {
        val page = read(pagePath) ?: run { assumeTrue(false); return }
        assertTrue(page.contains("host_picker_title"))
        assertTrue(page.contains("host_picker_subtitle"))
        assertTrue("风险提示必须是一行", page.contains("host_picker_risk"))
        assertTrue("图标", page.contains("Image(icon, null"))
        assertTrue("名称", page.contains("candidate.displayName"))
        assertTrue("包名", page.contains("candidate.packageName"))
        assertTrue("未选择时下一步禁用", page.contains("enabled = ui.canGoNext"))
        assertTrue("加载/空/失败都要有文案", page.contains("host_picker_loading"))
        assertTrue(page.contains("host_picker_empty"))
        assertTrue("失败显示原因", page.contains("ui.failureRes"))
        assertTrue("失败显示分步结果", page.contains("HostStepList(ui.steps)"))
    }

    // 4
    @Test
    fun deactivateEntryAndStepsAreWired() {
        val screen = read(screenPath) ?: run { assumeTrue(false); return }
        val vm = read(vmPath) ?: run { assumeTrue(false); return }
        assertTrue("隐藏页缺少解除入口", screen.contains("lite_hidden_deactivate"))
        assertTrue(screen.contains("ui.showDeactivate"))
        assertTrue(screen.contains("HiddenDeactivateDialog("))
        assertTrue("解除需二次确认", screen.contains("lite_hidden_deactivate_title"))
        assertTrue(screen.contains("lite_hidden_deactivate_confirm"))
        assertTrue("解除步骤结果要展示", screen.contains("HostStepList(state.steps)"))
        assertTrue(vm.contains("LiteHiddenRuntimeProxy.deactivate("))
        assertTrue(vm.contains("HostPickerPolicy.deactivateFinished("))
        assertTrue(screen.contains("state.failureRes"))
    }

    @Test
    fun cleanupFailureHasASpecificHumanReadableStepLabel() {
        val page = requireNotNull(read(pagePath))
        assertTrue(page.contains("HiddenDeactivateStep.CLEANUP_RECOVERY.wire -> R.string.lite_hidden_deactivate_step_cleanup"))
        val zh = requireNotNull(read(zhPath))
        assertTrue(zh.contains("<string name=\"lite_hidden_deactivate_step_cleanup\">清理恢复资料</string>"))
        val en = requireNotNull(read("src/main/res/values/maplesu_litehidden.xml"))
        assertTrue(en.contains("<string name=\"lite_hidden_deactivate_step_cleanup\">Clean recovery data</string>"))
    }

    // 5
    @Test
    fun candidateFilteringRulesLiveInPurePolicy() {
        val policy = read(policyPath) ?: run { assumeTrue(false); return }
        listOf("isSystem", "SELF", "NO_ARM64", "NO_NATIVE_LIB", "INVALID_PACKAGE").forEach {
            assertTrue("过滤规则缺少 $it", policy.contains(it))
        }
        assertTrue("候选排序稳定", policy.contains("sortedWith"))
        assertTrue("arm64 判定", policy.contains("arm64-v8a"))
    }

    // 6
    @Test
    fun backendContractExposesActivateAndDeactivate() {
        val backend = read(backendPath) ?: run { assumeTrue(false); return }
        assertTrue(backend.contains("suspend fun activate(hostPackage: String, port: Int)"))
        assertTrue(backend.contains("suspend fun deactivate(hostPackage: String)"))
        assertTrue("未接线必须不可用", backend.contains("HiddenFailure.BACKEND_UNAVAILABLE"))
        assertTrue("激活步骤固定", backend.contains("precheck") && backend.contains("host_so"))
        assertTrue(
            "解除三步固定",
            backend.contains("restore_host_so") && backend.contains("revoke_su_auth") && backend.contains("stop_daemon"),
        )
        assertTrue("分步结果结构", backend.contains("data class HiddenStepResult"))
    }

    // 7
    @Test
    fun hostCopyLinesAreShortAndForbiddenWordFree() {
        val zh = read(zhPath) ?: run { assumeTrue(false); return }
        val newKeys = listOf(
            "host_picker_title",
            "host_picker_subtitle",
            "host_picker_risk",
            "host_picker_empty",
            "host_picker_next",
            "host_picker_running",
            "lite_hidden_deactivate",
            "lite_hidden_deactivate_title",
            "lite_hidden_deactivate_body",
            "lite_hidden_reason_host",
            "lite_hidden_reason_host_invalid",
            "lite_hidden_reason_host_scan",
            "lite_hidden_reason_host_so",
            "lite_hidden_reason_host_backup",
            "lite_hidden_reason_deactivate",
        )
        newKeys.forEach { key ->
            val value = Regex("<string name=\"$key\">([^<]*)</string>").find(zh)?.groupValues?.get(1)
            assertTrue("缺少文案 $key", value != null)
            assertTrue("「$value」超过 15 字", value!!.length <= 15)
            listOf("伪卸载", "等级", "痕迹", "演练", "高级", "Intent", "Logcat", "恢复端口").forEach { word ->
                assertFalse("「$value」含禁用词「$word」", value.contains(word))
            }
        }
    }

    // 8
    @Test
    fun pickerStateIsPureAndTestable() {
        val state = read(statePath) ?: run { assumeTrue(false); return }
        assertTrue("阶段机", state.contains("enum class HostPickerPhase"))
        assertTrue("未选禁用", state.contains("val canGoNext"))
        assertTrue("确认可用性", state.contains("val canConfirm"))
        assertTrue("分步 UI", state.contains("data class HostStepUi"))
        assertTrue("解除状态", state.contains("data class DeactivateUiState"))
        assertTrue("跃迁集中", state.contains("object HostPickerPolicy"))
        // 纯 Kotlin：不得引入 Android Context/PackageManager（扫描在 workmode/ui 层）。
        assertFalse(state.contains("PackageManager"))
        assertFalse(state.contains("Context"))
    }

    // 9
    @Test
    fun hostScanUsesConfigurableSource() {
        val source = read("src/main/java/dev/maplesu/manager/workmode/ui/LiteHiddenHostSource.kt")
            ?: run { assumeTrue(false); return }
        assertTrue("可注入替换", source.contains("interface HostCandidateSource"))
        assertTrue(source.contains("var current: HostCandidateSource?"))
        assertTrue("失败要有原因码", source.contains("HOST_SCAN_FAILED"))
        assertTrue("只认真实 .so", source.contains(".endsWith(\".so\")"))
        assertTrue("系统应用按 FLAG_SYSTEM 判定", source.contains("FLAG_SYSTEM"))
    }

    // 10
    @Test
    fun noFakeUninstallWordingAnywhereInHiddenSources() {
        val files = listOf(screenPath, vmPath, pagePath, statePath, policyPath, backendPath)
        files.mapNotNull { read(it) }.forEach { text ->
            listOf("伪卸载", "演练", "隐藏等级").forEach { word ->
                assertFalse("源码禁止出现「$word」", text.contains(word))
            }
        }
        val backend = read(backendPath)
        if (backend != null) {
            assertFalse("后端不再以卸载为契约", backend.contains("pm uninstall"))
        }
        assertTrue("被检查的源码必须存在", read(screenPath) != null)
    }
}
