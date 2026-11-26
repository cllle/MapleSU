package com.maplesu.core.ui.util

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Parcelable
import android.provider.OpenableColumns
import android.system.Os
import android.util.Log
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import com.maplesu.core.BuildConfig
import com.maplesu.core.Natives
import com.maplesu.core.R
import com.maplesu.core.core.tasks.ExtractImage
import com.maplesu.core.core.tasks.ProbeResult
import com.maplesu.core.core.utils.DataSourceChannel
import com.maplesu.core.ksuApp
import com.maplesu.core.ui.security.Stealth
import dev.maplesu.manager.installer.InstallPatchPath
import dev.maplesu.manager.installer.InstallSourceKind
import dev.maplesu.manager.installer.InstallTargetDecision
import dev.maplesu.manager.installer.InstallTargetDispatch
import dev.maplesu.manager.installer.InstallTargetMode
import dev.maplesu.manager.installer.InstallTargetRequest
import dev.maplesu.manager.installer.InstallCandidateImage
import dev.maplesu.manager.installer.InstallCommitOutcome
import dev.maplesu.manager.installer.InstallerServiceClient
import dev.maplesu.manager.installer.ImageRestorationKind
import dev.maplesu.manager.installer.StockInstallFlow
import dev.maplesu.manager.installer.UltraDirectEnvironmentUpgrade
import dev.maplesu.manager.installer.UltraDirectInstallPolicy
import dev.maplesu.manager.installer.UltraDirectInstallRequest
import dev.maplesu.manager.installer.installerTaskResult
import dev.maplesu.manager.workmode.ModeRuntime
import dev.maplesu.manager.workmode.service.ModeSwitchRuntime
import dev.maplesu.manager.workmode.service.ModeSwitchRootSession
import dev.maplesu.manager.workmode.service.SwitchOutcome
import dev.maplesu.manager.workmode.ui.ModeSwitchStep
import dev.maplesu.manager.workmode.ui.WorkModeStrings
import dev.maplesu.manager.workmode.ui.modeSwitchStepOf
import dev.maplesu.manager.installer.installTargetBanner
import dev.maplesu.manager.installer.installTargetBlockerMessage
import me.weishu.kernelsu.data.registry.DownloadVerificationException
import me.weishu.kernelsu.data.registry.RegistryDownloadStore
import me.weishu.kernelsu.data.registry.copyForInstall
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * @author weishu
 * @date 2023/1/1.
 */
private const val TAG = "KsuCli"

/**
 * 「上一次没建出 root shell」之后,隔多久才允许再建一次(毫秒)。
 *
 * D2(v2.2):自愈要和费电之间取个平衡 ——
 *   · 太短:真·没 root 的设备上,每次 `getRootShell()` 都起 2~3 个进程,白白费电;
 *   · 太长:用户刚授完 root 权限,界面要等很久才会"活过来"。
 * 3 秒是"用户点一下重试就够用"和"不会起进程风暴"之间的折中。
 * 一直失败就按 [SHELL_REBUILD_MAX_BACKOFF_MS] 翻倍退避。
 */
private const val SHELL_REBUILD_MIN_BACKOFF_MS = 3000L

/** 重建退避的上限(毫秒)。到顶之后最坏也只是"每分钟试一次"。 */
private const val SHELL_REBUILD_MAX_BACKOFF_MS = 60_000L

// ══════════════════════════════════════════════════════════════════════════
// 🟢 v2.16 删除：我们自创的「自己跑 `id -u` 探测 root」那套东西
//    （`SHELL_PROBE_TIMEOUT_MS` + `shellProbeExecutor` 单线程池 + `probeShellUid`）。
//
// 为什么删：**libsu 在 `Shell.Builder.build()` 里已经做过一模一样的事**，
// 而且是同步的。libsu 6.0.0 `ShellImpl` 的构造函数会：
//   ① 把 `shellCheck()` 提交到线程池，然后 **`future.get(timeout, SECONDS)` 阻塞等它**；
//   ② `shellCheck()` 先写 `echo SHELL_TEST\n` 确认对面是 shell（否则抛
//      `IOException("Created process is not a shell")`）；
//   ③ 再写 `id\n`，输出含 `uid=0` 才把 `status` 置成 `ROOT_SHELL`(=1)；
//   ④ 进程已经死了会直接抛 `IOException("Created process has terminated")`。
//   → 所以 `build()` 返回时 `shell.isRoot`（= `getStatus() >= 1`）**已经写好且可信**；
//     我们那套 1500ms 探针既重复、又引入了一个"超时就算没 root"的额外误判面。
//
// 本项目自己的 `rootAvailable()`（本文件下面）用的也一直是 `shell.isRoot` ——
// 也就是说 v2.15 那套探针和项目里既有的判据**自相矛盾**。v2.16 统一成 libsu 的结论。
// 证据（本机反汇编 libsu 6.0.0 core.aar 的 classes.jar）：
//   `javap -c com.topjohnwu.superuser.internal.ShellImpl` → 构造函数里
//   `FutureTask.get(JLjava/util/concurrent/TimeUnit;)` 后紧接
//   `putfield status:I`；`shellCheck()` 里 `String id\n` → `String uid=0`。
// ══════════════════════════════════════════════════════════════════════════

/**
 * 当前模式下 `ksud <subcommand>` 的可执行路径。
 *
 * - **Ultra（LITE/未判定）**：行为逐字节不变 —— `<nativeLibraryDir>/libksud.so`；
 * - **Normal（KSU）**：部署后的 `/data/adb/ksud`（`KsuDaemonPolicy.DEPLOYED_KSUD`）。
 *   以前这里恒返回 `libksud.so`，而 APK 里**没有**那个文件（它是 Ultra 的运行时），
 *   于是 Normal 下每条 `execKsud`（sulog/adb_root/feature save…）都必然失败。
 *
 * 部署路径不在本进程 `stat`：`/data/adb` 是 0700 root，app uid 读不到；
 * 内核 rc 的形态就是 `/data/adb/ksud`，直接用（失败会以真实退出码显式暴露）。
 */
internal fun getKsuDaemonPath(): String =
    if (dev.maplesu.manager.workmode.ModeRuntime.active == dev.maplesu.manager.workmode.WorkMode.KSU) {
        KsuDaemonPolicy.ksuDaemonPath()
    } else {
        ksuApp.applicationInfo.nativeLibraryDir + File.separator + "libksud.so"
    }

private fun String.shellQuote(): String = "'" + replace("'", "'\\''") + "'"

/**
 * 「这一次安装到底有没有写进分区」——S6 结果态（47 报告 §S6）。
 *
 * [FlashResult.code] 只回答"成功/失败"。2026-11-01 之前「真的写进 boot」与「只把候选镜像
 * 存到 Download」都是 `code=0`，页面无法区分，用户会以为已经刷进去了。
 * 这里把两者拆开：`WRITTEN` 才是写盘成功，`SAVED_ONLY` 必须显式说明"没写"。
 */
enum class FlashOutcome {
    /** SDK environment installed and read back; no boot partition was written. */
    ENVIRONMENT_UPDATED,

    /** 分区已写入并回读校验通过，等重启。 */
    WRITTEN,

    /** 只生成了候选镜像（无 root / 无事务 / 内核不匹配），**没有**写盘。 */
    SAVED_ONLY,

    /** 镜像本来就带着这套补丁，无需重复写。 */
    ALREADY_PATCHED,

    /** 写失败但已自动还原原始镜像。 */
    RESTORED,

    /** 写失败且还原也失败：需要救援。 */
    RECOVERY_REQUIRED,

    /** No transaction has started; wait for original images. */
    IMAGES_REQUIRED,

    /** 没进入写入：识别/构建/前置读取失败。 */
    FAILED,
}

/**
 * S5 的三个**粗粒度**阶段。
 *
 * 由 [installBoot] 通过 `onStage` 上报，页面只渲染、不解析自由文本日志
 * （47 报告 R5：修补阶段以前只有中文 `onStdout`，不可判定、不可本地化）。
 */
enum class InstallStage {
    UPGRADING_ENVIRONMENT,

    /** 识别 release、选变体、构建候选、全部摘要校验。 */
    PATCHING,

    /** 事务 prepare → commit → 回读。 */
    WRITING,

    /** 写盘成功，等用户重启。 */
    AWAITING_REBOOT,
}

data class FlashResult(
    val code: Int,
    val err: String,
    val showReboot: Boolean,
    /**
     * 结果态；`null` = 该调用点没有上报（模块安装、还原等旧路径），页面按 [code] 兜底。
     * 只有 [FlashOutcome.WRITTEN] 允许显示"已刷入"。
     */
    val outcome: FlashOutcome? = null,
    val outputPath: String? = null,
    /** The exact candidate used by this installation, not an older workspace image. */
    val bootCandidate: InstallCandidateImage? = null,
    val imagesRequired: SwitchOutcome.ImagesRequired? = null,
    /** Module receipts remain authoritative when an answer is pending, regardless of legacy [code]. */
    val moduleOutcome: dev.maplesu.manager.runtime.ultra.UltraOutcome? = null,
    /** False only when this installation has not called prepare; null retains unknown/other paths. */
    val installationSubmitted: Boolean? = null,
) {
    constructor(result: Shell.Result, showReboot: Boolean) : this(result.code, result.err.joinToString("\n"), showReboot)
    constructor(result: Shell.Result) : this(result, result.isSuccess)
}

/**
 * ⚠️ 2026-09-19 修:**这里绝对不能在"建对象"的时候去建 shell**。
 *
 * 原来写的是 `val SHELL: Shell = createRootShell()` —— 也就是把"起 root shell"放进了
 * `object KsuCli` 的**类初始化**里。类初始化一旦抛异常(起 shell 失败、libsu 内部
 * `Runtime.exec` 失败抛 `NoShellException`……),JVM 会把 `KsuCli` 这个类永久标记成
 * `Erroneous`:**之后主线程上任何一处 `KsuCli.xxx` / `getRootShell()` 都会抛
 * `NoClassDefFoundError` → 打开即闪退**,而且栈里看不到真正的原因(第一次的异常)。
 *
 * 现在改成:
 *  1. 类初始化只放几个 `@Volatile` 变量,**不碰任何可能失败的东西** → 初始化不可能失败;
 *  2. 真正的 shell 在**第一次被用到时**才建,并且整段包 `runCatching`(见 [safeRootShell]);
 *  3. 建失败**不会**被永久缓存,下次访问会重建 —— 一次失败不永久毒化(🔴 D2,v2.2);
 *  4. 实在建不出来时返回 [DeadShell] 占位:跑命令只会失败,**但不抛异常**,
 *     结果就是"这个 App 没有 root 能力",而不是崩。
 *
 * 对外行为不变:`SHELL` / `GLOBAL_MNT_SHELL` 仍然是 `Shell`(非空),调用方零改动。
 */
object KsuCli {
    /**
     * 缓存下来的建 shell 结果。`null` = 还没建过。
     *
     * 🔴 D2(v2.2 修,粉丝实测故障):这里以前是
     * `val SHELL: Shell by lazy { safeRootShell(false) }` —— `lazy` 会把**第一次**的结果
     * 永久钉死,包括 [DeadShell] 和"没拿到 root 的兜底 sh"。
     * 于是"App 刚启动那一瞬间内核/ksud 还没就绪"这一次失败,会让**整个进程**此后
     * 一直报「获取 Root 失败」—— 用户只能靠重启 App 才好,而他根本不知道为什么。
     *
     * 现在的规则:
     *   · 缓存里那个**已验证是 root**且进程还活着 → 直接用(正常路径,零额外开销);
     *   · 其它情况([DeadShell] / 没验到 root / shell 已死)→ **允许下次重建**,自愈;
     *   · 重建**限速 + 退避**（[SHELL_REBUILD_MIN_BACKOFF_MS] 起，翻倍到
     *     [SHELL_REBUILD_MAX_BACKOFF_MS]），否则"真的没 root"的设备上
     *     每次访问都要起 2~3 个进程 → 白白费电(本项目刚为费电问题做过专项优化)。
     */
    @Volatile
    private var cachedShell: Shell? = null

    @Volatile
    private var cachedGlobalMntShell: Shell? = null

    /** 上一次"重建尝试"的时间(`elapsedRealtime`),用于限速 */
    @Volatile
    private var lastRebuildAt: Long = 0L

    /** 当前的退避间隔（毫秒）。拿到 root 时复位成最小值，一直拿不到就翻倍到上限。 */
    @Volatile
    private var rebuildBackoffMs: Long = SHELL_REBUILD_MIN_BACKOFF_MS

    val SHELL: Shell get() = obtain(globalMnt = false)

    val GLOBAL_MNT_SHELL: Shell get() = obtain(globalMnt = true)

    /** An independently authenticated backend can immediately replace earlier unavailable shells. */
    @Synchronized
    fun onRuntimeReady() {
        if (cachedShell?.isRoot != true) cachedShell = null
        if (cachedGlobalMntShell?.isRoot != true) cachedGlobalMntShell = null
        lastRebuildAt = 0L
        rebuildBackoffMs = SHELL_REBUILD_MIN_BACKOFF_MS
        install()
        requestRuntimeModulesOnce()
    }

    /**
     * **快路径不加锁**：`getRootShell()` 是极热的调用点，
     * 每次访问都抢一把可能被"起 shell"（最长几秒）held 住的锁是不可接受的。
     * 正常情况下这里只做两次 `@Volatile` 读 + 一次 `isAlive()`。
     *
     * 🟢 v2.16：缓存判据从我们自创的 `ShellCreation.rootVerified` 换回
     * **libsu 自己的 `Shell.isRoot`**（理由见文件顶部那段"删除自创探针"的注释）。
     * `DeadShell.isRoot` 也是 false（它的 `getStatus()` 返回 `Shell.UNKNOWN`），
     * 所以占位 shell 同样不会被缓存。
     */
    private fun obtain(globalMnt: Boolean): Shell {
        // 🛑 Ultra 环境：不建任何 root shell。`MapleSuCommandExecutor` 启动的
        // `libmaplesu_runtime.so` 在 MapleSU Ultra 内核上会触发系统重启（2026-10-29 实测：
        // 设置页「配对 key」= `MapleSuCredentialStore.pair` → openShell → 系统重启）。
        // 这里返回占位 shell：所有 `KsuCli` 命令（launchApp / forceStop / 诊断…）只会
        // 失败返回，不抛异常、不起进程；既有的"未授权"分支也就自然成立。
        // 判据是 [dev.maplesu.manager.runtime.ultra.UltraEnvironmentProbe] 的缓存，不做 JNI。
        if (runCatching { dev.maplesu.manager.runtime.ultra.UltraEnvironmentProbe.usesUltraBackend(ksuApp) }
                .getOrDefault(false)) {
            return DeadShell
        }
        val cached = if (globalMnt) cachedGlobalMntShell else cachedShell
        if (cached != null && cached.isRoot && cached.isAlive()) return cached
        return obtainSlow(globalMnt)
    }

    /** 慢路径：需要（重）建 shell 时才进来，加锁串行化。 */
    @Synchronized
    private fun obtainSlow(globalMnt: Boolean): Shell {
        if (runCatching { dev.maplesu.manager.runtime.ultra.UltraEnvironmentProbe.usesUltraBackend(ksuApp) }
                .getOrDefault(false)) return DeadShell
        // 双检：等锁的期间可能已经有别的线程建好了
        val cached = if (globalMnt) cachedGlobalMntShell else cachedShell
        if (cached != null && cached.isRoot && cached.isAlive()) return cached

        // ② 缓存不可用。要么还没建过(第一次),要么上一次没拿到 root。
        //    限速:冷却期内先把手头这个(用不了 root 的)shell 交出去,
        //    不重复起进程;过了冷却期就重建 —— 这就是"不用重启 App 也能自己好"。
        //    ⚠️ 只对"上一次就没拿到 root"退避；"验过 root 但进程死了"是异常情况，
        //      那种要**立刻重建**，不能被退避拖住。
        val now = android.os.SystemClock.elapsedRealtime()
        if (cached != null && !cached.isRoot && now - lastRebuildAt < rebuildBackoffMs) {
            return cached
        }
        lastRebuildAt = now

        val fresh = safeRootShell(globalMnt)
        if (globalMnt) cachedGlobalMntShell = fresh else cachedShell = fresh

        // 退避：拿到 root 就复位；还是没 root 就翻倍（上限 60 秒）。
        // 为什么需要退避：真·没 root 的设备上，每次重建要起 2~3 个进程；
        // 如果固定 3 秒一次，用户一直翻界面 = 一直在起进程，白费电。
        // 翻倍之后最坏也只是"每分钟试一次"，而拿到 root 的那一刻会立刻复位成 3 秒。
        rebuildBackoffMs = if (fresh.isRoot) {
            SHELL_REBUILD_MIN_BACKOFF_MS
        } else {
            (rebuildBackoffMs * 2).coerceAtMost(SHELL_REBUILD_MAX_BACKOFF_MS)
        }
        return fresh
    }
}

/**
 * 建 shell 的**唯一安全入口**:任何 `Throwable` 都吞掉,最坏给一个 [DeadShell] 占位。
 * 调用方(可能是主线程)永远不会因为它拿到异常。
 */
private fun safeRootShell(globalMnt: Boolean): Shell =
    runCatching { createRootShellDetailed(globalMnt) }.getOrElse { e ->
        Log.e(TAG, "创建 root shell 失败,退回占位 shell(本次运行没有 root 能力): ", e)
        DeadShell
    }

/**
 * 「什么都做不了」的占位 shell。
 *
 * 存在的唯一理由:连 `sh` 都起不来的时候,`getRootShell()` 仍然必须返回一个**非空**的
 * `Shell`(几十个调用方直接 `shell.newJob()...`,不能改成可空)。它身上跑任何命令都只会
 * 拿到 code = -1 的空结果 —— 也就是"没有 root 能力",而不是抛异常闪退。
 */
private object DeadShell : Shell() {
    override fun isAlive(): Boolean = false
    override fun getStatus(): Int = Shell.UNKNOWN
    override fun execTask(task: Shell.Task) {
        throw IOException("dead shell: no shell available")
    }

    override fun submitTask(task: Shell.Task) = Unit
    override fun newJob(): Shell.Job = DeadJob
    override fun waitAndClose(timeout: Long, unit: TimeUnit): Boolean = true
    override fun close() = Unit
}

/** [DeadShell] 的 Job:所有命令"立刻失败",不碰进程、不抛异常。 */
private object DeadJob : Shell.Job() {
    override fun to(dest: MutableList<String>?): Shell.Job = this
    override fun to(out: MutableList<String>?, err: MutableList<String>?): Shell.Job = this
    override fun add(vararg commands: String): Shell.Job = this
    override fun add(input: InputStream): Shell.Job = this
    override fun exec(): Shell.Result = DeadResult
    override fun submit(executor: Executor?, callback: Shell.ResultCallback?) {
        callback?.onResult(DeadResult)
    }

    override fun enqueue(): Future<Shell.Result> =
        CompletableFuture.completedFuture(DeadResult as Shell.Result)
}

/** [DeadShell] 的 Result:空输出 + code = -1,`isSuccess` 因此恒为 false。 */
private object DeadResult : Shell.Result() {
    override fun getOut(): MutableList<String> = ArrayList()
    override fun getErr(): MutableList<String> = arrayListOf(ksuApp.getString(R.string.root_install_required))
    override fun getCode(): Int = -1
}

fun getRootShell(globalMnt: Boolean = false): Shell {
    return if (globalMnt) KsuCli.GLOBAL_MNT_SHELL else {
        KsuCli.SHELL
    }
}

inline fun <T> withNewRootShell(
    globalMnt: Boolean = false,
    block: Shell.() -> T
): T {
    return createRootShell(globalMnt).use(block)
}

fun Uri.getFileName(context: Context): String? {
    var fileName: String? = null
    val contentResolver: ContentResolver = context.contentResolver
    val cursor: Cursor? = contentResolver.query(this, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            fileName = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
        }
    }
    return fileName
}

/** Opens a newly authenticated MapleSU management shell. */
fun createRootShell(globalMnt: Boolean = false): Shell = createRootShellDetailed(globalMnt)

private fun createRootShellDetailed(globalMnt: Boolean): Shell {
    return dev.maplesu.manager.runtime.RootManagement.openShell(ksuApp, globalMnt)
}

fun execKsud(args: String, newShell: Boolean = false, globalMnt: Boolean = false): Boolean {
    return if (newShell) {
        withNewRootShell(globalMnt = globalMnt) {
            ShellUtils.fastCmdResult(this, "${getKsuDaemonPath()} $args")
        }
    } else {
        ShellUtils.fastCmdResult(getRootShell(globalMnt), "${getKsuDaemonPath()} $args")
    }
}

suspend fun getFeatureStatus(feature: String): String = withContext(Dispatchers.IO) {
    // ⚠️ v2.14：整段包 runCatching —— 这个探测以前裸调 `.exec()`，
    //    任何异常（shell 已死 / 进程起不来 / 上面那行命令被信号打断）都会沿协程往上抛。
    //    调用方是设置页与首页的多个 produceState，抛出去就是"打开设置/首页闪退"。
    //    现在最坏只是返回 "unknown"，绝不让主进程受影响。
    val result = runCatching {
        getRootShell().newJob()
            .add("${getKsuDaemonPath()} feature check $feature").to(ArrayList<String>(), null).exec()
    }.onFailure { Log.w(TAG, "feature check $feature 探测失败（按 unknown 处理）", it) }.getOrNull()

    // ⚠️ 部分 ROM 的 seccomp 会在 ksud 探测内核那一刻把**子进程**用 SIGSYS 杀掉
    //    （机制见 ksud 侧 userspace/ksud/src/ksucalls.rs 顶部注释；v2.14 起 ksud 自己
    //    已经改成在独立子进程里探测、父进程不会被杀）。这里再兜一层：退出码
    //    128 + 31(SIGSYS) = 159 且没有任何输出时，按"不支持"处理，而不是抛异常。
    val sigsysKilled = result?.code == 128 + 31

    // ⚠️ v2.1(审计 P0-2)保留的规则:以前直接取第一行,读不到时返回空串,界面就落到 `else`
    //    分支显示成"这个特性正常可用" —— 一句没根据的假话。
    //    只认 ksud 真正吐出来的三种结论,其余(空输出/报错/意料之外的行)一律
    //    "unknown",界面明确显示"读不到内核状态"。
    result?.out?.map { it.trim() }
        ?.firstOrNull { it == "supported" || it == "managed" || it == "unsupported" }
        ?: if (sigsysKilled) "unsupported" else "unknown"
}

suspend fun getFeaturePersistValue(feature: String): Long? = readFeatureValue("--config $feature")

// 内核里的当前生效值（区别于 --config 读出来的持久化值）
suspend fun getFeatureValue(feature: String): Long? = readFeatureValue(feature)

private suspend fun readFeatureValue(args: String): Long? = withContext(Dispatchers.IO) {
    val shell = getRootShell()
    val out = shell.newJob()
        .add("${getKsuDaemonPath()} feature get $args").to(ArrayList<String>(), null).exec().out
    val valueLine = out.firstOrNull { it.trim().startsWith("Value:") } ?: return@withContext null
    valueLine.substringAfter("Value:").trim().toLongOrNull()
}

private val runtimeModuleRequested = java.util.concurrent.atomic.AtomicBoolean(false)

private fun requestRuntimeModulesOnce() {
    // 模式隔离：Lite 的模块 boot 服务只属于 Ultra。Normal 下模块由 ksud 在内核侧挂载，
    // 起 Lite 前台服务既不生效、又会让两套模块系统互相看见（审计 §D0）。
    if (dev.maplesu.manager.workmode.ModeRuntime.active == dev.maplesu.manager.workmode.WorkMode.KSU) {
        Log.i(TAG, "lite module boot service not requested: active mode is KSU")
        return
    }
    if (runtimeModuleRequested.compareAndSet(false, true)) {
        runCatching { dev.maplesu.manager.modules.LiteModuleBootService.request(ksuApp) }
            .onFailure { runtimeModuleRequested.set(false) }
    }
}

/**
 * Ultra 运行时部署入口（**只属于 LITE/未判定**）。
 *
 * Normal（KSU）下用户态由 `KsuBootstrap` 部署 `/data/adb/ksud`，与 Ultra 的
 * `libmaplesu_runtime` 完全是两套东西；在 KSU 里跑 Ultra 部署属于跨模式污染。
 */
fun install() {
    if (dev.maplesu.manager.workmode.ModeRuntime.active == dev.maplesu.manager.workmode.WorkMode.KSU) {
        Log.i(TAG, "ultra runtime deployment skipped: active mode is KSU")
        return
    }
    dev.maplesu.manager.runtime.RuntimeDeployment.start(ksuApp) { requestRuntimeModulesOnce() }
}

/**
 * 模块列表（模块页/WebUI 的唯一同步入口）。
 *
 * 模式分派是**严格**的：
 * - Normal（KSU）→ `ksud module list` 的映射结果（`KsuBackendRegistry.moduleListJson()`）。
 *   未接线或读取失败时向调用方传播错误；合法空列表才返回 `[]`，不回落到 Lite 的列表；
 * - Ultra（LITE/未判定）→ 合并 Lite 脚本后端与 Ultra 后端的已安装模块。
 */
fun listModules(): String {
    val mode = dev.maplesu.manager.workmode.ModeRuntime.active
    if (mode == dev.maplesu.manager.workmode.WorkMode.KSU) {
        return dev.maplesu.manager.workmode.backend.KsuBackendRegistry.moduleListJson()
            ?: error("KSU_MODULE_BACKEND_UNAVAILABLE")
    }
    // Hook owns its script tree without an SDK identity. A verified SDK owner
    // retains that identity in safe mode, and its list failure must remain visible.
    val ultraItems = if (dev.maplesu.manager.runtime.ultra.UltraEnvironmentProbe.usesUltraBackend(ksuApp)) {
        dev.maplesu.manager.modules.MapleSuModuleBackend(ksuApp).listItems(required = true)
    } else null
    val merged = JSONArray()
    // Reuse the SDK list already read above. A stored ZIP/name does not establish a provider,
    // and provider availability does not assert that a particular module's hooks succeeded.
    val zygiskAvailable = ultraItems.orEmpty().any {
        it.id32 == dev.maplesu.manager.modules.LiteScriptModuleBackend.ZYGISK_HOST_ID &&
            it.runState == dev.maplesu.manager.ultra.model.MapleSuModRunState.RUNNING
    }
    val lite = JSONArray(dev.maplesu.manager.modules.LiteScriptModuleBackend.get(ksuApp).listJson(zygiskAvailable))
    for (index in 0 until lite.length()) {
        merged.put(lite.getJSONObject(index))
    }
    ultraItems?.forEach { item ->
        // 「打开 WebUI」按钮是否出现只取决于这一行的 web_ui 与环境状态，先把判据记下来，
        // 免得现场只能靠猜。
        Log.i(
            TAG,
            "ultra module row: id=${item.id32} web_ui=${item.isWebUi} state=${item.runState.name}",
        )
        merged.put(JSONObject().apply {
            put("id", "maplesu:${item.id32}")
            put("name", item.name.ifEmpty { item.id32 })
            put("version", item.ver)
            put("author", item.author)
            put("description", item.desc)
            put("enabled", item.enabled ?: (item.runState == dev.maplesu.manager.ultra.model.MapleSuModRunState.RUNNING))
            put("update", false)
            put("updateJson", item.updateJson)
            put("remove", item.runState == dev.maplesu.manager.ultra.model.MapleSuModRunState.REMOVED_PENDING_REBOOT)
            put("web", item.isWebUi)
            put("action", false)
            put("metamodule", false)
            put("runtimeStatus", item.runState.name.lowercase())
            put("canToggle", item.isEnableToggleSupported &&
                dev.maplesu.manager.workmode.ModeRuntime.active == dev.maplesu.manager.workmode.WorkMode.LITE)
            put("stages", JSONObject())
            put("props", JSONObject().apply {
                put("applied", JSONArray())
                put("failed", JSONArray())
            })
            put("needsReboot", item.isNeedsReboot)
            put("unsupported", JSONArray())
            put("builtin", false)
            put("backend", "maplesu_ultra")
            put("ultraState", item.runState.name)
            put("minSdkVer", item.miniSdk)
        })
    }
    return merged.toString()
}

fun getModuleCount(): Int = JSONArray(listModules()).length()

fun getSuperuserCount(): Int {
    // 🟠（v2.19）自己兜底（与同文件 getFeatureStatus 一致的写法）：
    // 这是 navigation badge 的**启动期**调用，裸调 JNI 一旦抛异常就会沿协程冒到界面
    // ⇒ "打开 App 就崩"。读不到按 0（角标不显示）处理，绝不外抛。
    return runCatching { Natives.getSuperuserCount() }.getOrDefault(0)
}

fun toggleModule(id: String, enable: Boolean): Boolean {
    if (id.startsWith("maplesu:")) {
        val receipt = dev.maplesu.manager.modules.MapleSuModuleBackend(ksuApp)
            .setEnabled(id.removePrefix("maplesu:"), enable)
        return receipt.outcome in setOf(
            dev.maplesu.manager.runtime.ultra.UltraOutcome.COMPLETED,
            dev.maplesu.manager.runtime.ultra.UltraOutcome.PENDING_REBOOT,
        )
    }
    return runCatching {
        dev.maplesu.manager.modules.LiteScriptModuleBackend.get(ksuApp).setEnabled(id, enable)
    }.onFailure { Log.w(TAG, "Module state change failed", it) }.getOrDefault(false)
}

fun undoUninstallModule(id: String): Boolean {
    if (id.startsWith("maplesu:")) return false
    return runCatching {
        dev.maplesu.manager.modules.LiteScriptModuleBackend.get(ksuApp).markRemove(id, false)
    }.onFailure { Log.w(TAG, "Module removal cancellation failed", it) }.getOrDefault(false)
}

fun uninstallModule(id: String): Boolean {
    if (id.startsWith("maplesu:")) {
        val id32 = id.removePrefix("maplesu:")
        return runCatching {
            val receipt = dev.maplesu.manager.modules.MapleSuModuleBackend(ksuApp).uninstall(id32)
            receipt.outcome == dev.maplesu.manager.runtime.ultra.UltraOutcome.COMPLETED ||
                receipt.outcome == dev.maplesu.manager.runtime.ultra.UltraOutcome.PENDING_REBOOT
        }.onFailure { Log.w(TAG, "Ultra module removal failed", it) }.getOrDefault(false)
    }
    return runCatching {
        dev.maplesu.manager.modules.LiteScriptModuleBackend.get(ksuApp).markRemove(id, true)
    }.onFailure { Log.w(TAG, "Module removal failed", it) }.getOrDefault(false)
}

/** 安装确认回调载荷：archive report（执行用）+ detector evidence（类型 UI/路由用）。 */
data class ModuleConfirmation(
    val report: dev.maplesu.manager.ModuleArchiveInspector.Report,
    val evidence: dev.maplesu.manager.modules.ModuleTypeEvidence,
)

suspend fun flashModule(
    uri: Uri,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit,
    confirmType: (suspend (ModuleConfirmation) -> dev.maplesu.manager.modules.ModuleType?)? = null,
    /**
     * 这次安装来自哪个入口（开发方案 §4.1）。只用于兼容门的回执文案与证据归因，
     * **不参与**命中判定；缺省按普通安装。更新/市场快捷动作在各自的 Route 构造点显式传入。
     * 本仓库前端目前没有「开发单次运行」入口，所以这里仍然没有 devRunOnce 的调用方。
     */
    action: dev.maplesu.manager.modules.compat.UltraModuleAction =
        dev.maplesu.manager.modules.compat.UltraModuleAction.INSTALL,
): FlashResult {
    // Registry 下载的包存在 App 私有目录、自带期望的 size + SHA-256；
    // 交给 Lite 模块后端的那一份在安装前重新校验哈希。
    val storeRoot = File(ksuApp.filesDir, RegistryDownloadStore.DIR_NAME)
    val expected = if (uri.scheme == ContentResolver.SCHEME_FILE) {
        uri.path?.let { RegistryDownloadStore.expectedFor(storeRoot, File(it)) }
    } else {
        null
    }
    val file = File.createTempFile("module-", ".zip", ksuApp.cacheDir)
    try {
        try {
            val input = ksuApp.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
            input.use { copyForInstall(it, file, expected) }
        } catch (e: IOException) {
            Log.w("KernelSU", "module $uri not installed: ${e.message}")
            if (e is DownloadVerificationException) {
                // 下载后被改坏/篡改过：绝不留着等重试。
                uri.path?.let { RegistryDownloadStore.delete(storeRoot, File(it)) }
            }
            val message = "! ${e.message ?: "Failed to read module"}"
            onStderr(message)
            return FlashResult(1, message, false)
        }
        onStdout(ksuApp.getString(R.string.module_archive_checking))
        val report = try {
            if (dev.maplesu.manager.workmode.ModeRuntime.active == dev.maplesu.manager.workmode.WorkMode.KSU) {
                dev.maplesu.manager.ModuleArchiveInspector.inspectKernelSu(file)
            } else {
                dev.maplesu.manager.ModuleArchiveInspector.inspect(file)
            }
        } catch (exception: IOException) {
            Log.w(TAG, "Module archive preflight failed", exception)
            val message = ksuApp.getString(R.string.module_archive_invalid)
            onStderr(message)
            return FlashResult(1, message, false)
        }
        Log.i(TAG, "Module archive checked: id=${report.id}, type=${report.type}, features=${report.features}")
        onStdout(ksuApp.getString(R.string.module_archive_checked))
        // 类型识别与执行解耦：detector 的 evidence 交给类型 UI 决定展示/选择/禁用，
        // 返回值同时决定实际后端（Ultra/KSU）；UNKNOWN/取消 → 不安装。
        val evidence = dev.maplesu.manager.modules.ModuleTypeDetector.detect(report)
        val chosenType = if (confirmType == null) {
            null
        } else {
            confirmType(ModuleConfirmation(report, evidence))
        }
        if (chosenType == null) {
            val message = ksuApp.getString(
                if (confirmType == null) R.string.module_type_confirmation_required
                else R.string.module_install_cancelled,
            )
            onStderr(message)
            return FlashResult(1, message, false)
        }
        try {
            dev.maplesu.manager.ModuleArchiveInspector.verifyUnchanged(file, report)
        } catch (exception: IOException) {
            val message = ksuApp.getString(R.string.module_archive_invalid)
            onStderr(message)
            return FlashResult(1, message, false)
        }
        return try {
            val installAsPro = chosenType == dev.maplesu.manager.modules.ModuleType.MAPLESU
            if (installAsPro) {
                // Ultra modules are installed by the Ultra host from the staged, hash-confirmed
                // bytes. They never enter the KSU script installer.
                val receipt = dev.maplesu.manager.modules.MapleSuModuleBackend(ksuApp)
                    .install(file, report, false, onStdout, onStderr, action)
                val accepted = receipt.outcome == dev.maplesu.manager.runtime.ultra.UltraOutcome.COMPLETED ||
                    receipt.outcome == dev.maplesu.manager.runtime.ultra.UltraOutcome.PENDING_REBOOT
                if (!accepted) {
                    val reason = receipt.reason ?: receipt.sdkError
                    val rolledBack = receipt.outcome == dev.maplesu.manager.runtime.ultra.UltraOutcome.FAILED &&
                        receipt.data?.opt("installCommitted") == false && receipt.data?.opt("cleanupComplete") == true
                    val message = if (rolledBack) "$reason\n${ksuApp.getString(R.string.module_install_failed_rolled_back)}" else reason
                    onStderr(message)
                    FlashResult(1, message, false, moduleOutcome = receipt.outcome)
                } else {
                    onStdout("${report.id}: ${ksuApp.getString(R.string.module_repo_installed)}")
                    if (receipt.needsReboot) onStdout(ksuApp.getString(R.string.reboot_to_apply))
                    FlashResult(0, "", receipt.needsReboot, moduleOutcome = receipt.outcome)
                }
            } else if (dev.maplesu.manager.workmode.ModeRuntime.active ==
                dev.maplesu.manager.workmode.WorkMode.KSU
            ) {
                // Normal（KSU）：交给 ksud 自己的模块安装器（`ksud module install <zip>`）。
                // 以前这里落到 `LiteScriptModuleBackend`（Ultra 的脚本安装流水线），
                // 与 ksud 的安装/权限/update-binary 语义不一致，且在 Normal 下必失败。
                val installed = runInterruptible {
                    dev.maplesu.manager.workmode.backend.KsuBackendRegistry
                        .installModuleResult(file, onStdout, onStderr)
                }
                if (installed == null) {
                    val message = KSU_MODULE_BACKEND_UNAVAILABLE
                    onStderr(message)
                    FlashResult(-1, message, false)
                } else {
                    val message = if (installed.code != 0 && installed.err.isEmpty()) {
                        "$KSU_MODULE_INSTALL_FAILED:${installed.code}".also(onStderr)
                    } else {
                        installed.err.joinToString("\n")
                    }
                    FlashResult(installed.code, message, installed.code == 0 && installed.needsReboot)
                }
            } else {
                val result = dev.maplesu.manager.modules.LiteScriptModuleBackend.get(ksuApp)
                    .install(file, report, onStdout, onStderr)
                FlashResult(result.code, "", result.needsReboot, moduleOutcome =
                    if (result.commitUnknown) dev.maplesu.manager.runtime.ultra.UltraOutcome.NEEDS_RECONCILIATION
                    else null)
            }
        } catch (exception: Exception) {
            if (exception is CancellationException && dev.maplesu.manager.workmode.ModeRuntime.active ==
                dev.maplesu.manager.workmode.WorkMode.KSU) throw exception
            val message = exception.message ?: "Module installation failed."
            onStderr(message)
            FlashResult(1, message, false)
        }
    } finally {
        // 成功/失败/异常都清掉，绝不给下次留半截包
        runCatching { file.delete() }
    }
}

fun runModuleAction(
    moduleId: String, onStdout: (String) -> Unit, onStderr: (String) -> Unit
): Boolean = try {
    if (moduleId.startsWith("maplesu:")) {
        onStderr("该模块不支持独立动作。")
        return false
    }
    when (dev.maplesu.manager.workmode.ModeRuntime.active) {
        // Normal（KSU）：`ksud module action <id>`（**无 `-g`**）+ global-mnt root shell，
        // stdout/stderr/退出码原样透传（上游 `KsuCli.kt:282-285` 形态）。
        // 未接线 → false + 显式错误，**不**回落到 Ultra 的脚本 action
        // （那会在错误的模块树上找 action.sh）。
        dev.maplesu.manager.workmode.WorkMode.KSU ->
            dev.maplesu.manager.workmode.backend.KsuBackendRegistry
                .runModuleAction(moduleId, onStdout, onStderr)
                ?: run {
                    onStderr(KSU_MODULE_BACKEND_UNAVAILABLE)
                    false
                }
        dev.maplesu.manager.workmode.WorkMode.LITE, null ->
            dev.maplesu.manager.modules.LiteScriptModuleBackend.get(ksuApp)
                .action(moduleId, onStdout, onStderr) == 0
    }
} catch (exception: Exception) {
    onStderr(exception.message ?: "Module action failed.")
    false
}

/** Normal 下"KSU 后端未接线"的显式错误（不伪造成功、不回退 Ultra）。 */
private const val KSU_MODULE_BACKEND_UNAVAILABLE =
    "KSU_MODULE_BACKEND_UNAVAILABLE: KSU module backend is not wired"

/** 安装命令无 stderr 时的失败摘要；具体退出码由调用方附加。 */
private const val KSU_MODULE_INSTALL_FAILED =
    "MODULE_INSTALL_FAILED"

fun restoreBoot(
    onStdout: (String) -> Unit, onStderr: (String) -> Unit, installationTaskId: String? = null
): FlashResult {
    val taskId = installationTaskId ?: java.util.UUID.randomUUID().toString()
    fun resultOf(status: android.os.Bundle): FlashResult {
        val state = status.getString("state").orEmpty()
        val result = installerTaskResult(state, status.getBoolean("rebootRequired"), status.getString("error").orEmpty())
        if (result.code == 0 && status.getString("operation") == "RESTORE_STOCK") {
            val exact = ImageRestorationKind.fromWire(status.getString("restorationKind")) == ImageRestorationKind.EXACT_STOCK
            onStdout(ksuApp.getString(if (exact) {
                if (result.showReboot) R.string.restore_boot_reboot else R.string.restore_boot_complete
            } else {
                if (result.showReboot) R.string.remove_owned_reboot else R.string.remove_owned_complete
            }))
            return result.copy(outcome = if (exact) FlashOutcome.RESTORED else FlashOutcome.WRITTEN)
        }
        val message = ksuApp.getString(R.string.restore_boot_failed)
        onStderr(message)
        return result.copy(code = 1, err = message)
    }
    return try {
        onStdout(ksuApp.getString(R.string.restore_boot_started))
        resultOf(InstallerServiceClient.restoreStockImages(ksuApp, taskId))
    } catch (error: Exception) {
        Log.w(TAG, "Boot restore failed", error)
        val observed = runCatching { InstallerServiceClient.installationStatus(ksuApp, taskId) }.getOrNull()
        if (observed != null) return resultOf(observed)
        val message = ksuApp.getString(R.string.restore_boot_failed)
        onStderr(message)
        FlashResult(1, message, false, FlashOutcome.RECOVERY_REQUIRED)
    }
}

fun uninstallPermanently(
    onStdout: (String) -> Unit, onStderr: (String) -> Unit, installationTaskId: String? = null
): FlashResult = restoreBoot(onStdout, onStderr, installationTaskId)

@Parcelize
sealed class LkmSelection : Parcelable {
    @Parcelize
    data class LkmUri(val uri: Uri) : LkmSelection()

    @Parcelize
    data class KmiString(val value: String) : LkmSelection()

    @Parcelize
    data object KmiNone : LkmSelection()
}

// ksud 的 --block-module 解析器接受的单个模块名。
private fun isValidBlockModuleName(name: String): Boolean =
    name.isNotEmpty() && name.length <= 64 &&
        name.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' }

/** 把黑名单输入框里逗号分隔的内容解析成非空模块名列表。 */
fun parseBlockModules(input: String): List<String> =
    input.split(',').map { it.trim() }.filter { it.isNotEmpty() }

// 内核把 block_modules= 的值拷进 256 字节缓冲区（可用 255 字节）；
// 更长的列表会被静默截断、部分模块没被拦。这里同样拒掉。
private const val MAX_BLOCK_MODULES_LEN = 255

/** 黑名单里的每个名字都合法才算合法（空串合法 = 不拦任何模块）。 */
fun isBlockModulesValid(input: String): Boolean {
    val names = parseBlockModules(input)
    return names.all(::isValidBlockModuleName) &&
        names.joinToString(",").length <= MAX_BLOCK_MODULES_LEN
}

fun installBoot(
    bootUri: Uri?,
    lkm: LkmSelection,
    ota: Boolean,
    partition: String?,
    allowShell: Boolean,
    enableAdb: Boolean,
    forceBackup: Boolean,
    blockModules: String,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit,
    installationTaskId: String? = null,
    /**
     * 本次修补的**目标工作模式**（安装页选择，事务开始时快照）。
     *
     * `null` = 兼容旧调用点：按当前已激活模式推导（LITE→Ultra / KSU→Normal / 未判定→Ultra）。
     */
    targetMode: InstallTargetMode? = null,
    /** 本次输入来自下载并提取的目标分区镜像。 */
    downloaded: Boolean = false,
    /**
     * S4 用户在确认页选定的变体 id（`k22` / `k21` / release 目录里的 asset id）。
     *
     * `null` = 没选过（旧路由/旧调用点）→ 沿用目录默认变体 + 既有回退，行为逐字节不变。
     */
    variantId: String? = null,
    /**
     * S5 结构化阶段回调。默认空实现：旧调用点零改动。
     *
     * 页面**只能**通过它显示「修补中 / 写入中 / 待重启」，不允许 grep [onStdout] 的文案。
     */
    onStage: (InstallStage) -> Unit = {},
    existingRootConflictAccepted: Boolean = false,
    restoreOriginalImage: Boolean = false,
    originalManagerPackage: String? = null,
    lenovoSigning: dev.maplesu.manager.installer.LenovoSigningOptions = dev.maplesu.manager.installer.LenovoSigningOptions(),
    vivoInputs: dev.maplesu.manager.installer.VivoCompatibilityInputs = dev.maplesu.manager.installer.VivoCompatibilityInputs(),
    ultraInitBootArtifact: dev.maplesu.image.UltraInitBootProducer.Artifact? = null,
    forceRepatch: Boolean = false,
    pairedBootSnapshot: android.os.Bundle? = null,
    pairedBootUri: Uri? = null,
    pairedSourceSha256: String? = null,
    pairedBootSha256: String? = null,
): FlashResult {
    // ══════════════════════════════════════════════════════════════════════
    // 目标工作模式分派（D-新）。**唯一**分派点是纯函数
    // `dev.maplesu.manager.installer.InstallTargetDispatch.decide`：
    //   Ultra  → Ultra 内核 release 资产/门禁链路（下方**原有实现**，逐字保留）；
    //   Normal → KSU LKM 链路（init_boot 补丁 + 该 KMI 的 kernelsu.ko）；
    //   Normal 的 KMI 矩阵外 / 缺产物 → 明确 blocker + 稳定码，**绝不静默回退 Ultra**。
    // 页面只把选择快照传进来，这里也只消费分派结果，不再自己 if(mode)。
    // ══════════════════════════════════════════════════════════════════════
    val requestedTarget = targetMode
        ?: InstallTargetMode.fromWorkMode(dev.maplesu.manager.workmode.ModeRuntime.active)
    val source = when {
        bootUri == null -> InstallSourceKind.CURRENT_PARTITION
        downloaded -> InstallSourceKind.DOWNLOADED_BOOT
        else -> InstallSourceKind.IMAGE
    }
    val release = deviceKernelRelease()
    val imageCarrier = bootUri?.let { uri -> runCatching {
        ksuApp.contentResolver.openInputStream(uri)?.use(
            dev.maplesu.manager.installer.InstallPartitionPolicy::imagePartition)
    }.getOrNull() }
    // Explicit targets reach the transaction without a separate discovery/root-token session.
    val discoveredTarget = if (bootUri == null && partition.isNullOrBlank() &&
        requestedTarget == InstallTargetMode.NORMAL) {
        dev.maplesu.manager.installer.StockInstallPreflight.discoverTarget(ksuApp, requestedTarget)
    } else null
    val availablePartitions = discoveredTarget?.availablePartitions
    val partitionResolution = dev.maplesu.manager.installer.InstallPartitionPolicy.resolve(
        requestedTarget, imageCarrier?.wire ?: partition?.takeIf(String::isNotBlank),
        availablePartitions,
        dev.maplesu.manager.workmode.switch.ModeSwitchKmi.parse(release))
    val resolvedPartition = (partitionResolution as? dev.maplesu.manager.installer.InstallPartitionResolution.Ready)
        ?.partition?.wire ?: imageCarrier?.wire ?: partition
    // This is an optional shortcut, never an installation precondition. Read the protected
    // receipt first; only a same-target completed install needs a live digest observation.
    val installedCarrier = if (bootUri == null && requestedTarget == InstallTargetMode.ULTRA &&
        ModeRuntime.active == dev.maplesu.manager.workmode.WorkMode.LITE && !forceRepatch) {
        try {
            InstallerServiceClient.withService(ksuApp, persistent = true, rootTarget = requestedTarget) { service ->
                val receipt = service.getBootInstall("")
                if (receipt.getString("targetMode") != "LITE" || receipt.getString("operation") != "INSTALL" ||
                    receipt.getString("partition") != resolvedPartition || receipt.getBoolean("operationActive") ||
                    receipt.getString("state") !in setOf("REBOOT_REQUIRED", "REBOOT_REQUESTED", "BOOT_IMAGE_VERIFIED", "VERIFIED", "BOOT_VERIFIED")) null
                else {
                    val observed = InstallerServiceClient.decodeSnapshot(service.observeBootChain())
                    resolvedPartition?.takeIf {
                        val current = observed.partitions[it]
                        current != null && receipt.getString("slotSuffix") == observed.slotSuffix &&
                            receipt.getString("partitionSha256") == current.sha256
                    }
                }
            }
        } catch (error: Exception) {
            dev.maplesu.manager.installer.rethrowInstallerCancellation(error)
            Log.w(TAG, "Optional environment carrier verification failed; preparing installation", error)
            null
        }
    } else null
    val changesUltraCarrier = requestedTarget == InstallTargetMode.ULTRA &&
        source == InstallSourceKind.CURRENT_PARTITION && resolvedPartition != null &&
        installedCarrier != resolvedPartition
    var directRequest = UltraDirectInstallRequest(
        activeMode = ModeRuntime.active, target = requestedTarget, source = source, partition = resolvedPartition,
        signing = lenovoSigning.enabled,
        environmentWasRunning = source == InstallSourceKind.CURRENT_PARTITION &&
            dev.maplesu.manager.runtime.ultra.UltraEnvironmentProbe.isRunning(),
        bootOptions = ota || lkm !is LkmSelection.KmiNone || allowShell || enableAdb ||
            forceBackup || blockModules.isNotBlank(),
        forceRepatch = forceRepatch || changesUltraCarrier,
    )
    if (variantId != null && UltraDirectInstallPolicy.eligible(directRequest)) {
        directRequest = directRequest.copy(defaultKernel = variantId == "generated" ||
            variantId == dev.maplesu.manager.ultrakernel.UltraKernelSpecGenerator().recipeId)
    }
    var kernelCredentialToPreserve: dev.maplesu.manager.ultrakernel.UltraKernelCredential? = null
    // Run before any native PRE, original-image recovery, or boot candidate generation.
    val environmentResult = UltraDirectInstallPolicy.dispatch<FlashResult?>(
        directRequest,
        facts = { dev.maplesu.manager.runtime.ultra.UltraEnvironmentUpgrade.readFacts(ksuApp) },
        upgrade = {
            onStage(InstallStage.UPGRADING_ENVIRONMENT)
            onStdout(ksuApp.getString(R.string.ultra_env_update_running))
            UltraDirectEnvironmentUpgrade.install(ksuApp, installationTaskId)
        },
        unavailable = { decision ->
            onStage(InstallStage.UPGRADING_ENVIRONMENT)
            UltraDirectEnvironmentUpgrade.unavailable(ksuApp, installationTaskId, decision)
        },
        patchBoot = { null },
        requiresKernelUpdate = {
            dev.maplesu.manager.installer.UltraKernelEnvironmentUpgrade.required(ksuApp).also { required ->
                if (required) kernelCredentialToPreserve = dev.maplesu.manager.modules.UltraRootKeyStore.read(ksuApp)
                    ?.let(dev.maplesu.manager.ultrakernel.UltraKernelCredential::of)
            }
        },
    )
    if (environmentResult != null) return environmentResult
    // 只有"安装页显式选了目标"或 Normal 路径才播报目标行：旧调用点（无 targetMode）的
    // Ultra 输出保持逐字节不变（既有 instrumentation 测试断言了 output 的首行/为空）。
    val announceTarget = targetMode != null || requestedTarget == InstallTargetMode.NORMAL
    // 内嵌 KMI 目录只在 Normal 目标下探测（Ultra 不需要 ksud，也不该多起一个 root shell）。
    val kmiCatalog = if (requestedTarget == InstallTargetMode.NORMAL) packagedKmiCatalog() else null
    val targetDecision = InstallTargetDispatch.decide(
        InstallTargetRequest(
            target = requestedTarget,
            source = source,
            partition = resolvedPartition,
            kernelRelease = release,
            availableKmis = kmiCatalog,
            sdkInt = android.os.Build.VERSION.SDK_INT,
            availablePartitions = availablePartitions,
        ),
    )
    when (targetDecision) {
        is InstallTargetDecision.Blocked -> {
            val message = installTargetBlockerMessage(ksuApp.resources, targetDecision)
            Log.w(TAG, "MAPLESU_INSTALL_TARGET_BLOCKED: ${targetDecision.code} ${targetDecision.detail}")
            dev.maplesu.manager.ksu.KsuLog.record(
                "install-target-blocked",
                "${targetDecision.code} ${targetDecision.detail}",
            )
            onStderr(message)
            return FlashResult(78, message, false)
        }

        is InstallTargetDecision.Ready -> {
            if (announceTarget) {
                // 用户可见的确认文案 / 事务记录：第一条日志就是「目标：Ultra|Normal（修补方式…）」。
                onStdout(installTargetBanner(ksuApp.resources, targetDecision))
                installationTaskId?.takeIf { bootUri == null || downloaded }?.let { taskId ->
                    runCatching {
                        val installer = dev.maplesu.manager.installer.InstallerServiceClient
                        installer.rememberInstallTarget(ksuApp, taskId, targetDecision.target)
                        // 回读校验：事务记录必须是本次快照，不能是上一次任务残留。
                        if (installer.installTargetOf(ksuApp, taskId) != targetDecision.target) {
                            Log.w(TAG, "INSTALL_TARGET_RECORD_MISMATCH task=$taskId")
                        }
                    }
                }
            }
            when (targetDecision.path) {
                InstallPatchPath.NORMAL_KSU_LKM -> {
                    // The producer reads a boot file's own kernel KMI. A kernel-less init_boot
                    // uses the explicit target KMI, or the current-device default as in ksud.
                    val policy = dev.maplesu.manager.ksu.KsuInstallPolicy.evaluate(
                        bootUri != null, targetDecision.kmi ?: (lkm as? LkmSelection.KmiString)?.value
                            ?: dev.maplesu.manager.installer.KmiRule.parse(release.orEmpty()),
                        lkm is LkmSelection.LkmUri, ota,
                        targetDecision.partition, allowShell, enableAdb, forceBackup, blockModules,
                        directInstall = bootUri == null,
                    )
                    if (policy !is dev.maplesu.manager.ksu.KsuInstallDecision.Allowed) {
                        val message = ksuApp.getString(R.string.install_image_required)
                        onStderr(message)
                        return FlashResult(78, message, false, FlashOutcome.FAILED)
                    }
                    return installStockTarget(
                        requestedTarget, requireNotNull(targetDecision.partition), bootUri,
                        installationTaskId, restoreOriginalImage, originalManagerPackage,
                        existingRootConflictAccepted, variantId, policy.options, kmiCatalog,
                        onStdout, onStderr, onStage, selectedImageOnly = !downloaded,
                        lenovoSigning = lenovoSigning,
                        vivoInputs = vivoInputs,
                    )
                }

                InstallPatchPath.ULTRA_ULTRA_KERNEL -> Unit // 继续下方既有 Ultra 实现
            }
        }
    }

    if (ota || lkm !is LkmSelection.KmiNone && !(resolvedPartition == "init_boot" && lkm is LkmSelection.KmiString) ||
        allowShell || enableAdb || forceBackup || blockModules.isNotBlank()) {
        Log.w(TAG, "MAPLESU_INSTALL_REQUEST_UNSUPPORTED")
        val message = ksuApp.getString(R.string.install_image_required)
        onStderr(message)
        return FlashResult(78, message, false)
    }
    // ══════════════════════════════════════════════════════════════════════
    // Ultra 只使用 Ultra 内核候选；用户选文件只导出，直接安装才进入写入事务。
    // ══════════════════════════════════════════════════════════════════════
    return installStockTarget(
        requestedTarget, requireNotNull(resolvedPartition), bootUri, installationTaskId,
        restoreOriginalImage,
        originalManagerPackage, existingRootConflictAccepted, variantId, null, null,
        onStdout, onStderr, onStage, selectedImageOnly = !downloaded,
        lenovoSigning = lenovoSigning,
        vivoInputs = vivoInputs,
        preservedCredential = kernelCredentialToPreserve,
        initBootArtifact = ultraInitBootArtifact,
        initBootKmi = (lkm as? LkmSelection.KmiString)?.value,
        pairedBootSnapshot = pairedBootSnapshot,
        pairedBootUri = pairedBootUri,
        pairedSourceSha256 = pairedSourceSha256,
        pairedBootSha256 = pairedBootSha256,
    )
}

/** Explicit environment repair shares the existing boot transaction, without SDK-only dispatch. */
internal fun installUltraKernelUpdate(taskId: String): FlashResult {
    check(ModeRuntime.active == dev.maplesu.manager.workmode.WorkMode.LITE) { "ULTRA_KERNEL_UPGRADE_MODE_CHANGED" }
    val credential = dev.maplesu.manager.modules.UltraRootKeyStore.read(ksuApp)
        ?.let(dev.maplesu.manager.ultrakernel.UltraKernelCredential::of)
        ?: error("ULTRA_KERNEL_CREDENTIAL_REQUIRED")
    val (partition, _) = InstallerServiceClient.analyzeCurrentCarrier(ksuApp)
    return installStockTarget(
        target = InstallTargetMode.ULTRA, partition = partition, bootUri = null, taskId = taskId,
        restoreOriginal = false, originalManager = null, conflictAccepted = false, variantId = null,
        normalOptions = null, kmiCatalog = null,
        onStdout = { Log.i(TAG, it) }, onStderr = { Log.w(TAG, it) }, onStage = {},
        selectedImageOnly = false, preservedCredential = credential,
        lenovoSigning = dev.maplesu.manager.installer.UltraKernelEnvironmentUpgrade.signingOptions(ksuApp),
    )
}

/** A builder can stop before any write (including an already-patched input). */
private class InstallBuildStopped(val result: FlashResult) : Exception(result.err)

private fun installStockTarget(
    target: InstallTargetMode,
    partition: String,
    bootUri: Uri?,
    taskId: String?,
    restoreOriginal: Boolean,
    originalManager: String?,
    conflictAccepted: Boolean,
    variantId: String?,
    normalOptions: dev.maplesu.manager.ksu.KsuBootPatchOptions?,
    kmiCatalog: Collection<String>?,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit,
    onStage: (InstallStage) -> Unit,
    selectedImageOnly: Boolean,
    lenovoSigning: dev.maplesu.manager.installer.LenovoSigningOptions = dev.maplesu.manager.installer.LenovoSigningOptions(),
    vivoInputs: dev.maplesu.manager.installer.VivoCompatibilityInputs = dev.maplesu.manager.installer.VivoCompatibilityInputs(),
    preservedCredential: dev.maplesu.manager.ultrakernel.UltraKernelCredential? = null,
    initBootArtifact: dev.maplesu.image.UltraInitBootProducer.Artifact? = null,
    initBootKmi: String? = null,
    pairedBootSnapshot: android.os.Bundle? = null,
    pairedBootUri: Uri? = null,
    pairedSourceSha256: String? = null,
    pairedBootSha256: String? = null,
): FlashResult {
    check(pairedBootUri == null || target == InstallTargetMode.ULTRA && partition == "init_boot" &&
        bootUri != null && selectedImageOnly && pairedBootSnapshot == null &&
        (pairedSourceSha256 == null) == (pairedBootSha256 == null)) { "ULTRA_INIT_SELECTED_PAIR_MODE" }
    val id = taskId ?: java.util.UUID.randomUUID().toString()
    return dev.maplesu.manager.installer.LenovoSigningSession(ksuApp, lenovoSigning, target, id,
        bootUri != null && selectedImageOnly, onStdout).use { signing ->
    var exportOnly = false
    var bootCandidate: InstallCandidateImage? = null
    var preparedRestoreOriginal = restoreOriginal
    var nativePrepareSubmitted: Boolean? = null
    var preparedBridgeArtifact: dev.maplesu.image.UltraInitBootProducer.Artifact? = null
    val componentArtifact by lazy {
        if (target == InstallTargetMode.ULTRA && partition == "init_boot" && pairedBootUri == null &&
            initBootArtifact?.isKmiComponent != false) {
            val kmi = initBootKmi ?: initBootArtifact?.componentKmi?.takeIf(String::isNotBlank)
                ?: dev.maplesu.manager.installer.KmiRule.parse(deviceKernelRelease().orEmpty())
                ?: error("ULTRA_INIT_TARGET_KMI_REQUIRED")
            (initBootArtifact ?: requireNotNull(dev.maplesu.manager.ultrakernel.UltraKernelFlow.initBootArtifact(ksuApp, kmi)) {
                if (dev.maplesu.manager.ultrakernel.UltraKernelFlow.availableInitBootKmis(ksuApp).isEmpty()) {
                    "ULTRA_INIT_COMPONENT_UNAVAILABLE: " + ksuApp.getString(R.string.ultra_init_components_unavailable)
                } else {
                    "ULTRA_INIT_KMI_COMPONENT_MISSING:$kmi"
                }
            }).also {
                check(it.isKmiComponent && it.componentKmi == kmi) { "ULTRA_INIT_TARGET_COMPONENT_KMI:$kmi:${it.componentKmi}" }
            }
        } else null
    }
    val bridgeArtifact by lazy {
        if (componentArtifact != null) componentArtifact
        else if (bootUri == null && (ModeRuntime.active == null || ModeRuntime.active == target.workMode)) preparedBridgeArtifact
        else if (target == InstallTargetMode.ULTRA && partition == "init_boot") {
            if (bootUri == null || pairedBootSnapshot != null) {
                val observed = InstallerServiceClient.observeBootChain(ksuApp)
                pairedBootSnapshot?.let { encoded ->
                    val expected = InstallerServiceClient.decodeSnapshot(encoded)
                    check(observed.slotSuffix == expected.slotSuffix && observed.kernelRelease == expected.kernelRelease &&
                        observed.firmware == expected.firmware && observed.partitions == expected.partitions) {
                        "INSTALLER_DEVICE_CHANGED"
                    }
                }
                val boot = observed.partitions["boot"] ?: error("ULTRA_INIT_PAIRED_BOOT_REQUIRED")
                val paired = InstallerServiceClient.capturePairedBoot(ksuApp, observed,
                    File(ksuApp.filesDir, "ultra-init-paired-${boot.sha256}.img"))
                initBootArtifact?.let { dev.maplesu.manager.ultrakernel.UltraInitBootTargetResolver.resolve(it, paired,
                    dev.maplesu.manager.ultrakernel.UltraKernelContextAssets(ksuApp)) }
                    ?: dev.maplesu.manager.ultrakernel.UltraKernelFlow.initBootArtifact(ksuApp, paired)
            } else requireNotNull(initBootArtifact) { "ULTRA_KERNEL_INIT_BOOT_ARTIFACT_REQUIRED" }
        } else null
    }
    fun written(): FlashResult {
        InstallerServiceClient.rememberInstallTarget(ksuApp, id, target)
        if (target == InstallTargetMode.ULTRA) {
            runCatching { dev.maplesu.manager.runtime.ultra.UltraInstallCredentialRecovery.recover(ksuApp) }
                .onFailure { Log.w(TAG, "Install credential handoff deferred to startup", it) }
        }
        onStage(InstallStage.AWAITING_REBOOT)
        onStdout(ksuApp.getString(R.string.install_image_installed))
        return FlashResult(0, "", true, FlashOutcome.WRITTEN, bootCandidate = bootCandidate)
    }
    fun buildPatched(source: File,
        recoveredDeployment: dev.maplesu.manager.ultrakernel.UltraKernelDeploymentNamespace? = null,
        explicitArtifact: dev.maplesu.image.UltraInitBootProducer.Artifact? = null): InstallCandidateImage {
        if (pairedBootUri != null && explicitArtifact == null) {
            val input = ksuApp.contentResolver.openInputStream(pairedBootUri) ?: error("BOOT_SOURCE_READ_FAILED")
            val expected = pairedSourceSha256?.let { sourceSha ->
                dev.maplesu.manager.installer.SelectedBootPairInput.Identity(sourceSha, requireNotNull(pairedBootSha256))
            }
            return dev.maplesu.manager.installer.SelectedBootPairInput.use(source, input, ksuApp.cacheDir,
                dev.maplesu.manager.ImageRepository.MAX_IMAGE_BYTES, expected) { paired, _ ->
                val artifact = requireNotNull(dev.maplesu.manager.ultrakernel.UltraKernelFlow.initBootArtifact(ksuApp, paired)) {
                    "ULTRA_KERNEL_INIT_BOOT_ARTIFACT_REQUIRED"
                }
                buildPatched(source, recoveredDeployment, artifact)
            }
        }
        onStage(InstallStage.PATCHING)
        if (target == InstallTargetMode.ULTRA) {
            val selectedArtifact = explicitArtifact ?: bridgeArtifact
            val pairedDeployment = if (selectedArtifact?.isKmiComponent != true && explicitArtifact == null &&
                partition == "init_boot" && bootUri != null && pairedBootSnapshot != null) {
                val expected = InstallerServiceClient.decodeSnapshot(pairedBootSnapshot)
                InstallerServiceClient.verifyInitBootSource(ksuApp, expected, source)
            } else null
            val repository = dev.maplesu.manager.ImageRepository(ksuApp)
            val imported = repository.import(Uri.fromFile(source))
            val built = ultraImageBuildCandidate(repository, imported.report.sha256, onStdout, variantId,
                imageOnly = exportOnly, initBootArtifact = selectedArtifact,
                recoveredDeployment = pairedDeployment ?: recoveredDeployment)
            return when (built) {
                is UltraImageBuild.Candidate, is UltraImageBuild.AlreadyPatched -> {
                    val result = if (built is UltraImageBuild.Candidate) built else {
                        onStdout("镜像已修补，继续安装")
                        (built as UltraImageBuild.AlreadyPatched).candidate
                    }
                    val artifact = selectedArtifact
                    val expectedCredential = if (result.partition == "init_boot" &&
                        artifact?.credentialMode == "module-parameter") {
                        preservedCredential?.withMetadataDisplacement(artifact.metadataDisplacement)
                    } else preservedCredential
                    dev.maplesu.manager.ultrakernel.UltraKernelUpdateRequirement.requirePreserved(
                        expectedCredential, result.credential)
                    check(result.partition == partition) { "ULTRA_KERNEL_CANDIDATE_PARTITION_MISMATCH" }
                    if (!exportOnly) dev.maplesu.manager.runtime.ultra.UltraInstallCredentials.retainCandidate(
                        ksuApp, result.image, result.bootSha256, result.credential, id)
                    InstallCandidateImage(result.image, result.bootSha256, result.image.length(), partition)
                }
                is UltraImageBuild.Refused -> throw InstallBuildStopped(
                    FlashResult(built.code, built.message, false, FlashOutcome.FAILED))
            }.also { bootCandidate = it }
        }
        val daemon = File(ksuApp.applicationInfo.nativeLibraryDir, "libksud_ksu.so")
        val session = if (!exportOnly && ModeRuntime.active != null) ModeSwitchRootSession(ksuApp).also {
            check(it.ensureReady().ok) { "INSTALLER_ROOT_UNAVAILABLE" }
        } else null
        val runner = if (exportOnly) dev.maplesu.manager.ksu.KsuFileCommandRunner()
            else if (session == null) dev.maplesu.manager.ksu.LibsuRootCommandRunner(daemon.path)
            else dev.maplesu.manager.ksu.KsuRootCommandRunner { command, _ ->
                session.execute(command).let {
                    dev.maplesu.manager.ksu.KsuShellResult(it.code, it.output.lines(), listOf(it.detail).filter(String::isNotBlank))
                }
            }
        val producer = dev.maplesu.manager.ksu.KsuInstallProducer(
            daemonPath = daemon.path,
            runner = runner,
            workRoot = dev.maplesu.manager.ksu.KsuCandidateWorkspaces.install(ksuApp.filesDir),
            commit = dev.maplesu.manager.ksu.KsuCandidateCommitPort { _, _ ->
                dev.maplesu.manager.ksu.KsuCommitResult.NoTransaction("PRODUCE_ONLY", "")
            },
            kmiCatalog = { kmiCatalog },
            appUid = if (exportOnly) null else android.os.Process.myUid(),
        )
        return when (val result = producer.produceCandidate(source, requireNotNull(normalOptions), onStdout, onStderr,
            onCommand = { receipt ->
                dev.maplesu.manager.ksu.KsuLog.record("boot-patch", dev.maplesu.manager.runtime.ultra.UltraSdkLogging.redact(
                    "argv=${receipt.argv} code=${receipt.result.code} stdout=${receipt.result.outText} stderr=${receipt.result.errText}"))
            })) {
            is dev.maplesu.manager.ksu.KsuProduceResult.Produced -> result.candidate.let {
                InstallCandidateImage(it.candidate, it.sha256, it.size, it.partition)
            }
            is dev.maplesu.manager.ksu.KsuProduceResult.Rejected -> {
                Log.w(TAG, "Candidate rejected: ${result.code} ${result.detail}")
                throw InstallBuildStopped(FlashResult(result.exitCode ?: 1,
                    "${ksuApp.getString(R.string.install_image_patch_failed)} [${result.code}] ${result.detail}",
                    false, FlashOutcome.FAILED))
            }
        }.also { bootCandidate = it }
    }
    fun build(source: File): InstallCandidateImage {
        val patched = buildPatched(source)
        val compatible = if (dev.maplesu.manager.installer.VivoCompatibility.deviceSupported())
            dev.maplesu.manager.installer.VivoCompatibility.prepareFiles(ksuApp, target.workMode, patched, vivoInputs, onStdout)
            else patched
        return signing.finish(compatible, source).also { bootCandidate = it }
    }
    fun publish(candidate: InstallCandidateImage): String {
        if (!lenovoSigning.enabled && candidate.compatibilityImages.isEmpty())
            return dev.maplesu.manager.BootCandidatePublisher.publish(ksuApp, candidate.image, candidate.sha256)
        val images = linkedMapOf(candidate.partition to candidate.image).apply {
            putAll(signing.companions(candidate.partition))
            putAll(candidate.compatibilityImages.mapValues { it.value.image })
        }
        val names = images.mapValues { (name, image) ->
            "maplesu-patched-$name-${dev.maplesu.manager.workmode.Hashes.sha256(image).take(12)}.img"
        }
        val saved = dev.maplesu.manager.BootCandidatePublisher.publishAll(ksuApp,
            images.map { (name, image) -> names.getValue(name) to dev.maplesu.manager.BootCandidatePublisher.Image(image,
                dev.maplesu.manager.workmode.Hashes.sha256(image)) }.toMap())
        images.keys.forEach { name -> onStdout("镜像输出路径 [$name]：${saved.getValue(names.getValue(name))}") }
        onStdout("完整镜像组已输出。")
        return saved.getValue(names.getValue(candidate.partition))
    }
    if (bootUri != null) {
        if (selectedImageOnly) {
            exportOnly = true
            try {
                val source = File.createTempFile("patch-source-", ".img", ksuApp.cacheDir)
                val saved = dev.maplesu.manager.exportSelectedImage(
                    source,
                    copySource = { copyInstallerImage(bootUri, it) },
                    build = ::build,
                    publish = ::publish,
                )
                val message = ksuApp.getString(R.string.install_image_saved, saved)
                onStdout(message)
                return FlashResult(0, "", false, FlashOutcome.SAVED_ONLY, outputPath = saved)
            } catch (stop: InstallBuildStopped) {
                if (stop.result.code == 0) onStdout(stop.result.err) else onStderr(stop.result.err)
                return stop.result
            } catch (error: Exception) {
                dev.maplesu.manager.installer.rethrowInstallerCancellation(error)
                Log.w(TAG, "Selected image patch/export failed", error)
                val message = ksuApp.getString(R.string.install_image_patch_failed) + " ${error.message.orEmpty()}"
                onStderr(message)
                return FlashResult(1, message, false, FlashOutcome.FAILED)
            }
        }
    }
    try {
        val active = ModeRuntime.active
        if (active != null && active != target.workMode) {
            InstallerServiceClient.rememberModeSwitchTask(ksuApp, id)
            val facade = ModeSwitchRuntime.facadeOrNull() ?: ModeSwitchRuntime.initialize(ksuApp)
            val progress: (String) -> Unit = { stage ->
                val step = modeSwitchStepOf(stage, stage, stage)
                onStage(when (step) {
                    ModeSwitchStep.WRITE, ModeSwitchStep.RESTORE -> InstallStage.WRITING
                    ModeSwitchStep.REBOOT -> InstallStage.AWAITING_REBOOT
                    else -> InstallStage.PATCHING
                })
                if (step != null) onStdout(ksuApp.getString(WorkModeStrings.stepRes(step)))
            }
            if (bootUri == null) {
                val result = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                    facade.startSwitch(target.workMode, confirmHidden = true, onProgress = progress,
                        requestedPartition = partition, requestedSwitchId = id,
                        initBootArtifact = bridgeArtifact,
                        signingMode = if (lenovoSigning.enabled) lenovoSigning.mode(partition).name else "",
                        finalizeCandidate = if (lenovoSigning.enabled) { candidate, source ->
                            signing.finish(candidate, source).also { bootCandidate = it }
                        } else null)
                }
                if (result is SwitchOutcome.AwaitingReboot) {
                    bootCandidate = result.candidate
                    return written()
                }
                val converted = dev.maplesu.manager.installer.installerModeSwitchResult(result)
                if (converted.imagesRequired != null) return converted
                Log.w(TAG, "Mode installation stopped: $result")
                onStderr(converted.err)
                return converted
            }
            val source = File.createTempFile("switch-source-", ".img", ksuApp.cacheDir)
            try {
                copyInstallerImage(bootUri, source)
                if (lenovoSigning.enabled) {
                    // Preparation must precede signing: companions and restore hashes come
                    // from the native PRE, never from an unprepared single-image writer.
                    val candidate = buildPatched(source)
                    val result = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                        facade.startSwitch(target.workMode, confirmHidden = true, onProgress = progress,
                            requestedPartition = partition, requestedSwitchId = id,
                            initBootArtifact = bridgeArtifact,
                            signingMode = lenovoSigning.mode(partition).name,
                            finalizeCandidate = { patched, _ -> signing.finish(patched, source).also { bootCandidate = it } },
                            providedCandidate = candidate)
                    }
                    if (result is SwitchOutcome.AwaitingReboot) {
                        bootCandidate = result.candidate
                        return written()
                    }
                    return dev.maplesu.manager.installer.installerModeSwitchResult(result).also { onStderr(it.err) }
                }
                val candidate = build(source)
                val result = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                    facade.installCommit(candidate, id, progress, target.workMode, initBootArtifact = bridgeArtifact)
                }
                if (result is InstallCommitOutcome.AwaitingReboot) return written()
                result as InstallCommitOutcome.Rejected
                Log.w(TAG, "Mode installation stopped: ${result.code} ${result.detail}")
                val message = ksuApp.getString(if (result.requiresRecovery) R.string.install_image_recovery_required else R.string.install_image_patch_failed) +
                    "\n" + dev.maplesu.manager.installer.installerFailureDiagnostic(null, result.code)
                onStderr(message)
                return FlashResult(1, message, false,
                    if (result.requiresRecovery) FlashOutcome.RECOVERY_REQUIRED else FlashOutcome.FAILED)
            } finally { source.delete() }
        }
        // First install and same-mode updates use the same root-owned stock/PRE transaction.
        nativePrepareSubmitted = false
        val result = StockInstallFlow.install(
            ksuApp, id, target, partition, bootUri, restoreOriginal, originalManager, conflictAccepted,
            onPrepared = { prepared ->
                preparedRestoreOriginal = prepared.getBoolean("restoreOriginal")
                prepared.getString("baselineDirectory")?.takeIf(String::isNotBlank)?.let {
                    onStdout(ksuApp.getString(R.string.install_originals_saved, it))
                }
                prepared.getString("backupDirectory")?.takeIf(String::isNotBlank)?.let {
                    onStdout(ksuApp.getString(R.string.install_rollback_saved, it))
                }
            },
            produce = ::buildPatched,
            onWriting = {
                onStage(InstallStage.WRITING)
                onStdout(ksuApp.getString(if (preparedRestoreOriginal) R.string.install_stock_restoring else R.string.install_image_writing))
            },
            signingOptions = lenovoSigning,
            signing = signing,
            log = onStdout,
            initBootArtifact = if (bootUri == null) componentArtifact else bridgeArtifact,
            onPrepareSubmitted = { nativePrepareSubmitted = true },
            resolveInitBootArtifact = if (bootUri != null) null else { paired ->
                (initBootArtifact?.let {
                    dev.maplesu.manager.ultrakernel.UltraInitBootTargetResolver.resolve(it, paired,
                        dev.maplesu.manager.ultrakernel.UltraKernelContextAssets(ksuApp))
                } ?: dev.maplesu.manager.ultrakernel.UltraKernelFlow.initBootArtifact(ksuApp, paired))
                    .also { preparedBridgeArtifact = it }
            },
        )
        if (result.getString("state") == "REBOOT_REQUIRED" && result.getBoolean("rebootRequired")) return written()
        val recovered = installerTaskResult(result.getString("state").orEmpty(),
            result.getBoolean("rebootRequired"), result.getString("error").orEmpty())
        val message = ksuApp.getString(when (recovered.outcome) {
            FlashOutcome.RESTORED -> R.string.install_image_restored
            FlashOutcome.RECOVERY_REQUIRED -> R.string.install_image_recovery_required
            else -> R.string.install_image_patch_failed
        }) + "\n" + dev.maplesu.manager.installer.installerFailureDiagnostic(null, recovered.err)
        onStderr(message)
        return recovered.copy(err = message)
    } catch (stop: InstallBuildStopped) {
        if (stop.result.code == 0) onStdout(stop.result.err) else onStderr(stop.result.err)
        return stop.result
    } catch (error: Exception) {
        dev.maplesu.manager.installer.rethrowInstallerCancellation(error)
        Log.w(TAG, "Stock installation failed", error)
        // Only a submitted or uncertain request can have progressed after losing its reply.
        val status = if (nativePrepareSubmitted == false) null else runCatching {
            InstallerServiceClient.installationStatus(ksuApp, id, target)
        }.getOrElse {
            dev.maplesu.manager.installer.rethrowInstallerCancellation(it)
            null
        }
        if (status?.getString("state") in setOf("REBOOT_REQUIRED", "REBOOT_REQUESTED", "BOOT_IMAGE_VERIFIED", "BOOT_VERIFIED", "VERIFIED")) {
            return written()
        }
        val recovery = status?.let {
            installerTaskResult(it.getString("state").orEmpty(), it.getBoolean("rebootRequired"), it.getString("error").orEmpty())
        } ?: FlashResult(1, "", false,
            if (nativePrepareSubmitted == false) FlashOutcome.FAILED else FlashOutcome.RECOVERY_REQUIRED)
        val accessFailure = com.maplesu.core.ui.screen.install.installSourceAccessFailure(error)
        val message = ksuApp.getString(when {
            status == null && accessFailure == com.maplesu.core.ui.screen.install.InstallSourceAccessFailure.ROOT_REQUIRED ->
                R.string.install_root_required
            status == null && accessFailure == com.maplesu.core.ui.screen.install.InstallSourceAccessFailure.CHANNEL_UNAVAILABLE ->
                R.string.install_source_service_unavailable
            recovery.outcome == FlashOutcome.RESTORED -> R.string.install_image_restored
            recovery.outcome == FlashOutcome.RECOVERY_REQUIRED -> R.string.install_image_recovery_required
            else -> R.string.install_image_patch_failed
        }) + "\n" + dev.maplesu.manager.installer.installerFailureDiagnostic(error, status?.getString("error").orEmpty())
        onStderr(message)
        return recovery.copy(code = dev.maplesu.manager.installer.installerFailureExitCode(error, recovery.code),
            err = message, installationSubmitted = nativePrepareSubmitted)
    }
    }
}

private fun copyInstallerImage(uri: Uri, destination: File) {
    checkNotNull(ksuApp.contentResolver.openInputStream(uri)) { "DOCUMENT_READ_FAILED" }.use { input ->
        destination.outputStream().use { output ->
            val buffer = ByteArray(64 * 1024)
            var size = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                size += count
                check(size <= dev.maplesu.manager.ImageRepository.MAX_IMAGE_BYTES) { "IMAGE_SIZE_LIMIT" }
                output.write(buffer, 0, count)
            }
            check(size > 0) { "IMAGE_EMPTY" }
            output.fd.sync()
        }
    }
}

/**
 * 「有 Root 时直接安装」：dump 当前 boot → 建候选 → 事务写（行为与 2026-10-31 一致，
 * 只多了阶段上报与结果态，以及**不再**回落到 Lite）。
 */
private fun installBootIntoCurrentPartition(
    transactionTaskId: String,
    variantId: String?,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit,
    onStage: (InstallStage) -> Unit,
): FlashResult {
    dev.maplesu.manager.installer.InstallerServiceClient.rememberTask(ksuApp, transactionTaskId)
    onStdout(ksuApp.getString(R.string.install_check_root))
    try {
        onStdout(ksuApp.getString(R.string.install_check_partitions))
        val observed = dev.maplesu.manager.installer.InstallerServiceClient.analyzeCurrentBoot(ksuApp)
        onStdout(ksuApp.getString(R.string.install_partition_checked, "boot${observed.slotSuffix}"))
        onStdout(ksuApp.getString(R.string.install_image_verified))
        onStage(InstallStage.PATCHING)
        return ultraDirectInstall(observed, transactionTaskId, onStdout, onStderr, variantId, onStage)
    } catch (exception: dev.maplesu.manager.installer.InstallerServiceClient.AuthorizationFailure) {
        dev.maplesu.manager.installer.rethrowInstallerCancellation(exception)
        Log.w(TAG, "Installation authorization failed", exception)
        val failure = com.maplesu.core.ui.screen.install.installSourceAccessFailure(exception)
        val message = ksuApp.getString(if (failure == com.maplesu.core.ui.screen.install.InstallSourceAccessFailure.CHANNEL_UNAVAILABLE)
            R.string.install_source_service_unavailable else R.string.install_root_required)
        onStderr(message)
        return FlashResult(dev.maplesu.manager.installer.installerFailureExitCode(exception), message, false, FlashOutcome.FAILED)
    } catch (exception: Exception) {
        dev.maplesu.manager.installer.rethrowInstallerCancellation(exception)
        Log.w(TAG, "Installation boot-chain observation failed", exception)
        val failure = com.maplesu.core.ui.screen.install.installSourceAccessFailure(exception)
        val message = ksuApp.getString(if (failure == com.maplesu.core.ui.screen.install.InstallSourceAccessFailure.CHANNEL_UNAVAILABLE)
            R.string.install_source_service_unavailable else R.string.install_partition_read_failed)
        onStderr(message)
        return FlashResult(dev.maplesu.manager.installer.installerFailureExitCode(exception), message, false, FlashOutcome.FAILED)
    }
}

/**
 * 「选择文件并刷补」：**这一次真的写盘**（缺陷 1）。
 *
 * 顺序是有意的，不能换：
 *
 *  1. 先 dump 现场 boot 并立刻另存为回滚镜像 —— `ImageRepository` 只有一个槽
 *     `files/images/imported.img`，两条来源都往它写（47 报告 R3）。先 import 用户文件
 *     会把"回滚镜像"变成用户文件本身，服务端随后以 `INSTALLER_RECOVERY_MISMATCH` 拒绝。
 *  2. 再 import 用户选择的 boot（覆盖单槽）。
 *  3. 识别 release → 按用户选定的变体建候选（全部摘要校验在写盘前完成）。
 *  4. 有快照 + 回滚镜像 + release 与设备当前 boot 一致 → 走既有事务写盘（不新造写盘）。
 *     否则 → **只出候选**（`SAVED_ONLY`）并明确写出原因，绝不假装成功，也绝不换成 Lite。
 */
private fun installBootFromChosenImage(
    bootUri: Uri,
    transactionTaskId: String?,
    variantId: String?,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit,
    onStage: (InstallStage) -> Unit,
): FlashResult {
    // ① 现场快照 + 回滚镜像（必须在 import 之前）
    var snapshot: dev.maplesu.manager.installer.InstallerSnapshot? = null
    var recovery: File? = null
    var saveOnlyReason: String? = null
    if (transactionTaskId == null) {
        saveOnlyReason = ksuApp.getString(R.string.install_boot_saved_no_transaction)
    } else {
        onStdout(ksuApp.getString(R.string.install_check_root))
        try {
            val observed = dev.maplesu.manager.installer.InstallerServiceClient.analyzeCurrentBoot(ksuApp)
            val backup = dev.maplesu.manager.ultrakernel.UltraKernelFlow(ksuApp).backupCurrentBoot()
            snapshot = observed
            recovery = backup
            onStdout(ksuApp.getString(R.string.install_partition_checked, "boot${observed.slotSuffix}"))
        } catch (exception: Exception) {
            // 无 root / 服务不通：文件路径仍然能出候选（只是不写盘）。这不是拦截。
            Log.w(TAG, "Rollback image unavailable; the candidate will only be published", exception)
            saveOnlyReason = ksuApp.getString(R.string.install_boot_saved_no_root)
        }
    }

    // ② 导入用户选择的 boot
    onStdout(ksuApp.getString(R.string.install_image_analyzing))
    val repository = dev.maplesu.manager.ImageRepository(ksuApp)
    val sourceSha256 = try {
        repository.import(bootUri).report.sha256
    } catch (exception: Exception) {
        Log.w(TAG, "Image analysis failed", exception)
        val message = ksuApp.getString(R.string.install_image_invalid)
        onStderr(message)
        return FlashResult(1, message, false, FlashOutcome.FAILED)
    }
    try {
        check(repository.verify().report.sha256 == sourceSha256) { "PATCH_SOURCE_CHANGED" }
    } catch (exception: Exception) {
        Log.w(TAG, "Image verification failed", exception)
        val message = ksuApp.getString(R.string.install_image_invalid)
        onStderr(message)
        return FlashResult(1, message, false, FlashOutcome.FAILED)
    }
    onStdout(ksuApp.getString(R.string.install_image_verified))

    // ③ 识别 + 选变体 + 建候选（S5 PATCHING）
    onStage(InstallStage.PATCHING)
    val built = when (val result = ultraImageBuildCandidate(repository, sourceSha256, onStdout, variantId)) {
        is UltraImageBuild.Refused -> {
            onStderr(result.message)
            return FlashResult(result.code, result.message, false, FlashOutcome.FAILED)
        }
        is UltraImageBuild.AlreadyPatched -> {
            onStdout("镜像已修补，继续安装")
            result.candidate
        }
        is UltraImageBuild.Candidate -> result
    }

    // ④ 写盘。release 必须与设备当前 boot 一致，否则服务端会以
    //    INSTALLER_CANDIDATE_KERNEL_MISMATCH 拒绝（InstallerBootTransaction.prepare）。
    //    与其白建一个事务，不如只出候选并把原因写清楚。
    val observed = snapshot
    val rollback = recovery
    val mismatch = observed != null && observed.kernelRelease != built.release
    if (built.partition == "boot" && observed != null && rollback != null && !mismatch && transactionTaskId != null) {
        onStage(InstallStage.WRITING)
        onStdout(ksuApp.getString(R.string.install_image_writing))
        val installed = try {
            dev.maplesu.manager.runtime.ultra.UltraInstallCredentials.retainCandidate(
                ksuApp, built.image, built.bootSha256, built.credential, transactionTaskId)
            dev.maplesu.manager.installer.InstallerServiceClient.installBoot(
                ksuApp, built.image, observed, rollback, transactionTaskId)
        } catch (exception: Exception) {
            Log.w(TAG, "Installer transaction failed", exception)
            val message = ksuApp.getString(R.string.install_image_patch_failed) +
                " ${exception.message.orEmpty()}"
            onStderr(message)
            return FlashResult(1, message, false, FlashOutcome.FAILED)
        }
        val state = installed.getString("state")
        if (state == "REBOOT_REQUIRED" && installed.getBoolean("rebootRequired")) {
            runCatching { dev.maplesu.manager.runtime.ultra.UltraInstallCredentialRecovery.recover(ksuApp) }
                .onFailure { Log.w(TAG, "Install credential handoff deferred to startup", it) }
            onStage(InstallStage.AWAITING_REBOOT)
            onStdout(ksuApp.getString(R.string.install_image_installed))
            return FlashResult(0, "", true, FlashOutcome.WRITTEN)
        }
        Log.w(TAG, "Chosen-image installation stopped at state=$state")
        val restored = state == "RESTORED"
        val message = ksuApp.getString(
            if (restored) R.string.install_image_restored else R.string.install_image_recovery_required)
        onStderr(message)
        return FlashResult(1, message, restored,
            if (restored) FlashOutcome.RESTORED else FlashOutcome.RECOVERY_REQUIRED)
    }

    // ⑤ 只出候选（S6 SAVED_ONLY）
    val saved = dev.maplesu.manager.BootCandidatePublisher.publish(
        ksuApp, built.image, built.bootSha256)
    onStdout(ksuApp.getString(R.string.install_image_saved, saved))
    val reason = if (mismatch) {
        ksuApp.getString(R.string.install_boot_saved_release_mismatch, built.release,
            observed?.kernelRelease.orEmpty())
    } else {
        saveOnlyReason ?: ksuApp.getString(R.string.install_boot_saved_no_root)
    }
    onStderr(reason)
    return FlashResult(0, reason, false, FlashOutcome.SAVED_ONLY)
}

fun downloadBoot(
    url: String,
    partition: String,
    lkm: LkmSelection,
    allowShell: Boolean,
    enableAdb: Boolean,
    forceBackup: Boolean,
    blockModules: String,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit,
    /** 目标工作模式（见 [installBoot]）；下载按同一个已选分区提取镜像。 */
    targetMode: InstallTargetMode = InstallTargetMode.DEFAULT,
    installationTaskId: String? = null,
    variantId: String? = null,
    onStage: (InstallStage) -> Unit = {},
    existingRootConflictAccepted: Boolean = false,
    restoreOriginalImage: Boolean = false,
    originalManagerPackage: String? = null,
): FlashResult {
    if (lkm !is LkmSelection.KmiNone || allowShell || enableAdb || forceBackup || blockModules.isNotBlank()) {
        val message = ksuApp.getString(R.string.install_image_required)
        onStderr(message)
        return FlashResult(78, message, false)
    }
    if (dev.maplesu.manager.installer.InstallPartitionPolicy.resolve(targetMode, partition)
        !is dev.maplesu.manager.installer.InstallPartitionResolution.Ready) {
        val message = ksuApp.getString(R.string.install_partition_read_failed)
        onStderr(message)
        return FlashResult(78, message, false)
    }
    val bootFile = File.createTempFile("download-boot-", ".img", ksuApp.cacheDir)
    try {
        onStdout("- Downloading and extracting $partition image")
        DataSourceChannel(newDownloadClient(), url).use { channel ->
            val magic = readMagic(channel)
            val image = ExtractImage(bootFile, onStdout)
            if (magic == "CrAU") {
                image.consumePayload(channel, partition)
            } else {
                image.consume(channel, partition)
            }
        }
        // Remote and local images share validation and the same MapleSU capability boundary.
        return installBoot(Uri.fromFile(bootFile), lkm, false, partition,
            allowShell, enableAdb, forceBackup, blockModules, onStdout, onStderr,
            installationTaskId = installationTaskId, targetMode = targetMode, downloaded = true,
            variantId = variantId, onStage = onStage,
            existingRootConflictAccepted = existingRootConflictAccepted,
            restoreOriginalImage = restoreOriginalImage, originalManagerPackage = originalManagerPackage)
    } catch (e: Exception) {
        Log.w(TAG, "Boot download or extraction failed", e)
        val message = ksuApp.getString(R.string.install_image_invalid)
        onStderr(message)
        return FlashResult(1, message, false)
    } finally {
        bootFile.delete()
    }
}

suspend fun probeRemoteBootPartitions(url: String): ProbeResult = withContext(Dispatchers.IO) {
    Log.d(TAG, "probe start: $url")
    val channel = DataSourceChannel(newDownloadClient(), url)
    Log.d(TAG, "probe connected, size=${channel.size()}")
    val magic = readMagic(channel)
    Log.d(TAG, "probe magic: $magic")
    // Only list the partitions here; the KMI is extracted later when the
    // payload is downloaded for patching.
    val result = if (magic == "CrAU") {
        ExtractImage.probePayload(channel, withKmi = false)
    } else {
        ExtractImage.probe(channel, withKmi = false)
    }
    Log.d(TAG, "probe partitions: ${result.partitions}")
    result
}

private fun newDownloadClient(): OkHttpClient {
    return OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
}

private fun readMagic(channel: DataSourceChannel): String {
    val buffer = ByteBuffer.allocate(4)
    channel.read(buffer)
    channel.position(0)
    return String(buffer.array(), StandardCharsets.ISO_8859_1)
}

fun reboot(reason: String = ""): Boolean = requestSystemReboot(ksuApp, reason)

fun rootAvailable(): Boolean {
    return managerRootAvailable(
        mode = ModeRuntime.active,
        ultraIdentity = runCatching {
            dev.maplesu.manager.runtime.ultra.UltraEnvironmentProbe.usesUltraBackend(ksuApp)
        }.getOrDefault(false),
        capability = dev.maplesu.manager.runtime.RootExec.capability(),
        legacyShellRoot = { getRootShell().isRoot },
    )
}

suspend fun getCurrentKmi(): String = withContext(Dispatchers.IO) {
    val shell = getRootShell()
    val cmd = "boot-info current-kmi"
    ShellUtils.fastCmd(shell, "${getKsuDaemonPath()} $cmd")
}

/**
 * 🟢 v2.7：读**磁盘上**那份持久化隐身标志 `/data/adb/sevenk/stealth`（内核写，掉电/重装都不丢）。
 *
 * 用途只有一个：内核的 `STEALTH_GET` ioctl **读不出来**时（三态里的 UNKNOWN，见
 * [Stealth.state]），给「这台机器是不是真开着隐身」补一个独立信号 ——
 * 决定安装页要不要藏起「直接安装（推荐）」（判据就在 `InstallScreen.kt` 里那一小段 `when`）。
 *
 * ⚠️ 为什么会有"内核读不出来"：`STEALTH_GET` 是 `only_manager` 命令，本 App
 *    **没被内核认主**时（重装/换 appid/待重启）会被拒；App 还有一条带世代号的安全阀命令
 *    （JNI `stealth_get` 里的 `KSU_IOCTL_STEALTH_GET_G`）也会一起失败 ——
 *    真机上就是这种情况（logcat：`no driver fd` + `回前台读隐身状态失败(state=-1)`）。
 *    此时磁盘上的标志是**唯一**还读得到的真相：内核 POST_FS_DATA 时就是按它加载的
 *    （`kernel/manager/stealth.c::ksu_stealth_load`）。
 *
 * 契约：明确读到 `'1'` → true；明确读到 `'0'` → false；**其余一律 null**
 * （没 root 读不到 / 文件不在 / 输出不认识）—— 调用方对 null 按"没法证明没开"处理，
 * 但**这个函数本身不替调用方做安全决定**。
 */
internal suspend fun getPersistedStealthFlag(): Boolean? = withContext(Dispatchers.IO) {
    runCatching {
        // P1 修复：flag 路径随模式变化（B=/data/adb/ksu/stealth，A=/data/adb/sevenk/stealth）。
        // B 优先 ksu，读不到再试 sevenk 只读兼容；A/未判定保持 sevenk 优先（现状不变）。
        val mode = dev.maplesu.manager.workmode.ModeRuntime.active
        for (path in com.maplesu.core.ui.security.StealthPathPolicy.flagPaths(mode)) {
            val out = ShellUtils.fastCmd(getRootShell(), "cat $path 2>/dev/null")
            when (out.trim().firstOrNull()) {
                '1' -> return@runCatching true
                '0' -> return@runCatching false
                else -> Unit
            }
        }
        null
    }.getOrElse { e ->
        Log.w(TAG, "读持久化隐身标志失败（返回 null，交给上层按保守方向处理）: ${e.javaClass.simpleName}")
        null
    }
}

suspend fun getSupportedKmis(): List<String> = withContext(Dispatchers.IO) {
    val shell = getRootShell()
    val cmd = "boot-info supported-kmis"
    val out = shell.newJob().add("${getKsuDaemonPath()} $cmd").to(ArrayList(), null).exec().out
    out.filter { it.isNotBlank() }.map { it.trim() }
}

/**
 * 本次 APK 内嵌的 KMI 目录（`ksud boot-info supported-kmis` 的同步版）。
 *
 * `null` = 读不到 / ksud 未部署（**不等于"全都有"**）：分派把 Normal 的 `kmiCatalogKnown`
 * 标 false，产物是否存在最终由 ksud 的内嵌资产查找硬失败兜底（绝不换用别的 KMI）。
 *
 * 普通 UID 只读取 ksud 自带资产索引；安装页的 Normal 文件 KMI 选择复用同一目录。
 */
internal fun packagedKmiCatalog(context: Context = ksuApp): List<String>? = runCatching {
    val daemon = File(context.applicationInfo.nativeLibraryDir, "libksud_ksu.so")
    val result = dev.maplesu.manager.runtime.ultra.ProcessSuExec.exec(
        listOf(daemon.path, "boot-info", "supported-kmis"), 10_000, null)
    if (!result.ok) return@runCatching null
    result.output.lines().map(String::trim).filter(String::isNotEmpty).distinct().sorted()
}.getOrNull()?.takeIf { it.isNotEmpty() }

/**
 * 设备内核 release（等价 `uname -r`）：Normal（KSU/LKM）的 KMI 判定输入。
 *
 * 读不到 → null：Normal 会按「KMI 无法判定」fail-closed（**不猜**，也不回退 Ultra）。
 */
private fun deviceKernelRelease(): String? =
    runCatching { Os.uname().release }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }

suspend fun isAbDevice(): Boolean = withContext(Dispatchers.IO) {
    val shell = getRootShell()
    val cmd = "boot-info is-ab-device"
    ShellUtils.fastCmd(shell, "${getKsuDaemonPath()} $cmd").trim().toBoolean()
}

suspend fun getDefaultPartition(): String = withContext(Dispatchers.IO) {
    val shell = getRootShell()
    if (shell.isRoot) {
        val cmd = "boot-info default-partition"
        ShellUtils.fastCmd(shell, "${getKsuDaemonPath()} $cmd").trim()
    } else {
        if (!Os.uname().release.contains("android12-")) "init_boot" else "boot"
    }
}

suspend fun getSlotSuffix(ota: Boolean): String = withContext(Dispatchers.IO) {
    val shell = getRootShell()
    val cmd = if (ota) {
        "boot-info slot-suffix --ota"
    } else {
        "boot-info slot-suffix"
    }
    ShellUtils.fastCmd(shell, "${getKsuDaemonPath()} $cmd").trim()
}

suspend fun getAvailablePartitions(): List<String> = withContext(Dispatchers.IO) {
    val shell = getRootShell()
    val cmd = "boot-info available-partitions"
    val out = shell.newJob().add("${getKsuDaemonPath()} $cmd").to(ArrayList(), null).exec().out
    out.filter { it.isNotBlank() }.map { it.trim() }
}

// 已知的厂商反 root 内核模块 —— `--block-module` 就是为它们准备的。
private val KNOWN_BLOCKABLE_MODULES = listOf(
    "vr", "vklp", "oplus_secure_guard", "oplus_secure_guard_new", "mkp"
)

// 返回本机 /proc/modules 里实际加载着的已知反 root 模块，
// 安装页据此预填黑名单（只预填真实存在的那些）。
suspend fun getLoadedBlockableModules(): List<String> = withContext(Dispatchers.IO) {
    val shell = getRootShell()
    val out = shell.newJob().add("cat /proc/modules").to(ArrayList(), null).exec().out
    val loaded = out.mapNotNull { it.trim().substringBefore(' ').takeIf(String::isNotBlank) }.toHashSet()
    KNOWN_BLOCKABLE_MODULES.filter { it in loaded }
}

fun hasMagisk(): Boolean = runCatching {
    val shell = getRootShell(true)
    // `command -v` also reports where it resolved to, which is what lets us exclude
    // MapleSU's own compatibility shim below.
    val result = shell.newJob().add("command -v magisk").exec()
    val resolved = result.out.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    // MapleSU publishes a `magisk` shim for LSPosed installers (BINARY_DIR/compat).
    // Its own shim must never be read as "another root implementation": that is what
    // showed "因与 Magisk 有冲突，所有模块不可用" on a device that only runs MapleSU.
    val otherRoot = resolved.isNotEmpty() && !resolved.startsWith("/data/adb/maplesu/")
    Log.i(TAG, "has magisk: $otherRoot ($resolved)")
    otherRoot
}.getOrElse {
    // This runs in a viewModelScope coroutine with no other guard, and the root shell
    // can die between acquisition and exec; an exception here used to take the whole
    // Manager down while opening the module page.
    Log.w(TAG, "has magisk probe failed; treating Magisk as absent", it)
    false
}

fun isSepolicyValid(rules: String?): Boolean {
    if (rules == null) {
        return true
    }
    val shell = getRootShell()
    val result =
        shell.newJob().add("${getKsuDaemonPath()} sepolicy check ${rules.shellQuote()}").to(ArrayList(), null)
            .exec()
    return result.isSuccess
}

fun getSepolicy(pkg: String): String {
    val shell = getRootShell()
    val result =
        shell.newJob().add("${getKsuDaemonPath()} profile get-sepolicy ${pkg.shellQuote()}").to(ArrayList(), null)
            .exec()
    Log.i(TAG, "code: ${result.code}, out: ${result.out}, err: ${result.err}")
    return result.out.joinToString("\n")
}

fun setSepolicy(pkg: String, rules: String): Boolean {
    val shell = getRootShell()
    val result = shell.newJob().add("${getKsuDaemonPath()} profile set-sepolicy ${pkg.shellQuote()} ${rules.shellQuote()}")
        .to(ArrayList(), null).exec()
    Log.i(TAG, "set sepolicy result: ${result.code}")
    return result.isSuccess
}

fun listAppProfileTemplates(): List<String> {
    val shell = getRootShell()
    return shell.newJob().add("${getKsuDaemonPath()} profile list-templates").to(ArrayList(), null)
        .exec().out
}

fun getAppProfileTemplate(id: String): String {
    val shell = getRootShell()
    return shell.newJob().add("${getKsuDaemonPath()} profile get-template ${id.shellQuote()}")
        .to(ArrayList(), null).exec().out.joinToString("\n")
}

fun setAppProfileTemplate(id: String, template: String): Boolean {
    val shell = getRootShell()
    val cmd = """${getKsuDaemonPath()} profile set-template ${id.shellQuote()} ${template.shellQuote()}"""
    return shell.newJob().add(cmd)
        .to(ArrayList(), null).exec().isSuccess
}

fun deleteAppProfileTemplate(id: String): Boolean {
    val shell = getRootShell()
    return shell.newJob().add("${getKsuDaemonPath()} profile delete-template ${id.shellQuote()}")
        .to(ArrayList(), null).exec().isSuccess
}

fun forceStopApp(packageName: String, userId: Int? = null) {
    // Ultra 环境：`am force-stop` 只能由 root/ADB 执行，而这里每一条 root 命令都会落到
    // `libmaplesu_runtime`（Ultra 内核上会重启系统）。所以不做，只留一行日志 ——
    // 与应用里其它 Ultra 分支一致：宁可少一个便利功能，也不碰那条会重启系统的路径。
    if (isUltraEnvironmentRunning()) {
        Log.i(TAG, "force stop $packageName skipped: Ultra environment has no root shell")
        return
    }
    val shell = getRootShell()
    val userArg = userId?.let { " --user $it" } ?: ""
    val result = shell.newJob().add("am force-stop$userArg $packageName").exec()
    Log.i(TAG, "force stop $packageName result: $result")
}

fun launchApp(packageName: String, userId: Int? = null) {
    // 🟢 Ultra 环境：拉起应用**不需要 root**，用 PackageManager 的启动 Intent 就够了。
    //    这保住了既有 UI 的「启动应用」便利入口，也顺带让刚授权的应用立刻重新发起
    //    su 请求（Ultra 的授权是内核在请求时按 su_auth 白名单判定的）。
    if (isUltraEnvironmentRunning()) {
        val started = runCatching {
            val intent = ksuApp.packageManager.getLaunchIntentForPackage(packageName)
                ?: return@runCatching false
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            ksuApp.startActivity(intent)
            true
        }.getOrDefault(false)
        Log.i(TAG, "launch $packageName via PackageManager started=$started (Ultra)")
        return
    }
    val shell = getRootShell()
    val userArg = userId?.let { " --user $it" } ?: ""
    val result =
        shell.newJob()
            .add("cmd package resolve-activity --brief$userArg $packageName | tail -n 1 | xargs cmd activity start-activity$userArg -n")
            .exec()
    Log.i(TAG, "launch $packageName result: $result")
}

/** Cache-only Ultra check; shared by the Ultra fallbacks in this file (never starts a process). */
private fun isUltraEnvironmentRunning(): Boolean =
    runCatching { dev.maplesu.manager.runtime.ultra.UltraEnvironmentProbe.usesUltraBackend(ksuApp) }.getOrDefault(false)

fun restartApp(packageName: String, userId: Int? = null) {
    forceStopApp(packageName, userId)
    launchApp(packageName, userId)
}
