package dev.maplesu.manager.runtime.ultra

import dev.maplesu.manager.compat.ultraPreferences

import android.content.Context
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Process
import android.os.Bundle
import dev.maplesu.manager.installer.InstallerServiceClient
import android.os.SystemClock
import android.util.Log
import dev.maplesu.manager.modules.UltraRootKeyStore
import dev.maplesu.manager.runtime.authorization.ContextPackageSnapshotSource
import dev.maplesu.manager.runtime.authorization.PackageIdentityReader
import dev.maplesu.manager.workmode.superuser.LiteImplantQuiesceGate
import dev.maplesu.manager.workmode.ModeRuntime
import dev.maplesu.manager.workmode.WorkMode
import dev.maplesu.manager.workmode.ui.WorkModeSwitchSession
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CancellationException
import org.json.JSONArray
import org.json.JSONObject

/** Prefer a working app key; environment-owned recovery survives app data removal. */
internal object UltraIdentityRecovery {
    private const val TAG = "MapleSUUltraIdentity"
    private const val READY = "MAPLESU_IDENTITY_READY"
    private val deploymentLock = Any()

    internal fun blocked(context: Context, preparingUpgrade: Boolean = false) = LiteImplantQuiesceGate.isBlocked() ||
        (!preparingUpgrade && UltraEnvironmentProbe.currentSnapshot(context).state == UltraEnvironmentProbe.State.Fault) ||
        UltraRootKeyStore.isSuspended(context) || WorkModeSwitchSession.executing ||
        ModeRuntime.active == WorkMode.KSU ||
        // A temporary owner must never verify, pair or back up a historical permanent key.
        UltraSdkSession.current(context) != null ||
        dev.maplesu.manager.runtime.HookRootExecutor.production(context).available() ||
        ModeRuntime.latestDetection?.trace?.firstOrNull { it.id == "S1" }?.ok == true

    private data class Candidate(val key: String, val buildId: String?, val deploymentId: String?, val pid: Int)

    private fun endpoint(userId: Int, withBuild: Boolean, delegatedUid: Int? = null): UltraIdentityBrokerDeployment.Endpoint<Candidate> {
        fun read(version: Int) = recoveryCandidate {
            if (Thread.currentThread().isInterrupted) return@recoveryCandidate null
            LocalSocket().use { socket ->
                socket.connect(LocalSocketAddress(UltraIdentityBroker.socketName(userId), LocalSocketAddress.Namespace.ABSTRACT))
                socket.soTimeout = 1500
                val peer = socket.peerCredentials
                UltraIdentityBrokerDeployment.readEndpoint(peer.uid, peer.pid) {
                    DataOutputStream(socket.outputStream).apply {
                        if (delegatedUid == null) writeInt(version) else {
                            writeInt(UltraIdentityHandshake.DELEGATED_VERSION)
                            writeInt(delegatedUid)
                            writeInt(version)
                        }
                        flush()
                    }
                    val input = DataInputStream(socket.inputStream)
                    if (input.readInt() != version) return@readEndpoint null
                    val bytes = ByteArray(UltraRecoveryKey.KEY_LENGTH)
                    try {
                        input.readFully(bytes)
                        val build = if (version >= UltraIdentityHandshake.BUILD_VERSION) input.readUTF() else null
                        if (build != null && !build.matches(Regex("[A-Za-z0-9._-]{1,128}"))) return@readEndpoint null
                        val deployment = if (version == UltraIdentityHandshake.DEPLOYMENT_VERSION) input.readUTF() else null
                        if (deployment != null && !deployment.matches(Regex("[0-9a-f]{64}"))) return@readEndpoint null
                        Candidate(bytes.toString(Charsets.US_ASCII), build, deployment, peer.pid)
                    } finally { bytes.fill(0) }
                }
            }
        }
        val versions = if (withBuild) listOf(UltraIdentityHandshake.DEPLOYMENT_VERSION,
            UltraIdentityHandshake.BUILD_VERSION, UltraIdentityBroker.VERSION) else listOf(UltraIdentityBroker.VERSION)
        var ownerPid: Int? = null
        for (version in versions) {
            val observed = read(version) ?: continue
            if (observed.ownerPid != null) ownerPid = observed.ownerPid
            if (observed.identity != null) return observed
        }
        return UltraIdentityBrokerDeployment.Endpoint(ownerPid, null)
    }

    /** The root service connects in its own domain; caller UID/signature are checked by Binder. */
    internal fun readForRoot(managerUid: Int, withBuild: Boolean): Bundle {
        check(Process.myUid() == 0 && managerUid % 100_000 in 10_000..19_999)
        val observed = endpoint(managerUid / 100_000, withBuild, delegatedUid = managerUid)
        return Bundle().apply {
            observed.ownerPid?.let { putInt("rootSocketOwnerPid", it) }
            observed.identity?.let {
                putString("key", it.key)
                putString("buildId", it.buildId)
                putString("deploymentId", it.deploymentId)
                putInt("brokerPid", it.pid)
            }
        }
    }

    private fun endpoint(context: Context, userId: Int, withBuild: Boolean): UltraIdentityBrokerDeployment.Endpoint<Candidate> {
        val direct = endpoint(userId, withBuild)
        if (direct.identity != null) return direct
        val delegated = recoveryCandidate {
            if (Thread.currentThread().isInterrupted) return@recoveryCandidate null
            check(userId == Process.myUid() / 100_000)
            InstallerServiceClient.withService(context, persistent = true) { service ->
                val observed = service.readUltraIdentity(withBuild)
                check(observed.getInt("serviceUid", -1) == 0 &&
                    observed.getInt("servicePid", -1) > 1 && observed.getInt("servicePid") != Process.myPid() &&
                    observed.getInt("callerUid", -1) == Process.myUid())
                val ownerPid = observed.getInt("rootSocketOwnerPid", -1).takeIf { it > 1 }
                val key = observed.getString("key")
                val pid = observed.getInt("brokerPid", -1)
                val build = observed.getString("buildId")
                val deployment = observed.getString("deploymentId")
                val identity = if (Thread.currentThread().isInterrupted || key == null || !UltraRecoveryKey.isValidKeyFormat(key) || pid <= 1 ||
                    (build != null && !build.matches(Regex("[A-Za-z0-9._-]{1,128}"))) ||
                    (deployment != null && !deployment.matches(Regex("[0-9a-f]{64}")))) null
                    else Candidate(key, build, deployment, pid)
                UltraIdentityBrokerDeployment.Endpoint(ownerPid, identity)
            }
        }
        return delegated?.takeIf { it.ownerPid != null || it.identity != null } ?: direct
    }

    private fun candidate(context: Context, userId: Int, withBuild: Boolean): Candidate? =
        endpoint(context, userId, withBuild).identity

    @Synchronized
    fun recover(context: Context): String {
        val startedAt = SystemClock.elapsedRealtime()
        val app = context.applicationContext
        if (UltraEnvironmentProbe.currentSnapshot(app).state == UltraEnvironmentProbe.State.Fault) return "RECOVERY_SUSPENDED"
        if (UltraSdkSession.current(app) != null) {
            return if (UltraEnvironmentAutoSetup.blockedReason(app, bootSession = true) == null)
                "BOOT_SDK_CURRENT" else "RECOVERY_SUSPENDED"
        }
        if (blocked(app)) return "RECOVERY_SUSPENDED"
        if (UltraEnvironmentProbe.hasCurrentRootProof(app)) {
            return if (UltraSdkCredentials.read(app) == null) "RECOVERY_OWNER_VERIFIED"
                else "RECOVERY_LOCAL_KEY_VERIFIED"
        }
        val recoveryBootId = UltraEnvironmentAutoSetup.currentBootId()
        val result = UltraIdentityResolution.recover(object : UltraIdentityResolution.Port {
            override fun currentKey() = UltraRootKeyStore.read(app)
            override fun blocked() = blocked(app)
            override fun bootId() = UltraEnvironmentAutoSetup.currentBootId()
            override fun rootUid(key: String): Int? {
                val client = UltraRuntimeClient { key }
                return uid(client)
            }
            override fun kernelKey(): String? =
                if (blocked()) null else UltraRuntimeClient().claimManagerIdentity()
            override fun restoreBootSession(): String? = UltraSdkSession.restore(app).also {
                it?.let { result -> Log.i(TAG, "boot SDK recovery=$result") }
            }
            override fun brokerKey() = candidate(app, Process.myUid() / 100_000, withBuild = false)?.key
            override fun backupKey(): String? {
                if (blocked()) return null
                // No mode/paired flag prerequisite: a clean reinstall has neither.
                // The backup also requires the root-owned environment signer pin;
                // an inherited package-name SU grant is not an identity proof.
                val su = UltraSuChannel(timeoutMillis = 700L)
                if (su.rootUid() != 0 || blocked()) return null
                return UltraRecoveryKeyStore.restore(app, RootScriptRunner { script ->
                    su.runRoot(script, timeout = 1_000L)
                }).key
            }
            override fun installKeys() = UltraInstallCredentials.recoveryCandidates(app)
            override fun imageKeys() = UltraImageIdentityRecovery.candidates(app.filesDir) { kernel ->
                if (blocked()) emptyList() else UltraRuntimeClient().imageCredentials(kernel)
            }
            override fun attempted(source: String, index: Int, root: Boolean) {
                Log.i(TAG, "identity source=$source candidate=$index root=$root")
            }
            override fun store(expected: String?, key: String): Boolean = synchronized(UltraRootKeyStore) {
                if (blocked() || UltraRootKeyStore.read(app) != expected) return@synchronized false
                try {
                    if (expected == key) {
                        // Do not erase a staged next-boot credential when validating
                        // the still-running kernel's current key.
                        if (!UltraKeyPairedStore.matchesVerifiedKey(app, key)) UltraKeyPairedStore.markPaired(app, key)
                    } else {
                        UltraKeyPairedStore.storeVerifiedKey(app, key)
                    }
                    true
                } catch (_: Exception) {
                    false
                }
            }
            override fun accepted(key: String) {
                UltraEnvironmentProbe.refreshRecoveredIdentity(app, key, recoveryBootId)
            }
        })
        runCatching {
            app.ultraPreferences("ultra-identity-recovery", Context.MODE_PRIVATE).edit()
                .putString("result", result)
                .putString("boot", UltraEnvironmentAutoSetup.currentBootId())
                .putLong("at", System.currentTimeMillis()).apply()
        }
        Log.i(TAG, "environment identity recovery=$result elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
        return result
    }

    internal fun uid(client: UltraRuntimeClient): Int? = client.runRootCommand("/system/bin/id -u").let {
        if (it.succeeded) client.decodeText(it.string("result"))?.trim()?.toIntOrNull() else null
    }

    /** Called with a working kernel key. No dependency on the old manager's su/UID grant. */
    fun ensure(context: Context, key: String, client: UltraRuntimeClient, force: Boolean = false,
               preparingUpgrade: Boolean = false): String = synchronized(deploymentLock) { runCatching {
        if (Thread.currentThread().isInterrupted || blocked(context, preparingUpgrade)) return "IDENTITY_SUSPENDED"
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(
            (key + UltraSdkBuild.BUILD_ID + startScript() + sha256(File(context.applicationInfo.sourceDir)) + context.applicationInfo.sourceDir + ":" + Process.myUid())
                .toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
        var preparedGeneration: String? = null
        UltraIdentityBrokerDeployment.ensure(force, object : UltraIdentityBrokerDeployment.Port {
            override fun observe(): UltraIdentityBrokerDeployment.Observation {
                val observed = endpoint(context, Process.myUid() / 100_000, withBuild = true)
                val running = observed.identity
                return UltraIdentityBrokerDeployment.Observation(observed.ownerPid,
                    running?.key == key && running.buildId == UltraSdkBuild.BUILD_ID &&
                        running.deploymentId == fingerprint)
            }
            override fun prepare(): String? {
                val generation = UUID.randomUUID().toString().replace("-", "")
                val result = prepareDeployment(context, key, client, fingerprint, generation)
                if (result == null) preparedGeneration = generation
                return result
            }
            override fun eligible() = !Thread.currentThread().isInterrupted &&
                !blocked(context, preparingUpgrade) && UltraRootKeyStore.read(context) == key
            override fun stop(pid: Int): Boolean {
                val stopped = client.runRootCommand(restartScript(pid))
                return stopped.succeeded && client.decodeText(stopped.string("result"))?.trim() == "MAPLESU_IDENTITY_STOPPED"
            }
            override fun start() = client.runRootCommand(
                "/system/bin/sh '${UltraIdentityBroker.DIRECTORY}/start.sh' </dev/null >/dev/null 2>&1 &").succeeded
            override fun complete() = completeDeployment(context, key, client, preparingUpgrade, preparedGeneration)
        })
    }.getOrElse {
        when (it) {
            is CancellationException -> throw it
            is InterruptedException -> {
                Thread.currentThread().interrupt()
                "IDENTITY_SUSPENDED"
            }
            else -> "IDENTITY_FAILED:${it.javaClass.simpleName}"
        }
    } }

    private fun prepareDeployment(context: Context, key: String, client: UltraRuntimeClient, fingerprint: String, generation: String): String? {
        if (uid(client) != 0) return "IDENTITY_ROOT_NOT_VERIFIED"
        val identity = PackageIdentityReader(ContextPackageSnapshotSource(context)).read(Process.myUid())
            ?: return "IDENTITY_PACKAGE_UNVERIFIED"
        UltraReinstallTrust(identity.packageName, identity.uid / 100_000, identity.signers)
        val androidId = UltraRecoveryKeyStore.androidId(context)
        val payload = UltraRecoveryKey.seal(key, androidId, identity.packageName)
            ?: return "IDENTITY_SEAL_FAILED"
        val config = JSONObject().put("schema", UltraIdentityBroker.VERSION)
            .put("sdkBuildId", UltraSdkBuild.BUILD_ID)
            .put("deploymentId", fingerprint)
            .put("userId", identity.uid / 100_000).put("packageName", identity.packageName)
            .put("signers", JSONArray(identity.signers.sorted())).put("payload", payload)
            .put("androidId", androidId.orEmpty())
        val directory = File(context.cacheDir, "ultra-identity-${UUID.randomUUID()}").apply { check(mkdir()) }
        try {
            val material = File(directory, "identity.json").apply { writeText(config.toString()) }
            val start = File(directory, "start.sh").apply { writeText(startScript()) }
            val apk = File(context.applicationInfo.sourceDir)
            val bridge = File(context.applicationInfo.nativeLibraryDir, "libmaplesu_ultra_bridge.so")
            if (!bridge.isFile) return "IDENTITY_BRIDGE_ASSET_ABSENT"
            val deployStarted = SystemClock.elapsedRealtime()
            val deploy = client.runRootCommand(deployScript(apk, sha256(apk), material, start, bridge, sha256(bridge), generation))
            val output = client.decodeText(deploy.string("result"))?.trim()
            runCatching { Log.i(TAG, "identity deploy elapsedMs=${SystemClock.elapsedRealtime() - deployStarted} " +
                "bridgeVersion=${deploy.bridgeVersion} sdkError=${deploy.sdkError} " +
                "exitCode=${deploy.int("exitCode")} timedOut=${deploy.bool("timedOut")} " +
                "outputChars=${output?.length ?: 0} readyExact=${output == READY}") }
            if (!deploy.succeeded || output != READY) {
                val detail = Regex("(?m)^MAPLESU_IDENTITY_DEPLOY_ERROR:([A-Z_]+):([0-9]{1,3})$")
                    .find(output.orEmpty())?.groupValues?.drop(1)?.joinToString(":")
                return ("IDENTITY_DEPLOY_FAILED" + (detail?.let { ":$it" } ?: ""))
                    .also { Log.w(TAG, it) }
            }
            return null
        } finally { directory.deleteRecursively() }
    }

    private fun completeDeployment(context: Context, key: String, client: UltraRuntimeClient, preparingUpgrade: Boolean, generation: String?): String {
        val environment = UltraInstalledIdentityProbe.readAndVerify(context, client,
            readbackTimeoutMillis = UltraInstalledIdentityProbe.MAINTENANCE_READBACK_TIMEOUT_MS)
        if (Thread.currentThread().isInterrupted || blocked(context, preparingUpgrade) || UltraRootKeyStore.read(context) != key) {
            return "IDENTITY_SUSPENDED"
        }
        if (generation != null && !completeGeneration(client, generation)) return "IDENTITY_TUPLE_PUBLISH_UNVERIFIED"
        return UltraIdentityBootHook.ensure(environment.verification.verified, preparingUpgrade,
            environment.readbackFailure ?: environment.verification.reason) {
            // The SDK's system directory is the 16-character prefix plus its first character.
            // Do not enumerate all modules, or uninstall a never-installed ID into its state lists.
            val path = "/data/${key.take(16)}${key.first()}/modules/${UltraIdentityBroker.LEGACY_MODULE_ID}"
            fun legacyState(): String? {
                val probe = client.runRootCommand("if [ -e '$path' ] || [ -L '$path' ]; then echo PRESENT; else echo ABSENT; fi")
                return if (probe.succeeded) client.decodeText(probe.string("result"))?.trim() else null
            }
            when (legacyState()) {
                "ABSENT" -> null
                "PRESENT" -> {
                    val removed = client.uninstallModule(UltraIdentityBroker.LEGACY_MODULE_ID)
                    when {
                        !removed.succeeded -> "IDENTITY_LEGACY_RETIRE_FAILED:${removed.sdkError}"
                        legacyState() != "ABSENT" -> "IDENTITY_LEGACY_RETIRE_UNVERIFIED"
                        else -> null
                    }
                }
                else -> "IDENTITY_LEGACY_PROBE_FAILED"
            }
        }
    }

    /** Only the authenticated root socket owner's PID is eligible for a controlled restart. */
    internal fun restartScript(pid: Int): String {
        require(pid > 1)
        return """
            P='$pid'
            if [ -d "/proc/${'$'}P" ]; then
                [ "${'$'}(stat -c %u /proc/${'$'}P)" = 0 ] || exit 2
                name=${'$'}(tr '\000' '\n' < "/proc/${'$'}P/cmdline" | head -n 1)
                [ "${'$'}name" = 'maplesu-ultra-identity' ] || exit 3
                kill -TERM "${'$'}P" || exit 4
                count=0
                while kill -0 "${'$'}P" 2>/dev/null; do
                    count=${'$'}((count + 1)); [ "${'$'}count" -lt 20 ] || exit 5
                    /system/bin/sleep 0.1
                done
            fi
            echo MAPLESU_IDENTITY_STOPPED
        """.trimIndent()
    }

    /** Keep the key on explicit removal, but prevent a surviving service from resuming it. */
    fun suspend(client: UltraRuntimeClient) {
        runCatching { client.runRootCommand("[ ! -d '${UltraIdentityBroker.DIRECTORY}' ] || { umask 077; : > '${UltraIdentityBroker.DISABLED}'; }") }
    }

    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val bytes = ByteArray(64 * 1024)
            while (true) { val n = input.read(bytes); if (n < 0) break; digest.update(bytes, 0, n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private fun startScript() = """
        #!/system/bin/sh
        umask 077
        D='${UltraIdentityBroker.DIRECTORY}'
        G="${'$'}{0%/*}"
        case "${'$'}G" in "${'$'}D"/generation-*) ;; *) exit 2 ;; esac
        generation="${'$'}{G##*/}"
        generation="${'$'}{generation#generation-}"
        case "${'$'}generation" in ''|*[!0-9a-f]*) exit 2 ;; esac
        [ "${'$'}{#generation}" = 32 ] || exit 2
        [ ! -e "${'$'}D/disabled" ] || exit 0
        count=0
        until [ "${'$'}(/system/bin/getprop sys.boot_completed)" = 1 ]; do
            count=${'$'}((count + 1)); [ "${'$'}count" -lt 150 ] || exit 1
            /system/bin/sleep 2
        done
        [ ! -e "${'$'}D/disabled" ] || exit 0
        ${dev.maplesu.manager.litehidden.UltraRecoveryBoot.content()}
        export CLASSPATH="${'$'}G/manager.apk"
        exec /system/bin/app_process -Dmaplesu.ultra.boot.generation="${'$'}generation" -Dmaplesu.ultra.boot.library="${'$'}G/libmaplesu_ultra_bridge.so" /system/bin --nice-name=maplesu-ultra-identity dev.maplesu.manager.runtime.ultra.UltraIdentityBroker
    """.trimIndent() + "\n"

    private fun deployScript(apk: File, hash: String, config: File, start: File, bridge: File,
                             bridgeHash: String, generation: String): String {
        require(generation.matches(Regex("[0-9a-f]{32}")))
        val configHash = sha256(config)
        val startHash = sha256(start)
        val launcher = "#!/system/bin/sh\nexec /system/bin/sh '${UltraIdentityBroker.DIRECTORY}/generation-$generation/start.sh'\n"
        return """
            (
            set -eu
            umask 077
            phase=ROOT
            published=0
            temporary=0
            retained=0
            launcher_written=0
            D='${UltraIdentityBroker.DIRECTORY}'
            G="${'$'}D/generation-$generation"
            T="${'$'}D/.generation-$generation"
            S="${'$'}D/.start-$generation.tmp"
            clean_tuple() {
                rm -f "${'$'}1/manager.apk" "${'$'}1/identity.json" "${'$'}1/start.sh" "${'$'}1/libmaplesu_ultra_bridge.so"
                rmdir "${'$'}1" 2>/dev/null || :
            }
            trap 'status=${'$'}?; if [ "${'$'}launcher_written" = 1 ]; then rm -f "${'$'}S"; fi; if [ "${'$'}temporary" = 1 ] && [ -d "${'$'}T" ]; then clean_tuple "${'$'}T"; fi; if [ "${'$'}retained" = 1 ] && [ "${'$'}published" = 0 ] && [ -d "${'$'}G" ]; then clean_tuple "${'$'}G"; fi; if [ "${'$'}status" -ne 0 ]; then printf "MAPLESU_IDENTITY_DEPLOY_ERROR:%s:%s\n" "${'$'}phase" "${'$'}status"; fi' EXIT
            [ "${'$'}(id -u)" = 0 ] || exit 2
            phase=DIRECTORY
            for p in /data/adb /data/adb/maplesu "${'$'}D"; do
                [ ! -L "${'$'}p" ] || exit 3
                [ ! -e "${'$'}p" ] || { [ -d "${'$'}p" ] && [ "${'$'}(stat -c %u "${'$'}p")" = 0 ]; } || exit 3
                if [ -e "${'$'}p" ]; then mode=${'$'}(stat -c %a "${'$'}p"); [ "${'$'}((8#${'$'}mode & 8#22))" -eq 0 ] || exit 3; fi
            done
            mkdir -p "${'$'}D"; chmod 700 "${'$'}D"
            [ ! -e "${'$'}G" ] && [ ! -L "${'$'}G" ] && [ ! -e "${'$'}T" ] && [ ! -L "${'$'}T" ] || exit 3
            [ ! -L "${'$'}D/start.sh" ] && [ ! -L "${'$'}D/disabled" ] && [ ! -e "${'$'}S" ] && [ ! -L "${'$'}S" ] || exit 3
            mkdir "${'$'}T"; temporary=1; chmod 700 "${'$'}T"
            phase=APK
            cp ${quote(apk.path)} "${'$'}T/manager.apk"
            [ "${'$'}(sha256sum "${'$'}T/manager.apk" | cut -d ' ' -f 1)" = '$hash' ] || exit 4
            phase=BRIDGE
            cp ${quote(bridge.path)} "${'$'}T/libmaplesu_ultra_bridge.so"
            [ "${'$'}(sha256sum "${'$'}T/libmaplesu_ultra_bridge.so" | cut -d ' ' -f 1)" = '$bridgeHash' ] || exit 4
            phase=CONFIG
            cp ${quote(config.path)} "${'$'}T/identity.json"
            cp ${quote(start.path)} "${'$'}T/start.sh"
            [ "${'$'}(sha256sum "${'$'}T/identity.json" | cut -d ' ' -f 1)" = '$configHash' ] || exit 4
            [ "${'$'}(sha256sum "${'$'}T/start.sh" | cut -d ' ' -f 1)" = '$startHash' ] || exit 4
            chmod 600 "${'$'}T/manager.apk" "${'$'}T/libmaplesu_ultra_bridge.so" "${'$'}T/identity.json"
            chmod 700 "${'$'}T/start.sh"
            for name in manager.apk libmaplesu_ultra_bridge.so identity.json; do
                [ "${'$'}(stat -c '%a %u %h' "${'$'}T/${'$'}name")" = '600 0 1' ] || exit 4
            done
            [ "${'$'}(stat -c '%a %u %h' "${'$'}T/start.sh")" = '700 0 1' ] || exit 4
            phase=PUBLISH
            mv "${'$'}T" "${'$'}G"
            retained=1
            printf '%s' ${quote(launcher)} > "${'$'}S"
            launcher_written=1
            chmod 700 "${'$'}S"
            [ "${'$'}(stat -c '%a %u %h' "${'$'}S")" = '700 0 1' ] || exit 4
            sync
            mv -f "${'$'}S" "${'$'}D/start.sh"
            published=1
            rm -f "${'$'}D/disabled"
            sync
            echo $READY
            ) 2>&1
        """.trimIndent()
    }

    private fun completeGeneration(client: UltraRuntimeClient, generation: String): Boolean {
        require(generation.matches(Regex("[0-9a-f]{32}")))
        val script = """
            (
            set -eu
            umask 077
            D='${UltraIdentityBroker.DIRECTORY}'
            G="${'$'}D/generation-$generation"
            [ "${'$'}(id -u)" = 0 ] || exit 2
            [ ! -L "${'$'}D" ] && [ "${'$'}(stat -c '%a %u' "${'$'}D")" = '700 0' ] || exit 3
            [ ! -L "${'$'}G" ] && [ "${'$'}(stat -c '%a %u' "${'$'}G")" = '700 0' ] || exit 3
            [ ! -L "${'$'}G/identity.json" ] && [ "${'$'}(stat -c '%a %u %h' "${'$'}G/identity.json")" = '600 0 1' ] || exit 3
            P="${'$'}D/.identity-$generation.tmp"
            [ ! -L "${'$'}D/identity.json" ] && [ ! -e "${'$'}P" ] && [ ! -L "${'$'}P" ] || exit 3
            trap 'rm -f "${'$'}P"' EXIT
            cp "${'$'}G/identity.json" "${'$'}P"
            chmod 600 "${'$'}P"
            cmp -s "${'$'}G/identity.json" "${'$'}P"
            mv -f "${'$'}P" "${'$'}D/identity.json"
            rm -f "${'$'}D/${dev.maplesu.manager.compat.StoredInputs.BOOT_APK}" "${'$'}D/${dev.maplesu.manager.compat.StoredInputs.BOOT_LIBRARY}"
            sync
            echo MAPLESU_IDENTITY_GENERATION_COMMITTED
            ) 2>&1
        """.trimIndent()
        val result = client.runRootCommand(script)
        return result.succeeded && client.decodeText(result.string("result"))?.trim() == "MAPLESU_IDENTITY_GENERATION_COMMITTED"
    }
}
