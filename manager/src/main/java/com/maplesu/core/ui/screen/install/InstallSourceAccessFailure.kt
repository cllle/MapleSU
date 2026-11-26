package com.maplesu.core.ui.screen.install

internal enum class InstallSourceAccessFailure { ROOT_REQUIRED, CHANNEL_UNAVAILABLE, CHECK_FAILED }

/** Service transport failures do not prove that the device has lost root. */
internal fun installSourceAccessFailure(error: Throwable, rootWasRunning: Boolean = false): InstallSourceAccessFailure {
    dev.maplesu.manager.installer.rethrowInstallerCancellation(error)
    val causes = generateSequence(error) { it.cause }.take(8).toList()
    if (causes.any {
            it.message.orEmpty().startsWith("MODE_SWITCH_ROOT_") ||
                it.message.orEmpty().startsWith("INSTALLER_SERVICE_") ||
                it.message == "INSTALLER_ROOT_SOURCES_EXHAUSTED" ||
                it.javaClass.simpleName in setOf("TimeoutException", "DeadObjectException", "RemoteException")
        }) return InstallSourceAccessFailure.CHANNEL_UNAVAILABLE
    if (causes.any { it.message in setOf("INSTALLER_ROOT_REQUIRED", "INSTALLER_ROOT_UNAVAILABLE") }) {
        return if (rootWasRunning) InstallSourceAccessFailure.CHANNEL_UNAVAILABLE
            else InstallSourceAccessFailure.ROOT_REQUIRED
    }
    return InstallSourceAccessFailure.CHECK_FAILED
}
