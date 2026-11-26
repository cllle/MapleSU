package com.maplesu.core.ui.viewmodel

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.maplesu.core.Natives
import com.maplesu.core.R
import com.maplesu.core.data.model.Module
import com.maplesu.core.data.model.ModuleUpdateInfo
import me.weishu.kernelsu.data.registry.UpdateVerification
import com.maplesu.core.data.repository.ModuleRepoRepository
import com.maplesu.core.data.repository.ModuleRepoRepositoryImpl
import com.maplesu.core.data.repository.ModuleRepositoryImpl
import com.maplesu.core.data.repository.SettingsRepository
import com.maplesu.core.data.repository.SettingsRepositoryImpl
import com.maplesu.core.ksuApp
import com.maplesu.core.ui.component.SearchStatus
import com.maplesu.core.ui.screen.module.ModuleConfirmDialogState
import com.maplesu.core.ui.screen.module.ModuleConfirmRequest
import com.maplesu.core.ui.screen.module.ModuleEffect
import com.maplesu.core.ui.screen.module.ModuleRequestGeneration
import com.maplesu.core.ui.screen.module.ModuleUiState
import com.maplesu.core.ui.screen.module.PendingModuleOperations
import com.maplesu.core.ui.screen.module.moduleListInputs
import com.maplesu.core.ui.screen.module.withModuleLoadResult
import com.maplesu.core.ui.screen.module.withModuleReadContextReset
import com.maplesu.core.ui.screen.module.withUltraSafety
import com.maplesu.core.ui.screen.module.withModulePending
import com.maplesu.core.ui.screen.module.withModuleRemovalRequest
import com.maplesu.core.ui.screen.module.withModuleRemovalResult
import com.maplesu.core.ui.screen.module.finishModuleRemoval
import com.maplesu.core.ui.screen.module.withModuleToggleRequest
import com.maplesu.core.ui.screen.module.finishModuleToggle
import com.maplesu.core.ui.util.PinyinUtil
import com.maplesu.core.ui.util.hasMagisk
import dev.maplesu.manager.modules.ModuleEnvironmentWatch
import dev.maplesu.manager.modules.ModuleLoadDecision
import dev.maplesu.manager.modules.ModuleStartupGate
import dev.maplesu.manager.modules.ModuleWaitReason
import dev.maplesu.manager.runtime.ultra.UltraEnvironmentProbe
import dev.maplesu.manager.workmode.ModeActivation
import dev.maplesu.manager.workmode.ModeRuntime
import dev.maplesu.manager.workmode.WorkMode
import okhttp3.Request
import java.text.Collator
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

class ModuleViewModel(
    // W13: 模块操作按模式路由（LITE 原实现 / KSU / 未判定阻断，见 ModuleRepositoryImpl）。
    // 保留具体类型是为了让新操作方法可用；`ModuleRepository` 接口契约（getModules/checkUpdate）不变。
    private val repo: ModuleRepositoryImpl = ModuleRepositoryImpl(),
    private val settingsRepo: SettingsRepository = SettingsRepositoryImpl(),
    private val registryRepo: ModuleRepoRepository = ModuleRepoRepositoryImpl(),
) : ViewModel() {

    companion object {
        private const val TAG = "ModuleViewModel"

        /**
         * T10：等运行环境就绪的最长时限（激活 + KSU 后端接线）。
         *
         * 阶段二的保底延迟是 2.5 s，加上判定/取证余量；超时后允许**一次**如实失败的取列表，
         * 而不是无限"启动中"（失败必须可见）。
         */
        internal const val ENVIRONMENT_WAIT_TIMEOUT_MS = 15_000L
    }

    private data class ModuleUpdateSignature(
        val updateJson: String,
        val versionCode: Int,
        val enabled: Boolean,
        val update: Boolean,
        val remove: Boolean
    )

    private data class ModuleUpdateCache(
        val signature: ModuleUpdateSignature,
        val info: ModuleUpdateInfo
    )

    private val _uiState = MutableStateFlow(ModuleUiState())
    val uiState: StateFlow<ModuleUiState> = _uiState.asStateFlow()

    // One-shot UI events (toast/snackbar): buffered Channel, never dropped/duplicated/overwritten
    private val _moduleEvent = Channel<ModuleEffect>(Channel.BUFFERED)
    val moduleEvent: Flow<ModuleEffect> = _moduleEvent.receiveAsFlow()

    private val updateInfoMutex = Mutex()
    private var updateInfoCache: MutableMap<String, ModuleUpdateCache> = mutableMapOf()
    private val updateInfoInFlight = mutableMapOf<String, Job>()
    private var searchJob: Job? = null

    private var fetchJob: Job? = null
    private var fetchOperationGeneration = 0L
    private var fetchActivation: ModeActivation? = null
    private var fetchCheckUpdate = false
    private var fetchResort = false
    private var fetchListPending = false
    private var moduleListActivation: ModeActivation? = null
    private var moduleListReadScope: Any? = null
    private val refreshRequests = ModuleRequestGeneration()
    private var listJob: Job? = null
    private val listRequests = ModuleRequestGeneration()
    private val environmentRequests = ModuleRequestGeneration()
    private var environmentRefreshJob: Job? = null
    private val moduleOperations = PendingModuleOperations()
    private var moduleOperationGeneration = 0L
    private var moduleOperationReadbackNeeded = false

    /**
     * T10：冷启动"启动中"门（D4）。运行环境未接线时**不取**列表，改为置 `pending`
     * 并订阅一次性就绪事件，就绪后自动重取**一次**（不轮询、不常驻线程）。
     */
    private val startupGate = ModuleStartupGate(ModuleEnvironmentWatch::facts)
    private var environmentWaitJob: Job? = null

    var isNeedRefresh = false
        private set

    private data class EnvironmentDisplayState(
        val activation: ModeActivation,
        val safeMode: Boolean = false,
        val webUiAvailable: Boolean = false,
    )

    init {
        viewModelScope.launch {
            var previous = UltraEnvironmentProbe.current()
            combine(UltraEnvironmentProbe.states, ModeRuntime.detectionRevision) { state, _ -> state }
                .collectLatest { state ->
                    val request = environmentRequests.next()
                    val activation = ModeRuntime.activation
                    val environment = withContext(Dispatchers.IO) { readEnvironmentDisplayState(activation) }
                    if (!environmentRequests.isCurrent(request) || activation !== ModeRuntime.activation) return@collectLatest
                    _uiState.update { it.withEnvironmentDisplayState(request, environment) }
                    if (state == UltraEnvironmentProbe.State.Running && previous != state &&
                        ModeRuntime.active == WorkMode.LITE &&
                        _uiState.value.isCurrentPage && _uiState.value.loadError != null && fetchJob?.isActive != true) {
                        fetchModuleList(checkUpdate = false)
                    }
                    previous = state
                }
        }
    }

    private fun readEnvironmentDisplayState(activation: ModeActivation): EnvironmentDisplayState {
        if ((activation as? ModeActivation.Active)?.mode != WorkMode.LITE) return EnvironmentDisplayState(activation)
        val snapshot = UltraEnvironmentProbe.currentSnapshot(ksuApp)
        val sdkBackend = UltraEnvironmentProbe.usesUltraBackend(ksuApp)
        return EnvironmentDisplayState(
            activation = activation,
            safeMode = sdkBackend && snapshot.state == UltraEnvironmentProbe.State.Fault,
            webUiAvailable = snapshot.state == UltraEnvironmentProbe.State.Running || sdkBackend,
        )
    }

    private fun ModuleUiState.withEnvironmentDisplayState(request: Long, environment: EnvironmentDisplayState): ModuleUiState =
        if (!environmentRequests.isCurrent(request) || environment.activation !== ModeRuntime.activation) this else
            withUltraSafety(environment.safeMode, (environment.activation as? ModeActivation.Active)?.mode)
                .copy(environmentWebUiAvailable = environment.webUiAvailable)

    fun setCurrentPage(isCurrentPage: Boolean) {
        _uiState.update { it.copy(isCurrentPage = isCurrentPage) }
    }

    fun markNeedRefresh() {
        isNeedRefresh = true
        moduleOperationGeneration++
        ModuleRepositoryImpl.invalidateModuleList()
    }

    fun refreshAfterReturn() {
        val state = _uiState.value
        fetchModuleList(
            checkUpdate = !state.hasLoaded || isNeedRefresh,
            resort = !state.hasLoaded,
            showRefreshing = !state.hasLoaded,
            coalesce = true,
        )
    }

    fun initializePreferences() {
        _uiState.update {
            it.copy(
                checkModuleUpdate = settingsRepo.checkModuleUpdate,
                sortEnabledFirst = settingsRepo.moduleSortEnabledFirst,
                sortActionFirst = settingsRepo.moduleSortActionFirst,
            )
        }
        updateModuleList()
    }

    fun toggleSortActionFirst() {
        val newValue = !_uiState.value.sortActionFirst
        settingsRepo.moduleSortActionFirst = newValue
        _uiState.update { it.copy(sortActionFirst = newValue) }
        updateModuleList()
    }

    fun toggleSortEnabledFirst() {
        val newValue = !_uiState.value.sortEnabledFirst
        settingsRepo.moduleSortEnabledFirst = newValue
        _uiState.update { it.copy(sortEnabledFirst = newValue) }
        updateModuleList()
    }

    fun refreshEnvironmentState() {
        val request = environmentRequests.next()
        val activation = ModeRuntime.activation
        val mode = (activation as? ModeActivation.Active)?.mode
        environmentRefreshJob?.cancel()
        environmentRefreshJob = viewModelScope.launch {
            val (magiskInstalled, ksuSafeMode, environment) = withContext(Dispatchers.IO) {
                if (mode == WorkMode.LITE) {
                    runCatching { UltraEnvironmentProbe.refresh(ksuApp, force = false) }
                        .onFailure { if (it is CancellationException) throw it }
                }
                val magiskInstalled = hasMagisk()
                val ksuSafeMode = mode == WorkMode.KSU && runCatching { Natives.isSafeMode }
                    .getOrElse { if (it is CancellationException) throw it else false }
                Triple(magiskInstalled, ksuSafeMode, readEnvironmentDisplayState(activation))
            }
            _uiState.update {
                if (activation !== ModeRuntime.activation) it else it.copy(
                    magiskInstalled = magiskInstalled,
                    isSafeMode = if (mode == WorkMode.KSU) ksuSafeMode else it.isSafeMode,
                ).withEnvironmentDisplayState(request, environment)
            }
        }
    }

    fun updateSearchStatus(status: SearchStatus) {
        val previous = _uiState.value.searchStatus
        _uiState.update {
            if (previous.searchText == status.searchText) it.copy(searchStatus = status) else {
                it.copy(
                    searchStatus = status.copy(resultStatus = searchLoadingStatusFor(status.searchText)),
                    searchResults = emptyList(),
                )
            }
        }
        if (previous.searchText != status.searchText) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch {
                delay(150)
                applySearchText(status.searchText)
            }
        }
    }

    fun updateSearchText(text: String) {
        updateSearchStatus(_uiState.value.searchStatus.copy(searchText = text))
    }

    private fun filterModules(modules: List<Module>, text: String): List<Module> {
        if (text.isEmpty()) return emptyList()

        return modules.filter {
            it.id.contains(text, true) || it.name.contains(text, true) ||
                    it.description.contains(text, true) || it.author.contains(text, true) ||
                    PinyinUtil.toPinyin(it.name).contains(text, true)
        }
    }

    private suspend fun applySearchText(text: String) {
        if (text != _uiState.value.searchStatus.searchText) return
        updateModuleList().join()
    }

    private fun updateModuleList(resort: Boolean = true): Job {
        val request = listRequests.next()
        listJob?.cancel()
        val snapshot = _uiState.value
        val inputs = snapshot.moduleListInputs()
        return viewModelScope.launch {
            val searchText = snapshot.searchStatus.searchText
            val (sorted, searchResults) = withContext(Dispatchers.IO) {
                val sorted = if (resort || snapshot.moduleList.isEmpty()) {
                    snapshot.modules.sortedWith(moduleComparator(snapshot))
                } else {
                    // Keep order, refresh data, drop removed and append new after an operation.
                    val byId = snapshot.modules.associateBy { it.id }
                    val existingIds = snapshot.moduleList.mapTo(HashSet()) { it.id }
                    snapshot.moduleList.mapNotNull { byId[it.id] } + snapshot.modules.filter { it.id !in existingIds }
                }
                sorted to filterModules(sorted, searchText)
            }

            _uiState.update { state ->
                if (!listRequests.isCurrent(request) || !inputs.matches(state.moduleListInputs())) state else state.copy(
                    moduleList = if (sorted == state.moduleList) state.moduleList else sorted,
                    searchResults = if (searchResults == state.searchResults) state.searchResults else searchResults,
                    searchStatus = state.searchStatus.copy(
                        resultStatus = searchResultStatusFor(searchText, searchResults.isEmpty())
                    )
                )
            }
        }.also { listJob = it }
    }

    private fun moduleComparator(state: ModuleUiState): Comparator<Module> {
        return compareBy<Module>(
            {
                val executable = it.hasWebUi || it.hasActionScript
                when {
                    it.metamodule && it.enabled -> 0
                    state.sortEnabledFirst && state.sortActionFirst -> when {
                        it.enabled && executable -> 1
                        it.enabled -> 2
                        executable -> 3
                        else -> 4
                    }

                    state.sortEnabledFirst && !state.sortActionFirst -> if (it.enabled) 1 else 2
                    !state.sortEnabledFirst && state.sortActionFirst -> if (executable) 1 else 2
                    else -> 1
                }
            },
            { if (state.sortEnabledFirst) !it.enabled else 0 },
            { if (state.sortActionFirst) !(it.hasWebUi || it.hasActionScript) else 0 },
        ).thenBy(Collator.getInstance(Locale.getDefault()), Module::id)
    }

    private suspend fun loadModuleList(resort: Boolean, request: Long): Boolean {
        val started = SystemClock.elapsedRealtime()
        val operationGeneration = moduleOperationGeneration
        val environmentRequest = environmentRequests.next()
        val activation = ModeRuntime.activation
        val (result, environment) = withContext(Dispatchers.IO) {
            val backendStarted = SystemClock.elapsedRealtime()
            var count = -1
            val modules = try {
                repo.getModules { readScope ->
                    withContext(Dispatchers.Main) {
                        if (refreshRequests.isCurrent(request) && operationGeneration == moduleOperationGeneration &&
                            activation === ModeRuntime.activation &&
                            moduleListReadScope != readScope) {
                            moduleListReadScope = readScope
                            _uiState.update { it.withModuleReadContextReset() }
                            updateInfoMutex.withLock {
                                updateInfoCache.clear()
                                updateInfoInFlight.clear()
                            }
                        }
                    }
                }.also { count = it.getOrNull()?.size ?: -1 }
            } finally {
                Log.i(TAG, "MAPLESU_MODULE_LATENCY phase=backend count=$count elapsed=${SystemClock.elapsedRealtime() - backendStarted}")
            }
            modules to readEnvironmentDisplayState(activation)
        }

        return withContext(Dispatchers.Main) {
            if (!refreshRequests.isCurrent(request)) return@withContext false
            if (operationGeneration != moduleOperationGeneration || activation !== ModeRuntime.activation) return@withContext false
            val previous = _uiState.value
            _uiState.update { it.withModuleLoadResult(result).withEnvironmentDisplayState(environmentRequest, environment) }
            // T10：终态（成功或具名失败）发布之后不再等待、也不再自动重取（幂等）。
            startupGate.onTerminal()
            if (result.isSuccess) {
                val shouldResort = resort || fetchResort
                if (shouldResort || !previous.hasLoaded || previous.modules !== _uiState.value.modules) {
                    updateModuleList(shouldResort).join()
                }
                if (!shouldResort && fetchResort && refreshRequests.isCurrent(request)) {
                    updateModuleList().join()
                }
                if (refreshRequests.isCurrent(request) && operationGeneration == moduleOperationGeneration &&
                    activation === ModeRuntime.activation) {
                    isNeedRefresh = false
                    Log.i(TAG, "MAPLESU_MODULE_LATENCY phase=list-published count=${_uiState.value.moduleList.size} elapsed=${SystemClock.elapsedRealtime() - started}")
                }
            }
            result.isSuccess && refreshRequests.isCurrent(request) &&
                operationGeneration == moduleOperationGeneration && activation === ModeRuntime.activation
        }
    }

    fun fetchModuleList(checkUpdate: Boolean = false, resort: Boolean = true, showRefreshing: Boolean = true, coalesce: Boolean = false) {
        val activation = ModeRuntime.activation
        if (coalesce && fetchJob?.isActive == true && fetchActivation === activation &&
            fetchOperationGeneration == moduleOperationGeneration) {
            fetchCheckUpdate = fetchCheckUpdate || checkUpdate
            if (resort && !fetchResort) {
                fetchResort = true
                if (!fetchListPending) updateModuleList()
            }
            if (showRefreshing && fetchListPending) _uiState.update { it.copy(isRefreshing = true) }
            return
        }
        if (moduleListActivation !== activation) {
            moduleListActivation = activation
            moduleListReadScope = null
            _uiState.update { it.withModuleReadContextReset() }
        }
        // T10（D4）：运行环境未接线时**不取**列表。旧实现先取一次，撞上
        // Unresolved/Unavailable 就把 loadError + hasLoaded 一起置位 ⇒ 永久空态。
        when (val decision = startupGate.onLoadRequested()) {
            ModuleLoadDecision.Fetch -> Unit
            is ModuleLoadDecision.Wait -> {
                _uiState.update { it.withModulePending(decision.reason) }
                awaitEnvironmentWiring(decision.reason)
                return
            }
        }
        // Invalidate before cancellation: the old finally may run immediately.
        val request = refreshRequests.next()
        fetchJob?.cancel()
        fetchOperationGeneration = moduleOperationGeneration
        fetchActivation = activation
        fetchCheckUpdate = checkUpdate
        fetchResort = resort
        fetchListPending = true
        _uiState.update { it.copy(isRefreshing = showRefreshing || !it.hasLoaded) }
        fetchJob = viewModelScope.launch {
            try {
                val start = SystemClock.elapsedRealtime()

                val loaded = loadModuleList(resort, request)
                // Installed rows are ready; update metadata is a separate, optional query.
                if (refreshRequests.isCurrent(request)) {
                    fetchListPending = false
                    _uiState.update { it.copy(isRefreshing = false) }
                }
                if (loaded && fetchCheckUpdate) {
                    val updateStarted = SystemClock.elapsedRealtime()
                    try { syncModuleUpdateInfo(_uiState.value.modules) }
                    catch (error: CancellationException) { throw error }
                    catch (error: Exception) { Log.w(TAG, "Module update lookup failed", error) }
                    finally {
                        Log.i(TAG, "MAPLESU_MODULE_LATENCY phase=optional-update count=${_uiState.value.modules.size} elapsed=${SystemClock.elapsedRealtime() - updateStarted}")
                    }
                }

                Log.i(TAG, "MAPLESU_MODULE_LATENCY phase=refresh-complete count=${_uiState.value.modules.size} elapsed=${SystemClock.elapsedRealtime() - start}")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Module list refresh failed", error)
                if (refreshRequests.isCurrent(request)) {
                    _uiState.update { it.withModuleLoadResult(Result.failure(error)) }
                    startupGate.onTerminal()
                }
            } finally {
                // An obsolete/cancelled request never ends the newer request's feedback.
                if (refreshRequests.isCurrent(request)) {
                    fetchListPending = false
                    _uiState.update { it.copy(isRefreshing = false) }
                }
            }
        }
    }

    /**
     * T10：订阅**一次性**运行环境就绪事件（激活 + KSU 后端接线）。
     *
     * 一个协程、只挂起、**不轮询、不常驻线程**，无论就绪/超时都会结束：
     * - 就绪 → 自动重取**一次**（`startupGate` 保证每次等待最多一次）；
     * - 超时/接线失败 → 允许一次如实失败的取列表（失败必须可见，不能无限"启动中"）。
     */
    private fun awaitEnvironmentWiring(reason: ModuleWaitReason) {
        // 单飞：等待期间的重复请求（预取/进页/resume/下拉刷新）不重复订阅。
        if (environmentWaitJob?.isActive == true) return
        environmentWaitJob = viewModelScope.launch {
            val ready = ModuleEnvironmentWatch.awaitReady(reason, ENVIRONMENT_WAIT_TIMEOUT_MS)
            val refetch = if (ready) {
                startupGate.onEnvironmentReady()
            } else {
                Log.w(TAG, "environment still not ready after ${ENVIRONMENT_WAIT_TIMEOUT_MS}ms (${reason.code})")
                startupGate.onWaitTimedOut()
            }
            if (!refetch) return@launch
            Log.i(TAG, "environment ready (was ${reason.code}); refetching the module list once")
            fetchModuleList(checkUpdate = true)
        }
    }

    private fun Module.toSignature(): ModuleUpdateSignature {
        return ModuleUpdateSignature(
            updateJson = updateJson,
            versionCode = versionCode,
            enabled = enabled,
            update = update,
            remove = remove
        )
    }

    suspend fun syncModuleUpdateInfo(modules: List<Module>) {
        if (!_uiState.value.checkModuleUpdate) return

        val owner = currentCoroutineContext().job
        val modulesToFetch = mutableListOf<Triple<String, Module, ModuleUpdateSignature>>()
        val removedIds = mutableSetOf<String>()

        updateInfoMutex.withLock {
            val ids = modules.map { it.id }.toSet()
            updateInfoCache.keys.filter { it !in ids }.forEach { removedId ->
                removedIds += removedId
                updateInfoCache.remove(removedId)
                updateInfoInFlight.remove(removedId)
            }

            modules.forEach { module ->
                val signature = module.toSignature()
                val cached = updateInfoCache[module.id]
                if ((cached == null || cached.signature != signature) && updateInfoInFlight[module.id]?.isActive != true) {
                    updateInfoInFlight[module.id] = owner
                    modulesToFetch += Triple(module.id, module, signature)
                }
            }
        }

        try {
            val fetchedEntries = coroutineScope {
                modulesToFetch.map { (id, module, signature) ->
                    async {
                        val info = withTimeoutOrNull(5_000L.milliseconds) {
                            withContext(Dispatchers.IO) {
                                runCatching { checkUpdate(module) }
                                    .getOrElse { e ->
                                        if (e is CancellationException) throw e
                                        Log.w(TAG, "checkUpdate failed for ${module.id}", e)
                                        ModuleUpdateInfo.Empty
                                    }
                            }
                        } ?: ModuleUpdateInfo.Empty
                        id to ModuleUpdateCache(signature, info)
                    }
                }.awaitAll()
            }

            withContext(Dispatchers.Main) {
                updateInfoMutex.withLock {
                    val currentModules = _uiState.value.modules.associateBy { it.id }
                    val changedEntries = mutableListOf<Pair<String, ModuleUpdateCache>>()
                    fetchedEntries.forEach { (id, entry) ->
                        if (updateInfoInFlight[id] !== owner || currentModules[id]?.toSignature() != entry.signature) {
                            return@forEach
                        }
                        val existing = updateInfoCache[id]
                        if (existing == null || existing.signature != entry.signature || existing.info != entry.info) {
                            updateInfoCache[id] = entry
                            changedEntries += id to entry
                        }
                    }

                    if (removedIds.isEmpty() && changedEntries.isEmpty()) return@withLock

                    _uiState.update { state ->
                        val liveModules = state.modules.associateBy { it.id }
                        val newMap = state.updateInfo.toMutableMap()
                        removedIds.filter { it !in liveModules }.forEach { newMap.remove(it) }
                        changedEntries.forEach { (id, entry) ->
                            if (liveModules[id]?.toSignature() == entry.signature) newMap[id] = entry.info
                        }
                        state.copy(updateInfo = newMap)
                    }
                }
            }
        } finally {
            // A cancelled refresh may finish after its successor has claimed the same ID.
            withContext(NonCancellable) {
                updateInfoMutex.withLock {
                    modulesToFetch.forEach { (id, _, _) ->
                        if (updateInfoInFlight[id] === owner) updateInfoInFlight.remove(id)
                    }
                }
            }
        }
    }

    fun requestUpdateConfirmation(module: Module, updateInfo: ModuleUpdateInfo) {
        viewModelScope.launch {
            val verification = registryRepo.resolveUpdate(
                moduleId = module.id,
                updateJsonUrl = module.updateJson,
                zipUrl = updateInfo.downloadUrl,
                versionCode = updateInfo.versionCode,
            )
            if (verification is UpdateVerification.Rejected) {
                Log.w(TAG, "refusing update of ${module.id}: ${verification.reason}")
                emitEffect(ModuleEffect.Toast(ksuApp.getString(R.string.module_update_unverifiable, module.name)))
                return@launch
            }
            val dialogState = buildUpdateConfirmDialogState(module, updateInfo, verification)
            _uiState.update { it.copy(confirmDialogState = dialogState) }
        }
    }

    fun requestUninstallConfirmation(module: Module) {
        val res = ksuApp.resources
        _uiState.update {
            it.copy(
                confirmDialogState = ModuleConfirmDialogState(
                    request = ModuleConfirmRequest.Uninstall(module),
                    title = res.getString(R.string.module),
                    content = (if (module.metamodule) res.getString(R.string.metamodule_uninstall_confirm) else res.getString(R.string.module_uninstall_confirm)).format(
                        module.name
                    ),
                    confirm = res.getString(R.string.uninstall),
                    dismiss = res.getString(android.R.string.cancel),
                )
            )
        }
    }

    fun dismissConfirmRequest() {
        _uiState.update { it.copy(confirmDialogState = null) }
    }

    fun emitEffect(effect: ModuleEffect) {
        _moduleEvent.trySend(effect)
    }

    fun toggleModule(module: Module) {
        val current = _uiState.value.modules.firstOrNull { it.id == module.id } ?: return
        if (!current.canToggle || current.remove) return
        runModuleOperation(
            module = current,
            failureMessage = if (current.enabled) R.string.module_failed_to_disable else R.string.module_failed_to_enable,
            enabled = !current.enabled,
        ) { repo.toggleModule(current.id, !current.enabled) }
    }

    fun uninstallModule(module: Module) {
        val current = _uiState.value.modules.firstOrNull { it.id == module.id } ?: return
        if (!current.id.startsWith("maplesu:")) {
            setModuleRemoval(current, remove = true)
            return
        }
        runModuleOperation(
            module = current,
            failureMessage = R.string.module_uninstall_failed,
            dismissConfirmation = true,
            remove = true,
        ) { repo.uninstallModule(current.id) }
    }

    fun undoUninstallModule(module: Module) {
        val current = _uiState.value.modules.firstOrNull { it.id == module.id } ?: return
        if (!current.id.startsWith("maplesu:")) {
            setModuleRemoval(current, remove = false)
            return
        }
        runModuleOperation(
            module = current,
            failureMessage = R.string.module_undo_uninstall_failed,
            remove = false,
        ) { repo.undoUninstallModule(current.id) }
    }

    private fun setModuleRemoval(module: Module, remove: Boolean) {
        val running = module.id in _uiState.value.removalChanges
        if (!running && !moduleOperations.begin(module.id)) return
        moduleOperationGeneration++
        _uiState.update {
            it.withModuleRemovalRequest(module.id, remove).copy(
                pendingModuleIds = moduleOperations.ids,
                confirmDialogState = null,
                isRefreshing = false,
            )
        }
        // An undo can replace the desired state while a previous write is still running.
        if (running) return
        viewModelScope.launch {
            try {
                while (true) {
                    val target = _uiState.value.removalChanges[module.id]?.remove ?: break
                    val success = moduleOperations.execute {
                        withContext(Dispatchers.IO) {
                            try {
                                (if (target) repo.uninstallModule(module.id) else repo.undoUninstallModule(module.id)).getOrThrow()
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                Log.w(TAG, "Module removal failed for ${module.id}", error)
                                false
                            }
                        }
                    }
                    moduleOperationGeneration++
                    _uiState.update { it.withModuleRemovalResult(module.id, target, success) }
                    val latest = _uiState.value.removalChanges[module.id] ?: break
                    if (latest.remove != target && latest.remove != latest.confirmed.remove) continue
                    _uiState.update { it.finishModuleRemoval(module.id) }
                    if (!success && latest.remove == target) {
                        val message = if (target) R.string.module_uninstall_failed else R.string.module_undo_uninstall_failed
                        emitEffect(ModuleEffect.SnackBar(ksuApp.getString(message).format(module.name)))
                    }
                    break
                }
            } finally {
                moduleOperations.finish(module.id)
                moduleOperationGeneration++
                _uiState.update { it.finishModuleRemoval(module.id).copy(pendingModuleIds = moduleOperations.ids) }
                readbackAfterModuleOperations()
            }
        }
    }

    private fun runModuleOperation(
        module: Module,
        failureMessage: Int,
        dismissConfirmation: Boolean = false,
        enabled: Boolean? = null,
        remove: Boolean? = null,
        operation: suspend () -> Result<Boolean>,
    ) {
        if (!moduleOperations.begin(module.id)) return
        moduleOperationGeneration++
        // Publish before launching: the very first tap both responds and prevents duplicates.
        _uiState.update {
            val changed = when {
                enabled != null -> it.withModuleToggleRequest(module.id, enabled)
                remove != null -> it.withModuleRemovalRequest(module.id, remove)
                else -> it
            }
            val confirmation = changed.confirmDialogState?.request as? ModuleConfirmRequest.Uninstall
            changed.copy(
                pendingModuleIds = moduleOperations.ids,
                confirmDialogState = if (dismissConfirmation && confirmation?.module?.id == module.id) null else it.confirmDialogState,
                isRefreshing = false,
            )
        }
        viewModelScope.launch {
            var success = false
            try {
                success = moduleOperations.execute {
                    withContext(Dispatchers.IO) {
                        try {
                            operation().getOrThrow()
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            Log.w(TAG, "Module operation failed for ${module.id}", error)
                            false
                        }
                    }
                }
                if (remove != null) _uiState.update { it.withModuleRemovalResult(module.id, remove, success) }
                if (remove != null && module.id.startsWith("maplesu:")) moduleOperationReadbackNeeded = true
                if (!success) {
                    // Native mutations can commit before their receipt is lost. Reconcile
                    // after the queue drains, without list loading or holding any card.
                    if (module.id.startsWith("maplesu:")) moduleOperationReadbackNeeded = true
                    emitEffect(ModuleEffect.SnackBar(ksuApp.getString(failureMessage).format(module.name)))
                }
            } finally {
                moduleOperations.finish(module.id)
                moduleOperationGeneration++
                _uiState.update {
                    it.finishModuleToggle(module.id, success).finishModuleRemoval(module.id)
                        .copy(pendingModuleIds = moduleOperations.ids)
                }
                readbackAfterModuleOperations()
            }
        }
    }

    private fun readbackAfterModuleOperations() {
        if (!moduleOperationReadbackNeeded || moduleOperations.ids.isNotEmpty()) return
        moduleOperationReadbackNeeded = false
        fetchModuleList(checkUpdate = false, resort = false, showRefreshing = false)
    }

    private suspend fun buildUpdateConfirmDialogState(
        module: Module,
        updateInfo: ModuleUpdateInfo,
        verification: UpdateVerification,
    ): ModuleConfirmDialogState {
        val res = ksuApp.resources
        val changelogUrl = updateInfo.changelog

        var changelog = ""
        var html = false
        // Registry-hosted content is always shown in the restricted (JavaScript-off) renderer.
        var restricted = false

        withContext(Dispatchers.IO) {
            if (verification is UpdateVerification.Registry) {
                // A registry release: only ever show the registry's own release notes.
                verification.release.changelogHtml?.takeIf { it.isNotBlank() }?.let {
                    changelog = it
                    html = true
                    restricted = true
                }
                if (changelog.isBlank() && registryRepo.isRegistryUrl(changelogUrl)) {
                    registryRepo.fetchRegistryText(changelogUrl)?.let {
                        changelog = it
                        restricted = true
                    }
                }
                return@withContext
            }
            if (changelogUrl.isBlank()) return@withContext
            if (changelogUrl.startsWith("#") && changelogUrl.contains('@')) {
                val parts = changelogUrl.substring(1).split('@', limit = 2)
                if (parts.size == 2) {
                    registryRepo.fetchChangelogHtml(parts[0], parts[1])?.let {
                        changelog = it
                        html = true
                        restricted = true
                    }
                }
                return@withContext
            }
            if (registryRepo.isRegistryUrl(changelogUrl)) {
                registryRepo.fetchRegistryText(changelogUrl)?.let {
                    changelog = it
                    restricted = true
                }
                return@withContext
            }
            changelog = runCatching {
                ksuApp.okhttpClient.newCall(
                    Request.Builder().url(changelogUrl).build()
                ).execute().use { it.body.string() }
            }.getOrDefault("")
        }

        val registry = verification as? UpdateVerification.Registry
        return ModuleConfirmDialogState(
            request = ModuleConfirmRequest.Update(
                module = module,
                downloadUrl = registry?.zipUrl ?: updateInfo.downloadUrl,
                fileName = "${module.name}-${updateInfo.version}.zip",
                expected = registry?.expected,
            ),
            title = if (changelog.isNotBlank()) res.getString(R.string.module_changelog) else res.getString(R.string.module_update),
            content = changelog.ifBlank { res.getString(R.string.module_start_downloading).format(module.name) },
            markdown = changelog.isNotBlank() && !html,
            html = changelog.isNotBlank() && html,
            restricted = changelog.isNotBlank() && restricted,
            confirm = res.getString(R.string.module_update),
        )
    }

    private suspend fun checkUpdate(m: Module): ModuleUpdateInfo {
        return repo.checkUpdate(m).getOrDefault(ModuleUpdateInfo.Empty)
    }
}
