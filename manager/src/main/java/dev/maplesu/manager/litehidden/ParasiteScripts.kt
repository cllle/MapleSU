package dev.maplesu.manager.litehidden

import java.io.File

/**
 * 寄生后端（隐藏模式生产实现）的**纯策略 + 纯脚本**层：不碰 Android API、不执行 root，
 * 只把「宿主能不能寄生」「每个 root 步骤长什么样」「root 输出怎样判定成功」写成可测函数。
 *
 * 设计约束（来自 R&D spike `EVIDENCE.md` 的实测坑，逐条落成断言）：
 * - `extractNativeLibs` 是硬前提：现代 App 默认不在磁盘上留 so，没有可改写目标；
 * - SELinux label 取**目录自身**（`ls -Zd "$LIB"`），不是取文件列（取错列会 chcon 成空串 → 宿主崩溃）；
 * - 不用 `pkill -f`：`su -c <script>` 会把整段脚本放进 su 进程 argv，pattern 会匹配到自己；
 *   幂等靠 pid 文件，就绪靠真实取页；
 * - su 用 `/system/bin/su`（app 域可执行），先 `addSuAuth` 再要 root。
 *
 * 所有脚本都是单条 `sh` 字符串，由**既有** root 通道执行（A：Ultra `run_root_cmd`；
 * B：`RootExecutionRouter`），本层不新开 libsu/su 进程。
 */
object ParasitePaths {
    const val DATA_DIR = "/data/adb/maplesu"
    const val RECOVERY = "$DATA_DIR/recovery"
    const val HOST_BACKUP_ROOT = "$RECOVERY/host"
    const val CLEANUP_CHECKPOINT = "$RECOVERY/parasite-cleanup"
    const val APK = "$RECOVERY/manager.apk"
    const val WEBUI_CONFIG = "$RECOVERY/webui.conf"
    const val START_SCRIPT = "$RECOVERY/parasite-start.sh"
    const val PID_FILE = "$RECOVERY/liteweb.pid"
    const val HOST_CHALLENGE = "$RECOVERY/host-start.challenge"
    const val HOST_PROOF = "$RECOVERY/host-start.proof"
    const val LOADER_NAME = "libmaplesu_parasite.so"
    const val MANIFEST_NAME = "host.manifest"

    /**
     * 备份**完整性标记**：只有全部目标 `.orig` 都写完并逐个 sha 复核通过后才落这个文件。
     * 没有它（只有半截 `.orig`）就说明备份中途失败、从未用于注入 —— 这种目录绝不能
     * 当作可用备份去「恢复」，否则会把旧库写回一个根本没被改过的宿主。
     */
    const val BACKUP_MARKER_NAME = "BACKUP_COMPLETE"
    const val LIBDIR_FILE = "libdir.txt"
    const val LABEL_FILE = "label.txt"

    /**
     * 撤销 su 授权所需的**身份材料**（App 作用域 `ANDROID_ID` + 包名 + uid）。
     *
     * 为什么必须落盘：App 侧用的是**应用作用域**的 `Settings.Secure.ANDROID_ID`（Android 8+ 每个
     * 应用不同），daemon 用 `settings get secure android_id` 取到的是**系统值**，两者不等 ⇒
     * rootkey 的 AES-GCM tag 校验失败、撤销步永远 409（2026-10-31 真机 D1）。这里只存
     * **身份材料**，绝不存 rootKey 明文/token；解密仍在 daemon 内存里做。
     */
    const val IDENTITY_FILE = "$RECOVERY/identity.json"
    const val IDENTITY_TEMP = "$IDENTITY_FILE.tmp"

    /** 陈旧备份的隔离根：`recovery/host-quarantine-<yyyyMMdd>/`（**只改名，不删除**）。 */
    fun quarantineDir(stamp: String): String = "$RECOVERY/host-quarantine-$stamp"
    const val DAEMON_CLASS = "dev.maplesu.manager.liteweb.daemon.LiteWebDaemonMain"
    const val PATCH_MAIN = "dev.maplesu.manager.litehidden.HostSoPatchMain"
    const val WEBUI_PATH_PREFIX = "x7k9f"
    const val DAEMON_NICE_NAME = "maplesu-liteweb"

    /** 备份目录：`/data/adb/maplesu/recovery/host/<包名>/`（包名已通过白名单正则）。 */
    fun hostBackupDir(packageName: String): String = "$HOST_BACKUP_ROOT/$packageName"

    fun manifestPath(packageName: String): String = "${hostBackupDir(packageName)}/$MANIFEST_NAME"

    fun backupMarkerPath(packageName: String): String = "${hostBackupDir(packageName)}/$BACKUP_MARKER_NAME"

    fun libDirFile(packageName: String): String = "${hostBackupDir(packageName)}/$LIBDIR_FILE"

    fun labelFile(packageName: String): String = "${hostBackupDir(packageName)}/$LABEL_FILE"

    fun loaderPath(libraryDir: String): String = "$libraryDir/$LOADER_NAME"

    /** 恢复链接（token 只在内存；URL 形状与 `HiddenModePolicy.webUiLinkPreview` 一致）。 */
    fun webUiUrl(port: Int, token: String): String =
        "http://127.0.0.1:$port/$WEBUI_PATH_PREFIX/$token/"
}

/** 后端结构化失败码（沿用既有 wire 语义；新增码只加在后面，不改旧码）。 */
object ParasiteCodes {
    const val BUSY = "PARASITE_BUSY"
    const val HOST_UNSUPPORTED = "HOST_UNSUPPORTED"
    const val HOST_SCAN_FAILED = "HOST_SCAN_FAILED"
    const val HOST_BACKUP_STALE = "HOST_BACKUP_STALE"
    const val HOST_BACKUP_FAILED = "HOST_BACKUP_FAILED"
    const val HOST_SO_PATCH_FAILED = "HOST_SO_PATCH_FAILED"
    const val HOST_SO_RESTORE_FAILED = "HOST_SO_RESTORE_FAILED"
    const val SU_AUTH_FAILED = "SU_AUTH_FAILED"
    const val SU_AUTH_REVOKE_FAILED = "SU_AUTH_REVOKE_FAILED"
    const val RECOVERY_PREPARE_FAILED = "RECOVERY_PREPARE_FAILED"
    const val RECOVERY_VERIFY_FAILED = "RECOVERY_VERIFY_FAILED"
    const val DAEMON_START_FAILED = "LITEWEB_DAEMON_START_FAILED"
    const val DAEMON_PING_FAILED = "LITEWEB_DAEMON_PING_FAILED"
    const val DAEMON_STOP_FAILED = "LITEWEB_DAEMON_STOP_FAILED"
    const val DAEMON_ALREADY_RUNNING_ELSEWHERE = "LITEWEB_PORT_OCCUPIED_BY_OTHER"
    const val HIDDEN_STATE_WRITE_FAILED = "LITE_HIDDEN_WRITE_FAILED"
    const val HIDDEN_STATE_READBACK_FAILED = "LITE_HIDDEN_READBACK_FAILED"
    const val VERIFY_FAILED = "LITE_HIDDEN_VERIFY_FAILED"
    const val HOST_START_UNVERIFIED = "HOST_START_UNVERIFIED"
    const val ROLLBACK_INCOMPLETE = "ACTIVATION_ROLLBACK_INCOMPLETE"
    const val HOST_BACKUP_CLEANUP_FAILED = "HOST_BACKUP_CLEANUP_FAILED"
    const val IDENTITY_CLEANUP_FAILED = "RECOVERY_IDENTITY_CLEANUP_FAILED"
    const val CLEANUP_CHECKPOINT_FAILED = "RECOVERY_CLEANUP_CHECKPOINT_FAILED"
}

/** 脚本输出标记（后端只认这些标记，不从人类文案里猜成功）。 */
object ParasiteMarkers {
    const val FAIL = "PARASITE_FAIL"
    const val LABEL = "PARASITE_LABEL="
    const val BACKUP = "PARASITE_BACKUP "
    const val BACKUP_OK = "PARASITE_BACKUP_OK"
    const val PATCHED = "PARASITE_PATCHED "
    const val SKIP = "PARASITE_SKIP "
    const val LOADER = "PARASITE_LOADER "
    const val OK = "PARASITE_OK"
    const val RESTORED = "PARASITE_RESTORED "
    const val RESTORE_SKIP = "PARASITE_RESTORE_SKIP "
    const val RESTORE_OK = "PARASITE_RESTORE_OK"
    const val BACKUP_GONE = "PARASITE_BACKUP_GONE"
    const val BACKUP_RETIRED = "PARASITE_BACKUP_RETIRED"
    const val CLEANUP_NONE = "PARASITE_CLEANUP_NONE"
    const val CLEANUP_READY = "PARASITE_CLEANUP_READY"
    const val CLEANUP_GONE = "PARASITE_CLEANUP_GONE"
    const val VERIFY = "PARASITE_VERIFY "
    const val VERIFY_OK = "PARASITE_VERIFY_OK"
    const val HOST_STARTED = "PARASITE_HOST_STARTED"
    const val STARTED = "PARASITE_STARTED "
    const val ALIVE = "PARASITE_ALIVE "
    const val STOPPED = "PARASITE_STOPPED"
    const val LOADER_GONE = "PARASITE_LOADER_GONE"
    const val LITE_OK = "LITE_OK"

    /** 身份材料写入自证（写完必须回读 `600 0` 才打这个标记）。 */
    const val IDENTITY_OK = "MAPLESU_IDENTITY_OK"
    const val IDENTITY_GONE = "MAPLESU_IDENTITY_GONE"

    /** 备份目录事实行前缀：`PARASITE_BK|name|marker|manifestBytes|manifestEntries|origCount`。 */
    const val BACKUP_FACT = "PARASITE_BK|"

    /** 陈旧残留隔离自证：`PARASITE_QUARANTINED n=<数量>;dir=<目录>`。 */
    const val QUARANTINED = "PARASITE_QUARANTINED"
}

/** 宿主 native 目录里的一个 `.so`（只有名字与大小，不读内容）。 */
data class HostLibFile(val name: String, val sizeBytes: Long)

/** PackageManager 读到的宿主原始事实（判定全部在 [ParasitePolicy]）。 */
data class HostFacts(
    val packageName: String,
    val isSystem: Boolean,
    val abis: List<String>,
    val nativeLibraryDir: String,
    val extractNativeLibs: Boolean,
    val libFiles: List<HostLibFile>,
    val uid: Int = -1,
)

/** 宿主事实查询结果（区分「没装」与「查失败」，两者文案/失败码不同）。 */
sealed interface HostFactsLookup {
    data class Ok(val facts: HostFacts) : HostFactsLookup

    data class Missing(val packageName: String) : HostFactsLookup

    data class Failed(val detail: String) : HostFactsLookup
}

/** 宿主事实来源（生产 = PackageManager；主机测试 = fake）。 */
fun interface HostFactsSource {
    fun load(packageName: String): HostFactsLookup
}

/** 宿主校验结论：通过时带回**已排序、已过滤**的注入目标列表。 */
sealed interface HostValidation {
    data class Ok(val facts: HostFacts, val targets: List<String>) : HostValidation

    data class Rejected(val code: String, val detail: String) : HostValidation
}

/**
 * 宿主校验 + 注入目标选择（纯函数）。
 *
 * 校验顺序固定（先命中先返回）：包名合法 → 非 MapleSU 自身 → 非系统应用 → arm64 →
 * `extractNativeLibs=true` → native 目录里确实有可改写的 `.so`。任一不满足 → `HOST_UNSUPPORTED`
 * 并在 detail 里给出**具体原因**（不给用户看机器码，但诊断/日志/测试要能区分）。
 *
 * 复用 [HiddenHostPolicy] 已锁定的排除规则（包名/自身/系统/arm64/native 库），
 * 只**追加**本后端特有的 `extractNativeLibs` 硬闸门（spike §10.1 的决定性限制）。
 */
object ParasitePolicy {

    /**
     * 一次激活最多改写的 `.so` 数量。
     *
     * 2026-10-31 真机：真实 App 的 `lib/arm64` 常见 20–48 个 `.so`（`123云盘` = 48），
     * 旧的 16 上限会让这类宿主直接 `HOST_UNSUPPORTED too_many_libs`，等于寄生不可用。
     * 上限提高到 64（仍防异常 App）；注入按 [ParasitePolicy.INJECT_CHUNK] 分批执行，
     * 避免单条 root 命令过长/超时。
     */
    const val MAX_TARGETS = 64

    /** 每条 root 命令处理的库数量（分批注入；清单增量合并）。 */
    const val INJECT_CHUNK = 8

    /** 每条 root 命令备份的库数量（48 个库的宿主单条命令过长/过慢）。 */
    const val BACKUP_CHUNK = 16

    private val LIB_NAME = Regex("^[A-Za-z0-9._-]{1,120}\\.so$")

    fun isSafeLibName(name: String): Boolean = LIB_NAME.matches(name)

    /**
     * 「设备路径必须是绝对路径」的唯一判定，供宿主校验 / 清单完整性 / 注入执行端共用。
     *
     * 设备侧是 POSIX（`/data/app/...`）：Linux 上 `File.isAbsolute` 与 `startsWith("/")`
     * 完全等价，所以安全语义不变。之所以还要看 [File.isAbsolute]，是因为同一份策略代码
     * 要在主机单测里跑，而 Windows 临时目录是 `D:\...` 形式的绝对路径 —— 只认 `/` 会把
     * 合法路径误判成 `BAD_ARGS`。相对路径（含 `C:foo` 这种 drive-relative）仍然一律拒绝。
     */
    fun isAbsolutePath(path: String): Boolean = path.startsWith("/") || File(path).isAbsolute

    fun validate(
        facts: HostFacts,
        selfPackages: Set<String> = HiddenHostPolicy.SELF_PACKAGES,
        maxTargets: Int = MAX_TARGETS,
    ): HostValidation {
        val packageName = facts.packageName.trim()
        val policyFacts = HostAppFacts(
            packageName = packageName,
            isSystem = facts.isSystem,
            abis = facts.abis,
            hasNativeLib = facts.libFiles.any { isSafeLibName(it.name) },
        )
        when (val decision = HiddenHostPolicy.evaluate(policyFacts, selfPackages)) {
            is HostDecision.Rejected -> return HostValidation.Rejected(
                ParasiteCodes.HOST_UNSUPPORTED,
                "reject=${decision.reason.wire}",
            )
            is HostDecision.Eligible -> Unit
        }
        if (!facts.extractNativeLibs) {
            // 决定性限制：so 还在 APK 里按页映射，磁盘上没有可改写目标。
            return HostValidation.Rejected(ParasiteCodes.HOST_UNSUPPORTED, "extractNativeLibs=false")
        }
        val libraryDir = facts.nativeLibraryDir.trim()
        if (libraryDir.isEmpty() || !ParasitePolicy.isAbsolutePath(libraryDir)) {
            return HostValidation.Rejected(ParasiteCodes.HOST_UNSUPPORTED, "nativeLibraryDir")
        }
        val candidates = facts.libFiles
            .filter { isSafeLibName(it.name) }
            .map { it.name }
            .distinct()
            .sorted()
        if (candidates.isEmpty()) {
            return HostValidation.Rejected(ParasiteCodes.HOST_UNSUPPORTED, "no_lib")
        }
        if (candidates.size > maxTargets) {
            return HostValidation.Rejected(ParasiteCodes.HOST_UNSUPPORTED, "too_many_libs=${candidates.size}")
        }
        return HostValidation.Ok(facts.copy(packageName = packageName, nativeLibraryDir = libraryDir), candidates)
    }

    /** 注入脚本里的目标串（`name1:name2`；名字已过滤，不会带出分隔符）。 */
    fun targetsArgument(targets: List<String>): String = targets.joinToString(":")

    fun parseTargets(argument: String): List<String> =
        argument.split(':').map { it.trim() }.filter { isSafeLibName(it) }.distinct()
}

/** 一个被改写过的宿主库：回滚材料（sha 级复原）。 */
data class HostBackupFile(
    val name: String,
    val originalSha256: String,
    val patchedSha256: String,
    val mode: String,
    val uid: Int,
    val gid: Int,
)

/** 宿主备份清单：回滚/复核的唯一真相（root 写、root 读、App 只解析）。 */
data class HostManifest(
    val packageName: String,
    val libraryDir: String,
    val label: String,
    val loaderName: String,
    val loaderSha256: String,
    val files: List<HostBackupFile>,
) {
    val isComplete: Boolean
        get() = packageName.isNotBlank() &&
            ParasitePolicy.isAbsolutePath(libraryDir) &&
            loaderName.isNotBlank() &&
            loaderSha256.length == 64 &&
            files.isNotEmpty() &&
            files.all { it.originalSha256.length == 64 && it.patchedSha256.length == 64 }
}

/**
 * 清单编解码（key=value 行，`file=` 行用 `|` 分隔）。
 * 解析**严格**：schema 不认、字段缺失、sha 形状不对 → null（调用方按失败处理，不猜）。
 * 故意不用 JSON：root 侧只需 `cat`/`printf`，daemon/App 侧解析也不需要额外依赖。
 */
object HostManifestCodec {
    const val SCHEMA = 1

    fun encode(manifest: HostManifest): String = buildString {
        appendLine("schema=$SCHEMA")
        appendLine("package=${manifest.packageName}")
        appendLine("libdir=${manifest.libraryDir}")
        appendLine("label=${manifest.label}")
        appendLine("loader=${manifest.loaderName}")
        appendLine("loader_sha=${manifest.loaderSha256}")
        manifest.files.forEach { file ->
            appendLine(
                "file=${file.name}|${file.originalSha256}|${file.patchedSha256}|" +
                    "${file.mode}|${file.uid}|${file.gid}",
            )
        }
    }

    fun decode(text: String?): HostManifest? {
        val lines = text.orEmpty().lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        val values = linkedMapOf<String, String>()
        val files = mutableListOf<HostBackupFile>()
        for (line in lines) {
            val index = line.indexOf('=')
            if (index <= 0) return null
            val key = line.substring(0, index)
            val value = line.substring(index + 1)
            if (key == "file") {
                val parts = value.split('|')
                if (parts.size != 6) return null
                val uid = parts[4].toIntOrNull() ?: return null
                val gid = parts[5].toIntOrNull() ?: return null
                if (!ParasitePolicy.isSafeLibName(parts[0])) return null
                if (parts[1].length != 64 || parts[2].length != 64) return null
                if (!parts[3].matches(Regex("^[0-7]{3,4}$"))) return null
                files += HostBackupFile(parts[0], parts[1], parts[2], parts[3], uid, gid)
            } else {
                if (key == "schema") {
                    if (value.toIntOrNull() != SCHEMA) return null
                } else if (key !in KNOWN_KEYS) {
                    return null
                }
                values[key] = value
            }
        }
        if (values["schema"] != SCHEMA.toString()) return null
        val manifest = HostManifest(
            packageName = values["package"].orEmpty(),
            libraryDir = values["libdir"].orEmpty(),
            label = values["label"].orEmpty(),
            loaderName = values["loader"].orEmpty(),
            loaderSha256 = values["loader_sha"].orEmpty(),
            files = files.distinctBy { it.name }.sortedBy { it.name },
        )
        return manifest.takeIf { it.isComplete }
    }

    private val KNOWN_KEYS = setOf("package", "libdir", "label", "loader", "loader_sha")
}

/**
 * root 脚本生成器（纯字符串；每条都可单独在设备上重放）。
 *
 * 全部脚本都遵守两条铁律：**失败立刻打 `PARASITE_FAIL code=` 并非 0 退出**；
 * 成功只靠标记行判定（不回显路径/凭据）。
 */
object ParasiteScripts {

    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    /**
     * `sha256sum <pathExpr> 2>/dev/null | awk '{print $1}'`。
     *
     * [pathExpr] 必须是一段**已经带好引号**的 shell 表达式（本文件里全是 `"$S"` /
     * `"$D/$F.orig"` 这种形式）。这里**绝对不能再包一层引号**：`quote()` 用的是单引号，
     * 单引号里的 `$S` 不会展开，`sha256sum '"$S"'` 会去找一个字面名叫 `$S` 的文件、
     * 拿到空串 —— 真机上表现为 backup 永远 `BACKUP_SHA` 失败（只留下第一个 `.orig`），
     * restore/verify 同样全假失败。2026-10-31 真机取证确认，见
     * `.analysis/parasite-prod-check/`。
     */
    private fun hashLine(pathExpr: String): String = "sha256sum $pathExpr 2>/dev/null | awk '{print \$1}'"

    /** root 通道自检：真的以 uid 0 执行（不是「后端对象存在」就算有 root）。 */
    fun probeRoot(): String = "id -u 2>/dev/null || echo ROOT_PROBE_FAILED"

    /**
     * 上次激活是否留下**完整**备份（有清单或完成标记 → 不覆盖、不继续，先要求解除寄生）。
     *
     * 只有半截 `.orig`（备份中途失败、没有完成标记）→ 直接清掉再继续：那批字节从未用于
     * 注入，留着只会在以后「解除寄生」时把旧库写回宿主（比失败更糟）。
     */
    fun backupGuard(packageName: String): String {
        val dir = quote(ParasitePaths.hostBackupDir(packageName))
        val manifest = quote(ParasitePaths.manifestPath(packageName))
        val marker = quote(ParasitePaths.backupMarkerPath(packageName))
        return buildString {
            appendLine("set -u")
            appendLine("D=$dir")
            // Identity is shared: no host may activate while a previous cleanup owns it.
            val cleanup = quote(ParasitePaths.CLEANUP_CHECKPOINT)
            appendLine("if [ -e $cleanup ] || [ -L $cleanup ]; then echo PARASITE_STALE_BACKUP; exit 0; fi")
            appendLine("if [ -f $manifest ] || [ -f $marker ]; then echo PARASITE_STALE_BACKUP; exit 0; fi")
            appendLine("rm -rf \"\$D\" 2>/dev/null")
            appendLine("echo PARASITE_CLEAN")
        }
    }

    /**
     * 备份宿主原 so。
     *
     * 先写 `libdir.txt`/`label.txt`（回滚只需要这两个 + `*.orig`，不依赖 App 内存/JSON），
     * 再逐文件 `cp -p`（保留 mode/uid/gid）并双向 sha 校验。label 取**目录自身**。
     */
    fun backup(facts: HostFacts, targets: List<String>, complete: Boolean = true): String {
        val dir = ParasitePaths.hostBackupDir(facts.packageName)
        val lib = facts.nativeLibraryDir
        return buildString {
            appendLine("set -u")
            appendLine("D=${quote(dir)}")
            appendLine("L=${quote(lib)}")
            // The first KSU backup can precede recovery setup by staging/daemon startup.
            appendLine("R=${quote(ParasitePaths.RECOVERY)}")
            appendLine("if [ ! -e \"\$R\" ] && [ ! -L \"\$R\" ]; then mkdir -p \"\$R\" || { echo ${ParasiteMarkers.FAIL} code=BACKUP_RECOVERY_MKDIR; exit 20; }; fi")
            appendLine("[ -d \"\$R\" ] && [ ! -L \"\$R\" ] && [ \"\$(stat -c '%u' \"\$R\" 2>/dev/null)\" = 0 ] || { echo ${ParasiteMarkers.FAIL} code=BACKUP_RECOVERY_UNTRUSTED; exit 20; }")
            appendLine("chmod 700 \"\$R\" && [ ! -L \"\$R\" ] && [ \"\$(stat -c '%a %u' \"\$R\" 2>/dev/null)\" = '700 0' ] || { echo ${ParasiteMarkers.FAIL} code=BACKUP_RECOVERY_UNTRUSTED; exit 20; }")
            appendLine("mkdir -p \"\$D\" || { echo ${ParasiteMarkers.FAIL} code=BACKUP_MKDIR; exit 20; }")
            appendLine("chmod 700 \"\$D\" 2>/dev/null")
            appendLine("printf '%s' \"\$L\" > \"\$D/${ParasitePaths.LIBDIR_FILE}\" || { echo ${ParasiteMarkers.FAIL} code=BACKUP_LIBDIR; exit 21; }")
            appendLine("LBL=\$(ls -Zd \"\$L\" 2>/dev/null | awk '{print \$1}')")
            appendLine("case \"\$LBL\" in *:*:*) : ;; *) LBL=\"\" ;; esac")
            appendLine("printf '%s' \"\$LBL\" > \"\$D/${ParasitePaths.LABEL_FILE}\" 2>/dev/null")
            appendLine("echo \"${ParasiteMarkers.LABEL}\$LBL\"")
            targets.forEach { name ->
                appendLine("F=${quote(name)}")
                appendLine("S=\"\$L/\$F\"")
                appendLine("[ -f \"\$S\" ] || { echo ${ParasiteMarkers.FAIL} code=BACKUP_MISSING detail=\"\$F\"; exit 22; }")
                appendLine("cp -p \"\$S\" \"\$D/\$F.orig\" || { echo ${ParasiteMarkers.FAIL} code=BACKUP_COPY detail=\"\$F\"; exit 23; }")
                appendLine("A=\$(${hashLine("\"\$S\"")})")
                appendLine("B=\$(${hashLine("\"\$D/\$F.orig\"")})")
                appendLine("[ -n \"\$A\" ] && [ \"\$A\" = \"\$B\" ] || { echo ${ParasiteMarkers.FAIL} code=BACKUP_SHA detail=\"\$F\"; exit 24; }")
                appendLine("M=\$(stat -c '%a' \"\$S\" 2>/dev/null); U=\$(stat -c '%u' \"\$S\" 2>/dev/null); G=\$(stat -c '%g' \"\$S\" 2>/dev/null)")
                appendLine("echo \"${ParasiteMarkers.BACKUP}name=\$F sha=\$A mode=\$M uid=\$U gid=\$G\"")
            }
            // 完成标记：全部目标都写完并逐个复核通过才算「可复原的备份」。
            // 分批备份时由 [markBackupComplete] 在最后一批之后单独落标记（complete=false）。
            if (complete) {
                appendLine("MK=${quote(ParasitePaths.backupMarkerPath(facts.packageName))}")
                appendLine(": > \"\$MK\" || { echo ${ParasiteMarkers.FAIL} code=BACKUP_MARKER; exit 25; }")
            }
            appendLine("echo ${ParasiteMarkers.BACKUP_OK}")
        }
    }

    /** 分批备份全部成功后落「完成标记」（没有它，恢复流程会拒绝使用这份备份）。 */
    fun markBackupComplete(packageName: String): String = buildString {
        appendLine("set -u")
        appendLine("MK=${quote(ParasitePaths.backupMarkerPath(packageName))}")
        appendLine(": > \"\$MK\" || { echo ${ParasiteMarkers.FAIL} code=BACKUP_MARKER; exit 25; }")
        appendLine("echo ${ParasiteMarkers.BACKUP_OK}")
    }

    /**
     * 注入：以 root 起 `app_process` 跑 [HostSoPatchMain]（纯 JDK，只做 ELF 改写 + 元数据），
     * 由它把补丁写到宿主 lib 目录并原子换入。`exec` 让退出码就是 app_process 的退出码。
     */
    fun inject(
        facts: HostFacts,
        targets: List<String>,
        managerApkPath: String,
        loaderSourcePath: String,
        label: String,
    ): String {
        val dir = ParasitePaths.hostBackupDir(facts.packageName)
        return buildString {
            appendLine("set -u")
            appendLine("APK=${quote(managerApkPath)}")
            appendLine("SRC=${quote(loaderSourcePath)}")
            appendLine("[ -f \"\$APK\" ] || { echo ${ParasiteMarkers.FAIL} code=PATCH_APK_MISSING; exit 30; }")
            appendLine("[ -f \"\$SRC\" ] || { echo ${ParasiteMarkers.FAIL} code=PATCH_LOADER_MISSING; exit 31; }")
            append("CLASSPATH=\"\$APK\" exec /system/bin/app_process /system/bin ")
            append(ParasitePaths.PATCH_MAIN)
            append(" --libdir ").append(quote(facts.nativeLibraryDir))
            append(" --backup ").append(quote(dir))
            append(" --loader-src \"\$SRC\"")
            append(" --loader-name ").append(quote(ParasitePaths.LOADER_NAME))
            append(" --label ").append(quote(label))
            append(" --manifest ").append(quote(ParasitePaths.manifestPath(facts.packageName)))
            append(" --package ").append(quote(facts.packageName))
            append(" --files ").append(quote(ParasitePolicy.targetsArgument(targets)))
            appendLine()
        }
    }

    /**
     * 回滚/解除：按备份目录里的 `*.orig` 逐个复原（**不依赖清单**，清单缺失也能复原），
     * 每个文件恢复后立即 sha 复核；最后删掉 loader。
     */
    fun restore(packageName: String, libraryDir: String, label: String): String =
        restore(packageName, libraryDir, label, emptyMap())

    fun restore(packageName: String, libraryDir: String, label: String, expectedOriginals: Map<String, String>): String {
        val dir = ParasitePaths.hostBackupDir(packageName)
        return buildString {
            appendLine("set -u")
            appendLine("D=${quote(dir)}")
            appendLine("L=${quote(libraryDir)}")
            appendLine("LBL=${quote(label)}")
            appendLine("[ -d \"\$D\" ] || { echo ${ParasiteMarkers.FAIL} code=RESTORE_NO_BACKUP; exit 40; }")
            // 没有完成标记/清单 = 半截备份：绝不能拿它写回宿主（会把旧库塞进没改过的文件）。
            appendLine("[ -f \"\$D/${ParasitePaths.BACKUP_MARKER_NAME}\" ] || [ -f \"\$D/${ParasitePaths.MANIFEST_NAME}\" ] || { echo ${ParasiteMarkers.FAIL} code=RESTORE_INCOMPLETE_BACKUP; exit 47; }")
            if (expectedOriginals.isNotEmpty()) append(expectedOriginalsCheck(expectedOriginals, "D"))
            appendLine("RESTORED=0")
            appendLine("for S in \"\$D\"/*.orig; do")
            appendLine("  [ -f \"\$S\" ] || continue")
            appendLine("  F=\${S##*/}; F=\${F%.orig}")
            appendLine("  T=\"\$L/\$F\"")
            appendLine("  WANT=\$(${hashLine("\"\$S\"")})")
            appendLine("  [ -n \"\$WANT\" ] || { echo ${ParasiteMarkers.FAIL} code=RESTORE_BACKUP_UNREADABLE detail=\"\$F\"; exit 41; }")
            appendLine("  NOW=\$(${hashLine("\"\$T\"")})")
            appendLine("  if [ \"\$NOW\" = \"\$WANT\" ]; then echo \"${ParasiteMarkers.RESTORE_SKIP}name=\$F sha=\$NOW\"; RESTORED=\$((RESTORED+1)); continue; fi")
            appendLine("  rm -f \"\$T.maplesu.new\"")
            appendLine("  cp -p \"\$S\" \"\$T.maplesu.new\" || { echo ${ParasiteMarkers.FAIL} code=RESTORE_COPY detail=\"\$F\"; exit 42; }")
            appendLine("  [ -z \"\$LBL\" ] || chcon \"\$LBL\" \"\$T.maplesu.new\" || { echo ${ParasiteMarkers.FAIL} code=RESTORE_LABEL detail=\"\$F\"; exit 43; }")
            appendLine("  GOT=\$(${hashLine("\"\$T.maplesu.new\"")})")
            appendLine("  [ \"\$GOT\" = \"\$WANT\" ] || { echo ${ParasiteMarkers.FAIL} code=RESTORE_TEMP_SHA detail=\"\$F\"; exit 44; }")
            appendLine("  mv -f \"\$T.maplesu.new\" \"\$T\" || { echo ${ParasiteMarkers.FAIL} code=RESTORE_RENAME detail=\"\$F\"; exit 45; }")
            appendLine("  AFTER=\$(${hashLine("\"\$T\"")})")
            appendLine("  [ \"\$AFTER\" = \"\$WANT\" ] || { echo ${ParasiteMarkers.FAIL} code=RESTORE_SHA detail=\"\$F\"; exit 46; }")
            appendLine("  echo \"${ParasiteMarkers.RESTORED}name=\$F sha=\$AFTER\"")
            appendLine("  RESTORED=\$((RESTORED+1))")
            appendLine("done")
            appendLine("rm -f \"\$L/${ParasitePaths.LOADER_NAME}\" \"\$L\"/*.maplesu.new || { echo ${ParasiteMarkers.FAIL} code=RESTORE_CLEANUP; exit 48; }")
            appendLine("for T in \"\$L/${ParasitePaths.LOADER_NAME}\" \"\$L\"/*.maplesu.new; do")
            appendLine("  [ ! -e \"\$T\" ] && [ ! -L \"\$T\" ] || { echo ${ParasiteMarkers.FAIL} code=RESTORE_CLEANUP; exit 48; }")
            appendLine("done")
            appendLine("echo \"${ParasiteMarkers.RESTORE_OK} count=\$RESTORED\"")
        }
    }

    /** Bind rollback restoration/retirement to the complete pre-injection backup set. */
    private fun expectedOriginalsCheck(expected: Map<String, String>, directoryVariable: String): String = buildString {
        require(expected.isNotEmpty() && expected.all { (name, sha) ->
            ParasitePolicy.isSafeLibName(name) && Regex("[0-9a-f]{64}").matches(sha)
        })
        expected.toSortedMap().forEach { (name, sha) ->
            val file = "\"\$$directoryVariable/$name.orig\""
            appendLine("[ -f $file ] && [ ! -L $file ] && [ \"\$(${hashLine(file)})\" = ${quote(sha)} ] || { echo ${ParasiteMarkers.FAIL} code=RESTORE_EXPECTED_SHA; exit 49; }")
        }
        appendLine("ORIG_COUNT=0")
        appendLine("for S in \"\$$directoryVariable\"/*.orig; do [ ! -e \"\$S\" ] && [ ! -L \"\$S\" ] || ORIG_COUNT=\$((ORIG_COUNT+1)); done")
        appendLine("[ \"\$ORIG_COUNT\" = '${expected.size}' ] || { echo ${ParasiteMarkers.FAIL} code=RESTORE_EXPECTED_COUNT; exit 49; }")
    }

    fun retireRestoredBackup(packageName: String, expected: Map<String, String>, nonce: String): String =
        retiredBackupScript(packageName, expected, nonce, move = true)

    /** A lost move acknowledgement is checked without issuing another mutation. */
    fun verifyRetiredBackup(packageName: String, expected: Map<String, String>, nonce: String): String =
        retiredBackupScript(packageName, expected, nonce, move = false)

    private fun retiredBackupScript(packageName: String, expected: Map<String, String>, nonce: String, move: Boolean): String = buildString {
        require(HiddenHostPolicy.isValidPackage(packageName) && Regex("[a-f0-9]{32}").matches(nonce))
        val quarantine = ParasitePaths.quarantineDir("rollback-$nonce")
        appendLine(if (move) "# PARASITE_BACKUP_RETIRE" else "# PARASITE_BACKUP_RETIRE_VERIFY")
        appendLine("set -u; LC_ALL=C; export LC_ALL")
        appendLine("D=${quote(ParasitePaths.hostBackupDir(packageName))}; Q=${quote(quarantine)}; T=\"\$Q/$packageName\"; I=\"\$Q/inventory\"")
        appendLine(trustedCleanupDirectory())
        // The backup is flat. Reject links, directories and ambiguous names instead of
        // inventing a lossy manifest; every data/metadata file is hashed and counted.
        appendLine("inventory() {")
        appendLine("  B=\"\$1\"; COUNT=0")
        appendLine("  [ -d \"\$B\" ] && [ ! -L \"\$B\" ] || return 1")
        appendLine("  for F in \"\$B\"/* \"\$B\"/.[!.]* \"\$B\"/..?*; do")
        appendLine("    [ -e \"\$F\" ] || [ -L \"\$F\" ] || continue")
        appendLine("    N=\${F##*/}; case \"\$N\" in ''|*[!A-Za-z0-9._-]*) return 1;; esac")
        appendLine("    [ -f \"\$F\" ] && [ ! -L \"\$F\" ] || return 1")
        appendLine("    H=\$(${hashLine("\"\$F\"")}); [ \"\${#H}\" = 64 ] || return 1")
        appendLine("    M=\$(stat -c '%s %a %u %g' \"\$F\") || return 1")
        appendLine("    printf '%s|%s|%s\\n' \"\$N\" \"\$H\" \"\$M\"; COUNT=\$((COUNT+1))")
        appendLine("  done")
        appendLine("  [ \"\$COUNT\" -gt 0 ] || return 1")
        appendLine("  echo \"count=\$COUNT\"")
        appendLine("}")
        if (move) {
            appendLine("B=\"\$D\"")
            append(expectedOriginalsCheck(expected, "B"))
            appendLine("BEFORE=\$(inventory \"\$D\") || exit 71")
            appendLine("mkdir \"\$Q\" && chmod 700 \"\$Q\" || exit 71")
            appendLine("[ ! -L \"\$Q\" ] && [ \"\$(stat -c '%a %u' \"\$Q\")\" = '700 0' ] || exit 71")
            appendLine("[ \"\$(stat -c '%d' \"\$D\")\" = \"\$(stat -c '%d' \"\$Q\")\" ] || exit 71")
            appendLine("umask 077; printf '%s' \"\$BEFORE\" > \"\$I\" && chmod 600 \"\$I\" || exit 71")
            appendLine("[ ! -e \"\$T\" ] && [ ! -L \"\$T\" ] || exit 71")
            appendLine("mv \"\$D\" \"\$T\" || exit 71")
        }
        appendLine("[ -d \"\$Q\" ] && [ ! -L \"\$Q\" ] && [ \"\$(stat -c '%a %u' \"\$Q\")\" = '700 0' ] || exit 71")
        appendLine("[ -f \"\$I\" ] && [ ! -L \"\$I\" ] && [ \"\$(stat -c '%a %u' \"\$I\")\" = '600 0' ] || exit 71")
        appendLine("[ ! -e \"\$D\" ] && [ ! -L \"\$D\" ] || exit 71")
        appendLine("B=\"\$T\"")
        append(expectedOriginalsCheck(expected, "B"))
        appendLine("SAVED=\$(cat \"\$I\") && AFTER=\$(inventory \"\$T\") && [ \"\$SAVED\" = \"\$AFTER\" ] || exit 71")
        appendLine("echo ${ParasiteMarkers.BACKUP_RETIRED}")
    }

    /** 最终复核：宿主 lib 当前 sha 必须等于清单里的 patched sha，loader 必须在且 sha 匹配。 */
    fun verifyEmbedded(manifest: HostManifest): String = buildString {
        appendLine("set -u")
        appendLine("L=${quote(manifest.libraryDir)}")
        appendLine("BAD=0")
        manifest.files.forEach { file ->
            appendLine("N=\$(${hashLine("\"\$L/${file.name}\"")})")
            appendLine("if [ \"\$N\" = ${quote(file.patchedSha256)} ]; then echo \"${ParasiteMarkers.VERIFY}name=${file.name} state=ok\"; else echo \"${ParasiteMarkers.VERIFY}name=${file.name} state=mismatch\"; BAD=\$((BAD+1)); fi")
        }
        appendLine("S=\$(${hashLine("\"\$L/${manifest.loaderName}\"")})")
        appendLine("if [ \"\$S\" = ${quote(manifest.loaderSha256)} ]; then echo \"${ParasiteMarkers.VERIFY}name=${manifest.loaderName} state=ok\"; else echo \"${ParasiteMarkers.VERIFY}name=${manifest.loaderName} state=mismatch\"; BAD=\$((BAD+1)); fi")
        appendLine("[ \"\$BAD\" = 0 ] || { echo ${ParasiteMarkers.FAIL} code=VERIFY_SHA bad=\$BAD; exit 50; }")
        appendLine("echo ${ParasiteMarkers.VERIFY_OK}")
    }

    /**
     * daemon 启动（同时写入 `parasite-start.sh` 供 loader 开机/宿主启动时调用）。
     * PID 仅作索引；进程身份及带鉴权的 HTTP 探活都通过才报告成功。
     *
     * **classpath 必须走 CLASSPATH 环境变量，不能用 `-Djava.class.path=`**：
     * `app_process` 的参数解析只认 `--nice-name=`/`--zygote` 这类开关，第一个未知参数
     * 会被当成**主类名**（真机上表现为 `Unable to find class '-Djava.class.path=...'`）。
     * 日志落到 `liteweb.log`（每次启动截断），daemon 起不来时能查原因；不打印 token。
     */
    fun daemonStart(apkPath: String = ParasitePaths.APK): String = buildString {
        appendLine("#!/system/bin/sh")
        appendLine("umask 077")
        appendLine("R=" + quote(ParasitePaths.RECOVERY))
        appendLine("APK=" + quote(apkPath))
        appendLine("PF=\"\$R/liteweb.pid\"")
        appendLine("[ \"\$(id -u)\" = 0 ] || { echo ${ParasiteMarkers.FAIL} code=ROOT_REQUIRED; exit 9; }")
        appendLine("[ ! -e \"\$R/webui.paused\" ] || { echo ${ParasiteMarkers.FAIL} code=DAEMON_PAUSED; exit 12; }")
        appendLine("[ -f \"\$APK\" ] || { echo ${ParasiteMarkers.FAIL} code=DAEMON_APK_MISSING; exit 10; }")
        appendLine(daemonIdentity())
        appendLine(hostStartupProof())
        appendLine("ready() { CLASSPATH=\"\$APK\" /system/bin/timeout -k 1 8 /system/bin/app_process /system/bin ${ParasitePaths.DAEMON_CLASS} --await-ready </dev/null >/dev/null 2>&1; }")
        appendLine("if [ -f \"\$PF\" ]; then")
        appendLine("  P=\$(cat \"\$PF\" 2>/dev/null)")
        appendLine("  if is_daemon \"\$P\"; then")
        appendLine("    if ready && is_daemon \"\$P\"; then [ \"\${1:-}\" != '--host-start' ] || [ ! -f \"\$R/host-start.challenge\" ] || exit 14; echo \"${ParasiteMarkers.ALIVE}pid=\$P\"; exit 0; fi")
        appendLine("    is_daemon \"\$P\" && kill \"\$P\" 2>/dev/null")
        appendLine("    sleep 1")
        appendLine("    is_daemon \"\$P\" && kill -9 \"\$P\" 2>/dev/null")
        appendLine("  fi")
        appendLine("fi")
        appendLine(": > \"\$R/liteweb.log\" 2>/dev/null")
        appendLine("LD_LIBRARY_PATH=\"\$R/native:\${LD_LIBRARY_PATH:-}\" CLASSPATH=\"\$APK\" nohup /system/bin/app_process /system/bin --nice-name=${ParasitePaths.DAEMON_NICE_NAME} ${ParasitePaths.DAEMON_CLASS} </dev/null >>\"\$R/liteweb.log\" 2>&1 &")
        appendLine("START_PID=\$!")
        // Only the lock owner publishes liteweb.pid, after bind. A losing concurrent launch never overwrites it.
        appendLine("if ready; then")
        appendLine("  N=0")
        appendLine("  while [ \"\$N\" -lt 5 ]; do")
        appendLine("    P=\$(cat \"\$PF\" 2>/dev/null)")
        appendLine("    if is_daemon \"\$P\"; then host_proof \"\$@\" || exit 14; echo \"${ParasiteMarkers.STARTED}pid=\$P\"; exit 0; fi")
        appendLine("    N=\$((N+1)); sleep 0.1")
        appendLine("  done")
        appendLine("fi")
        appendLine("echo ${ParasiteMarkers.FAIL} code=DAEMON_NOT_READY; exit 11")
    }

    /** Called only by a loader-origin invocation after authenticated daemon readiness. */
    private fun hostStartupProof(): String = """
        host_proof() {
          [ "${'$'}{1:-}" = '--host-start' ] || return 0
          [ -f "${'$'}R/host-start.challenge" ] || return 0
          [ ! -L "${'$'}R/host-start.challenge" ] || return 1
          [ "${'$'}(stat -c '%a %u %g' "${'$'}R/host-start.challenge")" = '600 0 0' ] || return 1
          [ "${'$'}P" = "${'$'}{START_PID:-}" ] || return 1
          HP=${'$'}{2:-}; HU=${'$'}{3:-}; HS=${'$'}{4:-}
          case "${'$'}HP:${'$'}HU:${'$'}HS" in *[!0-9:]*|:*|*::*) return 1;; esac
          [ "${'$'}HP" -gt 1 ] && [ "${'$'}HU" -ge 10000 ] || return 1
          C="${'$'}R/host-start.challenge"
          NONCE=${'$'}(sed -n 's/^nonce=//p' "${'$'}C")
          EXPECT_UID=${'$'}(sed -n 's/^uid=//p' "${'$'}C")
          EXPECT_BOOT=${'$'}(sed -n 's/^boot=//p' "${'$'}C")
          EXPECT_LOADER=${'$'}(sed -n 's/^loader=//p' "${'$'}C")
          EXPECT_PKG=${'$'}(sed -n 's/^package=//p' "${'$'}C")
          [ "${'$'}HU" = "${'$'}EXPECT_UID" ] || return 1
          [ "${'$'}(cat /proc/sys/kernel/random/boot_id)" = "${'$'}EXPECT_BOOT" ] || return 1
          [ "${'$'}(awk '/^Uid:/ {print ${'$'}2}' "/proc/${'$'}HP/status")" = "${'$'}HU" ] || return 1
          [ "${'$'}(sed 's/^.*) //' "/proc/${'$'}HP/stat" | awk '{print ${'$'}20}')" = "${'$'}HS" ] || return 1
          HOST_NAME=${'$'}(tr '\000' '\n' < "/proc/${'$'}HP/cmdline" | head -n 1)
          case "${'$'}HOST_NAME" in "${'$'}EXPECT_PKG"|"${'$'}EXPECT_PKG":*) ;; *) return 1;; esac
          awk -v lib="${'$'}EXPECT_LOADER" '${'$'}6 == lib {found=1} END {exit !found}' "/proc/${'$'}HP/maps" || return 1
          [ ! -L "${'$'}R/host-start.proof" ] || return 1
          printf 'nonce=%s uid=%s boot=%s\n' "${'$'}NONCE" "${'$'}HU" "${'$'}EXPECT_BOOT" > "${'$'}R/host-start.proof.tmp" || return 1
          chmod 600 "${'$'}R/host-start.proof.tmp" && mv -f "${'$'}R/host-start.proof.tmp" "${'$'}R/host-start.proof"
        }
    """.trimIndent()

    /** Stop the manager-started daemon, then require the patched host to bring it back. */
    fun hostStartupCheck(facts: HostFacts, nonce: String): String {
        require(facts.uid >= 10000 && Regex("^[a-f0-9]{32}$").matches(nonce))
        require(Regex("^[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+$").matches(facts.packageName))
        return buildString {
            appendLine("# MAPLESU_HOST_START_CHECK")
            appendLine("set -eu")
            appendLine("am force-stop --user ${facts.uid / 100000} ${quote(facts.packageName)} || exit 20")
            appendLine("(\n" + daemonStop() + "\n) || exit 21")
            appendLine(writeStartScript())
            appendLine("R=" + quote(ParasitePaths.RECOVERY))
            appendLine("[ ! -L \"\$R/host-start.challenge\" ] && [ ! -L \"\$R/host-start.proof\" ] || exit 22")
            appendLine("rm -f \"\$R/host-start.proof\" \"\$R/host-start.proof.tmp\"")
            appendLine("BOOT=\$(cat /proc/sys/kernel/random/boot_id)")
            appendLine("umask 077")
            appendLine("trap 'rm -f \"\$R/host-start.challenge\" \"\$R/host-start.proof\" \"\$R/host-start.proof.tmp\"' EXIT")
            appendLine("printf '%s\\n' ${quote("nonce=$nonce")} ${quote("uid=${facts.uid}")} \"boot=\$BOOT\" " +
                quote("package=${facts.packageName}") + " " + quote("loader=${ParasitePaths.loaderPath(facts.nativeLibraryDir)}") +
                " > \"\$R/host-start.challenge\"")
            appendLine("chmod 600 \"\$R/host-start.challenge\"")
            appendLine("ENTRY=\$(cmd package resolve-activity --brief --user ${facts.uid / 100000} -a android.intent.action.MAIN " +
                "-c android.intent.category.LAUNCHER ${quote(facts.packageName)} 2>/dev/null | tail -n 1)")
            appendLine("case \"\$ENTRY\" in ${quote(facts.packageName + "/")}*) ;; *) exit 23;; esac")
            appendLine("/system/bin/timeout -k 1 10 am start -W --user ${facts.uid / 100000} -n \"\$ENTRY\" >/dev/null 2>&1 || exit 24")
            appendLine("N=0; while [ \"\$N\" -lt 90 ]; do")
            appendLine("  if [ ! -L \"\$R/host-start.proof\" ] && [ -f \"\$R/host-start.proof\" ] && " +
                "[ \"\$(stat -c '%a %u %g' \"\$R/host-start.proof\")\" = '600 0 0' ] && " +
                "[ \"\$(cat \"\$R/host-start.proof\")\" = \"nonce=$nonce uid=${facts.uid} boot=\$BOOT\" ]; then")
            appendLine("    echo ${ParasiteMarkers.HOST_STARTED}; exit 0")
            appendLine("  fi")
            appendLine("  N=\$((N+1)); sleep 0.25")
            appendLine("done")
            appendLine("echo ${ParasiteMarkers.FAIL} code=${ParasiteCodes.HOST_START_UNVERIFIED}; exit 25")
        }
    }

    private fun daemonIdentity(): String = """
        NAME='${ParasitePaths.DAEMON_NICE_NAME}'
        is_daemon() {
          P=${'$'}1
          case "${'$'}P" in ''|*[!0-9]*) return 1;; esac
          [ "${'$'}P" -gt 1 ] && kill -0 "${'$'}P" 2>/dev/null || return 1
          [ "${'$'}(awk '/^Uid:/ {print ${'$'}2}' "/proc/${'$'}P/status" 2>/dev/null)" = 0 ] || return 1
          CMD=${'$'}(tr '\000' ' ' < "/proc/${'$'}P/cmdline" 2>/dev/null)
          A0=${'$'}{CMD%% *}
          if [ "${'$'}A0" = "/system/bin/app_process" ] || [ "${'$'}A0" = "/system/bin/app_process64" ]; then
            case "${'$'}CMD" in *"--nice-name=${'$'}NAME "*"${ParasitePaths.DAEMON_CLASS}"*) ;; *) return 1;; esac
          elif [ "${'$'}A0" = "${'$'}NAME" ]; then
            :
          else return 1
          fi
          CP=${'$'}(tr '\000' '\n' < "/proc/${'$'}P/environ" 2>/dev/null | sed -n 's/^CLASSPATH=//p')
          case "${'$'}CP" in "${'$'}R/manager.apk"|/data/app/*/dev.maplesu.manager-*/base.apk) return 0;; *) return 1;; esac
        }
    """.trimIndent()

    /**
     * daemon 停止：pid 文件 + argv 精确匹配（**绝不用 pkill -f**，spike §8.2），
     * 并且**杀完必须自证**（进程消失 + 端口不再 LISTEN）才输出 [ParasiteMarkers.STOPPED]；
     * 验不过就输出 `PARASITE_FAIL code=DAEMON_STOP_FAILED` 并 `exit 1`。
     *
     * 为什么还要 argv 兜底：`liteweb.pid` 只在**本脚本/修好后的 WebUI 自检路径**里写；
     * 历史上由旧版本、或绕过启动脚本手工拉起的 daemon 没有 pid 文件（2026-10-31 真机缺陷）。
     *
     * 为什么 argv 有两种形态（这就是上一版"永不命中"的根因）：
     *  1. `A0 == /system/bin/app_process` —— `app_process` 原生 argv（`--nice-name=<名>` 在后续参数里）；
     *  2. `A0 == maplesu-liteweb` —— `--nice-name` 会**改写进程名/argv[0]**，Android 16 真机上
     *     `/proc/<pid>/cmdline` 只剩纯 nice-name，`app_process` 与参数都不在。
     * 所以**不能**用 `A0 = app_process` 做预过滤：两种都认，且都要求名字精确匹配，
     * 这样既不误杀执行脚本的 sh 自身，也不会再漏掉真机形态。
     *
     * 为什么用端口而不是 `ps` 判定存活：Android 16 上 `ps -A` 看不到其它 uid 的进程
     *（2026-10-31 真机复测 §5.4），只有监听 socket 是 root 侧可验证的事实。
     * 端口取 daemon 自己的 `webui.conf`（`webuiPort=`），与 daemon 实际监听同源。
     */
    fun daemonStop(): String = buildString {
        appendLine("set -u")
        appendLine("R=" + quote(ParasitePaths.RECOVERY))
        appendLine("PF=\"\$R/liteweb.pid\"")
        appendLine(daemonIdentity())
        // 端口：与 daemon 同一份配置（daemon/激活写 `webuiPort=`，预览/管理写 `port=`，两种都认）；
        // 读不到就只按进程判定，并在 detail 里如实标注 port=unknown。
        appendLine("PORT=\$(sed -n 's/^webuiPort=//p;s/^port=//p' \"\$R/webui.conf\" 2>/dev/null | head -n 1)")
        appendLine("HEX=\"\"")
        appendLine("if [ -n \"\$PORT\" ]; then HEX=\$(printf '%04X' \"\$PORT\" 2>/dev/null); fi")
        appendLine("TARGETS=\"\"")
        appendLine("if [ -f \"\$PF\" ]; then")
        appendLine("  P=\$(cat \"\$PF\" 2>/dev/null)")
        appendLine("  if is_daemon \"\$P\"; then")
        appendLine("    TARGETS=\"\$TARGETS \$P\"")
        appendLine("    kill \"\$P\" 2>/dev/null")
        appendLine("  fi")
        appendLine("  rm -f \"\$PF\"")
        appendLine("fi")
        // 兜底扫描：只认上面两种 argv 形态，名字必须精确等于 $NAME（不做子串匹配、不用 pkill -f）。
        appendLine("for D in /proc/[0-9]*; do")
        appendLine("  P=\${D#/proc/}")
        appendLine("  is_daemon \"\$P\" || continue")
        appendLine("  [ \"\$P\" = \"\$\$\" ] && continue")
        appendLine("  TARGETS=\"\$TARGETS \$P\"")
        appendLine("  kill \"\$P\" 2>/dev/null")
        appendLine("done")
        // 自证：最多等 8 秒。第 2 秒起升级 SIGKILL。判据必须**同时**成立：
        //   (a) 命中的 pid 全部消失（kill -0 失败）；
        //   (b) 端口不再有 LISTEN 项（/proc/net/tcp{,6} 里 state=0A；无端口可读时只看 (a)）。
        appendLine("N=0")
        appendLine("while [ \"\$N\" -lt 8 ]; do")
        appendLine("  ALIVE=0")
        appendLine("  for P in \$TARGETS; do")
        appendLine("    is_daemon \"\$P\" && ALIVE=1")
        appendLine("  done")
        appendLine("  LISTEN=0")
        appendLine("  if [ -n \"\$HEX\" ] && grep -qE \":\$HEX [0-9A-F]{8,}:0+ 0A \" /proc/net/tcp /proc/net/tcp6 2>/dev/null; then LISTEN=1; fi")
        appendLine("  if [ \"\$ALIVE\" = 0 ] && [ \"\$LISTEN\" = 0 ]; then")
        appendLine("    rm -f \"\$R/parasite-start.sh\" 2>/dev/null || exit 1")
        appendLine("    echo \"${ParasiteMarkers.STOPPED} port=\${PORT:-unknown}\"")
        appendLine("    exit 0")
        appendLine("  fi")
        appendLine("  N=\$((N+1))")
        appendLine("  if [ \"\$N\" = 2 ]; then")
        appendLine("    for P in \$TARGETS; do is_daemon \"\$P\" && kill -9 \"\$P\" 2>/dev/null; done")
        appendLine("  fi")
        appendLine("  sleep 1")
        appendLine("done")
        appendLine("echo \"${ParasiteMarkers.FAIL} code=${ParasiteCodes.DAEMON_STOP_FAILED} detail=alive=\$ALIVE;listen=\$LISTEN;port=\${PORT:-unknown}\"")
        appendLine("exit 1")
    }

    /**
     * 解除寄生第 2 步：**复核** loader 已被删除（[restore] 已删；这里独立成步，避免把
     * "宿主库还原成功" 当成 "loader 也没了"）。后续撤权可能失败，启动脚本保留到恢复完成。
     *
     * 判定必须来自文件事实（`test ! -e`），不靠日志文案。
     */
    fun removeLoader(libraryDir: String): String = buildString {
        appendLine("set -u")
        appendLine("L=" + quote(libraryDir))
        appendLine("rm -f \"\$L/${ParasitePaths.LOADER_NAME}\" \"\$L\"/*.maplesu.new 2>/dev/null")
        appendLine("[ ! -e \"\$L/${ParasitePaths.LOADER_NAME}\" ] || { echo \"${ParasiteMarkers.FAIL} code=LOADER_DELETE_FAILED\"; exit 60; }")
        appendLine("echo ${ParasiteMarkers.LOADER_GONE}")
    }

    /**
     * 列出 `host/<pkg>` 下每个目录的**完整性事实**（一行一个目录，字段用 `|` 分隔）：
     *
     * `PARASITE_BK|<name>|<marker 0/1>|<manifest 字节数>|<manifest 中 file= 行数>|<*.orig 个数>`
     *
     * 为什么不再只列目录名：现场有 4 个 10-30 遗留的**半截备份**目录（只有几个 `.orig`，既无
     * `BACKUP_COMPLETE` 也无 `host.manifest`），旧实现「见目录数 >1 即 AMBIGUOUS」把它们当成
     * 真歧义，导致 WebUI 一键解除永久 409（真机 D2）。完整性由调用方按事实判定，本函数只
     * 采事实、不做结论（结论在 `DaemonParasiteUnhide` 里，主机可测）。
     *
     * 说明：`grep -c` 无匹配时退出码非 0，但**仍打印 0**，因此这里只取输出不取退出码。
     */
    fun listHostBackupFacts(): String = buildString {
        appendLine("set -u")
        appendLine("H=" + quote(ParasitePaths.HOST_BACKUP_ROOT))
        appendLine("[ -d \"\$H\" ] || exit 0")
        appendLine("for D in \"\$H\"/*; do")
        appendLine("  [ -d \"\$D\" ] || continue")
        appendLine("  N=\${D##*/}")
        appendLine("  M=\"\$D/${ParasitePaths.MANIFEST_NAME}\"")
        appendLine("  MARK=0")
        appendLine("  [ -f \"\$D/${ParasitePaths.BACKUP_MARKER_NAME}\" ] && MARK=1")
        appendLine("  BYTES=0")
        appendLine("  ENTRIES=0")
        appendLine("  if [ -f \"\$M\" ]; then")
        appendLine("    BYTES=\$(wc -c < \"\$M\" 2>/dev/null | tr -d ' ')")
        appendLine("    ENTRIES=\$(grep -c '^file=' \"\$M\" 2>/dev/null)")
        appendLine("  fi")
        appendLine("  ORIGS=0")
        appendLine("  for O in \"\$D\"/*.orig; do [ -f \"\$O\" ] && ORIGS=\$((ORIGS+1)); done")
        appendLine("  [ -n \"\$BYTES\" ] || BYTES=0")
        appendLine("  [ -n \"\$ENTRIES\" ] || ENTRIES=0")
        appendLine("  echo \"${ParasiteMarkers.BACKUP_FACT}\$N|\$MARK|\$BYTES|\$ENTRIES|\$ORIGS\"")
        appendLine("done")
    }

    /**
     * 把**陈旧残留**备份目录改名进 `recovery/host-quarantine-<stamp>/`（保留证据，绝不删除）。
     *
     * 只接受调用方已按包名白名单过滤过的名字（这里是第二道：逐个 `quote`，并且拒绝空名/
     * 含 `/`/`..` 的名字）。目标已存在时加时间戳后缀，绝不覆盖上一次的证据目录。
     */
    fun quarantineHostBackups(stamp: String, names: List<String>): String {
        val safe = names.filter { it.isNotEmpty() && !it.contains('/') && !it.contains("..") }
        return buildString {
            appendLine("set -u")
            appendLine("H=" + quote(ParasitePaths.HOST_BACKUP_ROOT))
            appendLine("Q=" + quote(ParasitePaths.quarantineDir(stamp)))
            appendLine("mkdir -p \"\$Q\" 2>/dev/null || { echo ${ParasiteMarkers.FAIL} code=QUARANTINE_DIR; exit 70; }")
            appendLine("N=0")
            if (safe.isEmpty()) {
                appendLine("echo \"${ParasiteMarkers.QUARANTINED} n=0;dir=\$Q\"")
            } else {
                appendLine("for B in " + safe.joinToString(" ") { quote(it) } + "; do")
                appendLine("  [ -d \"\$H/\$B\" ] || continue")
                appendLine("  T=\"\$Q/\$B\"")
                appendLine("  if [ -e \"\$T\" ]; then T=\"\$Q/\$B.\$(date +%s)\"; fi")
                appendLine(
                    "  mv \"\$H/\$B\" \"\$T\" 2>/dev/null || { echo ${ParasiteMarkers.FAIL} " +
                        "code=QUARANTINE_MOVE detail=\$B; exit 71; }",
                )
                appendLine("  N=\$((N+1))")
                appendLine("done")
                appendLine("echo \"${ParasiteMarkers.QUARANTINED} n=\$N;dir=\$Q\"")
            }
        }
    }

    /** 把 daemon 启动脚本落盘（root 0700），loader 之后只认这个路径。 */
    fun writeStartScript(): String =
        "mkdir -p " + quote(ParasitePaths.RECOVERY) + " && cat > " + quote(ParasitePaths.START_SCRIPT) +
            " <<'MAPLESU_EOF'\n" + daemonStart() + "MAPLESU_EOF\n" +
            "chmod 700 " + quote(ParasitePaths.START_SCRIPT) + " && echo LITE_OK"

    /** 执行已落盘的启动脚本（根通道）。 */
    fun runStartScript(): String = "/system/bin/sh " + quote(ParasitePaths.START_SCRIPT)

    fun readText(path: String): String = "cat " + quote(path) + " 2>/dev/null"

    fun readManifest(packageName: String): String = readText(ParasitePaths.manifestPath(packageName))

    fun readLibDir(packageName: String): String = readText(ParasitePaths.libDirFile(packageName))

    fun readLabel(packageName: String): String = readText(ParasitePaths.labelFile(packageName))

    /**
     * 是否存在**可复原**的备份目录：必须有完成标记或清单。
     *
     * 只有半截 `.orig` 的目录不算备份，顺手清掉（它从未用于注入）。脚本里的
     * `PARASITE_BACKUP_PROBE` 注释是给测试用的稳定锚点 —— 输出仍只有 YES/NO。
     */
    fun backupDirExists(packageName: String): String {
        val dir = quote(ParasitePaths.hostBackupDir(packageName))
        val manifest = quote(ParasitePaths.manifestPath(packageName))
        val marker = quote(ParasitePaths.backupMarkerPath(packageName))
        return buildString {
            appendLine("set -u")
            appendLine("# PARASITE_BACKUP_PROBE")
            appendLine("D=$dir")
            appendLine("if [ ! -d \"\$D\" ]; then echo PARASITE_NO; exit 0; fi")
            appendLine("if [ -f $manifest ] || [ -f $marker ]; then echo PARASITE_YES; exit 0; fi")
            appendLine("rm -rf \"\$D\" 2>/dev/null")
            appendLine("echo PARASITE_NO")
        }
    }

    fun removeBackup(packageName: String): String = buildString {
        val dir = quote(ParasitePaths.hostBackupDir(packageName))
        appendLine("rm -rf $dir 2>/dev/null || { echo ${ParasiteMarkers.FAIL} code=BACKUP_DELETE_FAILED; exit 87; }")
        appendLine("[ ! -e $dir ] && [ ! -L $dir ] || { echo ${ParasiteMarkers.FAIL} code=BACKUP_DELETE_FAILED; exit 87; }")
        appendLine("echo ${ParasiteMarkers.BACKUP_GONE}")
    }

    /** Written only after restore, revoke and stop have each been verified. */
    fun cleanupReceipt(packageName: String, mode: String, restored: Boolean): String {
        require(HiddenHostPolicy.isValidPackage(packageName) && mode in setOf("LITE", "KSU"))
        return "MAPLESU_CLEANUP_V1 mode=$mode host=$packageName restored=${if (restored) 1 else 0}"
    }

    private fun trustedCleanupDirectory(): String {
        val dir = quote(ParasitePaths.RECOVERY)
        return "[ -d $dir ] && [ ! -L $dir ] && [ \"\$(stat -c '%a %u' $dir 2>/dev/null)\" = '700 0' ] || exit 88"
    }

    fun readCleanupCheckpoint(): String = buildString {
        appendLine("# PARASITE_CLEANUP_READ")
        val dir = quote(ParasitePaths.RECOVERY)
        appendLine("if [ -e $dir ] || [ -L $dir ]; then")
        appendLine(trustedCleanupDirectory())
        appendLine("fi")
        appendLine("F=${quote(ParasitePaths.CLEANUP_CHECKPOINT)}")
        appendLine("if [ ! -e \"\$F\" ] && [ ! -L \"\$F\" ]; then echo ${ParasiteMarkers.CLEANUP_NONE}; exit 0; fi")
        appendLine("[ -f \"\$F\" ] && [ ! -L \"\$F\" ] && [ \"\$(stat -c '%a %u' \"\$F\" 2>/dev/null)\" = '600 0' ] || { echo ${ParasiteMarkers.FAIL} code=CLEANUP_UNTRUSTED; exit 88; }")
        appendLine("cat \"\$F\" || exit 88")
    }

    fun beginCleanup(packageName: String, mode: String, restored: Boolean): String = buildString {
        val receipt = quote(cleanupReceipt(packageName, mode, restored))
        appendLine("# PARASITE_CLEANUP_BEGIN")
        appendLine("F=${quote(ParasitePaths.CLEANUP_CHECKPOINT)}; T=\"\$F.tmp\"")
        appendLine("[ ! -e \"\$F\" ] && [ ! -L \"\$F\" ] && [ ! -L \"\$T\" ] || exit 88")
        val dir = quote(ParasitePaths.RECOVERY)
        appendLine("mkdir -p $dir || exit 88")
        appendLine("[ ! -L $dir ] && [ \"\$(stat -c '%u' $dir 2>/dev/null)\" = '0' ] || exit 88")
        appendLine("chmod 700 $dir || exit 88")
        appendLine(trustedCleanupDirectory())
        appendLine("umask 077")
        appendLine("printf %s $receipt > \"\$T\" && chmod 600 \"\$T\" && chown 0:0 \"\$T\" || exit 88")
        appendLine("mv -f \"\$T\" \"\$F\" || exit 88")
        appendLine("[ \"\$(stat -c '%a %u' \"\$F\" 2>/dev/null)\" = '600 0' ] && [ \"\$(cat \"\$F\")\" = $receipt ] || exit 88")
        appendLine("echo ${ParasiteMarkers.CLEANUP_READY}")
    }

    fun removeCleanupIdentity(): String = buildString {
        appendLine(RecoveryIdentityScripts.remove())
        listOf(ParasitePaths.IDENTITY_FILE, ParasitePaths.IDENTITY_TEMP).forEach { path ->
            val file = quote(path)
            appendLine("[ ! -e $file ] && [ ! -L $file ] || { echo ${ParasiteMarkers.FAIL} code=IDENTITY_DELETE_FAILED; exit 86; }")
        }
    }

    fun finishCleanup(packageName: String, mode: String, restored: Boolean): String = buildString {
        appendLine("# PARASITE_CLEANUP_FINISH")
        appendLine(trustedCleanupDirectory())
        appendLine("F=${quote(ParasitePaths.CLEANUP_CHECKPOINT)}")
        appendLine("[ -f \"\$F\" ] && [ ! -L \"\$F\" ] && [ \"\$(stat -c '%a %u' \"\$F\" 2>/dev/null)\" = '600 0' ] && [ \"\$(cat \"\$F\")\" = ${quote(cleanupReceipt(packageName, mode, restored))} ] || exit 88")
        listOf(ParasitePaths.hostBackupDir(packageName), ParasitePaths.IDENTITY_FILE, ParasitePaths.IDENTITY_TEMP).forEach { path ->
            val file = quote(path)
            appendLine("[ ! -e $file ] && [ ! -L $file ] || exit 88")
        }
        appendLine("rm -f \"\$F\" || exit 88")
        appendLine("[ ! -e \"\$F\" ] && [ ! -L \"\$F\" ] || exit 88")
        appendLine("echo ${ParasiteMarkers.CLEANUP_GONE}")
    }
}
