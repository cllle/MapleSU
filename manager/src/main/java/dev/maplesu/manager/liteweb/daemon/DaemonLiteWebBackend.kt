package dev.maplesu.manager.liteweb.daemon

import dev.maplesu.manager.liteweb.LiteHiddenPort
import dev.maplesu.manager.liteweb.LiteHiddenResult
import dev.maplesu.manager.liteweb.LiteWebAssets
import dev.maplesu.manager.liteweb.LiteWebBackend
import dev.maplesu.manager.liteweb.LiteWebBootPersistence
import dev.maplesu.manager.liteweb.LiteWebConstants
import dev.maplesu.manager.liteweb.LiteWebHiddenView
import dev.maplesu.manager.liteweb.LiteWebJson
import dev.maplesu.manager.liteweb.LiteWebModuleList
import dev.maplesu.manager.liteweb.LiteWebPortPolicy
import dev.maplesu.manager.liteweb.LiteWebRecoveryStatus
import dev.maplesu.manager.liteweb.LiteWebStatusView
import dev.maplesu.manager.liteweb.LiteWebSulogView
import dev.maplesu.manager.liteweb.LiteWebToggleView
import dev.maplesu.manager.liteweb.LiteWebUnhidePort
import dev.maplesu.manager.liteweb.UnavailableUnhidePort
import dev.maplesu.manager.liteweb.LiteWebBackupInfo
import dev.maplesu.manager.liteweb.LiteWebDiagnosticsPort
import dev.maplesu.manager.liteweb.LiteWebDiagnosticsView
import dev.maplesu.manager.liteweb.LiteWebHiddenModeBackend
import dev.maplesu.manager.liteweb.LiteWebMode
import dev.maplesu.manager.liteweb.LiteWebModeInfo
import dev.maplesu.manager.liteweb.LiteWebWorkModePort
import dev.maplesu.manager.liteweb.LiteWebWorkModeView
import dev.maplesu.manager.liteweb.UnavailableHiddenModeBackend
import dev.maplesu.manager.liteweb.LiteWebSuperuserPort
import dev.maplesu.manager.liteweb.UnavailableSuperuserPort
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.zip.ZipFile

/**
 * daemon 侧的只读/受限 root 后端（**纯 JDK，无 Android import**）。
 *
 * 存在的理由：真卸载后只剩 `/data/adb/maplesu/recovery/` 里的 APK 副本与配置，没有
 * `LiteWebAdminService`（Android RootService，进程内才有 AssetManager/Context）。
 * 这里用 JDK 直接读设备文件系统与 APK zip，把同一套 [LiteWebBackend] 语义补上：
 *
 * - 静态资源：`manager.apk!assets/liteweb/...`（[ZipFile] 按需读 + 有界内存缓存）；
 * - 状态/模块：独立进程只读 SDK 事实；Ultra 仅 SDK 列表，明确已提交 Normal 才扫描 KSU 目录；
 * - Normal 模块：`disable`=停用、`remove`=待卸载；未知模式绝不回退目录扫描；
 * - 超管：生产注入共享 SDK 端口；无端口时旧列表仅保留兼容空数组；
 * - su 日志：`/data/adb/maplesu/logs/su.log` 有界尾部，否则 unavailable；
 * - Normal 模块写仅有 `disable` 文件增删（安全 id + canonical path 复核）。
 *
 * 其它（activate/restore/settings/module action/...）保持
 * [LiteWebBackend] 的 `Unavailable*` 默认实现：前端拿到 501，不假装成功。
 */
class DaemonLiteWebBackend(
    /** `/data/adb`；测试可换成临时目录。 */
    private val adbRoot: String = "/data/adb",
    /** 恢复备份的 APK 副本（WebUI 静态资源的唯一来源）。 */
    private val managerApkPath: String = "$adbRoot/maplesu/recovery/manager.apk",
    private val hiddenStatePath: String = "$adbRoot/maplesu/lite_hidden",
    private val modulesRoot: String = "$adbRoot/modules",
    private val suLogPath: String = "$adbRoot/maplesu/logs/su.log",
    private val configuredPortValue: Int = LiteWebPortPolicy.DEFAULT_PORT,
    private val selfUid: () -> Int? = { DaemonProcStatus.uid() },
    private val assetsReader: DaemonLiteWebAssets = DaemonLiteWebAssets(
        apkPath = managerApkPath,
        // 恢复备份还没做出来时（App 侧拉起 daemon），退回到正在运行的这份 APK。
        fallbackApkPaths = listOfNotNull(DaemonClasspathApk.path()),
    ),
    private val moduleCatalog: DaemonModuleCatalog = DaemonModuleCatalog(modulesRoot),
    private val hiddenPortValue: DaemonLiteHiddenPort = DaemonLiteHiddenPort(hiddenStatePath),
    private val suLog: DaemonSuLog = DaemonSuLog(suLogPath),
    /**
     * 解除寄生端口（`POST /api/hidden {enable:false}`）：
     * 生产由 [LiteWebDaemonMain] 注入 [DaemonParasiteUnhide]（root 脚本 + Ultra SDK 撤销 + 自杀）；
     * 默认 [UnavailableUnhidePort] → handler 仍回 501（不假装能解除）。
     */
    private val unhidePortValue: LiteWebUnhidePort = UnavailableUnhidePort,
    private val runtimeSource: DaemonRuntimeSource = UnavailableDaemonRuntimeSource,
    private val superuserPortValue: LiteWebSuperuserPort = UnavailableSuperuserPort,
    private val managerInstalled: () -> Boolean? = { null },
    private val portValue: () -> Int = { configuredPortValue },
    private val changePort: (Int) -> dev.maplesu.manager.liteweb.LiteWebRestoreStep = {
        dev.maplesu.manager.liteweb.UnavailableActivatePort.applyPort(it)
    },
) : LiteWebBackend, AutoCloseable {

    private val startedAtNanos = System.nanoTime()
    private val moduleFiles = dev.maplesu.manager.liteweb.LiteWebModuleFiles(File(modulesRoot))
    @Volatile private var browserBridge: dev.maplesu.manager.liteweb.LiteWebModuleBridgePort =
        dev.maplesu.manager.liteweb.UnavailableModuleBridgePort

    internal fun attachModuleBridge(bridge: dev.maplesu.manager.liteweb.LiteWebModuleBridgePort) {
        browserBridge.close()
        browserBridge = bridge
    }

    override fun moduleBridgePort() = browserBridge
    override fun moduleWebFile(moduleId: String, relativePath: String) = moduleFiles.read(moduleId, relativePath)


    /** 隐藏态读不到为 null；模块数来自当前模式的真实后端，失败单独标记。 */
    override fun status(): LiteWebStatusView {
        val hidden = hiddenPortValue.currentHidden()
        val runtime = runtimeSnapshot()
        val modules = modules(runtime)
        val count = LiteWebModuleList.count(modules.json)
        return LiteWebStatusView(
            mode = runtime.mode,
            rootAvailable = rootAvailable(),
            moduleCount = count.count,
            hidden = hidden ?: false,
            uapi = "daemon",
            serverVersion = LiteWebConstants.SERVER_VERSION,
            moduleCountError = modules.error.ifEmpty { count.error },
            // 新增字段（向后兼容）：null = 未断言，handler 会输出 "hidden":null。
            hiddenKnown = hidden,
        )
    }

    /** daemon 自己 uid==0 且 /data/adb 可读，才敢声明 root 可用。 */
    fun rootAvailable(): Boolean {
        if (selfUid() != 0) return false
        return runCatching {
            val root = File(adbRoot)
            root.isDirectory && root.canRead()
        }.getOrDefault(false)
    }

    override fun modulesJson(): String = modules(runtimeSnapshot()).responseJson()

    private fun runtimeSnapshot(): DaemonRuntimeSnapshot =
        runCatching { runtimeSource.snapshot() }.getOrDefault(DaemonRuntimeSnapshot())

    private fun modules(runtime: DaemonRuntimeSnapshot): DaemonModulesSnapshot = when (runtime.mode) {
        LiteWebMode.LITE -> runCatching { runtimeSource.ultraModules() }
            .getOrElse { DaemonModulesSnapshot(error = "ULTRA_MODULES_UNAVAILABLE") }
        LiteWebMode.KSU -> DaemonModulesSnapshot(moduleCatalog.listJson())
        else -> DaemonModulesSnapshot(error = "DAEMON_MODE_UNRESOLVED")
    }

    /**
     * 旧版列表兼容值。生产 HTTP 优先使用 [superuserPort] 的 SDK 回读；未注入时保留旧契约。
     */
    override fun superusersJson(): String = "[]"

    override fun superuserPort(): LiteWebSuperuserPort = superuserPortValue

    override fun sulog(): LiteWebSulogView = suLog.read()

    override fun toggleModule(moduleId: String, enabled: Boolean): LiteWebToggleView =
        when (runtimeSnapshot().mode) {
            LiteWebMode.KSU -> moduleCatalog.setEnabled(moduleId, enabled)
            LiteWebMode.LITE -> runtimeSource.toggleUltraModule(moduleId, enabled)
            else -> LiteWebToggleView.Unavailable("DAEMON_MODE_UNRESOLVED")
        }

    override fun modeBackend(): LiteWebHiddenModeBackend =
        object : LiteWebHiddenModeBackend by UnavailableHiddenModeBackend {
            override fun mode(): LiteWebModeInfo {
                val runtime = runtimeSnapshot()
                return LiteWebModeInfo(
                    mode = runtime.mode,
                    rootBackend = runtime.rootBackend,
                    daemonRunning = true,
                    daemonPort = configuredPort(),
                )
            }

            override fun backup(): LiteWebBackupInfo = LiteWebBackupInfo(
                present = runCatching { File(managerApkPath).isFile }.getOrNull(),
            )
        }

    override fun diagnosticsPort(): LiteWebDiagnosticsPort = object : LiteWebDiagnosticsPort {
        override fun read(): LiteWebDiagnosticsView {
            val runtime = runtimeSnapshot()
            return LiteWebDiagnosticsView(
                probe = "${runtime.probe}:${runtime.source}",
                rootBackend = runtime.rootBackend,
                mode = runtime.mode,
                port = configuredPort(),
                apkBackupPresent = runCatching { File(managerApkPath).isFile }.getOrNull(),
                daemonUptimeSeconds = (System.nanoTime() - startedAtNanos).coerceAtLeast(0) / 1_000_000_000,
            )
        }
    }

    override fun workModePort(): LiteWebWorkModePort = object : LiteWebWorkModePort {
        override fun read(): LiteWebWorkModeView = LiteWebWorkModeView(mode = runtimeSnapshot().mode)
    }

    override fun assets(): LiteWebAssets = assetsReader

    override fun hiddenPort(): LiteHiddenPort = hiddenPortValue

    /** 完整解除寄生（宿主库还原 → loader 删除 → 撤销授权 → 停 daemon）；默认不可用 → 501。 */
    override fun unhidePort(): LiteWebUnhidePort = unhidePortValue

    override fun configuredPort(): Int = portValue()

    override fun applyConfiguredPort(port: Int): dev.maplesu.manager.liteweb.LiteWebRestoreStep = changePort(port)

    /**
     * `GET /api/hidden`：只用本进程能证明的事实。
     * `apkShaValid` 需要完整 sha 校验（每次 GET 都读几十 MB），这里不冒充 → null；
     * `bootPersistence` 需要 boot-id 证据，daemon 侧不持有 → UNKNOWN。
     */
    override fun hiddenStatus(): LiteWebHiddenView = LiteWebHiddenView(
        hidden = hiddenPortValue.currentHidden(),
        port = configuredPort(),
        recovery = LiteWebRecoveryStatus(
            apkBackupPresent = runCatching { File(managerApkPath).isFile }.getOrNull(),
            apkShaValid = null,
            // 本进程正在响应这次请求 —— daemon 确实活着。
            daemonRunning = true,
            bootPersistence = LiteWebBootPersistence.UNKNOWN,
        ),
        steps = emptyList(),
        managerUninstalled = runCatching { managerInstalled()?.not() }.getOrNull(),
    )

    override fun close() {
        browserBridge.close()
        assetsReader.close()
    }

    companion object {
        /** 前端只接受数组；这个常量给日志/文档一个稳定理由串。 */
        const val SUPERUSERS_UNAVAILABLE =
            "DAEMON_SUPERUSERS_UNAVAILABLE: recovery daemon has no readable authorization store"
    }
}

/** 限额常量集中一处，便于审计「有界」这条红线。 */
object DaemonLiteWebLimits {
    /** 单个静态资源上限（APK 里的 WebUI 是页面，不是镜像）。 */
    const val MAX_ASSET_BYTES: Long = 8L * 1024 * 1024

    /** 静态资源内存缓存下限/上限（按总字节 + 条目数双重封顶）。 */
    const val ASSET_CACHE_BYTES: Long = 32L * 1024 * 1024
    const val ASSET_CACHE_ENTRIES: Int = 128

    /** APK 内 WebUI 资源前缀。 */
    const val ASSET_PREFIX: String = "assets/liteweb/"

    /** 模块扫描：目录数上限 + module.prop 上限。 */
    const val MAX_MODULES: Int = 512
    const val MAX_MODULE_PROP_BYTES: Long = 64L * 1024
    const val MAX_MODULE_TEXT: Int = 1024

    /** su 日志尾部上限（行数 + 字节数）。 */
    const val MAX_SULOG_BYTES: Long = 128L * 1024
    const val MAX_SULOG_LINES: Int = 200

    /** lite_hidden 文件上限（只应有一个 0/1）。 */
    const val MAX_HIDDEN_FILE_BYTES: Long = 16
}

/**
 * APK 内 `assets/liteweb/...` 的只读访问（[ZipFile]，无 Android AssetManager）。
 *
 * 安全：路径再次做字符串复核（`..`/`.`/反斜杠/NUL/空段），不接受绝对路径；
 * 只按**精确条目名**取，不做前缀/通配匹配，所以不可能读到 `assets/liteweb/` 之外。
 * 单文件超过 [DaemonLiteWebLimits.MAX_ASSET_BYTES] → 不可服务（按 404 处理，不返回半截数据）。
 */
class DaemonLiteWebAssets(
    private val apkPath: String,
    /**
     * 备用 APK（`app_process` 的 CLASSPATH 指向的那一份）。
     *
     * 只在主路径（恢复备份 `manager.apk`）缺失/打不开时使用：App 未真卸载前由
     * `UltraEnvLiteWebDaemon` 拉起 daemon 时可能还没有恢复备份，此时 classpath 里的 APK
     * 就是「正在跑的这份代码」，它的 assets 与 daemon 同源，比直接 404 诚实。
     */
    private val fallbackApkPaths: List<String> = emptyList(),
    private val maxFileBytes: Long = DaemonLiteWebLimits.MAX_ASSET_BYTES,
    private val cacheBudgetBytes: Long = DaemonLiteWebLimits.ASSET_CACHE_BYTES,
    private val cacheMaxEntries: Int = DaemonLiteWebLimits.ASSET_CACHE_ENTRIES,
) : LiteWebAssets, AutoCloseable {
    private val lock = Any()
    private val cache = LinkedHashMap<String, ByteArray>()
    private var cacheBytes: Long = 0L

    @Volatile
    private var zip: ZipFile? = null

    override fun read(relativePath: String): ByteArray? {
        val safe = safeRelative(relativePath) ?: return null
        synchronized(lock) {
            cache[safe]?.let { return it }
            val bytes = readFromApkLocked(safe) ?: return null
            if (cache.size < cacheMaxEntries && cacheBytes + bytes.size <= cacheBudgetBytes) {
                cache[safe] = bytes
                cacheBytes += bytes.size
            }
            return bytes
        }
    }

    /** 供测试/重启复用：关闭底层 zip（下次 read 会重新打开）。 */
    override fun close() {
        synchronized(lock) {
            runCatching { zip?.close() }
            zip = null
            cache.clear()
            cacheBytes = 0L
        }
    }

    private fun readFromApkLocked(relative: String): ByteArray? {
        val entryName = DaemonLiteWebLimits.ASSET_PREFIX + relative
        val file = openLocked() ?: return null
        val entry = runCatching { file.getEntry(entryName) }.getOrNull() ?: return null
        if (entry.isDirectory || entry.size > maxFileBytes) return null
        val bytes = runCatching {
            file.getInputStream(entry).use { readBounded(it, maxFileBytes) }
        }.getOrNull() ?: return null
        if (bytes.size.toLong() > maxFileBytes) return null
        return bytes
    }

    private fun openLocked(): ZipFile? {
        zip?.let { return it }
        val candidates = buildList {
            add(apkPath)
            addAll(fallbackApkPaths)
        }
        for (candidate in candidates) {
            val apk = File(candidate)
            if (!apk.isFile || !apk.canRead()) continue
            val opened = runCatching { ZipFile(apk) }.getOrNull() ?: continue
            zip = opened
            return opened
        }
        return null
    }

    /** 相对路径复核：拒绝绝对路径/反斜杠/控制字符/`.`/`..`/空段。 */
    private fun safeRelative(path: String): String? {
        if (path.isEmpty() || path.startsWith("/")) return null
        if (path.any { it == '\\' || it == '\u0000' || it.code < 0x20 }) return null
        val segments = path.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) return null
        return path
    }

    /** 有界读取：超过 [limit] 立即失败（不缓存半截 zip bomb）。 */
    private fun readBounded(input: InputStream, limit: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw IOException("ASSET_TOO_LARGE")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}

/**
 * `/data/adb/modules/<id>` 的只读投影 + 唯一写操作（`disable` 文件增删）。
 *
 * 字段口径与前端一致（`app.js` 的 `moduleCard`）：`id` 取**目录名**（KSU 的模块身份就是
 * `/data/adb/modules/<id>`，也是 toggle 的寻址键），其余字段**只在 module.prop 里有时才输出**；
 * `enabled`/`remove` 是文件事实；`hasAction`/`hasWebUi` 保留文件能力，执行端口未接线则不展示按钮。
 * `update` 需要远端版本信息，
 * daemon 没有 → 不输出（前端不显示「可更新」）。
 */
class DaemonModuleCatalog(
    private val modulesRoot: String,
    private val maxModules: Int = DaemonLiteWebLimits.MAX_MODULES,
) {
    fun listJson(): String {
        val root = File(modulesRoot)
        if (!root.isDirectory) return "[]"
        val rootPath = runCatching { root.canonicalPath }.getOrNull() ?: return "[]"
        val directories = runCatching { root.listFiles() }.getOrNull()
            ?.filter { runCatching { it.isDirectory }.getOrDefault(false) }
            ?.sortedBy { it.name }
            .orEmpty()
        val out = StringBuilder("[")
        var first = true
        var emitted = 0
        for (directory in directories) {
            if (emitted >= maxModules) break
            val id = directory.name
            if (!MODULE_ID.matches(id) || id == "." || id.contains("..")) continue
            // canonical path 复核：模块目录里可能有指向外部的符号链接。
            val canonical = runCatching { directory.canonicalPath }.getOrNull() ?: continue
            if (canonical != "$rootPath${File.separator}$id") continue
            val propsFile = File(directory, "module.prop")
            if (!runCatching { propsFile.isFile }.getOrDefault(false)) continue
            val props = readProps(propsFile) ?: continue
            // 单个模块渲染失败只跳过它（不产出半截 JSON）。
            val moduleJson = runCatching { renderModule(id, directory, props) }.getOrNull() ?: continue
            if (!first) out.append(',')
            first = false
            emitted++
            out.append(moduleJson)
        }
        return out.append(']').toString()
    }

    /** 只允许安全 id：与 `LiteWebHandler.validModuleId` 同源，但更严（≤64 且无 `..`）。 */
    fun setEnabled(moduleId: String, enabled: Boolean): LiteWebToggleView {
        if (!MODULE_ID.matches(moduleId) || moduleId == "." || moduleId.contains("..")) {
            return LiteWebToggleView.Failed(
                "MODULE_ID_INVALID",
                "module id must match [A-Za-z0-9._-]{1,64}",
            )
        }
        val root = File(modulesRoot)
        if (!runCatching { root.isDirectory }.getOrDefault(false)) {
            return LiteWebToggleView.Failed("MODULES_DIR_ABSENT", modulesRoot)
        }
        val rootPath = runCatching { root.canonicalPath }.getOrNull()
            ?: return LiteWebToggleView.Failed("MODULES_DIR_ABSENT", modulesRoot)
        val directory = File(root, moduleId)
        if (!runCatching { directory.isDirectory }.getOrDefault(false)) {
            return LiteWebToggleView.Failed("MODULE_NOT_FOUND", moduleId)
        }
        val canonical = runCatching { directory.canonicalPath }.getOrNull()
            ?: return LiteWebToggleView.Failed("MODULE_NOT_FOUND", moduleId)
        if (canonical != "$rootPath${File.separator}$moduleId") {
            return LiteWebToggleView.Failed("MODULE_PATH_REJECTED", moduleId)
        }
        if (!runCatching { File(directory, "module.prop").isFile }.getOrDefault(false)) {
            return LiteWebToggleView.Failed("MODULE_NOT_FOUND", moduleId)
        }
        if (runCatching { File(directory, "remove").exists() }.getOrDefault(false)) {
            return LiteWebToggleView.Failed(
                "MODULE_REMOVE_PENDING",
                "module is marked for removal; reboot before toggling",
            )
        }
        val disable = File(directory, "disable")
        if (enabled) {
            if (runCatching { disable.exists() }.getOrDefault(false)) {
                val deleted = runCatching { disable.delete() }.getOrDefault(false)
                if (!deleted && runCatching { disable.exists() }.getOrDefault(true)) {
                    return LiteWebToggleView.Failed("MODULE_TOGGLE_FAILED", "could not delete disable")
                }
                if (runCatching { disable.exists() }.getOrDefault(true)) {
                    return LiteWebToggleView.Failed("MODULE_TOGGLE_FAILED", "disable still present")
                }
            }
        } else {
            val written = runCatching { disable.writeBytes(ByteArray(0)) }.isSuccess
            if (!written || !runCatching { disable.isFile }.getOrDefault(false)) {
                return LiteWebToggleView.Failed("MODULE_TOGGLE_FAILED", "could not create disable")
            }
        }
        return LiteWebToggleView.Ok(moduleId, enabled)
    }

    /** 单个模块的 JSON（字段口径见类注释；缺的字段不输出）。 */
    private fun renderModule(id: String, directory: File, props: Map<String, String>): String {
        val out = StringBuilder(256)
        out.append('{')
        out.append("\"id\":").append(LiteWebJson.quote(id))
        for (field in TEXT_FIELDS) {
            val raw = props[field] ?: continue
            val value = sanitize(raw)
            if (value.isEmpty()) continue
            out.append(',').append(LiteWebJson.quote(field)).append(':').append(LiteWebJson.quote(value))
        }
        props["versionCode"]?.trim()?.toIntOrNull()?.let { out.append(",\"versionCode\":").append(it) }
        out.append(",\"enabled\":").append(!File(directory, "disable").exists())
        out.append(",\"remove\":").append(File(directory, "remove").exists())
        out.append(",\"hasAction\":").append(runCatching { File(directory, "action.sh").isFile }.getOrDefault(false))
        out.append(",\"hasWebUi\":").append(runCatching { File(directory, "webroot").isDirectory }.getOrDefault(false))
        out.append(",\"action\":false,\"web\":false,\"canWeb\":false")
        out.append(",\"backend\":\"ksu\",\"canToggle\":true")
        out.append('}')
        return out.toString()
    }

    private fun readProps(file: File): Map<String, String>? {
        val length = runCatching { file.length() }.getOrNull() ?: return null
        if (length <= 0 || length > DaemonLiteWebLimits.MAX_MODULE_PROP_BYTES) return null
        return runCatching {
            val values = LinkedHashMap<String, String>()
            file.useLines(Charsets.UTF_8) { lines ->
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                    val separator = trimmed.indexOf('=')
                    if (separator <= 0) continue
                    val key = trimmed.substring(0, separator).trim()
                    if (key.isEmpty() || key in values) continue
                    values[key] = trimmed.substring(separator + 1).trim()
                }
            }
            values
        }.getOrNull()
    }

    /** 去掉控制字符并截断：module.prop 是第三方不可信文本，前端用 textContent 渲染。 */
    private fun sanitize(value: String): String =
        value.filter { it.code >= 0x20 && it.code != 0x7f }
            .trim()
            .take(DaemonLiteWebLimits.MAX_MODULE_TEXT)

    companion object {
        val MODULE_ID = Regex("^[A-Za-z0-9._-]{1,64}$")
        private val TEXT_FIELDS = listOf("name", "version", "author", "description")
    }
}

/**
 * `/data/adb/maplesu/lite_hidden`（`1`/`0`）的读写。
 *
 * 读：文件缺失/不可读/内容不是 0/1 → null（status 输出 `hidden:null`，不冒充关闭）。
 * 写：tmp + fsync + 原子换入 + 回读校验；任何一步失败 → 结构化失败码，不报成功。
 */
class DaemonLiteHiddenPort(private val statePath: String) : LiteHiddenPort {

    override fun currentHidden(): Boolean? = runCatching {
        val file = File(statePath)
        if (!file.isFile) return null
        if (file.length() > DaemonLiteWebLimits.MAX_HIDDEN_FILE_BYTES) return null
        when (file.readText(StandardCharsets.UTF_8).trim()) {
            "1" -> true
            "0" -> false
            else -> null
        }
    }.getOrNull()

    override fun setHidden(hidden: Boolean, level: String?): LiteHiddenResult {
        val file = File(statePath)
        val parent = file.parentFile
        if (parent == null) {
            return LiteHiddenResult.Failed("LITE_HIDDEN_WRITE_FAILED", "state path has no parent")
        }
        if (!parent.isDirectory && !parent.mkdirs() && !parent.isDirectory) {
            return LiteHiddenResult.Failed("LITE_HIDDEN_WRITE_FAILED", "mkdir failed: ${parent.path}")
        }
        val temporary = File(parent, ".${file.name}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write((if (hidden) "1\n" else "0\n").toByteArray(StandardCharsets.US_ASCII))
                output.flush()
                output.fd.sync()
            }
            runCatching {
                Files.setPosixFilePermissions(
                    temporary.toPath(),
                    PosixFilePermissions.fromString("rw-------"),
                )
            }
            try {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            runCatching {
                Files.setPosixFilePermissions(
                    file.toPath(),
                    PosixFilePermissions.fromString("rw-------"),
                )
            }
        } catch (error: IOException) {
            runCatching { temporary.delete() }
            return LiteHiddenResult.Failed(
                "LITE_HIDDEN_WRITE_FAILED",
                error.message ?: error.javaClass.simpleName,
            )
        } finally {
            runCatching { temporary.delete() }
        }
        val readback = currentHidden()
        if (readback != hidden) {
            return LiteHiddenResult.Failed(
                "LITE_HIDDEN_READBACK_FAILED",
                "readback=${readback ?: "unknown"} expected=$hidden",
            )
        }
        return LiteHiddenResult.Ok(hidden, if (hidden) "hidden enabled" else "hidden disabled")
    }
}

/**
 * `/data/adb/maplesu/logs/su.log` 的有界尾部读取（daemon 不 tail、不常驻 fd）。
 *
 * 文件不存在 → `available=false` + 原因；存在但读失败 → 同样显式不可用（不是空日志）。
 */
class DaemonSuLog(
    private val logPath: String,
    private val maxBytes: Long = DaemonLiteWebLimits.MAX_SULOG_BYTES,
    private val maxLines: Int = DaemonLiteWebLimits.MAX_SULOG_LINES,
) {
    fun read(): LiteWebSulogView {
        val file = File(logPath)
        if (!runCatching { file.isFile }.getOrDefault(false)) {
            return LiteWebSulogView(false, reason = "SULOG_NOT_FOUND")
        }
        val length = runCatching { file.length() }.getOrNull()
            ?: return LiteWebSulogView(false, reason = "SULOG_READ_FAILED")
        if (length <= 0L) return LiteWebSulogView(true, emptyList())
        val start = (length - maxBytes).coerceAtLeast(0L)
        val bytes = runCatching {
            RandomAccessFile(file, "r").use { handle ->
                handle.seek(start)
                val buffer = ByteArray((length - start).toInt())
                handle.readFully(buffer)
                buffer
            }
        }.getOrNull() ?: return LiteWebSulogView(false, reason = "SULOG_READ_FAILED")
        var text = String(bytes, StandardCharsets.UTF_8)
        if (start > 0L) text = text.substringAfter('\n', text)
        val lines = text.split('\n')
            .map { it.trimEnd('\r') }
            .filter { it.isNotEmpty() }
            .takeLast(maxLines)
        return LiteWebSulogView(true, lines)
    }
}

/** `/proc/self/status` 的 `Uid:` 行（纯 JDK；daemon 没有 Android 的 `Process.myUid()`）。 */
object DaemonProcStatus {
    fun uid(path: String = "/proc/self/status"): Int? = runCatching {
        File(path).useLines { lines -> lines.firstOrNull { it.startsWith("Uid:") } }
    }.getOrNull()
        ?.removePrefix("Uid:")
        ?.trim()
        ?.split(Regex("\\s+"))
        ?.firstOrNull()
        ?.toIntOrNull()
}

/**
 * 当前进程 classpath 里的 APK/JAR（`app_process` 的 CLASSPATH 就是一份 APK）。
 *
 * 纯 JDK：从 code source 反查，不需要 Context；拿不到（宿主用 DexClassLoader 且没有
 * code source）就返回 null —— 这只是静态资源的**备用**来源，不是必需路径。
 */
object DaemonClasspathApk {
    fun path(marker: Class<*> = LiteWebDaemonMain::class.java): String? = runCatching {
        val location = marker.protectionDomain?.codeSource?.location ?: return null
        val file = File(location.toURI())
        if (file.isFile) file.absolutePath else null
    }.getOrNull()
}

/**
 * 既有 `GET /x7k9f/<token>/status` 契约的响应体：保留 `service` 标记（App 侧探活/排障读它），
 * 同时带上 WebUI 需要的 Lite 字段（与 `LiteWebHandler` 的 `/api/status` 同源同值）。
 *
 * `superusers` 额外标注 daemon 读不到授权库（前端 `/api/superusers` 只接受数组，所以在
 * 这里把「不可用」这个事实表达出来，而不是让 `[]` 看起来像「零个授权用户」）。
 */
object DaemonLiteWebStatus {
    const val SERVICE = "maplesu-liteweb-daemon"

    fun render(backend: LiteWebBackend): String {
        val status = backend.status()
        val hidden = runCatching { backend.hiddenPort().currentHidden() }.getOrNull()
        return buildString {
            append("{\"ok\":true,\"service\":").append(LiteWebJson.quote(SERVICE))
            append(",\"mode\":").append(LiteWebJson.quote(status.mode))
            append(",\"root\":").append(status.rootAvailable)
            append(",\"moduleCount\":").append(status.moduleCount)
            if (status.moduleCountError.isNotEmpty()) {
                append(",\"moduleCountError\":").append(LiteWebJson.quote(status.moduleCountError))
            }
            append(",\"hidden\":").append(hidden?.toString() ?: "null")
            append(",\"uapi\":").append(LiteWebJson.quote(status.uapi))
            append(",\"serverVersion\":").append(status.serverVersion)
            append(",\"webuiPort\":").append(backend.configuredPort())
            append(",\"superusers\":").append(LiteWebJson.quote("unavailable"))
            append("}")
        }
    }
}
