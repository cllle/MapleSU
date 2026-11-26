package com.maplesu.core.ui.screen.module

import com.maplesu.core.data.model.Module
import org.junit.Assert.*
import org.junit.Test

class ModuleBackgroundRefreshStateTest {
    private val first = Module("first", "First", "MapleSU", "1", 1, "", true, false,
        false, "", true, true, false, null, null)
    private val second = first.copy(id = "second", name = "Second")
    private fun state() = listOf(first, second).let {
        ModuleUiState(hasLoaded = true, modules = it, moduleList = it, searchResults = it)
    }

    @Test fun equalBackendRowsKeepSourceAndVisibleSnapshots() {
        val old = state()
        val next = old.withModuleLoadResult(Result.success(old.modules.map { it.copy() }))
        assertSame(old.modules, next.modules)
        assertSame(old.moduleList, next.moduleList)
        assertSame(old.searchResults, next.searchResults)
        assertEquals(old, next)
    }

    @Test fun oneChangedRowRetainsEveryOtherRowIdentity() {
        val old = state()
        val next = old.withModuleLoadResult(Result.success(listOf(first.copy(), second.copy(version = "2"))))
        assertSame(first, next.modules[0])
        assertEquals("2", next.modules[1].version)
        assertNotSame(second, next.modules[1])
        assertSame(old.moduleList, next.moduleList)
    }

    @Test fun immediateRemovalOnlyDropsTheAbsentBackendRow() {
        val next = state().withModuleLoadResult(Result.success(listOf(second.copy())))
        assertEquals(listOf(second), next.modules)
        assertSame(second, next.modules.single())
    }

    @Test fun pendingRebootRemovalStaysVisibleWithItsRealStatus() {
        val removed = second.copy(remove = true, runtimeStatus = "remove_pending", canToggle = false)
        val next = state().withModuleLoadResult(Result.success(listOf(first, removed)))
        assertEquals(2, next.modules.size)
        assertTrue(next.modules.last().remove)
        assertEquals("remove_pending", next.modules.last().runtimeStatus)
        assertFalse(next.modules.last().canToggle)
    }

    @Test fun aRefreshFailureKeepsRowsAndTheOriginalCause() {
        val old = state()
        val error = IllegalStateException("SDK_BUSY: original cause")
        val next = old.withModuleLoadResult(Result.failure(error))
        assertSame(old.modules, next.modules)
        assertSame(old.moduleList, next.moduleList)
        assertSame(old.searchResults, next.searchResults)
        assertSame(error, next.loadError)
    }

    @Test fun actualEmptySuccessRemainsDifferentFromFailure() {
        val next = state().withModuleLoadResult(Result.success(emptyList()))
        assertTrue(next.modules.isEmpty())
        assertNull(next.loadError)
        assertTrue(next.hasLoaded)
    }

    @Test fun aNewReadIdentityDropsOldRowsAndOptimisticFactsBeforeReading() {
        val old = state().withModuleRemovalRequest(first.id, true)
            .withModuleToggleRequest(second.id, false)
        val next = old.withModuleReadContextReset()
        assertTrue(next.modules.isEmpty())
        assertTrue(next.moduleList.isEmpty())
        assertTrue(next.searchResults.isEmpty())
        assertTrue(next.updateInfo.isEmpty())
        assertTrue(next.removalChanges.isEmpty())
        assertTrue(next.toggleChanges.isEmpty())
        assertFalse(next.hasLoaded)
        assertTrue(next.isRefreshing)
        assertNull(next.loadError)
    }

    @Test fun readContextResetDoesNotClearEnvironmentSafety() {
        val next = state().copy(isSafeMode = true, isUltra = true,
            environmentWebUiAvailable = false).withModuleReadContextReset()
        assertTrue(next.recoveryOnly)
        assertFalse(next.environmentWebUiAvailable)
    }
}
