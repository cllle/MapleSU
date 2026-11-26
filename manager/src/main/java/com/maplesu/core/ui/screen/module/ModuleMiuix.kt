package com.maplesu.core.ui.screen.module

import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.FixedScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import com.maplesu.core.R
import com.maplesu.core.data.model.Module
import com.maplesu.core.data.model.ModuleUpdateInfo
import com.maplesu.core.data.repository.isSoftRebootPreferred
import com.maplesu.core.ui.component.ListPopupDefaults
import com.maplesu.core.ui.component.ObserveAsEvents
import com.maplesu.core.ui.component.ScrollToTopOnChange
import com.maplesu.core.ui.component.SearchStatus
import com.maplesu.core.ui.component.dialog.rememberConfirmDialog
import com.maplesu.core.ui.component.miuix.SearchBarFake
import com.maplesu.core.ui.component.miuix.SearchBox
import com.maplesu.core.ui.component.miuix.SearchPager
import com.maplesu.core.ui.theme.LocalEnableBlur
import com.maplesu.core.ui.theme.LocalModuleDescriptionMaxLines
import com.maplesu.core.ui.theme.miuixDialogColor
import com.maplesu.core.ui.util.BlurredBar
import com.maplesu.core.ui.util.miuixBarColor
import com.maplesu.core.ui.util.getFileName
import com.maplesu.core.ui.util.reboot
import com.maplesu.core.ui.util.rememberBlurBackdrop
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.maplesu.core.ui.util.ModuleCardArt
import com.maplesu.core.ui.util.drawCardArt
import dev.maplesu.manager.modules.webui.ModuleWebUiPlan.ULTRA_ID_PREFIX
import com.maplesu.core.ui.screen.maplemarket.MapleMarketInstallMenu
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.FloatingActionButtonDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.icon.extended.Undo
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@SuppressLint("StringFormatInvalid", "LocalContextGetResourceValueCall")
@Composable
fun ModulePagerMiuix(
    uiState: ModuleUiState,
    confirmDialogState: ModuleConfirmDialogState?,
    moduleEvent: Flow<ModuleEffect>,
    actions: ModuleActions,
    bottomInnerPadding: Dp,
    listState: LazyListState = rememberLazyListState(),
    searchListState: LazyListState = rememberLazyListState(),
) {
    val modules = uiState.moduleList
    val searchStatus = uiState.searchStatus

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val density = LocalDensity.current
    val enableBlur = LocalEnableBlur.current

    val installPromptWithName = stringResource(R.string.module_install_prompt_with_name, "%s")
    val confirmDialog = rememberConfirmDialog(
        onConfirm = {
            when (val request = confirmDialogState?.request) {
                is ModuleConfirmRequest.Uninstall -> {
                    actions.onUninstallModule(request.module)
                }

                is ModuleConfirmRequest.Update -> {
                    actions.onConfirmUpdate(request)
                }

                null -> Unit
            }
        },
        onDismiss = actions.onDismissConfirmRequest,
    )

    val scrollBehavior = MiuixScrollBehavior()
    val dynamicTopPadding by remember {
        derivedStateOf { 12.dp * (1f - scrollBehavior.state.collapsedFraction) }
    }

    val shortcutState = rememberModuleShortcutState(context)
    val showShortcutDialog = remember { mutableStateOf(false) }

    val pickShortcutIconLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        shortcutState.updateIconUri(uri?.toString())
    }

    LaunchedEffect(confirmDialogState) {
        confirmDialogState?.let {
            confirmDialog.showConfirm(
                title = it.title,
                content = it.content,
                markdown = it.markdown,
                html = it.html,
                confirm = it.confirm,
                dismiss = it.dismiss,
                restricted = it.restricted,
            )
        }
    }

    val scope = rememberCoroutineScope()
    val snackbarJob = remember { mutableStateOf<Job?>(null) }
    ObserveAsEvents(moduleEvent) { event ->
        when (event) {
            is ModuleEffect.Toast -> {
                Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }

            is ModuleEffect.SnackBar -> {
                // Cancel the previous reboot snackbar so a new one replaces it instead of queueing
                snackbarJob.value?.cancel()
                snackbarHostState.newestSnackbarData()?.dismiss()
                // Soft reboot keeps the jailbreak and still applies module changes
                val softReboot = isSoftRebootPreferred()
                snackbarJob.value = scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = context.getString(if (softReboot) R.string.reboot_soft else R.string.reboot),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        reboot(if (softReboot) "soft_reboot" else "")
                    }
                }
            }
        }
    }

    fun onModuleAddShortcut(module: Module, type: ShortcutType) {
        shortcutState.bindModule(module)
        shortcutState.selectType(type)
        showShortcutDialog.value = true
    }

    var fabHeight by remember { mutableStateOf(FloatingActionButtonDefaults.MinHeight) }
    val listBottomPadding = bottomInnerPadding + 12.dp +
        (if (uiState.installButtonVisible) fabHeight + 36.dp else 0.dp)

    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = miuixBarColor(blurActive)

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                searchStatus.TopAppBarAnim(backgroundColor = barColor) {
                    TopAppBar(
                        color = barColor,
                        title = stringResource(R.string.module),
                        actions = {
                            Box {
                                val showTopPopup = remember { mutableStateOf(false) }
                                IconButton(
                                    onClick = { showTopPopup.value = true },
                                    holdDownState = showTopPopup.value
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.Sort,
                                        tint = colorScheme.onSurface,
                                        contentDescription = null
                                    )
                                }
                                OverlayListPopup(
                                    show = showTopPopup.value,
                                    popupPositionProvider = ListPopupDefaults.MenuPositionProvider,
                                    alignment = PopupPositionProvider.Align.TopEnd,
                                    onDismissRequest = {
                                        showTopPopup.value = false
                                    },
                                    content = {
                                        ListPopupColumn {
                                            DropdownImpl(
                                                text = stringResource(R.string.module_sort_action_first),
                                                optionSize = 2,
                                                isSelected = uiState.sortActionFirst,
                                                onSelectedIndexChange = {
                                                    actions.onToggleSortActionFirst()
                                                    showTopPopup.value = false
                                                },
                                                index = 0
                                            )
                                            DropdownImpl(
                                                text = stringResource(R.string.module_sort_enabled_first),
                                                optionSize = 2,
                                                isSelected = uiState.sortEnabledFirst,
                                                onSelectedIndexChange = {
                                                    actions.onToggleSortEnabledFirst()
                                                    showTopPopup.value = false
                                                },
                                                index = 1
                                            )
                                        }
                                    }
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = actions.onOpenRepo,
                                enabled = uiState.modeResolved,
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Download,
                                    tint = colorScheme.onSurface,
                                    contentDescription = stringResource(if (uiState.isUltra) R.string.maple_market_title else R.string.module_repos)
                                )
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        bottomContent = {
                            Box(
                                modifier = Modifier
                                    .alpha(if (searchStatus.isCollapsed()) 1f else 0f)
                                    .onGloballyPositioned { coordinates ->
                                        with(density) {
                                            val newOffsetY = coordinates.positionInWindow().y.toDp()
                                            if (searchStatus.offsetY != newOffsetY) {
                                                actions.onSearchStatusChange(searchStatus.copy(offsetY = newOffsetY))
                                            }
                                        }
                                    }
                                    .then(
                                        if (searchStatus.isCollapsed()) {
                                            Modifier.pointerInput(Unit) {
                                                detectTapGestures {
                                                    actions.onSearchStatusChange(searchStatus.copy(current = SearchStatus.Status.EXPANDING))
                                                }
                                            }
                                        } else Modifier,
                                    ),
                            ) {
                                SearchBarFake(searchStatus.label, dynamicTopPadding)
                            }
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            if (uiState.installButtonVisible) {
                val moduleInstall = stringResource(id = R.string.module_install)
                val confirmTitle = stringResource(R.string.module)
                var zipUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
                val confirmDialog = rememberConfirmDialog(
                    onConfirm = {
                        actions.onOpenFlash(zipUris)
                    }
                )
                val selectZipLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { activityResult ->
                    val uris = mutableListOf<Uri>()
                    if (activityResult.resultCode != RESULT_OK) {
                        return@rememberLauncherForActivityResult
                    }
                    val data = activityResult.data ?: return@rememberLauncherForActivityResult
                    val clipData = data.clipData

                    if (clipData != null) {
                        for (i in 0 until clipData.itemCount) {
                            clipData.getItemAt(i)?.uri?.let { uris.add(it) }
                        }
                    } else {
                        data.data?.let { uris.add(it) }
                    }

                    if (uris.size == 1) {
                        actions.onOpenFlash(listOf(uris.first()))
                    } else if (uris.size > 1) {
                        // multiple files selected
                        zipUris = uris
                        val moduleNames = uris.mapIndexed { index, uri -> "\n${index + 1}. ${uri.getFileName(context)}" }.joinToString("")
                        val confirmContent = installPromptWithName.format(moduleNames)
                        confirmDialog.showConfirm(
                            title = confirmTitle,
                            content = confirmContent
                        )
                    }
                }
                MapleMarketInstallMenu(
                    isUltra = uiState.isUltra,
                    active = uiState.isCurrentPage,
                    modifier = Modifier.padding(bottom = bottomInnerPadding + 20.dp, end = 20.dp),
                    onMarket = actions.onOpenRepo,
                    onLocalFile = {
                        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        }
                        selectZipLauncher.launch(intent)
                    },
                ) { rotation, expanded, onClick ->
                    FloatingActionButton(
                        modifier = Modifier
                            .onSizeChanged { fabHeight = with(density) { it.height.toDp() } }
                            .border(0.05.dp, colorScheme.outline.copy(alpha = 0.5f), CircleShape),
                        shadowElevation = 0.dp,
                        onClick = onClick,
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            if (expanded) stringResource(R.string.maple_market_close_menu) else moduleInstall,
                            modifier = Modifier.size(40.dp).rotate(rotation),
                            tint = colorScheme.onPrimary,
                        )
                    }
                }
            }
        },
        popupHost = {
            searchStatus.SearchPager(
                onSearchStatusChange = actions.onSearchStatusChange,
                defaultResult = {},
                searchBarTopPadding = dynamicTopPadding,
            ) {
                val imeBottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
                ModuleList(
                    modifier = Modifier
                        .fillMaxSize()
                        .overScrollVertical(),
                    modules = uiState.searchResults,
                    updateInfoMap = uiState.updateInfo,
                    pendingModuleIds = uiState.pendingModuleIds,
                    removalChanges = uiState.removalChanges.keys,
                    recoveryOnly = uiState.recoveryOnly,
                    environmentWebUiAvailable = uiState.environmentWebUiAvailable,
                    actions = actions,
                    onModuleAddShortcut = ::onModuleAddShortcut,
                    contentPadding = PaddingValues(
                        top = 6.dp,
                        start = 0.dp,
                        end = 0.dp,
                        bottom = maxOf(bottomInnerPadding, imeBottomPadding),
                    ),
                    listState = searchListState,
                )
            }
        },
        snackbarHost = {
            SnackbarHost(
                state = snackbarHostState,
                modifier = if (uiState.installButtonVisible) {
                    Modifier
                } else {
                    // No FAB slot to stack above: keep the snackbar clear of the main bottom bar.
                    Modifier.padding(bottom = bottomInnerPadding + 20.dp)
                },
            )
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        if (uiState.magiskInstalled) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.module_magisk_conflict),
                    textAlign = TextAlign.Center,
                )
            }
            return@Scaffold
        }
        val layoutDirection = LocalLayoutDirection.current
        searchStatus.SearchBox {
            val pullToRefreshState = rememberPullToRefreshState()
            val refreshTexts = listOf(
                stringResource(R.string.refresh_pulling),
                stringResource(R.string.refresh_release),
                stringResource(R.string.refresh_refresh),
                stringResource(R.string.refresh_complete),
            )
            val contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 6.dp,
                start = innerPadding.calculateStartPadding(layoutDirection),
                end = innerPadding.calculateEndPadding(layoutDirection),
                bottom = listBottomPadding,
            )
            PullToRefresh(
                isRefreshing = uiState.isRefreshing,
                pullToRefreshState = pullToRefreshState,
                onRefresh = {
                    actions.onRefresh()
                },
                refreshTexts = refreshTexts,
                contentPadding = contentPadding,
            ) {
                if (modules.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                top = innerPadding.calculateTopPadding(),
                                start = innerPadding.calculateStartPadding(layoutDirection),
                                end = innerPadding.calculateEndPadding(layoutDirection),
                                bottom = bottomInnerPadding
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        uiState.emptyStateMessage?.let { message ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    stringResource(message),
                                    textAlign = TextAlign.Center,
                                    color = Color.Gray,
                                )
                                if (uiState.loadError != null) {
                                    TextButton(
                                        text = stringResource(R.string.network_retry),
                                        onClick = actions.onRefresh,
                                        enabled = !uiState.isRefreshing,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    val latestModules = rememberUpdatedState(modules)
                    val latestRefreshing = rememberUpdatedState(uiState.isRefreshing)
                    ScrollToTopOnChange(
                        listState,
                        uiState.sortEnabledFirst,
                        uiState.sortActionFirst,
                        isBusy = { latestRefreshing.value },
                    ) { latestModules.value }
                    Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
                        ModuleList(
                            modifier = Modifier
                                .fillMaxHeight()
                                .scrollEndHaptic()
                                .overScrollVertical()
                                .nestedScroll(scrollBehavior.nestedScrollConnection),
                            modules = modules,
                            updateInfoMap = uiState.updateInfo,
                            pendingModuleIds = uiState.pendingModuleIds,
                            removalChanges = uiState.removalChanges.keys,
                            recoveryOnly = uiState.recoveryOnly,
                            environmentWebUiAvailable = uiState.environmentWebUiAvailable,
                            actions = actions,
                            onModuleAddShortcut = { module, type ->
                                onModuleAddShortcut(module, type)
                            },
                            contentPadding = contentPadding,
                            listState = listState,
                            showLoadError = uiState.loadError != null,
                            isRefreshing = uiState.isRefreshing,
                        )
                    }
                }
            }
        }
    }
    ModuleShortcutDialog(
        show = showShortcutDialog.value,
        onDismissRequest = { showShortcutDialog.value = false },
        shortcutState = shortcutState,
        onPickShortcutIcon = { pickShortcutIconLauncher.launch("image/*") },
        onDeleteShortcut = {
            shortcutState.deleteShortcut(context)
            showShortcutDialog.value = false
        },
        onConfirmShortcut = {
            // 🔴（v2.19）createShortcut 现在要跑 root 命令：挪进 IO 协程，别堵主线程（onClick 非 @Composable）
            scope.launch { shortcutState.createShortcut(context) }
            showShortcutDialog.value = false
        },
    )
}

@Composable
private fun ModuleShortcutDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    shortcutState: ModuleShortcutState,
    onPickShortcutIcon: () -> Unit,
    onDeleteShortcut: () -> Unit,
    onConfirmShortcut: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current

    fun copyShortcutUrl() {
        val url = shortcutState.buildShortcutUrl() ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("MapleSU deep link", url))
        Toast.makeText(context, resources.getString(R.string.module_shortcut_scheme_copied), Toast.LENGTH_SHORT).show()
    }

    OverlayDialog(
        show = show,
        title = stringResource(R.string.module_shortcut_title),
        onDismissRequest = onDismissRequest,
        content = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(vertical = 16.dp)
                        .size(100.dp)
                        .clip(RoundedCornerShape(25.dp))
                ) {
                    val preview = shortcutState.previewIcon
                    if (preview != null) {
                        Image(
                            bitmap = preview,
                            modifier = Modifier.size(100.dp),
                            contentDescription = null,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .background(Color.White)
                        )
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            contentScale = FixedScale(1.5f)
                        )
                    }
                }
                Row {
                    TextButton(
                        modifier = Modifier.weight(1f),
                        text = stringResource(id = R.string.module_shortcut_icon_pick),
                        onClick = onPickShortcutIcon,
                    )
                    AnimatedVisibility(
                        visible = shortcutState.iconUri != shortcutState.defaultShortcutIconUri,
                        enter = expandHorizontally() + slideInHorizontally(initialOffsetX = { it }),
                        exit = shrinkHorizontally() + slideOutHorizontally(targetOffsetX = { it }),
                        modifier = Modifier.align(Alignment.CenterVertically),
                    ) {
                        IconButton(
                            onClick = shortcutState::resetIconToDefault,
                            modifier = Modifier.padding(start = 12.dp)
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Undo,
                                contentDescription = null,
                                tint = colorScheme.onSurface,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }
                TextField(
                    value = shortcutState.name,
                    onValueChange = shortcutState::updateName,
                    label = stringResource(id = R.string.module_shortcut_name_label)
                )
                if (shortcutState.hasExistingShortcut) {
                    TextButton(
                        text = stringResource(id = R.string.module_shortcut_delete),
                        onClick = onDeleteShortcut,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(
                    text = stringResource(id = R.string.module_shortcut_copy_scheme),
                    onClick = ::copyShortcutUrl,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = stringResource(id = android.R.string.cancel),
                        onClick = onDismissRequest,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = if (shortcutState.hasExistingShortcut) {
                            stringResource(id = R.string.module_update)
                        } else {
                            stringResource(id = android.R.string.ok)
                        },
                        onClick = onConfirmShortcut,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    )
}

@Composable
private fun ModuleList(
    modifier: Modifier = Modifier,
    modules: List<Module>,
    updateInfoMap: Map<String, ModuleUpdateInfo>,
    pendingModuleIds: Set<String>,
    removalChanges: Set<String>,
    actions: ModuleActions,
    onModuleAddShortcut: (Module, ShortcutType) -> Unit,
    contentPadding: PaddingValues,
    listState: LazyListState = rememberLazyListState(),
    recoveryOnly: Boolean = false,
    environmentWebUiAvailable: Boolean = false,
    showLoadError: Boolean = false,
    isRefreshing: Boolean = false,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxHeight(),
        contentPadding = contentPadding,
        overscrollEffect = null,
    ) {
        if (recoveryOnly) {
            item(key = "ultra-safe-mode", contentType = "safe-mode") {
                com.maplesu.core.ui.screen.home.HomeSafeModeBanner(
                    modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                )
            }
        }
        if (showLoadError) {
            item(key = -1, contentType = "module-load-error") {
                Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                    Text(
                        stringResource(R.string.module_refresh_failed),
                        color = colorScheme.error,
                    )
                    TextButton(
                        text = stringResource(R.string.network_retry),
                        onClick = actions.onRefresh,
                        enabled = !isRefreshing,
                    )
                }
            }
        }
        items(
            items = modules,
            key = { it.id },
            contentType = { "module" }
        ) { module ->
            val currentModuleState = rememberUpdatedState(module)
            val moduleUpdateInfo = updateInfoMap[module.id] ?: ModuleUpdateInfo.Empty
            val content: @Composable () -> Unit = {
                ModuleItem(
                    modifier = Modifier.animateItem(),
                    module = module,
                    isPending = module.id in pendingModuleIds,
                    removalPending = module.id in removalChanges,
                    recoveryOnly = recoveryOnly,
                    environmentWebUiAvailable = environmentWebUiAvailable,
                    updateUrl = moduleUpdateInfo.downloadUrl,
                    onUninstall = {
                        actions.onRequestUninstallConfirmation(currentModuleState.value)
                    },
                    onUndoUninstall = {
                        actions.onUndoUninstallModule(currentModuleState.value)
                    },
                    onCheckChanged = { _: Boolean ->
                        actions.onToggleModule(currentModuleState.value)
                    },
                    onUpdate = {
                        actions.onRequestUpdateConfirmation(currentModuleState.value, moduleUpdateInfo)
                    },
                    onExecuteAction = {
                        actions.onExecuteModuleAction(currentModuleState.value)
                    },
                    onAddActionShortcut = { type: ShortcutType ->
                        onModuleAddShortcut(currentModuleState.value, type)
                    },
                    onOpenWebUi = {
                        if (module.hasWebUi) {
                            actions.onOpenWebUi(module)
                        }
                    }
                )
            }

            content()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModuleItem(
    module: Module,
    updateUrl: String,
    onUndoUninstall: () -> Unit,
    onUninstall: () -> Unit,
    onCheckChanged: (Boolean) -> Unit,
    onUpdate: () -> Unit,
    onExecuteAction: () -> Unit,
    onAddActionShortcut: (ShortcutType) -> Unit,
    onOpenWebUi: () -> Unit,
    isPending: Boolean = false,
    removalPending: Boolean = false,
    recoveryOnly: Boolean = false,
    environmentWebUiAvailable: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val secondaryContainer = colorScheme.secondaryContainer
    val actionIconTint = colorScheme.onSurface
    val updateBg = colorScheme.tertiaryContainer
    val updateTint = colorScheme.onTertiaryContainer
    val hasUpdate = updateUrl.isNotEmpty()
    val textDecoration = if (module.remove) TextDecoration.LineThrough else null
    val hasDescription = module.description.isNotBlank()
    val maxLinesLimit = LocalModuleDescriptionMaxLines.current
    var expanded by rememberSaveable(module.id) { mutableStateOf(false) }

    val context = LocalContext.current
    val cardArt by produceState<ImageBitmap?>(null, context, module.id, ModuleCardArt.version) {
        value = withContext(Dispatchers.IO) { ModuleCardArt.load(context, module.id, 1024) }
    }
    var showCardArtDialog by remember { mutableStateOf(false) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    val art = cardArt
    val cardArtScrim = colorScheme.surfaceContainer.copy(alpha = ModuleCardArt.SCRIM)
    val cardArtModifier = if (art != null) {
        Modifier.squircleClip(CardDefaults.CornerRadius).drawWithContent {
            drawCardArt(art, size, cardArtScrim)
            drawContent()
        }
    } else Modifier
    val toggle: @Composable () -> Unit = {
        Switch(
            modifier = Modifier.heightIn(min = 48.dp),
            enabled = !recoveryOnly && module.canToggle && !module.remove && !isPending,
            checked = module.enabled,
            onCheckedChange = { if (it != module.enabled) onCheckChanged(it) },
        )
    }

    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)
            .onSizeChanged { cardSize = it }.then(cardArtModifier),
        insideMargin = PaddingValues(16.dp),
        colors = if (art != null) CardColors(Color.Transparent, colorScheme.onSurfaceContainer)
            else CardDefaults.defaultColors(),
        onClick = if (hasDescription) { { expanded = !expanded } } else null,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val ownToggleRow = moduleCardNeedsOwnToggleRow(maxWidth.value, LocalDensity.current.fontScale)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    com.maplesu.core.ui.component.miuix.ModuleIcon(moduleId = module.id, moduleName = module.name)
                    Text(
                        text = module.name,
                        modifier = Modifier.weight(1f),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                        textDecoration = textDecoration,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!ownToggleRow) toggle()
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ModuleCardBadges(module, Modifier.weight(1f))
                    if (ownToggleRow) toggle()
                }
            }
        }
        Text(
            text = "${stringResource(R.string.module_version)}: ${module.version}",
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            fontSize = 13.sp,
            color = colorScheme.onSurfaceVariantSummary,
            textDecoration = textDecoration,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${stringResource(R.string.module_author)}: ${module.author}",
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp),
            fontSize = 13.sp,
            color = colorScheme.onSurfaceVariantSummary,
            textDecoration = textDecoration,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (hasDescription) {
            ExpandableDescriptionText(
                text = module.description,
                expanded = expanded,
                maxLinesLimit = maxLinesLimit,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp),
                fontSize = 14.sp,
                color = colorScheme.onSurfaceVariantSummary,
                textDecoration = textDecoration,
            )
        }
        ModuleCardInformation(module = module, isPending = isPending)
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            thickness = 0.5.dp,
            color = colorScheme.outline.copy(alpha = 0.5f),
        )
        val actionsAvailable = (module.enabled && !module.remove && !module.update) ||
            (module.id.startsWith(ULTRA_ID_PREFIX) && module.hasWebUi && environmentWebUiAvailable)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!recoveryOnly && actionsAvailable && module.hasActionScript) {
                ModuleCardActionMiuix(
                    label = stringResource(R.string.action), icon = Icons.Rounded.PlayArrow,
                    background = secondaryContainer, tint = actionIconTint,
                    enabled = !isPending, onClick = onExecuteAction,
                    onLongClick = { onAddActionShortcut(ShortcutType.Action) },
                )
            }
            if (!recoveryOnly && actionsAvailable && module.hasWebUi) {
                ModuleCardActionMiuix(
                    label = stringResource(R.string.module_open_webui), icon = Icons.Rounded.Code,
                    background = secondaryContainer, tint = actionIconTint,
                    onClick = onOpenWebUi,
                    onLongClick = { onAddActionShortcut(ShortcutType.WebUI) },
                )
            }
            if (!recoveryOnly && hasUpdate) {
                ModuleCardActionMiuix(
                    label = stringResource(R.string.module_update), icon = MiuixIcons.UploadCloud,
                    background = updateBg, tint = updateTint,
                    enabled = !module.remove && !isPending, onClick = onUpdate,
                )
            }
            ModuleCardActionMiuix(
                label = stringResource(R.string.module_card_artwork), icon = Icons.Rounded.Wallpaper,
                background = secondaryContainer, tint = actionIconTint,
                onClick = { showCardArtDialog = true },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(
                modifier = Modifier.size(48.dp),
                enabled = (!isPending || removalPending) && !(module.remove && module.id.startsWith("maplesu:")),
                onClick = if (module.remove) onUndoUninstall else onUninstall,
            ) {
                Icon(
                    imageVector = if (module.remove) MiuixIcons.Undo else MiuixIcons.Delete,
                    contentDescription = stringResource(if (module.remove) R.string.undo else R.string.uninstall),
                    modifier = Modifier.size(20.dp),
                    tint = actionIconTint,
                )
            }
        }
    }
    if (showCardArtDialog) {
        val cardAspect = if (cardSize.height > 0) cardSize.width.toFloat() / cardSize.height else 2.2f
        ModuleCardArtDialog(
            moduleId = module.id, moduleName = module.name, aspect = cardAspect,
            onDismiss = { showCardArtDialog = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModuleCardActionMiuix(
    label: String,
    icon: ImageVector,
    background: Color,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
) {
    val contentColor = if (enabled) tint else tint.copy(alpha = 0.38f)
    Row(
        modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp)
            .clip(CircleShape).background(if (enabled) background else background.copy(alpha = 0.38f))
            .combinedClickable(enabled = enabled, role = Role.Button, onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = contentColor)
        Text(label, color = contentColor, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 「卡面」设置框:选图(并自裁) / 重新自裁 / 移除背景图 / 取消 */
@Composable
private fun ModuleCardArtDialog(
    moduleId: String,
    moduleName: String,
    aspect: Float,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var cropping by remember { mutableStateOf(false) }
    val hasArt = remember(moduleId, ModuleCardArt.version) { ModuleCardArt.has(context, moduleId) }
    val hasOriginal = remember(moduleId, ModuleCardArt.version) { ModuleCardArt.hasOriginal(context, moduleId) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            if (ModuleCardArt.saveOriginal(context, moduleId, uri)) {
                // 选完直接进自裁框
                cropping = true
            } else {
                Toast.makeText(context, "这张图读不出来,换一张试试", Toast.LENGTH_LONG).show()
            }
        }
    }

    // 【只用这一个 WindowDialog】菜单 / 自裁 只切内容,不再叠两个窗口
    // (v0.13.56 就是因为菜单和自裁分别是两个窗口、旧窗还在退场动画时把触摸全吃掉,
    //  导致自裁框"看得见却点不动")。
    WindowDialog(
        show = true,
        backgroundColor = miuixDialogColor(),
        title = if (cropping) "自裁卡面" else "卡面",
        // 【必须传】WindowDialog 的 onDismissRequest 默认是 null,不传的话返回键和
        // 点击外部都不响应 —— 万一下面按钮不可见就彻底退不出来(详见 ModuleIcon.kt)。
        onDismissRequest = onDismiss,
        content = {
            if (cropping) {
                ModuleCardArtCropContent(
                    moduleId = moduleId,
                    aspect = aspect,
                    onCancel = onDismiss,
                    onApplied = onDismiss,
                )
            } else {
                Column {
                    Text(
                        text = "给「$moduleName」这张卡片换一张背景图;选完可以拖动/双指缩放自裁。",
                        fontSize = 13.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    TextButton(
                        text = "换一张图片(并自裁)",
                        onClick = { picker.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                    if (hasOriginal) {
                        TextButton(
                            text = "自裁这张图",
                            onClick = { cropping = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (hasArt) {
                        TextButton(
                            text = "移除背景图",
                            onClick = {
                                ModuleCardArt.clear(context, moduleId)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    TextButton(
                        text = "取消",
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    )
}

/**
 * 自裁卡面:取景框比例 = **卡片真实比例**(aspect),所以所见即所得。
 *
 * 尺寸按**可用高度**算 —— WindowDialog 的内容区没有滚动,超高的部分会被直接裁掉,
 * 下面那排按钮就会被顶出屏幕、弹窗退不出来(v0.13.57 就是这么锁死的)。
 */
@Composable
private fun ModuleCardArtCropContent(
    moduleId: String,
    aspect: Float,
    onCancel: () -> Unit,
    onApplied: () -> Unit,
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, context, moduleId, ModuleCardArt.version) {
        value = withContext(Dispatchers.IO) { ModuleCardArt.loadOriginal(context, moduleId) }
    }
    val saved = remember(moduleId) { ModuleCardArt.loadCrop(context, moduleId) }
    var biasX by remember { mutableFloatStateOf(saved?.biasX ?: 0f) }
    var biasY by remember { mutableFloatStateOf(saved?.biasY ?: 0f) }
    var zoom by remember { mutableFloatStateOf(saved?.zoom ?: 1f) }
    val a = aspect.coerceIn(0.5f, 4f)

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val budget = if (maxHeight.value.isFinite()) maxHeight - 150.dp else 220.dp
        val previewW = minOf(maxWidth * 0.92f, budget * a, 380.dp).coerceAtLeast(120.dp)
        val previewH = (previewW / a).coerceAtLeast(64.dp)

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(previewW, previewH)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            biasX = (biasX - pan.x / w * 2f).coerceIn(-1f, 1f)
                            biasY = (biasY - pan.y / h * 2f).coerceIn(-1f, 1f)
                            zoom = (zoom * gestureZoom).coerceIn(1f, 4f)
                        }
                    },
            ) {
                val b = bitmap
                if (b != null) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val r = ModuleCardArt.cropRect(b.width, b.height, a, biasX, biasY, zoom)
                        drawImage(
                            image = b,
                            srcOffset = IntOffset(r.left.toInt(), r.top.toInt()),
                            srcSize = IntSize(
                                r.width().toInt().coerceAtLeast(1),
                                r.height().toInt().coerceAtLeast(1),
                            ),
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize(
                                size.width.toInt().coerceAtLeast(1),
                                size.height.toInt().coerceAtLeast(1),
                            ),
                            filterQuality = FilterQuality.Medium,
                        )
                    }
                }
            }
            Text(
                text = "拖动调位置 · 双指缩放 · 现在是 ${"%.1f".format(zoom)}×",
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    text = "缩小",
                    onClick = { zoom = (zoom - 0.25f).coerceAtLeast(1f) },
                    modifier = Modifier.weight(1f),
                    enabled = zoom > 1f,
                )
                TextButton(
                    text = "放大",
                    onClick = { zoom = (zoom + 0.25f).coerceAtMost(4f) },
                    modifier = Modifier.weight(1f),
                    enabled = zoom < 4f,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    text = "复位",
                    onClick = {
                        biasX = 0f
                        biasY = 0f
                        zoom = 1f
                    },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "取消",
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "确定",
                    onClick = {
                        if (ModuleCardArt.bake(context, moduleId, a, biasX, biasY, zoom)) {
                            onApplied()
                        } else {
                            Toast.makeText(context, "保存失败,重新选一张图试试", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}
