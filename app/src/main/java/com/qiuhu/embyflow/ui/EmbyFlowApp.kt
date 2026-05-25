package com.qiuhu.embyflow.ui

import android.os.SystemClock
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qiuhu.embyflow.data.settings.AppSettings
import com.qiuhu.embyflow.model.EmbyHomePayload
import com.qiuhu.embyflow.model.MediaItem
import com.qiuhu.embyflow.model.MediaPerson
import com.qiuhu.embyflow.model.MediaTag
import com.qiuhu.embyflow.model.isSeries
import com.qiuhu.embyflow.ui.components.EditorialBackground
import com.qiuhu.embyflow.ui.components.EditorialCard
import com.qiuhu.embyflow.ui.components.EditorialTextPrimary
import com.qiuhu.embyflow.ui.components.EditorialTextSecondary
import com.qiuhu.embyflow.ui.components.FloatingNavItem
import com.qiuhu.embyflow.ui.components.FloatingNavigationBar
import com.qiuhu.embyflow.ui.screens.DetailScreen
import com.qiuhu.embyflow.ui.screens.HomeScreen
import com.qiuhu.embyflow.ui.screens.LibraryScreen
import com.qiuhu.embyflow.ui.screens.MediaBrowseScreen
import com.qiuhu.embyflow.ui.screens.PlayerLoadingScreen
import com.qiuhu.embyflow.ui.screens.PlayerScreen
import com.qiuhu.embyflow.ui.screens.SearchOverlay
import com.qiuhu.embyflow.ui.screens.SettingsScreen
import com.qiuhu.embyflow.ui.screens.TagBrowseScreen
import com.qiuhu.embyflow.ui.theme.EmbyFlowTheme
import kotlinx.coroutines.delay

private enum class RootTab {
    Home,
    Library,
    Settings,
}

private data class LibraryScrollSnapshot(
    val index: Int = 0,
    val offset: Int = 0,
)

private sealed interface OverlayDestination {
    data class Detail(
        val mediaId: String,
        val snapshot: MediaItem? = null,
    ) : OverlayDestination

    data class Tag(
        val tag: MediaTag,
    ) : OverlayDestination

    data class Actor(
        val actor: MediaPerson,
    ) : OverlayDestination
}

private const val ErrorToastDurationMillis = 1800L
private const val TabTraceTag = "AurePTabSwitch"
private const val TabPrewarmDelayMillis = 450L

@Composable
fun EmbyFlowApp(
    embyViewModel: EmbyViewModel = viewModel(),
) {
    EmbyFlowTheme {
        var currentTab by rememberSaveable { mutableStateOf(RootTab.Home.name) }
        val overlayStack = remember { mutableStateListOf<OverlayDestination>() }
        var searchVisible by rememberSaveable { mutableStateOf(false) }
        val uiState by embyViewModel.uiState.collectAsStateWithLifecycle()
        val playbackState by embyViewModel.playbackState.collectAsStateWithLifecycle()
        val settings by embyViewModel.settings.collectAsStateWithLifecycle()
        val tagBrowseState by embyViewModel.tagBrowseState.collectAsStateWithLifecycle()
        val actorBrowseState by embyViewModel.actorBrowseState.collectAsStateWithLifecycle()
        val mediaDetails by embyViewModel.mediaDetails.collectAsStateWithLifecycle()
        val seriesDetails by embyViewModel.seriesDetails.collectAsStateWithLifecycle()
        val searchState by embyViewModel.searchState.collectAsStateWithLifecycle()
        val serverProfilesState by embyViewModel.serverProfilesState.collectAsStateWithLifecycle()
        val appUpdateState by embyViewModel.appUpdateState.collectAsStateWithLifecycle()
        val libraryGridState = rememberLazyGridState()
        val libraryScrollSnapshots = remember { mutableStateMapOf<String, LibraryScrollSnapshot>() }
        var playbackTransientErrorMessage by rememberSaveable { mutableStateOf<String?>(null) }
        val payload = currentPayload(uiState)
        val isServerConnected = uiState is EmbyUiState.Ready
        val hasConfiguredServer = serverProfilesState.activeProfile != null
        fun openMediaDetail(media: MediaItem) {
            overlayStack += OverlayDestination.Detail(
                mediaId = media.id,
                snapshot = media,
            )
            embyViewModel.loadMediaDetail(media)
        }

        fun openTagBrowse(tag: MediaTag) {
            overlayStack += OverlayDestination.Tag(tag)
            embyViewModel.openTag(tag)
        }

        fun openActorBrowse(actor: MediaPerson) {
            overlayStack += OverlayDestination.Actor(actor)
            embyViewModel.openActor(actor)
        }

        fun popOverlay() {
            val removed = overlayStack.removeLastOrNull() ?: return
            when (removed) {
                is OverlayDestination.Tag -> embyViewModel.closeTagBrowse()
                is OverlayDestination.Actor -> embyViewModel.closeActorBrowse()
                is OverlayDestination.Detail -> Unit
            }

            when (val next = overlayStack.lastOrNull()) {
                is OverlayDestination.Tag -> {
                    if (tagBrowseState.activeTag != next.tag) {
                        embyViewModel.openTag(next.tag)
                    }
                }

                is OverlayDestination.Actor -> {
                    if (actorBrowseState.activeActor?.id != next.actor.id) {
                        embyViewModel.openActor(next.actor)
                    }
                }

                is OverlayDestination.Detail,
                null,
                -> Unit
            }
        }

        val activeOverlay = overlayStack.lastOrNull()
        val selectedMedia = remember(
            activeOverlay,
            payload,
            tagBrowseState.items,
            actorBrowseState.items,
            mediaDetails,
        ) {
            val detail = activeOverlay as? OverlayDestination.Detail ?: return@remember null
            detail.mediaId.let { id ->
                mediaDetails[id]
                    ?: findMediaById(
                        id = id,
                        payload = payload,
                        extraItems = tagBrowseState.items + actorBrowseState.items,
                    )
                    ?: detail.snapshot?.takeIf { it.id == id }
            }
        }

        val currentRootTab = RootTab.valueOf(currentTab)

        LaunchedEffect(playbackState) {
            val errorState = playbackState as? PlaybackUiState.Error ?: return@LaunchedEffect
            playbackTransientErrorMessage = errorState.message
            embyViewModel.closePlayer()
        }

        LaunchedEffect(playbackTransientErrorMessage) {
            val message = playbackTransientErrorMessage ?: return@LaunchedEffect
            delay(ErrorToastDurationMillis)
            if (playbackTransientErrorMessage == message) {
                playbackTransientErrorMessage = null
            }
        }

        BackHandler(
            enabled = playbackState !is PlaybackUiState.Idle ||
                searchVisible ||
                overlayStack.isNotEmpty() ||
                currentRootTab != RootTab.Home,
        ) {
            when {
                playbackState !is PlaybackUiState.Idle -> embyViewModel.closePlayer()
                searchVisible -> {
                    searchVisible = false
                    embyViewModel.clearSearch()
                }
                overlayStack.isNotEmpty() -> popOverlay()
                currentRootTab != RootTab.Home -> currentTab = RootTab.Home.name
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(EditorialBackground),
        ) {

            when (val player = playbackState) {
                is PlaybackUiState.Loading -> PlayerLoadingScreen(title = player.media.title)
                is PlaybackUiState.Ready -> PlayerScreen(
                    media = player.media,
                    mediaId = player.media.id,
                    title = player.source.title,
                    source = player.source,
                    initialResumePositionMs = player.initialPositionMs,
                    settings = settings,
                    onPlaybackStarted = embyViewModel::reportPlaybackStarted,
                    onPlaybackProgress = embyViewModel::reportPlaybackProgress,
                    onPlaybackStopped = embyViewModel::reportPlaybackStopped,
                    onClose = { positionMs, durationMs ->
                        embyViewModel.closePlayer(
                            positionMs = positionMs,
                            durationMs = durationMs,
                        )
                    },
                )
                is PlaybackUiState.Error -> RootScaffold(
                    payload = payload,
                    settings = settings,
                    currentTab = currentRootTab,
                    isRefreshingLibrary = false,
                    isAppendingLibrary = false,
                    libraryGridState = libraryGridState,
                    libraryScrollSnapshots = libraryScrollSnapshots,
                    errorMessage = playbackTransientErrorMessage ?: player.message,
                    onRetry = embyViewModel::closePlayer,
                    onTabSelected = { currentTab = it.name },
                    onOpenMedia = ::openMediaDetail,
                    onOpenSearch = { searchVisible = true },
                    onSelectLibrary = embyViewModel::selectLibrary,
                    onLoadMoreLibrary = embyViewModel::loadMoreLibrary,
                    onUpdatePlayerMode = embyViewModel::updatePlayerMode,
                    onUpdateEmbeddedSubtitleLanguage = embyViewModel::updateEmbeddedSubtitleLanguage,
                    onUpdateExternalSubtitleLanguage = embyViewModel::updateExternalSubtitleLanguage,
                    onUpdateLayoutMode = embyViewModel::updateLayoutMode,
                    onUpdateShowLibraryCardTitle = embyViewModel::updateShowLibraryCardTitle,
                    onUpdateExperimentalDualBackendRace = embyViewModel::updateExperimentalDualBackendRace,
                    onUpdateLibrarySortMode = embyViewModel::updateLibrarySortMode,
                    appUpdateState = appUpdateState,
                    onRefreshAppUpdate = embyViewModel::refreshAppUpdateStatus,
                    serverProfilesState = serverProfilesState,
                    onSaveServerProfile = embyViewModel::saveServerProfile,
                    onDeleteServerProfile = embyViewModel::deleteServerProfile,
                    onActivateServerProfile = embyViewModel::activateServerProfile,
                    isServerConnected = isServerConnected,
                    hasConfiguredServer = hasConfiguredServer,
                    onOpenLibrary = {
                        currentTab = RootTab.Library.name
                        embyViewModel.selectLibrary(it.id)
                    },
                )
                PlaybackUiState.Idle -> AnimatedContent(targetState = activeOverlay, label = "root-screen") { destination ->
                    when (destination) {
                        is OverlayDestination.Detail -> {
                            val media = selectedMedia
                            if (media == null) {
                                LoadingScreen()
                            } else {
                                DetailScreen(
                                    media = media,
                                    seriesDetail = media.takeIf { it.isSeries }?.let { seriesDetails[it.id] },
                                    relatedItems = payload.latestItems.filterNot { it.id == media.id }.take(6),
                                    onPlay = embyViewModel::openPlayer,
                                    onBack = ::popOverlay,
                                    onOpenRelated = ::openMediaDetail,
                                    onOpenTag = ::openTagBrowse,
                                    onOpenActor = ::openActorBrowse,
                                    onSelectSeason = { seasonId ->
                                        embyViewModel.selectSeriesSeason(media.id, seasonId)
                                    },
                                    onPlayEpisode = embyViewModel::openPlayer,
                                )
                            }
                        }

                        is OverlayDestination.Tag -> TagBrowseScreen(
                            tag = destination.tag,
                            items = tagBrowseState.items,
                            isLoading = tagBrowseState.isLoading,
                            errorMessage = tagBrowseState.errorMessage,
                            onBack = ::popOverlay,
                            onOpenMedia = ::openMediaDetail,
                        )

                        is OverlayDestination.Actor -> MediaBrowseScreen(
                            title = destination.actor.name,
                            subtitle = destination.actor.role.ifBlank { "演员作品" },
                            items = actorBrowseState.items,
                            isLoading = actorBrowseState.isLoading,
                            errorMessage = actorBrowseState.errorMessage,
                            emptyMessage = "这个演员还没有可显示的作品",
                            loadingMessage = "正在加载这个演员的作品",
                            onBack = ::popOverlay,
                            onOpenMedia = ::openMediaDetail,
                            columns = 3,
                            cardCompact = true,
                            titleBelow = true,
                        )

                        null -> when (val state = uiState) {
                            EmbyUiState.Loading -> LoadingScreen()
                            is EmbyUiState.Error -> RootScaffold(
                                payload = state.fallback,
                                settings = settings,
                                currentTab = currentRootTab,
                                isRefreshingLibrary = false,
                                isAppendingLibrary = false,
                                libraryGridState = libraryGridState,
                                libraryScrollSnapshots = libraryScrollSnapshots,
                                errorMessage = playbackTransientErrorMessage ?: state.detail,
                                onRetry = embyViewModel::refresh,
                                onTabSelected = { currentTab = it.name },
                                onOpenMedia = ::openMediaDetail,
                                onOpenSearch = { searchVisible = true },
                                onSelectLibrary = embyViewModel::selectLibrary,
                                onLoadMoreLibrary = embyViewModel::loadMoreLibrary,
                                onUpdatePlayerMode = embyViewModel::updatePlayerMode,
                                onUpdateEmbeddedSubtitleLanguage = embyViewModel::updateEmbeddedSubtitleLanguage,
                                onUpdateExternalSubtitleLanguage = embyViewModel::updateExternalSubtitleLanguage,
                                onUpdateLayoutMode = embyViewModel::updateLayoutMode,
                                onUpdateShowLibraryCardTitle = embyViewModel::updateShowLibraryCardTitle,
                                onUpdateExperimentalDualBackendRace = embyViewModel::updateExperimentalDualBackendRace,
                                onUpdateLibrarySortMode = embyViewModel::updateLibrarySortMode,
                                appUpdateState = appUpdateState,
                                onRefreshAppUpdate = embyViewModel::refreshAppUpdateStatus,
                                serverProfilesState = serverProfilesState,
                                onSaveServerProfile = embyViewModel::saveServerProfile,
                                onDeleteServerProfile = embyViewModel::deleteServerProfile,
                                onActivateServerProfile = embyViewModel::activateServerProfile,
                                isServerConnected = isServerConnected,
                                hasConfiguredServer = hasConfiguredServer,
                                onOpenLibrary = {
                                    currentTab = RootTab.Library.name
                                    embyViewModel.selectLibrary(it.id)
                                },
                            )

                            is EmbyUiState.Ready -> RootScaffold(
                                payload = state.payload,
                                settings = settings,
                                currentTab = currentRootTab,
                                isRefreshingLibrary = state.isRefreshingLibrary,
                                isAppendingLibrary = state.isAppendingLibrary,
                                libraryGridState = libraryGridState,
                                libraryScrollSnapshots = libraryScrollSnapshots,
                                errorMessage = playbackTransientErrorMessage,
                                onRetry = embyViewModel::refresh,
                                onTabSelected = { currentTab = it.name },
                                onOpenMedia = ::openMediaDetail,
                                onOpenSearch = { searchVisible = true },
                                onSelectLibrary = embyViewModel::selectLibrary,
                                onLoadMoreLibrary = embyViewModel::loadMoreLibrary,
                                onUpdatePlayerMode = embyViewModel::updatePlayerMode,
                                onUpdateEmbeddedSubtitleLanguage = embyViewModel::updateEmbeddedSubtitleLanguage,
                                onUpdateExternalSubtitleLanguage = embyViewModel::updateExternalSubtitleLanguage,
                                onUpdateLayoutMode = embyViewModel::updateLayoutMode,
                                onUpdateShowLibraryCardTitle = embyViewModel::updateShowLibraryCardTitle,
                                onUpdateExperimentalDualBackendRace = embyViewModel::updateExperimentalDualBackendRace,
                                onUpdateLibrarySortMode = embyViewModel::updateLibrarySortMode,
                                appUpdateState = appUpdateState,
                                onRefreshAppUpdate = embyViewModel::refreshAppUpdateStatus,
                                serverProfilesState = serverProfilesState,
                                onSaveServerProfile = embyViewModel::saveServerProfile,
                                onDeleteServerProfile = embyViewModel::deleteServerProfile,
                                onActivateServerProfile = embyViewModel::activateServerProfile,
                                isServerConnected = isServerConnected,
                                hasConfiguredServer = hasConfiguredServer,
                                onOpenLibrary = {
                                    currentTab = RootTab.Library.name
                                    embyViewModel.selectLibrary(it.id)
                                },
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = searchVisible,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 10 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 10 }),
            ) {
                SearchOverlay(
                    state = searchState,
                    onDismiss = {
                        searchVisible = false
                        embyViewModel.clearSearch()
                    },
                    onQueryChange = embyViewModel::updateSearchQuery,
                    onOpenMedia = { media ->
                        embyViewModel.recordSearchQuery(searchState.query)
                        searchVisible = false
                        embyViewModel.clearSearch()
                        openMediaDetail(media)
                    },
                    onSelectRecentQuery = embyViewModel::selectRecentSearch,
                    onCommitSearch = embyViewModel::recordSearchQuery,
                    onClearRecentSearches = embyViewModel::clearRecentSearches,
                )
            }
        }
    }
}

@Composable
private fun RootScaffold(
    payload: EmbyHomePayload,
    settings: AppSettings,
    currentTab: RootTab,
    isRefreshingLibrary: Boolean,
    isAppendingLibrary: Boolean = false,
    libraryGridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    libraryScrollSnapshots: MutableMap<String, LibraryScrollSnapshot>,
    errorMessage: String?,
    onRetry: () -> Unit,
    onTabSelected: (RootTab) -> Unit,
    onOpenMedia: (MediaItem) -> Unit,
    onOpenSearch: () -> Unit,
    onSelectLibrary: (String) -> Unit,
    onLoadMoreLibrary: () -> Unit = {},
    onUpdatePlayerMode: (String) -> Unit,
    onUpdateEmbeddedSubtitleLanguage: (String) -> Unit,
    onUpdateExternalSubtitleLanguage: (String) -> Unit,
    onUpdateLayoutMode: (String) -> Unit,
    onUpdateShowLibraryCardTitle: (Boolean) -> Unit,
    onUpdateExperimentalDualBackendRace: (Boolean) -> Unit,
    onUpdateLibrarySortMode: (String) -> Unit,
    appUpdateState: com.qiuhu.embyflow.data.update.AppUpdateState,
    onRefreshAppUpdate: () -> Unit,
    serverProfilesState: com.qiuhu.embyflow.model.ServerProfilesState,
    onSaveServerProfile: (com.qiuhu.embyflow.model.ServerProfile) -> Unit,
    onDeleteServerProfile: (String) -> Unit,
    onActivateServerProfile: (String) -> Unit,
    isServerConnected: Boolean,
    hasConfiguredServer: Boolean,
    onOpenLibrary: (MediaItem) -> Unit,
) {
    val saveableStateHolder = rememberSaveableStateHolder()
    val cachedTabs = rememberSaveable(
        saver = listSaver(
            save = { tabs -> tabs.map { it.name } },
            restore = { names ->
                mutableStateListOf<RootTab>().apply {
                    addAll(names.map(RootTab::valueOf))
                }
            },
        ),
    ) {
        mutableStateListOf(currentTab)
    }
    var tabSwitchTarget by remember { mutableStateOf<RootTab?>(null) }
    var tabSwitchStartedAt by remember { mutableStateOf<Long?>(null) }
    var transientErrorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(errorMessage) {
        if (errorMessage.isNullOrBlank()) {
            transientErrorMessage = null
            return@LaunchedEffect
        }

        transientErrorMessage = errorMessage
        delay(ErrorToastDurationMillis)
        if (transientErrorMessage == errorMessage) {
            transientErrorMessage = null
        }
    }

    LaunchedEffect(currentTab) {
        if (currentTab !in cachedTabs) {
            cachedTabs += currentTab
        }
        if (tabSwitchTarget != currentTab) return@LaunchedEffect
        withFrameNanos { }
        val startedAt = tabSwitchStartedAt
        val elapsed = startedAt?.let { SystemClock.uptimeMillis() - it } ?: -1L
        Log.d(
            TabTraceTag,
            "visible tab=${currentTab.name} elapsed=${elapsed}ms",
        )
        tabSwitchTarget = null
        tabSwitchStartedAt = null
    }

    LaunchedEffect(isServerConnected, cachedTabs.size) {
        if (!isServerConnected) return@LaunchedEffect
        if (cachedTabs.size >= RootTab.entries.size) return@LaunchedEffect
        delay(TabPrewarmDelayMillis)
        RootTab.entries.forEach { tab ->
            if (tab !in cachedTabs) {
                cachedTabs += tab
                withFrameNanos { }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        RootTab.entries.forEach { tab ->
            if (tab !in cachedTabs) return@forEach
            val isActive = tab == currentTab
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (isActive) 0f else -1f)
                    .graphicsLayer {
                        alpha = if (isActive) 1f else 0f
                    },
            ) {
                saveableStateHolder.SaveableStateProvider(key = tab.name) {
                    RootTabContent(
                        tab = tab,
                        isActive = isActive,
                        payload = payload,
                        settings = settings,
                        isRefreshingLibrary = isRefreshingLibrary,
                        isAppendingLibrary = isAppendingLibrary,
                        libraryGridState = libraryGridState,
                        libraryScrollSnapshots = libraryScrollSnapshots,
                        appUpdateState = appUpdateState,
                        onRefreshAppUpdate = onRefreshAppUpdate,
                        serverProfilesState = serverProfilesState,
                        onSaveServerProfile = onSaveServerProfile,
                        onDeleteServerProfile = onDeleteServerProfile,
                        onActivateServerProfile = onActivateServerProfile,
                        isServerConnected = isServerConnected,
                        hasConfiguredServer = hasConfiguredServer,
                        onOpenMedia = onOpenMedia,
                        onOpenLibrary = onOpenLibrary,
                        onSelectLibrary = onSelectLibrary,
                        onLoadMoreLibrary = onLoadMoreLibrary,
                        onUpdatePlayerMode = onUpdatePlayerMode,
                        onUpdateEmbeddedSubtitleLanguage = onUpdateEmbeddedSubtitleLanguage,
                        onUpdateExternalSubtitleLanguage = onUpdateExternalSubtitleLanguage,
                        onUpdateLayoutMode = onUpdateLayoutMode,
                        onUpdateShowLibraryCardTitle = onUpdateShowLibraryCardTitle,
                        onUpdateExperimentalDualBackendRace = onUpdateExperimentalDualBackendRace,
                        onUpdateLibrarySortMode = onUpdateLibrarySortMode,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = transientErrorMessage != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp),
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 3 }),
        ) {
            EditorialCard(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(20.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = when {
                            isServerConnected -> "操作失败"
                            hasConfiguredServer -> "连接失败"
                            else -> "未配置服务器"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = EditorialTextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = transientErrorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = EditorialTextSecondary,
                    )
                }
            }
        }

        FloatingNavigationBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(10f),
            selectedTab = currentTab,
            items = listOf(
                FloatingNavItem(RootTab.Home, "发现", Icons.Rounded.Home),
                FloatingNavItem(RootTab.Library, "追剧", Icons.Rounded.Subscriptions),
                FloatingNavItem(RootTab.Settings, "设置", Icons.Rounded.Settings),
            ),
            searchIcon = Icons.Rounded.Search,
            onTabSelected = { tab ->
                if (tab != currentTab) {
                    tabSwitchTarget = tab
                    tabSwitchStartedAt = SystemClock.uptimeMillis()
                    Log.d(
                        TabTraceTag,
                        "request from=${currentTab.name} to=${tab.name}",
                    )
                }
                onTabSelected(tab)
            },
            onSearchClick = onOpenSearch,
        )
    }
}

@Composable
private fun RootTabContent(
    tab: RootTab,
    isActive: Boolean,
    payload: EmbyHomePayload,
    settings: AppSettings,
    isRefreshingLibrary: Boolean,
    isAppendingLibrary: Boolean,
    libraryGridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    libraryScrollSnapshots: MutableMap<String, LibraryScrollSnapshot>,
    appUpdateState: com.qiuhu.embyflow.data.update.AppUpdateState,
    onRefreshAppUpdate: () -> Unit,
    serverProfilesState: com.qiuhu.embyflow.model.ServerProfilesState,
    onSaveServerProfile: (com.qiuhu.embyflow.model.ServerProfile) -> Unit,
    onDeleteServerProfile: (String) -> Unit,
    onActivateServerProfile: (String) -> Unit,
    isServerConnected: Boolean,
    hasConfiguredServer: Boolean,
    onOpenMedia: (MediaItem) -> Unit,
    onOpenLibrary: (MediaItem) -> Unit,
    onSelectLibrary: (String) -> Unit,
    onLoadMoreLibrary: () -> Unit,
    onUpdatePlayerMode: (String) -> Unit,
    onUpdateEmbeddedSubtitleLanguage: (String) -> Unit,
    onUpdateExternalSubtitleLanguage: (String) -> Unit,
    onUpdateLayoutMode: (String) -> Unit,
    onUpdateShowLibraryCardTitle: (Boolean) -> Unit,
    onUpdateExperimentalDualBackendRace: (Boolean) -> Unit,
    onUpdateLibrarySortMode: (String) -> Unit,
) {
    when (tab) {
        RootTab.Home -> HomeScreen(
            isTabActive = isActive,
            layoutMode = settings.layoutMode,
            heroItems = if (isServerConnected) payload.heroItems else emptyList(),
            highlightItems = if (isServerConnected) payload.highlightItems else emptyList(),
            continueWatchingItems = if (isServerConnected) payload.resumeItems else emptyList(),
            libraries = if (isServerConnected) payload.libraries else emptyList(),
            isServerConnected = isServerConnected,
            hasConfiguredServer = hasConfiguredServer,
            onOpenMedia = onOpenMedia,
            onOpenLibrary = onOpenLibrary,
        )

        RootTab.Library -> LibraryScreen(
            isTabActive = isActive,
            libraries = if (isServerConnected) payload.libraries else emptyList(),
            selectedLibraryId = if (isServerConnected) payload.selectedLibraryId else null,
            layoutMode = settings.layoutMode,
            showLibraryCardTitle = settings.showLibraryCardTitle,
            librarySortMode = settings.librarySortMode,
            libraryItems = if (isServerConnected) payload.libraryItems else emptyList(),
            libraryTotalCount = if (isServerConnected) payload.libraryTotalCount else 0,
            isRefreshing = isRefreshingLibrary,
            isAppending = isAppendingLibrary,
            isServerConnected = isServerConnected,
            hasConfiguredServer = hasConfiguredServer,
            gridState = libraryGridState,
            restoredScrollIndex = payload.selectedLibraryId
                ?.let { libraryScrollSnapshots[it]?.index }
                ?: 0,
            restoredScrollOffset = payload.selectedLibraryId
                ?.let { libraryScrollSnapshots[it]?.offset }
                ?: 0,
            onSelectLibrary = onSelectLibrary,
            onLoadMore = onLoadMoreLibrary,
            onSelectLibrarySortMode = onUpdateLibrarySortMode,
            onOpenMedia = onOpenMedia,
            onGridScrollChanged = { index, offset ->
                payload.selectedLibraryId?.let { libraryId ->
                    libraryScrollSnapshots[libraryId] = LibraryScrollSnapshot(
                        index = index,
                        offset = offset,
                    )
                }
            },
        )

        RootTab.Settings -> SettingsScreen(
            isActive = isActive,
            server = payload.server,
            settings = settings,
            appUpdateState = appUpdateState,
            serverProfilesState = serverProfilesState,
            onUpdatePlayerMode = onUpdatePlayerMode,
            onUpdateEmbeddedSubtitleLanguage = onUpdateEmbeddedSubtitleLanguage,
            onUpdateExternalSubtitleLanguage = onUpdateExternalSubtitleLanguage,
            onUpdateLayoutMode = onUpdateLayoutMode,
            onUpdateShowLibraryCardTitle = onUpdateShowLibraryCardTitle,
            onUpdateExperimentalDualBackendRace = onUpdateExperimentalDualBackendRace,
            onRefreshAppUpdate = onRefreshAppUpdate,
            onSaveServerProfile = onSaveServerProfile,
            onDeleteServerProfile = onDeleteServerProfile,
            onActivateServerProfile = onActivateServerProfile,
            isServerConnected = isServerConnected,
        )
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EditorialBackground),
        contentAlignment = Alignment.Center,
    ) {
        EditorialCard(
            modifier = Modifier.padding(horizontal = 28.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 24.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "正在连接媒体库",
                    style = MaterialTheme.typography.headlineSmall,
                    color = EditorialTextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "验证账号并同步首页内容",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EditorialTextSecondary,
                )
            }
        }
    }
}

private fun currentPayload(uiState: EmbyUiState): EmbyHomePayload = when (uiState) {
    is EmbyUiState.Error -> uiState.fallback
    is EmbyUiState.Ready -> uiState.payload
    EmbyUiState.Loading -> com.qiuhu.embyflow.model.SampleCatalog.fallbackPayload
}

private fun findMediaById(
    id: String?,
    payload: EmbyHomePayload,
    extraItems: List<MediaItem> = emptyList(),
): MediaItem? {
    if (id == null) return null
    return (
        payload.heroItems +
            payload.highlightItems +
            payload.latestItems +
            payload.resumeItems +
            payload.libraries +
            payload.libraryItems +
            extraItems
        ).firstOrNull { it.id == id }
}
