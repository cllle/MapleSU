package dev.maplesu.manager.installer

/** The authenticated installer must execute as root, independently of its transport's ancestors. */
internal object InstallerModeGate {
    const val ROOT_REQUIRED = "INSTALLER_ROOT_REQUIRED"

    enum class WriteScope { LITE_INSTALL, MODE_SWITCH }

    /** Manager identity is verified by the service before this root-process check. */
    @Suppress("UNUSED_PARAMETER")
    fun refusal(serviceUid: Int, scope: WriteScope): String? =
        if (serviceUid == 0) null else ROOT_REQUIRED

    fun writeRefusal(scope: WriteScope): String? = refusal(android.os.Process.myUid(), scope)

    fun refusal(): String? = writeRefusal(WriteScope.LITE_INSTALL)
}
