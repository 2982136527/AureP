package com.qiuhu.embyflow.ui

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qiuhu.embyflow.data.settings.AppSettings
import com.qiuhu.embyflow.model.EmbyHomePayload
import com.qiuhu.embyflow.model.MediaItem
import com.qiuhu.embyflow.model.displayName
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

private const val ErrorToastDurationMillis = 1800L

@Composable
fun EmbyFlowApp(
    embyViewModel: EmbyViewModel = viewModel(),
) {
    EmbyFlowTheme {
        var currentTab by rememberSaveable { mutableStateOf(RootTab.Home.name) }
        var selectedMediaId by rememberSaveable { mutableStateOf<String?>(null) }
        var selectedMediaSnapshot by remember { mutableStateOf<MediaItem?>(null) }
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
        val payload = currentPayload(uiState)
        val isServerConnected = uiState is EmbyUiState.Ready
        val hasConfiguredServer = serverProfilesState.activeProfile != null
        fun openMediaDetail(media: MediaItem) {
            selectedMediaId = media.id
            selectedMediaSnapshot = media
            embyViewModel.loadMediaDetail(media)
        }

        fun closeMediaDetail() {
            selectedMediaId = null
            selectedMediaSnapshot = null
        }

        val selectedMedia = remember(
            selectedMediaId,
            selectedMediaSnapshot,
            payload,
            tagBrowseState.items,
            actorBrowseState.items,
            mediaDetails,
        ) {
            selectedMediaId?.let { id ->
                mediaDetails[id]
                    ?: findMediaById(
                        id = id,
                        payload = payload,
                        extraItems = tagBrowseState.items + actorBrowseState.items,
                    )
                    ?: selectedMediaSnapshot?.takeIf { it.id == id }
            }
        }

        val currentRootTab = RootTab.valueOf(currentTab)

        BackHandler(
            enabled = playbackState !is PlaybackUiState.Idle ||
                searchVisible ||
                selectedMedia != null ||
                tagBrowseState.activeTag != null ||
                actorBrowseState.activeActor != null ||
                currentRootTab != RootTab.Home,
        ) {
            when {
                playbackState !is PlaybackUiState.Idle -> embyViewModel.closePlayer()
                searchVisible -> {
                    searchVisible = false
                    embyViewModel.clearSearch()
                }
                selectedMedia != null -> closeMediaDetail()
                actorBrowseState.activeActor != null -> embyViewModel.closeActorBrowse()
                tagBrowseState.activeTag != null -> embyViewModel.closeTagBrowse()
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
                    errorMessage = player.message,
                    onRetry = embyViewModel::closePlayer,
                    onTabSelected = { currentTab = it.name },
                    onOpenMedia = ::openMediaDetail,
                    onOpenSearch = { searchVisible = true },
                    onSelectLibrary = embyViewModel::selectLibrary,
                    onLoadMoreLibrary = embyViewModel::loadMoreLibrary,
                    onUpdatePlayerMode = embyViewModel::updatePlayerMode,
                    onUpdateSubtitleMode = embyViewModel::updateSubtitleMode,
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
                PlaybackUiState.Idle -> AnimatedContent(targetState = selectedMedia, label = "root-screen") { media ->
                    val activeTag = tagBrowseState.activeTag
                    val activeActor = actorBrowseState.activeActor
                    if (activeTag != null) {
                        TagBrowseScreen(
                            tag = activeTag,
                            items = tagBrowseState.items,
                            isLoading = tagBrowseState.isLoading,
                            errorMessage = tagBrowseState.errorMessage,
                            onBack = embyViewModel::closeTagBrowse,
                            onOpenMedia = {
                                openMediaDetail(it)
                                embyViewModel.closeTagBrowse()
                            },
                        )
                    } else if (activeActor != null) {
                        MediaBrowseScreen(
                            title = activeActor.name,
                            subtitle = activeActor.role.ifBlank { "演员作品" },
                            items = actorBrowseState.items,
                            isLoading = actorBrowseState.isLoading,
                            errorMessage = actorBrowseState.errorMessage,
                            emptyMessage = "这个演员还没有可显示的作品",
                            loadingMessage = "正在加载这个演员的作品",
                            onBack = embyViewModel::closeActorBrowse,
                            onOpenMedia = {
                                openMediaDetail(it)
                                embyViewModel.closeActorBrowse()
                            },
                            columns = 3,
                            cardCompact = true,
                            titleBelow = true,
                        )
                    } else if (media != null) {
                        DetailScreen(
                            media = media,
                            seriesDetail = media.takeIf { it.isSeries }?.let { seriesDetails[it.id] },
                            relatedItems = payload.latestItems.filterNot { it.id == media.id }.take(6),
                            onPlay = embyViewModel::openPlayer,
                            onBack = ::closeMediaDetail,
                            onOpenRelated = ::openMediaDetail,
                            onOpenTag = embyViewModel::openTag,
                            onOpenActor = embyViewModel::openActor,
                            onSelectSeason = { seasonId ->
                                embyViewModel.selectSeriesSeason(media.id, seasonId)
                            },
                            onPlayEpisode = embyViewModel::openPlayer,
                        )
                    } else {
                        when (val state = uiState) {
                            EmbyUiState.Loading -> LoadingScreen()
                            is EmbyUiState.Error -> RootScaffold(
                                payload = state.fallback,
                                settings = settings,
                                currentTab = currentRootTab,
                                isRefreshingLibrary = false,
                                isAppendingLibrary = false,
                                libraryGridState = libraryGridState,
                                libraryScrollSnapshots = libraryScrollSnapshots,
                                errorMessage = state.detail,
                                onRetry = embyViewModel::refresh,
                                onTabSelected = { currentTab = it.name },
                                onOpenMedia = ::openMediaDetail,
                                onOpenSearch = { searchVisible = true },
                                onSelectLibrary = embyViewModel::selectLibrary,
                                onLoadMoreLibrary = embyViewModel::loadMoreLibrary,
                                onUpdatePlayerMode = embyViewModel::updatePlayerMode,
                                onUpdateSubtitleMode = embyViewModel::updateSubtitleMode,
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
                                errorMessage = null,
                                onRetry = embyViewModel::refresh,
                                onTabSelected = { currentTab = it.name },
                                onOpenMedia = ::openMediaDetail,
                                onOpenSearch = { searchVisible = true },
                                onSelectLibrary = embyViewModel::selectLibrary,
                                onLoadMoreLibrary = embyViewModel::loadMoreLibrary,
                                onUpdatePlayerMode = embyViewModel::updatePlayerMode,
                                onUpdateSubtitleMode = embyViewModel::updateSubtitleMode,
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
    onUpdateSubtitleMode: (String) -> Unit,
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
    val activeProfile = serverProfilesState.activeProfile
    var transientErrorMessage by remember { mutableStateOf<String?>(null) }
    val displayServerName = if (isServerConnected) {
        payload.server.serverName
    } else {
        activeProfile?.displayName() ?: "未配置服务器"
    }
    val displayUserName = if (isServerConnected) {
        payload.server.userName
    } else {
        activeProfile?.username ?: "未配置"
    }

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

    Box(modifier = Modifier.fillMaxSize()) {
        when (currentTab) {
            RootTab.Home -> HomeScreen(
                serverName = displayServerName,
                userName = displayUserName,
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
                server = payload.server,
                settings = settings,
                appUpdateState = appUpdateState,
                serverProfilesState = serverProfilesState,
                onUpdatePlayerMode = onUpdatePlayerMode,
                onUpdateSubtitleMode = onUpdateSubtitleMode,
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
            modifier = Modifier.align(Alignment.BottomCenter),
            selectedTab = currentTab,
            items = listOf(
                FloatingNavItem(RootTab.Home, "发现", Icons.Rounded.Home),
                FloatingNavItem(RootTab.Library, "追剧", Icons.Rounded.Subscriptions),
                FloatingNavItem(RootTab.Settings, "设置", Icons.Rounded.Settings),
            ),
            searchIcon = Icons.Rounded.Search,
            onTabSelected = onTabSelected,
            onSearchClick = onOpenSearch,
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
