package com.maplesu.core.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import com.maplesu.core.data.model.Module
import com.maplesu.core.data.model.ModuleUpdateInfo
import com.maplesu.core.ksuApp
import com.maplesu.core.ui.util.isNetworkAvailable
import com.maplesu.core.ui.util.listModules
import com.maplesu.core.ui.util.module.sanitizeVersionString
import com.maplesu.core.ui.util.toggleModule as toggleLiteModule
import com.maplesu.core.ui.util.undoUninstallModule as undoUninstallLiteModule
import com.maplesu.core.ui.util.uninstallModule as uninstallLiteModule
import dev.maplesu.manager.modules.JsonModuleListParser
import dev.maplesu.manager.modules.ModuleBackendPorts
import dev.maplesu.manager.modules.ModuleBackendRouter
import dev.maplesu.manager.modules.ModuleListParser
import dev.maplesu.manager.modules.ModuleOpsPort
import dev.maplesu.manager.modules.ModuleRouteResult
import dev.maplesu.manager.runtime.readResult
import dev.maplesu.manager.workmode.ModeRuntime
import dev.maplesu.manager.workmode.WorkMode
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/**
 * 模块仓库（W13）：按 `ModeRuntime.active` 显式分派，禁止默认 LITE（01 §1.2/§2.2、04 §6.1）。
 *
 * - `LITE` → [DefaultLiteModuleOpsPort]：委托现有 `listModules()/toggleModule()/uninstallModule()/
 *   undoUninstallModule()` 顶层实现（`LiteScriptModuleBackend` + `MapleSuModuleBackend` 合并逻辑），
 *   行为与改动前逐字节一致。
 * - `KSU` → KSU 端口（`KsuModuleOps` 的适配，由集成轮经 [ModuleBackendPorts.ksuFactory] 注入；
 *   modules 层不得 import `src/ksu`，见 `KsuSourceIsolationTest`）。端口未注入/不可用时返回
 *   [ModuleRouteResult.Unavailable] —— 这是**显式错误**，不是空成功。
 * - `null`（未判定）→ [ModuleRouteResult.Unresolved]：返回 `Result.failure`，
 *   绝不发起任何 root/ksud 调用，也绝不把列表当成空列表成功。
 *
 * `checkUpdate` 与模式无关（纯 HTTP + 行数据），保持原实现。
 */
class ModuleRepositoryImpl(
    private val mode: () -> WorkMode? = { ModeRuntime.active },
    private val litePort: ModuleOpsPort = DefaultLiteModuleOpsPort,
    private val ksuPortFactory: () -> ModuleOpsPort? = { ModuleBackendPorts.ksuFactory?.invoke() },
    /**
     * 列表 JSON → 模型。生产用 [JsonModuleListParser]（org.json）；JVM 单测注入 fake parser，
     * 因为 android.jar 的 org.json 在主机上是 stub（"Method length not mocked"）。
     */
    private val listParser: ModuleListParser = JsonModuleListParser,
) : ModuleRepository {

    private val router = ModuleBackendRouter(
        mode = mode,
        litePort = { litePort },
        ksuPort = ksuPortFactory,
    )

    companion object {
        private const val TAG = "ModuleRepository"
        private val listReads = dev.maplesu.manager.runtime.BoundedPageRead<Result<List<Module>>>(15_000L)

        fun invalidateModuleList() = listReads.invalidate()
    }

    private class ListReadScope(
        private val mode: WorkMode?,
        private val activation: Any,
        private val ultra: List<Any?>,
        private val backend: List<Any?>,
    ) {
        override fun equals(other: Any?): Boolean = other is ListReadScope &&
            mode == other.mode && activation === other.activation && ultra == other.ultra && backend == other.backend

        override fun hashCode(): Int = 31 * (31 * (31 * (mode?.hashCode() ?: 0) +
            System.identityHashCode(activation)) + ultra.hashCode()) + backend.hashCode()
    }

    /** IO only: identity APIs can read prefs/proc, but never query the SDK. */
    private fun listReadScope(requestedMode: WorkMode?): ListReadScope = ListReadScope(
        requestedMode,
        ModeRuntime.activation,
        if (requestedMode == WorkMode.LITE && litePort === DefaultLiteModuleOpsPort) listOf(
            dev.maplesu.manager.runtime.ultra.UltraEnvironmentAutoSetup.currentBootId(),
            dev.maplesu.manager.runtime.ultra.UltraSdkCredentials.read(ksuApp),
            dev.maplesu.manager.runtime.ultra.UltraSdkCredentials.suspended(ksuApp),
            dev.maplesu.manager.runtime.ultra.UltraSdkCredentials.hookWithoutSdk(ksuApp),
            dev.maplesu.manager.runtime.ultra.UltraEnvironmentProbe.usesUltraBackend(ksuApp),
        ) else emptyList(),
        if (requestedMode == WorkMode.KSU) listOf(
            ksuPortFactory,
            ModuleBackendPorts.ksuFactory,
            dev.maplesu.manager.workmode.backend.KsuBackendRegistry.rootShellExecutor(),
        ) else listOf(litePort),
    )

    override suspend fun getModules(): Result<List<Module>> = getModules { }

    /** Observe the existing identity fence; null means the read confirmed its context expired. */
    internal suspend fun getModules(onReadScope: suspend (Any?) -> Unit): Result<List<Module>> {
        val requestedMode = mode()
        return withContext(Dispatchers.IO) {
            val requestedScope = try {
                listReadScope(requestedMode)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                return@withContext Result.failure(error)
            }
            onReadScope(requestedScope)
            val result = listReads.readResult(requestedScope) {
                check(listReadScope(mode()) == requestedScope) { "PAGE_QUERY_CONTEXT_CHANGED" }
                val result = readModules()
                check(listReadScope(mode()) == requestedScope) { "PAGE_QUERY_CONTEXT_CHANGED" }
                result
            }
            val error = result.exceptionOrNull()
            if (error is IllegalStateException && error.message == "PAGE_QUERY_CONTEXT_CHANGED") {
                onReadScope(null)
            }
            result
        }
    }

    private fun readModules(): Result<List<Module>> =
        when (val list = router.listJson()) {
            is ModuleRouteResult.Ok -> runCatching { listParser.parse(list.value) }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
            }
            ModuleRouteResult.Unresolved -> Result.failure(ModuleModeUnresolvedException())
            is ModuleRouteResult.Unavailable -> Result.failure(ModuleBackendUnavailableException(list.reason))
            is ModuleRouteResult.Failed -> {
                if (list.error is kotlinx.coroutines.CancellationException) throw list.error
                Result.failure(list.error)
            }
        }

    /** 模块启停：LITE 原实现；KSU → `ksud module enable|disable`；未判定 → 失败。 */
    suspend fun toggleModule(id: String, enable: Boolean): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try { runInterruptible { router.toggle(id, enable).toResult() } } finally { listReads.invalidate() }
        }

    /** 卸载（标记 remove）：LITE 原实现；KSU → `ksud module uninstall`；未判定 → 失败。 */
    suspend fun uninstallModule(id: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try { runInterruptible { router.uninstall(id).toResult() } } finally { listReads.invalidate() }
        }

    /** 撤销卸载：LITE 原实现；KSU → `ksud module undo-uninstall`；未判定 → 失败。 */
    suspend fun undoUninstallModule(id: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try { runInterruptible { router.undoUninstall(id).toResult() } } finally { listReads.invalidate() }
        }

    /** 安装已下载的模块 zip：KSU → `ksud module install <zip>`；LITE 走 flashModule 流水线（端口返回失败）。 */
    suspend fun installModule(zip: File): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try { runInterruptible { router.install(zip).toResult() } } finally { listReads.invalidate() }
        }

    override suspend fun checkUpdate(module: Module): Result<ModuleUpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            if (!isNetworkAvailable(ksuApp)) {
                return@runCatching ModuleUpdateInfo.Empty
            }
            if (module.updateJson.isEmpty() || module.remove || module.update || !module.enabled) {
                return@runCatching ModuleUpdateInfo.Empty
            }

            val url = module.updateJson
            val result = ksuApp.okhttpClient.newCall(
                Request.Builder().url(url).build()
            ).execute().use { response ->
                if (response.isSuccessful) response.body.string() else ""
            }

            if (result.isEmpty()) {
                return@runCatching ModuleUpdateInfo.Empty
            }

            val updateJson = JSONObject(result)
            var version = updateJson.optString("version", "")
            version = sanitizeVersionString(version)
            val versionCode = updateJson.optInt("versionCode", 0)
            val zipUrl = updateJson.optString("zipUrl", "")
            val changelog = updateJson.optString("changelog", "")

            if (versionCode <= module.versionCode || zipUrl.isEmpty()) {
                ModuleUpdateInfo.Empty
            } else {
                ModuleUpdateInfo(zipUrl, version, changelog, versionCode)
            }
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
    }

}

/** 未判定态：任何模块后端都不得启动（04 §1.7）。 */
class ModuleModeUnresolvedException :
    IllegalStateException("MODE_UNRESOLVED: work mode is not determined yet; module backend is blocked")

/** 当前模式的后端未装配/不可用（KSU 端口未注入、ksud 缺失等）。 */
class ModuleBackendUnavailableException(reason: String) :
    IllegalStateException("BACKEND_UNAVAILABLE: $reason")

private fun <T> ModuleRouteResult<T>.toResult(): Result<T> = when (this) {
    is ModuleRouteResult.Ok -> Result.success(value)
    ModuleRouteResult.Unresolved -> Result.failure(ModuleModeUnresolvedException())
    is ModuleRouteResult.Unavailable -> Result.failure(ModuleBackendUnavailableException(reason))
    is ModuleRouteResult.Failed -> {
        if (error is kotlinx.coroutines.CancellationException) throw error
        Result.failure(error)
    }
}

/**
 * LITE 端口：**逐字节语义等价**于现有顶层实现（同一批函数，不复制逻辑）。
 * `install` 不在本端口内：LITE 的安装走 `ui/util/KsuCli.kt#flashModule`（解压/预检/脚本流水线），
 * 不能在仓库层用一条命令替代，故返回 false（不会伪造成功）。
 */
internal object DefaultLiteModuleOpsPort : ModuleOpsPort {
    override fun isAvailable(): Boolean = true

    override fun listJson(): String = listModules()

    override fun toggle(id: String, enable: Boolean): Boolean = toggleLiteModule(id, enable)

    override fun uninstall(id: String): Boolean = uninstallLiteModule(id)

    override fun undoUninstall(id: String): Boolean = undoUninstallLiteModule(id)

    override fun install(zip: File): Boolean = false
}
