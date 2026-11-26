package dev.maplesu.manager.jailbreak

import android.content.Context
import com.topjohnwu.superuser.Shell
import dev.maplesu.manager.runtime.NativeSuDirectoryProvider
import dev.maplesu.manager.runtime.JailbreakLeaseArgv
import java.io.IOException
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Holds the one authenticated raw lease produced by a provider attempt inside the
 * `:root_directory` process.
 *
 * The lease is a [Process], so the trusted daemon launcher consumes it exactly
 * like the normal-mode shell: `Shell.Builder.create().setContext(app)
 * .setTimeout(10).build(process)`. Nothing here grants authorisation, marks the
 * App installed or persists a "rooted" flag; the lease is bound to one boot, one
 * provider and one attempt id, and is dropped when its transport dies.
 */
internal object JailbreakLeaseStore {
    private val claimed = AtomicBoolean(false)
    private val executorProbeLock = Any()

    @Volatile private var lease: RootShellLease? = null
    @Volatile private var transport: JailbreakLeaseTransport? = null
    @Volatile private var method: String? = null
    @Volatile private var attempt: String? = null
    @Volatile private var lastFailure: String? = null
    @Volatile private var handedOver = false
    @Volatile private var executorPid = 0
    @Volatile private var lastPingAt = 0L
    @Volatile private var lastPingOk = false

    /** Starts one attempt: nothing recorded by an earlier attempt survives it. */
    @Synchronized
    fun begin(attemptId: String, provider: String) {
        release()
        lastFailure = null
        attempt = attemptId
        method = provider
    }

    @Synchronized
    fun record(lease: RootShellLease, provider: String, attemptId: String) {
        release()
        this.lease = lease
        transport = JailbreakLeaseTransport(lease, sequence) { source, reason ->
            synchronized(this) {
                if (transport === source) lastFailure = reason
            }
        }
        method = provider
        attempt = attemptId
        claimed.set(false)
        lastFailure = null
    }

    /**
     * A failed attempt never keeps a live transport: leaving the lease open would
     * keep the provider's privileged consumer alive after the product already
     * reported failure.
     */
    @Synchronized
    fun recordFailure(reason: String) {
        lastFailure = reason
        release()
    }

    private fun release() {
        val current = lease
        val previous = transport
        transport = null
        lease = null
        claimed.set(false)
        handedOver = false
        executorPid = 0
        lastPingAt = 0L
        lastPingOk = false
        if (previous != null) previous.close("JAILBREAK_LEASE_REPLACED")
        else if (current != null) runCatching { current.close() }
    }

    val identity: LeaseProtocol.Identity?
        get() = lease?.identity()

    val restore: LeaseProtocol.Restore?
        get() = lease?.restore()

    val failure: String?
        get() = lastFailure

    val provider: String?
        get() = method

    val attemptId: String?
        get() = attempt

    val pid: Int
        get() = lease?.identity()?.workerPid ?: -1

    /**
     * Liveness of the capability, not of the launcher.
     *
     * S04: once the executor owns the lease the launcher shell is gone by design,
     * so liveness has to follow the published Root endpoint instead of the lease
     * transport - otherwise every successful attempt would read as EXPIRED.
     */
    /**
     * After the handover the consumer exits by design and its socket closes, while the
     * executor it handed the lease to keeps serving. Liveness therefore follows the
     * executor process (recorded at launch) and not the socket.
     */
    val live: Boolean
        get() = when {
            handedOver -> executorAnswers()
            else -> lease?.isAlive == true
        }

    /**
     * A uid-0 executor is not visible in /proc from the app uid, so liveness cannot
     * be observed directly; the only honest probe is asking it. Cached briefly so a
     * status read does not hammer the transport.
     */
    private fun executorAnswers(): Boolean {
        // Share only completed probes. While one ping is in flight, another reader
        // must not observe lastPingOk's initial false as an executor death.
        synchronized(executorProbeLock) {
            val current: JailbreakLeaseTransport
            synchronized(this) {
                current = transport ?: return false
                if (current.failure != null) return false
                val now = System.currentTimeMillis()
                if (now - lastPingAt < 2_000) return lastPingOk
            }
            val answered = try {
                current.request("ping", 2).startsWith("OK ")
            } catch (error: Throwable) {
                false
            }
            return synchronized(this) {
                if (transport !== current || current.failure != null) false
                else { lastPingAt = System.currentTimeMillis(); lastPingOk = answered; answered }
            }
        }
    }

    /** Records the executor answered by this attempt, so liveness can follow it. */
    fun recordExecutor(pid: Int) {
        if (pid > 1) executorPid = pid
    }

    private fun executorAlive(): Boolean =
        executorPid > 1 && java.io.File("/proc/$executorPid").exists()

    /**
     * Direct transport proof on the lease itself.
     *
     * DEV-10: libsu tasks over this lease are accepted but never reach the shell,
     * so the product cannot use a libsu Shell as the launcher. The lease owns the
     * shell's stdin/stdout directly, and this is the smallest test of that
     * channel: one command in, one line out, bounded.
     */
    fun probeTransport(command: String = "id -u"): String {
        val current = lease ?: return "no-lease"
        val output = current.outputStream
        val worker = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "maplesu-lease-probe").apply { isDaemon = true }
        }
        return try {
            output.write((command + "\n").toByteArray())
            output.flush()
            val line = worker.submit<String?> {
                val reader = java.io.BufferedReader(java.io.InputStreamReader(current.inputStream))
                reader.readLine()
            }.get(3, java.util.concurrent.TimeUnit.SECONDS)
            "ok:" + (line ?: "null")
        } catch (error: Throwable) {
            "failed:" + (error.cause?.message ?: error.message ?: error.javaClass.simpleName)
        } finally {
            worker.shutdownNow()
        }
    }

    private val sequence = java.util.concurrent.atomic.AtomicInteger(0)

    /** Capture BEFORE start; all task I/O stays on this original lease even if
     * the store is rebound. Consumers retain Registration.failure separately
     * from a child exit code and close their pipes on transport failure. */
    @Synchronized
    fun eventTransport(): JailbreakLeaseTransport =
        transport ?: throw IllegalStateException("JAILBREAK_LEASE_MISSING")

    fun registerEvents(token: Int, listener: (String) -> Unit): JailbreakLeaseTransport.Registration =
        eventTransport().registerEvents(token, listener)

    fun unregisterEvents(token: Int) {
        transport?.unregisterEvents(token)
    }

    @Synchronized
    fun current(): RootShellLease? = lease?.takeIf { it.isAlive }

    /** One existing sequence-tagged request/reply; waits never own the store
     * monitor. The transport serializes only the complete outbound line. */
    fun request(line: String, timeoutSeconds: Long = 10): String =
        eventTransport().request(line, timeoutSeconds)

    fun requestChecked(line: String, timeoutSeconds: Long = 6, maxLines: Int = 16): List<String> {
        require(maxLines > 0) { "JAILBREAK_REPLY_LIMIT_INVALID" }
        return eventTransport().exchange(line, timeoutSeconds)
    }

    /**
     * One argv1 command on the original lease; only a complete exit-0 reply
     * returns stdout lines. Failure retains the exact reply, including exit and
     * both raw Base64 streams, instead of turning unknown/truncated output into success.
     */
    fun command(line: String, timeoutSeconds: Long = 10, maxLines: Int = 64): List<String> {
        require(maxLines > 0) { "JAILBREAK_COMMAND_LINE_LIMIT_INVALID" }
        val encoded = JailbreakLeaseArgv.encode(arrayOf("/system/bin/sh", "-c", line))
        val reply = request("run $encoded format=argv1", timeoutSeconds)
        if (reply.startsWith("ERR ")) throw IOException(reply)
        fun failed(reason: String, cause: Throwable? = null): Nothing =
            throw IOException("$reason $reply", cause)
        val result = Regex("OK exit=(-?[0-9]+) out=([^ ]*) err=([^ ]*)").matchEntire(reply)
            ?: failed("JAILBREAK_COMMAND_REPLY_INVALID")
        val exit = result.groupValues[1].toIntOrNull() ?: failed("JAILBREAK_COMMAND_REPLY_INVALID")
        if (exit.toString() != result.groupValues[1]) failed("JAILBREAK_COMMAND_REPLY_INVALID")
        // Same per-stream raw-byte capture bound as the resident executor.
        val captureLimit = 256 * 1024
        fun decode(value: String): ByteArray {
            if (value.length > ((captureLimit + 2) / 3) * 4) failed("JAILBREAK_COMMAND_CAPTURE_LIMIT")
            val bytes = try {
                Base64.getDecoder().decode(value)
            } catch (error: IllegalArgumentException) {
                failed("JAILBREAK_COMMAND_BASE64_INVALID", error)
            }
            if (bytes.size > captureLimit) failed("JAILBREAK_COMMAND_CAPTURE_LIMIT")
            if (Base64.getEncoder().encodeToString(bytes) != value) failed("JAILBREAK_COMMAND_BASE64_INVALID")
            return bytes
        }
        val out = decode(result.groupValues[2])
        decode(result.groupValues[3])
        if (exit < 0) failed("JAILBREAK_COMMAND_EXIT_UNKNOWN")
        if (exit != 0) failed("JAILBREAK_COMMAND_EXIT_FAILED")
        val text = out.toString(Charsets.UTF_8)
        if (!text.toByteArray(Charsets.UTF_8).contentEquals(out)) failed("JAILBREAK_COMMAND_STDOUT_ENCODING_INVALID")
        val lines = if (text.isEmpty()) emptyList() else text.removeSuffix("\n").split('\n')
        if (lines.size > maxLines) failed("JAILBREAK_COMMAND_LINE_LIMIT")
        return lines
    }

    /**
     * Called by the launcher after the executor answered its identity: from here
     * on the consumer keeps the detached executor and the product must not reap it.
     */
    @Synchronized
    fun confirmHandover() {
        val current = lease ?: return
        current.confirmHandover()
        if (current.handedOver()) handedOver = true
    }

    /**
     * Wraps the lease once as the trusted launcher shell for the same attempt. A
     * second call, a different attempt id or a dead transport returns null so a
     * stale transport can never be reused for a new daemon.
     */
    @Synchronized
    fun claimForDaemon(context: Context, attemptId: String?): Shell? {
        val current = lease ?: return null
        if (attemptId == null || attemptId != attempt) return null
        if (!claimed.compareAndSet(false, true)) return null
        if (!current.isAlive) { claimed.set(false); return null }
        val shell = try {
            Shell.Builder.create().setContext(context.applicationContext).setTimeout(10).build(current)
        } catch (error: Exception) {
            // S12: a throw here used to leave the claim consumed, so no later call
            // in this attempt could ever claim the lease.
            claimed.set(false)
            return null
        }
        if (!shell.isAlive || !shell.isRoot) {
            claimed.set(false)
            runCatching { shell.close() }
            return null
        }
        return shell
    }

    /** Aborts the pending transport of the running attempt without killing the native stage. */
    @Synchronized
    fun cancelPending() {
        transport?.close("JAILBREAK_LEASE_CANCELLED")
    }

    @Synchronized
    fun clear() {
        release()
        method = null
        attempt = null
    }

}
