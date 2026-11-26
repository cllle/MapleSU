package dev.maplesu.manager.installer

/**
 * Kernel proof read inside the installer service (02-switch-contract §5.3).
 *
 * <p>Only the service process may decide whether the KSU native channel is usable; the App's
 * `isManager` flag is never accepted as authority. W11 injects the JNI supercall implementation;
 * until then the proof is unavailable (version=0), so the KSU native channel cannot be *proven*
 * as a root transport. Partition authority uses the actual service UID and verified Manager;
 * [RootSourcePolicy] selects the KSU source, and [InstallerRootSession]
 * degrades to another transport instead of refusing — an unavailable proof never blocks a
 * user-initiated flash.
 */
internal data class KsuKernelProof(val version: Int, val uapi: Int) {
    /** Matches the UAPI=7 contract (`DOC:684`); version 0 means "not wired". */
    val usable: Boolean get() = version > 0 && uapi == 7
}

internal object KernelProof {
    /**
     * Host-safe stub: no JNI is bound in this build, so no proof can be produced yet. The verdict is
     * collected for evidence/logging only (see [InstallerRootSession.kernelProof]).
     */
    fun inService(): KsuKernelProof = KsuKernelProof(0, 0)
}
