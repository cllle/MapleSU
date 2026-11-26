package dev.maplesu.manager.runtime.ultra

import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/** Only platform/SDK boundaries are fixtures. The complete checker is compiled unchanged. */
object SELinuxBoundary {
    @Volatile var key: String? = null
    @Volatile var boot: String? = "boot-a"
    @Volatile var owner: UltraRuntimeClient.ManagerOwnerIdentity? = null
    @Volatile var liveOwner: UltraRuntimeClient.ManagerOwnerIdentity? = null
    @Volatile var ultra = true
    @Volatile var hook = false
    @Volatile var legacyCalls = 0
    @Volatile var hookCalls = 0
    val statusCalls = AtomicInteger()
    val rootCalls = AtomicInteger()
    val ownerCalls = AtomicInteger()
    val submittedKeys = Collections.synchronizedList(mutableListOf<String?>())
    @Volatile var status: () -> NativeReceipt = { NativeReceipt(true, 1, true) }
    @Volatile var root: () -> NativeReceipt = { NativeReceipt(true, result = "Enforcing") }
}

class NativeReceipt(
    val succeeded: Boolean,
    private val enforcing: Int = -1,
    private val readable: Boolean = false,
    private val result: String? = null,
) {
    fun int(name: String): Int? = enforcing.takeIf { name == "selinux" }
    fun bool(name: String): Boolean? = readable.takeIf { name == "selinux_readable" }
    fun string(name: String): String? = result.takeIf { name == "result" }
}

object UltraEnvironmentProbe {
    data class Snapshot(val managerOwnerIdentity: UltraRuntimeClient.ManagerOwnerIdentity?)
    fun usesUltraBackend(context: Any) = SELinuxBoundary.ultra
    fun isRunning() = SELinuxBoundary.ultra
    fun currentSnapshot(context: Any) = Snapshot(SELinuxBoundary.owner)
}

object UltraEnvironmentAutoSetup { fun currentBootId() = SELinuxBoundary.boot }

class UltraRuntimeClient(private val key: () -> String? = { null }) {
    data class ManagerOwnerIdentity(val fullUid: Int, val epoch: Long, val hostInstance: String)
    data class ManagerOwnerIdentityResult(val identity: ManagerOwnerIdentity?)
    private var identityCurrent: () -> Boolean = { true }
    fun scoped(expectedKey: String?, isCurrent: () -> Boolean): UltraRuntimeClient =
        UltraRuntimeClient { expectedKey?.takeIf { isCurrent() && key() == it } }.also {
            it.identityCurrent = { identityCurrent() && isCurrent() && key() == expectedKey }
        }
    // The real client's systemStatus/localCall also does not enter identityCurrent.
    fun systemStatus(): NativeReceipt {
        SELinuxBoundary.statusCalls.incrementAndGet()
        return SELinuxBoundary.status()
    }
    fun queryManagerOwnerIdentity(): ManagerOwnerIdentityResult {
        SELinuxBoundary.ownerCalls.incrementAndGet()
        return ManagerOwnerIdentityResult(SELinuxBoundary.liveOwner)
    }
    fun runRootCommand(command: String): NativeReceipt {
        check(command == "/system/bin/getenforce")
        if (!identityCurrent()) return NativeReceipt(false)
        SELinuxBoundary.rootCalls.incrementAndGet()
        SELinuxBoundary.submittedKeys.add(key())
        return SELinuxBoundary.root()
    }
    fun decodeText(text: String?): String? = text
}

object UltraSdkCredentials {
    fun read(context: Any) = SELinuxBoundary.key
    fun client(context: Any, expectedKey: String?, expectedBootId: String?): UltraRuntimeClient =
        UltraRuntimeClient { expectedKey }.scoped(expectedKey) {
            SELinuxBoundary.key == expectedKey && SELinuxBoundary.boot == expectedBootId
        }
}
