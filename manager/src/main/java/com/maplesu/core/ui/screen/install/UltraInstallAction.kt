package com.maplesu.core.ui.screen.install

internal enum class UltraInstallAction {
    AUTOMATIC, FIRST_PATCH, REPATCH_SDK, RESTORE, SWITCH_CARRIER;

    val forceRepatch: Boolean get() = this == REPATCH_SDK || this == SWITCH_CARRIER
    val restoresOtherCarrier: Boolean get() = this == SWITCH_CARRIER
}

/** The user chooses a target; the installed carrier determines the required operation. */
internal fun resolvedUltraInstallAction(action: UltraInstallAction, selected: String?,
    installed: String?, direct: Boolean): UltraInstallAction =
    if (action != UltraInstallAction.AUTOMATIC) action
    else if (direct && installed in setOf("boot", "init_boot") && selected != installed)
        UltraInstallAction.SWITCH_CARRIER
    else UltraInstallAction.FIRST_PATCH

internal data class UltraInstallRoute(val partition: String?, val blocker: String? = null)

/** UI projection of observed carriers; the installer still validates the live transaction. */
internal fun ultraInstallRoute(
    action: UltraInstallAction,
    selected: String?,
    installed: String?,
    available: Collection<String>?,
    direct: Boolean,
    bridgeFailure: String?,
): UltraInstallRoute {
    val resolved = resolvedUltraInstallAction(action, selected, installed, direct)
    val carriers = setOf("boot", "init_boot")
    val current = installed?.takeIf { it in carriers }
    val partition = if (direct && resolved in setOf(UltraInstallAction.REPATCH_SDK, UltraInstallAction.RESTORE))
        current else selected?.takeIf { it in carriers }
    if (resolved == UltraInstallAction.RESTORE && !direct)
        return UltraInstallRoute(partition, "INSTALLER_RESTORE_REQUIRES_CURRENT_PARTITION")
    if (direct && resolved != UltraInstallAction.FIRST_PATCH && current == null)
        return UltraInstallRoute(null, "INSTALLER_CARRIER_UNCONFIRMED")
    if (partition == null || direct && available != null && partition !in available)
        return UltraInstallRoute(partition, "INSTALL_PARTITION_UNSUPPORTED")
    if (resolved == UltraInstallAction.SWITCH_CARRIER) {
        if (!direct) return UltraInstallRoute(partition, "INSTALLER_CARRIER_SWITCH_REQUIRES_CURRENT_PARTITION")
        if (partition == current) return UltraInstallRoute(partition, "INSTALLER_CARRIER_SWITCH_TARGET_UNCHANGED")
    }
    if (direct && resolved == UltraInstallAction.FIRST_PATCH && current != null && partition != current)
        return UltraInstallRoute(partition, "INSTALLER_CARRIER_SWITCH_REQUIRED")
    if (direct && resolved != UltraInstallAction.RESTORE && partition == "init_boot" && bridgeFailure != null)
        return UltraInstallRoute(partition, bridgeFailure)
    return UltraInstallRoute(partition)
}
