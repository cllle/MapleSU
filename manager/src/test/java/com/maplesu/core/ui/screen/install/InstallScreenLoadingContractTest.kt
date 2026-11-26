package com.maplesu.core.ui.screen.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Guards the install page's non-blocking reads, click guards and the single confirm. */
class InstallScreenLoadingContractTest {
    private fun screen(): String {
        val file = File("src/main/java/com/maplesu/core/ui/screen/install/InstallScreen.kt")
        assumeTrue(file.path, file.isFile)
        return file.readText()
    }

    private fun uiState(): String {
        val file = File("src/main/java/com/maplesu/core/ui/screen/install/InstallUiState.kt")
        assumeTrue(file.path, file.isFile)
        return file.readText()
    }

    /**
     * 去掉 Kotlin 注释后的源码（字符串字面量原样保留）。
     *
     * 四步流程的改动让 `InstallScreen.kt` 的文档注释开始**解释**"为什么这页没有迁移入口"
     * （`HomeScreen` 的 `Route.Migration` 才是它的家）。注释不是这页构造的路由，
     * 但 `source.contains("Route.Migration")` 分不出来。这里先把注释剥掉再断言，
     * 断言本身仍然是真实的：只要**代码**里出现 `Route.Migration` 就会失败。
     */
    private fun code(source: String): String {
        val out = StringBuilder(source.length)
        var i = 0
        while (i < source.length) {
            val c = source[i]
            val next = source.getOrNull(i + 1)
            when {
                c == '/' && next == '/' -> while (i < source.length && source[i] != '\n') i++
                c == '/' && next == '*' -> {
                    i += 2
                    while (i < source.length && !(source[i] == '*' && source.getOrNull(i + 1) == '/')) i++
                    i += 2
                }
                c == '"' -> {
                    out.append(c)
                    i++
                    while (i < source.length && source[i] != '"') {
                        if (source[i] == '\\' && i + 1 < source.length) {
                            out.append(source[i]).append(source[i + 1])
                            i += 2
                        } else {
                            out.append(source[i])
                            i++
                        }
                    }
                    if (i < source.length) {
                        out.append(source[i])
                        i++
                    }
                }
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.toString()
    }

    @Test
    fun pendingUsesTheExistingSessionWithoutReadingHomeFacts() {
        val source = screen()
        assertTrue(source.contains("WorkModeSwitchSession.executing"))
        assertFalse(source.contains("HomeWorkModeSwitchRuntime"))
        assertFalse(source.contains("isSwitchPending()"))
        assertFalse(source.contains("statusAsync("))
    }

    @Test
    fun kernelReleaseReadRunsOffMainAndHasAnExplicitLoadingState() {
        val source = screen()
        assertTrue(source.contains("var kernelReleaseLoading by remember { mutableStateOf(true) }"))
        assertTrue(source.contains("withContext(Dispatchers.IO) { readDeviceKernelRelease() }"))
        assertFalse(source.contains("remember { readDeviceKernelRelease() }"))
        val effect = source.substringAfter("LaunchedEffect(Unit) {").substringBefore("LifecycleResumeEffect(Unit)")
        assertTrue(effect.contains("deviceKernelRelease = withContext(Dispatchers.IO)"))
        assertTrue(effect.indexOf("readDeviceKernelRelease()") < effect.indexOf("kernelReleaseLoading = false"))
    }

    @Test
    fun pageResumeDoesNotPreparePartitionsAndPauseCancelsItsOwnReads() {
        val source = screen()
        val effect = source.substringAfter("LifecycleResumeEffect(context, detectionRevision, hiddenRevision) {")
            .substringBefore("val selectFileTip")
        assertFalse(effect.contains("StockInstallPreflight"))
        assertFalse(effect.contains("observeBootChain"))
        assertFalse(effect.contains("installedCarrierPartition"))
        assertTrue(effect.contains("pageResumed = false"))
        assertFalse(effect.contains("rootGranted = false"))
        assertFalse(effect.contains("EnvironmentProbesAdapter(context).suPresent()"))
        assertFalse(effect.contains("managerRootAvailable("))
        assertFalse(effect.contains("RootExec."))
        assertFalse(effect.contains("UltraKeyPairedStore"))
        assertFalse(effect.contains("UltraRootKeyStore"))
        assertTrue(effect.contains("onPauseOrDispose"))
        assertTrue(effect.contains("job.cancel()"))
    }

    @Test
    fun actionGuardsRecheckPendingBeforeStartingTheTransaction() {
        val source = screen()
        val action = source.substringAfter("val onInstall = onInstall@{").substringBefore("val selectImageLauncher")
        assertTrue(action.contains("if (!pageResumed || installInFlight || WorkModeSwitchSession.executing) return@onInstall"))
        assertTrue(action.contains("installMethodOptions.none { it::class == method::class }"))
        assertFalse(action.contains("bootFlow?.target ?: return@onInstall"))
        assertFalse(action.contains("InstallTargetDispatch.decide("))
        assertTrue(action.contains("installInFlight = true"))
        assertTrue(action.indexOf("installInFlight = true") < action.indexOf("navigator.push(Route.Flash(request))"))
        // 步骤 ① 的选择在事务开始那一刻快照，之后改选择不影响本次事务。
        assertTrue(action.contains("val mode = targetMode"))
        assertTrue(action.contains("val selectedPartition = partition"))
        assertTrue(action.contains("val requestedPartition = selectedPartition.wire"))
        assertTrue(action.contains("partition = requestedPartition"))
    }

    @Test
    fun theNextButtonGatesOnTheVisibleSelectionsOfTheSelectedMode() {
        val state = uiState()
        val canInstall = state.substringAfter("val canInstall: Boolean")
        assertFalse("预览不作为实际生成结果", canInstall.contains("bootFlow?.selectable"))
        assertFalse("Normal 执行按实际源镜像判断", canInstall.contains("normalDecision is InstallTargetDecision.Ready"))
        assertTrue("事务中下一步必须禁用", canInstall.contains("!targetSelectable"))
        assertTrue("下一步必须依赖已选择的可用来源", canInstall.contains("!hasInstallSource"))
        val source = state.substringAfter("val hasInstallSource: Boolean").substringBefore("val canInstall: Boolean")
        assertTrue("步骤 ② 必须选中一个现有来源", source.contains("installMethodOptions.any"))
        assertTrue("选文件来源必须有 uri", source.contains("method.uri != null"))
        assertFalse(state.contains("InstallTargetModeUi"))
    }

    @Test
    fun kernelLoadingHintExistsInBothLocales() {
        listOf("values", "values-zh-rCN").forEach { locale ->
            val file = File("src/main/res/$locale/strings_workmode.xml")
            assumeTrue(file.path, file.isFile)
            assertTrue(file.readText().contains("<string name=\"install_target_kernel_loading\">"))
        }
    }

    @Test
    fun theInstallPageNoLongerBuildsTheMigrationRoute() {
        // 只看代码：文档注释里写着"迁移入口在首页的 `Route.Migration`"是**解释**，
        // 不是这页构造了那条路由（60-ui-four-steps §2）。注释剥掉后断言从头到尾都真实。
        val source = code(screen())
        assertFalse("迁移入口在首页，不在四步流程里", source.contains("Route.Migration"))
        assertFalse(source.contains("MigrationViewModel"))
        // 「下一步」仍然只有一个落点：刷写页。
        assertEquals(1, Regex(Regex.escape("navigator.push(Route.Flash(request))")).findAll(source).count())
    }
}
