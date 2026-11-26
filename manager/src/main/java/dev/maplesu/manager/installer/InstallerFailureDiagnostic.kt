package dev.maplesu.manager.installer

import dev.maplesu.manager.runtime.RootServiceBootstrap

internal fun rethrowInstallerCancellation(error: Throwable) {
    val cancelled = generateSequence(error) { it.cause }.take(8).firstOrNull {
        it is java.util.concurrent.CancellationException || it is InterruptedException
    } ?: return
    if (cancelled is InterruptedException) Thread.currentThread().interrupt()
    throw cancelled
}

internal fun installerServiceFailure(error: RootServiceBootstrap.Failure): RuntimeException =
    if (error.message == "INSTALLER_ROOT_REQUIRED") InstallerServiceClient.AuthorizationFailure(error) else error

internal fun installerFailureExitCode(error: Throwable, fallback: Int = 1): Int =
    generateSequence(error) { it.cause }.take(8).filterIsInstance<RootServiceBootstrap.Failure>()
        .firstOrNull()?.result?.code?.takeUnless { it == 0 } ?: fallback

/** Surface a stable diagnostic identifier, never arbitrary exception messages or paths. */
internal fun installerFailureDiagnostic(error: Throwable?, recorded: String = ""): String {
    val messages = generateSequence(error) { it.cause }.take(8).mapNotNull { it.message }.toList() + recorded
    messages.firstOrNull { it.startsWith("INSTALLER_SERVICE_") &&
        it.matches(Regex("INSTALLER_SERVICE_[A-Z0-9_]{1,100}")) }?.let { return it }
    return messages.asSequence().map { it.substringBefore(':') }
        .firstOrNull {
            it == "PRODUCE_KSud_FAILED" ||
                it.matches(Regex("(?:INSTALLER|VR_BOOTSTRAP|ULTRA_KERNEL|ULTRA_INIT|BASELINE|SWITCH|MODE_SWITCH|KSU|PRODUCE|ULTRA_ENV|HIDDEN|SUPERUSER|MOVE)_[A-Z0-9_]{1,100}"))
        }
        ?: "INSTALLER_OPERATION_FAILED"
}
