// SPDX-License-Identifier: GPL-3.0-or-later
package dev.maplesu.manager.runtime.ultra

import dev.maplesu.manager.compat.ultraPreferences

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import android.util.Log
import dev.maplesu.manager.runtime.ultra.bridge.NativeBridge

/** 「这份身份是从哪读来的」。[Unknown] 表示读不到，不是「没有身份」。 */
enum class Source { Readback, Persisted, Unknown }

/**
 * 设备上**已安装**环境的身份。
 *
 * <p>与随包身份（[UltraSdkBuild]）分开：同一上游版本可以对应多个构建，只有把两边放在一起
 * 才可能判断「是不是这一版」。字段全部可为空，空即「不知道」——调用方不得把 null 当成
 * 「相同」，也不得把「读不到」说成「已是最新」。
 *
 * @param helperSha256 归一化 key 槽之后的 helper 摘要（64 位小写十六进制）
 * @param helperPath   设备上的绝对路径（仅供显示/排障）
 * @param helperSize   字节数
 * @param buildId      `maplesu-ultra-5.0.5-r1` 形态；磁盘上不存在该字段，按摘要反查
 * @param bootId       读到该身份时的 `/proc/sys/kernel/random/boot_id`
 * @param source       [Source.Readback] 才能用于「升级已生效」的结论
 */
data class InstalledIdentity(
    val helperSha256: String?,
    val helperPath: String?,
    val helperSize: Long?,
    val buildId: String?,
    val bootId: String?,
    val source: Source,
) {
    /** 是否有可用于比较的身份材料（摘要或构建号之一）。 */
    val known: Boolean
        get() = source != Source.Unknown && (UltraInstalledIdentityProbe.normalizeSha(helperSha256) != null || buildId != null)

    companion object {
        fun unknown(bootId: String? = null): InstalledIdentity =
            InstalledIdentity(null, null, null, null, bootId, Source.Unknown)
    }
}

/** 一次身份回读 + 与随包身份核对的结果。[reason] 是机器码，可直接进日志/界面。 */
data class IdentityVerification(
    val verified: Boolean,
    val reason: String?,
    val buildId: String?,
) {
    companion object {
        fun passed(buildId: String?): IdentityVerification = IdentityVerification(true, null, buildId)
    }
}

/** 回读到的身份 + 核对结论（安装路径只在同一把锁内使用一次）。 */
data class IdentityObservation(
    val identity: InstalledIdentity,
    val verification: IdentityVerification,
    val readbackFailure: String? = null,
)

object UltraInstalledIdentityProbe {

    private const val TAG = "MapleSUUltraIdentity"

    const val PREFS = "ultra-env-identity"
    const val CACHE_TTL_MS = 60_000L
    internal const val MAINTENANCE_READBACK_TIMEOUT_MS = 5_000

    private const val KEY_SHA = "helperSha256"
    private const val KEY_PATH = "helperPath"
    private const val KEY_SIZE = "helperSize"
    private const val KEY_BUILD = "buildId"
    private const val KEY_BOOT = "bootId"
    private const val KEY_AT = "verifiedAtMillis"
    private const val KEY_APP = "appVersion"
    private const val KEY_CREDENTIAL = "credentialFingerprint"

    private val SHA256 = Regex("""[0-9a-f]{64}""")
    private val HELPER_PATH = Regex("/data/[A-Za-z0-9]{16}/init")

    /** 一次回读的原始结果。 */
    data class Readback(val sha256: String, val path: String, val size: Long)

    private val cache = UltraIdentityReadCache(SystemClock::elapsedRealtime, CACHE_TTL_MS)

    private val completedReadbacks = MutableStateFlow(0L)
    val readbackRevision = completedReadbacks.asStateFlow()

    @Volatile
    private var bridgeIdentity: NativeReceipt? = null

    @Volatile
    private var bridgeIdentityRead = false

    // --- 读 -------------------------------------------------------------------------

    /**
     * 读设备上已装环境的身份（后台线程）。
     *
     * @param client 使用当前环境的 SDK Root 通道回读，不依赖独立的 su 授权。
     */
    fun read(context: Context, client: UltraRuntimeClient, force: Boolean = false): InstalledIdentity =
        readWithBudget(context, client, force, 300) {}

    private fun readWithBudget(context: Context, client: UltraRuntimeClient, force: Boolean,
                               timeoutMillis: Int, failure: (NativeReceipt?) -> Unit): InstalledIdentity {
        if (Thread.currentThread().isInterrupted) throw CancellationException("IDENTITY_READBACK_INTERRUPTED")
        check(Looper.myLooper() != Looper.getMainLooper()) { "ULTRA_IDENTITY_BACKGROUND_THREAD_REQUIRED" }
        val key = keyFingerprint(context)
        val bootId = UltraEnvironmentAutoSetup.currentBootId()
        var completedReadback = false
        val observed = cache.read(key, bootId, force) {
            val identity = readFromDevice(context, client, timeoutMillis, failure)
                ?: readPersisted(context)
                ?: InstalledIdentity.unknown(bootId)
            if (Thread.currentThread().isInterrupted || key != keyFingerprint(context) || bootId != UltraEnvironmentAutoSetup.currentBootId()) {
                InstalledIdentity.unknown()
            } else {
                Log.i(TAG, "identity source=${identity.source} known=${identity.known} buildId=${identity.buildId.orEmpty()}")
                completedReadback = identity.source == Source.Readback
                identity
            }
        }
        // A completed live read replaces an earlier display observation. Cached
        // reads and persisted fallbacks do not create another refresh event.
        if (completedReadback) completedReadbacks.update { it + 1 }
        return observed
    }

    /** A local completion event never starts another helper/SDK read. */
    fun completedReadback(context: Context): InstalledIdentity? {
        val fingerprint = keyFingerprint(context)
        val bootId = UltraEnvironmentAutoSetup.currentBootId()
        val identity = cache.peek(fingerprint, bootId)
        return identity?.takeIf { fingerprint == keyFingerprint(context) &&
            bootId == UltraEnvironmentAutoSetup.currentBootId() }
    }

    private fun keyFingerprint(context: Context): String? {
        val key = UltraSdkCredentials.read(context)
        val observation = UltraEnvironmentProbe.currentSnapshot(context)
        return identityFingerprint(key, observation.managerOwnerIdentity?.takeIf { it.fullUid == android.os.Process.myUid() },
            UltraEnvironmentAutoSetup.currentBootId(),
            System.identityHashCode(observation.modeActivation))
    }

    /** Identity partitions cached file evidence; none of these fields grant Root. */
    internal fun identityFingerprint(key: String?, owner: UltraRuntimeClient.ManagerOwnerIdentity?,
                                     bootId: String?, activation: Int): String? {
        if (bootId.isNullOrBlank() || key == null && owner == null) return null
        val material = "key:$key:owner:${owner?.fullUid}:${owner?.epoch}:${owner?.hostInstance}:$bootId:$activation"
        return MessageDigest.getInstance("SHA-256").digest(material.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    /**
     * 回读 + 与随包身份核对（安装路径用；回读前先失效缓存）。
     *
     * @param attempts 安装刚结束时文件可能还没落齐：允许有界重试（默认 1 次，不重试）
     * @param delayMillis 两次尝试之间的等待
     */
    fun readAndVerify(
        context: Context,
        client: UltraRuntimeClient,
        attempts: Int = 1,
        delayMillis: Long = 0L,
        readbackTimeoutMillis: Int = 300,
    ): IdentityObservation {
        var last = IdentityObservation(
            InstalledIdentity.unknown(),
            IdentityVerification(false, "IDENTITY_UNREADABLE", null),
        )
        repeat(attempts.coerceAtLeast(1)) { index ->
            var failedReadback: NativeReceipt? = null
            var readbackFailed = false
            val identity = readWithBudget(context, client, true, readbackTimeoutMillis) {
                readbackFailed = true
                failedReadback = it
            }
            val verification = verifyAgainstBundled(identity)
            val failure = if (!readbackFailed) null else {
                val code = if (failedReadback?.succeeded == true) "IDENTITY_READBACK_RECEIPT_INVALID"
                    else failedReadback?.sdkError?.takeIf { it.matches(Regex("[A-Za-z0-9_]{1,96}")) } ?: "NO_RECEIPT"
                val stage = failedReadback?.data?.optString("failureStage")
                    ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,64}")) } ?: "readback"
                "$code:$stage:${failedReadback?.data?.optInt("systemError") ?: 0}"
            }
            last = IdentityObservation(identity, verification, failure)
            if (verification.verified) return last
            if (Thread.currentThread().isInterrupted || failedReadback?.data?.optBoolean("cleanupPending") == true ||
                failedReadback?.data?.optBoolean("timedOut") == true) return last
            if (index + 1 < attempts && delayMillis > 0) {
                try { Thread.sleep(delayMillis) } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return last
                }
            }
        }
        return last
    }

    /** 丢掉进程内缓存：安装/卸载之后必须调用。 */
    fun invalidate() {
        cache.invalidate()
    }

    private fun readFromDevice(context: Context, client: UltraRuntimeClient, timeoutMillis: Int,
                               failure: (NativeReceipt?) -> Unit): InstalledIdentity? {
        val helperPath = installedHelperPath(UltraSdkCredentials.read(context))
        val offset = keySlotOffset()
        // Read the installed length, including helpers from an older build.
        val line = locateInstalledHelper(client, helperPath, offset, timeoutMillis, failure)
            ?: return null
        if (helperPath != null && line.path != helperPath || line.size <= 0) return null
        return InstalledIdentity(
            helperSha256 = line.sha256,
            helperPath = line.path,
            helperSize = line.size,
            buildId = buildIdFor(line.sha256, readPersisted(context)),
            bootId = UltraEnvironmentAutoSetup.currentBootId(),
            source = Source.Readback,
        )
    }

    private fun locateInstalledHelper(client: UltraRuntimeClient, path: String?, offset: Long?, timeoutMillis: Int,
                                      failure: (NativeReceipt?) -> Unit): Readback? {
        val result = try { client.readInstalledHelper(path, offset, timeoutMillis) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: InterruptedException) { Thread.currentThread().interrupt(); throw CancellationException("IDENTITY_READBACK_INTERRUPTED") }
        catch (error: Exception) { Log.w(TAG, "identity readback threw ${error.javaClass.simpleName}"); null }
        if (result == null || !result.succeeded) {
            failure(result)
            val diagnostics = listOf("failureStage", "rootPhase", "timeoutMillis", "elapsedUs", "completionUs",
                "admissionUs", "forkUs", "authenticationUs", "sourceUs", "receiveUs", "waitUs", "systemError", "uidError", "modeError",
                "channelError", "sdkError", "credentialsError", "rootSystemError", "producerError", "sinkError",
                "transportError", "waitError", "admissionError", "readAttempted", "headerReceived", "producerEndReceived",
                "sourceEof", "metadataStable", "pathStable", "rootCheckpointReceived", "receiptComplete", "streamEof",
                "uidObserved", "taskStateKnown", "rootVerified", "ownerMode", "namespaceRequested", "namespaceResolved",
                "digestAttempted", "digestComplete", "timedOut", "cleanupPending", "cleanupScheduled", "cleanupError",
                "watchdogArmed", "watchdogError", "childIoError", "childException", "terminationRequested",
                "terminationError", "childStatusValid", "childStatus").mapNotNull { field ->
                val value = result?.data?.opt(field)
                when {
                    field in setOf("failureStage", "rootPhase") && value is String && value.matches(Regex("[A-Za-z0-9_-]{1,64}")) -> "$field=$value"
                    value is Boolean || value is Number || value is String && value.toLongOrNull() != null -> "$field=$value"
                    else -> null
                }
            }.joinToString(" ")
            Log.i(TAG, UltraSdkLogging.redact("identity readback unavailable sdkError=${result?.sdkError} $diagnostics"))
            return null
        }
        val readback = if (path == null) parseOwnerReadback(result, offset, android.os.Process.myUid())
            else parseReadback(result, path, offset)
        if (readback == null) failure(result)
        else Log.i(TAG, "identity readback complete timeoutMillis=$timeoutMillis " +
            listOf("admissionUs", "forkUs", "authenticationUs", "sourceUs", "receiveUs", "waitUs", "elapsedUs")
                .joinToString(" ") { "$it=${result.data?.optLong(it) ?: 0L}" })
        return readback
    }

    // --- 持久化 ---------------------------------------------------------------------

    /**
     * 落「上次安装并校验通过」的身份。只接受 [Source.Readback] 且摘要合法：
     * 未经验证的身份写进来，下一次读回失败时就会被当成真的。
     */
    fun persist(context: Context, identity: InstalledIdentity): Boolean {
        val sha = normalizeSha(identity.helperSha256) ?: return false
        if (identity.source != Source.Readback) return false
        val fingerprint = keyFingerprint(context) ?: return false
        return context.ultraPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_CREDENTIAL, fingerprint)
            .putString(KEY_SHA, sha)
            .putString(KEY_PATH, identity.helperPath)
            .putLong(KEY_SIZE, identity.helperSize ?: -1L)
            .putString(KEY_BUILD, identity.buildId)
            .putString(KEY_BOOT, identity.bootId)
            .putLong(KEY_AT, System.currentTimeMillis())
            .putString(KEY_APP, UltraSdkBuild.APP_VERSION)
            .commit()
    }

    /** 上次成功安装的身份；没有则 null。 */
    fun readPersisted(context: Context): InstalledIdentity? {
        val prefs = context.ultraPreferences(PREFS, Context.MODE_PRIVATE)
        val sha = normalizeSha(prefs.getString(KEY_SHA, null)) ?: return null
        val fingerprint = prefs.getString(KEY_CREDENTIAL, null)
        val currentFingerprint = keyFingerprint(context) ?: return null
        val expectedPath = installedHelperPath(UltraSdkCredentials.read(context))
        if (fingerprint != null && fingerprint != currentFingerprint) return null
        if (fingerprint == null && (expectedPath == null || prefs.getString(KEY_PATH, null) != expectedPath)) return null
        return InstalledIdentity(
            helperSha256 = sha,
            helperPath = prefs.getString(KEY_PATH, null),
            helperSize = prefs.getLong(KEY_SIZE, -1L).takeIf { it >= 0 },
            buildId = prefs.getString(KEY_BUILD, null),
            bootId = prefs.getString(KEY_BOOT, null),
            source = Source.Persisted,
        )
    }

    /** 持久化身份是哪个管理器版本装的（比较「管理器有没有回退」用）。 */
    fun persistedAppVersion(context: Context): String? = context
        .ultraPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_APP, null)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    /** 身份不再可信时（卸载/身份冲突）清掉持久化，下次读回失败会回到 Unknown。 */
    fun clear(context: Context) {
        cache.invalidate()
        context.ultraPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    // --- 随包身份 -------------------------------------------------------------------

    fun bundledHelperSha256(): String? =
        normalizeSha(bridgeIdentity()?.string("helperSha256")) ?: normalizeSha(UltraSdkBuild.HELPER_SHA256)

    /** 随包构建号（`maplesu-ultra-5.0.5-r1`）；未知 → null。 */
    fun bundledBuildId(): String? =
        cleanBuildId(UltraSdkBuild.BUILD_ID) ?: cleanBuildId(bridgeIdentity()?.string("buildId"))

    /** 随包 helper 字节数；未知 → null，仅作随包身份信息。 */
    fun bundledHelperSizeBytes(): Long? = UltraSdkBuild.HELPER_SIZE.takeIf { it > 0 }
        ?: bridgeIdentity()?.int("helperSize")?.toLong()?.takeIf { it > 0 }

    /** helper 内嵌 rootKey 槽的偏移；未知 → null（不做清零，摘要按原样比较）。 */
    fun keySlotOffset(): Long? = UltraSdkBuild.KEY_SLOT_OFFSET.takeIf { it > 0 }
        ?: bridgeIdentity()?.int("helperKeySlotOffset")?.toLong()?.takeIf { it > 0 }

    /** 桥侧随包身份（只读，不调用 SDK）。失败/不可用一律 null。 */
    fun bridgeReceipt(): NativeReceipt? = bridgeIdentity()

    private fun bridgeIdentity(): NativeReceipt? {
        if (bridgeIdentityRead) return bridgeIdentity
        synchronized(this) {
            if (!bridgeIdentityRead) {
                bridgeIdentity = runCatching {
                    NativeReceipt.parse(NativeBridge.getSdkBuildIdentity())
                }
                    .onFailure { Log.w(TAG, "sdk build identity unavailable: ${it.javaClass.simpleName}") }
                    .getOrNull()
                    ?.takeIf { it.succeeded }
                bridgeIdentityRead = true
            }
            return bridgeIdentity
        }
    }

    // --- 核对 -----------------------------------------------------------------------

    /**
     * 回读身份 vs 随包身份。只有 [Source.Readback] 且两侧同一坐标轴（摘要或构建号）都
     * 已知、且**相同**才通过；未知一律不通过（[reason] 说明是哪一侧缺）。
     *
     * 摘要优先：构建号是摘要的映射结果，磁盘上没有构建号。两侧都没有摘要时才退回构建号
     * 比较（仅当桥/清单给出构建号且回读能反查到同一构建号）。
     */
    fun verifyAgainstBundled(
        identity: InstalledIdentity,
        bundledSha: String? = bundledHelperSha256(),
        bundledBuildId: String? = bundledBuildId(),
    ): IdentityVerification {
        val observedSha = normalizeSha(identity.helperSha256)
        val expectedSha = normalizeSha(bundledSha)
        val observedBuild = cleanBuildId(identity.buildId)
        val expectedBuild = cleanBuildId(bundledBuildId)
        if (identity.source != Source.Readback) {
            val reason = if (identity.source == Source.Persisted) "IDENTITY_PERSISTED_ONLY" else "IDENTITY_UNREADABLE"
            return IdentityVerification(false, reason, observedBuild)
        }
        if (expectedSha != null) {
            return when {
                observedSha == null -> IdentityVerification(false, "IDENTITY_SHA_ABSENT", observedBuild)
                observedSha == expectedSha -> IdentityVerification.passed(observedBuild ?: expectedBuild)
                else -> IdentityVerification(false, "HELPER_SHA_MISMATCH", observedBuild)
            }
        }
        if (expectedBuild != null && observedBuild != null && observedBuild == expectedBuild) {
            return IdentityVerification.passed(observedBuild)
        }
        return IdentityVerification(false, "BUNDLED_IDENTITY_UNKNOWN", observedBuild)
    }

    // --- 纯函数（主机可测） ---------------------------------------------------------

    /** Matches the SDK's get_kernel_file_dir_path; never enumerate its hidden directory. */
    fun installedHelperPath(rootKey: String?): String? = rootKey
        ?.takeIf { it.matches(Regex("[A-Za-z0-9]{48}")) }
        ?.let { "/data/${it.take(16)}/init" }

    /** A prefix digest or a persisted/legacy shell receipt is not a completed native read. */
    fun parseReadback(receipt: NativeReceipt?, expectedPath: String, slotOffset: Long?): Readback? =
        parseReadback(receipt, expectedPath, slotOffset, null)

    internal fun parseOwnerReadback(receipt: NativeReceipt?, slotOffset: Long?, fullUid: Int): Readback? =
        parseReadback(receipt, null, slotOffset, fullUid)

    private fun parseReadback(receipt: NativeReceipt?, expectedPath: String?, slotOffset: Long?, fullUid: Int?): Readback? {
        if (receipt == null || !receipt.succeeded || expectedPath != null && !HELPER_PATH.matches(expectedPath)) return null
        val json = runCatching { JSONObject(receipt.raw) }.getOrNull() ?: return null
        fun integer(objectValue: JSONObject, field: String): Long? = when (val value = objectValue.opt(field)) {
            is Int -> value.toLong()
            is Long -> value
            else -> null
        }
        if (integer(json, "schemaVersion") != 1L || integer(json, "bridgeVersion") != 1L ||
            json.opt("backend") as? String != UltraRuntimeClient.BACKEND ||
            json.opt("operation") as? String != "read_installed_helper" ||
            json.opt("ok") as? Boolean != true || json.opt("sdkError") as? String != "OK" ||
            integer(json, "sdkErrorCode") != 0L) return null
        val data = json.opt("data") as? JSONObject ?: return null
        val sha = data.opt("sha256") as? String ?: return null
        val path = data.opt("path") as? String ?: return null
        if (!HELPER_PATH.matches(path)) return null
        if (expectedPath == null) {
            val deployment = data.opt("deployment") as? JSONObject ?: return null
            val epoch = integer(data, "ownerEpoch") ?: return null
            val hostLow = data.opt("ownerHostLow") as? String ?: return null
            val hostHigh = data.opt("ownerHostHigh") as? String ?: return null
            if (data.opt("ownerMode") as? Boolean != true || data.opt("namespaceRequested") as? Boolean != true ||
                data.opt("namespaceResolved") as? Boolean != true ||
                fullUid == null || fullUid < 0 || integer(data, "ownerFullUid") != fullUid.toLong() ||
                epoch !in 1L..0xffff_ffffL ||
                !hostLow.matches(Regex("[0-9a-f]{16}")) || !hostHigh.matches(Regex("[0-9a-f]{16}")) ||
                hostLow.all { it == '0' } && hostHigh.all { it == '0' } ||
                deployment.opt("attempted") as? Boolean != true || deployment.opt("replyObserved") as? Boolean != true ||
                deployment.opt("completed") as? Boolean != true || deployment.opt("error") as? String != "0" ||
                integer(deployment, "command") != 27L || integer(deployment, "status") != 0L ||
                integer(deployment, "effects") != 0L || deployment.opt("result") as? String != "0000000000000018" ||
                deployment.opt("handle") as? String != "0000000000000000") return null
        }
        val size = integer(data, "size") ?: return null
        val offset = slotOffset?.takeIf { it > 0 } ?: 0L
        if (integer(data, "abiVersion") != 1L || data.opt("complete") as? Boolean != true ||
            data.opt("rootVerified") as? Boolean != true || expectedPath != null && path != expectedPath ||
            !SHA256.matches(sha) || size !in 4096L..9_007_199_254_740_991L ||
            integer(data, "bytesRead") != size || integer(data, "normalizedOffset") != offset ||
            offset > size || 4096L > size - offset) return null
        return Readback(sha, path, size)
    }

    /**
     * 已装 helper 摘要 → 构建号。
     *
     * **只认上次安装时记下的身份**：helper 字节在不同构建之间可能完全相同（本次 5.0.5 的
     * m052 payload 就是逐字节继承的），所以「摘要对得上随包 helper」不能反推出「设备上是
     * 这一版」；构建号只取当初写下的持久化记录。没有记录 → null；升级策略仍可按摘要比较。
     */
    fun buildIdFor(sha256: String?, persisted: InstalledIdentity?): String? {
        val sha = normalizeSha(sha256) ?: return null
        val persistedSha = normalizeSha(persisted?.helperSha256)
        return if (persistedSha != null && persistedSha == sha) cleanBuildId(persisted?.buildId) else null
    }

    /** 64 位小写十六进制摘要；`"unknown"`、空白、大写混写之外的脏值一律 null。 */
    fun normalizeSha(value: String?): String? {
        val text = value?.trim()?.lowercase() ?: return null
        return text.takeIf { SHA256.matches(it) }
    }

    private fun cleanBuildId(value: String?): String? = value
        ?.trim()
        ?.takeIf { it.isNotEmpty() && !it.equals("unknown", ignoreCase = true) }
}
