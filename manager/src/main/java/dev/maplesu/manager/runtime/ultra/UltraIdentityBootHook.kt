package dev.maplesu.manager.runtime.ultra

/** Called only after the deployed broker's key, build and deployment handshake matches. */
internal object UltraIdentityBootHook {
    fun ensure(environmentVerified: Boolean, preparingUpgrade: Boolean, verificationFailure: String? = null,
               retireLegacy: () -> String?): String {
        if (!environmentVerified && verificationFailure != null && verificationFailure != "HELPER_SHA_MISMATCH")
            return "IDENTITY_ENVIRONMENT_UNVERIFIED:$verificationFailure"
        if (!environmentVerified) return if (preparingUpgrade) "IDENTITY_UPGRADE_PREPARED"
            else "IDENTITY_ENVIRONMENT_UPGRADE_REQUIRED"
        return retireLegacy() ?: "IDENTITY_READY"
    }
}
