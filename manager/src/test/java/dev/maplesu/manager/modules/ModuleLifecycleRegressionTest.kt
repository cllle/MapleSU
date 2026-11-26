package dev.maplesu.manager.modules

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Android lifecycle boundaries which must never wait for a root task or catalog lock. */
class ModuleLifecycleRegressionTest {
    private fun source(name: String) = File("src/main/java/$name").readText()

    @Test fun foregroundDeadlineDoesNotDependOnModuleInitializationOrMode() {
        val service = source("dev/maplesu/manager/modules/LiteModuleBootService.kt")
        val create = service.substringAfter("override fun onCreate()").substringBefore("private fun promoteToForeground")
        assertTrue("Promote the service immediately in onCreate", create.contains("promoteToForeground()"))
        assertFalse("Catalog I/O belongs on the worker", create.contains("LiteScriptModuleBackend(this)"))
    }

    @Test fun destroyingWorkerDoesNotWaitForModuleLockOnMainThread() {
        val service = source("dev/maplesu/manager/modules/LiteModuleBootService.kt")
        val destroy = service.substringAfter("override fun onDestroy()").substringBefore("companion object")
        assertTrue("Cancel root tasks off the lifecycle thread", destroy.contains("Thread("))
        assertTrue(destroy.indexOf("Thread(") < destroy.indexOf("cancelBootTasks()"))
    }

    @Test fun operationRefreshCannotReenterBundledInstallation() {
        val vm = source("com/maplesu/core/ui/viewmodel/ModuleViewModel.kt")
        val mutation = vm.substringAfter("private fun runModuleOperation(").substringBefore("private fun readbackAfterModuleOperations()")
        assertFalse("Successful writes never reload the whole page", mutation.contains("fetchModuleList("))
        val refresh = vm.substringAfter("private fun readbackAfterModuleOperations()").substringBefore("private suspend fun buildUpdateConfirmDialogState")
        assertTrue("Only uncertain writes need a read-only silent readback", refresh.contains("moduleOperationReadbackNeeded"))
        assertTrue(refresh.contains("fetchModuleList(checkUpdate = false, resort = false, showRefreshing = false)"))
        val load = vm.substringAfter("private suspend fun loadModuleList(").substringBefore("fun fetchModuleList(")
        assertTrue(Regex("""repo\.getModules\s*(?:\(|\{)""").containsMatchIn(load))
        assertFalse("List loading never preinstalls modules", vm.contains("ensureBundled"))
    }
}
