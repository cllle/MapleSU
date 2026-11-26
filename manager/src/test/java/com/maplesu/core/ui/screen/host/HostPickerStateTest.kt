package com.maplesu.core.ui.screen.host

import dev.maplesu.manager.litehidden.HiddenStepResult
import dev.maplesu.manager.litehidden.HostCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「选择寄生应用」页状态机（JVM 纯映射）：加载→列表→选择→确认→执行→失败/关闭，
 * 以及解除寄生的三步结果展示。UI 与 ViewModel 只调用这些函数，规则只在这里定义。
 */
class HostPickerStateTest {

    private val chat = HostCandidate("com.example.chat", "Chat")
    private val mail = HostCandidate("com.example.mail", "Mail")
    private val list = listOf(chat, mail)

    private fun listed(): HostPickerUiState =
        HostPickerPolicy.loaded(HostPickerPolicy.open(HostPickerPolicy.initial(), "18427", true), list)

    // 1
    @Test
    fun openStartsInLoadingAndClearsPreviousState() {
        val state = HostPickerPolicy.open(HostPickerPolicy.initial(), "18427", true)
        assertTrue(state.visible)
        assertEquals(HostPickerPhase.LOADING, state.phase)
        assertTrue(state.candidates.isEmpty())
        assertNull(state.selected)
        assertNull(state.failureRes)
        assertEquals("18427", state.portText)
    }

    // 2
    @Test
    fun loadedShowsListWithCandidates() {
        val state = listed()
        assertEquals(HostPickerPhase.LIST, state.phase)
        assertEquals(2, state.candidates.size)
        assertFalse(state.isEmpty)
        assertFalse("未选择时不能下一步", state.canGoNext)
    }

    // 3
    @Test
    fun emptyListIsNotAnErrorButCannotProceed() {
        val state = HostPickerPolicy.loaded(
            HostPickerPolicy.open(HostPickerPolicy.initial(), "18427", true),
            emptyList(),
        )
        assertTrue(state.isEmpty)
        assertFalse(state.canGoNext)
    }

    // 4
    @Test
    fun scanFailureKeepsReasonInsteadOfEmptySuccess() {
        val state = HostPickerPolicy.loadFailed(
            HostPickerPolicy.open(HostPickerPolicy.initial(), "18427", true),
            "HOST_SCAN_FAILED",
            7,
        )
        assertEquals(HostPickerPhase.FAILED, state.phase)
        assertEquals("HOST_SCAN_FAILED", state.failureCode)
        assertEquals(7, state.failureRes)
        assertTrue(state.candidates.isEmpty())
    }

    // 5
    @Test
    fun selectOnlyAcceptsPackagesFromTheList() {
        val selected = HostPickerPolicy.select(listed(), "com.example.mail")
        assertEquals("com.example.mail", selected.selected)
        assertEquals("Mail", selected.selectedDisplayName)
        assertTrue(selected.canGoNext)

        val forged = HostPickerPolicy.select(listed(), "com.example.not.in.list")
        assertNull("列表外的包名不得被选中", forged.selected)
        assertFalse(forged.canGoNext)
    }

    // 6
    @Test
    fun cannotSelectBeforeListIsReady() {
        val loading = HostPickerPolicy.open(HostPickerPolicy.initial(), "18427", true)
        assertNull(HostPickerPolicy.select(loading, "com.example.chat").selected)
    }

    // 7
    @Test
    fun nextRequiresSelectionAndOpensConfirm() {
        val noSelection = listed()
        assertEquals("未选择时 next 无效果", HostPickerPhase.LIST, HostPickerPolicy.next(noSelection).phase)

        val confirming = HostPickerPolicy.next(HostPickerPolicy.select(listed(), "com.example.chat"))
        assertEquals(HostPickerPhase.CONFIRM, confirming.phase)
        assertTrue(confirming.isConfirming)
        assertTrue(confirming.canConfirm)
        assertEquals("Chat", confirming.selectedDisplayName)
        assertEquals("18427", confirming.portText)
    }

    // 8
    @Test
    fun confirmIsBlockedByInvalidPort() {
        val bad = HostPickerPolicy.open(HostPickerPolicy.initial(), "80", false)
        val state = HostPickerPolicy.next(HostPickerPolicy.select(HostPickerPolicy.loaded(bad, list), "com.example.chat"))
        assertFalse("端口非法时二次确认按钮禁用", state.canConfirm)
        assertEquals("执行中状态不得被打开", HostPickerPhase.CONFIRM, HostPickerPolicy.startRunning(state).phase)
    }

    // 9
    @Test
    fun runningOnlyFromConfirmAndCannotConfirmTwice() {
        val confirming = HostPickerPolicy.next(HostPickerPolicy.select(listed(), "com.example.chat"))
        val running = HostPickerPolicy.startRunning(confirming)
        assertEquals(HostPickerPhase.RUNNING, running.phase)
        assertTrue(running.isRunning)
        assertFalse("执行中不能再次确认", running.canConfirm)
        assertEquals("执行中不可重复进入确认", HostPickerPhase.RUNNING, HostPickerPolicy.startRunning(running).phase)
    }

    // 10
    @Test
    fun backToListKeepsSelectionAndDismissClearsEverything() {
        val confirming = HostPickerPolicy.next(HostPickerPolicy.select(listed(), "com.example.chat"))
        val back = HostPickerPolicy.backToList(confirming)
        assertEquals(HostPickerPhase.LIST, back.phase)
        assertEquals("com.example.chat", back.selected)

        val dismissed = HostPickerPolicy.dismiss(back)
        assertFalse(dismissed.visible)
        assertEquals(HostPickerPhase.IDLE, dismissed.phase)
        assertNull(dismissed.failureRes)
    }

    // 11
    @Test
    fun reloadKeepsSelectionOnlyWhenHostStillExists() {
        val withSelection = HostPickerPolicy.select(listed(), "com.example.mail")
        val kept = HostPickerPolicy.loaded(withSelection, list)
        assertEquals("com.example.mail", kept.selected)

        val gone = HostPickerPolicy.loaded(withSelection, listOf(chat))
        assertNull("宿主已卸载 → 选择失效", gone.selected)
        assertFalse(gone.canGoNext)
    }

    // 12
    @Test
    fun activationFailureKeepsStepsAndReasonForDisplay() {
        val running = HostPickerPolicy.startRunning(
            HostPickerPolicy.next(HostPickerPolicy.select(listed(), "com.example.chat")),
        )
        val failed = HostPickerPolicy.fail(
            running,
            "HOST_SO_PATCH_FAILED",
            9,
            listOf(
                HiddenStepResult("precheck", ok = true),
                HiddenStepResult("host_so", ok = false, code = "HOST_SO_PATCH_FAILED"),
            ),
        )
        assertEquals(HostPickerPhase.FAILED, failed.phase)
        assertEquals("HOST_SO_PATCH_FAILED", failed.failureCode)
        assertEquals(9, failed.failureRes)
        assertEquals(2, failed.steps.size)
        assertEquals("precheck", failed.steps[0].wire)
        assertTrue(failed.steps[0].ok)
        assertFalse("失败步骤必须显式失败", failed.steps[1].ok)
        assertEquals("HOST_SO_PATCH_FAILED", failed.steps[1].code)
    }

    // 13
    @Test
    fun stepOrderAndFlagsFollowBackendExactly() {
        val steps = HostPickerPolicy.stepsOf(
            listOf(
                HiddenStepResult("webui_ping", ok = true),
                HiddenStepResult("hide_state", ok = false, code = "LITE_HIDDEN_ON_FAILED"),
            ),
        )
        assertEquals(listOf("webui_ping", "hide_state"), steps.map { it.wire })
        assertEquals(listOf(true, false), steps.map { it.ok })
        assertEquals("后端给出的码不得被改写", "LITE_HIDDEN_ON_FAILED", steps[1].code)
        assertTrue(HostPickerPolicy.stepsOf(emptyList()).isEmpty())
    }

    // 14
    @Test
    fun deactivateFlowShowsThreeIndependentStepsOnFailure() {
        var state = HostPickerPolicy.deactivateOpen(HostPickerPolicy.deactivateInitial(), "com.example.chat")
        assertTrue(state.visible)
        assertEquals("com.example.chat", state.hostPackage)
        assertNull(state.doneRes)

        state = HostPickerPolicy.deactivateRunning(state)
        assertTrue(state.running)

        state = HostPickerPolicy.deactivateFinished(
            state,
            success = false,
            doneRes = 1,
            failureCode = "HIDDEN_DEACTIVATE_FAILED",
            failureRes = 2,
            steps = listOf(
                HiddenStepResult("restore_host_so", ok = true),
                HiddenStepResult("revoke_su_auth", ok = false, code = "SU_AUTH_REVOKE_FAILED"),
                HiddenStepResult("stop_daemon", ok = true),
            ),
        )
        assertFalse(state.running)
        assertNull("失败不得显示成功文案", state.doneRes)
        assertEquals("HIDDEN_DEACTIVATE_FAILED", state.failureCode)
        assertEquals(2, state.failureRes)
        assertEquals(3, state.steps.size)
        assertEquals(listOf(true, false, true), state.steps.map { it.ok })
        assertTrue(state.showSteps)
    }

    // 15
    @Test
    fun deactivateSuccessClearsFailureAndShowsDoneCopy() {
        val running = HostPickerPolicy.deactivateRunning(
            HostPickerPolicy.deactivateOpen(HostPickerPolicy.deactivateInitial(), "com.example.mail"),
        )
        val done = HostPickerPolicy.deactivateFinished(
            running,
            success = true,
            doneRes = 3,
            failureCode = "SHOULD_BE_IGNORED",
            failureRes = 4,
            steps = listOf(
                HiddenStepResult("restore_host_so", ok = true),
                HiddenStepResult("revoke_su_auth", ok = true),
                HiddenStepResult("stop_daemon", ok = true),
            ),
        )
        assertEquals(3, done.doneRes)
        assertEquals("", done.failureCode)
        assertNull(done.failureRes)
        assertFalse(done.running)
        assertTrue(done.steps.all { it.ok })
    }

    // 16
    @Test
    fun deactivateDismissResetsDialogState() {
        val opened = HostPickerPolicy.deactivateOpen(HostPickerPolicy.deactivateInitial(), "com.example.chat")
        val dismissed = HostPickerPolicy.deactivateDismiss(opened)
        assertFalse(dismissed.visible)
        assertTrue(dismissed.steps.isEmpty())
        assertNull(dismissed.failureRes)
        assertNull(dismissed.doneRes)
    }

    // 17
    @Test
    fun selectedCandidateIsNullWhenSelectionTextIsStale() {
        val state = HostPickerPolicy.loaded(
            HostPickerPolicy.select(listed(), "com.example.chat"),
            list,
        ).copy(selected = "com.example.vanished")
        assertNull(state.selectedCandidate)
        assertFalse(state.hasSelection)
        assertFalse(state.canGoNext)
        assertFalse("没有选中宿主时确认按钮必须禁用", state.copy(phase = HostPickerPhase.CONFIRM).canConfirm)
    }

    // 18
    @Test
    fun confirmStateKeepsPortSnapshotForActivation() {
        val opened = HostPickerPolicy.open(HostPickerPolicy.initial(), "20000", true)
        val confirming = HostPickerPolicy.next(HostPickerPolicy.select(HostPickerPolicy.loaded(opened, list), "com.example.chat"))
        assertEquals("20000", confirming.portText)
        assertNotNull(confirming.selectedCandidate)
    }

    @Test
    fun searchMatchesNameIgnoringCaseAndTrimsQuery() {
        val state = HostPickerPolicy.search(listed(), "  cHaT  ")
        assertEquals(listOf(chat), state.filteredCandidates)
        assertEquals(list, state.candidates)
    }

    @Test
    fun searchMatchesPackageAndLocalizedName() {
        assertEquals(listOf(mail), HostPickerPolicy.search(listed(), "EXAMPLE.MAIL").filteredCandidates)
        val localized = HostPickerPolicy.loaded(listed(), listOf(HostCandidate("com.example.reader", "阅读助手")))
        assertEquals(1, HostPickerPolicy.search(localized, " 阅读 ").filteredCandidates.size)
    }

    @Test
    fun filteringAndClearingPreserveSelectedPackageEvenOutsideMatches() {
        val selected = HostPickerPolicy.select(listed(), chat.packageName)
        val filtered = HostPickerPolicy.search(selected, "mail")
        assertEquals(listOf(mail), filtered.filteredCandidates)
        assertEquals(chat.packageName, filtered.selected)
        assertTrue(filtered.canGoNext)
        val cleared = HostPickerPolicy.search(filtered, "")
        assertEquals(list, cleared.filteredCandidates)
        assertEquals(chat.packageName, cleared.selected)
    }

    @Test
    fun whitespaceShowsAllAndNoMatchIsDistinctFromNoCandidates() {
        assertEquals(list, HostPickerPolicy.search(listed(), "  ").filteredCandidates)
        val missing = HostPickerPolicy.search(listed(), "missing")
        assertTrue(missing.filteredCandidates.isEmpty())
        assertFalse(missing.isEmpty)
    }

    @Test
    fun queryDoesNotChangeDuringConfirmOrRunningAndNewOpenResetsIt() {
        val queried = HostPickerPolicy.search(HostPickerPolicy.select(listed(), chat.packageName), "chat")
        val confirming = HostPickerPolicy.next(queried)
        assertEquals(confirming, HostPickerPolicy.search(confirming, "mail"))
        val running = HostPickerPolicy.startRunning(confirming)
        assertEquals(running, HostPickerPolicy.search(running, "mail"))
        assertEquals("", HostPickerPolicy.open(running, "18427", true).searchQuery)
    }

    @Test
    fun elapsedTimingUsesMonotonicDifferenceAndOnlyMeasuredEstimate() {
        val first = HiddenOperationProgress(10_000L, 1)
        assertNull(first.estimatedSeconds)
        assertEquals(0L, first.elapsedSeconds(9_000L))
        assertEquals(2L, first.elapsedSeconds(12_900L))
        assertEquals(3L, first.copy(estimatedDurationMs = 2_001L).estimatedSeconds)
        assertNull(first.copy(estimatedDurationMs = 0L).estimatedSeconds)
    }

    @Test
    fun secondConfirmDoesNotResetRunningTimingAndFailureClearsIt() {
        val confirming = HostPickerPolicy.next(HostPickerPolicy.select(listed(), chat.packageName))
        val progress = HiddenOperationProgress(10_000L, 1, 2_000L)
        val running = HostPickerPolicy.startRunning(confirming, progress)
        assertEquals(progress, running.progress)
        assertEquals(running, HostPickerPolicy.startRunning(running, progress.copy(startedAtElapsedMs = 20_000L)))
        assertNull(HostPickerPolicy.fail(running, "FAILED", 2).progress)
        assertNull(HostPickerPolicy.dismiss(running).progress)
    }

    @Test
    fun deactivateRetryClearsOldFailureAndCannotRestartItsClock() {
        val opened = HostPickerPolicy.deactivateOpen(HostPickerPolicy.deactivateInitial(), chat.packageName)
        val failed = HostPickerPolicy.deactivateFinished(opened, false, 1, "FAILED", 2,
            listOf(HiddenStepResult("restore_host_so", false)))
        val progress = HiddenOperationProgress(10_000L, 3)
        val retry = HostPickerPolicy.deactivateRunning(failed, progress)
        assertTrue(retry.running)
        assertTrue(retry.steps.isEmpty())
        assertNull(retry.failureRes)
        assertEquals("", retry.failureCode)
        assertEquals(retry, HostPickerPolicy.deactivateRunning(retry, progress.copy(startedAtElapsedMs = 20_000L)))
        assertNull(HostPickerPolicy.deactivateFinished(retry, true, 1).progress)
    }
}
