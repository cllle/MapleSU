package dev.maplesu.manager.runtime.ultra

import android.content.Context
import dev.maplesu.manager.ultrakernel.UltraKernelCredential
import dev.maplesu.manager.workmode.*
import dev.maplesu.manager.workmode.ui.WorkModeSwitchSession
import java.io.File

/** Only external SDK/Android/storage boundaries live here; startup decisions are production text. */
internal object Fixture {
    val calls = mutableListOf<String>()
    val logs = mutableListOf<String>()
    val KEY_A = encoded('A')
    val KEY_B = encoded('B')
    val SESSION = encoded('C')
    private fun encoded(char: Char) = String(UltraKernelCredential.of(char.toString().repeat(48))
        .withMetadataDisplacement(-4096).copySecret(), Charsets.US_ASCII)
    var key: String? = KEY_A
    var boot: String? = "fixture-boot-a"
    var session: String? = null
    var pair = false
    var pairedKey: String? = null
    var ready = false
    var uid: Int? = null
    var owner: UltraRuntimeClient.ManagerOwnerIdentity? = null
    var ownerRoot = false
    var sdkState = "Running"
    var sdkSucceeded = true
    var sdkError: String? = null
    var kernelKey: String? = null
    var brokerKey: String? = null
    var backupKey: String? = null
    var installKeys = emptyList<String>()
    var imageKeys = emptyList<String>()
    var restoreResult: String? = null
    var setupInstalled = false
    var suspended = false
    var quiescing = false
    var hook = false
    var pendingInstall = false
    var moduleSucceeded = true
    var moduleArray = true
    var versionSucceeded = true
    var version: String? = "fixture-v1"
    var authorizationOk = true
    var rootMarkerCurrent = true
    var sessionMarkerCurrent = true
    var onEnvironment: () -> Unit = {}
    var onAuthorization: () -> Unit = {}
    var gate: String? = null
    var rootKeys = mutableListOf<String?>()
    fun reset() {
        android.os.SystemClock.now = 0L
        key = KEY_A; boot = "fixture-boot-a"; session = null
        pair = false; pairedKey = null; ready = false; uid = null; owner = null; ownerRoot = false
        sdkState = "Running"; sdkSucceeded = true; sdkError = null
        kernelKey = null; brokerKey = null; backupKey = null; installKeys = emptyList(); imageKeys = emptyList()
        restoreResult = null; setupInstalled = false; suspended = false; quiescing = false; hook = false
        pendingInstall = false; moduleSucceeded = true; moduleArray = true; versionSucceeded = true
        version = "fixture-v1"; authorizationOk = true; rootMarkerCurrent = true; sessionMarkerCurrent = true
        onEnvironment = {}; onAuthorization = {}; gate = null
        ModeRuntime.active = WorkMode.LITE; ModeRuntime.activation = ModeActivation()
        ModeRuntime.detectionRevision.value++
        ModeRuntime.detectionComplete = true; ModeRuntime.detectionFailure = null
        ModeRuntime.latestDetection = Detection(); WorkModeSwitchSession.executing = false
        // Test setup seeds the real private cache; no production decision is duplicated.
        for (name in listOf("snapshotKey", "snapshotBootId", "verifiedInstalledIdentity", "verifiedOwnerIdentity")) {
            UltraEnvironmentProbe::class.java.getDeclaredField(name).apply { isAccessible = true }.set(null, null)
        }
        UltraEnvironmentProbe.publishSnapshot(UltraEnvironmentProbe.Snapshot())
        calls.clear(); logs.clear(); rootKeys.clear()
        Thread.interrupted()
    }
    fun observe(observation: UltraEnvironmentProbe.Snapshot) {
        UltraEnvironmentProbe::class.java.getDeclaredField("snapshotKey").apply { isAccessible = true }
            .set(null, session ?: key)
        UltraEnvironmentProbe::class.java.getDeclaredField("snapshotBootId").apply { isAccessible = true }
            .set(null, boot)
        UltraEnvironmentProbe.publishSnapshot(observation)
    }
    fun terminal(): String = logs.last { it.startsWith("Ultra startup terminal") }
    fun count(call: String) = calls.count { it == call }
}

class Receipt(val succeeded: Boolean = true, val sdkError: String? = null,
              private val strings: Map<String, String?> = emptyMap(), private val hasModules: Boolean = false) {
    val sdkErrorCode: Int? = null
    val reason: String? = null
    fun string(name: String): String? = strings[name]
    fun array(name: String): List<String>? = if (name == "modules" && hasModules) emptyList() else null
    fun obj(name: String): Obj? = null
    class Obj { fun opt(name: String): Any? = null }
}

class UltraRuntimeClient(private val key: () -> String? = { null }) {
    data class ManagerOwnerIdentity(val fullUid: Int, val epoch: Long, val hostInstance: String)
    data class OwnerReply(val identity: ManagerOwnerIdentity?, val receipt: Receipt = Receipt())
    data class OwnerProof(val owner: ManagerOwnerIdentity)
    data class OwnerRootReply(val proof: OwnerProof?, val receipt: Receipt = Receipt())
    data class Authorization(val ok: Boolean)
    fun environmentState(): Receipt {
        Fixture.calls += "environment"
        val result = Receipt(Fixture.sdkSucceeded, Fixture.sdkError, mapOf("state" to Fixture.sdkState))
        Fixture.onEnvironment()
        return result
    }
    fun runRootCommand(command: String): Receipt {
        check(command == "/system/bin/id -u")
        Fixture.calls += "root"; Fixture.rootKeys += key()
        return Receipt(Fixture.uid != null, if (Fixture.uid == null) Fixture.sdkError else null,
            strings = mapOf("result" to Fixture.uid?.toString()))
    }
    fun decodeText(value: String?) = value
    fun claimManagerIdentity(): String? { Fixture.calls += "kernel"; return Fixture.kernelKey }
    fun imageCredentials(kernel: File) = Fixture.imageKeys
    fun queryManagerOwnerIdentity(): OwnerReply {
        Fixture.calls += "owner"; return OwnerReply(Fixture.owner)
    }
    fun probeManagerOwnerRoot(): OwnerRootReply {
        Fixture.calls += "owner-root"
        return OwnerRootReply(Fixture.owner?.takeIf { Fixture.ownerRoot }?.let(::OwnerProof))
    }
    fun moduleList(): Receipt {
        Fixture.calls += "modules"
        return Receipt(Fixture.moduleSucceeded, hasModules = Fixture.moduleArray)
    }
    fun installedEnvironmentVersion(): Receipt {
        Fixture.calls += "version"
        return Receipt(Fixture.versionSucceeded, strings = mapOf("version" to Fixture.version))
    }
    fun suUidProfiles(): Authorization {
        Fixture.calls += "authorization"; Fixture.onAuthorization()
        return Authorization(Fixture.authorizationOk)
    }
}

object UltraSdkCredentials {
    fun read(context: Context) = Fixture.session ?: Fixture.key
    fun suspended(context: Context) = Fixture.suspended
    fun hookWithoutSdk(context: Context) = Fixture.hook
    fun client(context: Context, key: String? = read(context), bootId: String? = Fixture.boot) = UltraRuntimeClient { key }
}
object UltraEnvironmentAutoSetup {
    data class Result(val installed: Boolean, val reason: String = "fixture")
    fun currentBootId() = Fixture.boot
    fun hasPostBootUltraSwitchRecovery(context: Context) = false
    fun hasVerifiedUltraIdentity(context: Context) = false
    fun blockedReason(context: Context, bootSession: Boolean): String? = if (Fixture.suspended) "SUSPENDED" else null
    fun ensure(context: Context): Result { Fixture.calls += "setup"; return Result(Fixture.setupInstalled) }
}
object UltraInstallCredentialRecovery {
    fun hasPendingInstallCredential(context: Context) = Fixture.pendingInstall
}
object UltraInstallCredentials {
    fun recoveryCandidates(context: Context): List<String> { Fixture.calls += "install"; return Fixture.installKeys }
}
object UltraSdkSession {
    data class Result(val ready: Boolean)
    fun current(context: Context) = Fixture.session
    fun restore(context: Context): String? { Fixture.calls += "restore"; return Fixture.restoreResult }
    fun ensure(context: Context): Result { Fixture.calls += "boot-ready"; return Result(Fixture.ready) }
    fun captureOwnerMarker(context: Context): () -> Boolean = { Fixture.sessionMarkerCurrent }
}
object UltraAutoPair {
    data class Result(val paired: Boolean, val state: String = "fixture", val reason: String = "fixture")
    fun ensure(context: Context, maintenance: Boolean): Result {
        check(!maintenance); Fixture.calls += "pair"; return Result(Fixture.pair)
    }
}
object UltraSdkLogging {
    fun redact(value: String) = value
    fun ensureDisabled(context: Context) { Fixture.calls += "persistent-log" }
    fun apply(client: UltraRuntimeClient) { Fixture.calls += "persistent-log" }
}
object UltraKeyPairedStore {
    fun isPaired(context: Context) = Fixture.pairedKey != null
    fun matchesVerifiedKey(context: Context, key: String) = Fixture.pairedKey == key
    fun markPaired(context: Context, key: String) { Fixture.calls += "mark-paired"; Fixture.pairedKey = key }
    fun storeVerifiedKey(context: Context, key: String) {
        Fixture.calls += "store"; Fixture.key = key; Fixture.pairedKey = key
    }
}
object UltraPairingMaintenance {
    fun request(context: Context, key: String) { Fixture.calls += "maintenance" }
}
class UltraSuChannel(timeoutMillis: Long) {
    fun rootUid(): Int? { Fixture.calls += "backup-su"; return null }
    fun runRoot(script: String, timeout: Long) = ""
}
fun interface RootScriptRunner { fun run(script: String): String }
object UltraRecoveryKeyStore {
    data class Result(val key: String?)
    fun restore(context: Context, runner: RootScriptRunner): Result {
        Fixture.calls += "backup-read"; return Result(Fixture.backupKey)
    }
}
object UltraImageIdentityRecovery {
    fun candidates(directory: File, read: (File) -> List<String>): Sequence<String> {
        Fixture.calls += "image"; return read(directory).asSequence()
    }
}
