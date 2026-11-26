package dev.maplesu.manager.installer

import dev.maplesu.image.UltraInitBootProducer
import dev.maplesu.manager.workmode.Hashes

/** Reusable components reach the kernel loader; explicit legacy artifacts retain exact input binding. */
internal object UltraInitBootInstallTarget {
    @Suppress("UNUSED_PARAMETER")
    fun requireCompatible(
        artifact: UltraInitBootProducer.Artifact,
        kernelRelease: String,
        bootSha256: String? = null,
        bootSize: Long? = null,
    ) {
        if (artifact.isKmiComponent) return
        val boot = requireNotNull(artifact.targetBoot) { "ULTRA_KERNEL_INIT_BOOT_TARGET_CHANGED" }
        check(bootSha256 != null && Hashes.sha256(boot) == bootSha256 &&
            (bootSize == null || boot.length() == bootSize)) {
            "ULTRA_KERNEL_INIT_BOOT_TARGET_CHANGED"
        }
    }
}
