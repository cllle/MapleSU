package dev.maplesu.manager.modules

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import com.maplesu.core.R
import dev.maplesu.manager.ModuleArchiveInspector
import dev.maplesu.manager.ModuleArchiveInspector.Feature
import dev.maplesu.manager.ModuleArchiveInspector.ModuleType
import dev.maplesu.manager.ModuleArchiveInspector.Report
import dev.maplesu.manager.runtime.RootManagement
import dev.maplesu.manager.workmode.ModeRuntime
import org.apache.commons.compress.archivers.zip.ZipFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Confirmed packages and their executable revisions have deliberately separate storage. */
class LiteScriptModuleBackend @JvmOverloads constructor(
    context: Context,
    private val scriptRunner: ModuleScriptRunner = ManagementModuleScriptRunner(context),
) {
    private val context = context.applicationContext
    private val catalogRoot = File(this.context.filesDir, "module-catalog")
    private val catalog = ModuleCatalog(catalogRoot)
    private val runtimeRoot = File(this.context.filesDir, "module-runtime")
    private val revisions = File(runtimeRoot, "revisions").apply { mkdirs() }
    private val staging = File(runtimeRoot, "staging").apply { mkdirs() }
    private val receipts = File(runtimeRoot, "receipts").apply { mkdirs() }
    private val rootTrees = RootModuleTree(scriptRunner)
    @Volatile private var rootSnapshot: Map<String, RootModuleTree.Snapshot> = emptyMap()
    private data class ScriptEntry(val report: Report, val archive: File?, val enabled: Boolean, val disk: Boolean = false,
                                   val catalogToken: String? = null, val pendingReport: Report? = null)
    @Volatile private var diskEntries: Map<String, ScriptEntry> = emptyMap()
    private var earlyScriptDeadlineMillis = 0L
    private var inventoryReadAt = Long.MIN_VALUE
    private var inventoryActivation: Any? = null
    @Volatile private var catalogReadError: String? = null

    // Zygisk must read the complete module before system_server starts, before unlock.
    // Keep catalogs/receipts as they are; only executable Zygisk revisions use DE.
    private val earlyRoot = File(this.context.createDeviceProtectedStorageContext().filesDir, "module-zygisk")
    private val earlyRevisions = File(earlyRoot, "revisions").apply { mkdirs() }
    private val earlyStaging = File(earlyRoot, "staging").apply { mkdirs() }
    private val retiredZygisk = File(earlyRoot, "retired").apply { mkdirs() }

    data class InstallResult(val code: Int, val needsReboot: Boolean,
                             val rootCommitted: Boolean = false, val archiveError: String? = null,
                             val commitUnknown: Boolean = false)
    internal data class BootTask(
        val id: String,
        val sha256: String,
        val stage: String,
        val bootId: String,
        /** T2: a public `<stage>.d` task, not tied to a module. */
        val common: Boolean = false,
    )

    /** One immutable display observation; projecting host capabilities performs no I/O. */
    class ListInventory internal constructor(private val json: String, zygiskRows: Set<Int>) {
        private val zygiskRows = zygiskRows.toSet()

        fun toJson(zygiskAvailable: Boolean): String {
            if (!zygiskAvailable || zygiskRows.isEmpty()) return json
            return JSONArray(json).apply {
                zygiskRows.forEach { index ->
                    getJSONObject(index).put("unsupported", JSONArray(
                        LiteModuleRuntimeState.unsupportedCapabilities(true, zygiskAvailable)))
                }
            }.toString()
        }
    }

    @JvmOverloads
    fun listJson(zygiskAvailable: Boolean = false): String = readListInventory().toJson(zygiskAvailable)

    fun readListInventory(): ListInventory {
        val requestedAt = System.nanoTime()
        return synchronized(installLock) {
        val stored = refreshTreesForRead(requestedAt)
        synchronized(lock) {
        check(!migrationCleanupRunning) { "MODULE_CLEANUP_RUNNING" }
        val boot = bootId()
        val currentEntries = entries(stored)
        check(catalogReadError == null || currentEntries.isNotEmpty()) {
            "MODULE_ARCHIVE_INVENTORY_FAILED:$catalogReadError"
        }
        val zygiskRows = mutableSetOf<Int>()
        val json = JSONArray().apply {
            currentEntries.filter { it.report.type == ModuleType.KERNEL_SU }.forEach { entry ->
                val report = entry.report
                val display = entry.pendingReport ?: report
                val pending = snapshot(report).pending?.takeIf { it.directory && it.revision.matches(Regex("[0-9a-f]{64}")) }
                val tree = tree(report)
                val state = if (receipt(report).isFile) state(report) else null
                val enabled = snapshot(report).enabled
                // An existing KSU tree is installed even without our private execution receipt.
                // Merely listing it never grants boot execution or changes its ownership.
                val retained = entry.disk && !owns(report)
                val installed = regular(File(tree, "module.prop")) && (entry.disk || state != null && owns(report))
                val currentStages = state?.stages?.filterValues { it.bootId == boot }.orEmpty()
                val missingOwner = currentStages.any { (stage, value) ->
                    value.status in setOf("queued", "running") && taskKey(report, stage) !in scheduled
                }
                val lateScripts = bootScripts.filter { regular(File(tree, "$it.sh")) }
                val hasProps = regular(File(tree, "system.prop"))
                val hasMount = needsMount(tree) ||
                    (Feature.METAMODULE in report.features && regular(File(tree, "metamount.sh")))
                val hasPolicy = regular(File(tree, "sepolicy.rule"))
                val status = when {
                    snapshot(report).removed -> "remove_pending"
                    !installed -> "stored"
                    !enabled -> "disabled"
                    retained -> ""
                    missingOwner -> "interrupted"
                    currentStages.any { it.value.status in setOf("failed", "interrupted") } -> "failed"
                    currentStages.any { it.value.status == "running" } -> "running"
                    state?.propsBootId == boot && state.propsFailed.isNotEmpty() -> "failed"
                    lateScripts.isEmpty() && !hasProps && !hasMount && !hasPolicy -> "installed"
                    lateScripts.all { currentStages[it]?.status in setOf("ok", "started") } &&
                        (!hasProps || state?.propsBootId == boot) &&
                        (!hasMount || currentStages[MOUNT_STAGE]?.status == "ok") &&
                        (!hasPolicy || currentStages[POLICY_STAGE]?.status in setOf("ok", "skipped")) -> "late_completed"
                    else -> "late_pending"
                }
                val errorCode = currentStages.values.firstOrNull { it.status in setOf("failed", "interrupted") }?.exitCode
                val usesZygisk = hasZygisk(report)
                if (usesZygisk) zygiskRows += length()
                put(JSONObject().apply {
                    display.properties.forEach { (name, value) -> put(name, value) }
                    put("id", externalId(report))
                    put("name", display.properties["name"] ?: report.id)
                    put("versionCode", display.versionCode)
                    put("enabled", installed && enabled)
                    // Pending is a root fact, independent of this Manager's CE receipt.
                    put("update", pending != null && "update" in snapshot(report).files)
                    put("remove", snapshot(report).removed)
                    put("web", installed && regular(File(tree, "webroot/index.html")))
                    put("action", installed && regular(File(tree, "action.sh")))
                    // T5: metamodule is a declared fact, never inferred from a name.
                    put("metamodule", Feature.METAMODULE in display.features)
                    put("runtimeStatus", status)
                    put("needsReboot", pending != null || (!retained && state?.needsReboot(boot, enabled,
                        lateScripts.isNotEmpty() || hasProps || hasMount || hasPolicy || hasZygisk(report)) == true))
                    catalogReadError?.let { put("archiveError", it) }
                    put("canToggle", installed && canManage(entry) && !snapshot(report).removed)
                    put("runtimeExitCode", errorCode ?: JSONObject.NULL)
                    // T4: a module can be installable and still have no working consumer
                    // for these capabilities. They are reported, never silently ignored.
                    put("stages", JSONObject().apply {
                        state?.stages?.forEach { (name, value) ->
                            put(name, JSONObject().apply {
                                put("status", value.status)
                                value.exitCode?.let { put("code", it) }
                                value.detail?.takeIf { it.isNotEmpty() }?.let { put("detail", it) }
                            })
                        }
                    })
                    put("props", JSONObject().apply {
                        put("applied", JSONArray(state?.propsApplied.orEmpty()))
                        put("failed", JSONArray(state?.propsFailed.orEmpty()))
                    })
                    put("unsupported", JSONArray(LiteModuleRuntimeState.unsupportedCapabilities(
                        usesZygisk, false)))
                    put("builtin", false)
                    put("backend", "lite_script")
                })
            }
        }.toString()
        ListInventory(json, zygiskRows)
        }
        }
    }

    fun install(source: File, report: Report, stdout: (String) -> Unit, stderr: (String) -> Unit): InstallResult {
        backgroundThread()
        check(!migrationCleanupRunning) { "MODULE_CLEANUP_RUNNING" }
        if (dev.maplesu.manager.runtime.ultra.UltraEnvironmentProbe.isSafeMode(context)) {
            stderr(context.getString(R.string.safe_mode_module_disabled))
            return InstallResult(1, false)
        }
        if (report.type != ModuleType.KERNEL_SU) {
            stderr(context.getString(R.string.module_maplesu_not_supported))
            return InstallResult(1, false)
        }
        return synchronized(installLock) {
            check(!migrationCleanupRunning) { "MODULE_CLEANUP_RUNNING" }
            val staged = catalog.stage(source, report.type, report.sha256)
            var reserved = false
            var committed = false
            var commitAttempted = false
            var extracted: File? = null
            var rootTransaction = false
            var cleanupError: Throwable? = null

            val result = run installation@{
            try {
                refreshTrees()
                synchronized(lock) {
                    val previous = entryFor(report.type, report.id)
                    check(scheduled.none { it.startsWith(moduleKey(report) + ":") ||
                        (previous != null && it.startsWith(moduleKey(previous.report) + ":")) }) {
                        "A module script is still running."
                    }
                    check(installing.add(identityKey(report))) { "Module installation is already running." }
                    reserved = true
                }
                refreshTrees()
                val previous = entryFor(report.type, report.id)
                rootTrees.recover(report.id, if (previous?.disk == true) snapshot(previous.report).revision
                    else previous?.report?.let(::moduleKey).orEmpty(), previous?.catalogToken)
                previous?.let { migrateEarlyRevision(it.report) }
                refreshTrees()
                val archive = File(catalogRoot, "staged/${staged.token}/archive.zip")
                val temporary = File(if (hasZygisk(report)) earlyStaging else staging,
                    UUID.randomUUID().toString()).apply { check(mkdir()) }
                extracted = temporary
                val scripts = extract(archive, temporary)
                LiteScriptModulePolicy.check(report, scripts)
                // Bundled tools are published before any module script runs, so a
                // module that calls resetprop resolves MapleSU's own copy instead of
                // whatever another Root manager left on PATH. Publishing is a file
                // operation only: it performs no mount and no policy work.
                deployBundledTools()
                // Only selected modules supply policy. Include the installed active set
                // so declarations from another module are available to this installer.
                val policyCode = if (regular(File(temporary, "sepolicy.rule")))
                    applyModulePolicies(temporary, stdout, stderr)[File(temporary, "sepolicy.rule").absolutePath] ?: 1 else 0
                // KSU parses only the script it executes, using its bundled ash.
                // Other .sh payloads may target another interpreter or be replaced
                // by customize.sh; pre-parsing the whole archive rejects valid installs.
                ModuleArchiveInspector.verifyUnchanged(archive, report)
                check(ModuleTree.allowsPublish(ModeRuntime.active)) { "MODULE_TREE_MODE_REJECTED" }
                rootTransaction = true
                val installDirectory = File(rootTrees.preparePendingInstall(report.id, temporary.absolutePath, moduleKey(report),
                    if (previous?.disk == true) snapshot(previous.report).revision.takeIf { it.isNotEmpty() }
                    else previous?.report?.let(::moduleKey), legacyPaths(report), previous?.enabled ?: true,
                    if (previous?.disk == true) snapshot(previous.report).metadata?.let { sha256(it.toByteArray()) } else null,
                    staged.token))
                stdout("Installing ${report.properties["name"] ?: report.id} as an Ultra script module.")
                // Install-time contract, mirroring installer.sh:392-430. A module that
                // ships `install.sh` is a legacy module: it is sourced and driven
                // through the print_modname/on_install/set_permissions callbacks and
                // the SKIPMOUNT/PROPFILE/POSTFSDATA/LATESTARTSERVICE flags. Otherwise a
                // `customize.sh` module is sourced and owns its own extraction when it
                // declares the bare `SKIPUNZIP=1` line.
                val legacy = "install.sh" in scripts
                val customize = "customize.sh" in scripts
                stdout("MAPLESU_INSTALL_TREE path=${installDirectory.absolutePath} " +
                    "rootDirectory=true legacy=$legacy customize=$customize")
                // The installer also owns modules with no customize.sh and lets the
                // active metamodule wrap either the modern or legacy installer.
                val result = execute(report, installDirectory, "customize", archive, stdout, stderr)
                if (result != 0) return@installation InstallResult(result, false)
                // The default KSU path publishes only metadata; executable bytes stay pending.
                // Customize may generate rules, so inspect the real candidate through Root.
                rootSnapshot = rootSnapshot + rootTrees.snapshot(listOf(report.id))
                // The root journal publishes a metamodule pointer together with its live
                // stub and pending candidate, before the same atomic commit marker.
                var installedState = LiteModuleRuntimeState(bootId()).withStage("install", bootId(), "ok", 0)
                if (snapshot(report).pending?.files?.contains("sepolicy.rule") == true) {
                    // Customize may generate/change the rules after the initial load.
                    val code = applyModulePolicies(installDirectory, stdout, stderr)[File(installDirectory, "sepolicy.rule").absolutePath] ?: 1
                    installedState = installedState.withStage(POLICY_STAGE, bootId(),
                        LiteModuleRuntimeState.policyStageStatus(code), code)
                } else if (policyCode != 0) {
                    installedState = installedState.withStage(POLICY_STAGE, bootId(),
                        LiteModuleRuntimeState.policyStageStatus(policyCode), policyCode)
                }
                commitAttempted = true
                rootTrees.commitPendingInstall(report.id, moduleKey(report), staged.token, Feature.METAMODULE in report.features)
                committed = true
                var hotApplied = false
                var hotUnknown = false
                // The committed candidate is the only source of a module's hot request.
                // A failed/uncertain hook never rolls back the already committed install.
                try {
                    val hot = rootTrees.hotInstall(report.id, moduleKey(report), staged.token,
                        Feature.METAMODULE in report.features,
                        dev.maplesu.manager.runtime.ultra.UltraEnvironmentProbe.isSafeMode(context), stdout, stderr)
                    hotApplied = hot.applied
                    if (hot.requested) installedState = installedState.withStage("hot-install", bootId(),
                        if (hot.status == "applied") "ok" else hot.status, hot.code)
                } catch (error: Exception) {
                    hotUnknown = true
                    installedState = installedState.withStage("hot-install", bootId(), "interrupted", 75,
                        error.message ?: error.javaClass.simpleName)
                    stderr("MODULE_HOT_RESULT_UNKNOWN: ${error.message}; the root install remains committed")
                }
                // Root is already committed. CE failures preserve the staged archive
                // and report their own outcome rather than rolling back executable state.
                val archiveFailure = runCatching {
                    installedState.save(receipt(report))
                    catalog.commit(staged.token)
                }.exceptionOrNull()?.let {
                    stderr("MODULE_ROOT_COMMITTED_ARCHIVE_FAILED: ${it.message}")
                    it.message ?: it.javaClass.simpleName
                }
                if (!hotUnknown) runCatching { rootTrees.recover(report.id, "") }
                    .onFailure { stderr("MODULE_COMMIT_CLEANUP_PENDING: ${it.message}") }
                rootSnapshot = rootSnapshot + rootTrees.snapshot(listOf(report.id))
                val live = snapshot(report)
                val bootWork = bootScripts.any { "$it.sh" in live.files } ||
                    "system.prop" in live.files || "sepolicy.rule" in live.files ||
                    needsMount(tree(report)) || live.zygisk || "initrc.d" in live.directories
                val needsReboot = !hotApplied || bootWork
                stdout(if (hotApplied) "Installed. The module is live; boot-only work waits for the next boot."
                    else "Installed. The pending module will activate at the next boot.")
                InstallResult(0, needsReboot, rootCommitted = true, archiveError = archiveFailure)
            } catch (error: Exception) {
                if (error is RootModuleTree.PendingCommitRejected) commitAttempted = false
                val unknown = commitAttempted && !committed
                if (unknown) stderr("MODULE_COMMIT_UNKNOWN token=${staged.token} stage=staged/${staged.token}; retained for diagnosis")
                stderr(error.message ?: "Module installation failed.")
                InstallResult(if (committed) 0 else if (unknown) 75 else 1, committed, rootCommitted = committed,
                    archiveError = if (committed) error.message ?: error.javaClass.simpleName else null,
                    commitUnknown = unknown)
            } finally {
                extracted?.let {
                    runCatching { removeTree(it, if (it.parentFile == earlyStaging) earlyStaging else staging) }
                        .onFailure { if (cleanupError == null) cleanupError = it }
                }
                if (!committed && !commitAttempted && rootTransaction) {
                    runCatching { rootTrees.recover(report.id, "") }
                        .onFailure { if (cleanupError == null) cleanupError = it }
                    // Do not erase the previous receipt when reinstalling the same revision.
                    runCatching {
                        if (catalog.get(report.type, report.id)?.report?.sha256 != report.sha256) {
                            Files.deleteIfExists(receipt(report).toPath())
                        }
                    }.onFailure { if (cleanupError == null) cleanupError = it }
                }
                // An uncertain commit reply retains the journal and stage for root recovery.
                if (!committed && !commitAttempted) runCatching { catalog.discard(staged.token) }
                    .onFailure { if (cleanupError == null) cleanupError = it }
                if (reserved) synchronized(lock) { installing.remove(identityKey(report)) }
                invalidateInventory()
            }
            }
            if (result.code != 0 && !committed && !commitAttempted) {
                stderr(context.getString(if (cleanupError == null) R.string.module_install_failed_rolled_back
                    else R.string.module_install_failed_cleanup_pending))
                cleanupError?.let { android.util.Log.w("MapleSUModules", "Module rollback cleanup failed: ${report.id}", it) }
            }
            result
        }
    }

    fun setEnabled(id: String, enabled: Boolean): Boolean = changeModule(id) { entry ->
        require(entry.report.type == ModuleType.KERNEL_SU) { "The native module loader is not active." }
        migrateEarlyRevision(entry.report)
        refreshTrees()
        check(!snapshot(entry.report).removed) { "This module is pending removal." }
        require(canManage(entry) && directory(tree(entry.report))) { "The script module is not installed." }
        setModuleMarker(entry.report, "disable", !enabled)
        try {
            if (!entry.disk) catalog.setEnabled(entry.report.type, entry.report.id, enabled)
        } catch (error: Exception) {
            android.util.Log.w("MapleSUModules", "Module marker committed; catalog preference failed: $id", error)
        }
        if (!enabled) cancel(entry.report)
        android.util.Log.i("MapleSUModules", "MAPLESU_MODULE_TOGGLE id=${entry.report.id} enabled=$enabled markerVerified=true")
        true
    }

    fun markRemove(id: String, remove: Boolean): Boolean = changeModule(id) { entry ->
        if (entry.report.type == ModuleType.MAPLESU) {
            require(remove)
            catalog.uninstall(entry.report.type, entry.report.id)
        } else {
            if (!entry.disk) {
                migrateEarlyRevision(entry.report)
                refreshTrees()
            }
            val owned = owns(entry.report)
            val identity = snapshot(entry.report).identity
            check(canManage(entry)) {
                "MODULE_TREE_OWNER_CHANGED"
            }
            val current = if (receipt(entry.report).isFile) state(entry.report) else LiteModuleRuntimeState(bootId())
            if (!remove) check(current.canCancelRemoval(bootId())) { "The uninstall script has already started." }
            setModuleMarker(entry.report, "remove", remove)
            try {
                synchronized(lock) {
                    // A script may have completed while the root marker was being written.
                    val latest = if (receipt(entry.report).isFile) state(entry.report) else current
                    latest.copy(remove = remove, removeRequestedBootId = if (remove) bootId() else null,
                        removalIdentity = if (remove && !owned) identity else null).save(receipt(entry.report))
                }
            } catch (error: Exception) {
                android.util.Log.w("MapleSUModules", "Module removal marker committed; receipt failed: $id", error)
            }
            if (remove) cancel(entry.report)
        }
        true
    }

    /** Reserve the module while root I/O runs; list reads and cancellation keep their state monitor. */
    private fun <T> changeModule(id: String, mutation: (ScriptEntry) -> T): T = synchronized(installLock) {
        refreshTrees()
        val entry = synchronized(lock) {
            check(!migrationCleanupRunning) { "MODULE_CLEANUP_RUNNING" }
            resolve(id).also {
                check(installing.add(identityKey(it.report))) { "Module installation is running." }
            }
        }
        try { mutation(entry) } finally {
            synchronized(lock) { installing.remove(identityKey(entry.report)) }
            invalidateInventory()
        }
    }

    fun action(id: String, stdout: (String) -> Unit, stderr: (String) -> Unit): Int {
        backgroundThread()
        check(!dev.maplesu.manager.runtime.ultra.UltraEnvironmentProbe.isSafeMode(context)) { "ULTRA_SAFE_MODE" }
        refreshTrees(id.takeUnless { it.startsWith("maplesu:") })
        val (entry, expectedTree) = synchronized(lock) {
            check(!migrationCleanupRunning) { "MODULE_CLEANUP_RUNNING" }
            resolve(id).also {
                check(identityKey(it.report) !in installing) { "Module installation is running." }
                require(it.report.type == ModuleType.KERNEL_SU && snapshot(it.report).enabled && !state(it.report).remove) {
                    "The script module is disabled or pending removal."
                }
                check(canRun(it.report)) { "MODULE_TREE_OWNER_CHANGED" }
                check(regular(File(tree(it.report), "action.sh"))) { "This module has no action script." }
                check(taskKey(it.report, "action") !in scheduled) { "The module action is already running." }
                scheduled.add(taskKey(it.report, "action"))
                updateStage(it.report, "action", bootId(), "queued")
            }.let { it to snapshot(it.report) }
        }
        return runRecorded(entry.report, "action", bootId(), stdout, stderr, expectedTree)
    }

    internal fun hasScheduledBootTasks(): Boolean = synchronized(lock) {
        scheduled.any { !it.endsWith(":action") && !it.endsWith(":customize") }
    }

    internal fun prepareBootTasks(): List<BootTask> {
        if (migrationCleanupRunning) return emptyList()
        if (dev.maplesu.manager.runtime.ultra.UltraEnvironmentProbe.isSafeMode(context)) return emptyList()
        // KSU Until: one shared budget for common/module post-fs-data and post-mount.
        earlyScriptDeadlineMillis = SystemClock.elapsedRealtime() + 35_000L
        val boot = bootId()
        if (rootTrees.sdkOwnsBoot(boot)) return emptyList()
        // Migration and root inventory are I/O, not in-memory scheduling operations.
        // Reserve each module while migrating, without monopolizing the state monitor.
        val currentEntries = synchronized(installLock) {
            val stored = refreshTrees()
            collectRetiredZygisk(boot, stored)
            entries(stored).also { current ->
                current.filter { it.report.type == ModuleType.KERNEL_SU }.forEach { entry ->
                    val reserved = synchronized(lock) { !migrationCleanupRunning && installing.add(identityKey(entry.report)) }
                    if (reserved) try { migrateEarlyRevision(entry.report) }
                    finally { synchronized(lock) { installing.remove(identityKey(entry.report)) } }
                }
            }
        }
        return queueBootTasks(currentEntries, boot)
    }

    private fun queueBootTasks(currentEntries: List<ScriptEntry>, boot: String): List<BootTask> {
        return synchronized(lock) {
        if (migrationCleanupRunning) return@synchronized emptyList()
        val tasks = mutableListOf<BootTask>()
        // T2: the public `<stage>.d` hooks run before that stage's module scripts. The
        // directory check happens on the Root side (the Manager UID cannot stat
        // /data/adb), so an absent directory is a no-op rather than a failure.
        val commonFile = File(receipts, COMMON_RECEIPT)
        val common = Properties().apply {
            if (commonFile.isFile) commonFile.inputStream().use { load(it) }
        }
        val active = activeModules(currentEntries)
        // No active policy files means no loader invocation, including on app startup.
        if (active.any { regular(File(tree(it.report), "sepolicy.rule")) } &&
            common.getProperty("stage.$POLICY_STAGE.bootId") != boot && scheduled.add("common:$POLICY_STAGE")) {
            tasks.add(BootTask("", "", POLICY_STAGE, boot, common = true))
        }
        // Also reset our previous mounts when a disabled/removed module is present.
        // The helper touches only its exact recorded mount IDs; an absent ledger is a no-op.
        if (currentEntries.any { it.report.type == ModuleType.KERNEL_SU } &&
            common.getProperty("stage.$MOUNT_STAGE.bootId") != boot && scheduled.add("common:$MOUNT_STAGE")) {
            tasks.add(BootTask("", "", MOUNT_STAGE, boot, common = true))
        }
        bootScripts.forEach { stage ->
            if (common.getProperty("stage.$stage.bootId") != boot && scheduled.add("common:$stage")) {
                tasks.add(BootTask("", "", stage, boot, common = true))
            }
        }
        currentEntries.filter { it.report.type == ModuleType.KERNEL_SU }.forEach { entry ->
            val report = entry.report
            if (identityKey(report) in installing) return@forEach
            // The standard remove marker is durable even if app-private receipts were lost.
            // Bind that explicit removal to the observed tree before scheduling uninstall.
            if (!receipt(report).exists() && snapshot(report).removed && canManage(entry)) {
                writeReceipt(report, LiteModuleRuntimeState("00000000-0000-0000-0000-000000000000",
                    remove = true, removalIdentity = if (owns(report)) null else snapshot(report).identity))
            }
            if (!directory(tree(report)) || (!receipt(report).exists() && !foreignDisk(report))) return@forEach
            val state = state(report)
            if (if (state.remove) !canRemove(report, state) else !canRun(report)) return@forEach
            val stages = state.pendingStages(boot, snapshot(report).enabled,
                bootScripts.filter { regular(File(tree(report), "$it.sh")) })
            stages.forEach { stage ->
                if (state.stages[stage]?.bootId != boot && scheduled.add(taskKey(report, stage))) {
                    updateStage(report, stage, boot, "queued")
                    tasks.add(BootTask(report.id, report.sha256, stage, boot))
                }
            }
            // T5: the metamodule's mount hook is dispatched, never implemented. KSU
            // runs it after the post-fs-data scripts (init_event.rs:110-118); the same
            // relative position is kept here even though every stage runs late.
            if (Feature.METAMODULE in report.features && snapshot(report).enabled && !state.remove &&
                state.installedBootId != boot && regular(File(tree(report), "metamount.sh")) &&
                state.stages[MOUNT_STAGE]?.bootId != boot &&
                scheduled.add(taskKey(report, MOUNT_STAGE))) {
                tasks.add(BootTask(report.id, report.sha256, MOUNT_STAGE, boot))
            }
            // KSU applies system.prop after post-fs-data and before mounting. This
            // backend preserves that order within its late boot dispatch.
            if (snapshot(report).enabled && !state.remove && state.installedBootId != boot &&
                state.propsBootId != boot && (regular(File(tree(report), "system.prop")) ||
                    regular(File(tree(report), "post-fs-data.sh"))) &&
                scheduled.add(taskKey(report, PROPS_STAGE))) {
                tasks.add(BootTask(report.id, report.sha256, PROPS_STAGE, boot))
            }
        }
        val metamodules = entries().filter { Feature.METAMODULE in it.report.features }.map { it.report.id }.toSet()
        tasks.sortedWith(compareBy({ LiteModuleRuntimeState.bootStageOrder.indexOf(it.stage) },
            { if (it.common) 0 else if (it.id in metamodules) 1 else 2 }))
        }
    }

    internal fun runBootTask(task: BootTask, stdout: (String) -> Unit, stderr: (String) -> Unit) {
        refreshTreesForRead()
        if (task.common) {
            try {
                when (task.stage) {
                    POLICY_STAGE -> {
                        val entries = synchronized(lock) { activeModules() }
                            .filter { regular(File(tree(it.report), "sepolicy.rule")) }
                        val results = applyModulePolicies(null, stdout, stderr)
                        synchronized(lock) {
                            entries.forEach { entry ->
                                val code = results[File(tree(entry.report), "sepolicy.rule").absolutePath] ?: 1
                                updateStage(entry.report, POLICY_STAGE, task.bootId,
                                    LiteModuleRuntimeState.policyStageStatus(code), code)
                            }
                        }
                        writeCommonReceipt(task.stage, task.bootId, entries.map { entry ->
                            entry.report.id to (results[File(tree(entry.report), "sepolicy.rule").absolutePath] ?: 1)
                        })
                    }
                    MOUNT_STAGE -> runModuleMounts(task.bootId, stdout, stderr)
                    else -> runStageDirectory(task.stage, task.bootId, stdout, stderr)
                }
            } finally {
                synchronized(lock) { scheduled.remove("common:${task.stage}") }
            }
            return
        }
        val report = synchronized(lock) {
            entryFor(ModuleType.KERNEL_SU, task.id)?.takeIf { it.report.sha256 == task.sha256 }?.report
        } ?: return
        if (task.stage == PROPS_STAGE) {
            // The receipt is written inside applySystemProperties (task book §0.2).
            try {
                // post-fs-data may have just created system.prop.
                refreshTrees()
                applySystemProperties(report, stdout, stderr)
            } finally {
                synchronized(lock) { scheduled.remove(taskKey(report, PROPS_STAGE)) }
            }
            return
        }
        // The metamodule may own uninstall work even without a module uninstall.sh.
        val code = runRecorded(report, task.stage, task.bootId, stdout, stderr)
        if (task.stage == MOUNT_STAGE && Feature.METAMODULE in report.features) synchronized(lock) {
            activeModules().filter { needsMount(tree(it.report)) && state(it.report).installedBootId != task.bootId }
                .forEach { updateStage(it.report, MOUNT_STAGE, task.bootId,
                    if (code == 0) "ok" else "failed", code, "metamodule:${report.id}") }
        }
        // KSU records hook errors and still prunes the module. An interrupted dispatch
        // keeps its marker so a later boot can finish the same removal.
        if (task.stage == "uninstall" && !Thread.currentThread().isInterrupted) {
            changeModule(externalId(report)) { current ->
                check(current.report.sha256 == report.sha256) { "The module revision changed." }
                removeInstalled(report)
            }
        }
    }

    internal fun abandonBootTask(task: BootTask) {
        val (key, process) = synchronized(lock) {
            if (task.common) {
                scheduled.remove("common:${task.stage}")
                return
            }
            val entry = entryFor(ModuleType.KERNEL_SU, task.id) ?: return
            if (entry.report.sha256 != task.sha256) return
            val key = taskKey(entry.report, task.stage)
            if (state(entry.report).stages[task.stage]?.status in setOf("queued", "running"))
                updateStage(entry.report, task.stage, task.bootId, "interrupted", 137)
            key to running[key]
        }
        process?.destroyForcibly()
        synchronized(lock) { scheduled.remove(key) }
    }

    internal fun cancelBootTasks() {
        val active = synchronized(lock) {
            running.filterKeys { !it.endsWith(":action") && !it.endsWith(":customize") }.values.toList()
        }
        active.forEach { it.destroyForcibly() }
    }

    private fun runRecorded(report: Report, stage: String, boot: String, stdout: (String) -> Unit, stderr: (String) -> Unit,
                            expectedTree: RootModuleTree.Snapshot? = null): Int {
        // T3/T5: `stage.<stage>.detail` must be a single line that explains a failure.
        // The last non-blank stderr line is the script's own reason (for a metamodule
        // that is typically the overlayfs refusal), so it is what gets recorded.
        var detail: String? = null
        val captured: (String) -> Unit = { line -> if (line.isNotBlank()) detail = line; stderr(line) }
        var launched = false
        val output: (String) -> Unit = { line ->
            if (stage in bootScripts && line == "MAPLESU_SCRIPT_STARTED") launched = true else stdout(line)
        }
        return try {
            synchronized(lock) { recordStage(report, stage, boot, "running") }
            val code = execute(report, tree(report), stage, entryFor(report.type, report.id)?.archive, output, captured, expectedTree)
            synchronized(lock) {
                recordStage(report, stage, boot,
                    if (code == 0) { if (launched) "started" else "ok" }
                    else if (code == 137) "interrupted" else "failed",
                    if (code == 0 && launched) null else code,
                    if (code == 0 && launched) "Background launch acknowledged; script exit is not awaited." else detail)
            }
            code
        } catch (error: Exception) {
            stderr(error.message ?: "Module script failed.")
            val interrupted = error is InterruptedException || Thread.currentThread().isInterrupted
            if (interrupted) Thread.currentThread().interrupt()
            val code = if (interrupted) 137 else 1
            synchronized(lock) {
                recordStage(report, stage, boot, if (interrupted) "interrupted" else "failed", code,
                    detail ?: error.message)
            }
            code
        } finally {
            synchronized(lock) { scheduled.remove(taskKey(report, stage)) }
        }
    }

    private fun recordStage(report: Report, stage: String, boot: String, status: String,
                            code: Int? = null, detail: String? = null) {
        updateStage(report, stage, boot, status, code, detail)
    }

    // Content-addressed helper name. The previous "write once, replace when the
    // bytes differ" cache was observed serving a stale script on device (a
    // 4300-byte runner.sh while the APK asset was 6436 bytes), which silently
    // runs old code after a fix. Naming the file by its own digest makes that
    // state unrepresentable: a stale file can never be selected.
    private fun helperFile(helperName: String): File {
        val helperBytes = context.assets.open("maplesu/modules/$helperName").use { it.readBytes() }
        val helperDigest = MessageDigest.getInstance("SHA-256").digest(helperBytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }.take(16)
        val helper = File(runtimeRoot, "${helperName.removeSuffix(".sh")}-$helperDigest.sh")
        if (!helper.isFile || helper.length() != helperBytes.size.toLong()) {
            val temporary = File.createTempFile(".$helperName-", ".tmp", runtimeRoot)
            try {
                temporary.writeBytes(helperBytes)
                Files.move(temporary.toPath(), helper.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
            } catch (error: Exception) {
                // A helper that cannot be written must fail loudly: silently running a
                // different (older) script is worse than refusing the task.
                error("MODULE_HELPER_UNWRITABLE: ${helper.name}: ${error.message}")
            } finally { temporary.delete() }
        }
        // Best effort: drop older copies of the same helper so they cannot be reused
        // by an older build of this code.
        runCatching {
            val prefix = "${helperName.removeSuffix(".sh")}-"
            runtimeRoot.listFiles()
                ?.filter { it.name.startsWith(prefix) && it.name.endsWith(".sh") && it.name != helper.name }
                ?.forEach { it.delete() }
        }
        return helper
    }

    private fun scriptDeadline(stage: String): String =
        if (stage in setOf("post-fs-data", "post-mount")) {
            (earlyScriptDeadlineMillis.takeIf { it > 0L } ?: (SystemClock.elapsedRealtime() + 35_000L)).toString()
        } else "0"

    private fun execute(report: Report, directory: File, stage: String, archive: File?,
                        stdout: (String) -> Unit, stderr: (String) -> Unit,
                        expectedTree: RootModuleTree.Snapshot? = null): Int {
        // Install/uninstall wrappers also handle modules without their own script.
        val scriptStage = if (stage == MOUNT_STAGE) "metamount" else stage
        val script = File(directory, "$scriptStage.sh")
        // prepareInstall verified module.prop through Root; the app UID cannot stat modules_update.
        require(stage == "customize" || if (stage == "uninstall") regular(File(directory, "module.prop")) else regular(script)) {
            "Module script is missing or is not a regular file."
        }
        val argv = synchronized(lock) {
            if (stage != "customize") {
                val current = requireNotNull(entryFor(report.type, report.id))
                check(identityKey(report) !in installing) { "Module installation is running." }
                check(current.report.sha256 == report.sha256) { "The module revision changed." }
                check(sameTree(report, expectedTree)) { "MODULE_TREE_OWNER_CHANGED" }
                check(if (stage == "uninstall") canRemove(report, state(report)) else canRun(report)) {
                    "MODULE_TREE_OWNER_CHANGED"
                }
                check(if (stage == "uninstall") state(report).remove else snapshot(report).enabled && !state(report).remove) {
                    "The module task was cancelled by a state change."
                }
            }
            // Three distinct execution contracts, matching the pinned KSU userspace:
            //
            //  * legacy `install.sh` - sourced by the bridge, because install.sh
            //    defines the print_modname/on_install/set_permissions callbacks that
            //    installer.sh:396-410 then invokes.
            //  * `customize.sh` - sourced by runner.sh, because it must see the
            //    installer helpers in its own shell (installer.sh:430).
            //  * lifecycle stages - exec'd, because KSU runs them as
            //    `busybox sh <abs path>` (module.rs:341-344) so the script owns its
            //    `$0`, `set -e`, traps and exit status. Sourcing those is what made
            //    `MODDIR=${0%/*}` resolve to the runner directory instead.
            val helperName = when (stage) {
                "customize" -> "runner.sh"
                else -> "run.sh"
            }
            val helper = helperFile(helperName)
            when (stage) {
                // runner.sh selects the modern/legacy installer and metamodule hook.
                "customize" -> listOf(ModuleToolDeployment.busyboxPath(), "sh", helper.absolutePath,
                    directory.absolutePath, script.name, requireNotNull(archive).absolutePath,
                    helperFile("legacy-install.sh").absolutePath)
                else -> listOf("/system/bin/sh", helper.absolutePath,
                    directory.absolutePath, scriptStage, scriptDeadline(stage))
            }
        }
        // Root IPC and a synchronous script may take the whole operation.
        // Never hold the module lock across either; toggles and list reads use it too.
        // start() can throw after the remote task accepted argv. The existing
        // management router selects its transport; an uncertain reply is not replayed.
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        val task = RootManagement.startModule(context, argv)
        if (Thread.currentThread().isInterrupted) {
            runCatching { task.destroyForcibly() }
            runCatching { task.outputStream.close() }
            runCatching { task.inputStream.close() }
            runCatching { task.errorStream.close() }
            Thread.currentThread().interrupt()
            throw InterruptedException()
        }
        val cancelStarted = synchronized(lock) {
            running[taskKey(report, stage)] = task
            if (stage != "customize") {
                val current = entryFor(report.type, report.id)
                current == null || current.report.sha256 != report.sha256 ||
                    !sameTree(report, expectedTree) ||
                    (stage != "uninstall" && (!snapshot(report).enabled || state(report).remove))
            } else false
        }
        if (cancelStarted) task.destroyForcibly()
        return await(task, taskKey(report, stage), stdout, stderr)
    }

    /** KSU Forever for foreground scripts; run.sh owns the boot stages' wait policy. */
    private fun await(task: Process, key: String, stdout: (String) -> Unit,
                      stderr: (String) -> Unit): Int {
        val out = pumps.submit { task.inputStream.bufferedReader().useLines { lines -> lines.forEach(stdout) } }
        val err = pumps.submit { task.errorStream.bufferedReader().useLines { lines -> lines.forEach(stderr) } }
        try {
            // No interactive input is required; an accidental read must not stall boot.
            task.outputStream.close()
            task.waitFor()
            out.get(5, TimeUnit.SECONDS)
            err.get(5, TimeUnit.SECONDS)
            return task.exitValue()
        } finally {
            task.destroyForcibly()
            synchronized(lock) { running.remove(key) }
            runCatching { task.inputStream.close() }
            runCatching { task.errorStream.close() }
            out.cancel(true)
            err.cancel(true)
        }
    }

    /**
     * T2: runs the executable entries of the public `/data/adb/<stage>.d`
     * directory, mirroring KSU `exec_common_scripts`
     * (`.research/KernelSU/userspace/ksud/src/module.rs:385-405`):
     *
     * - only regular, non-symlink files are considered;
     * - non-executable entries are skipped, not failed;
     * - entries run in filename order so a boot is deterministic;
     * - one failure never stops the following entries.
     *
     * The directory is listed through the Root shell because the Manager UID cannot
     * stat anything under `/data/adb` (0700 root).
     */
    private fun runStageDirectory(stage: String, boot: String, stdout: (String) -> Unit, stderr: (String) -> Unit) {
        val results = mutableListOf<Pair<String, Int>>()
        val started = mutableSetOf<String>()
        // KSU's public directory first; retain hooks published in MapleSU's namespace.
        val directories = listOf("/data/adb/$stage.d", "${ModuleToolDeployment.WORKING_DIR}/$stage.d")
        directories.forEach { dir ->
            listStageEntries(stage, dir, stdout, stderr).forEach { name ->
                val code = runStageEntry(stage, "$dir/$name", { line ->
                    if (line == "MAPLESU_SCRIPT_STARTED") started += "$dir/$name" else stdout(line)
                }, stderr)
                results += "$dir/$name" to code
                stdout("MAPLESU_STAGE_ENTRY stage=$stage name=$name code=$code")
            }
        }
        writeCommonReceipt(stage, boot, results, started)
    }

    /** Returns the executable entry names in [dir], sorted, as the Root shell sees them. */
    private fun listStageEntries(stage: String, dir: String, stdout: (String) -> Unit,
                                 stderr: (String) -> Unit): List<String> {
        val script = """
            set -u
            export LC_ALL=C
            dir=${quote(dir)}
            if [ ! -d "${'$'}dir" ]; then /system/bin/printf 'MAPLESU_STAGE_DIR_ABSENT:%s\n' ${quote(stage)}; exit 0; fi
            for f in "${'$'}dir"/*; do
                [ -f "${'$'}f" ] || continue
                [ -L "${'$'}f" ] && continue
                name=${'$'}{f##*/}
                if [ -x "${'$'}f" ]; then /system/bin/printf 'E:%s\n' "${'$'}name"
                else /system/bin/printf 'X:%s\n' "${'$'}name"; fi
            done
            exit 0
        """.trimIndent()
        val result = runCatching {
            trustedShell().use { shell ->
                shell.newJob().add(script).to(ArrayList<String>(), ArrayList<String>()).exec()
            }
        }.getOrElse {
            throw IllegalStateException("The public $stage.d directory could not be listed", it)
        }
        check(result.isSuccess) { "MODULE_STAGE_LIST_FAILED:$stage:${result.code}" }
        result.out.filter { it.startsWith("X:") }.forEach {
            stdout("MAPLESU_STAGE_SKIP stage=$stage name=${it.removePrefix("X:")} not-executable")
        }
        return result.out.filter { it.startsWith("E:") }.map { it.removePrefix("E:") }.sorted()
    }

    /** Public entries use the same BusyBox, cwd, environment and wait policy as modules. */
    private fun runStageEntry(stage: String, path: String, stdout: (String) -> Unit,
                              stderr: (String) -> Unit): Int {
        val key = "common:$stage:$path"
        val script = File(path)
        val argv = listOf("/system/bin/sh", helperFile("run.sh").absolutePath,
            script.parentFile!!.absolutePath, stage, scriptDeadline(stage), script.name)
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        val task = RootManagement.startModule(context, argv)
        if (Thread.currentThread().isInterrupted) {
            runCatching { task.destroyForcibly() }
            runCatching { task.outputStream.close() }
            runCatching { task.inputStream.close() }
            runCatching { task.errorStream.close() }
            Thread.currentThread().interrupt()
            throw InterruptedException()
        }
        synchronized(lock) { running[key] = task }
        return await(task, key, stdout, stderr)
    }

    /**
     * T2 receipt for the public directories. They belong to no module, so this is a
     * separate `_common.properties` file with the same `stage.<stage>.*` keys as a
     * module receipt (task book §0.2).
     */
    private fun writeCommonReceipt(stage: String, boot: String, results: List<Pair<String, Int>>,
                                   started: Set<String> = emptySet()) {
        val file = File(receipts, COMMON_RECEIPT)
        val values = if (file.isFile && file.length() in 1..16_384) {
            runCatching { Properties().apply { file.inputStream().use { load(it) } } }
                .getOrDefault(Properties())
        } else Properties()
        values.setProperty("stage.$stage.bootId", boot)
        values.remove("stage.$stage.code")
        values.remove("stage.$stage.failure")
        val failures = results.filter { it.second != 0 &&
            (stage != POLICY_STAGE || it.second != LiteModuleRuntimeState.POLICY_DISABLED_EXIT) }
        values.setProperty("stage.$stage.status",
            if (failures.isNotEmpty()) "failed" else if (started.isNotEmpty()) "started"
            else if (stage == POLICY_STAGE && results.isNotEmpty() &&
                results.all { it.second == LiteModuleRuntimeState.POLICY_DISABLED_EXIT }) "skipped" else "ok")
        values.setProperty("stage.$stage.detail",
            results.joinToString(";") { (name, code) -> if (name in started && code == 0) "$name:started" else "$name:$code" }.replace('\n', ' '))
        failures.firstOrNull()?.let { (name, code) ->
            values.setProperty("stage.$stage.code", code.toString())
            values.setProperty("stage.$stage.failure", name)
        }
        val temporary = File.createTempFile(".common-", ".properties", receipts)
        try {
            temporary.outputStream().use { output -> values.store(output, null); output.fd.sync() }
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
    }

    /**
     * T1: parses a module `system.prop` the way the pinned KSU userspace does
     * (`load_system_prop_file` → `resetprop -n -f`, `.research/KernelSU/userspace/
     * ksud/src/resetprop.rs:26-88`): one `key=value` per line, blank lines and `#`
     * comments skipped, the first `=` splits key and value, both sides trimmed.
     *
     * A line without `=` is returned with an empty key. The applier turns that into
     * `bad-key`; the alternative (guessing a property name from arbitrary text) would
     * write a property the module never asked for.
     */
    private fun parsePropFile(file: File): List<Pair<String, String>> {
        val entries = mutableListOf<Pair<String, String>>()
        val lines = rootFile(file)?.let { rootTrees.read(file.parentFile!!.name, file.name).lineSequence() }
            ?: file.readLines().asSequence()
        lines.forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val separator = line.indexOf('=')
            if (separator < 0) entries += "" to line
            else entries += line.substring(0, separator).trim() to line.substring(separator + 1).trim()
        }
        return entries
    }

    /**
     * T1: applies this module's `system.prop` through MapleSU's published resetprop,
     * one `resetprop -n <key> <value>` per entry so every property gets its own exit
     * status. `-n` (skip property_service) is mandatory: an arbitrary module property
     * name has no `property_contexts` entry, so the service path fails with `-1`
     * (verified by `ResetpropContractAndroidTest`).
     *
     * Writes `props.applied` / `props.failed` to the receipt (task book §0.2) and
     * returns both lists.
     */
    fun applySystemProperties(report: Report, stdout: (String) -> Unit,
                              stderr: (String) -> Unit): Pair<List<String>, List<String>> {
        backgroundThread()
        val file = File(tree(report), "system.prop")
        if (!regular(file)) return emptyList<String>() to emptyList()
        val applied = mutableListOf<String>()
        val failed = mutableListOf<String>()
        val entries = parsePropFile(file)
        val tool = ModuleToolDeployment.toolPath("resetprop")
        if (entries.isNotEmpty()) {
            trustedShell().use { shell ->
                for ((key, value) in entries) {
                    if (!PROP_KEY.matches(key)) {
                        failed += "${key.ifEmpty { "<malformed>" }}:bad-key"
                        stderr("resetprop: rejected a malformed system.prop line in ${report.id}")
                        continue
                    }
                    val result = shell.newJob()
                        .add("${quote(tool)} -n ${quote(key)} ${quote(value)}")
                        .to(ArrayList<String>(), ArrayList<String>()).exec()
                    if (result.isSuccess) {
                        applied += "$key=$value"
                    } else {
                        val reason = (result.err + result.out).firstOrNull { it.isNotBlank() }
                            ?.trim()?.replace('\n', ' ') ?: "exit${result.code}"
                        failed += "$key=$value:${reason.take(160)}"
                    }
                }
            }
        }
        synchronized(lock) {
            // Re-read under the lock: a concurrent stage update must not be overwritten.
            writeReceipt(report, state(report).copy(
                propsApplied = applied, propsFailed = failed, propsBootId = bootId()))
        }
        stdout("MAPLESU_PROPS module=${report.id} applied=${applied.size} failed=${failed.size}")
        if (failed.isNotEmpty()) {
            stderr("MAPLESU_PROPS_FAILED module=${report.id} " + failed.joinToString(";"))
        }
        return applied to failed
    }

    private fun sha256(bytes: ByteArray): String = digestBytes(bytes)

    private fun activeModules(): List<ScriptEntry> = activeModules(entries())

    private fun activeModules(current: List<ScriptEntry>): List<ScriptEntry> = current.filter {
        it.report.type == ModuleType.KERNEL_SU && canRun(it.report) && snapshot(it.report).enabled &&
            (receipt(it.report).isFile || foreignDisk(it.report)) && directory(tree(it.report)) && !state(it.report).remove
    }

    /** Metamodule scripts consume the same disable marker as KernelSU. */
    private fun setModuleMarker(report: Report, name: String, present: Boolean) {
        rootTrees.marker(report.id, name, present, snapshot(report).identity.takeIf { it.isNotEmpty() })
        rootSnapshot = rootSnapshot + rootTrees.snapshot(listOf(report.id))
    }

    /** No manager base rules: only enabled modules and the explicitly selected installer. */
    private fun applyModulePolicies(extra: File?, stdout: (String) -> Unit, stderr: (String) -> Unit): Map<String, Int> {
        val rules = synchronized(lock) { activeModules().map { File(tree(it.report), "sepolicy.rule") } }
            .plus(listOfNotNull(extra?.let { File(it, "sepolicy.rule") }))
            .filter(::regular).distinctBy { it.absolutePath }
        if (rules.isEmpty()) return emptyMap()
        return try {
            deployBundledTools()
            trustedShell().use { shell ->
                fun apply(files: List<File>): Int {
                    val arguments = files.joinToString(" ") { "--rules ${quote(it.absolutePath)}" }
                    repeat(3) { attempt ->
                        val result = shell.newJob().add("${quote(ModuleToolDeployment.policyPath())} module-apply --require-boot-policy $arguments")
                            .to(ArrayList<String>(), ArrayList<String>()).exec()
                        result.out.forEach(stdout)
                        result.err.forEach(stderr)
                        if (result.code != 75 || attempt == 2) return result.code
                        Thread.sleep(100L)
                    }
                    error("MODULE_POLICY_RETRY_EXHAUSTED")
                }
                val batch = apply(rules)
                val results = rules.associate { it.absolutePath to batch }.toMutableMap()
                if (batch != 0 && batch != LiteModuleRuntimeState.POLICY_DISABLED_EXIT && rules.size > 1) {
                    // One incompatible module must not discard another module's rules.
                    // Retry failures only while successful declarations can satisfy dependencies.
                    var pending = rules
                    do {
                        val failed = pending.filter { rule ->
                            val code = apply(listOf(rule))
                            results[rule.absolutePath] = code
                            code != 0
                        }
                        val progress = failed.size < pending.size
                        pending = failed
                    } while (pending.isNotEmpty() && progress)
                }
                results
            }
        } catch (error: Exception) {
            stderr("MAPLESU_MODULE_POLICY_FAILED: ${error.message}")
            rules.associate { it.absolutePath to 1 }
        }
    }

    private fun runModuleMounts(boot: String, stdout: (String) -> Unit, stderr: (String) -> Unit) {
        val active = synchronized(lock) { activeModules() }
        val delegated = active.any { Feature.METAMODULE in it.report.features &&
            regular(File(tree(it.report), "metamount.sh")) }
        val candidates = active.filter { state(it.report).installedBootId != boot && needsMount(tree(it.report)) }
        val targets = if (delegated) emptyList() else candidates
        val bytes = context.assets.open("maplesu/modules/mount.sh").use { it.readBytes() }
        val helper = File(runtimeRoot, "mount-${sha256(bytes).take(16)}.sh")
        if (!helper.isFile) {
            val temporary = File.createTempFile(".mount-", ".sh", runtimeRoot)
            try {
                temporary.writeBytes(bytes)
                Files.move(temporary.toPath(), helper.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
            } finally { temporary.delete() }
        }
        val command = listOf("/system/bin/sh", helper.absolutePath) +
            targets.flatMap { listOf(it.report.id, tree(it.report).absolutePath) }
        val result = trustedShell().use { shell ->
            shell.newJob().add(command.joinToString(" ", transform = ::quote))
                .to(ArrayList<String>(), ArrayList<String>()).exec()
        }
        result.out.forEach(stdout)
        result.err.forEach(stderr)
        synchronized(lock) {
            candidates.forEach { entry ->
                val prefix = "MAPLESU_MOUNT_RESULT:${entry.report.id}:"
                val code = result.out.lastOrNull { it.startsWith(prefix) }?.removePrefix(prefix)?.toIntOrNull()
                updateStage(entry.report, MOUNT_STAGE, boot,
                    if (delegated) "skipped" else if (code == 0) "ok" else "failed",
                    if (delegated) null else code ?: result.code.takeIf { it != 0 } ?: 1,
                    if (delegated) "metamodule" else null)
            }
        }
        writeCommonReceipt(MOUNT_STAGE, boot, listOf("batch" to result.code))
    }

    /**
     * Trusted Root shell for module work.
     *
     * Prefers the MapleSU runtime shell - the privileged transport MapleSU already
     * ships and the one the rest of the product uses - and only falls back to the
     * Binder execution owner. Module installation must not depend on the Binder
     * executor being published: the two transports fail independently, and observing
     * `SU_EXECUTOR_UNAVAILABLE` from a module install while a direct MapleSU shell
     * still works is exactly that coupling, not a module defect.
     */
    /**
     * 模块脚本的 root 通道：经 [ModuleRootShell] → [RootExecutionRouter]（Lite 下 Ultra
     * run_root_cmd 优先，LITE_KERNEL 兜底；NONE 时脚本返回非 0，安装 fail-closed）。
     * 不再依赖 libsu `su`（A2 形态无 su 时安装会失败）。
     */
    private fun trustedShell(): ModuleRootShell = ModuleRootShell(scriptRunner)

    private fun trustedShellOrNull(): ModuleRootShell? =
        runCatching { trustedShell() }.getOrNull()

    private fun deployBundledTools(): ModuleToolDeployment.Published =
        trustedShell().use { shell ->
            val result = shell.newJob().add(ModuleToolDeployment.script(context))
                .to(ArrayList<String>(), ArrayList<String>()).exec()
            check(result.isSuccess) {
                "Bundled module tools could not be published: " + result.err.joinToString("\n")
            }
            ModuleToolDeployment.parse(result.out)
        }

    private fun removeInstalled(report: Report) {
        check(entryFor(report.type, report.id)?.report?.sha256 == report.sha256)
        check(state(report).remove) { "The pending removal was cancelled." }
        check(canRemove(report, state(report))) { "MODULE_TREE_OWNER_CHANGED" }
        // T3: drop the standard path (and the metamodule pointer) first, and only while
        // they still point at this exact revision, so another manager's entry is never
        // deleted by our uninstall. Leaving either behind would name this module after
        // every other trace is gone.
        if (Feature.METAMODULE in report.features) {
            runCatching { Metamodule.unlink(context, tree(report).absolutePath) }
        }
        retireOrRemove(report)
        if (catalog.get(report.type, report.id) != null) catalog.uninstall(report.type, report.id)
        synchronized(lock) { diskEntries = diskEntries - report.id }
        // T3: the receipt stays as the durable record that the uninstall stage ran and
        // what it returned ("回执残留为最终状态"). Deleting it would erase the only
        // evidence; a reinstall of this revision overwrites it through the normal
        // install path.
        synchronized(lock) { scheduled.remove(taskKey(report, "uninstall")) }
    }

    private fun cancel(report: Report) {
        val active = synchronized(lock) { running.filterKeys { it.startsWith(moduleKey(report) + ":") }.values.toList() }
        active.forEach { it.destroyForcibly() }
    }

    private fun state(report: Report): LiteModuleRuntimeState {
        // A module present on disk under /data/adb/modules (installed by another manager or
        // before this backend ran) has no receipt yet. Treat that as a fresh state instead of
        // failing the whole action with "Invalid module runtime receipt".
        val saved = if (receipt(report).exists()) LiteModuleRuntimeState.read(receipt(report))
        else LiteModuleRuntimeState("00000000-0000-0000-0000-000000000000")
        val removed = snapshot(report).removed
        return saved.copy(remove = removed,
            removeRequestedBootId = if (removed) saved.removeRequestedBootId ?: saved.installedBootId else null)
    }

    private fun bootId() = LiteModuleBootEpoch.read(context)

    /** T4: every receipt write goes through this one function. */
    private fun writeReceipt(report: Report, state: LiteModuleRuntimeState) {
        state.save(receipt(report))
    }

    private fun updateStage(report: Report, stage: String, boot: String, status: String,
                            code: Int? = null, detail: String? = null) {
        writeReceipt(report, state(report).withStage(stage, boot, status, code, detail))
    }
    private fun resolve(id: String): ScriptEntry {
        val type = if (id.startsWith("maplesu:")) ModuleType.MAPLESU else ModuleType.KERNEL_SU
        val original = if (type == ModuleType.MAPLESU) id.removePrefix("maplesu:") else id
        return requireNotNull(entryFor(type, original)) { "The module is not installed." }
    }
    private fun entries(): List<ScriptEntry> = entries(readCatalog())

    private fun readCatalog(): List<ModuleCatalog.Entry> {
        val started = SystemClock.elapsedRealtime()
        var count = -1
        try {
            return runCatching { catalog.list(ModuleType.KERNEL_SU) }
                .onSuccess { catalogReadError = null; count = it.size }
                .onFailure {
                    catalogReadError = it.message ?: it.javaClass.simpleName
                    android.util.Log.w("MapleSUModules", "Module archive inventory failed; reading Root trees", it)
                }.getOrDefault(emptyList())
        } finally {
            android.util.Log.i("MapleSUModules", "MAPLESU_MODULE_LATENCY phase=catalog count=$count elapsed=${SystemClock.elapsedRealtime() - started}")
        }
    }

    private fun readRootSnapshot(ids: List<String>, discover: Boolean): Map<String, RootModuleTree.Snapshot> {
        val started = SystemClock.elapsedRealtime()
        var count = -1
        try {
            return rootTrees.snapshot(ids, discover = discover).also { count = it.size }
        } finally {
            android.util.Log.i("MapleSUModules", "MAPLESU_MODULE_LATENCY phase=root-snapshot count=$count elapsed=${SystemClock.elapsedRealtime() - started}")
        }
    }

    private fun entries(stored: List<ModuleCatalog.Entry>): List<ScriptEntry> = stored.map {
        ScriptEntry(it.report, it.archive, it.enabled, catalogToken = it.revisionToken)
    }.filter { it.report.type != ModuleType.KERNEL_SU || it.report.id !in diskEntries } + diskEntries.values

    private fun entryFor(type: ModuleType, id: String): ScriptEntry? =
        if (type == ModuleType.KERNEL_SU && id in diskEntries) diskEntries[id]
        else runCatching { catalog.get(type, id) }
            .onFailure { android.util.Log.w("MapleSUModules", "Module archive lookup failed: $id", it) }
            .getOrNull()?.let { ScriptEntry(it.report, it.archive, it.enabled, catalogToken = it.revisionToken) }

    // Share a just-completed inventory between boot dispatch and immediate page reads.
    // Mutations still take a fresh snapshot under the transaction lock.
    private fun invalidateInventory() {
        inventoryReadAt = Long.MIN_VALUE
        inventoryActivation = null
        com.maplesu.core.data.repository.ModuleRepositoryImpl.invalidateModuleList()
    }

    private fun refreshTreesForRead(): List<ModuleCatalog.Entry> = refreshTreesForRead(System.nanoTime())

    private fun refreshTreesForRead(requestedAt: Long): List<ModuleCatalog.Entry> {
        return synchronized(installLock) {
            val age = System.nanoTime() - inventoryReadAt
            // Boot migration may outlast the TTL after publishing a complete inventory.
            // A page already waiting for that transaction must not immediately repeat it.
            if (inventoryReadAt != Long.MIN_VALUE && inventoryActivation === ModeRuntime.activation &&
                (inventoryReadAt - requestedAt >= 0 || age in 0 until 1_000_000_000L)) readCatalog()
            else refreshTrees()
        }
    }

    // Recovery and snapshot publication share the existing install transaction lock.
    // Root I/O never owns the short-lived scheduling/cancellation monitor.
    private fun refreshTrees(): List<ModuleCatalog.Entry> = refreshTrees(null)

    private fun refreshTrees(targetId: String?): List<ModuleCatalog.Entry> = synchronized(installLock) {
        if (migrationCleanupRunning) return@synchronized emptyList()
        inventoryReadAt = Long.MIN_VALUE
        inventoryActivation = null
        val activation = ModeRuntime.activation
        val verifiedCatalog = if (targetId == null) readCatalog() else runCatching {
            listOfNotNull(catalog.get(ModuleType.KERNEL_SU, targetId))
        }.onFailure {
            android.util.Log.w("MapleSUModules", "Module archive lookup failed: $targetId", it)
        }.getOrDefault(emptyList())
        val stored = verifiedCatalog.filter { it.report.type == ModuleType.KERNEL_SU }
        val ids = targetId?.let(::listOf) ?: stored.map { it.report.id }
        var observed = readRootSnapshot(ids, discover = targetId == null)
        val known = stored.associateBy { it.report.id }
        val interrupted = synchronized(lock) {
            observed.filter { (id, value) ->
                value.pendingTransaction && "${ModuleType.KERNEL_SU}:$id" !in installing
            }.keys
        }
        interrupted.forEach { id -> rootTrees.recover(id, known[id]?.report?.let(::moduleKey).orEmpty(), known[id]?.revisionToken) }
        if (interrupted.isNotEmpty()) observed = readRootSnapshot(ids, discover = targetId == null)
        synchronized(lock) {
        if (migrationCleanupRunning) return@synchronized
        rootSnapshot = if (targetId == null) observed else rootSnapshot + observed
        val refreshed = observed.mapNotNull { (id, value) ->
            if (!value.directory || value.pendingTransaction || value.legacyTarget.isNotEmpty() || value.metadata == null ||
                (value.revision.isNotEmpty() && !value.revision.matches(Regex("[0-9a-f]{64}")))) return@mapNotNull null
            val entry = known[id]
            val pendingReport = value.pending?.takeIf {
                it.directory && it.metadata != null && it.revision.matches(Regex("[0-9a-f]{64}"))
            }?.let { pending ->
                if (entry != null && pending.revision == moduleKey(entry.report)) entry.report
                else runCatching { ModuleArchiveInspector.installedKsu(id, requireNotNull(pending.metadata),
                    pending.identity, pending.files, pending.directories, pending.zygisk) }.getOrNull()
            }
            if (entry != null && value.revision == moduleKey(entry.report)) {
                return@mapNotNull id to ScriptEntry(entry.report, entry.archive, value.enabled,
                    catalogToken = entry.revisionToken, pendingReport = pendingReport)
            }
            val report = runCatching {
                ModuleArchiveInspector.installedKsu(id, value.metadata, value.identity, value.files, value.directories, value.zygisk)
            }.onFailure { android.util.Log.w("MapleSUModules", "Invalid installed module metadata: $id", it) }
                .getOrNull() ?: return@mapNotNull null
            // A disk-only inventory never supplies an archive or replays installation.
            if (value.revision.isNotEmpty() && !receipt(report).isFile) runCatching {
                LiteModuleRuntimeState("00000000-0000-0000-0000-000000000000").save(receipt(report))
            }.onFailure { android.util.Log.w("MapleSUModules", "Root module discovered; receipt unavailable: $id", it) }
            id to ScriptEntry(report, null, value.enabled, disk = true, pendingReport = pendingReport)
        }.toMap()
        diskEntries = if (targetId == null) refreshed else (diskEntries - targetId) + refreshed
        inventoryReadAt = if (targetId == null && ModeRuntime.activation === activation) System.nanoTime()
            else Long.MIN_VALUE
        inventoryActivation = activation.takeIf { inventoryReadAt != Long.MIN_VALUE }
        }
        verifiedCatalog
    }

    private fun snapshot(report: Report) = rootSnapshot[report.id] ?: RootModuleTree.Snapshot()
    private fun sameTree(report: Report, expected: RootModuleTree.Snapshot?) = expected == null || snapshot(report).let {
        it.revision == expected.revision && it.identity == expected.identity && it.legacyTarget == expected.legacyTarget
    }
    private fun owns(report: Report) = snapshot(report).revision == moduleKey(report) ||
        snapshot(report).legacyTarget in legacyPaths(report) ||
        (snapshot(report).revision.matches(Regex("[0-9a-f]{64}")) &&
            diskEntries[report.id]?.report?.sha256 == report.sha256)
    /** Marker edits are explicit user actions, not permission to replay retained scripts. */
    private fun canManage(entry: ScriptEntry): Boolean = owns(entry.report) || (entry.disk && foreignDisk(entry.report))
    /**
     * A tree this backend did not install.
     *
     * A module that already lives under /data/adb/modules - installed by KernelSU, by another
     * manager, or before this backend ever ran - carries no MapleSU owner revision and no legacy
     * target. Its tree identity (device:inode) is the only durable proof that the tree is still
     * the one the user picked, and both discovery and targeted reads capture it with stat. A missing
     * identity remains an unconfirmed tree.
     */
    private fun foreignDisk(report: Report): Boolean {
        val snap = snapshot(report)
        return diskEntries[report.id]?.disk == true && snap.revision.isEmpty() && snap.legacyTarget.isEmpty() &&
            snap.directory && snap.identity.matches(Regex("[0-9]+:[0-9]+"))
    }
    /** Running a module stage (including `action.sh`) is allowed for foreign disk trees too. */
    private fun canRun(report: Report): Boolean = owns(report) || foreignDisk(report)
    private fun canRemove(report: Report, state: LiteModuleRuntimeState): Boolean = owns(report) ||
        (state.remove && state.removalIdentity != null && state.removalIdentity == snapshot(report).identity &&
            snapshot(report).revision.isEmpty() && snapshot(report).legacyTarget.isEmpty())
    private fun hasZygisk(report: Report) = snapshot(report).zygisk || hasZygiskPayload(report, emptyList())
    private fun tree(report: Report) = File(rootTrees.path(report.id))
    private fun legacyPaths(report: Report) = listOf(File(earlyRevisions, moduleKey(report)).absolutePath,
        File(revisions, moduleKey(report)).absolutePath)

    private fun rootFile(file: File): Pair<RootModuleTree.Snapshot, String>? {
        val pendingPrefix = ModuleTree.ROOT + "_update/"
        val pending = file.absolutePath.startsWith(pendingPrefix)
        val prefix = if (pending) pendingPrefix else ModuleTree.ROOT + "/"
        if (!file.absolutePath.startsWith(prefix)) return null
        val relative = file.absolutePath.removePrefix(prefix)
        val id = relative.substringBefore('/')
        val state = if (pending) rootSnapshot[id]?.pending else rootSnapshot[id]
        return (state ?: RootModuleTree.Snapshot()) to relative.substringAfter('/', "")
    }
    private fun regular(file: File): Boolean = rootFile(file)?.let { (state, path) -> path in state.files }
        ?: Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)
    private fun directory(file: File): Boolean = rootFile(file)?.let { (state, path) ->
        if (path.isEmpty()) state.directory else path in state.directories
    } ?: file.isDirectory
    private fun needsMount(directory: File): Boolean = rootFile(directory)?.first?.let { state ->
        "skip_mount" !in state.files && state.directories.any { it in listOf("system", "vendor", "product", "system_ext", "odm", "oem") }
    } ?: (!File(directory, "skip_mount").exists() &&
        listOf("system", "vendor", "product", "system_ext", "odm", "oem").any { File(directory, it).isDirectory })

    private fun removeRevision(directory: File) =
        removeTree(directory, if (directory.parentFile == earlyRevisions) earlyRevisions else revisions)

    private fun retireOrRemove(report: Report) {
        rootTrees.remove(report.id, snapshot(report).revision, snapshot(report).metadata?.let { sha256(it.toByteArray()) },
            snapshot(report).identity.takeIf { it.isNotEmpty() })
        legacyPaths(report).forEach { path -> runCatching { removeRevision(File(path)) } }
    }

    private fun cleanupLegacy(report: Report) {
        val early = File(earlyRevisions, moduleKey(report))
        if (hasZygiskPayload(report, listOf(early)) && early.isDirectory) {
            File(retiredZygisk, moduleKey(report)).writeText(bootId())
            runCatching { removeRevision(File(revisions, moduleKey(report))) }
        } else legacyPaths(report).forEach { path -> runCatching { removeRevision(File(path)) } }
        receipt(report).delete()
    }

    private fun collectRetiredZygisk(boot: String, stored: List<ModuleCatalog.Entry>) {
        val active = stored.map { moduleKey(it.report) }.toSet()
        retiredZygisk.listFiles()?.forEach { marker ->
            if (!marker.name.matches(Regex("[0-9a-f]{64}")) || !marker.isFile) return@forEach
            if (marker.name in active) { marker.delete(); return@forEach }
            if (marker.readText() == boot) return@forEach
            runCatching {
                removeTree(File(earlyRevisions, marker.name), earlyRevisions)
                marker.delete()
            }
        }
    }

    /** Upgrade the old private-tree publication without rerunning customize.sh. */
    private fun migrateEarlyRevision(report: Report) {
        val key = moduleKey(report)
        if (diskEntries[report.id]?.report?.sha256 == report.sha256) return
        if (snapshot(report).revision == key) return
        val entry = catalog.get(report.type, report.id)
        rootTrees.recover(report.id, key, entry?.revisionToken)
        rootSnapshot = rootSnapshot + rootTrees.snapshot(listOf(report.id))
        if (snapshot(report).revision == key) return
        val old = legacyPaths(report).map(::File).firstOrNull { it.isDirectory } ?: return
        rootTrees.publish(report.id, old.absolutePath, key, null, legacyPaths(report),
            entry?.enabled ?: true, catalogToken = entry?.revisionToken)
        rootTrees.commit(report.id)
        rootSnapshot = rootSnapshot + rootTrees.snapshot(listOf(report.id))
    }
    private fun receipt(report: Report) = File(receipts, "${moduleKey(report)}.properties")
    private fun identityKey(report: Report) = "${report.type}:${report.id}"
    private fun moduleKey(report: Report) = digest("${report.type}\n${report.id}\n${report.sha256}")
    private fun taskKey(report: Report, stage: String) = "${moduleKey(report)}:$stage"
    private fun externalId(report: Report) = if (report.type == ModuleType.MAPLESU) "maplesu:${report.id}" else report.id

    private fun removeTree(directory: File, parent: File) {
        require(directory.parentFile.canonicalFile == parent.canonicalFile)
        if (!Files.exists(directory.toPath(), LinkOption.NOFOLLOW_LINKS)) return
        // Scripts may create Root-owned private subdirectories; the Manager UID cannot walk them.
        trustedShell().use { shell ->
            val result = shell.newJob().add("rm -rf -- ${quote(directory.absolutePath)}").exec()
            check(result.isSuccess) { result.err.joinToString("\n").ifBlank { "The module tree could not be removed." } }
        }
    }

    companion object {
        @Volatile private var instance: LiteScriptModuleBackend? = null
        private val instanceLock = Any()

        fun get(context: Context): LiteScriptModuleBackend = instance ?: synchronized(instanceLock) {
            instance ?: LiteScriptModuleBackend(context.applicationContext).also { instance = it }
        }

        @Volatile private var migrationCleanupRunning = false

        /** Exclude installs and scheduled scripts without holding the inventory lock over root IPC. */
        internal fun <T> withMigrationCleanup(action: () -> T): T = synchronized(installLock) {
            synchronized(lock) {
                check(!migrationCleanupRunning && installing.isEmpty() && scheduled.isEmpty() && running.isEmpty()) {
                    "MODULE_OPERATION_BUSY"
                }
                migrationCleanupRunning = true
            }
            try { action() } finally { synchronized(lock) { migrationCleanupRunning = false } }
        }

        /** Fixed native host identity, matched against the SDK's current run state. */
        const val ZYGISK_HOST_ID = "c45e8a7390b24987a4b01d6e2c5f9143"

        /** T1: internal dispatch name for the `system.prop` step; never a stage entry. */
        const val PROPS_STAGE = "props"

        const val POLICY_STAGE = "policy"

        /** Module projection, followed by the optional metamodule hook. */
        const val MOUNT_STAGE = "mount"

        /**
         * T2: the lifecycle stages this backend runs, in KSU order. `post-fs-data`
         * runs at BOOT_COMPLETED time here (recorded, not hidden: see the module UI
         * note); `service` and `boot-completed` follow.
         */
        val bootScripts = listOf("post-fs-data", "post-mount", "service", "boot-completed")

        internal fun hasZygiskPayload(report: Report, directories: List<File>): Boolean =
            report.nativeAbis.keys.any { it.startsWith("zygisk/") && it.endsWith(".so") } ||
                directories.any { directory -> File(directory, "zygisk").listFiles()
                    ?.any { it.name.endsWith(".so") && regularPayload(it) } == true }

        internal fun extract(archive: File, destination: File): Map<String, String> {
            val scripts = mutableMapOf<String, String>()
            ZipFile.builder().setFile(archive).setUseUnicodeExtraFields(false).get().use { zip ->
                val entries = zip.entries.asSequence().map { it to ModuleArchiveInspector.checkedName(it) }.toList()
                val customize = entries.firstOrNull { it.second == "customize.sh" }?.first
                require(customize == null || customize.size <= 1024 * 1024) { "A module script exceeds the Ultra size limit." }
                val skipUnzip = entries.none { it.second == "install.sh" } && customize != null &&
                    zip.getInputStream(customize).bufferedReader().use { input ->
                        input.readText().split('\n').any { it == "SKIPUNZIP=1" }
                    }
                entries.forEach { (entry, name) ->
                    val target = File(destination, name).toPath().normalize()
                    require(target.startsWith(destination.toPath()) && target != destination.toPath())
                    require(name != ".maplesu" && !name.startsWith(".maplesu/"))
                    if (skipUnzip && name !in setOf("module.prop", "customize.sh")) return@forEach
                    if (name == "META-INF" || name.startsWith("META-INF/")) return@forEach
                    if (entry.isDirectory) Files.createDirectories(target)
                    else {
                        Files.createDirectories(target.parent)
                        zip.getInputStream(entry).use { input -> Files.newOutputStream(target).use { input.copyTo(it) } }
                        if (name.endsWith(".sh")) {
                            require(entry.size <= 1024 * 1024) { "A module script exceeds the Ultra size limit." }
                            scripts[name] = target.toFile().readText()
                        }
                    }
                }
            }
            return scripts
        }


        /** T2: the public-directory receipt belongs to no module, so it needs its own file. */
        private const val COMMON_RECEIPT = "_common.properties"

        /** T1: property names accepted by `resetprop`/property_service. */
        private val PROP_KEY = Regex("^[A-Za-z0-9_.-]+$")

        private val lock = Any()
        private val installLock = Any()
        private val installing = mutableSetOf<String>()
        private val scheduled = mutableSetOf<String>()
        private val running = mutableMapOf<String, Process>()
        private val pumps = Executors.newCachedThreadPool { task -> Thread(task, "maplesu-module-output").apply { isDaemon = true } }
        private fun backgroundThread() { check(Looper.myLooper() != Looper.getMainLooper()) { "Module execution requires a worker thread." } }
        private fun regularPayload(file: File) = Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)
        private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
        private fun digest(value: String) = digestBytes(value.toByteArray())
        private fun digestBytes(bytes: ByteArray) = buildString(64) {
            MessageDigest.getInstance("SHA-256").digest(bytes).forEach { value ->
                append(Character.forDigit((value.toInt() ushr 4) and 15, 16))
                append(Character.forDigit(value.toInt() and 15, 16))
            }
        }
    }
}
