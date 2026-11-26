package com.maplesu.core.ui.util

import com.maplesu.core.ksuApp
import dev.maplesu.manager.runtime.HookRootExecutor
import dev.maplesu.manager.runtime.RootManagement
import dev.maplesu.manager.runtime.ultra.UltraEnvironmentProbe
import dev.maplesu.manager.runtime.ultra.UltraRuntimeClient
import dev.maplesu.manager.runtime.ultra.UltraSdkCredentials
import java.io.File
import java.util.concurrent.CountDownLatch

/**
 * Returns the raw SELinux status. Call on an IO thread: an app domain may need
 * the authenticated Ultra executor when its local enforce file is unreadable.
 * SDK systemStatus can also use an app-domain file read; Unknown is preserved.
 */
fun getSELinuxStatusRaw(): String = getSELinuxStatusRaw { File("/sys/fs/selinux/enforce").readText() }

internal fun getSELinuxStatusRaw(readEnforce: () -> String): String {
    val hook = HookRootExecutor.production(ksuApp)
    if (hook.available()) {
        return resolveSELinuxStatus(
            readEnforce = readEnforce,
            queryStatus = {
                val result = hook.run("/system/bin/getenforce")
                if (result.ok) result.output else ""
            },
        )
    }
    if (usesUltraEnvironment()) {
        val scope = currentUltraScope() ?: return "Unknown"
        return readUltraStatus(scope, readEnforce)
    }
    return resolveSELinuxStatus(
        readEnforce = readEnforce,
        queryStatus = {
            // An ordinary app-owned sh has the same SELinux restrictions as this
            // process. Use MapleSU's authenticated executor, not another su manager.
            RootManagement.openShell(ksuApp).use { shell ->
                val result = shell.newJob().add("/system/bin/getenforce")
                    .to(ArrayList<String>(), ArrayList<String>()).exec()
                if (result.isSuccess) result.out.joinToString("\n") else ""
            }
        },
    )
}

// Reuse a known observation briefly; Unknown never extends its lifetime.
private const val ULTRA_STATUS_TTL_MS = 5_000L

private data class UltraSELinuxScope(
    val key: String?,
    val bootId: String,
    val owner: UltraRuntimeClient.ManagerOwnerIdentity?,
)
private data class UltraSELinuxObservation(
    val value: String,
    val observedAt: Long,
    val scope: UltraSELinuxScope,
    val revision: Long,
)

/** Request identity only; the ordinary class never formats credential contents. */
internal class UltraSELinuxObservationToken internal constructor(
    internal val key: String?,
    internal val bootId: String,
    internal val revision: Long,
    internal val owner: UltraRuntimeClient.ManagerOwnerIdentity?,
)

private class UltraSELinuxRead(val revision: Long) {
    val completed = CountDownLatch(1)
    @Volatile var result = "Unknown"
}

private var ultraStatusCache: UltraSELinuxObservation? = null
private val ultraStatusReads = mutableMapOf<UltraSELinuxScope, UltraSELinuxRead>()
private var ultraStatusRevision = 0L

private fun currentUltraScope(): UltraSELinuxScope? = runCatching {
    if (HookRootExecutor.production(ksuApp).available() || !usesUltraEnvironment()) return null
    val key = UltraSdkCredentials.read(ksuApp)
    val bootId = dev.maplesu.manager.runtime.ultra.UltraEnvironmentAutoSetup.currentBootId() ?: return null
    val owner = if (key == null) UltraEnvironmentProbe.currentSnapshot(ksuApp).managerOwnerIdentity
        ?.takeIf { it.fullUid == android.os.Process.myUid() } ?: return null else null
    UltraSELinuxScope(key, bootId, owner)
}.getOrNull()

private fun UltraSELinuxObservationToken.scope() = UltraSELinuxScope(key, bootId, owner)

/** Capture before the producer starts its existing SDK request, on an IO thread. */
internal fun captureUltraSELinuxObservation(): UltraSELinuxObservationToken? {
    val scope = currentUltraScope() ?: return null
    return synchronized(ultraStatusReads) {
        UltraSELinuxObservationToken(scope.key, scope.bootId, ultraStatusRevision, scope.owner)
    }
}

/** A known write invalidates observations only; it never queries or changes SELinux. */
internal fun invalidateUltraSELinuxStatus() {
    synchronized(ultraStatusReads) {
        ultraStatusRevision++
        ultraStatusCache = null
        ultraStatusReads.clear()
    }
}

private fun isCurrentObservation(token: UltraSELinuxObservationToken): Boolean {
    if (currentUltraScope() != token.scope()) return false
    return synchronized(ultraStatusReads) { ultraStatusRevision == token.revision }
}

// Called only while holding the existing observation metadata monitor.
private fun cachedUltraStatus(scope: UltraSELinuxScope, revision: Long): String? {
    val cached = ultraStatusCache ?: return null
    val age = android.os.SystemClock.elapsedRealtime() - cached.observedAt
    return cached.value.takeIf {
        cached.scope == scope && cached.revision == revision && age in 0 until ULTRA_STATUS_TTL_MS
    }
}

/** Returns only a current known observation, without querying SELinux. */
fun peekCachedSELinuxStatus(): String? {
    val token = captureUltraSELinuxObservation() ?: return null
    val value = synchronized(ultraStatusReads) { cachedUltraStatus(token.scope(), token.revision) } ?: return null
    return value.takeIf { isCurrentObservation(token) }
}

/** Publish only a verified observation belonging to the original request and revision. */
internal fun rememberUltraSELinuxStatus(value: String, token: UltraSELinuxObservationToken?): Boolean {
    if (token == null || value !in setOf("Enforcing", "Permissive", "Disabled")) return false
    val scope = token.scope()
    if (currentUltraScope() != scope) return false
    val now = android.os.SystemClock.elapsedRealtime()
    return synchronized(ultraStatusReads) {
        if (ultraStatusRevision != token.revision) false else {
            ultraStatusCache = UltraSELinuxObservation(value, now, scope, token.revision)
            true
        }
    }
}

private fun readUltraStatus(scope: UltraSELinuxScope, readEnforce: () -> String): String {
    var cached: String? = null
    var owner = false
    lateinit var token: UltraSELinuxObservationToken
    val read = synchronized(ultraStatusReads) {
        token = UltraSELinuxObservationToken(scope.key, scope.bootId, ultraStatusRevision, scope.owner)
        cached = cachedUltraStatus(scope, token.revision)
        if (cached != null) null else ultraStatusReads[scope]?.takeIf { it.revision == token.revision }
            ?: UltraSELinuxRead(token.revision).also {
                ultraStatusReads[scope] = it
                owner = true
            }
    }
    cached?.let { return if (isCurrentObservation(token)) it else "Unknown" }
    checkNotNull(read)
    if (owner) {
        try {
            val value = runCatching {
                when (runCatching { readEnforce().trim() }.getOrNull()) {
                    "1" -> "Enforcing"
                    "0" -> "Permissive"
                    else -> ultraEnvironmentSelinuxStatus(token) ?: "Unknown"
                }
            }.getOrDefault("Unknown")
            if (rememberUltraSELinuxStatus(value, token)) read.result = value
        } finally {
            synchronized(ultraStatusReads) {
                if (ultraStatusReads[scope] === read) ultraStatusReads.remove(scope)
            }
            read.completed.countDown()
        }
    } else {
        try {
            read.completed.await()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return "Unknown"
        }
    }
    return if (isCurrentObservation(token)) read.result else "Unknown"
}

/**
 * SELinux state from the Ultra SDK. `null` means "the SDK could not read it", which the caller
 * must render as unknown rather than as Permissive.
 */
private fun ultraEnvironmentSelinuxStatus(token: UltraSELinuxObservationToken): String? {
    val source = UltraSdkCredentials.client(ksuApp, token.key, token.bootId)
    fun scopeCurrent(): Boolean = isCurrentObservation(token) && (token.owner == null ||
        runCatching { source.queryManagerOwnerIdentity().identity }.getOrNull() == token.owner)
    if (!scopeCurrent()) return null
    val client = source.scoped(token.key, ::scopeCurrent)
    val receipt = runCatching { client.systemStatus() }.getOrNull()
    if (!scopeCurrent()) return null
    if (receipt != null && receipt.succeeded && receipt.bool("selinux_readable") != false) {
        when (receipt.int("selinux")) {
            1 -> return "Enforcing"
            0 -> return "Permissive"
        }
    }
    // 实测（本机 Ultra 内核）：SDK 自己的 `get_system_status` 回
    //   {"selinux":-1,"selinux_readable":false} —— 它读不到 enforce。
    // 退回 SDK 的 `run_root_cmd` 跑一次 getenforce：仍然是 SDK 自己的 root 通道，
    // **不碰** libmaplesu_runtime（那条路才会重启系统）。
    val rootReceipt = runCatching { client.runRootCommand("/system/bin/getenforce") }.getOrNull()
    if (!scopeCurrent()) return null
    val text = rootReceipt?.takeIf { it.succeeded }?.string("result")
        ?.let { client.decodeText(it) }?.trim()
    return when (text) {
        "Enforcing" -> "Enforcing"
        "Permissive" -> "Permissive"
        "Disabled" -> "Disabled"
        else -> null
    }
}

/** A temporary probe failure must not launch a streaming shell just to read getenforce. */
private fun usesUltraEnvironment(): Boolean =
    runCatching { UltraEnvironmentProbe.usesUltraBackend(ksuApp) || UltraEnvironmentProbe.isRunning() }.getOrDefault(false)

internal fun resolveSELinuxStatus(readEnforce: () -> String, queryStatus: () -> String): String {
    when (runCatching(readEnforce).getOrNull()?.trim()) {
        "1" -> return "Enforcing"
        "0" -> return "Permissive"
    }
    return when (val status = runCatching(queryStatus).getOrDefault("").trim()) {
        "Enforcing", "Permissive", "Disabled" -> status
        else -> "Unknown"
    }
}
